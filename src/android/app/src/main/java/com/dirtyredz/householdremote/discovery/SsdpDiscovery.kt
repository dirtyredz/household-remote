package com.dirtyredz.householdremote.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.dirtyredz.householdremote.inventory.HouseholdImport
import com.dirtyredz.householdremote.inventory.Tv
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SsdpDiscovery(context: Context, private val info: TvProbe) {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    suspend fun discover(): List<Tv> = withContext(Dispatchers.IO) {
        val lock = wifi.createMulticastLock("household-discovery").apply {
            setReferenceCounted(false)
        }
        val candidates = linkedSetOf<String>()
        try {
            lock.acquire()
            DatagramSocket().use { socket ->
                socket.soTimeout = 300
                val query = (
                    "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                        "ST: urn:samsung.com:device:RemoteControlReceiver:1\r\n\r\n"
                    ).toByteArray()
                socket.send(
                    DatagramPacket(
                        query,
                        query.size,
                        InetAddress.getByName("239.255.255.250"),
                        1900
                    )
                )
                val deadline = System.nanoTime() + 3_000_000_000L
                while (System.nanoTime() < deadline && candidates.size < 16) {
                    currentCoroutineContext().ensureActive()
                    val packet = DatagramPacket(ByteArray(8192), 8192)
                    try {
                        socket.receive(packet)
                        val ip = packet.address.hostAddress.orEmpty()
                        if (HouseholdImport.privateIp(ip)) candidates += ip
                    } catch (_: SocketTimeoutException) { /* bounded receive */ }
                }
            }
        } finally {
            if (lock.isHeld) lock.release()
        }
        val result = mutableListOf<Tv>()
        for (ip in candidates) {
            currentCoroutineContext().ensureActive()
            try {
                result += info.probe(ip)
            } catch (
                error: Exception
            ) {
                currentCoroutineContext().ensureActive()
            }
        }
        result.distinctBy { it.mac }
    }
}
