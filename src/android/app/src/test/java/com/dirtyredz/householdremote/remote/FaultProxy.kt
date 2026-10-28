package com.dirtyredz.householdremote.remote

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Loopback TCP forwarder in front of a fake TV. [drop] severs every live connection (an
 * abrupt network cut); [freeze] keeps TCP open but swallows bytes, a silent half-open peer
 * that only a heartbeat can detect. [thaw] closes the frozen connections and forwards again.
 */
class FaultProxy(private val targetPort: Int) : AutoCloseable {
    private val listener = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
    private val sockets = CopyOnWriteArrayList<Socket>()
    private val threads = Executors.newCachedThreadPool()

    @Volatile private var frozen = false
    val port: Int get() = listener.localPort

    init {
        threads.submit {
            try {
                while (!listener.isClosed) {
                    val incoming = listener.accept()
                    val outgoing = Socket("127.0.0.1", targetPort)
                    sockets += incoming
                    sockets += outgoing
                    pump(incoming, outgoing)
                    pump(outgoing, incoming)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun pump(from: Socket, to: Socket) = threads.submit {
        val buffer = ByteArray(8192)
        try {
            val input = from.getInputStream()
            val output = to.getOutputStream()
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                // A frozen pipe swallows bytes: pings never reach the peer and no pong returns.
                if (!frozen) {
                    output.write(buffer, 0, read)
                    output.flush()
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { to.close() }
        }
    }

    fun drop() {
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
    }

    fun freeze() {
        frozen = true
    }

    fun thaw() {
        drop()
        frozen = false
    }

    override fun close() {
        listener.close()
        drop()
        threads.shutdownNow()
        check(threads.awaitTermination(5, TimeUnit.SECONDS))
    }
}
