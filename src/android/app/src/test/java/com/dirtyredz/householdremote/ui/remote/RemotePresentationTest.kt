package com.dirtyredz.householdremote.ui.remote

import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.power.PowerPhase
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePresentationTest {
    @Test fun powerTableStates() {
        fun p(
            connection: ConnectionState = ConnectionState.Offline,
            power: PowerState = PowerState(),
            selected: Boolean = true
        ) = powerPresentation(selected, connection, power, onWifi = true)

        assertEquals(
            PowerPresentation(
                PowerLabel.Power,
                PowerStatus.SelectTv,
                enabled = false
            ),
            p(ConnectionState.Connected, selected = false)
        )
        assertEquals(
            PowerPresentation(PowerLabel.TurnOff, PowerStatus.OnConnected, true),
            p(ConnectionState.Connected)
        )
        for (offline in listOf(
            ConnectionState.Offline,
            ConnectionState.Connecting,
            ConnectionState.AwaitingApproval
        )) {
            assertEquals(
                "a session that is not usable is never shown as on",
                PowerPresentation(PowerLabel.TurnOn, PowerStatus.OffOrUnreachable, true),
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
        }
        val standby = p(ConnectionState.Connected, PowerState(standbySent = true))
        assertEquals(PowerLabel.TurnOn, standby.label)
        assertEquals(PowerStatus.StandbySent, standby.status)
        val failed = p(power = PowerState(error = "TV not ready"))
        assertTrue("failure keeps the action available", failed.enabled)
    }

    @Test fun connectionLabels() {
        fun label(state: ConnectionState, selected: Boolean = true, onWifi: Boolean = true) =
            connectionLabel(selected, state, onWifi)
        assertNull(label(ConnectionState.Connected, selected = false))
        assertEquals(ConnectionLabel.Connected, label(ConnectionState.Connected))
        assertEquals(ConnectionLabel.Connecting, label(ConnectionState.Connecting))
        assertEquals(ConnectionLabel.ApproveOnTv, label(ConnectionState.AwaitingApproval))
        assertEquals(ConnectionLabel.Offline, label(ConnectionState.Offline))
        assertEquals(ConnectionLabel.Reconnecting, label(ConnectionState.Reconnecting))
        assertEquals(ConnectionLabel.Unavailable, label(ConnectionState.Unavailable))
        for (state in ConnectionState.values()) {
            assertEquals(
                "no Wi-Fi outranks every session state",
                ConnectionLabel.NotOnWifi,
                label(state, onWifi = false)
            )
        }
        assertNull(label(ConnectionState.Offline, selected = false, onWifi = false))
    }

    @Test fun recoveringSessionIsNeverShownAsOn() {
        for (state in listOf(ConnectionState.Reconnecting, ConnectionState.Unavailable)) {
            val power = powerPresentation(true, state, PowerState(), onWifi = true)
            assertEquals(PowerLabel.TurnOn, power.label)
            assertEquals(PowerStatus.OffOrUnreachable, power.status)
        }
    }

    @Test fun turnOnWithoutWifiShowsTheHintAndCannotBePressed() {
        val idle = powerPresentation(true, ConnectionState.Unavailable, PowerState(), false)
        assertEquals(PowerLabel.TurnOn, idle.label)
        assertEquals(PowerStatus.NotOnWifi, idle.status)
        assertFalse(idle.enabled)
        val afterStandby = powerPresentation(
            true,
            ConnectionState.Offline,
            PowerState(standbySent = true),
            onWifi = false
        )
        assertEquals(PowerStatus.NotOnWifi, afterStandby.status)
        val waking = powerPresentation(
            true,
            ConnectionState.Offline,
            PowerState(phase = PowerPhase.Waking),
            onWifi = false
        )
        assertFalse("work already running keeps power disabled", waking.enabled)
        assertEquals(
            PowerStatus.SelectTv,
            powerPresentation(false, ConnectionState.Offline, PowerState(), false).status
        )
    }

    @Test fun layoutRules() {
        assertFalse("owner phone, default text", compactRemote(820.dp, 1f))
        assertFalse("owner phone, 1.3 text", compactRemote(820.dp, 1.3f))
        assertTrue("360x640 at 1.3 text", compactRemote(640.dp, 1.3f))
        assertTrue("short window", compactRemote(560.dp, 1f))
        assertEquals(DirectionPadMin, directionPadSide(328.dp, 90.dp))
        assertEquals(DirectionPadMax, directionPadSide(379.dp, 600.dp))
        assertEquals(200.dp, directionPadSide(328.dp, 200.dp))
        assertEquals("width-bound", 180.dp, directionPadSide(180.dp, 400.dp))
    }
}
