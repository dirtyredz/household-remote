package com.dirtyredz.householdremote.ui.setup

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Local household JSON import; the pasted text never leaves the device. */
@Composable
fun ImportManifestDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var manifest by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import local household JSON") },
        text = {
            OutlinedTextField(
                manifest,
                { manifest = it },
                label = { Text("Paste exported manifest") }
            )
        },
        confirmButton = { TextButton(onClick = { onImport(manifest) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
