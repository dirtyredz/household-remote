package com.dirtyredz.householdremote.ui.typing

import com.dirtyredz.householdremote.remote.GridTyping

/** Routes one keyboard generation according to whether this TV attempt opened its IME. */
internal class TypingInput(
    private val sendSnapshot: (String) -> Boolean,
    private val grid: GridTyping,
    private val discardGrid: () -> Unit = {}
) {
    private var draft = ""
    private var tvImeActive = false
    private var opened = false

    fun open(tvImeActive: Boolean) {
        reset()
        opened = true
        this.tvImeActive = tvImeActive
        if (!tvImeActive) grid.open()
    }

    fun tvImeStarted() {
        discardGrid()
        grid.reset()
        tvImeActive = true
    }

    fun reset() {
        discardGrid()
        grid.reset()
        draft = ""
        tvImeActive = false
        opened = false
    }

    fun changed(next: String): Boolean {
        if (!opened) return false
        val accepted = if (tvImeActive) sendSnapshot(next) else grid.edit(draft, next)
        draft = next
        return accepted
    }
}

/** Latches the first visible-to-hidden transition so duplicate inset updates close only once. */
internal class ImeVisibilityState {
    private var seen = false
    private var closed = false

    fun changed(visible: Boolean): Boolean {
        if (visible) seen = true
        if (!visible && seen && !closed) {
            closed = true
            return true
        }
        return false
    }
}
