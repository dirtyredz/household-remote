package com.dirtyredz.householdremote.remote

import com.dirtyredz.householdremote.shortcuts.ShortcutProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class KeyDispatcher(
    private val scope: CoroutineScope,
    private val send: (String) -> Boolean,
    private val status: (String) -> Unit
) {
    private var channel: Channel<String>? = null
    private var worker: Job? = null
    private var sequence: Job? = null
    private var generation = 0
    val busy: Boolean get() = sequence?.isActive == true
    fun start() {
        cancel()
        startQueue()
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
}
