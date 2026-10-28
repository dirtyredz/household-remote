package com.dirtyredz.householdremote.ui

import com.dirtyredz.householdremote.apps.AppInstall
import com.dirtyredz.householdremote.apps.TvApp
import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionStatus

/**
 * Immutable screen state. [connection], [power] and [onWifi] are independent typed fields;
 * [message] is general feedback only and is never parsed for connection or power state.
 */
data class RemoteState(
    val tvs: List<Tv> = emptyList(),
    val selected: Tv? = null,
    val message: String = "Add or discover a TV",
    val connection: ConnectionStatus = ConnectionStatus.initial,
    /** What the selected TV reported per app; a missing entry is [AppInstall.Unknown]. */
    val apps: Map<TvApp, AppInstall> = emptyMap(),
    val power: PowerState = PowerState(),
    /** The phone has an active Wi-Fi network (the TVs are only reachable over the LAN). */
    val onWifi: Boolean = true
) {
    val ready: Boolean get() = connection.ready

    /** Exit, app launches and standby: a usable session with no power work or off intent. */
    val inputEnabled: Boolean
        get() = selected != null && ready && power.idle && !power.standbySent

    /**
     * Ordinary navigation/volume keys: usable now, or one fresh key may wait for automatic
     * recovery, which needs Wi-Fi. App launches, Exit and standby stay on [inputEnabled].
     */
    val keysEnabled: Boolean
        get() = inputEnabled ||
            (
                selected != null && onWifi && connection.recoverable && power.idle &&
                    !power.standbySent
                )

    fun install(app: TvApp): AppInstall = apps[app] ?: AppInstall.Unknown

    /** An app button works on a usable session unless the TV said the app is not installed. */
    fun appEnabled(app: TvApp): Boolean = inputEnabled && install(app) != AppInstall.NotInstalled
}
