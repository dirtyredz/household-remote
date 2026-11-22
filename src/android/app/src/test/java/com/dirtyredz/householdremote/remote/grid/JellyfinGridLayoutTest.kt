package com.dirtyredz.householdremote.remote.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expectations transcribed independently from the 2026-11-22 photos of the TV keyboard. */
class JellyfinGridLayoutTest {
    private val letters = JellyfinGridLayout.letters
    private val symbols = JellyfinGridLayout.symbols
    private val keyboard = JellyfinGridLayout.keyboard

    /** [moves] is `from→to` pairs separated by `|`; `∅` means the move leaves the grid. */
    private fun GridLayout.expect(key: String, moves: String) {
        moves.split('|').forEach { pair ->
            val (from, to) = pair.trim().split('→')
            val landed = move(cell(from), "KEY_$key")?.label
            assertEquals("$from $key", to.takeUnless { it == "∅" }, landed)
        }
    }

    @Test fun lettersPageMatchesThePhoto() {
        letters.expect(
            "RIGHT",
            "a→b | g→www. | www.→backspace | backspace→∅ | n→.com | .com→!#1 | !#1→∅ | " +
                "u→: | :→globe | z→, | ,→. | .→/ | /→shift | shift→∅ | gear→space | " +
                "space→go | go→caret-left | caret-left→caret-right | caret-right→∅"
        )
        letters.expect("LEFT", "a→∅ | h→∅ | o→∅ | v→∅ | gear→∅ | space→gear | go→space")
        letters.expect("UP", "a→∅ | www.→∅ | backspace→∅ | h→a")
        // Bottom row (photo): gear under v, space under w..z, Go under , and ., then < and >.
        letters.expect(
            "DOWN",
            "v→gear | w→space | x→space | y→space | z→space | ,→go | .→go | " +
                "/→caret-left | shift→caret-right | gear→∅ | space→∅ | go→∅"
        )
        // Up from a wide key lands on its left column: unverified on the TV.
        letters.expect(
            "UP",
            "gear→v | space→w | go→, | caret-left→/ | caret-right→shift"
        )
    }

    @Test fun symbolsPageMatchesThePhoto() {
        symbols.expect("RIGHT", "0→backspace | ]→ABC | )→globe | unknown-3-9→1/2")
        symbols.expect("DOWN", "1→+ | +→! | !→- | -→gear | 8→> | >→*")
        // Space spans the six cells ' " : ; , ?; Go spans . and the unidentified glyph.
        symbols.expect(
            "DOWN",
            "'→space | \"→space | :→space | ;→space | ,→space | ?→space | .→go | " +
                "unknown-3-8→go | unknown-3-9→caret-left | 1/2→caret-right"
        )
        symbols.expect("UP", "space→' | go→.")
    }

    @Test fun onlyTextCellsAreCharacterTargets() {
        val targeted = (GridKeyboard.BACKSPACE..0xFFFF).flatMap { keyboard.targets(it).orEmpty() }
        val labels = targeted.map { it.label }.toSet()
        val navigationOnly = "gear go caret-left caret-right globe www. .com !#1 ABC 1/2 shift " +
            "unknown-3-8 unknown-3-9"
        navigationOnly.split(' ').forEach { assertFalse(it, it in labels) }
        val untypable = (0x20..0x7E).filter { keyboard.targets(it) == null }
            .map { it.toChar() }.joinToString("")
        assertEquals("\\`{|}~", untypable)
        assertNull(keyboard.targets('€'.code))
        assertEquals(listOf(GridKey("letters", "a", true)), keyboard.targets('A'.code))
    }

    @Test fun everyTargetIsReachableFromEveryCellWithoutBottomRowWaypoints() {
        for (page in keyboard.pages) {
            val layout = page.layout
            val switches = page.switches.map { it.label }
            val targets = layout.cells.filter {
                it.transit || it.label == GridKeyboard.SPACE || it.label in switches
            }
            for (from in layout.cells) {
                for (to in targets) {
                    var cursor = from
                    layout.path(from, to).forEach { key ->
                        assertTrue("${from.label}->${to.label}", cursor == from || cursor.transit)
                        cursor = requireNotNull(layout.move(cursor, key))
                    }
                    assertEquals(to, cursor)
                }
            }
        }
    }
}
