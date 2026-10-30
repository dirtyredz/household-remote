package com.dirtyredz.householdremote.remote

import com.dirtyredz.householdremote.apps.TvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serialized input for one usable session: key taps, app launches and text/Enter batches share one queue of
 * ready-to-send frames. Frames accepted by the socket are never resent; while the session
 * recovers, at most one fresh, never-submitted ordinary key may [hold] for [pendingMs] and is
 * submitted once when the next session [start]s. App launches and text are never held.
 */
class KeyDispatcher(
    private val scope: CoroutineScope,
    private val send: (String) -> Boolean,
    private val dropped: () -> Unit,
    private val pendingMs: Long = PENDING_KEY_MS
) {
    private var channel: Channel<List<String>>? = null
    private var worker: Job? = null
    private var pending: String? = null
    private var pendingExpiry: Job? = null
    val holding: Boolean get() = pending != null

    /** Starts a fresh queue for a new usable session and submits a still-valid held key once. */
    fun start() {
        cancel()
        startQueue()
        val held = pending ?: return
        discardPending()
        channel?.trySend(listOf(SamsungProtocol.key(held)))
    }
    private fun startQueue() {
        val queue = Channel<List<String>>(16)
        channel = queue
        worker =
            scope.launch {
                for (frames in queue) {
                    if (!frames.all(send)) {
                        dropped()
                        cancel()
                        break
                    }
                }
            }
    }
    fun tap(key: String): Boolean {
        val queue = channel ?: return false
        return queue.trySend(listOf(SamsungProtocol.key(key))).isSuccess
    }

    /**
     * Queues one launch of [app] on the live session. Without one it is refused, not held:
     * opening an app later, after the moment has passed, would surprise whoever pressed it.
     */
    fun launch(app: TvApp): Boolean {
        val queue = channel ?: return false
        return queue.trySend(listOf(SamsungProtocol.launch(app.appId, app.launchType))).isSuccess
    }

    /** One entry: text then Enter, without interleaving or holding through recovery. */
    fun text(text: String): Boolean {
        val queue = channel ?: return false
        return queue.trySend(
            listOf(SamsungProtocol.text(text), SamsungProtocol.key("KEY_ENTER"))
        ).isSuccess
    }

    /**
     * Holds one ordinary key while no session is usable. Rejects KEY_POWER, a second key, and
     * any key while a queue is live. The key expires if no session starts within [pendingMs].
     */
    fun hold(key: String): Boolean {
        if (key == POWER || channel != null || pending != null) return false
        require(key in SamsungProtocol.keys)
        pending = key
        pendingExpiry = scope.launch {
            delay(pendingMs)
            pending = null
            pendingExpiry = null
            dropped()
        }
        return true
    }

    /** Drops a held key silently: selection, lifecycle, explicit reconnect or a trust failure. */
    fun discardPending() {
        pendingExpiry?.cancel()
        pendingExpiry = null
        pending = null
    }

    /**
     * Cancels queued input and a held key, then attempts exactly one KEY_POWER frame. Returns
     * whether the session accepted the frame; never retries. Ordinary input stays stopped
     * until [start] runs for a new usable session.
     */
    fun standby(): Boolean {
        cancel()
        discardPending()
        return send(SamsungProtocol.key(POWER))
    }

    /** Cancels the queue. A held key survives: it belongs to recovery. */
    fun cancel() {
        channel?.cancel()
        channel = null
        worker?.cancel()
        worker = null
    }

    companion object {
        const val PENDING_KEY_MS = 5_000L
        private const val POWER = "KEY_POWER"
    }
}
