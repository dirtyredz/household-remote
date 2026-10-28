package com.dirtyredz.householdremote.remote

import com.dirtyredz.householdremote.apps.TvApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** App buttons send one `ed.apps.launch` per press: never held, never replayed. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLaunchTest {
    private class Harness(scope: TestScope) {
        val sent = mutableListOf<String>()
        var accept = true
        val dispatcher = KeyDispatcher(scope, {
            if (accept) sent += it
            accept
        }, {})
    }

    private fun expected(appId: String) =
        """{"method":"ms.channel.emit","params":{"event":"ed.apps.launch","to":"host",""" +
            """"data":{"appId":"$appId","action_type":"DEEP_LINK"}}}"""

    @Test fun eachAppLaunchesByItsVerifiedSamsungId() {
        val ids = mapOf(
            TvApp.Netflix to "3201907018807",
            TvApp.Prime to "3201910019365",
            TvApp.Disney to "3201901017640",
            TvApp.Plex to "3201512006963"
        )
        assertEquals(TvApp.entries.toSet(), ids.keys)
        ids.forEach { (app, id) ->
            assertEquals(id, app.appId)
            assertEquals(expected(id), SamsungProtocol.launch(app.appId, app.launchType))
        }
        assertEquals(listOf("Netflix", "Prime", "Disney+", "Plex"), TvApp.entries.map { it.label })
    }

    @Test fun launchRejectsMalformedIdsAndTypes() {
        assertThrows(IllegalArgumentException::class.java) {
            SamsungProtocol.launch("", TvApp.DEEP_LINK)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SamsungProtocol.launch("\"},{\"x", TvApp.DEEP_LINK)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SamsungProtocol.launch("3201907018807", "OPEN")
        }
    }

    @Test fun launchGoesOutOnceInOrderWithKeys() = runTest {
        val h = Harness(this)
        h.dispatcher.start()
        assertTrue(h.dispatcher.tap("KEY_HOME"))
        assertTrue(h.dispatcher.launch(TvApp.Plex))
        runCurrent()
        assertEquals(
            listOf(
                SamsungProtocol.key("KEY_HOME"),
                SamsungProtocol.launch(TvApp.Plex.appId, TvApp.DEEP_LINK)
            ),
            h.sent
        )
        h.dispatcher.cancel()
    }

    @Test fun launchWithoutASessionIsRefusedAndNeverHeld() = runTest {
        val h = Harness(this)
        assertFalse(h.dispatcher.launch(TvApp.Netflix))
        assertFalse("a refused launch leaves no held key", h.dispatcher.holding)
        h.dispatcher.start()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue("nothing is sent when the session returns", h.sent.isEmpty())
        h.dispatcher.cancel()
    }

    @Test fun acceptedOrQueuedLaunchIsNeverReplayedAfterReconnect() = runTest {
        val h = Harness(this)
        h.dispatcher.start()
        assertTrue(h.dispatcher.launch(TvApp.Netflix))
        runCurrent()
        assertTrue(h.dispatcher.launch(TvApp.Disney))
        h.dispatcher.cancel() // the drop arrives before the second frame went out
        h.dispatcher.start()
        h.dispatcher.start()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf(SamsungProtocol.launch(TvApp.Netflix.appId, TvApp.DEEP_LINK)), h.sent)
        h.dispatcher.cancel()
    }

    @Test fun refusedLaunchIsNotRetried() = runTest {
        var attempts = 0
        val dispatcher = KeyDispatcher(this, {
            attempts++
            false
        }, {})
        dispatcher.start()
        assertTrue(dispatcher.launch(TvApp.Prime))
        runCurrent()
        dispatcher.start()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, attempts)
        dispatcher.cancel()
    }

    @Test fun theTvsLaunchReplyIsReadWithoutDroppingTheSession() {
        assertEquals(
            "ed.apps.launch" to null,
            SamsungProtocol.event("""{"data":200,"event":"ed.apps.launch","from":"host"}""")
        )
        assertEquals(
            "ms.channel.connect" to "t1",
            SamsungProtocol.event("""{"event":"ms.channel.connect","data":{"token":"t1"}}""")
        )
        assertEquals(
            "ed.installedApp.get" to null,
            SamsungProtocol.event("""{"event":"ed.installedApp.get","data":[]}""")
        )
    }
}
