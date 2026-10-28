package com.dirtyredz.householdremote.remote

import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.security.Credential
import com.dirtyredz.householdremote.security.Credentials
import com.dirtyredz.householdremote.security.TvTrust
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
 * Real-socket recovery against a loopback TLS fake TV with the production certificate pin.
 * Reproduces dk-4bbfb674: a graceful close or abrupt cut must reconnect on its own with the
 * saved token and certificate, and the next key must reach the TV exactly once.
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

    @Test fun gracefulDropReconnectsAutomatically() = dropAndRecover(abrupt = false)

    @Test fun abruptDropReconnectsAutomatically() = dropAndRecover(abrupt = true)

    private fun dropAndRecover(abrupt: Boolean) {
        val certificate = HeldCertificate.Builder().commonName("Fake TV").build()
        val store = Memory().apply {
            val trust = TvTrust(null, true)
            trust.checkServerTrusted(arrayOf(certificate.certificate), "RSA")
            value = Credential("saved-token", trust.observed!!)
        }
        val saved = store.value
        val executor = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + executor)
        val states = LinkedBlockingQueue<ConnectionState>()
        val peers = LinkedBlockingQueue<WebSocket>()
        val keys = LinkedBlockingQueue<String>()
        lateinit var dispatcher: KeyDispatcher
        val session = RemoteSession(scope, store, { status ->
            if (status.ready) dispatcher.start()
            states.add(status.state)
        }, { dispatcher.cancel() })
        dispatcher = KeyDispatcher(scope, session::send) {}
        fun awaitConnected() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                if (states.poll(100, TimeUnit.MILLISECONDS) == ConnectionState.Connected) return
            }
            fail("No automatic Connected within 5 s")
        }
        MockWebServer().use { server ->
            server.useHttps(
                HandshakeCertificates.Builder().heldCertificate(
                    certificate
                ).build().sslSocketFactory()
            )
            fun response() = MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
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
            }).build()
            server.enqueue(response())
            server.enqueue(response())
            server.start()
            val proxy = if (abrupt) FaultProxy(server.port) else null
            try {
                runBlocking(executor) { session.connect(tv, false, proxy?.port ?: server.port) }
                awaitConnected()
                assertEquals("saved-token", server.takeRequest().url.queryParameter("token"))
                val peer = peers.poll(5, TimeUnit.SECONDS)!!
                if (abrupt) proxy!!.drop() else peer.close(1000, "simulated idle drop")
                awaitConnected()
                assertEquals(
                    "the automatic attempt reuses the saved token",
                    "saved-token",
                    server.takeRequest(1, TimeUnit.SECONDS)!!.url.queryParameter("token")
                )
                assertTrue(runBlocking(executor) { dispatcher.tap("KEY_HOME") })
                assertEquals(SamsungProtocol.key("KEY_HOME"), keys.poll(5, TimeUnit.SECONDS))
                assertNull("delivered exactly once", keys.poll(300, TimeUnit.MILLISECONDS))
                assertEquals(saved, store.value)
                assertEquals(0, store.removals)
            } finally {
                runBlocking(executor) { session.disconnect() }
                scope.cancel()
                executor.close()
                proxy?.close()
            }
        }
    }
}
