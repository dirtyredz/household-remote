package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.R

/** Centered D-pad: four directional targets around a distinct OK button. */
@Composable
fun DirectionPad(enabled: Boolean, onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val arrow = Modifier.size(72.dp)
    Box(
        modifier
            .size(224.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
    ) {
        DirectionKey(
            R.drawable.ic_remote_up,
            R.string.key_up,
            "KEY_UP",
            enabled,
            onKey,
            arrow.align(Alignment.TopCenter)
        )
        DirectionKey(
            R.drawable.ic_remote_down,
            R.string.key_down,
            "KEY_DOWN",
            enabled,
            onKey,
            arrow.align(Alignment.BottomCenter)
        )
        DirectionKey(
            R.drawable.ic_remote_left,
            R.string.key_left,
            "KEY_LEFT",
            enabled,
            onKey,
            arrow.align(Alignment.CenterStart)
        )
        DirectionKey(
            R.drawable.ic_remote_right,
            R.string.key_right,
            "KEY_RIGHT",
            enabled,
            onKey,
            arrow.align(Alignment.CenterEnd)
        )
        val ok = stringResource(R.string.key_ok)
        RemoteButton(
            description = ok,
            onClick = { onKey("KEY_ENTER") },
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            minSize = 72.dp,
            tag = "key_KEY_ENTER",
            modifier = Modifier.size(80.dp).align(Alignment.Center)
        ) {
            Text(ok, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DirectionKey(
    icon: Int,
    description: Int,
    key: String,
    enabled: Boolean,
    onKey: (String) -> Unit,
    modifier: Modifier
) {
    RemoteIconButton(
        icon = icon,
        description = stringResource(description),
        onClick = { onKey(key) },
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tag = "key_$key",
        modifier = modifier
    )
}
