package com.dirtyredz.householdremote.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dirtyredz.householdremote.network.openWifiSettings
import com.dirtyredz.householdremote.ui.remote.RemoteCallbacks
import com.dirtyredz.householdremote.ui.remote.RemoteScreen
import com.dirtyredz.householdremote.ui.remote.keyboard.TvKeyboardDialog
import com.dirtyredz.householdremote.ui.settings.RemoteSettingsSheet
import com.dirtyredz.householdremote.ui.settings.SettingsDialog
import com.dirtyredz.householdremote.ui.setup.ImportManifestDialog
import com.dirtyredz.householdremote.ui.setup.PairingDialog
import com.dirtyredz.householdremote.ui.setup.TvManagementSheet

private enum class Overlay { Tvs, Actions, Settings, Import, Pairing }

/**
 * Production route: lifecycle-driven start/stop, ViewModel collection, and which sheet or
 * dialog is showing. Pairing is bound to the TV it was opened for and closes when the
 * selection changes.
 */
@Composable
fun RemoteApp(providedModel: RemoteViewModel? = null) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val factory = remember(application) { RemoteViewModelFactory(application) }
    val model = providedModel ?: viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleBinding(model)
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var overlayTv by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedId = state.selected?.id
    LaunchedEffect(selectedId) {
        if (overlayTv != null && overlayTv != selectedId) {
            overlay = null
            overlayTv = null
        }
    }
    fun open(next: Overlay, boundToTv: Boolean = false) {
        overlay = next
        overlayTv = if (boundToTv) selectedId else null
    }
    fun close() {
        overlay = null
        overlayTv = null
    }
    RemoteScreen(
        state,
        RemoteCallbacks(
            onKey = model::tap,
            onPower = model::togglePower,
            onLaunchApp = model::launch,
            onPickTv = { open(Overlay.Tvs) },
            onOverflow = { open(Overlay.Actions) },
            onWifiSettings = { openWifiSettings(context) },
            onKeyboard = model::openKeyboard
        )
    )
    if (state.keyboardOpen && state.inputEnabled) {
        TvKeyboardDialog(onDone = model::sendText, onDismiss = model::dismissKeyboard)
    }
    val tv = state.selected
    when (overlay) {
        Overlay.Tvs -> TvManagementSheet(
            tvs = state.tvs,
            selected = tv,
            message = state.message,
            onSelect = {
                model.select(it)
                close()
            },
            onDiscover = model::discover,
            onImport = { open(Overlay.Import) },
            onAdd = model::add,
            onDismiss = ::close
        )

        Overlay.Actions -> RemoteSettingsSheet(
            tvName = tv?.name,
            exitEnabled = state.inputEnabled,
            onReconnect = {
                model.connect(false)
                close()
            },
            onPair = { open(Overlay.Pairing, boundToTv = true) },
            onExit = { model.tap("KEY_EXIT") },
            onSettings = { open(Overlay.Settings) },
            onDismiss = ::close
        )

        Overlay.Settings -> SettingsDialog(onDismiss = ::close)

        Overlay.Import -> ImportManifestDialog(
            onImport = {
                model.importManifest(it)
                close()
            },
            onDismiss = ::close
        )

        Overlay.Pairing -> if (tv != null && tv.id == overlayTv) {
            PairingDialog(
                tv.name,
                onPair = {
                    close()
                    model.connect(true)
                },
                onDismiss = ::close
            )
        }

        null -> Unit
    }
}

@Composable
private fun LifecycleBinding(model: RemoteViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> model.start()
                Lifecycle.Event.ON_STOP -> model.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) model.start()
        onDispose {
            lifecycle.removeObserver(observer)
            model.stop()
        }
    }
}
