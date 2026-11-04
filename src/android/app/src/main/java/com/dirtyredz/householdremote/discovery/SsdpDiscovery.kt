package com.dirtyredz.householdremote.discovery

import android.content.Context
import com.dirtyredz.householdremote.inventory.HouseholdImport
import com.dirtyredz.householdremote.inventory.Tv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Finds Samsung TVs by SSDP, then confirms each with [info] (:8001/api/v2/). Mirrors the LAN-proven Node
 * reference in tv-tuneup (`ssdp.mjs`): query `ssdp:all`, keep replies that mention Samsung, resend once
 * because UDP is lossy. The multicast lock is held from before the first send until the socket is closed.
 */
class SsdpDiscovery(
    private val info: TvProbe,
    private val lock: () -> MulticastLockHandle,
    private val openSocket: () -> SsdpSocket,
    private val windowMs: Long = 4_000,
    private val resendAfterMs: Long = 700
) {
    constructor(context: Context, info: TvProbe) :
        this(info, { AndroidMulticastLock(context) }, { AndroidSsdpSocket(context) })

    suspend fun discover(): List<Tv> = withContext(Dispatchers.IO) {
        val candidates = linkedSetOf<String>()
        lock().withLock {
            openSocket().use { socket ->
                val start = System.nanoTime()
                fun elapsedMs() = (System.nanoTime() - start) / 1_000_000
                var sends = 0
                while (elapsedMs() < windowMs && candidates.size < MAX_CANDIDATES) {
                    currentCoroutineContext().ensureActive()
                    if (sends == 0 || (sends == 1 && elapsedMs() >= resendAfterMs)) {
                        socket.send(QUERY)
                        sends++
                    }
                    val reply = socket.receive(RECEIVE_SLICE_MS) ?: continue
                    if (isSamsung(reply) &&
                        HouseholdImport.privateIp(reply.ip)
                    ) {
                        candidates += reply.ip
                    }
                }
            }
        }
        val result = mutableListOf<Tv>()
        for (ip in candidates) {
            currentCoroutineContext().ensureActive()
            try {
                result += info.probe(ip)
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
            }
        }
        result.distinctBy { it.mac }
    }

    private fun isSamsung(reply: SsdpReply) = reply.text.contains("samsung", ignoreCase = true)

    private companion object {
        const val MAX_CANDIDATES = 16
        const val RECEIVE_SLICE_MS = 300
        val QUERY = (
            "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n"
            ).toByteArray()
    }
}
