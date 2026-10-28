package com.dirtyredz.householdremote.ui.shortcuts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.shortcuts.ShortcutProfile

/**
 * Calibration editor for one app at a time, done while watching the selected TV. The text
 * uses the existing steps codec; blank disables the shortcut.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalibrationDialog(
    tvName: String,
    profileText: (String) -> String,
    onSave: (app: String, steps: String) -> Unit,
    onDismiss: () -> Unit
) {
    var app by rememberSaveable { mutableStateOf(ShortcutProfile.apps.first()) }
    var steps by rememberSaveable { mutableStateOf(profileText(ShortcutProfile.apps.first())) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calibrate $app on $tvName while watching") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShortcutProfile.apps.forEach { option ->
                        FilterChip(
                            selected = option == app,
                            onClick = {
                                app = option
                                steps = profileText(option)
                            },
                            label = { Text(option) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Text(
                    "Use one KEY and delay in milliseconds per line. Start KEY_HOME; establish a repeatable position; finish KEY_ENTER. Example: KEY_HOME 1500. Blank disables. Test from Home and an open app."
                )
                OutlinedTextField(steps, { steps = it }, label = { Text("Navigation sequence") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(app, steps) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
