package com.dirtyredz.householdremote.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridTypingTest {
    private val layout = GridLayout.household

    @Test fun everyPairIsShortestAndNeverCrossesAnEdge() {
        // Independent all-pairs distance oracle (Floyd-Warshall).
        val cells = layout.cells
        val distance = Array(cells.size) { a ->
            IntArray(cells.size) { b ->
                if (a ==
                    b
                ) {
                    0
                } else {
                    999
                }
            }
        }
        fun neighbor(cell: GridCell, key: String): GridCell? {
            val bottom = cell.row == 6
            val column = cell.columns.first
            val (row, col) = when (key) {
                "KEY_UP" -> cell.row - 1 to column
                "KEY_DOWN" -> cell.row + 1 to column
                "KEY_LEFT" -> cell.row to column - if (bottom) 2 else 1
                else -> cell.row to column + if (bottom) 2 else 1
            }
            return cells.find { it.row == row && col in it.columns }
        }
        cells.forEachIndexed { a, cell ->
            GridLayout.directions.forEach { key ->
                val next = neighbor(cell, key)
                assertEquals(next, layout.move(cell, key))
                if (next != null) distance[a][cells.indexOf(next)] = 1
            }
        }
        for (k in cells.indices) {
            for (a in cells.indices) {
                for (b in cells.indices) {
                    distance[a][b] = minOf(distance[a][b], distance[a][k] + distance[k][b])
                }
            }
        }
        cells.forEachIndexed { a, from ->
            cells.forEachIndexed { b, to ->
                val path = layout.path(from, to)
                assertEquals(distance[a][b], path.size)
                var cursor = from
                path.forEach { cursor = requireNotNull(neighbor(cursor, it)) }
                assertEquals(to, cursor)
            }
        }
    }

    @Test fun honeyIExactSequenceAndLabels() {
        val presses = mutableListOf<GridPress>()
        val grid = GridTyping({ presses.addAll(it) })
        grid.open()
        assertTrue(grid.edit("", "honey i"))
        val expected = listOf(
            "DOWN RIGHT ENTER", // h
            "DOWN RIGHT ENTER", // o
            "LEFT ENTER", // n
            "UP UP RIGHT RIGHT RIGHT ENTER", // e
            "DOWN DOWN DOWN DOWN LEFT LEFT LEFT LEFT ENTER", // y
            "DOWN DOWN ENTER", // space
            "RIGHT UP UP UP UP UP ENTER" // i, via backspace -> 7 (shortest)
        ).flatMap { it.split(' ').map { key -> "KEY_$key" } }
        assertEquals(expected, presses.map { it.key })
        assertEquals(
            listOf("h", "o", "n", "e", "y", "space", "i"),
            presses.filter {
                it.key ==
                    "KEY_ENTER"
            }.map { it.target }
        )
    }

    @Test fun digitsDoNotMoveCursorAndUppercaseSpaceBackspaceAreGridKeys() {
        val presses = mutableListOf<GridPress>()
        var ignored = 0
        val grid = GridTyping({ presses.addAll(it) }, { ignored++ })
        grid.open()
        grid.edit("", "H12I!")
        assertEquals(
            listOf(
                "KEY_DOWN",
                "KEY_RIGHT",
                "KEY_ENTER",
                "KEY_1",
                "KEY_2",
                "KEY_RIGHT",
                "KEY_ENTER"
            ),
            presses.map {
                it.key
            }
        )
        assertEquals(1, ignored)
        presses.clear()
        grid.edit("H12I!", "H12")
        assertEquals(2, presses.count { it.key == "KEY_ENTER" && it.target == "backspace" })
        presses.clear()
        grid.edit("H12", "H12 ")
        assertEquals(listOf("KEY_LEFT", "KEY_ENTER"), presses.map { it.key })
        grid.reset()
        assertFalse(grid.edit("", "a"))
        grid.open()
        presses.clear()
        grid.edit("", "a")
        assertEquals(listOf(GridPress("KEY_ENTER", "a")), presses)
    }

    @Test fun rejectedBatchMakesPositionUnknownUntilReopened() {
        var accepts = false
        val grid = GridTyping({ accepts })
        grid.open()
        assertFalse(grid.edit("", "h"))
        accepts = true
        assertFalse(grid.edit("h", "hi"))
        grid.open()
        assertTrue(grid.edit("", "a"))
    }
}
