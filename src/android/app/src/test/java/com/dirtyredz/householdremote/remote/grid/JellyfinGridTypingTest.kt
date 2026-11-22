package com.dirtyredz.householdremote.remote.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinGridTypingTest {
    private val presses = mutableListOf<GridPress>()
    private var ignored = 0

    private fun typing(keyboard: GridKeyboard = JellyfinGridLayout.keyboard) =
        GridTyping({ presses.addAll(it) }, { ignored++ }, keyboard).also { it.open() }

    /** "D3 R E" -> KEY_DOWN x3, KEY_RIGHT, KEY_ENTER. */
    private fun keys(spec: String): List<String> = spec.split(' ').flatMap { token ->
        val key = when (token[0]) {
            'U' -> "KEY_UP"
            'D' -> "KEY_DOWN"
            'L' -> "KEY_LEFT"
            'R' -> "KEY_RIGHT"
            else -> "KEY_ENTER"
        }
        List(token.drop(1).toIntOrNull() ?: 1) { key }
    }

    private fun entered() = presses.filter { it.key == "KEY_ENTER" }.map { it.target }

    @Test fun spaceUsesTheWideKeyAndUpLandsOnItsLeftColumn() {
        assertTrue(typing().edit("", "a b"))
        // a; down to v, right to w, down onto space; up from space lands on w, then up to b.
        assertEquals(keys("E D3 R D E U4 E"), presses.map { it.key })
        assertEquals(listOf("a", "space", "b"), entered())
    }

    @Test fun mixedCaseDigitsAndSymbolsSwitchPagesAndShiftOnce() {
        assertTrue(typing().edit("", "Ab1_z"))
        val expected = listOf(
            "D3 R8 E", // shift
            "U3 L8 E", // A (one-shot shift has reverted)
            "R E", // b
            "D R7 E", // !#1 -> symbols page, highlight on ABC
            "U L10 E", // 1
            "D R5 E", // _
            "R5 E", // ABC -> letters page, highlight on !#1
            "D2 L4 E" // z
        ).flatMap { keys(it) }
        assertEquals(expected, presses.map { it.key })
        assertEquals(listOf("shift", "a", "b", "!#1", "1", "_", "ABC", "z"), entered())
        assertEquals(0, ignored)
    }

    @Test fun lettersPagePunctuationStaysOnTheLettersPage() {
        assertTrue(typing().edit("", ".:"))
        assertEquals(keys("D3 R6 E U R E"), presses.map { it.key })
        assertEquals(listOf(".", ":"), entered())
    }

    @Test fun backspaceUsesTheCurrentPagesKey() {
        val grid = typing()
        grid.edit("", "1")
        presses.clear()
        assertTrue(grid.edit("1", ""))
        assertEquals(keys("R10 E"), presses.map { it.key })
        assertEquals(listOf("backspace"), entered())
    }

    @Test fun unsupportedCharactersAreIgnoredWithoutPresses() {
        assertTrue(typing().edit("", "a~é€b"))
        assertEquals(3, ignored)
        assertEquals(keys("E R E"), presses.map { it.key })
        assertFalse(presses.any { it.target in setOf("go", "gear", "globe", "shift", "!#1") })
    }

    @Test fun toggleShiftIsInjectableAndPressedAgainForLowercase() {
        val sticky = GridKeyboard(
            JellyfinGridLayout.keyboard.pages,
            shift = GridKey(JellyfinGridLayout.LETTERS, "shift"),
            shiftMode = ShiftMode.Toggle
        )
        assertTrue(typing(sticky).edit("", "ABa"))
        assertEquals(listOf("shift", "a", "b", "shift", "a"), entered())
    }

    @Test fun reopeningAssumesLettersPageLowercaseOnA() {
        val grid = typing()
        grid.edit("", "1")
        grid.reset()
        assertFalse(grid.edit("1", "12"))
        grid.open()
        presses.clear()
        assertTrue(grid.edit("", "h"))
        assertEquals(listOf(GridPress("KEY_DOWN", "h"), GridPress("KEY_ENTER", "h")), presses)
    }

    @Test fun switchingKeyboardsInvalidatesFocus() {
        val grid = GridTyping({ presses.addAll(it) })
        grid.open()
        grid.use(JellyfinGridLayout.keyboard)
        assertFalse(grid.edit("", "h"))
        grid.open()
        assertTrue(grid.edit("", "h"))
        assertEquals(keys("D E"), presses.map { it.key })
    }
}
