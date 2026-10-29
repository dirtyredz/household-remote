package com.dirtyredz.householdremote.apps

import com.dirtyredz.householdremote.inventory.Tv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns install checks and launch work for the selected, foreground TV. REST is primary;
 * one live-session fallback is attempted only after REST reports failure. Resetting binds all
 * outstanding work to the old selection/session and clears its install results.
 */
class AppController(
    private val scope: CoroutineScope,
    private val probe: AppInstallProbe,
    private val launcher: AppLauncher,
    private val fallback: (TvApp) -> Boolean,
    private val selected: (Tv) -> Boolean,
    private val foreground: () -> Boolean,
    private val changed: (Map<TvApp, AppInstall>) -> Unit
) {
    private var installs: Map<TvApp, AppInstall> = emptyMap()
    private var check: Job? = null
    private val launches = mutableSetOf<Job>()

    /** Reads install state for one usable session and ignores results for a stale TV. */
    fun check(tv: Tv) {
        check?.cancel()
        if (!isCurrent(tv)) return
        update(emptyMap())
        check = scope.launch {
            val found = try {
                probe.installed(tv.ip)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            }
            if (isCurrent(tv)) update(found)
        }
    }

    /** Starts one launch for the live selection; failures remain silent. */
    fun launch(tv: Tv, app: TvApp) {
        if (!isCurrent(tv) || installs[app] == AppInstall.NotInstalled) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (!launcher.launch(tv.ip, app) &&
                    isCurrent(tv) &&
                    installs[app] != AppInstall.NotInstalled
                ) {
                    fallback(app)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Samsung does not confirm that an app opened; launch failures stay silent.
            }
        }
        launches += job
        job.invokeOnCompletion { launches -= job }
        job.start()
    }

    /** Cancels checks and launches and removes state tied to the old session. */
    fun reset() {
        check?.cancel()
        check = null
        launches.toList().forEach(Job::cancel)
        launches.clear()
        update(emptyMap())
    }

    private fun isCurrent(tv: Tv): Boolean = foreground() && selected(tv)

    private fun update(found: Map<TvApp, AppInstall>) {
        installs = found
        changed(found)
    }
}
