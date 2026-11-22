package com.dirtyredz.householdremote.remote.grid

/**
 * Jellyfin's Samsung-style TV keyboard, transcribed from owner photos (2026-11-22): the
 * letters page and symbols page 1/2. Page 2/2 (currency, shapes) is not modelled. Bottom-row
 * wide keys and the page-switch landings are photo-derived and unverified on the TV; the
 * bottom row is never a waypoint (see GridCell.transit). Multi-character labels (www., .com,
 * !#1, ABC, 1/2, gear, globe, go, shift, the arrow keys and the unidentified glyphs) are
 * navigation-only and never typed as text.
 */
object JellyfinGridLayout {
    const val LETTERS = "letters"
    const val SYMBOLS = "symbols"

    val letters = GridLayout(
        rows(
            listOf("a", "b", "c", "d", "e", "f", "g", "www.", GridKeyboard.BACKSPACE_LABEL),
            listOf("h", "i", "j", "k", "l", "m", "n", ".com", "!#1"),
            listOf("o", "p", "q", "r", "s", "t", "u", ":", "globe"),
            listOf("v", "w", "x", "y", "z", ",", ".", "/", "shift")
        ) + bottomRow(space = 1..4, go = 5..6, arrows = 7)
    )

    val symbols = GridLayout(
        rows(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", GridKeyboard.BACKSPACE_LABEL),
            listOf("+", "×", "÷", "=", "/", "_", "<", ">", "[", "]", "ABC"),
            listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "globe"),
            listOf("-", "'", "\"", ":", ";", ",", "?", ".", "unknown-3-8", "unknown-3-9", "1/2")
        ) + bottomRow(space = 1..6, go = 7..8, arrows = 9)
    )

    val keyboard = GridKeyboard(
        pages = listOf(
            GridPage(LETTERS, letters, listOf(PageSwitch("!#1", SYMBOLS, "ABC"))),
            GridPage(SYMBOLS, symbols, listOf(PageSwitch("ABC", LETTERS, "!#1")))
        ),
        shift = GridKey(LETTERS, "shift"),
        shiftMode = ShiftMode.OneShot
    )

    private fun rows(vararg labels: List<String>): List<GridCell> =
        labels.flatMapIndexed { row, cells ->
            cells.mapIndexed { column, label -> GridCell(label, row, column..column) }
        }

    private fun bottomRow(space: IntRange, go: IntRange, arrows: Int): List<GridCell> = listOf(
        GridCell("gear", 4, 0..0, transit = false),
        GridCell(GridKeyboard.SPACE, 4, space, transit = false),
        GridCell("go", 4, go, transit = false),
        GridCell("caret-left", 4, arrows..arrows, transit = false),
        GridCell("caret-right", 4, arrows + 1..arrows + 1, transit = false)
    )
}
