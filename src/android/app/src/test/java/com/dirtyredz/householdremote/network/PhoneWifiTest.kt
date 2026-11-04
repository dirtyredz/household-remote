package com.dirtyredz.householdremote.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneWifiTest {
    private fun wifi(name: String) = WifiCandidate(name, wifi = true, vpn = false)
    private val cell = WifiCandidate("cell", wifi = false, vpn = false)
    private val vpnOverWifi = WifiCandidate("vpn", wifi = true, vpn = true)

    @Test fun noWifiOrOnlyVpnOrCellResolvesToNull() {
        assertNull(pickWifi(emptyList<WifiCandidate<String>>(), null))
        assertNull(pickWifi(listOf(cell, vpnOverWifi), "cell"))
    }

    @Test fun mobileDataActiveStillFindsTheWifiNetwork() {
        assertEquals("wlan", pickWifi(listOf(cell, wifi("wlan")), "cell"))
    }

    @Test fun vpnIsSkippedEvenWhenItIsTheActiveNetwork() {
        assertEquals("wlan", pickWifi(listOf(vpnOverWifi, wifi("wlan")), "vpn"))
    }

    @Test fun activeWifiWinsOverAnotherWifi() {
        assertEquals("wlan1", pickWifi(listOf(wifi("wlan0"), wifi("wlan1")), "wlan1"))
        assertEquals("wlan0", pickWifi(listOf(wifi("wlan0"), wifi("wlan1")), null))
    }
}
