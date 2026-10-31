package com.dirtyredz.householdremote.remote

import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.security.Credential
import com.dirtyredz.householdremote.security.Credentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single owner of the selected TV's socket and its foreground recovery.
 *
 * An explicit [connect] or [disconnect] starts a new owner generation; each socket attempt
 * has its own id. Every callback, timer and retry checks the identity it was created for, so
 * stale work after a replacement is inert. A transient drop retries with [SessionTiming]
 * backoff, one attempt at a time, inside a bounded window; trust, pairing and protocol
 * failures never retry. Input is cancelled on every teardown and never replayed here.
 * While the phone has no Wi-Fi ([networkChanged] false) nothing is attempted: the target is
 * parked as Unavailable, and one fresh recovery window starts when Wi-Fi returns.
 * All methods and callbacks run on [scope]'s (serialized) dispatcher.
 */
class RemoteSession(
    private val scope: CoroutineScope,
    private val credentials: Credentials,
    private val status: (ConnectionStatus) -> Unit,
    private val cancelled: () -> Unit,
    private val timing: SessionTiming = SessionTiming(),
    private val imeChanged: (Boolean) -> Unit = {},
    private val transport: TvTransport = OkHttpTvTransport(timing.pingIntervalMs)
) {
    private data class Target(val tv: Tv, val port: Int)

    private var target: Target? = null
    private var owner = 0
    private var attempt = 0
    private var socket: TransportSocket? = null
    private var pairing = false
    val messages = TvMessageLog()
    private var pendingImeStart = false
    private var ready = false
    private var recover = true
    private var failures = 0
    private var token = ""
    private var network = true
    private var parked = false
    private var attemptTimer: Job? = null
    private var stableTimer: Job? = null
    private var retry: Job? = null
    private var window: Job? = null
    var state = ConnectionState.Offline
        private set

    /** Explicit (re)connect: replaces any session, attempt or recovery for a fresh one. */
    fun connect(tv: Tv, pairing: Boolean = false, port: Int = DEFAULT_PORT) {
        stopAll()
        target = Target(tv, port)
        messages.selectTv(tv.mac, listOf(tv.name, tv.mac, tv.ip))
        recover = true
        failures = 0
        // No Wi-Fi: remember the TV, but never start pairing (it discards saved credentials).
        if (!network) return park()
        open(pairing)
    }

    /**
     * Foreground start. Idempotent while the same TV is connected or being connected, so a
     * repeated start never restarts input.
     */
    fun ensureConnected(tv: Tv, port: Int = DEFAULT_PORT) {
        if (target == Target(tv, port) && (state in active || parked)) return
        connect(tv, port = port)
    }

    /**
     * Starts a fresh bounded window after recovery ran out. Returns false (and does nothing)
     * otherwise, so repeated taps can neither add attempts nor extend a running window.
     */
    fun resumeRecovery(): Boolean {
        if (state != ConnectionState.Unavailable || target == null || !network) return false
        failures = 0
        beginRecovery(firstWaitMs = 0)
        return true
    }

    /**
     * The phone's Wi-Fi came or went. Losing it tears down any attempt or recovery for the
     * target and parks it; regaining it starts one fresh recovery window for a parked target.
     * Terminal Offline states (pairing problems, the drop after standby) and a running
     * pairing approval are left alone.
     */
    fun networkChanged(available: Boolean) {
        if (available == network) return
        network = available
        if (!available) {
            val parkable = state != ConnectionState.Offline &&
                state != ConnectionState.AwaitingApproval
            if (target != null && parkable) park()
        } else if (parked) {
            parked = false
            failures = 0
            beginRecovery(firstWaitMs = 0)
        }
    }

    /** The next drop is terminal: standby is deliberate, and recovery must not fight it. */
    fun suppressRecovery() {
        recover = false
    }

    /** False when not connected or when the socket refused the frame (which starts recovery). */
    fun send(message: String): Boolean {
        val open = socket
        if (!ready || open == null) return false
        messages.outgoing(message)
        if (open.send(message)) return true
        ready = false
        val id = attempt
        scope.launch { dropped(id, ConnectionFailure.Transport) }
        return false
    }

    /** Explicit disconnect; reports Offline once when a session was active or in progress. */
    fun disconnect() {
        stopAll()
        target = null
        if (state !=
            ConnectionState.Offline
        ) {
            emit(ConnectionState.Offline, ConnectionStatus.DISCONNECTED)
        }
    }

    private fun open(pairingAttempt: Boolean) {
        val tv = target?.tv ?: return
        val port = target?.port ?: return
        messages.beginAttempt(tv.mac, listOf(tv.name, tv.mac, tv.ip))
        val id = ++attempt
        pairing = pairingAttempt
        // Credentials are re-read on every attempt, so a refreshed token is always used.
        val saved = try {
            if (pairingAttempt) null else credentials.get(tv.mac)
        } catch (_: Exception) {
            return terminal(ConnectionFailure.CredentialsUnavailable)
        }
        if (!pairingAttempt && saved == null) return terminal(ConnectionFailure.PairingRequired)
        if (pairingAttempt) credentials.remove(tv.mac)
        token = saved?.token.orEmpty()
        when {
            pairingAttempt -> emit(
                ConnectionState.AwaitingApproval,
                "Approve TV Tuneup on TV within 90 seconds"
            )

            window == null -> emit(ConnectionState.Connecting, "Connecting")

            else -> Unit // still Reconnecting; the attempt number was reported when scheduled
        }
        val expired = if (pairingAttempt) {
            ConnectionFailure.ApprovalTimeout
        } else {
            ConnectionFailure.Transport
        }
        attemptTimer = scope.launch {
            delay(if (pairingAttempt) timing.approvalMs else timing.attemptTimeoutMs)
            dropped(id, expired)
        }
        socket = transport.open(
            SamsungProtocol.url(tv.ip, token, port),
            saved?.certificate,
            pairingAttempt,
            object : TransportEvents {
                override fun message(text: String) {
                    scope.launch { if (id == attempt) received(id, tv, text) }
                }
                override fun failed(failure: ConnectionFailure) {
                    scope.launch { dropped(id, failure) }
                }
                override fun closed() {
                    scope.launch { dropped(id, ConnectionFailure.Transport) }
                }
            }
        )
    }

    private fun received(id: Int, tv: Tv, text: String) {
        messages.incoming(text)
        try {
            val (event, refreshed) = SamsungProtocol.event(text)
            if (event?.startsWith("ms.remote.ime") == true) {
                when (event) {
                    "ms.remote.imeStart" -> if (ready) imeChanged(true) else pendingImeStart = true

                    "ms.remote.imeEnd" -> {
                        pendingImeStart = false
                        imeChanged(false)
                    }
                }
                return
            }
            when (event) {
                "ms.channel.connect" -> {
                    val pin = socket?.observedPin ?: error("Missing certificate")
                    token = refreshed ?: token
                    credentials.put(tv.mac, Credential(token, pin))
                    // A repeated connect event on a live socket only refreshes the token.
                    if (!ready) established()
                }

                "ms.channel.unauthorized" -> dropped(id, ConnectionFailure.Denied)
            }
        } catch (_: Exception) {
            dropped(id, ConnectionFailure.ProtocolError)
        }
    }

    private fun established() {
        attemptTimer?.cancel()
        attemptTimer = null
        window?.cancel()
        window = null
        ready = true
        stableTimer = scope.launch {
            delay(timing.stableMs)
            failures = 0
        }
        emit(ConnectionState.Connected, "Connected")
        if (pendingImeStart) {
            pendingImeStart = false
            imeChanged(true)
        }
    }

    private fun dropped(id: Int, failure: ConnectionFailure) {
        if (id != attempt) return
        val wasPairing = pairing
        teardownAttempt()
        when {
            failure != ConnectionFailure.Transport -> terminal(failure)
            wasPairing -> terminal(null, ConnectionStatus.PAIRING_FAILED)
            !recover -> terminal(null, ConnectionStatus.AFTER_STANDBY)
            !network -> park()
            else -> beginRecovery(timing.backoff(failures))
        }
    }

    private fun beginRecovery(firstWaitMs: Long) {
        if (window == null) {
            val epoch = owner
            window = scope.launch {
                delay(timing.recoveryWindowMs)
                if (epoch == owner) exhausted()
            }
        }
        failures++
        emit(ConnectionState.Reconnecting, ConnectionStatus.RECONNECTING, attemptNumber = failures)
        val epoch = owner
        retry = scope.launch {
            delay(firstWaitMs)
            if (epoch == owner) {
                retry = null
                open(false)
            }
        }
    }

    /** Holds the target with no attempt, timer or window until Wi-Fi returns. */
    private fun park() {
        owner++
        stopRecovery()
        teardownAttempt()
        parked = true
        emit(ConnectionState.Unavailable, ConnectionStatus.NO_WIFI)
    }

    private fun exhausted() {
        window = null
        retry?.cancel()
        retry = null
        teardownAttempt()
        emit(ConnectionState.Unavailable, ConnectionStatus.UNAVAILABLE)
    }

    /** Ends recovery; callers have already torn the attempt down (or never opened one). */
    private fun terminal(
        failure: ConnectionFailure?,
        message: String = failure?.message.orEmpty()
    ) {
        stopRecovery()
        emit(ConnectionState.Offline, message, failure)
    }

    private fun emit(
        next: ConnectionState,
        message: String,
        failure: ConnectionFailure? = null,
        attemptNumber: Int = 0
    ) {
        state = next
        status(ConnectionStatus(next, message, failure, attemptNumber))
    }

    private fun stopRecovery() {
        window?.cancel()
        window = null
        retry?.cancel()
        retry = null
    }

    private fun stopAll() {
        owner++
        parked = false
        stopRecovery()
        teardownAttempt()
    }

    private fun teardownAttempt() {
        pendingImeStart = false
        imeChanged(false)
        attempt++
        ready = false
        attemptTimer?.cancel()
        attemptTimer = null
        stableTimer?.cancel()
        stableTimer = null
        val open = socket
        socket = null
        cancelled()
        open?.cancel()
    }

    companion object {
        const val DEFAULT_PORT = 8002
        private val active = setOf(
            ConnectionState.Connected,
            ConnectionState.Connecting,
            ConnectionState.AwaitingApproval,
            ConnectionState.Reconnecting
        )
    }
}
