package com.dirtyredz.householdremote.discovery

import com.dirtyredz.householdremote.inventory.Tv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SsdpDiscoveryTest {
    private val events = mutableListOf<String>()

    private val lock = object : MulticastLockHandle {
        override fun acquire() {
            events += "lock.acquire"
        }
        override fun release() {
            events += "lock.release"
        }
    }

    private inner class FakeSocket(
        private val replies: List<SsdpReply>,
        private val failOnReceive: Boolean = false
    ) : SsdpSocket {
        private var next = 0
        var sends = 0
        override fun send(payload: ByteArray) {
            sends++
            events += "send"
            assertTrue(String(payload).contains("ST: ssdp:all"))
        }
        override fun receive(timeoutMs: Int): SsdpReply? {
            if (failOnReceive) throw java.io.IOException("socket broke")
            events += "receive"
            return replies.getOrNull(next++)
        }
        override fun close() {
            events += "socket.close"
        }
    }

    private val probe = object : TvProbe {
        override suspend fun probe(ip: String) =
            Tv(ip, "TV $ip", "M", "AA:BB:CC:00:00:${ip.takeLast(2)}", ip)
    }

    private fun discovery(
        socket: () -> SsdpSocket,
        lockFactory: () -> MulticastLockHandle = {
            lock
        }
    ) = SsdpDiscovery(probe, lockFactory, {
        events += "socket.open"
        socket()
    }, windowMs = 400, resendAfterMs = 10)

    private val samsung = "HTTP/1.1 200 OK\r\nSERVER: Samsung-Linux/4.1 UPnP/1.0\r\n\r\n"

    @Test fun lockIsHeldFromBeforeSendThroughReceiveUntilAfterSocketClose() = runTest {
        val found = discovery({
            FakeSocket(listOf(SsdpReply("192.168.1.151", samsung)))
        }).discover()
        assertEquals(listOf("192.168.1.151"), found.map { it.ip })
        assertEquals("lock.acquire", events.first())
        assertEquals("socket.open", events[1])
        assertTrue(events.indexOf("send") > events.indexOf("lock.acquire"))
        assertTrue(events.indexOf("receive") > events.indexOf("send"))
        assertEquals(listOf("socket.close", "lock.release"), events.takeLast(2))
        assertEquals(1, events.count { it == "lock.release" })
    }

    @Test fun lockReleasedWhenReceiveThrows() = runTest {
        try {
            discovery({ FakeSocket(emptyList(), failOnReceive = true) }).discover()
            fail("expected IOException")
        } catch (_: java.io.IOException) {
        }
        assertEquals(listOf("socket.close", "lock.release"), events.takeLast(2))
    }

    @Test fun lockReleasedWhenSocketCannotBeOpened() = runTest {
        try {
            discovery({ throw java.net.SocketException("no wifi") }).discover()
            fail("expected SocketException")
        } catch (_: java.net.SocketException) {
        }
        assertEquals(listOf("lock.acquire", "socket.open", "lock.release"), events)
    }

    @Test fun failedAcquireNeverOpensSocketOrReleases() = runTest {
        val broken = object : MulticastLockHandle {
            override fun acquire() = throw SecurityException("no CHANGE_WIFI_MULTICAST_STATE")
            override fun release() {
                events += "lock.release"
            }
        }
        try {
            discovery({ FakeSocket(emptyList()) }, { broken }).discover()
            fail("expected SecurityException")
        } catch (_: SecurityException) {
        }
        assertTrue(events.isEmpty())
    }

    @Test fun onlySamsungPrivateRepliesAreProbedAndQueryIsResent() = runTest {
        val socket = FakeSocket(
            listOf(
                SsdpReply("192.168.1.20", "HTTP/1.1 200 OK\r\nSERVER: Linux UPnP router\r\n\r\n"),
                SsdpReply("192.168.1.151", samsung),
                SsdpReply("192.168.1.151", samsung),
                SsdpReply("8.8.8.8", samsung),
                SsdpReply("192.168.1.155", samsung.lowercase())
            )
        )
        val found = discovery({ socket }).discover()
        assertEquals(listOf("192.168.1.151", "192.168.1.155"), found.map { it.ip })
        assertEquals(2, socket.sends)
    }
}
