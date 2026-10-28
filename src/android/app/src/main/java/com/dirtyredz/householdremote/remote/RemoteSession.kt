package com.dirtyredz.householdremote.remote

import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.security.Credential
import com.dirtyredz.householdremote.security.Credentials
import com.dirtyredz.householdremote.security.TvTrust
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class RemoteSession(
    private val scope: CoroutineScope,
    private val credentials: Credentials,
    private val status: (ConnectionStatus) -> Unit,
    private val cancelled: () -> Unit
) {
    private var generation = 0
    private var socket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var timeout: Job? = null
    private var ready = false
    private var state = ConnectionState.Offline
    fun connect(
        tv: Tv,
        pairing: Boolean = false,
        port: Int = 8002,
        approvalMs: Long = SamsungProtocol.APPROVAL_MS
    ) {
        teardown()
        val epoch = generation
        val saved = try {
            if (pairing) null else credentials.get(tv.mac)
        } catch (
            _: Exception
        ) {
            emit(ConnectionState.Offline, "Credentials unavailable; re-pair required")
            return
        }
        if (!pairing && saved == null) {
            emit(ConnectionState.Offline, "Pair while watching this TV")
            return
        }
        if (pairing) credentials.remove(tv.mac)
        var currentToken = saved?.token.orEmpty()
        val trust = TvTrust(if (pairing) null else saved?.certificate, pairing)
        client = trust.client()
        if (pairing) {
            emit(ConnectionState.AwaitingApproval, "Approve TV Tuneup on TV within 90 seconds")
        } else {
            emit(ConnectionState.Connecting, "Connecting")
        }
        timeout =
            scope.launch {
                delay(approvalMs)
                if (epoch == generation &&
                    !ready
                ) {
                    finish("Approval timed out; reconnect manually")
                }
            }
        socket =
            client!!.newWebSocket(
                Request.Builder().url(
                    SamsungProtocol.url(tv.ip, if (pairing) "" else saved?.token.orEmpty(), port)
                ).build(),
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        scope.launch {
                            if (epoch != generation) return@launch
                            try {
                                val (event, token) = SamsungProtocol.event(text)
                                when (event) {
                                    "ms.channel.connect" -> {
                                        val pin = trust.observed ?: error("Missing certificate")
                                        val refreshed = token ?: currentToken
                                        credentials.put(
                                            tv.mac,
                                            Credential(
                                                refreshed,
                                                pin
                                            )
                                        )
                                        currentToken = refreshed
                                        timeout?.cancel()
                                        ready = true
                                        emit(ConnectionState.Connected, "Connected")
                                    }

                                    "ms.channel.unauthorized" -> finish(
                                        "Denied; approve or explicitly re-pair"
                                    )
                                }
                            } catch (
                                _: Exception
                            ) {
                                finish("Invalid TV response or credential storage failure")
                            }
                        }
                    }
                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {
                        scope.launch {
                            if (epoch ==
                                generation
                            ) {
                                finish(
                                    "Connection failed; check TV or re-pair if certificate changed"
                                )
                            }
                        }
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        scope.launch {
                            if (epoch ==
                                generation
                            ) {
                                finish("Disconnected")
                            }
                        }
                    }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, null)
                    }
                }
            )
    }
    fun send(message: String): Boolean = ready && socket?.send(message) == true
    private fun emit(next: ConnectionState, message: String) {
        state = next
        status(ConnectionStatus(next, message))
    }
    private fun finish(message: String) {
        teardown()
        emit(ConnectionState.Offline, message)
    }

    /** Explicit disconnect; reports Offline once when a session was active or in progress. */
    fun disconnect() {
        teardown()
        if (state != ConnectionState.Offline) emit(ConnectionState.Offline, "Disconnected")
    }
    private fun teardown() {
        generation++
        ready = false
        timeout?.cancel()
        timeout = null
        cancelled()
        socket?.cancel()
        socket = null
        client?.connectionPool?.evictAll()
        client?.dispatcher?.executorService?.shutdown()
        client =
            null
    }
}
