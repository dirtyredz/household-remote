package com.dirtyredz.householdremote.network

/**
 * Pure bookkeeping behind [AndroidWifiMonitor]: which networks currently carry Wi-Fi.
 * The phone is "on Wi-Fi" while at least one tracked network has the Wi-Fi transport,
 * whether or not that network has (validated) internet, because the TVs are on the LAN.
 * [K] is the platform network handle; tests use plain values.
 */
class WifiNetworks<K> {
    private val wifi = mutableSetOf<K>()

    val onWifi: Boolean get() = wifi.isNotEmpty()

    /** A network appeared or its capabilities changed. Returns whether [onWifi] changed. */
    fun update(network: K, hasWifiTransport: Boolean): Boolean {
        val before = onWifi
        if (hasWifiTransport) wifi += network else wifi -= network
        return before != onWifi
    }

    /** A network went away. Returns whether [onWifi] changed. */
    fun lost(network: K): Boolean {
        val before = onWifi
        wifi -= network
        return before != onWifi
    }

    fun clear() = wifi.clear()
}
