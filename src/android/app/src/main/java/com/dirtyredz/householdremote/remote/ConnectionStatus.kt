package com.dirtyredz.householdremote.remote

/**
 * Typed connection contract emitted by [RemoteSession]. Presentation and the power
 * coordinator read [state]; [message] carries the human detail for the current transition.
 * Reliability work plugs into this seam rather than parsing status text.
 */
enum class ConnectionState { Offline, Connecting, AwaitingApproval, Connected }

data class ConnectionStatus(val state: ConnectionState, val message: String) {
    val ready: Boolean get() = state == ConnectionState.Connected

    companion object {
        val initial = ConnectionStatus(ConnectionState.Offline, "Not connected")
        fun offline(message: String) = ConnectionStatus(ConnectionState.Offline, message)
    }
}
