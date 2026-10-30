package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One rocker key: icon, spoken description and the key it sends. */
class RockerKey(val icon: Int, val description: String, val key: String)

/**
 * One labelled up/down key pair, shared by volume and channel. Vertical like a physical
 * remote when there is height to spare; [horizontal] (down, label, up in one 48 dp row) on
 * short screens or large text, so the remote never needs to scroll.
 */
@Composable
fun RemoteRocker(
    label: String,
    up: RockerKey,
    down: RockerKey,
    enabled: Boolean,
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false
) {
    val background =
        modifier.background(
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
            MaterialTheme.shapes.large
        )
    if (horizontal) {
        Row(background, verticalAlignment = Alignment.CenterVertically) {
            RockerButton(down, enabled, onKey, Modifier.size(48.dp))
            RockerLabel(label, Modifier.weight(1f))
            RockerButton(up, enabled, onKey, Modifier.size(48.dp))
        }
    } else {
        Column(
            background,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            RockerButton(up, enabled, onKey, Modifier.fillMaxWidth())
            RockerLabel(label)
            RockerButton(down, enabled, onKey, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RockerButton(
    key: RockerKey,
    enabled: Boolean,
    onKey: (String) -> Unit,
    modifier: Modifier
) {
    RemoteIconButton(
        key.icon,
        key.description,
        { onKey(key.key) },
        modifier,
        enabled,
        MaterialTheme.shapes.large,
        tag = "key_${key.key}"
    )
}

@Composable
private fun RockerLabel(label: String, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    CompositionLocalProvider(LocalContentColor provides muted) {
        FitText(label, MaterialTheme.typography.labelMedium, modifier)
    }
}
