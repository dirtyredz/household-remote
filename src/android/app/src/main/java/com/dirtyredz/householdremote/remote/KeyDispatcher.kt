package com.dirtyredz.householdremote.remote

import com.dirtyredz.householdremote.shortcuts.ShortcutProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serialized input for one usable session. Keys accepted by the socket are never resent;
 * while the session recovers, at most one fresh, never-submitted ordinary key may [hold]
 * for [pendingMs] and is submitted once when the next session [start]s.
 */
class KeyDispatcher(
    private val scope: CoroutineScope,
    private val send: (String) -> Boolean,
    private val status: (String) -> Unit,
    private val pendingMs: Long = PENDING_KEY_MS
) {
    private var channel: Channel<String>? = null
    private var worker: Job? = null
    private var sequence: Job? = null
    private var generation = 0
    private var pending: String? = null
    private var pendingExpiry: Job? = null
    val busy: Boolean get() = sequence?.isActive == true
    val holding: Boolean get() = pending != null

    /** Starts a fresh queue for a new usable session and submits a still-valid held key once. */
    fun start() {
        cancel()
        startQueue()
        val held = pending ?: return
        discardPending()
        channel?.trySend(held)
    }
    private fun startQueue() {
        val queue = Channel<String>(16)
        channel = queue
        worker =
            scope.launch {
                for (key in queue) {
                    if (!send(SamsungProtocol.key(key))) {
                        status("Disconnected; input discarded")
                        cancel()
                        break
                    }
                }
            }
    }
    fun tap(key: String): Boolean = !busy && (channel?.trySend(key)?.isSuccess == true)

    /**
     * Holds one ordinary key while no session is usable. Rejects KEY_POWER, a second key, and
     * any key while a queue or shortcut is live. The key expires (with a visible message) if
     * no session starts within [pendingMs].
     */
    fun hold(key: String): Boolean {
        if (key == POWER || busy || channel != null || pending != null) return false
        require(key in SamsungProtocol.keys)
        pending = key
        pendingExpiry = scope.launch {
            delay(pendingMs)
            pending = null
            pendingExpiry = null
            status(KEY_EXPIRED)
        }
        return true
    }

    /** Drops a held key silently: selection, lifecycle, explicit reconnect or a trust failure. */
    fun discardPending() {
        pendingExpiry?.cancel()
        pendingExpiry = null
        pending = null
    }
    fun run(profile: ShortcutProfile) {
        if (busy || channel == null) return
        profile.validate()
        worker?.cancel()
        channel?.cancel()
        channel = null
        val epoch = generation
        sequence = scope.launch {
            try {
                for (step in profile.steps) {
                    if (!send(SamsungProtocol.key(step.key))) {
                        status("Disconnected; sequence cancelled")
                        return@launch
                    }
                    delay(step.delayMs)
                }
                status("Sequence sent")
                startQueue()
            } finally {
                if (epoch == generation) sequence = null
            }
        }
    }

    /**
     * Cancels queued input, any shortcut and a held key, then attempts exactly one KEY_POWER
     * frame. Returns whether the session accepted the frame; never retries. Ordinary input
     * stays stopped until [start] runs for a new usable session.
     */
    fun standby(): Boolean {
        cancel()
        discardPending()
        return send(SamsungProtocol.key(POWER))
    }

    /** Cancels the queue and any shortcut. A held key survives: it belongs to recovery. */
    fun cancel() {
        generation++
        channel?.cancel()
        channel = null
        worker?.cancel()
        worker = null
        sequence?.cancel()
        sequence =
            null
    }

    companion object {
        const val PENDING_KEY_MS = 5_000L
        const val KEY_EXPIRED = "Key not sent; TV still reconnecting"
        private const val POWER = "KEY_POWER"
    }
}
