package com.dirtyredz.householdremote.ui.remote

import com.dirtyredz.householdremote.power.PowerIntent
import com.dirtyredz.householdremote.power.PowerPhase
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionState

/** Pure mapping from typed state to what the controls show; composables only localize it. */
enum class PowerLabel { Power, TurnOff, TurnOn, TurningOn, Connecting, TurningOff }

enum class PowerStatus {
    SelectTv,
    OnConnected,
    OffOrUnreachable,
    StandbySent,
    InProgress,
    NotOnWifi
}

data class PowerPresentation(val label: PowerLabel, val status: PowerStatus, val enabled: Boolean)

fun powerPresentation(
    hasSelection: Boolean,
    connection: ConnectionState,
    power: PowerState,
    onWifi: Boolean
): PowerPresentation {
    if (!hasSelection) {
        return PowerPresentation(
            PowerLabel.Power,
            PowerStatus.SelectTv,
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
        return PowerPresentation(busyLabel, PowerStatus.InProgress, false)
    }
    // Without Wi-Fi there is no session and no wake broadcast: say why instead of trying.
    if (!onWifi) {
        return PowerPresentation(
            PowerLabel.TurnOn,
            PowerStatus.NotOnWifi,
            enabled = false
        )
    }
    val connected = connection == ConnectionState.Connected
    return when {
        power.standbySent -> PowerPresentation(
            PowerLabel.TurnOn,
            PowerStatus.StandbySent,
            enabled = true
        )

        power.intent(connected) == PowerIntent.Standby -> PowerPresentation(
            PowerLabel.TurnOff,
            PowerStatus.OnConnected,
            enabled = true
        )

        else -> PowerPresentation(
            PowerLabel.TurnOn,
            PowerStatus.OffOrUnreachable,
            enabled = true
        )
    }
}

enum class ConnectionLabel {
    Connected,
    Connecting,
    Reconnecting,
    ApproveOnTv,
    Unavailable,
    Offline,
    NotOnWifi
}

/** No selection has no header status; otherwise no Wi-Fi outranks the session state. */
fun connectionLabel(
    hasSelection: Boolean,
    connection: ConnectionState,
    onWifi: Boolean
): ConnectionLabel? = when {
    !hasSelection -> null

    !onWifi -> ConnectionLabel.NotOnWifi

    else -> when (connection) {
        ConnectionState.Connected -> ConnectionLabel.Connected
        ConnectionState.Connecting -> ConnectionLabel.Connecting
        ConnectionState.Reconnecting -> ConnectionLabel.Reconnecting
        ConnectionState.AwaitingApproval -> ConnectionLabel.ApproveOnTv
        ConnectionState.Unavailable -> ConnectionLabel.Unavailable
        ConnectionState.Offline -> ConnectionLabel.Offline
    }
}
