package com.dirtyredz.householdremote.remote

data class GridPress(val key: String, val target: String? = null)

/** Tracks the projected tail of the ordered queue, never a cursor recovered from a connection. */
class GridTyping(
    private val enqueue: (List<GridPress>) -> Boolean,
    private val ignored: () -> Unit = {},
    private val layout: GridLayout = GridLayout.household
) {
    private var cursor: GridCell? = null

    fun open() {
        cursor = layout.cells.first { it.label == "a" }
    }

    fun reset() {
        cursor = null
    }

    fun edit(previous: String, next: String): Boolean {
        var position = cursor ?: return false
        // A grid has no text caret. Replace the changed suffix (including any unchanged tail).
        val prefix = previous.commonPrefixWith(next).length
        val presses = mutableListOf<GridPress>()
        fun press(label: String) {
            val target = layout.cells.first { it.label == label }
            presses += (layout.path(position, target) + "KEY_ENTER").map { GridPress(it, label) }
            position = target
        }
        previous.substring(prefix).codePoints().forEach { press("backspace") }
        next.substring(prefix).codePoints().forEach { code ->
            when (code) {
                in '0'.code..'9'.code -> presses += GridPress("KEY_${code.toChar()}")
                in 'A'.code..'Z'.code -> press(code.toChar().lowercase())
                in 'a'.code..'z'.code -> press(code.toChar().toString())
                ' '.code -> press("space")
                else -> ignored()
            }
        }
        if (!enqueue(presses)) {
            reset()
            return false
        }
        cursor = position
        return true
    }
}
