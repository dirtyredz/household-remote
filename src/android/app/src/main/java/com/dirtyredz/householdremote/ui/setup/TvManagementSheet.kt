package com.dirtyredz.householdremote.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.dirtyredz.householdremote.inventory.Tv

/** TV list, discovery, manual IP and JSON import, kept off the default remote surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvManagementSheet(
    tvs: List<Tv>,
    selected: Tv?,
    message: String,
    onSelect: (Tv) -> Unit,
    onDiscover: () -> Unit,
    onImport: () -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var ip by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Televisions", style = MaterialTheme.typography.titleLarge)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            tvs.forEach { tv ->
                val current = selected?.id == tv.id
                val label = "${if (current) "● " else ""}${tv.name} · ${tv.ip}"
                if (current) {
                    Button(
                        onClick = { onSelect(tv) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) { Text(label) }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(tv) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) { Text(label) }
                }
            }
            Row {
                TextButton(onClick = onDiscover) { Text("Discover") }
                TextButton(onClick = onImport) { Text("Import JSON") }
            }
            HorizontalDivider()
            OutlinedTextField(
                ip,
                { ip = it },
                label = { Text("TV LAN IPv4 address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { onAdd(ip) }, enabled = ip.isNotBlank()) { Text("Add TV") }
        }
    }
}
