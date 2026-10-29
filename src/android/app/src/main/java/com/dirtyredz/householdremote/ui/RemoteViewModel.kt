package com.dirtyredz.householdremote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dirtyredz.householdremote.apps.AppInstall
import com.dirtyredz.householdremote.apps.AppInstallProbe
import com.dirtyredz.householdremote.apps.TvApp
import com.dirtyredz.householdremote.discovery.SsdpDiscovery
import com.dirtyredz.householdremote.discovery.TvProbe
import com.dirtyredz.householdremote.inventory.Household
import com.dirtyredz.householdremote.inventory.HouseholdImport
import com.dirtyredz.householdremote.inventory.HouseholdRepository
import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.network.WifiMonitor
import com.dirtyredz.householdremote.power.PowerController
import com.dirtyredz.householdremote.power.PowerLink
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionState
import com.dirtyredz.householdremote.remote.ConnectionStatus
import com.dirtyredz.householdremote.remote.KeyDispatcher
import com.dirtyredz.householdremote.remote.RemoteSession
import com.dirtyredz.householdremote.security.Credentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

typealias RemoteSessionFactory = (
    CoroutineScope,
    Credentials,
    (ConnectionStatus) -> Unit,
    () -> Unit
) -> RemoteSession

typealias KeyDispatcherFactory = (
    CoroutineScope,
    (String) -> Boolean,
    () -> Unit
) -> KeyDispatcher

typealias PowerControllerFactory = (
    CoroutineScope,
    PowerLink,
    (PowerState) -> Unit
) -> PowerController

class RemoteViewModel(
    private val repository: HouseholdRepository,
    private val probe: TvProbe,
    private val appProbe: AppInstallProbe,
    private val discovery: SsdpDiscovery,
    private val wifi: WifiMonitor,
    credentials: Credentials,
    sessionFactory: RemoteSessionFactory,
    dispatcherFactory: KeyDispatcherFactory,
    powerFactory: PowerControllerFactory
) : ViewModel() {
    private var household = repository.load()
    private val mutable = MutableStateFlow(
        RemoteState(
            household.tvs,
            household.tvs.find { it.id == household.selected }
        )
    )
    val state = mutable.asStateFlow()
    private val dispatcher: KeyDispatcher
    private val session: RemoteSession
    private val power: PowerController
    private var operation: Job? = null
    private var appCheck: Job? = null
    private var foreground = false

    init {
        session = sessionFactory(
            viewModelScope,
            credentials,
            { status ->
                val previous = mutable.value
                val wasReady = previous.ready
                mutable.value = previous.copy(connection = status)
                when {
                    // Only a new usable session starts input; a held fresh key goes out once.
                    status.ready && !wasReady -> {
                        dispatcher.start()
                        mutable.value.selected?.let(::checkApps)
                    }

                    status.state == ConnectionState.Offline -> dispatcher.discardPending()

                    else -> Unit
                }
                power.onConnection(status)
            },
            { dispatcher.cancel() }
        )
        dispatcher = dispatcherFactory(
            viewModelScope,
            { session.send(it) },
            {}
        )
        power = powerFactory(
            viewModelScope,
            object : PowerLink {
                override fun disconnect() {
                    session.disconnect()
                }
                override fun connect(tv: Tv): Boolean {
                    if (!foreground || state.value.selected?.mac != tv.mac) return false
                    session.connect(tv)
                    return true
                }
                override fun standby(): Boolean {
                    // Deliberate standby: the drop that follows is not recovered automatically.
                    session.suppressRecovery()
                    return dispatcher.standby()
                }
            },
            { power -> mutable.value = mutable.value.copy(power = power) }
        )
    }

    fun start() {
        foreground = true
        refresh()
        // Reports the current Wi-Fi state first, so the session knows it before connecting.
        wifi.start(::wifiChanged)
        state.value.selected?.let { session.ensureConnected(it) }
    }

    private fun wifiChanged(available: Boolean) {
        mutable.value = mutable.value.copy(onWifi = available)
        session.networkChanged(available)
    }

    fun stop() {
        foreground = false
        wifi.stop()
        operation?.cancel()
        appCheck?.cancel()
        power.reset()
        dispatcher.discardPending()
        session.disconnect()
    }

    private fun refresh() {
        mutable.value = mutable.value.copy(
            tvs = household.tvs,
            selected = household.tvs.find { it.id == household.selected }
        )
    }

    private fun updateHousehold(update: (Household) -> Household) {
        household = update(household)
        repository.save(household)
        refresh()
    }

    private fun disconnectForSelection() {
        power.reset()
        dispatcher.discardPending()
        session.disconnect()
        // Install status belongs to the TV it was read from.
        appCheck?.cancel()
        mutable.value = mutable.value.copy(apps = emptyMap())
    }

    /** Read-only install check for the app buttons; the result only applies to [tv]. */
    private fun checkApps(tv: Tv) {
        appCheck?.cancel()
        appCheck = viewModelScope.launch {
            val found = try {
                appProbe.installed(tv.ip)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            }
            if (state.value.selected?.mac == tv.mac) {
                mutable.value = mutable.value.copy(apps = found)
            }
        }
    }

    fun select(tv: Tv) {
        operation?.cancel()
        disconnectForSelection()
        updateHousehold { it.copy(selected = tv.id) }
        if (foreground) session.connect(tv)
    }

    fun connect(pair: Boolean) {
        operation?.cancel()
        power.reset()
        dispatcher.discardPending()
        if (foreground) state.value.selected?.let { session.connect(it, pair) }
    }

    private fun action(block: suspend () -> Unit) {
        operation?.cancel()
        operation = viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutable.value = mutable.value.copy(message = error.message ?: "Operation failed")
            }
        }
    }

    fun discover() = action {
        mutable.value = mutable.value.copy(message = "Discovering TVs…")
        val found = discovery.discover()
        updateHousehold { repository.merge(it, found) }
        mutable.value = mutable.value.copy(message = "Found ${found.size} TV(s)")
    }

    fun add(ip: String) = action {
        val tv = probe.probe(ip.trim())
        disconnectForSelection()
        updateHousehold { current ->
            val merged = repository.merge(current, listOf(tv))
            merged.copy(selected = merged.tvs.first { it.mac == tv.mac }.id)
        }
        if (foreground) state.value.selected?.let { session.connect(it) }
    }

    fun importManifest(text: String) = action {
        val imported = HouseholdImport.parse(text)
        updateHousehold { repository.merge(it, imported.tvs) }
        mutable.value = mutable.value.copy(
            message = "Imported ${imported.tvs.size} TV(s); each phone must pair"
        )
    }

    fun tap(key: String) {
        val current = state.value
        if (current.inputEnabled) {
            dispatcher.tap(key)
            return
        }
        if (!foreground || !current.keysEnabled) return
        // Recovery: one fresh key may wait; a tap after recovery ran out starts a new window.
        session.resumeRecovery()
        dispatcher.hold(key)
    }

    /** One launch request on the live session; never held for, or replayed after, recovery. */
    fun launch(app: TvApp) {
        val current = state.value
        if (current.appEnabled(app)) dispatcher.launch(app)
    }

    /** One power activation; intent is decided from the current session and off intent. */
    fun togglePower() {
        val tv = state.value.selected ?: return
        // Wake needs the phone's Wi-Fi broadcast; the control shows the hint instead.
        if (!state.value.onWifi) return
        power.toggle(tv, state.value.ready)
    }

    override fun onCleared() {
        wifi.stop()
        appCheck?.cancel()
        power.reset()
        dispatcher.discardPending()
        session.disconnect()
        probe.close()
        appProbe.close()
        super.onCleared()
    }
}
