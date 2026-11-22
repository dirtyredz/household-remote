package com.dirtyredz.householdremote.remote.grid

data class GridPress(val key: String, val target: String? = null)

/** Tracks the projected tail of the ordered queue, never a cursor recovered from a connection. */
class GridTyping(
    private val enqueue: (List<GridPress>) -> Boolean,
    private val ignored: () -> Unit = {},
    private var keyboard: GridKeyboard = GridKeyboard.household
) {
    private var focus: GridFocus? = null

    /** A different TV keyboard: the projected focus is meaningless until [open]. */
    fun use(keyboard: GridKeyboard) {
        this.keyboard = keyboard
        reset()
    }

    /** A fresh keyboard shows its first page, lowercase, highlighted on its start cell. */
    fun open() {
        focus = keyboard.start
    }

    fun reset() {
        focus = null
    }

    fun edit(previous: String, next: String): Boolean {
        val walk = GridWalk(keyboard, focus ?: return false)
        // A grid has no text caret. Replace the changed suffix (including any unchanged tail).
        val prefix = previous.commonPrefixWith(next).length
        previous.substring(prefix).codePoints().forEach { walk.type(GridKeyboard.BACKSPACE) }
        next.substring(prefix).codePoints().forEach { if (!walk.type(it)) ignored() }
        if (!enqueue(walk.presses)) {
            reset()
            return false
        }
        focus = walk.focus
        return true
    }
}

/** Plans one batch of presses from a projected focus; nothing is sent while planning. */
private class GridWalk(private val keyboard: GridKeyboard, var focus: GridFocus) {
    val presses = mutableListOf<GridPress>()

    /** Plans [code], or plans nothing and returns false when this keyboard cannot type it. */
    fun type(code: Int): Boolean {
        if (keyboard.directDigits && code in '0'.code..'9'.code) {
            presses += GridPress("KEY_${code.toChar()}")
            return true
        }
        val candidates = keyboard.targets(code) ?: return false
        val key = candidates.firstOrNull { it.page == focus.page.name } ?: candidates.first()
        val switch = if (key.page == focus.page.name) {
            null
        } else {
            focus.page.switches.firstOrNull { it.to == key.page } ?: return false
        }
        val shift = keyboard.shift
        // Shift state survives page switches; under OneShot it is only on just before a capital.
        val needsShift = key.shifted != null && key.shifted != focus.shifted
        if (needsShift && shift?.page != key.page) return false

        if (switch != null) {
            press(switch.label)
            val page = keyboard.page(switch.to)
            focus = focus.copy(page = page, cell = page.layout.cell(switch.landing))
        }
        if (needsShift) {
            press(requireNotNull(shift).label)
            focus = focus.copy(shifted = !focus.shifted)
        }
        press(key.label)
        if (key.shifted == true && keyboard.shiftMode == ShiftMode.OneShot) {
            focus = focus.copy(shifted = false)
        }
        return true
    }

    private fun press(label: String) {
        val layout = focus.page.layout
        val target = layout.cell(label)
        presses += (layout.path(focus.cell, target) + "KEY_ENTER").map { GridPress(it, label) }
        focus = focus.copy(cell = target)
    }
}
