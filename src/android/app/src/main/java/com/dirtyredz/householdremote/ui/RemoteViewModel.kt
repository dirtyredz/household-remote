package com.dirtyredz.householdremote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dirtyredz.householdremote.discovery.SsdpDiscovery
import com.dirtyredz.householdremote.discovery.TvInfoClient
import com.dirtyredz.householdremote.inventory.HouseholdImport
import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.inventory.TvRepository
import com.dirtyredz.householdremote.power.WakeOnLan
import com.dirtyredz.householdremote.remote.KeyDispatcher
import com.dirtyredz.householdremote.remote.RemoteSession
import com.dirtyredz.householdremote.security.TokenStore
import com.dirtyredz.householdremote.shortcuts.ShortcutProfile
import com.dirtyredz.householdremote.shortcuts.ShortcutStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class RemoteState(
    val tvs: List<Tv> = emptyList(),
    val selected: Tv? = null,
    val message: String = "Add or discover a TV",
    val ready: Boolean = false,
    val busy: Boolean = false,
    val configured: Set<String> = emptySet()
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TvRepository(application)
    private var household = repository.load()
    private val profiles = ShortcutStore(application)
    private val info = TvInfoClient()
    private val discovery = SsdpDiscovery(application, info)
    private val wake = WakeOnLan(application)
    private val mutable = MutableStateFlow(
        RemoteState(
            household.tvs,
            household.tvs.find {
                it.id ==
                    household.selected
            }
        )
    )
    val state = mutable.asStateFlow()
    private val dispatcher =
        KeyDispatcher(viewModelScope, { session.send(it) }, { message ->
            mutable.value =
                mutable.value.copy(message = message, busy = false)
        })
    private val session: RemoteSession =
        RemoteSession(viewModelScope, TokenStore(application), { message, ready ->
            mutable.value = mutable.value.copy(message = message, ready = ready, busy = false)
            if (ready) dispatcher.start()
        }, { dispatcher.cancel() })
    private var operation: Job? = null
    private var foreground = false
    fun start() {
        foreground = true
        refresh()
        state.value.selected?.let { session.connect(it) }
    }
    fun stop() {
        foreground = false
        operation?.cancel()
        session.disconnect()
        mutable.value =
            mutable.value.copy(ready = false, busy = false)
    }
    private fun refresh() {
        val selected = household.tvs.find { it.id == household.selected }
        mutable.value =
            mutable.value.copy(
                tvs = household.tvs,
                selected = selected,
                configured = ShortcutProfile.apps.filter {
                    selected !=
                        null &&
                        profiles.get(selected.mac, it) != null
                }.toSet()
            )
    }
    fun select(tv: Tv) {
        operation?.cancel()
        session.disconnect()
        mutable.value = mutable.value.copy(ready = false, busy = false)
        household = household.copy(selected = tv.id)
        repository.save(household)
        refresh()
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
            } catch (
                error: CancellationException
            ) {
                throw error
            } catch (error: Exception) {
                mutable.value =
                    mutable.value.copy(message = error.message ?: "Operation failed", busy = false)
            }
        }
    }
    fun discover() = action {
        mutable.value = mutable.value.copy(message = "Discovering TVs…")
        val found = discovery.discover()
        found.forEach { household = HouseholdImport.merge(household, it) }
        repository.save(household)
        refresh()
        mutable.value = mutable.value.copy(message = "Found ${found.size} TV(s)")
    }
    fun add(ip: String) = action {
        val tv = info.probe(ip.trim())
        household = HouseholdImport.merge(household, tv)
        repository.save(household)
        refresh()
        select(household.tvs.first { it.mac == tv.mac })
    }
    fun importManifest(text: String) = action {
        val imported = HouseholdImport.parse(text)
        imported.tvs.forEach { household = HouseholdImport.merge(household, it) }
        repository.save(household)
        refresh()
        mutable.value =
            mutable.value.copy(
                message = "Imported ${imported.tvs.size} TV(s); each phone must pair"
            )
    }
    fun tap(key: String) {
        if (!dispatcher.tap(key)) {
            mutable.value =
                mutable.value.copy(message = "Input unavailable; connect or cancel sequence")
        }
    }
    fun cancelSequence() {
        dispatcher.cancel()
        if (state.value.ready) dispatcher.start()
        mutable.value =
            mutable.value.copy(busy = false, message = "Sequence cancelled")
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
    }?.steps?.joinToString("\n") { "${it.key} ${it.delayMs}" }.orEmpty()
    fun saveProfile(app: String, text: String) = action {
        val tv = state.value.selected ?: return@action
        if (text.isBlank()) {
            profiles.remove(
                tv.mac,
                app
            )
        } else {
            profiles.save(tv.mac, ShortcutProfile.parse(app, text))
        }
        refresh()
        mutable.value = mutable.value.copy(message = "Calibration saved locally")
    }
    fun wake() = action {
        val tv = state.value.selected ?: return@action
        session.disconnect()
        mutable.value =
            mutable.value.copy(ready = false, message = "Wake sent; waiting up to 30 seconds…")
        wake.wake(tv.mac)
        val awake = withTimeoutOrNull(30_000) {
            while (true) {
                delay(1500)
                val found = try {
                    info.probe(tv.ip)
                } catch (
                    error: CancellationException
                ) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (found?.mac == tv.mac) return@withTimeoutOrNull true
            }
        }
        if (awake == true) {
            if (foreground) session.connect(tv)
            return@action
        }
        mutable.value = mutable.value.copy(message = "TV not ready; check Wi-Fi wake settings")
    }
    override fun onCleared() {
        session.disconnect()
        info.close()
        super.onCleared()
    }
}
