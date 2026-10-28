package com.dirtyredz.householdremote.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper

/** Whether the phone has an active Wi-Fi network, reported only while started (foreground). */
interface WifiMonitor {
    /**
     * Starts watching. [changed] runs on the main thread: once immediately with the current
     * value, then on every change until [stop]. A repeated start replaces the listener.
     */
    fun start(changed: (Boolean) -> Unit)

    fun stop()
}

/** [WifiMonitor] over ConnectivityManager callbacks for any Wi-Fi network, not just the default. */
class AndroidWifiMonitor(context: Context) : WifiMonitor {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val networks = WifiNetworks<Network>()
    private var listener: ((Boolean) -> Unit)? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun start(changed: (Boolean) -> Unit) {
        stop()
        listener = changed
        // First guess from the default network; the callback below corrects it within moments.
        // A VPN is skipped: the request below never reports it, so it could never be lost.
        connectivity.activeNetwork?.let { active ->
            val caps = connectivity.getNetworkCapabilities(active)
            val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            networks.update(active, !vpn && caps.isWifi())
        }
        val next = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (networks.update(network, caps.isWifi())) report()
            }

            override fun onLost(network: Network) {
                if (networks.lost(network)) report()
            }
        }
        // Remove the default INTERNET requirement: a LAN-only Wi-Fi still reaches the TVs.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivity.registerNetworkCallback(request, next, main)
        callback = next
        changed(networks.onWifi)
    }

    override fun stop() {
        callback?.let { connectivity.unregisterNetworkCallback(it) }
        callback = null
        listener = null
        networks.clear()
    }

    private fun report() {
        listener?.invoke(networks.onWifi)
    }

    private fun NetworkCapabilities?.isWifi(): Boolean =
        this?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
}
