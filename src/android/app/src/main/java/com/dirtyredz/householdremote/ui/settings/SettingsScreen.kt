package com.dirtyredz.householdremote.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dirtyredz.householdremote.BuildConfig
import kotlinx.coroutines.flow.StateFlow

/** Full-screen settings surface, separate from the remote's quick actions. */
@Composable
fun SettingsDialog(
    messages: StateFlow<List<String>>,
    gridDelayMs: Long,
    onGridDelay: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var showMessages by remember { mutableStateOf(false) }
        if (showMessages) {
            TvMessagesScreen(messages, onBack = { showMessages = false })
        } else {
            SettingsScreen(
                onBack = onDismiss,
                onMessages = { showMessages = true },
                gridDelayMs = gridDelayMs,
                onGridDelay = onGridDelay
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onMessages: () -> Unit = {},
    gridDelayMs: Long = 400,
    onGridDelay: (Long) -> Unit = {},
    updateContent: @Composable () -> Unit = { UpdateControl() }
) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize().testTag("settings_screen")) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        TextButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                            Text("Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onMessages) { Text("TV messages") }
                Text("Grid typing delay: $gridDelayMs ms")
                Slider(
                    value = gridDelayMs.toFloat(),
                    onValueChange = { onGridDelay((it / 50).toLong() * 50) },
                    valueRange = 200f..1000f,
                    steps = 15
                )
                Text("Open app search fresh with the highlight on a before opening Keyboard.")
                Text("App updates", style = MaterialTheme.typography.titleLarge)
                HorizontalDivider(Modifier.fillMaxWidth())
                Text("Current version ${BuildConfig.VERSION_NAME}")
                updateContent()
            }
        }
    }
}
