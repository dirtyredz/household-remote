package com.dirtyredz.householdremote.remote

import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.security.Credential
import com.dirtyredz.householdremote.security.Credentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session recovery state machine in virtual time, over a scripted transport. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryPolicyTest {
    private val living = Tv("living", "Living", "Test", "AA:BB:CC:DD:EE:FF", "192.168.1.5")
    private val bedroom = Tv("bedroom", "Bedroom", "Test", "11:22:33:44:55:66", "192.168.1.6")

    private class Store : Credentials {
        var token = "saved"
        var removals = 0
        override fun get(identity: String) = Credential(token, "pin")
        override fun put(identity: String, credential: Credential) {
            token = credential.token
        }
        override fun remove(identity: String) {
            removals++
        }
    }

    private class FakeSocket(val url: HttpUrl, val events: TransportEvents) : TransportSocket {
        var accept = true
        var cancelled = false
        val sent = mutableListOf<String>()
        override val observedPin = "pin"
        override fun send(text: String): Boolean {
            if (!accept) return false
            sent += text
            return true
        }
        override fun cancel() {
            cancelled = true
        }
        fun connectEvent(token: String? = null) = events.message(
            if (token == null) {
                """{"event":"ms.channel.connect","data":{}}"""
            } else {
                """{"event":"ms.channel.connect","data":{"token":"$token"}}"""
            }
        )
        fun drop() = events.closed()
    }

    private inner class Harness(scope: CoroutineScope, timing: SessionTiming = SessionTiming()) {
        val store = Store()
        val sockets = mutableListOf<FakeSocket>()
        val openedAt = mutableListOf<Long>()
        val statuses = mutableListOf<ConnectionStatus>()
        var ime = false
        var cancels = 0
        var now: () -> Long = { 0 }
        val session = RemoteSession(
            scope,
            store,
            { statuses += it },
            { cancels++ },
            timing,
            imeChanged = { ime = it }
        ) { url, _, _, events ->
            FakeSocket(url, events).also {
                sockets += it
                openedAt += now()
            }
        }
        val state get() = session.state
        val last get() = sockets.last()
        val live get() = sockets.count { !it.cancelled }
    }

    private fun TestScope.harness(timing: SessionTiming = SessionTiming()) =
        Harness(backgroundScope, timing).also { h -> h.now = { currentTime } }

    private fun TestScope.connected(h: Harness, tv: Tv = living) {
        h.session.connect(tv)
        runCurrent()
        h.last.connectEvent()
        runCurrent()
        assertEquals(ConnectionState.Connected, h.state)
    }

    @Test fun imeEventsRequireConnectedSessionAndResetOnTeardown() = runTest {
        val h = harness()
        h.session.connect(living)
        h.last.events.message("""{"event":"ms.remote.imeStart","data":null}""")
        runCurrent()
        assertFalse(h.ime)
        h.last.connectEvent()
        runCurrent()
        h.last.events.message("""{"event":"ms.remote.imeStart","data":{}}""")
        runCurrent()
        assertTrue(h.ime)
        h.last.events.message("""{"event":"ms.remote.imeEnd","data":0}""")
        runCurrent()
        assertFalse(h.ime)
        h.last.events.message("""{"event":"ms.remote.imeStart"}""")
        runCurrent()
        assertTrue(h.ime)
        val old = h.last
        old.drop()
        runCurrent()
        assertFalse(h.ime)
        old.events.message("""{"event":"ms.remote.imeStart"}""")
        runCurrent()
        assertFalse(h.ime)
        h.session.disconnect()
    }

    @Test fun imeEndClosesManualInputAndSelectionDiscardsOldImeEvents() = runTest {
        val h = harness()
        connected(h)
        h.ime = true // Phone's manual keyboard opened without an IME-start event.
        h.last.events.message("""{"event":"ms.remote.imeEnd"}""")
        runCurrent()
        assertFalse(h.ime)
        h.last.events.message("""{"event":"ms.remote.imeStart"}""")
        runCurrent()
        assertTrue(h.ime)
        val old = h.last
        h.session.connect(bedroom)
        assertFalse(h.ime)
        old.events.message("""{"event":"ms.remote.imeStart"}""")
        runCurrent()
        assertFalse(h.ime)
        h.last.connectEvent()
        h.last.events.message("""{"event":"ms.remote.imeStart"}""")
        runCurrent()
        assertTrue(h.ime)
        h.session.disconnect()
        assertFalse(h.ime)
    }

    @Test fun dropRetriesWithExactBackoffOneAttemptAtATimeThenReportsUnavailable() = runTest {
        val h = harness()
        connected(h)
        val dropped = currentTime
        h.last.drop()
        runCurrent()
        assertEquals(ConnectionState.Reconnecting, h.state)
        assertEquals(1, h.statuses.last().attempt)
        // Every attempt is refused the moment it opens; the TV stays unreachable.
        while (currentTime - dropped < 61_000) {
            advanceTimeBy(500)
            runCurrent()
            assertTrue("never two live attempts", h.live <= 1)
            if (h.sockets.size > 1 && !h.last.cancelled) h.last.drop()
            runCurrent()
        }
        assertEquals(
            "retries at 1, 3, 7, 15, 30 and 45 s (waits 1, 2, 4, 8, 15, 15 s), then the " +
                "60 s window ends",
            listOf(1_000L, 3_000L, 7_000L, 15_000L, 30_000L, 45_000L),
            h.openedAt.drop(1).map { it - dropped }
        )
        assertEquals(ConnectionState.Unavailable, h.state)
        assertEquals(ConnectionStatus.UNAVAILABLE, h.statuses.last().message)
        assertEquals(0, h.live)
        val opened = h.sockets.size
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals("no attempts after the window", opened, h.sockets.size)
        assertEquals(0, h.store.removals)
    }

    @Test fun unansweredAttemptIsAbandonedAfterTheAttemptDeadline() = runTest {
        val h = harness()
        connected(h)
        h.last.drop()
        advanceTimeBy(1_001)
        runCurrent()
        val silent = h.last
        advanceTimeBy(9_998)
        runCurrent()
        assertFalse(silent.cancelled)
        advanceTimeBy(3)
        runCurrent()
        assertTrue("10 s attempt deadline", silent.cancelled)
        assertEquals(ConnectionState.Reconnecting, h.state)
        assertEquals(2, h.statuses.last().attempt)
    }

    @Test fun recoveryReusesTheLatestSavedTokenAndConnects() = runTest {
        val h = harness()
        h.session.connect(living)
        runCurrent()
        h.last.connectEvent(token = "refreshed")
        runCurrent()
        h.last.drop()
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals("refreshed", h.last.url.queryParameter("token"))
        h.last.connectEvent()
        runCurrent()
        assertEquals(ConnectionState.Connected, h.state)
    }

    @Test fun failureStreakResetsOnlyAfterAStableConnection() = runTest {
        val h = harness()
        connected(h)
        h.last.drop()
        advanceTimeBy(1_001)
        runCurrent()
        h.last.connectEvent()
        runCurrent()
        // Flapping: dropped again inside 30 s, so the next wait grows.
        advanceTimeBy(10_000)
        h.last.drop()
        runCurrent()
        val flap = currentTime
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(2, h.sockets.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(3, h.sockets.size)
        assertEquals(2_000L, h.openedAt.last() - flap)
        h.last.connectEvent()
        runCurrent()
        advanceTimeBy(30_001)
        runCurrent()
        h.last.drop()
        runCurrent()
        val stable = currentTime
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(4, h.sockets.size)
        assertEquals(1_000L, h.openedAt.last() - stable)
    }

    @Test fun aTapAfterExhaustionStartsOneFreshWindowAndRepeatsAddNothing() = runTest {
        val h = harness(SessionTiming(backoffMs = listOf(1_000), recoveryWindowMs = 3_000))
        connected(h)
        h.last.drop()
        advanceTimeBy(3_500)
        runCurrent()
        assertEquals(ConnectionState.Unavailable, h.state)
        val before = h.sockets.size
        assertTrue(h.session.resumeRecovery())
        runCurrent()
        assertEquals("immediate attempt", before + 1, h.sockets.size)
        assertFalse("taps while reconnecting add no attempts", h.session.resumeRecovery())
        assertFalse(h.session.resumeRecovery())
        runCurrent()
        assertEquals(before + 1, h.sockets.size)
        h.last.connectEvent()
        runCurrent()
        assertEquals(ConnectionState.Connected, h.state)
        assertFalse("nothing to resume while connected", h.session.resumeRecovery())
    }

    @Test fun disconnectAndSelectionCancelPendingRecovery() = runTest {
        val h = harness()
        connected(h)
        h.last.drop()
        runCurrent()
        assertEquals(ConnectionState.Reconnecting, h.state)
        h.session.disconnect() // background (ON_STOP) or ViewModel cleared
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, h.sockets.size)
        assertEquals(ConnectionState.Offline, h.state)

        connected(h, living)
        h.last.drop()
        runCurrent()
        h.session.connect(bedroom) // selection change replaces the target
        runCurrent()
        h.last.connectEvent()
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals("no retry for the old TV", 3, h.sockets.size)
        assertEquals(bedroom.ip, h.last.url.host)
        assertTrue(h.sockets.none { it.url.host == living.ip && !it.cancelled })
    }

    @Test fun deliberateStandbyIsNotFoughtByRecovery() = runTest {
        val h = harness()
        connected(h)
        h.session.suppressRecovery()
        assertTrue(h.session.send(SamsungProtocol.key("KEY_POWER")))
        h.last.drop() // the TV closes the socket as it goes to standby
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, h.sockets.size)
        assertEquals(ConnectionState.Offline, h.state)
        assertEquals(ConnectionStatus.AFTER_STANDBY, h.statuses.last().message)
        // An explicit connect (Wake, Reconnect) re-arms recovery.
        connected(h)
        h.last.drop()
        runCurrent()
        assertEquals(ConnectionState.Reconnecting, h.state)
    }

    @Test fun trustPairingAndProtocolFailuresAreTerminalAndKeepCredentials() = runTest {
        for (failure in listOf(
            ConnectionFailure.TrustChanged,
            ConnectionFailure.PairingRequired,
            ConnectionFailure.Denied,
            ConnectionFailure.ProtocolError
        )) {
            val h = harness()
            connected(h)
            h.last.drop()
            advanceTimeBy(1_001)
            runCurrent()
            when (failure) {
                ConnectionFailure.Denied ->
                    h.last.events.message("""{"event":"ms.channel.unauthorized","data":{}}""")

                ConnectionFailure.ProtocolError -> h.last.events.message("not json")

                else -> h.last.events.failed(failure)
            }
            runCurrent()
            advanceTimeBy(120_000)
            runCurrent()
            assertEquals(failure.name, 2, h.sockets.size)
            assertEquals(ConnectionState.Offline, h.state)
            assertEquals(failure, h.statuses.last().failure)
            assertEquals("saved", h.store.token)
            assertEquals(0, h.store.removals)
        }
    }

    @Test fun aDroppedPairingAttemptIsNotRetried() = runTest {
        val h = harness()
        h.session.connect(living, pairing = true)
        runCurrent()
        h.last.drop()
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, h.sockets.size)
        assertEquals(ConnectionStatus.PAIRING_FAILED, h.statuses.last().message)
    }

    @Test fun staleCallbacksFromAReplacedSocketAreInert() = runTest {
        val h = harness()
        connected(h)
        val old = h.last
        h.session.connect(living)
        runCurrent()
        val reported = h.statuses.size
        old.connectEvent()
        old.drop()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(reported, h.statuses.size)
        assertEquals(ConnectionState.Connecting, h.state)
        assertEquals(2, h.sockets.size)
    }

    @Test fun repeatedForegroundStartAndConnectEventsDoNotResetLiveInput() = runTest {
        val h = harness()
        h.session.ensureConnected(living)
        h.session.ensureConnected(living)
        runCurrent()
        assertEquals(1, h.sockets.size)
        h.last.connectEvent()
        runCurrent()
        val cancels = h.cancels
        h.last.connectEvent(token = "rotated")
        h.session.ensureConnected(living)
        runCurrent()
        assertEquals(1, h.sockets.size)
        assertEquals("no input cancellation", cancels, h.cancels)
        assertEquals(1, h.statuses.count { it.state == ConnectionState.Connected })
        assertEquals("rotated", h.store.token)
    }

    @Test fun refusedSendIsDiscardedAndStartsRecoveryWithoutResending() = runTest {
        val h = harness()
        connected(h)
        h.last.accept = false
        val key = SamsungProtocol.key("KEY_UP")
        assertFalse(h.session.send(key))
        assertFalse("readiness clears at once", h.session.send(key))
        runCurrent()
        assertEquals(ConnectionState.Reconnecting, h.state)
        advanceTimeBy(1_001)
        runCurrent()
        h.last.connectEvent()
        runCurrent()
        assertEquals(ConnectionState.Connected, h.state)
        assertTrue("never resent on the new socket", h.last.sent.isEmpty())
        assertNull(h.statuses.last().failure)
    }

    @Test fun losingWifiParksRecoveryUntilWifiReturns() = runTest {
        val h = harness()
        connected(h)
        h.last.drop() // the socket dies with the phone's Wi-Fi
        runCurrent()
        assertEquals(ConnectionState.Reconnecting, h.state)
        h.session.networkChanged(false)
        runCurrent()
        assertEquals(ConnectionState.Unavailable, h.state)
        assertEquals(ConnectionStatus.NO_WIFI, h.statuses.last().message)
        assertEquals(0, h.live)
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals("no attempts without Wi-Fi", 1, h.sockets.size)
        assertFalse("a tap cannot restart recovery without Wi-Fi", h.session.resumeRecovery())

        h.session.networkChanged(true)
        runCurrent()
        assertEquals(ConnectionState.Reconnecting, h.state)
        assertEquals("one immediate attempt when Wi-Fi returns", 2, h.sockets.size)
        h.last.connectEvent()
        runCurrent()
        assertEquals(ConnectionState.Connected, h.state)
        assertEquals(0, h.store.removals)
    }

    @Test fun aDropWithoutWifiParksInsteadOfRetrying() = runTest {
        val h = harness()
        connected(h)
        h.session.networkChanged(false)
        runCurrent()
        assertEquals(ConnectionState.Unavailable, h.state)
        assertTrue("the live socket is torn down", h.last.cancelled)
        h.last.drop() // a late close from the dead socket is inert
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, h.sockets.size)
        assertEquals(ConnectionState.Unavailable, h.state)
    }

    @Test fun connectWithoutWifiWaitsAndNeverStartsPairing() = runTest {
        val h = harness()
        h.session.networkChanged(false)
        h.session.connect(living, pairing = true)
        h.session.ensureConnected(living)
        runCurrent()
        assertEquals(0, h.sockets.size)
        assertEquals(0, h.store.removals)
        assertEquals(ConnectionStatus.NO_WIFI, h.statuses.last().message)
        h.session.networkChanged(true)
        runCurrent()
        assertEquals(1, h.sockets.size)
        assertEquals(
            "saved credentials, not a pairing attempt",
            "saved",
            h.last.url.queryParameter("token")
        )
        assertEquals(0, h.store.removals)
    }

    @Test fun terminalStatesAndDisconnectAreNotResumedByWifi() = runTest {
        val h = harness()
        connected(h)
        h.session.suppressRecovery()
        h.last.drop() // after standby
        runCurrent()
        h.session.networkChanged(false)
        h.session.networkChanged(true)
        runCurrent()
        assertEquals(ConnectionState.Offline, h.state)
        assertEquals(1, h.sockets.size)

        connected(h)
        h.session.networkChanged(false)
        h.session.disconnect() // backgrounded while parked
        h.session.networkChanged(true)
        runCurrent()
        assertEquals(ConnectionState.Offline, h.state)
        assertEquals(2, h.sockets.size)
    }
}
