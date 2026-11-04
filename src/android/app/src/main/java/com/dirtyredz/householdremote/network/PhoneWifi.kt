package com.dirtyredz.householdremote.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** What the resolver needs to know about one network; [K] is the platform handle (plain values in tests). */
data class WifiCandidate<K>(val network: K, val wifi: Boolean, val vpn: Boolean)

/**
 * The one rule for "the phone's Wi-Fi network" used to pin LAN sockets (SSDP, Wake-on-LAN): a Wi-Fi network
 * that is not a VPN, preferring the [active] (default) one when it qualifies, else the first such network.
 */
fun <K> pickWifi(candidates: List<WifiCandidate<K>>, active: K?): K? {
    val usable = candidates.filter { it.wifi && !it.vpn }
    return (usable.firstOrNull { it.network == active } ?: usable.firstOrNull())?.network
}

/** Resolves [pickWifi] over the live connectivity state, or null when the phone has no such network. */
fun ConnectivityManager.phoneWifiNetwork(): Network? {
    val candidates = allNetworks.mapNotNull { network ->
        getNetworkCapabilities(network)?.let {
            WifiCandidate(
                network,
                wifi = it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                vpn = it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            )
        }
    }
    return pickWifi(candidates, activeNetwork)
}
