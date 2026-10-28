package com.dirtyredz.householdremote.ui.remote

import com.dirtyredz.householdremote.power.PowerIntent
import com.dirtyredz.householdremote.power.PowerPhase
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionState
import com.dirtyredz.householdremote.remote.ConnectionStatus

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
    power: PowerState,
    onWifi: Boolean
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
    // Without Wi-Fi there is no session and no wake broadcast: say why instead of trying.
    if (!onWifi) {
        return PowerPresentation(
            PowerLabel.TurnOn,
            PowerStatus.NotOnWifi,
            enabled = false,
            busy = false,
            error = power.error
        )
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

enum class ConnectionLabel {
    SelectTv,
    Connected,
    Connecting,
    Reconnecting,
    ApproveOnTv,
    Unavailable,
    Offline,
    NotOnWifi
}

/** "Phone not on Wi-Fi" outranks everything: no TV is reachable and nothing is attempted. */
fun connectionLabel(
    hasSelection: Boolean,
    connection: ConnectionState,
    onWifi: Boolean
): ConnectionLabel = when {
    !onWifi -> ConnectionLabel.NotOnWifi

    !hasSelection -> ConnectionLabel.SelectTv

    else -> when (connection) {
        ConnectionState.Connected -> ConnectionLabel.Connected
        ConnectionState.Connecting -> ConnectionLabel.Connecting
        ConnectionState.Reconnecting -> ConnectionLabel.Reconnecting
        ConnectionState.AwaitingApproval -> ConnectionLabel.ApproveOnTv
        ConnectionState.Unavailable -> ConnectionLabel.Unavailable
        ConnectionState.Offline -> ConnectionLabel.Offline
    }
}

/**
 * The one line beside the power button. It replaces the old bottom message line, so it adds
 * no height of its own. Power feedback outranks connection detail, which outranks general
 * feedback; only [Message] is transient (the slot hides it after a few seconds).
 */
sealed interface RemoteNotice {
    data class Error(val text: String) : RemoteNotice

    data class Progress(val label: PowerLabel) : RemoteNotice

    data object NotOnWifi : RemoteNotice

    data object StandbyNotConfirmed : RemoteNotice

    /** Why the connection stopped or what it is waiting for (pairing, re-pair, approval). */
    data class Detail(val text: String) : RemoteNotice

    data class Message(val text: String) : RemoteNotice
}

/** Offline messages the indicator's "Offline" already says. */
private val quietOffline = setOf(
    "",
    ConnectionStatus.initial.message,
    ConnectionStatus.DISCONNECTED
)

fun remoteNotice(
    power: PowerPresentation,
    connection: ConnectionStatus,
    message: String
): RemoteNotice? {
    power.error?.let { return RemoteNotice.Error(it) }
    if (power.busy) return RemoteNotice.Progress(power.label)
    if (power.status == PowerStatus.NotOnWifi) return RemoteNotice.NotOnWifi
    if (power.notConfirmed) return RemoteNotice.StandbyNotConfirmed
    val detail = when (connection.state) {
        ConnectionState.AwaitingApproval -> connection.message
        ConnectionState.Offline -> connection.message.takeUnless { it in quietOffline }
        else -> null
    }
    if (detail != null) return RemoteNotice.Detail(detail)
    return message.takeIf { it.isNotBlank() }?.let(RemoteNotice::Message)
}
