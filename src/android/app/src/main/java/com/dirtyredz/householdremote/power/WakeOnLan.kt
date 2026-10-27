package com.dirtyredz.householdremote.power

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.dirtyredz.householdremote.discovery.TvProbe
import com.dirtyredz.householdremote.inventory.Tv
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object WakePacket {
    fun bytes(mac: String): ByteArray {
        require(mac.matches(Regex("(?i)([a-f0-9]{2}:){5}[a-f0-9]{2}")))
        val address = mac.split(':').map { it.toInt(16).toByte() }.toByteArray()
        return ByteArray(102) { if (it < 6) 0xff.toByte() else address[(it - 6) % 6] }
    }
}

interface TvWake {
    suspend fun wake(mac: String)
}

interface AwakeClock {
    fun nowMillis(): Long
    suspend fun waitMillis(duration: Long)
}

object MonotonicAwakeClock : AwakeClock {
    override fun nowMillis(): Long = System.nanoTime() / 1_000_000
    override suspend fun waitMillis(duration: Long) = delay(duration)
}

suspend fun awaitAwake(
    tv: Tv,
    probe: TvProbe,
    timeoutMs: Long,
    intervalMs: Long,
    clock: AwakeClock = MonotonicAwakeClock
): Boolean {
    require(timeoutMs > 0 && intervalMs > 0)
    val started = clock.nowMillis()
    while (true) {
        currentCoroutineContext().ensureActive()
        val elapsed = clock.nowMillis() - started
        if (elapsed >= timeoutMs) return false
        clock.waitMillis(minOf(intervalMs, timeoutMs - elapsed))
        if (clock.nowMillis() - started >= timeoutMs) return false
        val found = try {
            probe.probe(tv.ip)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (found?.mac == tv.mac) return true
    }
}

class WakeOnLan(context: Context) : TvWake {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    override suspend fun wake(mac: String) = withContext(Dispatchers.IO) {
        val network = connectivity.activeNetwork ?: error("Connect to Wi-Fi first")
        check(
            connectivity.getNetworkCapabilities(
                network
            )?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ==
                true
        ) { "Wake requires active Wi-Fi" }
        val link = connectivity.getLinkProperties(network) ?: error("No LAN interface")
        val iface = NetworkInterface.getByName(link.interfaceName) ?: error("No LAN interface")
        val address =
            iface.interfaceAddresses.firstOrNull {
                it.address is Inet4Address &&
                    it.broadcast != null
            }
                ?: error("No IPv4 broadcast on Wi-Fi")
        val packet = WakePacket.bytes(mac)
        DatagramSocket(null).use { socket ->
            network.bindSocket(socket)
            socket.bind(java.net.InetSocketAddress(address.address, 0))
            socket.broadcast = true
            repeat(3) {
                socket.send(
                    DatagramPacket(packet, packet.size, address.broadcast as InetAddress, 9)
                )
                delay(150)
            }
        }
    }
}
