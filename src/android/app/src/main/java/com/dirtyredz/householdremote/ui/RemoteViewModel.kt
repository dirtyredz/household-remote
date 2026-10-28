package com.dirtyredz.householdremote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dirtyredz.householdremote.discovery.SsdpDiscovery
import com.dirtyredz.householdremote.discovery.TvProbe
import com.dirtyredz.householdremote.inventory.Household
import com.dirtyredz.householdremote.inventory.HouseholdImport
import com.dirtyredz.householdremote.inventory.HouseholdRepository
import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.power.PowerController
import com.dirtyredz.householdremote.power.PowerLink
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionState
import com.dirtyredz.householdremote.remote.ConnectionStatus
import com.dirtyredz.householdremote.remote.KeyDispatcher
import com.dirtyredz.householdremote.remote.RemoteSession
import com.dirtyredz.householdremote.security.Credentials
import com.dirtyredz.householdremote.shortcuts.ShortcutProfile
import com.dirtyredz.householdremote.shortcuts.ShortcutStore
import com.dirtyredz.householdremote.shortcuts.ShortcutTextCodec
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
    (String) -> Unit
) -> KeyDispatcher

typealias PowerControllerFactory = (
    CoroutineScope,
    PowerLink,
    (PowerState) -> Unit
) -> PowerController

class RemoteViewModel(
    private val repository: HouseholdRepository,
    private val profiles: ShortcutStore,
    private val probe: TvProbe,
    private val discovery: SsdpDiscovery,
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
    private var foreground = false

    init {
        session = sessionFactory(
            viewModelScope,
            credentials,
            { status ->
                val wasReady = mutable.value.ready
                mutable.value = mutable.value.copy(
                    connection = status,
                    message = status.message,
                    busy = false
                )
                when {
                    // Only a new usable session starts input; a held fresh key goes out once.
                    status.ready && !wasReady -> dispatcher.start()

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
            { message -> mutable.value = mutable.value.copy(message = message, busy = false) }
        )
        power = powerFactory(
            viewModelScope,
            object : PowerLink {
                override fun disconnect() {
                    session.disconnect()
                    mutable.value = mutable.value.copy(busy = false)
                }
                override fun connect(tv: Tv): Boolean {
                    if (!foreground || state.value.selected?.mac != tv.mac) return false
                    session.connect(tv)
                    return true
                }
                override fun standby(): Boolean {
                    mutable.value = mutable.value.copy(busy = false)
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
        state.value.selected?.let { session.ensureConnected(it) }
    }

    fun stop() {
        foreground = false
        operation?.cancel()
        power.reset()
        dispatcher.discardPending()
        session.disconnect()
        mutable.value = mutable.value.copy(busy = false)
    }

    private fun refresh() {
        val selected = household.tvs.find { it.id == household.selected }
        mutable.value = mutable.value.copy(
            tvs = household.tvs,
            selected = selected,
            configured = ShortcutProfile.apps.filter {
                selected != null && profiles.get(selected.mac, it) != null
            }.toSet()
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
        mutable.value = mutable.value.copy(busy = false)
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
                mutable.value = mutable.value.copy(
                    message = error.message ?: "Operation failed",
                    busy = false
                )
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
            if (!dispatcher.tap(key)) message(INPUT_UNAVAILABLE)
            return
        }
        if (!foreground || !current.keysEnabled) return message(INPUT_UNAVAILABLE)
        // Recovery: one fresh key may wait; a tap after recovery ran out starts a new window.
        session.resumeRecovery()
        message(if (dispatcher.hold(key)) KEY_HELD else KEY_REJECTED)
    }

    private fun message(text: String) {
        mutable.value = mutable.value.copy(message = text)
    }

    /** One power activation; intent is decided from the current session and off intent. */
    fun togglePower() {
        val tv = state.value.selected ?: return
        power.toggle(tv, state.value.ready)
    }

    fun cancelSequence() {
        dispatcher.cancel()
        if (state.value.ready) dispatcher.start()
        mutable.value = mutable.value.copy(busy = false, message = "Sequence cancelled")
    }

    fun shortcut(app: String) {
        val tv = state.value.selected ?: return
        val profile = profiles.get(tv.mac, app) ?: return
        if (!state.value.inputEnabled) return
        mutable.value = mutable.value.copy(busy = true, message = "Sending $app sequence…")
        dispatcher.run(profile)
    }

    fun profileText(app: String): String = state.value.selected?.let {
        profiles.get(it.mac, app)
    }?.let(ShortcutTextCodec::encode).orEmpty()

    fun saveProfile(app: String, text: String) = action {
        val tv = state.value.selected ?: return@action
        if (text.isBlank()) {
            profiles.remove(tv.mac, app)
        } else {
            profiles.save(tv.mac, ShortcutTextCodec.decode(app, text))
        }
        refresh()
        mutable.value = mutable.value.copy(message = "Calibration saved locally")
    }

    override fun onCleared() {
        power.reset()
        dispatcher.discardPending()
        session.disconnect()
        probe.close()
        super.onCleared()
    }

    private companion object {
        const val INPUT_UNAVAILABLE = "Input unavailable; connect or cancel sequence"
        const val KEY_HELD = "Reconnecting; key will send when connected"
        const val KEY_REJECTED = "Still reconnecting; key not sent"
    }
}
