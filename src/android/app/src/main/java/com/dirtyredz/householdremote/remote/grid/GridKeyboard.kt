package com.dirtyredz.householdremote.remote.grid

/** Pressing [label] shows page [to] with the TV highlight on its [landing] cell. */
data class PageSwitch(val label: String, val to: String, val landing: String)

/** One keyboard page. Its [switches] are navigation targets only, never typed text. */
class GridPage(
    val name: String,
    val layout: GridLayout,
    val switches: List<PageSwitch> = emptyList()
)

/** Where one character is typed. [shifted] is null when the key has no case to match. */
data class GridKey(val page: String, val label: String, val shifted: Boolean? = null)

/** The projected TV state at the tail of the ordered queue. */
data class GridFocus(val page: GridPage, val cell: GridCell, val shifted: Boolean = false)

/** What one shift press does. Unverified on the TV, so it is injected rather than assumed. */
enum class ShiftMode {
    /** Shift applies to the next letter only, then the keyboard reverts to lowercase. */
    OneShot,

    /** Shift stays on until it is pressed again. */
    Toggle
}

/**
 * A TV letter grid as data: pages, page switches, the starting highlight, optional shift and
 * which character each cell types. Only single-character labels, `space` and `backspace` type
 * text; every other label (Go, gear, globe, arrows, shift, page switches) is never a character
 * target. Keyboards without [shift] fold uppercase letters to lowercase.
 */
class GridKeyboard(
    val pages: List<GridPage>,
    val shift: GridKey? = null,
    val shiftMode: ShiftMode = ShiftMode.OneShot,
    /** Digits use KEY_0..KEY_9 and never move the highlight (shared apps; Netflix PINs). */
    val directDigits: Boolean = false,
    startLabel: String = "a"
) {
    val start = GridFocus(pages.first(), pages.first().layout.cell(startLabel))
    private val targets: Map<Int, List<GridKey>> = buildTargets()

    fun page(name: String): GridPage = pages.first { it.name == name }

    /** Candidate keys in page order, or null when nothing on this keyboard types [code]. */
    fun targets(code: Int): List<GridKey>? = targets[code]

    private fun buildTargets(): Map<Int, List<GridKey>> {
        val found = LinkedHashMap<Int, MutableList<GridKey>>()
        fun add(code: Int, key: GridKey) {
            found.getOrPut(code) { mutableListOf() }.add(key)
        }
        for (page in pages) {
            for (cell in page.layout.cells) {
                val label = cell.label
                when {
                    label == SPACE -> add(' '.code, GridKey(page.name, label))

                    label == BACKSPACE_LABEL -> add(BACKSPACE, GridKey(page.name, label))

                    label.codePointCount(0, label.length) == 1 -> {
                        val code = label.codePointAt(0)
                        if (code in 'a'.code..'z'.code) {
                            val cased = shift != null
                            add(code, GridKey(page.name, label, if (cased) false else null))
                            add(
                                code - 'a'.code + 'A'.code,
                                GridKey(page.name, label, if (cased) true else null)
                            )
                        } else {
                            add(code, GridKey(page.name, label))
                        }
                    }
                }
            }
        }
        return found
    }

    companion object {
        /** The pseudo character a removed draft character types. */
        const val BACKSPACE = -1
        const val SPACE = "space"
        const val BACKSPACE_LABEL = "backspace"

        /** The owner-verified Prime Video / Netflix / YouTube grid. */
        val household = GridKeyboard(
            listOf(GridPage("letters", GridLayout.household)),
            directDigits = true
        )
    }
}
