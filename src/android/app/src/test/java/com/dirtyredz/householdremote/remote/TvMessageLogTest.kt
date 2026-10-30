package com.dirtyredz.householdremote.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvMessageLogTest {
    @Test fun redactsNestedIdentityAndKeepsEventEnvelope() {
        val log = TvMessageLog { "now" }
        log.reset(listOf("Living TV"))
        log.incoming(
            """{"event":"ms.remote.imeUpdate","data":{"token":12345,"ip":"192.168.1.9","name":"Living TV","serial":"ABC123456789","nested":[{"TOKEN":"secret"}],"unknown":"another device"}}"""
        )
        val text = log.entries.value.single()
        for (privateValue in listOf(
            "12345",
            "192.168.1.9",
            "Living TV",
            "ABC123456789",
            "secret",
            "another device"
        )) {
            assertFalse(text.contains(privateValue))
        }
        assertTrue(text.contains("ms.remote.imeUpdate"))
        assertTrue(text.startsWith("now IN"))
        log.incoming("malformed token=secret")
        assertFalse(log.entries.value.last().contains("secret"))
    }

    @Test fun outgoingBase64CannotBypassRedactionAndHistoryIsBounded() {
        val log = TvMessageLog { "now" }
        log.reset(listOf("Living TV"))
        log.outgoing(SamsungProtocol.text("Living TV 192.168.1.9 AA:BB:CC:DD:EE:FF token=secret"))
        val text = log.entries.value.single()
        assertFalse(text.contains("secret"))
        assertTrue(text.contains("Decoded text: [redacted] [redacted] [redacted] [redacted]"))
        assertFalse(text.contains("TGl2aW5n"))
        repeat(105) { log.incoming("""{"event":"event$it"}""") }
        assertEquals(100, log.entries.value.size)
        log.reset(emptyList())
        assertTrue(log.entries.value.isEmpty())
        log.outgoing(SamsungProtocol.key("KEY_HOME"))
        assertTrue(log.entries.value.isEmpty())
        log.outgoing(SamsungProtocol.text("ab"))
        assertTrue(log.entries.value.single().contains("YWI="))
        assertTrue(log.entries.value.single().contains("Decoded text: ab"))
    }
}
