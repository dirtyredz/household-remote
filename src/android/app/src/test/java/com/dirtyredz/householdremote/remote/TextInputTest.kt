package com.dirtyredz.householdremote.remote

import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextInputTest {
    @Test fun unicodePasteLongInputAndCursorReplacementPreserveWholeUtf8Value() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(backgroundScope, { sent.add(it) }, {})
        val values = listOf("a", "abcdef", "abXYef", "é 📺", "long".repeat(1024), "")
        dispatcher.start()
        for (value in values) {
            dispatcher.text(value)
            runCurrent()
        }
        assertEquals(
            values,
            sent.map {
                val params = Json.parseToJsonElement(it).jsonObject.getValue("params").jsonObject
                val encoded = params.getValue("Cmd").jsonPrimitive.content
                assertFalse(encoded.contains('\n'))
                String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
            }
        )
        dispatcher.cancel()
    }

    @Test fun exactUtf8PayloadAndEmptyText() {
        assertEquals(
            """{"method":"ms.remote.control","params":{"TypeOfRemote":"SendInputEnd"}}""",
            SamsungProtocol.inputEnd()
        )
        assertEquals(
            """{"method":"ms.remote.control","params":{"Cmd":"SGVsbG8gw6kg8J+Tug==","DataOfCmd":"base64","TypeOfRemote":"SendInputString"}}""",
            SamsungProtocol.text("Hello \u00e9 \uD83D\uDCFA")
        )
        assertEquals(
            """{"method":"ms.remote.control","params":{"Cmd":"","DataOfCmd":"base64","TypeOfRemote":"SendInputString"}}""",
            SamsungProtocol.text("")
        )
    }

    @Test fun liveChangesDeleteAndCloseNeverPressEnter() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(backgroundScope, { sent.add(it) }, {})
        dispatcher.start()
        for (value in listOf("a", "ab", "abc", "ab", "")) {
            assertTrue(dispatcher.text(value))
            runCurrent()
        }
        assertTrue(dispatcher.endText())
        assertFalse(dispatcher.endText())
        runCurrent()
        assertEquals(
            listOf("a", "ab", "abc", "ab", "").map(SamsungProtocol::text) +
                SamsungProtocol.inputEnd(),
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
        dispatcher.endText()
        runCurrent()
        assertEquals(listOf(SamsungProtocol.text("lost")), attempted)
        dispatcher.start()
        runCurrent()
        assertEquals(1, attempted.size)
        dispatcher.cancel()
    }

    @Test fun backpressureCoalescesOneSnapshotAndReservesCompletion() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(backgroundScope, { sent.add(it) }, {})
        dispatcher.start()
        repeat(16) { assertTrue(dispatcher.tap("KEY_UP")) }
        assertTrue(dispatcher.text("a"))
        assertTrue(dispatcher.text("ab"))
        assertTrue(dispatcher.text("abc"))
        assertTrue(dispatcher.endText())
        assertFalse(dispatcher.beginText())
        runCurrent()
        assertEquals(
            List(16) { SamsungProtocol.key("KEY_UP") } +
                listOf(SamsungProtocol.text("abc"), SamsungProtocol.inputEnd()),
            sent
        )
        dispatcher.cancel()
    }

    @Test fun untouchedCloseAndInvalidation() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(backgroundScope, { sent.add(it) }, {})
        dispatcher.start()
        dispatcher.beginText()
        dispatcher.endText()
        runCurrent()
        assertEquals(listOf(SamsungProtocol.inputEnd()), sent)
        dispatcher.beginText()
        dispatcher.text("stale")
        dispatcher.endText()
        dispatcher.cancel()
        dispatcher.start()
        runCurrent()
        assertEquals(1, sent.size)
        dispatcher.text("standby")
        dispatcher.standby()
        runCurrent()
        assertEquals(SamsungProtocol.key("KEY_POWER"), sent.last())
        assertEquals(2, sent.size)
    }
}
