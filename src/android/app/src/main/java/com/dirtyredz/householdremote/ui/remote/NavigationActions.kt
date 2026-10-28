package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.R

private data class NavigationKey(val key: String, val icon: Int, val label: Int)

private val navigationKeys = listOf(
    NavigationKey("KEY_RETURN", R.drawable.ic_remote_back, R.string.key_back),
    NavigationKey("KEY_HOME", R.drawable.ic_remote_home, R.string.key_home),
    NavigationKey("KEY_SOURCE", R.drawable.ic_remote_source, R.string.key_source)
)

/** Back, Home and Source with icon and text labels. */
@Composable
fun NavigationActions(enabled: Boolean, onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        navigationKeys.forEach { item ->
            val label = stringResource(item.label)
            RemoteButton(
                description = label,
                onClick = { onKey(item.key) },
                enabled = enabled,
                tag = "key_${item.key}",
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    Modifier.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(painterResource(item.icon), contentDescription = null)
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
