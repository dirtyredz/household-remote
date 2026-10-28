package com.dirtyredz.householdremote.ui

import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionStatus

/**
 * Immutable screen state. [connection] and [power] are independent typed fields; [message]
 * is general feedback only and is never parsed for connection or power state.
 */
data class RemoteState(
    val tvs: List<Tv> = emptyList(),
    val selected: Tv? = null,
    val message: String = "Add or discover a TV",
    val connection: ConnectionStatus = ConnectionStatus.initial,
    val busy: Boolean = false,
    val configured: Set<String> = emptySet(),
    val power: PowerState = PowerState()
) {
    val ready: Boolean get() = connection.ready

    /** Ordinary keys and shortcuts: a usable session, no shortcut, no power work or off intent. */
    val inputEnabled: Boolean
        get() = selected != null && ready && !busy && power.idle && !power.standbySent

    /**
     * Ordinary navigation/volume keys: usable now, or one fresh key may wait for automatic
     * recovery. Shortcuts, Exit and standby stay on [inputEnabled].
     */
    val keysEnabled: Boolean
        get() = inputEnabled ||
            (
                selected != null && connection.recoverable && !busy && power.idle &&
                    !power.standbySent
                )
}
