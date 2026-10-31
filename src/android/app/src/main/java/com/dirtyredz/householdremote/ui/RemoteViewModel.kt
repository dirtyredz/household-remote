package com.dirtyredz.householdremote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dirtyredz.householdremote.apps.AppController
import com.dirtyredz.householdremote.apps.AppInstallProbe
import com.dirtyredz.householdremote.apps.AppLauncher
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
import com.dirtyredz.householdremote.remote.GridPacing
import com.dirtyredz.householdremote.remote.GridTyping
import com.dirtyredz.householdremote.remote.KeyDispatcher
import com.dirtyredz.householdremote.remote.RemoteSession
import com.dirtyredz.householdremote.security.Credentials
import com.dirtyredz.householdremote.ui.typing.TypingInput
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
    () -> Unit,
    (Boolean) -> Unit
) -> RemoteSession

typealias KeyDispatcherFactory = (
    CoroutineScope,
    (String) -> Boolean,
    () -> Unit,
    Long,
    (String, String) -> Unit
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
    private val appLauncher: AppLauncher,
    private val discovery: SsdpDiscovery,
    private val wifi: WifiMonitor,
    credentials: Credentials,
    sessionFactory: RemoteSessionFactory,
    dispatcherFactory: KeyDispatcherFactory,
    powerFactory: PowerControllerFactory,
    initialGridDelayMs: Long = GridPacing.DEFAULT_MS,
    private val saveGridDelay: (Long) -> Unit = {}
) : ViewModel() {
    private var household = repository.load()
    private val mutable = MutableStateFlow(
        RemoteState(
            household.tvs,
            household.tvs.find { it.id == household.selected },
            gridDelayMs = GridPacing.coerce(initialGridDelayMs)
        )
    )
    val state = mutable.asStateFlow()
    private val dispatcher: KeyDispatcher
    private val session: RemoteSession
    private val power: PowerController
    private val apps: AppController
    private val typing: TypingInput
    private var operation: Job? = null
    private var foreground = false

    private fun updateState(transform: (RemoteState) -> RemoteState) {
        val next = transform(mutable.value)
        if (next.keyboardOpen && !next.inputEnabled) {
            clearKeyboardIntent(next)
        } else {
            mutable.value = next
        }
    }

    /** The one invalidation path for keyboard intent and its still-unsent dispatcher work. */
    private fun clearKeyboardIntent(next: RemoteState = mutable.value, endText: Boolean = false) {
        typing.reset()
        val finish: () -> Unit = {
            if (endText && foreground && next.keyboardVisible) {
                dispatcher.endText()
            } else {
                dispatcher.discardText()
            }
        }
        mutable.value = next.discardKeyboardIntent(finish)
    }

    init {
        session = sessionFactory(
            viewModelScope,
            credentials,
            { status ->
                val previous = mutable.value
                val wasReady = previous.ready
                updateState { it.copy(connection = status) }
                when {
                    // Only a new usable session starts input; a held fresh key goes out once.
                    status.ready && !wasReady -> {
                        dispatcher.start()
                        mutable.value.selected?.let(apps::check)
                    }

                    status.state == ConnectionState.Offline -> dispatcher.discardPending()

                    else -> Unit
                }
                power.onConnection(status)
            },
            {
                dispatcher.cancel()
                dismissKeyboard()
            },
            ::tvImeChanged
        )
        dispatcher = dispatcherFactory(
            viewModelScope,
            { session.send(it) },
            {},
            state.value.gridDelayMs,
            session.messages::gridPress
        )
        typing = TypingInput(
            dispatcher::text,
            GridTyping(dispatcher::grid, session.messages::ignoredGridCharacter),
            dispatcher::discardGrid
        )
        apps = AppController(
            viewModelScope,
            appProbe,
            appLauncher,
            dispatcher::launch,
            { tv -> state.value.selected?.mac == tv.mac },
            { foreground },
            { found -> updateState { it.copy(apps = found) } }
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
            { power -> updateState { it.copy(power = power) } }
        )
    }

    val tvMessages get() = session.messages.entries

    fun setGridDelay(value: Long) {
        val delayMs = dispatcher.setGridDelay(value)
        updateState { it.copy(gridDelayMs = delayMs) }
        saveGridDelay(delayMs)
    }

    fun openKeyboard() {
        if (foreground && state.value.inputEnabled) {
            if (!state.value.keyboardVisible) typing.open(tvImeActive = false)
            updateState { it.copy(keyboardOpen = true) }
        }
    }

    private fun tvImeChanged(active: Boolean) {
        if (!active) {
            dismissKeyboard()
            return
        }
        if (!foreground || !state.value.inputEnabled) return
        if (state.value.keyboardVisible) {
            typing.tvImeStarted()
        } else {
            typing.open(tvImeActive = true)
            updateState { it.copy(keyboardOpen = true) }
        }
    }

    fun closeKeyboard() {
        clearKeyboardIntent(endText = true)
    }

    private fun dismissKeyboard() {
        clearKeyboardIntent()
    }

    fun sendText(text: String) {
        if (foreground && state.value.keyboardVisible) {
            typing.changed(text)
        }
    }

    fun start() {
        foreground = true
        refresh()
        // Reports the current Wi-Fi state first, so the session knows it before connecting.
        wifi.start(::wifiChanged)
        state.value.selected?.let { session.ensureConnected(it) }
    }

    private fun wifiChanged(available: Boolean) {
        updateState { it.copy(onWifi = available) }
        session.networkChanged(available)
    }

    fun stop() {
        foreground = false
        clearKeyboardIntent()
        wifi.stop()
        operation?.cancel()
        apps.reset()
        power.reset()
        dispatcher.discardPending()
        session.disconnect()
    }

    private fun refresh() {
        updateState {
            it.copy(
                tvs = household.tvs,
                selected = household.tvs.find { tv -> tv.id == household.selected }
            )
        }
    }

    private fun updateHousehold(update: (Household) -> Household) {
        household = update(household)
        repository.save(household)
        refresh()
    }

    private fun disconnectForSelection() {
        clearKeyboardIntent()
        power.reset()
        dispatcher.discardPending()
        session.disconnect()
        apps.reset()
    }

    fun select(tv: Tv) {
        operation?.cancel()
        disconnectForSelection()
        updateHousehold { it.copy(selected = tv.id) }
        if (foreground) session.connect(tv)
    }

    fun connect(pair: Boolean) {
        operation?.cancel()
        clearKeyboardIntent()
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
                updateState { it.copy(message = error.message ?: "Operation failed") }
            }
        }
    }

    fun discover() = action {
        updateState { it.copy(message = "Discovering TVs…") }
        val found = discovery.discover()
        updateHousehold { repository.merge(it, found) }
        updateState { it.copy(message = "Found ${found.size} TV(s)") }
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
        updateState {
            it.copy(message = "Imported ${imported.tvs.size} TV(s); each phone must pair")
        }
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

    fun launch(app: TvApp) {
        val current = state.value
        if (current.appEnabled(app)) current.selected?.let { apps.launch(it, app) }
    }

    /** One power activation; intent is decided from the current session and off intent. */
    fun togglePower() {
        val tv = state.value.selected ?: return
        // Wake needs the phone's Wi-Fi broadcast; the control shows the hint instead.
        if (!state.value.onWifi) return
        power.toggle(tv, state.value.ready)
    }

    override fun onCleared() {
        clearKeyboardIntent()
        wifi.stop()
        apps.reset()
        power.reset()
        dispatcher.discardPending()
        session.disconnect()
        probe.close()
        appProbe.close()
        appLauncher.close()
        super.onCleared()
    }
}
