package com.dirtyredz.householdremote.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/** Opaque controls keep the rose glow behind the remote, never inside its keys. */
object RemoteTokens {
    val surface = RemotePalette.control
    val rockerSurface = RemotePalette.surface
    val ringSurface = RemotePalette.surface
    val content = RemotePalette.text
    val disabledContent = RemotePalette.disabled
    val disabledHeart = RemotePalette.disabledSurface
    val accent = RemotePalette.accent
    private val outline = accent.copy(alpha = 0.28f)
    private val disabledOutline = RemotePalette.disabled.copy(alpha = 0.3f)

    fun controlBorder(enabled: Boolean) =
        BorderStroke(1.dp, if (enabled) outline else disabledOutline)
    fun ring(enabled: Boolean) = if (enabled) accent else RemotePalette.disabled
}

/** The measured D-pad centre anchors the glow in both compact and tall layouts. */
fun remoteBackgroundBrush(center: Offset, radius: Float): Brush = Brush.radialGradient(
    0f to RemotePalette.glow,
    0.55f to RemotePalette.glow.copy(alpha = 0.5f),
    1f to RemotePalette.background,
    center = center,
    radius = radius.coerceAtLeast(1f)
)

/** Measured glow geometry shared by the remote background and its D-pad anchor. */
@Stable
class RemoteGlowState internal constructor() {
    internal var backgroundOrigin by mutableStateOf(Offset.Zero)
    internal var padCenter by mutableStateOf(Offset.Zero)
    internal var radius by mutableStateOf(1f)
}

@Composable
fun rememberRemoteGlowState(): RemoteGlowState = remember { RemoteGlowState() }

/** Draws the remote background with its glow centred on the measured anchor. */
fun Modifier.remoteGlow(state: RemoteGlowState): Modifier =
    onGloballyPositioned { state.backgroundOrigin = it.positionInRoot() }
        .drawWithCache {
            val brush = remoteBackgroundBrush(
                state.padCenter - state.backgroundOrigin,
                state.radius
            )
            onDrawBehind { drawRect(brush) }
        }

/** Marks the D-pad whose measured centre and width define the remote glow. */
fun Modifier.remoteGlowAnchor(state: RemoteGlowState): Modifier = onGloballyPositioned {
    state.padCenter = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
    state.radius = it.size.width * 0.85f
}
