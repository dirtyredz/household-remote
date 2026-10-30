package com.dirtyredz.householdremote.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Berry startup colour; keep in step with remote_colors.xml. */
val RemoteBackground = Color(0xFF581B49)
val RemoteBackgroundBottom = Color(0xFF24133D)

/** The power key's fill; white icons on it meet 4.5:1 contrast. */
val RemotePowerRed = Color(0xFFD32F2F)

private val colors = darkColorScheme(
    primary = Color(0xFFFF80BF),
    secondary = Color(0xFFBC9AFF),
    onSecondary = Color(0xFF24133D),
    onPrimary = Color(0xFF392548),
    primaryContainer = Color(0xFF633250),
    onPrimaryContainer = Color(0xFFFFF7FC),
    background = RemoteBackground,
    onBackground = Color(0xFFFFF7FC),
    surface = Color(0xFF24133D),
    onSurface = Color(0xFFFFF7FC),
    surfaceVariant = Color(0xFF392548),
    onSurfaceVariant = Color(0xFFDCCBDF),
    surfaceContainer = Color(0xFF392548),
    surfaceContainerHigh = Color(0xFF392548),
    surfaceContainerHighest = Color(0xFF614264),
    outline = Color(0xFFBC9AFF),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A06),
    tertiary = Color(0xFF7FD79B)
)

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
    MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography, content = content)
}
