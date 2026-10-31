package com.dirtyredz.householdremote.remote

/** Column ranges describe wide keys. Vertical exits always use the left column of a key. */
data class GridCell(val label: String, val row: Int, val columns: IntRange)

class GridLayout(val cells: List<GridCell>) {
    fun move(from: GridCell, key: String): GridCell? = when (key) {
        "KEY_LEFT" -> cells.find { it.row == from.row && it.columns.last == from.columns.first - 1 }

        "KEY_RIGHT" -> cells.find {
            it.row == from.row && it.columns.first == from.columns.last + 1
        }

        "KEY_UP" -> cells.find { it.row == from.row - 1 && from.columns.first in it.columns }

        "KEY_DOWN" -> cells.find { it.row == from.row + 1 && from.columns.first in it.columns }

        else -> null
    }

    /** Breadth-first search on the directed grid, with no edge-crossing moves. */
    fun path(from: GridCell, to: GridCell): List<String> {
        require(from in cells && to in cells)
        val pending = ArrayDeque<Pair<GridCell, List<String>>>()
        val seen = mutableSetOf(from)
        pending.add(from to emptyList())
        while (pending.isNotEmpty()) {
            val (cell, keys) = pending.removeFirst()
            if (cell == to) return keys
            for (key in directions) {
                val next = move(cell, key) ?: continue
                if (seen.add(next)) pending.add(next to keys + key)
            }
        }
        error("Disconnected grid")
    }

    companion object {
        val directions = listOf("KEY_UP", "KEY_DOWN", "KEY_LEFT", "KEY_RIGHT")
        val household = GridLayout(
            listOf("abcdef", "ghijkl", "mnopqr", "stuvwx", "yz1234", "567890")
                .flatMapIndexed { row, letters ->
                    letters.mapIndexed { column, letter ->
                        GridCell(
                            letter.toString(),
                            row,
                            column..column
                        )
                    }
                } + listOf(
                GridCell("space", 6, 0..1),
                GridCell("backspace", 6, 2..3),
                GridCell("clear", 6, 4..5)
            )
        )
    }
}
