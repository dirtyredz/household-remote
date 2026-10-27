package com.dirtyredz.householdremote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dirtyredz.householdremote.discovery.SsdpDiscovery
import com.dirtyredz.householdremote.discovery.TvProbe
import com.dirtyredz.householdremote.inventory.Household
import com.dirtyredz.householdremote.inventory.HouseholdImport
import com.dirtyredz.householdremote.inventory.HouseholdRepository
import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.power.AwakeClock
import com.dirtyredz.householdremote.power.TvWake
import com.dirtyredz.householdremote.power.awaitAwake
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
    (String, Boolean) -> Unit,
    () -> Unit
) -> RemoteSession

typealias KeyDispatcherFactory = (
    CoroutineScope,
    (String) -> Boolean,
    (String) -> Unit
) -> KeyDispatcher

data class RemoteState(
    val tvs: List<Tv> = emptyList(),
    val selected: Tv? = null,
    val message: String = "Add or discover a TV",
    val ready: Boolean = false,
    val busy: Boolean = false,
    val configured: Set<String> = emptySet()
)

class RemoteViewModel(
    private val repository: HouseholdRepository,
    private val profiles: ShortcutStore,
    private val probe: TvProbe,
    private val discovery: SsdpDiscovery,
    private val wakeSender: TvWake,
    credentials: Credentials,
    private val awakeClock: AwakeClock,
    sessionFactory: RemoteSessionFactory,
    dispatcherFactory: KeyDispatcherFactory
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
    private var operation: Job? = null
    private var foreground = false

    init {
        session = sessionFactory(
            viewModelScope,
            credentials,
            { message, ready ->
                mutable.value = mutable.value.copy(message = message, ready = ready, busy = false)
                if (ready) dispatcher.start()
            },
            { dispatcher.cancel() }
        )
        dispatcher = dispatcherFactory(
            viewModelScope,
            { session.send(it) },
            { message -> mutable.value = mutable.value.copy(message = message, busy = false) }
        )
    }

    fun start() {
        foreground = true
        refresh()
        state.value.selected?.let { session.connect(it) }
    }

    fun stop() {
        foreground = false
        operation?.cancel()
        session.disconnect()
        mutable.value = mutable.value.copy(ready = false, busy = false)
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
        session.disconnect()
        mutable.value = mutable.value.copy(ready = false, busy = false)
    }

    fun select(tv: Tv) {
        operation?.cancel()
        disconnectForSelection()
        updateHousehold { it.copy(selected = tv.id) }
        if (foreground) session.connect(tv)
    }

    fun connect(pair: Boolean) {
        operation?.cancel()
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
        if (!dispatcher.tap(key)) {
            mutable.value = mutable.value.copy(
                message = "Input unavailable; connect or cancel sequence"
            )
        }
    }

    fun cancelSequence() {
        dispatcher.cancel()
        if (state.value.ready) dispatcher.start()
        mutable.value = mutable.value.copy(busy = false, message = "Sequence cancelled")
    }

    fun shortcut(app: String) {
        val tv = state.value.selected ?: return
        val profile = profiles.get(tv.mac, app) ?: return
        if (!state.value.ready || state.value.busy) return
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

    fun wake() = action {
        val tv = state.value.selected ?: return@action
        session.disconnect()
        mutable.value = mutable.value.copy(
            ready = false,
            message = "Wake sent; waiting up to 30 seconds…"
        )
        wakeSender.wake(tv.mac)
        if (awaitAwake(tv, probe, 30_000, 1_500, awakeClock)) {
            mutable.value = mutable.value.copy(message = "TV is awake")
            if (foreground) session.connect(tv)
        } else {
            mutable.value = mutable.value.copy(
                message = "TV not ready; check Wi-Fi wake settings"
            )
        }
    }

    override fun onCleared() {
        session.disconnect()
        probe.close()
        super.onCleared()
    }
}
