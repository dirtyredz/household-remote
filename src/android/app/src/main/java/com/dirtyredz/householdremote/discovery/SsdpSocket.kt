package com.dirtyredz.householdremote.discovery

import android.content.Context
import android.net.ConnectivityManager
import com.dirtyredz.householdremote.network.phoneWifiNetwork
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

data class SsdpReply(val ip: String, val text: String)

/** One M-SEARCH exchange: send the query, read replies until closed. */
interface SsdpSocket : AutoCloseable {
    fun send(payload: ByteArray)

    /** Next reply, or null when [timeoutMs] passes with none. */
    fun receive(timeoutMs: Int): SsdpReply?
}

/**
 * Socket pinned to the phone's Wi-Fi network. With mobile data active (or a VPN) an unbound socket sends the
 * multicast out the wrong interface, which looks exactly like "no TVs found".
 */
class AndroidSsdpSocket(context: Context) : SsdpSocket {
    private val socket = MulticastSocket(null as InetSocketAddress?)

    init {
        val connectivity = context.applicationContext.getSystemService(
            ConnectivityManager::class.java
        )
        val wifi = connectivity.phoneWifiNetwork()
        socket.reuseAddress = true
        wifi?.bindSocket(socket)
        socket.bind(InetSocketAddress(0))
        val iface = wifi?.let { connectivity.getLinkProperties(it)?.interfaceName }
        if (iface != null) {
            try {
                socket.networkInterface = NetworkInterface.getByName(iface)
            } catch (
                _: Exception
            ) { /* bindSocket already routes it; interface pin is best effort */ }
        }
    }

    override fun send(payload: ByteArray) {
        socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName(GROUP), PORT))
    }

    override fun receive(timeoutMs: Int): SsdpReply? {
        socket.soTimeout = timeoutMs
        val packet = DatagramPacket(ByteArray(8192), 8192)
        return try {
            socket.receive(packet)
            val address = packet.address as? Inet4Address ?: return null
            SsdpReply(address.hostAddress.orEmpty(), String(packet.data, 0, packet.length))
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    override fun close() = socket.close()

    companion object {
        const val GROUP = "239.255.255.250"
        const val PORT = 1900
    }
}
