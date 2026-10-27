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
    private val status: (String, Boolean) -> Unit,
    private val cancelled: () -> Unit
) {
    private var generation = 0
    private var socket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var timeout: Job? = null
    private var ready = false
    fun connect(
        tv: Tv,
        pairing: Boolean = false,
        port: Int = 8002,
        approvalMs: Long = SamsungProtocol.APPROVAL_MS
    ) {
        disconnect()
        val epoch = generation
        val saved = try {
            if (pairing) null else credentials.get(tv.mac)
        } catch (
            _: Exception
        ) {
            status("Credentials unavailable; re-pair required", false)
            return
        }
        if (!pairing && saved == null) {
            status("Pair while watching this TV", false)
            return
        }
        if (pairing) credentials.remove(tv.mac)
        var currentToken = saved?.token.orEmpty()
        val trust = TvTrust(if (pairing) null else saved?.certificate, pairing)
        client = trust.client()
        status(if (pairing) "Approve TV Tuneup on TV within 90 seconds" else "Connecting", false)
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
                                        status("Connected", true)
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
    private fun finish(message: String) {
        disconnect()
        status(message, false)
    }
    fun disconnect() {
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
