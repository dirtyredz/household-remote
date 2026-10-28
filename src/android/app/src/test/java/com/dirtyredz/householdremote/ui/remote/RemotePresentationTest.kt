package com.dirtyredz.householdremote.ui.remote

import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.power.PowerPhase
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionFailure
import com.dirtyredz.householdremote.remote.ConnectionState
import com.dirtyredz.householdremote.remote.ConnectionStatus
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
        fun label(state: ConnectionState, selected: Boolean = true, onWifi: Boolean = true) =
            connectionLabel(selected, state, onWifi)
        assertEquals(ConnectionLabel.SelectTv, label(ConnectionState.Connected, selected = false))
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
        assertEquals(
            ConnectionLabel.NotOnWifi,
            label(ConnectionState.Offline, selected = false, onWifi = false)
        )
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
        assertFalse(idle.busy)
        val afterStandby = powerPresentation(
            true,
            ConnectionState.Offline,
            PowerState(standbySent = true),
            onWifi = false
        )
        assertEquals(PowerStatus.NotOnWifi, afterStandby.status)
        assertFalse(afterStandby.notConfirmed)
        val waking = powerPresentation(
            true,
            ConnectionState.Offline,
            PowerState(phase = PowerPhase.Waking),
            onWifi = false
        )
        assertTrue("work already running keeps its progress", waking.busy)
        assertEquals(
            PowerStatus.SelectTv,
            powerPresentation(false, ConnectionState.Offline, PowerState(), false).status
        )
    }

    @Test fun noticePriorityIsPowerThenConnectionDetailThenFeedback() {
        val connected = ConnectionStatus(ConnectionState.Connected, "Connected")
        val on = PowerPresentation(PowerLabel.TurnOff, PowerStatus.OnConnected, true, false)
        assertNull("nothing to say", remoteNotice(on, connected, ""))
        assertEquals(
            RemoteNotice.Message("Sequence sent"),
            remoteNotice(on, connected, "Sequence sent")
        )
        assertEquals(
            RemoteNotice.Error("TV not ready"),
            remoteNotice(on.copy(error = "TV not ready"), connected, "Sequence sent")
        )
        assertEquals(
            RemoteNotice.Progress(PowerLabel.TurningOn),
            remoteNotice(
                PowerPresentation(PowerLabel.TurningOn, PowerStatus.InProgress, false, true),
                connected,
                "x"
            )
        )
        val standby = PowerPresentation(
            PowerLabel.TurnOn,
            PowerStatus.StandbySent,
            true,
            false,
            notConfirmed = true
        )
        assertEquals(
            RemoteNotice.StandbyNotConfirmed,
            remoteNotice(
                standby,
                ConnectionStatus.offline(ConnectionStatus.AFTER_STANDBY),
                ""
            )
        )
        val noWifi = powerPresentation(true, ConnectionState.Unavailable, PowerState(), false)
        assertEquals(
            RemoteNotice.NotOnWifi,
            remoteNotice(noWifi, ConnectionStatus.offline(ConnectionStatus.NO_WIFI), "x")
        )
    }

    @Test fun connectionDetailOnlyWhenTheIndicatorCannotSayIt() {
        val off = PowerPresentation(PowerLabel.TurnOn, PowerStatus.OffOrUnreachable, true, false)
        val trust = ConnectionFailure.TrustChanged
        assertEquals(
            RemoteNotice.Detail(trust.message),
            remoteNotice(off, ConnectionStatus(ConnectionState.Offline, trust.message, trust), "")
        )
        assertEquals(
            RemoteNotice.Detail(ConnectionStatus.PAIRING_FAILED),
            remoteNotice(off, ConnectionStatus.offline(ConnectionStatus.PAIRING_FAILED), "")
        )
        val approve = "Approve TV Tuneup on TV within 90 seconds"
        assertEquals(
            RemoteNotice.Detail(approve),
            remoteNotice(off, ConnectionStatus(ConnectionState.AwaitingApproval, approve), "")
        )
        for (quiet in listOf(
            ConnectionStatus.initial,
            ConnectionStatus.offline(ConnectionStatus.DISCONNECTED),
            ConnectionStatus(ConnectionState.Reconnecting, ConnectionStatus.RECONNECTING),
            ConnectionStatus(ConnectionState.Unavailable, ConnectionStatus.UNAVAILABLE)
        )) {
            assertNull("the indicator already says ${quiet.message}", remoteNotice(off, quiet, ""))
        }
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
