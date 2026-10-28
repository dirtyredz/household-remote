package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.dirtyredz.householdremote.R

/** Smallest pad: 48 dp arrows above and below a 54 dp OK. Below this the floor holds. */
val DirectionPadMin = 150.dp

/** Largest pad, so a tall phone does not get a dinner-plate D-pad. */
val DirectionPadMax = 300.dp

/** Side of the pad for the space it is given, clamped to [DirectionPadMin]..[DirectionPadMax]. */
fun directionPadSide(maxWidth: Dp, maxHeight: Dp): Dp =
    min(maxWidth, maxHeight).coerceIn(DirectionPadMin, DirectionPadMax)

/**
 * Centered D-pad: four directional targets around a distinct OK button. It fills the space
 * its parent gives it, so it is the part of the remote that shrinks on short screens; every
 * key keeps the 48 dp touch-target floor.
 */
@Composable
fun DirectionPad(enabled: Boolean, onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = directionPadSide(maxWidth, maxHeight)
        val arrow = Modifier.size(max(48.dp, side * 0.32f))
        Box(
            Modifier
                .size(side)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                .testTag("direction_pad")
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
                minSize = 48.dp,
                tag = "key_KEY_ENTER",
                modifier = Modifier.size(max(54.dp, side * 0.36f)).align(Alignment.Center)
            ) {
                Text(ok, style = MaterialTheme.typography.titleMedium)
            }
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
