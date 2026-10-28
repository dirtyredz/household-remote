package com.dirtyredz.householdremote.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiNetworksTest {
    @Test fun noNetworkAndMobileDataOnlyAreNotOnWifi() {
        val networks = WifiNetworks<String>()
        assertFalse(networks.onWifi)
        assertFalse("mobile data alone changes nothing", networks.update("cell", false))
        assertFalse(networks.onWifi)
    }

    @Test fun wifiArrivingAndLeavingReportsEachTransitionOnce() {
        val networks = WifiNetworks<String>()
        assertTrue(networks.update("wlan", true))
        assertTrue(networks.onWifi)
        assertFalse("a repeated callback is not a change", networks.update("wlan", true))
        assertTrue(networks.lost("wlan"))
        assertFalse(networks.onWifi)
        assertFalse("losing an unknown network is not a change", networks.lost("wlan"))
    }

    @Test fun phoneStaysOnWifiWhileAnyWifiNetworkRemains() {
        val networks = WifiNetworks<String>()
        networks.update("wlan0", true)
        networks.update("wlan1", true)
        assertFalse(networks.lost("wlan0"))
        assertTrue(networks.onWifi)
        assertTrue(networks.lost("wlan1"))
        assertFalse(networks.onWifi)
    }

    @Test fun aNetworkThatLosesTheWifiTransportCountsAsGone() {
        val networks = WifiNetworks<String>()
        networks.update("n", true)
        assertTrue(networks.update("n", false))
        assertFalse(networks.onWifi)
    }

    @Test fun clearForgetsEverything() {
        val networks = WifiNetworks<String>()
        networks.update("wlan", true)
        networks.clear()
        assertFalse(networks.onWifi)
    }
}
