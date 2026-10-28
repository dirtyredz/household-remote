package com.dirtyredz.householdremote.ui.remote

import com.dirtyredz.householdremote.power.PowerIntent
import com.dirtyredz.householdremote.power.PowerPhase
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionState

/** Pure mapping from typed state to what the controls show; composables only localize it. */
enum class PowerLabel { Power, TurnOff, TurnOn, TurningOn, Connecting, TurningOff }

enum class PowerStatus { SelectTv, OnConnected, OffOrUnreachable, StandbySent, InProgress }

data class PowerPresentation(
    val label: PowerLabel,
    val status: PowerStatus,
    val enabled: Boolean,
    val busy: Boolean,
    val notConfirmed: Boolean = false,
    val error: String? = null
)

fun powerPresentation(
    hasSelection: Boolean,
    connection: ConnectionState,
    power: PowerState
): PowerPresentation {
    if (!hasSelection) {
        return PowerPresentation(
            PowerLabel.Power,
            PowerStatus.SelectTv,
            false,
            false
        )
    }
    val busyLabel = when (power.phase) {
        PowerPhase.Waking -> PowerLabel.TurningOn
        PowerPhase.Connecting -> PowerLabel.Connecting
        PowerPhase.SendingStandby -> PowerLabel.TurningOff
        PowerPhase.Idle -> null
    }
    if (busyLabel != null) {
        return PowerPresentation(busyLabel, PowerStatus.InProgress, false, true)
    }
    val connected = connection == ConnectionState.Connected
    return when {
        power.standbySent -> PowerPresentation(
            PowerLabel.TurnOn,
            PowerStatus.StandbySent,
            enabled = true,
            busy = false,
            notConfirmed = true,
            error = power.error
        )

        power.intent(connected) == PowerIntent.Standby -> PowerPresentation(
            PowerLabel.TurnOff,
            PowerStatus.OnConnected,
            enabled = true,
            busy = false,
            error = power.error
        )

        else -> PowerPresentation(
            PowerLabel.TurnOn,
            PowerStatus.OffOrUnreachable,
            enabled = true,
            busy = false,
            error = power.error
        )
    }
}

enum class ConnectionLabel { SelectTv, Connected, Connecting, ApproveOnTv, Offline }

fun connectionLabel(hasSelection: Boolean, connection: ConnectionState): ConnectionLabel =
    if (!hasSelection) {
        ConnectionLabel.SelectTv
    } else {
        when (connection) {
            ConnectionState.Connected -> ConnectionLabel.Connected
            ConnectionState.Connecting -> ConnectionLabel.Connecting
            ConnectionState.AwaitingApproval -> ConnectionLabel.ApproveOnTv
            ConnectionState.Offline -> ConnectionLabel.Offline
        }
    }
