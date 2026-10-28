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

/** Charcoal background; keep in step with res/values/remote_colors.xml remote_background. */
val RemoteBackground = Color(0xFF121417)

private val colors = darkColorScheme(
    primary = Color(0xFF7CC4FA),
    onPrimary = Color(0xFF0B2236),
    primaryContainer = Color(0xFF1F4A6E),
    onPrimaryContainer = Color(0xFFDCEEFF),
    background = RemoteBackground,
    onBackground = Color(0xFFECEEF1),
    surface = RemoteBackground,
    onSurface = Color(0xFFECEEF1),
    surfaceVariant = Color(0xFF262A30),
    onSurfaceVariant = Color(0xFFC3C8D0),
    surfaceContainer = Color(0xFF1C1F24),
    surfaceContainerHigh = Color(0xFF262A30),
    surfaceContainerHighest = Color(0xFF30353C),
    outline = Color(0xFF8A919B),
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
