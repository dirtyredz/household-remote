package com.dirtyredz.householdremote.power

import com.dirtyredz.householdremote.discovery.TvProbe
import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.remote.ConnectionState
import com.dirtyredz.householdremote.remote.ConnectionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The narrow session/input surface the power coordinator needs. */
interface PowerLink {
    /** Ends the current session and its input before waking. */
    fun disconnect()

    /** Connects [tv] only if it is still selected and the app is foregrounded. */
    fun connect(tv: Tv): Boolean

    /** Attempts one standby key; true when the session accepted the frame. */
    fun standby(): Boolean
}

/**
 * Owns power intent, operation state and wake orchestration for the selected TV.
 * Standby is one accepted frame with no retry; wake reuses the existing burst and
 * identity-checked readiness polling, then finishes through the session outcome.
 * Every completion is bound to the activation generation, so a reset (selection,
 * background, reconnect) makes older work inert.
 */
class PowerController(
    private val scope: CoroutineScope,
    private val link: PowerLink,
    private val wakeSender: TvWake,
    private val probe: TvProbe,
    private val clock: AwakeClock,
    private val timeoutMs: Long = WAKE_TIMEOUT_MS,
    private val intervalMs: Long = WAKE_INTERVAL_MS,
    private val guardMs: Long = DUPLICATE_GUARD_MS,
    private val changed: (PowerState) -> Unit
) {
    var state = PowerState()
        private set(value) {
            field = value
            changed(value)
        }
    private var generation = 0
    private var job: Job? = null
    private var lastActivation: Long? = null

    /**
     * One power activation. Inert while an operation runs or inside the short duplicate
     * guard, so a double tap cannot immediately reverse the action. Returns whether it started.
     */
    fun toggle(tv: Tv, connected: Boolean): Boolean {
        if (!state.idle) return false
        val now = clock.nowMillis()
        if (lastActivation?.let { now - it < guardMs } == true) return false
        lastActivation = now
        val epoch = ++generation
        when (state.intent(connected)) {
            PowerIntent.Standby -> standby()
            PowerIntent.Wake -> wake(tv, epoch)
        }
        return true
    }

    /** Completes the Connecting phase from the typed session outcome. */
    fun onConnection(status: ConnectionStatus) {
        if (state.phase != PowerPhase.Connecting) return
        when (status.state) {
            ConnectionState.Connected -> state = PowerState()
            ConnectionState.Offline -> state = PowerState(error = CONNECT_FAILED)
            ConnectionState.Connecting, ConnectionState.AwaitingApproval -> Unit
        }
    }

    /** Cancels power work and clears transient state and the requested-off intent. */
    fun reset() {
        generation++
        job?.cancel()
        job = null
        lastActivation = null
        if (state != PowerState()) state = PowerState()
    }

    private fun standby() {
        state = state.copy(phase = PowerPhase.SendingStandby, error = null)
        state = if (link.standby()) {
            PowerState(standbySent = true)
        } else {
            PowerState(error = STANDBY_FAILED)
        }
    }

    private fun wake(tv: Tv, epoch: Int) {
        state = PowerState(phase = PowerPhase.Waking)
        job = scope.launch {
            try {
                link.disconnect()
                wakeSender.wake(tv.mac)
                val awake = awaitAwake(tv, probe, timeoutMs, intervalMs, clock)
                if (epoch != generation) return@launch
                if (!awake) {
                    state = PowerState(error = NOT_READY)
                    return@launch
                }
                state = PowerState(phase = PowerPhase.Connecting)
                if (!link.connect(tv) && epoch == generation &&
                    state.phase == PowerPhase.Connecting
                ) {
                    state = PowerState()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (epoch == generation) state = PowerState(error = error.message ?: WAKE_FAILED)
            }
        }
    }

    companion object {
        const val WAKE_TIMEOUT_MS = 30_000L
        const val WAKE_INTERVAL_MS = 1_500L
        const val DUPLICATE_GUARD_MS = 800L
        const val NOT_READY = "TV not ready; check Wi-Fi wake settings"
        const val CONNECT_FAILED = "TV responded; remote connection failed"
        const val STANDBY_FAILED = "Standby not sent; remote disconnected"
        const val WAKE_FAILED = "Wake failed"
    }
}
