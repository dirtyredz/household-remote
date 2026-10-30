package com.dirtyredz.householdremote.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTokensTest {
    @Test fun finalHouseholdPalette() {
        assertEquals(0xFF000000.toInt(), RemotePalette.background.toArgb())
        assertEquals(0xFF4A0F2A.toInt(), RemotePalette.glow.toArgb())
        assertEquals(0xFFFF1493.toInt(), RemoteTokens.accent.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), RemoteTokens.content.toArgb())
    }

    @Test fun disabledLabelsRemainReadableAndDistinct() {
        for (surface in listOf(
            RemoteTokens.surface,
            RemoteTokens.ringSurface,
            RemoteTokens.disabledHeart
        )) {
            assertTrue(contrast(RemoteTokens.disabledContent, surface) >= 4.5f)
        }
        assertNotEquals(RemoteTokens.content, RemoteTokens.disabledContent)
        assertNotEquals(RemoteTokens.ring(true), RemoteTokens.ring(false))
        assertNotEquals(RemoteTokens.controlBorder(true), RemoteTokens.controlBorder(false))
        assertNotEquals(RemoteTokens.accent, RemoteTokens.disabledHeart)
    }

    @Test fun whiteHeartLabelAndOfflineTextHaveContrast() {
        // The owner's exact hot pink/white pairing has >3:1, but is below 4.5:1.
        assertTrue(contrast(RemoteTokens.content, RemoteTokens.accent) >= 3f)
        assertTrue(contrast(RemotePalette.muted, RemotePalette.background) >= 4.5f)
        assertTrue(contrast(RemotePalette.connected, RemotePalette.background) >= 4.5f)
    }

    @Test fun materialFixedRolesDoNotFallBackToLavender() {
        assertEquals(RemoteTokens.accent, remoteColors.primaryFixed)
        assertEquals(RemoteTokens.accent, remoteColors.secondaryFixed)
        assertEquals(RemotePalette.glow, remoteColors.primaryFixedDim)
        assertEquals(RemotePalette.glow, remoteColors.secondaryFixedDim)
        assertEquals(RemotePalette.connected, remoteColors.tertiaryFixed)
    }

    private fun contrast(light: Color, dark: Color): Float =
        (light.luminance() + 0.05f) / (dark.luminance() + 0.05f)
}
