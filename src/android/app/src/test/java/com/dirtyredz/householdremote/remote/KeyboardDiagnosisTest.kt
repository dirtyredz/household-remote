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

/** Throwaway diagnosis: observes production frames, not TV interpretation. */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardDiagnosisTest {
    @Test fun completeStringsSurviveQueueAndUtf8Encoding() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(backgroundScope, { sent.add(it) }, {})
        dispatcher.start()
        val samples = listOf("a", "abcdef", "Hello \u00e9 \uD83D\uDCFA", "x".repeat(1024), "")
        samples.forEach { assertTrue(dispatcher.text(it)) }
        runCurrent()
        assertEquals(10, sent.size)
        samples.forEachIndexed { i, expected ->
            val params = Json.parseToJsonElement(sent[i * 2]).jsonObject["params"]!!.jsonObject
            val encoded = params["Cmd"]!!.jsonPrimitive.content
            assertFalse(encoded.contains('\n'))
            assertFalse(encoded.contains('\r'))
            assertEquals(expected, String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))
            assertEquals("base64", params["DataOfCmd"]!!.jsonPrimitive.content)
            assertEquals("SendInputString", params["TypeOfRemote"]!!.jsonPrimitive.content)
            assertEquals(SamsungProtocol.key("KEY_ENTER"), sent[i * 2 + 1])
        }
        dispatcher.cancel()
    }

    @Test fun repeatedStringsAreNotDeduplicatedOrCoalesced() = runTest {
        val sent = mutableListOf<String>()
        val dispatcher = KeyDispatcher(backgroundScope, { sent.add(it) }, {})
        dispatcher.start()
        listOf("a", "ab", "abc", "abc").forEach { assertTrue(dispatcher.text(it)) }
        runCurrent()
        assertEquals(8, sent.size)
        assertEquals(SamsungProtocol.text("abc"), sent[4])
        assertEquals(sent[4], sent[6])
        dispatcher.cancel()
    }

    @Test fun parserOnlyReturnsTopLevelEventAndToken() {
        assertEquals("ms.remote.imeStart" to null, SamsungProtocol.event(
            """{"event":"ms.remote.imeStart","data":{"text":"abcdef","inputType":"text"}}"""
        ))
        assertEquals("ms.remote.imeUpdate" to null, SamsungProtocol.event(
            """{"event":"ms.remote.imeUpdate","data":"abcdef"}"""
        ))
        assertEquals("ms.channel.emit" to null, SamsungProtocol.event(
            """{"event":"ms.channel.emit","data":{"event":"ms.remote.imeStart"}}"""
        ))
    }
}
