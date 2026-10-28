package com.dirtyredz.householdremote.remote

import com.dirtyredz.householdremote.apps.TvApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StandbyDispatchTest {
    private val power = SamsungProtocol.key("KEY_POWER")

    @Test fun standbySendsExactlyOnePowerFrame() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(this, {
            sent += it
            true
        }, {})
        dispatcher.start()
        assertTrue(dispatcher.standby())
        runCurrent()
        assertEquals(listOf(power), sent)
    }

    @Test fun pendingTapsAreCancelledAndNeverReplayed() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(this, {
            sent += it
            true
        }, {})
        dispatcher.start()
        assertTrue(dispatcher.tap("KEY_UP"))
        assertTrue(dispatcher.tap("KEY_DOWN"))
        assertTrue(dispatcher.standby())
        runCurrent()
        assertEquals(listOf(power), sent)
        assertFalse("ordinary input stays stopped after standby", dispatcher.tap("KEY_LEFT"))
        dispatcher.start()
        runCurrent()
        assertEquals("a new session never replays old input", listOf(power), sent)
        dispatcher.cancel()
    }

    @Test fun standbyDropsAQueuedAppLaunch() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(this, {
            sent += it
            true
        }, {})
        dispatcher.start()
        assertTrue(dispatcher.launch(TvApp.Netflix))
        assertTrue(dispatcher.standby())
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals("power outranks a launch not yet sent", listOf(power), sent)
        assertFalse("no launch until a new session", dispatcher.launch(TvApp.Netflix))
    }

    @Test fun rejectedSendIsReportedWithoutRetry() = runTest {
        var attempts = 0
        val dispatcher = KeyDispatcher(this, {
            attempts++
            false
        }, {})
        dispatcher.start()
        assertFalse(dispatcher.standby())
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, attempts)
    }
}
