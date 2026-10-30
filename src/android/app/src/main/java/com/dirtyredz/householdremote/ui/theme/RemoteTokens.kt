package com.dirtyredz.householdremote.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
