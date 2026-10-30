package com.dirtyredz.householdremote.remote

import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class SerialSessionScope : AutoCloseable {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun <T> onSession(block: () -> T): T = runBlocking(dispatcher) { block() }

    override fun close() {
        scope.cancel()
        dispatcher.close()
    }
}
