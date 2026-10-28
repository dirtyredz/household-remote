package com.dirtyredz.householdremote.ui.remote

import com.dirtyredz.householdremote.power.PowerPhase
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePresentationTest {
    @Test fun powerTableStates() {
        fun p(
            connection: ConnectionState = ConnectionState.Offline,
            power: PowerState = PowerState(),
            selected: Boolean = true
        ) = powerPresentation(selected, connection, power)

        assertEquals(
            PowerPresentation(
                PowerLabel.Power,
                PowerStatus.SelectTv,
                enabled = false,
                busy = false
            ),
            p(ConnectionState.Connected, selected = false)
        )
        assertEquals(
            PowerPresentation(PowerLabel.TurnOff, PowerStatus.OnConnected, true, false),
            p(ConnectionState.Connected)
        )
        for (offline in listOf(
            ConnectionState.Offline,
            ConnectionState.Connecting,
            ConnectionState.AwaitingApproval
        )) {
            assertEquals(
                "a session that is not usable is never shown as on",
                PowerPresentation(PowerLabel.TurnOn, PowerStatus.OffOrUnreachable, true, false),
                p(offline)
            )
        }
        mapOf(
            PowerPhase.Waking to PowerLabel.TurningOn,
            PowerPhase.Connecting to PowerLabel.Connecting,
            PowerPhase.SendingStandby to PowerLabel.TurningOff
        ).forEach { (phase, label) ->
            val busy = p(ConnectionState.Connected, PowerState(phase = phase))
            assertEquals(label, busy.label)
            assertFalse(busy.enabled)
            assertTrue(busy.busy)
        }
        val standby = p(ConnectionState.Connected, PowerState(standbySent = true))
        assertEquals(PowerLabel.TurnOn, standby.label)
        assertEquals(PowerStatus.StandbySent, standby.status)
        assertTrue(standby.notConfirmed)
        val failed = p(power = PowerState(error = "TV not ready"))
        assertEquals("TV not ready", failed.error)
        assertTrue("failure keeps the action available", failed.enabled)
    }

    @Test fun connectionLabels() {
        assertEquals(ConnectionLabel.SelectTv, connectionLabel(false, ConnectionState.Connected))
        assertEquals(ConnectionLabel.Connected, connectionLabel(true, ConnectionState.Connected))
        assertEquals(ConnectionLabel.Connecting, connectionLabel(true, ConnectionState.Connecting))
        assertEquals(
            ConnectionLabel.ApproveOnTv,
            connectionLabel(true, ConnectionState.AwaitingApproval)
        )
        assertEquals(ConnectionLabel.Offline, connectionLabel(true, ConnectionState.Offline))
    }
}
