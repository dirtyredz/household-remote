package com.dirtyredz.householdremote

import com.dirtyredz.householdremote.discovery.TvInfoClient
import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.remote.ConnectionFailure
import com.dirtyredz.householdremote.remote.ConnectionState
import com.dirtyredz.householdremote.remote.RemoteSession
import com.dirtyredz.householdremote.remote.SamsungProtocol
import com.dirtyredz.householdremote.remote.SessionTiming
import com.dirtyredz.householdremote.security.Credential
import com.dirtyredz.householdremote.security.Credentials
import com.dirtyredz.householdremote.security.TvTrust
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SessionTest {
    private class Memory : Credentials {
        var value: Credential? = null
        override fun get(identity: String) = value
        override fun put(identity: String, credential: Credential) {
            value = credential
        }
        override fun remove(identity: String) {
            value = null
        }
    }
    private val tv = Tv("fake", "Fake", "test", "AA:BB:CC:DD:EE:FF", "127.0.0.1")

    @Test fun fakeRestParsesIdentity() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse.Builder().body(
                    """{"device":{"name":"Fake","wifiMac":"AA:BB:CC:DD:EE:FF"}}"""
                ).build()
            )
            server.start()
            val info = TvInfoClient()
            try {
                assertEquals(tv.mac, info.probe("127.0.0.1", server.port, true).mac)
            } finally {
                info.close()
            }
            assertEquals("/api/v2/", server.takeRequest().url.encodedPath)
        }
    }

    @Test fun selfSignedPairTokenRefreshReconnectAndDenial() {
        val certificate = HeldCertificate.Builder().commonName("Fake TV").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory())
            val received = LinkedBlockingQueue<String>()
            fun response(token: String) =
                MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(
                            """{"event":"ms.channel.connect","data":{"token":"$token"}}"""
                        )
                        if (token == "first") {
                            webSocket.send("""{"event":"ms.channel.connect","data":{}}""")
                        }
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        received.add(text)
                    }
                }).build()
            server.enqueue(response("first"))
            server.enqueue(response("refreshed"))
            server.enqueue(
                MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("""{"event":"ms.channel.unauthorized","data":{}}""")
                    }
                }).build()
            )
            server.start()
            val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            val store = Memory()
            val events = LinkedBlockingQueue<String>()
            val states = LinkedBlockingQueue<ConnectionState>()
            val session = RemoteSession(scope, store, { status ->
                states.add(status.state)
                events.add(status.message)
            }, {})
            fun <T> onSession(block: () -> T) = runBlocking(dispatcher) { block() }
            fun awaitStatus(expected: String) {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (System.nanoTime() <
                    deadline
                ) {
                    if (events.poll(100, TimeUnit.MILLISECONDS)?.contains(expected) ==
                        true
                    ) {
                        return
                    }
                }
                fail("Missing status: $expected")
            }
            try {
                onSession { session.connect(tv, true, server.port) }
                awaitStatus("Connected")
                assertEquals("first", store.value!!.token)
                assertTrue(onSession { session.send(SamsungProtocol.key("KEY_HOME")) })
                assertEquals(SamsungProtocol.key("KEY_HOME"), received.poll(3, TimeUnit.SECONDS))
                onSession { session.disconnect() }
                assertFalse(onSession { session.send(SamsungProtocol.key("KEY_UP")) })
                onSession { session.connect(tv, false, server.port) }
                awaitStatus("Connected")
                assertEquals("refreshed", store.value!!.token)
                assertNull(received.poll(100, TimeUnit.MILLISECONDS))
                server.takeRequest()
                assertEquals("first", server.takeRequest().url.queryParameter("token"))
                onSession { session.connect(tv, false, server.port) }
                awaitStatus("Denied")
                assertFalse(onSession { session.send(SamsungProtocol.key("KEY_HOME")) })
                onSession { session.disconnect() }
                assertEquals(
                    "Typed transitions; a repeated connect event on a live socket is not " +
                        "re-reported; explicit disconnect reports Offline once",
                    listOf(
                        ConnectionState.AwaitingApproval,
                        ConnectionState.Connected,
                        ConnectionState.Offline,
                        ConnectionState.Connecting,
                        ConnectionState.Connected,
                        ConnectionState.Connecting,
                        ConnectionState.Offline
                    ),
                    states.toList()
                )
            } finally {
                onSession { session.disconnect() }
                scope.cancel()
                dispatcher.close()
            }
        }
    }

    @Test fun changedCertificateAndImplicitFirstTrustFailClosed() {
        val first = HeldCertificate.Builder().commonName("one").build().certificate
        val second = HeldCertificate.Builder().commonName("two").build().certificate
        val trust = TvTrust(null, true)
        trust.checkServerTrusted(arrayOf(first), "RSA")
        val pinned = TvTrust(trust.observed, false)
        pinned.checkServerTrusted(arrayOf(first), "RSA")
        assertThrows(Exception::class.java) { pinned.checkServerTrusted(arrayOf(second), "RSA") }
        assertThrows(Exception::class.java) {
            TvTrust(null, false).checkServerTrusted(arrayOf(first), "RSA")
        }
    }

    @Test fun changedCertificateIsRejectedByRealWssHandshake() {
        val oldCertificate = HeldCertificate.Builder().commonName("Old TV").build().certificate
        val observed = TvTrust(null, true).apply {
            checkServerTrusted(arrayOf(oldCertificate), "RSA")
        }.observed!!
        val changed = HeldCertificate.Builder().commonName("Changed TV").build()
        MockWebServer().use { server ->
            server.useHttps(
                HandshakeCertificates.Builder().heldCertificate(changed).build().sslSocketFactory()
            )
            server.enqueue(
                MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {}).build()
            )
            server.start()
            val store = Memory().apply { value = Credential("old-token", observed) }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val failed = LinkedBlockingQueue<ConnectionFailure>()
            val states = LinkedBlockingQueue<ConnectionState>()
            val session =
                RemoteSession(scope, store, { status ->
                    states.add(status.state)
                    status.failure?.let(failed::add)
                }, {}, SessionTiming(backoffMs = listOf(50)))
            try {
                session.connect(tv, false, server.port)
                assertEquals(ConnectionFailure.TrustChanged, failed.poll(5, TimeUnit.SECONDS))
                Thread.sleep(500)
                assertEquals(
                    "a changed pin is terminal: never retried, never re-paired",
                    listOf(ConnectionState.Connecting, ConnectionState.Offline),
                    states.toList()
                )
                assertEquals(observed, store.value!!.certificate)
                assertEquals("old-token", store.value!!.token)
                assertFalse(session.send(SamsungProtocol.key("KEY_HOME")))
            } finally {
                session.disconnect()
                scope.cancel()
            }
        }
    }

    @Test fun approvalTimeoutDiscardsInputAndDoesNotPersistTrust() {
        val certificate = HeldCertificate.Builder().commonName("Silent TV").build()
        MockWebServer().use { server ->
            server.useHttps(
                HandshakeCertificates.Builder().heldCertificate(
                    certificate
                ).build().sslSocketFactory()
            )
            server.enqueue(
                MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {}).build()
            )
            server.start()
            val store = Memory()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val timedOut = LinkedBlockingQueue<String>()
            val states = LinkedBlockingQueue<ConnectionState>()
            var cancelled = 0
            val session =
                RemoteSession(scope, store, { status ->
                    states.add(status.state)
                    if (status.message.startsWith(
                            "Approval timed out"
                        )
                    ) {
                        timedOut.add(status.message)
                    }
                }, { cancelled++ }, SessionTiming(approvalMs = 300))
            try {
                session.connect(tv, true, server.port)
                assertNotNull(timedOut.poll(5, TimeUnit.SECONDS))
                assertEquals(
                    listOf(ConnectionState.AwaitingApproval, ConnectionState.Offline),
                    states.toList()
                )
                assertNull(store.value)
                assertTrue(cancelled >= 2)
                assertFalse(session.send(SamsungProtocol.key("KEY_POWER")))
            } finally {
                session.disconnect()
                scope.cancel()
            }
        }
    }
}
