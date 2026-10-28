package com.dirtyredz.householdremote.ui.setup

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Explicit trust/re-pair confirmation, bound to the TV it was opened for. */
@Composable
fun PairingDialog(tvName: String, onPair: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trust this TV?") },
        text = {
            Text(
                "Watch $tvName on your trusted home LAN. This replaces its saved certificate and token. Approve TV Tuneup on that TV within 90 seconds."
            )
        },
        confirmButton = { TextButton(onClick = onPair) { Text("Pair this TV") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
