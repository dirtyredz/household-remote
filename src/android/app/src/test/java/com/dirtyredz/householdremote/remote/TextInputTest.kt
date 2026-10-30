package com.dirtyredz.householdremote.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextInputTest {
    @Test fun exactUtf8PayloadAndEmptyText() {
        assertEquals(
            """{"method":"ms.remote.control","params":{"Cmd":"SGVsbG8gw6kg8J+Tug==","DataOfCmd":"base64","TypeOfRemote":"SendInputString"}}""",
            SamsungProtocol.text("Hello \u00e9 \uD83D\uDCFA")
        )
        assertEquals(
            """{"method":"ms.remote.control","params":{"Cmd":"","DataOfCmd":"base64","TypeOfRemote":"SendInputString"}}""",
            SamsungProtocol.text("")
        )
    }

    @Test fun doneSendsTextThenEnterWithoutInterleaving() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(backgroundScope, { sent.add(it) }, {})
        dispatcher.start()
        assertTrue(dispatcher.text("hello"))
        dispatcher.tap("KEY_HOME")
        runCurrent()
        assertEquals(
            listOf(
                SamsungProtocol.text("hello"),
                SamsungProtocol.key("KEY_ENTER"),
                SamsungProtocol.key("KEY_HOME")
            ),
            sent
        )
        dispatcher.cancel()
    }

    @Test fun disconnectedAndCancelledTextIsNeverHeldOrReplayed() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(backgroundScope, { sent.add(it) }, {})
        assertFalse(dispatcher.text("offline"))
        dispatcher.start()
        assertTrue(dispatcher.text("cancelled"))
        dispatcher.cancel()
        assertFalse(dispatcher.text("recovering"))
        assertFalse(dispatcher.holding)
        dispatcher.start()
        runCurrent()
        assertTrue(sent.isEmpty())
        dispatcher.cancel()
    }

    @Test fun refusedTextDoesNotSendEnterOrRetry() = runTest {
        val attempted = mutableListOf<String>()
        val dispatcher = KeyDispatcher(backgroundScope, {
            attempted += it
            false
        }, {})
        dispatcher.start()
        dispatcher.text("lost")
        runCurrent()
        assertEquals(listOf(SamsungProtocol.text("lost")), attempted)
        dispatcher.start()
        runCurrent()
        assertEquals(1, attempted.size)
        dispatcher.cancel()
    }

    @Test fun fullQueueRejectsBothTextAndEnter() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(backgroundScope, { sent.add(it) }, {})
        dispatcher.start()
        repeat(16) { assertTrue(dispatcher.tap("KEY_UP")) }
        assertFalse(dispatcher.text("full"))
        runCurrent()
        assertEquals(List(16) { SamsungProtocol.key("KEY_UP") }, sent)
        dispatcher.cancel()
    }
}
