package com.dirtyredz.householdremote.apps

import com.dirtyredz.householdremote.inventory.Tv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppControllerTest {
    private val living = Tv("living", "Living", "Test", "AA:BB:CC:DD:EE:FF", "192.168.1.5")
    private val bedroom = Tv("bedroom", "Bedroom", "Test", "11:22:33:44:55:66", "192.168.1.6")

    private class FakeProbe : AppInstallProbe {
        var gate: CompletableDeferred<Map<TvApp, AppInstall>>? = null
        override suspend fun installed(ip: String): Map<TvApp, AppInstall> =
            gate?.await() ?: emptyMap()
    }

    private class FakeLauncher : AppLauncher {
        var succeeds = true
        var gate: CompletableDeferred<Unit>? = null
        var cancelled = false
        val launches = mutableListOf<Pair<String, TvApp>>()

        override suspend fun launch(ip: String, app: TvApp): Boolean {
            launches += ip to app
            try {
                gate?.await()
            } catch (error: CancellationException) {
                cancelled = true
                throw error
            }
            return succeeds
        }
    }

    private inner class Harness(scope: TestScope) {
        val probe = FakeProbe()
        val launcher = FakeLauncher()
        val fallbacks = mutableListOf<TvApp>()
        val states = mutableListOf<Map<TvApp, AppInstall>>()
        var current = living
        var foreground = true
        val controller = AppController(
            scope.backgroundScope,
            probe,
            launcher,
            { app -> fallbacks.add(app) },
            { it.mac == current.mac },
            { foreground },
            states::add
        )
    }

    @Test fun websocketFallbackRunsOnlyAfterRestFailure() = runTest {
        val h = Harness(this)
        h.controller.launch(living, TvApp.Netflix)
        runCurrent()
        assertEquals(listOf(living.ip to TvApp.Netflix), h.launcher.launches)
        assertTrue(h.fallbacks.isEmpty())

        h.launcher.succeeds = false
        h.controller.launch(living, TvApp.Plex)
        runCurrent()
        assertEquals(listOf(TvApp.Plex), h.fallbacks)
    }

    @Test fun staleTvAndBackgroundCannotStartLaunches() = runTest {
        val h = Harness(this)
        h.current = bedroom
        h.controller.launch(living, TvApp.Netflix)
        h.current = living
        h.foreground = false
        h.controller.launch(living, TvApp.Prime)
        runCurrent()
        assertTrue(h.launcher.launches.isEmpty())
        assertTrue(h.fallbacks.isEmpty())
    }

    @Test fun selectionChangeBeforeRestFailurePreventsFallback() = runTest {
        val h = Harness(this)
        h.launcher.succeeds = false
        h.launcher.gate = CompletableDeferred()
        h.controller.launch(living, TvApp.Disney)
        runCurrent()
        h.current = bedroom
        h.launcher.gate!!.complete(Unit)
        runCurrent()
        assertTrue(h.fallbacks.isEmpty())
    }

    @Test fun resetCancelsInFlightWorkAndClearsInstallState() = runTest {
        val h = Harness(this)
        h.launcher.gate = CompletableDeferred()
        h.controller.launch(living, TvApp.Netflix)
        runCurrent()
        assertFalse(h.launcher.cancelled)

        h.controller.reset()
        runCurrent()

        assertTrue(h.launcher.cancelled)
        assertTrue(h.states.last().isEmpty())
        assertTrue(h.fallbacks.isEmpty())
    }
}
