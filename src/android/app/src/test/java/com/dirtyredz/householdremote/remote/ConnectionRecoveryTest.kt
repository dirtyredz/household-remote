package com.dirtyredz.householdremote.remote

import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.security.Credential
import com.dirtyredz.householdremote.security.Credentials
import com.dirtyredz.householdremote.security.TvTrust
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Real-socket recovery against a loopback TLS fake TV with the production certificate pin
 * and OkHttp heartbeat. Reproduces dk-4bbfb674: a graceful close or abrupt cut must reconnect
 * on its own with the saved token and certificate, and the next key must arrive exactly once.
 */
class ConnectionRecoveryTest {
    private class Memory : Credentials {
        @Volatile var value: Credential? = null

        @Volatile var removals = 0
        override fun get(identity: String) = value
        override fun put(identity: String, credential: Credential) {
            value = credential
        }
        override fun remove(identity: String) {
            removals++
            value = null
        }
    }

    private val tv = Tv("fake", "Fake", "test", "AA:BB:CC:DD:EE:FF", "127.0.0.1")

    /** A paired fake TV behind an optional fault proxy, with the app's session and dispatcher. */
    private inner class Harness(timing: SessionTiming, proxied: Boolean) : AutoCloseable {
        val certificate = HeldCertificate.Builder().commonName("Fake TV").build()
        val store = Memory().apply {
            val trust = TvTrust(null, true)
            trust.checkServerTrusted(arrayOf(certificate.certificate), "RSA")
            value = Credential("saved-token", trust.observed!!)
        }
        val saved = store.value
        private val serial = SerialSessionScope()
        val states = LinkedBlockingQueue<ConnectionState>()
        val peers = LinkedBlockingQueue<WebSocket>()
        val keys = LinkedBlockingQueue<String>()
        val server = MockWebServer()
        lateinit var dispatcher: KeyDispatcher
        val session = RemoteSession(serial.scope, store, { status ->
            if (status.ready) dispatcher.start()
            states.add(status.state)
        }, { dispatcher.cancel() }, timing)
        val proxy: FaultProxy?

        init {
            dispatcher = KeyDispatcher(serial.scope, session::send, {})
            server.useHttps(
                HandshakeCertificates.Builder().heldCertificate(
                    certificate
                ).build().sslSocketFactory()
            )
            repeat(3) { server.enqueue(tvResponse()) }
            server.start()
            proxy = if (proxied) FaultProxy(server.port) else null
        }

        private fun tvResponse() = MockResponse.Builder().webSocketUpgrade(
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    peers.add(webSocket)
                    webSocket.send("""{"event":"ms.channel.connect","data":{}}""")
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    keys.add(text)
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                }
            }
        ).build()

        fun <T> onSession(block: () -> T): T = serial.onSession(block)

        fun connect() {
            onSession { session.connect(tv, false, proxy?.port ?: server.port) }
            await(ConnectionState.Connected)
            assertEquals("saved-token", server.takeRequest().url.queryParameter("token"))
        }

        fun await(expected: ConnectionState, seconds: Long = 5) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            while (System.nanoTime() < deadline) {
                if (states.poll(50, TimeUnit.MILLISECONDS) == expected) return
            }
            fail("No $expected within $seconds s")
        }

        override fun close() {
            serial.use {
                try {
                    onSession { session.disconnect() }
                } finally {
                    proxy?.close()
                    server.close()
                }
            }
        }
    }

    @Test fun gracefulDropReconnectsAutomatically() = dropAndRecover(abrupt = false)

    @Test fun abruptDropReconnectsAutomatically() = dropAndRecover(abrupt = true)

    private fun dropAndRecover(abrupt: Boolean) = Harness(SessionTiming(), abrupt).use { h ->
        h.connect()
        val peer = h.peers.poll(5, TimeUnit.SECONDS)!!
        val dropped = System.nanoTime()
        if (abrupt) h.proxy!!.drop() else peer.close(1000, "simulated idle drop")
        h.await(ConnectionState.Reconnecting)
        assertTrue("one fresh key may wait", h.onSession { h.dispatcher.hold("KEY_HOME") })
        h.await(ConnectionState.Connected)
        val recoveredMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - dropped)
        assertTrue("recovered in $recoveredMs ms", recoveredMs < 5_000)
        assertEquals(
            "the automatic attempt reuses the saved token",
            "saved-token",
            h.server.takeRequest(1, TimeUnit.SECONDS)!!.url.queryParameter("token")
        )
        assertEquals(SamsungProtocol.key("KEY_HOME"), h.keys.poll(5, TimeUnit.SECONDS))
        assertNull(
            "the held key is delivered exactly once",
            h.keys.poll(300, TimeUnit.MILLISECONDS)
        )
        assertTrue(h.onSession { h.dispatcher.tap("KEY_UP") })
        assertEquals(SamsungProtocol.key("KEY_UP"), h.keys.poll(5, TimeUnit.SECONDS))
        assertNull(h.keys.poll(300, TimeUnit.MILLISECONDS))
        assertEquals(h.saved, h.store.value)
        assertEquals(0, h.store.removals)
        println("RECOVERY abrupt=$abrupt recoveredMs=$recoveredMs")
    }

    @Test fun heartbeatDetectsASilentPeerAndRecovers() = Harness(
        SessionTiming(pingIntervalMs = 200, attemptTimeoutMs = 1_000, backoffMs = listOf(100)),
        true
    ).use { h ->
        h.connect()
        h.proxy!!.freeze()
        val frozen = System.nanoTime()
        // TCP stays open; only the missing pong can reveal the dead peer.
        h.await(ConnectionState.Reconnecting, seconds = 3)
        val detectedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - frozen)
        assertTrue("detected in $detectedMs ms", detectedMs < 1_500)
        h.proxy!!.thaw()
        h.await(ConnectionState.Connected)
        assertTrue(h.onSession { h.dispatcher.tap("KEY_DOWN") })
        assertEquals(SamsungProtocol.key("KEY_DOWN"), h.keys.poll(5, TimeUnit.SECONDS))
        println("HEARTBEAT pingMs=200 detectedMs=$detectedMs")
    }

    @Test fun responsivePeerStaysConnectedAcrossManyPingIntervals() =
        Harness(SessionTiming(pingIntervalMs = 100), false).use { h ->
            h.connect()
            assertNull("no drop over 8 intervals", h.states.poll(800, TimeUnit.MILLISECONDS))
            assertTrue(h.onSession { h.dispatcher.tap("KEY_LEFT") })
            assertEquals(SamsungProtocol.key("KEY_LEFT"), h.keys.poll(5, TimeUnit.SECONDS))
            assertEquals("only the original attempt", 1, h.server.requestCount)
        }

    @Test fun productionClientCarriesTheHeartbeat() {
        val client = TvTrust("fake-pin", false).client(SessionTiming().pingIntervalMs)
        try {
            assertEquals(15_000, client.pingIntervalMillis)
            assertTrue("HTTP retries stay off", !client.retryOnConnectionFailure)
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
