package com.dirtyredz.householdremote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Quick actions shown from the remote's overflow button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSettingsSheet(
    tvName: String?,
    exitEnabled: Boolean,
    onReconnect: () -> Unit,
    onPair: () -> Unit,
    onExit: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(tvName ?: "No TV selected", style = MaterialTheme.typography.titleLarge)
            val button = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            OutlinedButton(onClick = onReconnect, enabled = tvName != null, modifier = button) {
                Text("Reconnect")
            }
            OutlinedButton(onClick = onPair, enabled = tvName != null, modifier = button) {
                Text("Pair / re-pair")
            }
            OutlinedButton(onClick = onExit, enabled = exitEnabled, modifier = button) {
                Text("Send Exit")
            }
            OutlinedButton(onClick = onSettings, modifier = button) { Text("Settings") }
        }
    }
}
