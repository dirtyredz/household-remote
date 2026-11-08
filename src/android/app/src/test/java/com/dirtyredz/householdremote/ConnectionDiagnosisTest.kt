package com.dirtyredz.householdremote

import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.remote.KeyDispatcher
import com.dirtyredz.householdremote.remote.RemoteSession
import com.dirtyredz.householdremote.remote.SamsungProtocol
import com.dirtyredz.householdremote.security.Credential
import com.dirtyredz.householdremote.security.Credentials
import com.dirtyredz.householdremote.security.TvTrust
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
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
import org.junit.Assert.*
import org.junit.Test

/** Temporary diagnosis only: these assertions describe the current broken recovery behavior. */
class ConnectionDiagnosisTest {
    private class FaultProxy(private val targetPort: Int) : AutoCloseable {
        private val listener = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        private val sockets = CopyOnWriteArrayList<Socket>()
        private val threads = Executors.newCachedThreadPool()
        val port: Int get() = listener.localPort
        init {
            threads.submit {
                try {
                    while (!listener.isClosed) {
                        val incoming = listener.accept()
                        sockets.add(incoming)
                        val outgoing = Socket("127.0.0.1", targetPort)
                        sockets.add(outgoing)
                        fun pump(from: Socket, to: Socket) = threads.submit {
                            try { from.getInputStream().copyTo(to.getOutputStream()) } catch (_: Exception) { }
                        }
                        pump(incoming, outgoing)
                        pump(outgoing, incoming)
                    }
                } catch (_: Exception) { }
            }
        }
        fun drop() { sockets.forEach { it.close() } }
        override fun close() {
            listener.close()
            drop()
            threads.shutdownNow()
            check(threads.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
    private class Memory : Credentials {
        @Volatile var value: Credential? = null
        var removals = 0
        override fun get(identity: String) = value
        override fun put(identity: String, credential: Credential) { value = credential }
        override fun remove(identity: String) { removals++; value = null }
    }

    @Test fun gracefulDropNeedsManualReconnect() = drop(false)
    @Test fun abruptDropNeedsManualReconnect() = drop(true)

    private fun drop(abrupt: Boolean) {
        val certificate = HeldCertificate.Builder().commonName("Fake TV").build()
        val store = Memory().apply {
            val trust = TvTrust(null, true)
            trust.checkServerTrusted(arrayOf(certificate.certificate), "RSA")
            value = Credential("saved-token", trust.observed!!)
        }
        val saved = store.value
        val executor = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + executor)
        val statuses = LinkedBlockingQueue<Pair<String, Boolean>>()
        val peers = LinkedBlockingQueue<WebSocket>()
        val keys = LinkedBlockingQueue<String>()
        val tv = Tv("fake", "Fake", "test", "AA:BB:CC:DD:EE:FF", "127.0.0.1")
        lateinit var dispatcher: KeyDispatcher
        val session = RemoteSession(scope, store, { text, ready ->
            if (ready) dispatcher.start()
            statuses.add(text to ready)
        }, { dispatcher.cancel() })
        dispatcher = KeyDispatcher(scope, session::send) { statuses.add(it to false) }
        fun await(expected: String) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                val event = statuses.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (event.first.startsWith(expected)) return
            }
            fail("Missing $expected")
        }
        MockWebServer().use { server ->
            server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory())
            fun response() = MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    peers.add(webSocket)
                    webSocket.send("""{"event":"ms.channel.connect","data":{}}""")
                }
                override fun onMessage(webSocket: WebSocket, text: String) { keys.add(text) }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            }).build()
            server.enqueue(response())
            server.enqueue(response())
            server.start()
            val proxy = if (abrupt) FaultProxy(server.port) else null
            val connectPort = proxy?.port ?: server.port
            try {
                runBlocking(executor) { session.connect(tv, false, connectPort) }
                await("Connected")
                assertEquals("saved-token", server.takeRequest().url.queryParameter("token"))
                val peer = peers.poll(5, TimeUnit.SECONDS)!!
                val started = System.nanoTime()
                if (abrupt) proxy!!.drop() else peer.close(1000, "simulated idle drop")
                await(if (abrupt) "Connection failed" else "Disconnected")
                val detectedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                assertFalse(runBlocking(executor) { dispatcher.tap("KEY_HOME") })
                assertFalse(runBlocking(executor) { session.send(SamsungProtocol.key("KEY_HOME")) })
                assertNull(server.takeRequest(1500, TimeUnit.MILLISECONDS))
                assertNull(keys.poll())
                assertEquals(saved, store.value)
                runBlocking(executor) { session.connect(tv, false, connectPort) }
                await("Connected")
                assertEquals("saved-token", server.takeRequest().url.queryParameter("token"))
                assertNull(keys.poll(100, TimeUnit.MILLISECONDS))
                assertTrue(runBlocking(executor) { dispatcher.tap("KEY_HOME") })
                assertEquals(SamsungProtocol.key("KEY_HOME"), keys.poll(5, TimeUnit.SECONDS))
                assertEquals(0, store.removals)
                println("DIAG abrupt=$abrupt detectionMs=$detectedMs autoReconnectsWithin1500ms=0 postDropTapAccepted=false manualReconnect=true deliveredAfterManual=1 credentialRemovals=0")
            } finally {
                runBlocking(executor) { session.disconnect() }
                scope.cancel()
                executor.close()
                proxy?.close()
            }
        }
    }

    @Test fun tvClientHasNoHeartbeat() {
        val client = TvTrust("fake-pin", false).client()
        try {
            assertEquals(0, client.pingIntervalMillis)
            assertFalse(client.retryOnConnectionFailure)
            println("DIAG pingIntervalMillis=${client.pingIntervalMillis} retryOnConnectionFailure=${client.retryOnConnectionFailure}")
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
