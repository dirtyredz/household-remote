package com.dirtyredz.householdremote.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The power key's fill; white icons on it meet 4.5:1 contrast. */
val RemotePowerRed = RemotePalette.power

internal val remoteColors = with(RemotePalette) {
    darkColorScheme(
        primary = accent, onPrimary = text,
        primaryContainer = glow, onPrimaryContainer = text,
        secondary = accent, onSecondary = text,
        secondaryContainer = control, onSecondaryContainer = text,
        tertiary = connected, onTertiary = background,
        tertiaryContainer = control, onTertiaryContainer = text,
        background = background, onBackground = text,
        surface = surface, onSurface = text,
        surfaceVariant = control, onSurfaceVariant = muted,
        surfaceTint = accent, inverseSurface = text, inverseOnSurface = surface,
        inversePrimary = glow,
        surfaceDim = surface, surfaceBright = control,
        surfaceContainerLowest = background, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = control,
        surfaceContainerHighest = disabledSurface,
        outline = accent, outlineVariant = muted,
        error = error, onError = onError,
        errorContainer = onError, onErrorContainer = error,
        scrim = background,
        primaryFixed = accent, primaryFixedDim = glow,
        onPrimaryFixed = text, onPrimaryFixedVariant = text,
        secondaryFixed = accent, secondaryFixedDim = glow,
        onSecondaryFixed = text, onSecondaryFixedVariant = text,
        tertiaryFixed = connected, tertiaryFixedDim = control,
        onTertiaryFixed = background, onTertiaryFixedVariant = text
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

private val typography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = remoteColors,
        shapes = shapes,
        typography = typography,
        content = content
    )
}
