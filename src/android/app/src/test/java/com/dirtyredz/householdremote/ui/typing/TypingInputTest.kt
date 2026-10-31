package com.dirtyredz.householdremote.ui.typing

import com.dirtyredz.householdremote.remote.SamsungProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingInputTest {
    @Test fun digitWithoutTvImeUsesRemoteKeyWithoutDuplicateTextOrEnter() {
        val frames = mutableListOf<String>()
        val input = TypingInput(
            { frames.add(SamsungProtocol.text(it)) },
            { frames.add(SamsungProtocol.key("KEY_$it")) }
        )

        input.open(tvImeActive = false)
        input.changed("a")
        input.changed("a1")

        assertEquals(listOf(SamsungProtocol.text("a"), SamsungProtocol.key("KEY_1")), frames)
        assertFalse(frames.any { it.contains("KEY_ENTER") })
    }

    @Test fun digitWithTvImeUsesOnlyFullStringSnapshots() {
        val frames = mutableListOf<String>()
        val input = TypingInput(
            { frames.add(SamsungProtocol.text(it)) },
            { frames.add(SamsungProtocol.key("KEY_$it")) }
        )

        input.open(tvImeActive = true)
        input.changed("a")
        input.changed("a1")

        assertEquals(listOf(SamsungProtocol.text("a"), SamsungProtocol.text("a1")), frames)
        assertFalse(frames.any { it.contains("SendRemoteKey") || it.contains("KEY_ENTER") })
    }

    @Test fun reconnectAndNewKeyboardGenerationResetTvImeState() {
        val frames = mutableListOf<String>()
        val input = TypingInput(
            { frames.add(SamsungProtocol.text(it)) },
            { frames.add(SamsungProtocol.key("KEY_$it")) }
        )

        input.open(tvImeActive = true)
        input.changed("1")
        input.reset()
        input.open(tvImeActive = false)
        input.changed("2")

        assertEquals(listOf(SamsungProtocol.text("1"), SamsungProtocol.key("KEY_2")), frames)
    }

    @Test fun imeVisibilityClosesExactlyOnceAfterItWasVisible() {
        val visibility = ImeVisibilityState()

        assertFalse(visibility.changed(visible = false))
        assertFalse(visibility.changed(visible = true))
        assertTrue(visibility.changed(visible = false))
        assertFalse(visibility.changed(visible = false))
    }
}
