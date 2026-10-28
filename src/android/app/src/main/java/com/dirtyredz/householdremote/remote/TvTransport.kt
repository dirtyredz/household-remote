package com.dirtyredz.householdremote.remote

import com.dirtyredz.householdremote.security.TrustRejection
import com.dirtyredz.householdremote.security.TvTrust
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Events of one socket attempt. They may arrive on any thread; the session re-serializes. */
interface TransportEvents {
    fun message(text: String)
    fun failed(failure: ConnectionFailure)
    fun closed()
}

/** One open attempt. */
interface TransportSocket {
    /** Certificate digest the TLS handshake accepted, once known. */
    val observedPin: String?

    /** True when the frame was queued locally; not proof the TV executed it. */
    fun send(text: String): Boolean

    /** Abandons the socket and releases its resources; later events are ignored. */
    fun cancel()
}

/** Opens a TV control socket; the seam lets session tests drive attempts in virtual time. */
fun interface TvTransport {
    fun open(
        url: HttpUrl,
        pinned: String?,
        pairing: Boolean,
        events: TransportEvents
    ): TransportSocket
}

/**
 * Production transport: one isolated, pin-checking OkHttp client per attempt with the
 * protocol ping/pong heartbeat. Certificate refusals are typed so they are never retried.
 */
class OkHttpTvTransport(private val pingIntervalMs: Long) : TvTransport {
    override fun open(
        url: HttpUrl,
        pinned: String?,
        pairing: Boolean,
        events: TransportEvents
    ): TransportSocket {
        val trust = TvTrust(pinned, pairing)
        val client = trust.client(pingIntervalMs)
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) = events.message(text)
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                    events.failed(
                        when (trust.rejection) {
                            TrustRejection.Changed -> ConnectionFailure.TrustChanged
                            TrustRejection.FirstTrustRequired -> ConnectionFailure.PairingRequired
                            null -> ConnectionFailure.Transport
                        }
                    )
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                    events.closed()
            }
        )
        return OkHttpSocket(trust, client, socket)
    }

    private class OkHttpSocket(
        private val trust: TvTrust,
        private val client: OkHttpClient,
        private val socket: WebSocket
    ) : TransportSocket {
        override val observedPin: String? get() = trust.observed
        override fun send(text: String): Boolean = socket.send(text)
        override fun cancel() {
            socket.cancel()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
