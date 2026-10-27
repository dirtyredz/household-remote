package com.dirtyredz.householdremote.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dirtyredz.householdremote.BuildConfig
import com.dirtyredz.householdremote.shortcuts.ShortcutProfile

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { RemoteApp() } }
    }
}

@Composable
fun RemoteApp(model: RemoteViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event ==
                Lifecycle.Event.ON_START
            ) {
                model.start()
            } else if (event ==
                Lifecycle.Event.ON_STOP
            ) {
                model.stop()
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) model.start()
        onDispose {
            lifecycle.removeObserver(observer)
            model.stop()
        }
    }
    var ip by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var manifest by remember { mutableStateOf("") }
    var pairing by remember { mutableStateOf(false) }
    var calibration by remember { mutableStateOf<String?>(null) }
    var steps by remember { mutableStateOf("") }
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(
                padding
            ).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Household Remote", style = MaterialTheme.typography.headlineMedium)
            if (BuildConfig.FLAVOR ==
                "owner"
            ) {
                Text("Owner edition · parent features planned for v2")
            }
            Text(state.message)
            state.tvs.forEach { tv ->
                Button(onClick = {
                    model.select(tv)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("${if (state.selected?.id == tv.id) "● " else ""}${tv.name} · ${tv.ip}")
                }
            }
            Row {
                TextButton(onClick = model::discover) { Text("Discover") }
                TextButton(onClick = {
                    importing =
                        true
                }) { Text("Import JSON") }
            }
            OutlinedTextField(ip, {
                ip = it
            }, label = {
                Text("TV LAN IPv4 address")
            }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { model.add(ip) }, enabled = ip.isNotBlank()) { Text("Add TV") }
            state.selected?.let { tv ->
                HorizontalDivider()
                Text(tv.name, style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = { model.connect(false) }) { Text("Reconnect") }
                    TextButton(onClick = {
                        pairing =
                            true
                    }) { Text("Pair / re-pair") }
                    TextButton(onClick = model::wake) { Text("Wake") }
                }
                val enabled = state.ready && !state.busy
                KeyRow(
                    listOf("Home" to "KEY_HOME", "Up" to "KEY_UP", "Source" to "KEY_SOURCE"),
                    enabled,
                    model::tap
                )
                KeyRow(
                    listOf("Left" to "KEY_LEFT", "OK" to "KEY_ENTER", "Right" to "KEY_RIGHT"),
                    enabled,
                    model::tap
                )
                KeyRow(
                    listOf("Back" to "KEY_RETURN", "Down" to "KEY_DOWN", "Exit" to "KEY_EXIT"),
                    enabled,
                    model::tap
                )
                KeyRow(
                    listOf("Vol −" to "KEY_VOLDOWN", "Mute" to "KEY_MUTE", "Vol +" to "KEY_VOLUP"),
                    enabled,
                    model::tap
                )
                KeyRow(
                    listOf("Ch −" to "KEY_CHDOWN", "Standby" to "KEY_POWER", "Ch +" to "KEY_CHUP"),
                    enabled,
                    model::tap
                )
                Text("App shortcuts", style = MaterialTheme.typography.titleLarge)
                ShortcutProfile.apps.forEach { app ->
                    Row {
                        Button(
                            onClick = { model.shortcut(app) },
                            enabled =
                                enabled && app in state.configured
                        ) { Text(app) }
                        TextButton(onClick = {
                            steps = model.profileText(app)
                            calibration = app
                        }) { Text("Calibrate") }
                    }
                }
                if (state.busy) Button(onClick = model::cancelSequence) { Text("Cancel sequence") }
            }
            HorizontalDivider()
            UpdateControl()
        }
    }
    if (pairing) {
        AlertDialog(onDismissRequest = {
            pairing = false
        }, title = {
            Text("Trust this TV?")
        }, text = {
            Text(
                "Watch ${state.selected?.name} on your trusted home LAN. This replaces its saved certificate and token. Approve TV Tuneup on that TV within 90 seconds."
            )
        }, confirmButton = {
            TextButton(onClick = {
                pairing =
                    false
                model.connect(true)
            }) { Text("Pair this TV") }
        }, dismissButton = {
            TextButton(onClick = {
                pairing =
                    false
            }) { Text("Cancel") }
        })
    }
    if (importing) {
        AlertDialog(onDismissRequest = {
            importing = false
        }, title = { Text("Import local household JSON") }, text = {
            OutlinedTextField(manifest, {
                manifest =
                    it
            }, label = { Text("Paste exported manifest") })
        }, confirmButton = {
            TextButton(onClick = {
                model.importManifest(manifest)
                importing =
                    false
            }) { Text("Import") }
        }, dismissButton = {
            TextButton(onClick = {
                importing =
                    false
            }) { Text("Cancel") }
        })
    }
    calibration?.let { app ->
        AlertDialog(onDismissRequest = {
            calibration = null
        }, title = { Text("Calibrate $app while watching") }, text = {
            Column {
                Text(
                    "Use one KEY and delay in milliseconds per line. Start KEY_HOME; establish a repeatable position; finish KEY_ENTER. Example: KEY_HOME 1500. Blank disables. Test from Home and an open app."
                )
                OutlinedTextField(steps, { steps = it }, label = { Text("Navigation sequence") })
            }
        }, confirmButton = {
            TextButton(onClick = {
                model.saveProfile(app, steps)
                calibration = null
            }) { Text("Save") }
        }, dismissButton = {
            TextButton(onClick = {
                calibration =
                    null
            }) { Text("Cancel") }
        })
    }
}

@Composable
private fun KeyRow(keys: List<Pair<String, String>>, enabled: Boolean, tap: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { (label, key) ->
            Button(onClick = {
                tap(key)
            }, enabled = enabled, modifier = Modifier.weight(1f)) { Text(label) }
        }
    }
}
