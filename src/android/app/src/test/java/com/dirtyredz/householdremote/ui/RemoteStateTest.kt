package com.dirtyredz.householdremote.ui

import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.power.PowerPhase
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionFailure
import com.dirtyredz.householdremote.remote.ConnectionState
import com.dirtyredz.householdremote.remote.ConnectionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteStateTest {
    private val tv = Tv("living", "Living", "Test", "AA:BB:CC:DD:EE:FF", "192.168.1.5")
    private fun state(connection: ConnectionState, power: PowerState = PowerState()) =
        RemoteState(selected = tv, connection = ConnectionStatus(connection, ""), power = power)

    @Test fun ordinaryKeysMayWaitDuringRecoveryButShortcutsMayNot() {
        for (recovering in listOf(ConnectionState.Reconnecting, ConnectionState.Unavailable)) {
            val value = state(recovering)
            assertTrue(value.keysEnabled)
            assertFalse("shortcuts, Exit and standby need a usable session", value.inputEnabled)
        }
        assertTrue(state(ConnectionState.Connected).keysEnabled)
    }

    @Test fun keysStayOffWhenTerminalAfterStandbyOrDuringPowerWork() {
        assertFalse(state(ConnectionState.Offline).keysEnabled)
        assertFalse(
            RemoteState(
                selected = tv,
                connection = ConnectionStatus(
                    ConnectionState.Offline,
                    ConnectionFailure.TrustChanged.message,
                    ConnectionFailure.TrustChanged
                )
            ).keysEnabled
        )
        assertFalse(
            state(ConnectionState.Reconnecting, PowerState(standbySent = true)).keysEnabled
        )
        assertFalse(
            state(ConnectionState.Reconnecting, PowerState(phase = PowerPhase.Waking)).keysEnabled
        )
        assertFalse(state(ConnectionState.Reconnecting).copy(busy = true).keysEnabled)
        assertFalse(state(ConnectionState.Reconnecting).copy(selected = null).keysEnabled)
    }

    @Test fun noKeyMayWaitWhileThePhoneIsOffWifi() {
        val parked = state(ConnectionState.Unavailable).copy(onWifi = false)
        assertFalse("recovery is parked until Wi-Fi returns", parked.keysEnabled)
        assertTrue(parked.copy(onWifi = true).keysEnabled)
    }
}
