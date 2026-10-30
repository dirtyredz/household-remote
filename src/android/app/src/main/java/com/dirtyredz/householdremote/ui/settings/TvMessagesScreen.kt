package com.dirtyredz.householdremote.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TvMessagesScreen(messages: StateFlow<List<String>>, onBack: () -> Unit) {
    val entries by messages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row {
                TextButton(onClick = onBack) { Text("Back") }
                TextButton(onClick = {
                    val clipboard = context.getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("TV messages", entries.joinToString("\n\n"))
                    )
                }) { Text("Copy") }
            }
            Text("TV messages", style = MaterialTheme.typography.titleLarge)
            Text("Current session • last 100 messages • identifying values redacted")
            if (entries.isEmpty()) Text("No messages yet")
            LazyColumn {
                items(entries) { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
            }
        }
    }
}
