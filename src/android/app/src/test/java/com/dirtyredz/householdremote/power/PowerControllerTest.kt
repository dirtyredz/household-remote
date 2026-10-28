package com.dirtyredz.householdremote.power

import com.dirtyredz.householdremote.discovery.TvProbe
import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.remote.ConnectionState
import com.dirtyredz.householdremote.remote.ConnectionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PowerControllerTest {
    private val living = Tv("living", "Living", "Test", "AA:BB:CC:DD:EE:FF", "192.168.1.5")
    private val bedroom = Tv("bedroom", "Bedroom", "Test", "11:22:33:44:55:66", "192.168.1.6")

    private class FakeClock : AwakeClock {
        var now = 10_000L
        override fun nowMillis(): Long = now
        override suspend fun waitMillis(duration: Long) {
            now += duration
        }
    }

    private class FakeLink : PowerLink {
        var disconnects = 0
        val connects = mutableListOf<Tv>()
        var standbys = 0
        var standbyAccepted = true
        var connectAllowed = true
        override fun disconnect() {
            disconnects++
        }
        override fun connect(tv: Tv): Boolean {
            if (!connectAllowed) return false
            connects += tv
            return true
        }
        override fun standby(): Boolean {
            standbys++
            return standbyAccepted
        }
    }

    private class FakeWake : TvWake {
        val woken = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
        override suspend fun wake(mac: String) {
            woken += mac
            gate?.await()
            failure?.let { throw it }
        }
    }

    /** Answers with the TV whose IP was probed, or fails when [online] is false. */
    private inner class FakeProbe : TvProbe {
        var online = true
        override suspend fun probe(ip: String): Tv {
            check(online) { "offline" }
            return listOf(living, bedroom).first { it.ip == ip }
        }
    }

    private inner class Harness(scope: CoroutineScope) {
        val clock = FakeClock()
        val link = FakeLink()
        val wake = FakeWake()
        val probe = FakeProbe()
        val states = mutableListOf<PowerState>()
        val controller = PowerController(scope, link, wake, probe, clock) { states += it }
    }

    private fun TestScope.harness() = Harness(backgroundScope)

    @Test fun connectedSessionSelectsOneStandby() = runTest {
        val h = harness()
        assertTrue(h.controller.toggle(living, connected = true))
        runCurrent()
        assertEquals(1, h.link.standbys)
        assertTrue(h.wake.woken.isEmpty())
        assertEquals(PowerPhase.SendingStandby, h.states.first().phase)
        assertEquals(PowerState(standbySent = true), h.controller.state)
    }

    @Test fun rejectedStandbyReportsFailureWithoutRetry() = runTest {
        val h = harness()
        h.link.standbyAccepted = false
        h.controller.toggle(living, connected = true)
        runCurrent()
        assertEquals(1, h.link.standbys)
        assertFalse(h.controller.state.standbySent)
        assertEquals(PowerController.STANDBY_FAILED, h.controller.state.error)
    }

    @Test fun unavailableSessionWakesOnceThenConnectsThroughSessionOutcome() = runTest {
        val h = harness()
        h.controller.toggle(living, connected = false)
        runCurrent()
        assertEquals(listOf(living.mac), h.wake.woken)
        assertEquals(1, h.link.disconnects)
        assertEquals(listOf(living), h.link.connects)
        assertEquals(PowerPhase.Connecting, h.controller.state.phase)
        h.controller.onConnection(ConnectionStatus(ConnectionState.Connecting, "Connecting"))
        assertEquals(PowerPhase.Connecting, h.controller.state.phase)
        h.controller.onConnection(ConnectionStatus(ConnectionState.Connected, "Connected"))
        assertEquals(PowerState(), h.controller.state)
    }

    @Test fun readinessTimeoutReportsWakeSettings() = runTest {
        val h = harness()
        h.probe.online = false
        h.controller.toggle(living, connected = false)
        runCurrent()
        assertEquals(PowerController.NOT_READY, h.controller.state.error)
        assertTrue(h.controller.state.idle)
        assertTrue(h.link.connects.isEmpty())
        assertEquals(1, h.wake.woken.size)
    }

    @Test fun wakeErrorIsShownAndNothingRetries() = runTest {
        val h = harness()
        h.wake.failure = IllegalStateException("Wake requires active Wi-Fi")
        h.controller.toggle(living, connected = false)
        runCurrent()
        assertEquals("Wake requires active Wi-Fi", h.controller.state.error)
        assertEquals(1, h.wake.woken.size)
        assertTrue(h.link.connects.isEmpty())
    }

    @Test fun readinessFollowedByConnectionFailureIsExplained() = runTest {
        val h = harness()
        h.controller.toggle(living, connected = false)
        runCurrent()
        h.controller.onConnection(ConnectionStatus.offline("Pair while watching this TV"))
        assertEquals(PowerController.CONNECT_FAILED, h.controller.state.error)
        assertTrue(h.controller.state.idle)
    }

    @Test fun wokenTvMayNeedRecoveryAttemptsButExhaustionIsExplained() = runTest {
        val h = harness()
        h.controller.toggle(living, connected = false)
        runCurrent()
        h.controller.onConnection(ConnectionStatus(ConnectionState.Reconnecting, "Reconnecting…"))
        assertEquals(PowerPhase.Connecting, h.controller.state.phase)
        h.controller.onConnection(ConnectionStatus(ConnectionState.Unavailable, "Unavailable"))
        assertEquals(PowerController.CONNECT_FAILED, h.controller.state.error)
        assertTrue(h.controller.state.idle)
    }

    @Test fun backgroundedRouteEndsConnectingWithoutError() = runTest {
        val h = harness()
        h.link.connectAllowed = false
        h.controller.toggle(living, connected = false)
        runCurrent()
        assertEquals(PowerState(), h.controller.state)
    }

    @Test fun duplicateActivationsAreInert() = runTest {
        val h = harness()
        h.wake.gate = CompletableDeferred()
        assertTrue(h.controller.toggle(living, connected = false))
        runCurrent()
        assertFalse("busy power work ignores presses", h.controller.toggle(living, false))
        h.wake.gate!!.complete(Unit)
        runCurrent()
        h.controller.onConnection(ConnectionStatus(ConnectionState.Connected, "Connected"))
        assertTrue(h.controller.state.idle)
        val guarded = Harness(backgroundScope)
        assertTrue(guarded.controller.toggle(living, connected = true))
        assertFalse("double tap cannot reverse", guarded.controller.toggle(living, true))
        assertEquals(1, guarded.link.standbys)
        assertTrue(guarded.wake.woken.isEmpty())
        guarded.clock.now += PowerController.DUPLICATE_GUARD_MS
        assertTrue(guarded.controller.toggle(living, connected = true))
        runCurrent()
        assertEquals("standby sent selects wake next", listOf(living.mac), guarded.wake.woken)
    }

    @Test fun selectionOrBackgroundCancelsWakeAndStaleWorkCannotReconnect() = runTest {
        val h = harness()
        h.wake.gate = CompletableDeferred()
        h.controller.toggle(living, connected = false)
        runCurrent()
        assertEquals(PowerPhase.Waking, h.controller.state.phase)
        h.controller.reset()
        assertEquals(PowerState(), h.controller.state)
        h.wake.gate = null
        h.controller.toggle(bedroom, connected = false)
        runCurrent()
        assertEquals(listOf(bedroom), h.link.connects)
        assertEquals(listOf(living.mac, bedroom.mac), h.wake.woken)
    }

    @Test fun resetClearsRequestedOffIntent() = runTest {
        val h = harness()
        h.controller.toggle(living, connected = true)
        assertTrue(h.controller.state.standbySent)
        h.controller.reset()
        assertFalse(h.controller.state.standbySent)
        assertTrue("reset also clears the duplicate guard", h.controller.toggle(living, true))
        assertEquals(2, h.link.standbys)
    }

    @Test fun unrelatedStatusUpdatesCannotClearPowerErrors() = runTest {
        val h = harness()
        h.probe.online = false
        h.controller.toggle(living, connected = false)
        runCurrent()
        h.controller.onConnection(ConnectionStatus(ConnectionState.Connected, "Connected"))
        h.controller.onConnection(ConnectionStatus.offline("Disconnected"))
        assertEquals(PowerController.NOT_READY, h.controller.state.error)
        h.clock.now += PowerController.DUPLICATE_GUARD_MS
        h.probe.online = true
        h.controller.toggle(living, connected = false)
        runCurrent()
        assertNull("a fresh press clears the old error", h.controller.state.error)
    }
}
