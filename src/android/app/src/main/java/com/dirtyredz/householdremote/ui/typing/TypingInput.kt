package com.dirtyredz.householdremote.ui.typing

/** Routes one keyboard generation according to whether this TV attempt opened its IME. */
internal class TypingInput(
    private val sendSnapshot: (String) -> Boolean,
    private val sendDigit: (Char) -> Boolean
) {
    private var draft = ""
    private var tvImeActive = false

    fun open(tvImeActive: Boolean) {
        draft = ""
        this.tvImeActive = tvImeActive
    }

    fun tvImeStarted() {
        tvImeActive = true
    }

    fun reset() {
        draft = ""
        tvImeActive = false
    }

    fun changed(next: String): Boolean {
        val digit = insertedDigit(draft, next)
        draft = next
        return if (!tvImeActive && digit != null) sendDigit(digit) else sendSnapshot(next)
    }

    private fun insertedDigit(previous: String, next: String): Char? {
        if (next.length != previous.length + 1) return null
        val insertion = previous.indices.firstOrNull { previous[it] != next[it] } ?: previous.length
        val digit = next[insertion]
        if (digit !in '0'..'9') return null
        return digit.takeIf {
            previous.regionMatches(insertion, next, insertion + 1, previous.length - insertion)
        }
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
