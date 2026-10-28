package com.dirtyredz.householdremote.remote

/**
 * Typed connection contract emitted by [RemoteSession]. Presentation and the power
 * coordinator read [state]; [message] carries the human detail for the current transition.
 *
 * [Reconnecting] is automatic foreground recovery after a transient drop, using the saved
 * token and pinned certificate. [Unavailable] means that bounded recovery ran out; a fresh
 * ordinary tap, Reconnect or Wake starts a new window. [Offline] is terminal until the user
 * acts, and carries a typed [ConnectionStatus.failure] when a trust or pairing problem ended it.
 */
enum class ConnectionState {
    Offline,
    Connecting,
    AwaitingApproval,
    Connected,
    Reconnecting,
    Unavailable
}

/** Why an attempt ended. Only [Transport] is retried; the rest need the user. */
enum class ConnectionFailure(val message: String) {
    Transport("Connection lost"),
    TrustChanged("TV certificate changed; re-pair while watching this TV"),
    PairingRequired("Pair while watching this TV"),
    Denied("Denied; approve or explicitly re-pair"),
    CredentialsUnavailable("Credentials unavailable; re-pair required"),
    ProtocolError("Invalid TV response or credential storage failure"),
    ApprovalTimeout("Approval timed out; reconnect manually")
}

data class ConnectionStatus(
    val state: ConnectionState,
    val message: String,
    val failure: ConnectionFailure? = null,
    /** 1-based automatic attempt number while [ConnectionState.Reconnecting]. */
    val attempt: Int = 0
) {
    val ready: Boolean get() = state == ConnectionState.Connected

    /** One fresh ordinary key may wait: recovery is running, or a tap may restart it. */
    val recoverable: Boolean
        get() = state == ConnectionState.Reconnecting || state == ConnectionState.Unavailable

    companion object {
        val initial = ConnectionStatus(ConnectionState.Offline, "Not connected")
        fun offline(message: String) = ConnectionStatus(ConnectionState.Offline, message)
        const val RECONNECTING = "Reconnecting…"
        const val UNAVAILABLE = "TV unavailable — check Wi-Fi or Wake"
        const val PAIRING_FAILED = "Connection failed; check the TV and pair again"
        const val AFTER_STANDBY = "Disconnected after standby"
        const val DISCONNECTED = "Disconnected"
        const val NO_WIFI = "Phone not on Wi-Fi"
    }
}
