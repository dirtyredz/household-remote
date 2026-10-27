package com.dirtyredz.householdremote

import com.dirtyredz.householdremote.discovery.TvProbe
import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.power.AwakeClock
import com.dirtyredz.householdremote.power.awaitAwake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerTest {
    private val tv = Tv("living", "Living", "Test", "AA:BB:CC:DD:EE:FF", "192.168.1.5")

    private class FakeClock : AwakeClock {
        var now = 0L
        override fun nowMillis(): Long = now
        override suspend fun waitMillis(duration: Long) {
            now += duration
        }
    }

    private class FakeProbe(private val responses: List<Tv?>) : TvProbe {
        var calls = 0
        override suspend fun probe(ip: String): Tv =
            responses.getOrNull(calls++) ?: error("offline")
    }

    @Test fun awakePollingUsesMacIdentityAndInjectedClock() = runTest {
        val clock = FakeClock()
        val wrong = tv.copy(mac = "11:22:33:44:55:66")
        val probe = FakeProbe(listOf(null, wrong, tv))

        assertTrue(awaitAwake(tv, probe, timeoutMs = 5_000, intervalMs = 1_000, clock))
        assertEquals(3, probe.calls)
        assertEquals(3_000L, clock.now)
    }

    @Test fun awakePollingStopsAtDeadlineWithoutAnExtraProbe() = runTest {
        val clock = FakeClock()
        val probe = FakeProbe(emptyList())

        assertFalse(awaitAwake(tv, probe, timeoutMs = 2_500, intervalMs = 1_000, clock))
        assertEquals(2, probe.calls)
        assertEquals(2_500L, clock.now)
    }
}
