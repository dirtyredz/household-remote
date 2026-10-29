package com.dirtyredz.householdremote.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One fresh key may wait for recovery; nothing already submitted is ever replayed. */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingKeyTest {
    private class Harness(scope: TestScope) {
        val sent = mutableListOf<String>()
        var dropped = 0
        var accept = true
        val dispatcher = KeyDispatcher(scope, {
            if (accept) sent += it
            accept
        }, { dropped++ })
    }

    @Test fun oneHeldKeyIsSentOnceWhenTheSessionReturns() = runTest {
        val h = Harness(this)
        assertTrue(h.dispatcher.hold("KEY_UP"))
        assertFalse("a second waiting key is rejected", h.dispatcher.hold("KEY_DOWN"))
        h.dispatcher.cancel() // an attempt teardown during recovery keeps the held key
        advanceTimeBy(4_000)
        h.dispatcher.start()
        runCurrent()
        assertEquals(listOf(SamsungProtocol.key("KEY_UP")), h.sent)
        h.dispatcher.start() // a later session never replays it
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, h.sent.size)
        h.dispatcher.cancel()
    }

    @Test fun heldKeyExpiresSilentlyAfterFiveSeconds() = runTest {
        val h = Harness(this)
        assertTrue(h.dispatcher.hold("KEY_UP"))
        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(h.dispatcher.holding)
        advanceTimeBy(2)
        runCurrent()
        assertFalse(h.dispatcher.holding)
        assertEquals(1, h.dropped)
        assertTrue("a fresh key may wait after expiry", h.dispatcher.hold("KEY_DOWN"))
        h.dispatcher.start()
        runCurrent()
        assertEquals(
            "the expired key is never sent",
            listOf(SamsungProtocol.key("KEY_DOWN")),
            h.sent
        )
        h.dispatcher.cancel()
    }

    @Test fun discardedKeyIsNeverSent() = runTest {
        val h = Harness(this)
        assertTrue(h.dispatcher.hold("KEY_UP"))
        h.dispatcher.discardPending() // selection change, background, explicit reconnect
        h.dispatcher.start()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(h.sent.isEmpty())
        assertEquals(0, h.dropped)
        h.dispatcher.cancel()
    }

    @Test fun powerAndLiveInputAreNeverHeld() = runTest {
        val h = Harness(this)
        assertFalse("standby is never queued for later", h.dispatcher.hold("KEY_POWER"))
        h.dispatcher.start()
        assertFalse("a live queue takes taps directly", h.dispatcher.hold("KEY_UP"))
        h.dispatcher.cancel()
    }

    @Test fun standbyDiscardsAHeldKey() = runTest {
        val h = Harness(this)
        h.dispatcher.hold("KEY_UP")
        assertTrue(h.dispatcher.standby())
        h.dispatcher.start()
        runCurrent()
        assertEquals(listOf(SamsungProtocol.key("KEY_POWER")), h.sent)
        h.dispatcher.cancel()
    }
}
