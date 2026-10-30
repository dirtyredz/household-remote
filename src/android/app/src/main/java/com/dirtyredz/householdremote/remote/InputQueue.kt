package com.dirtyredz.householdremote.remote

internal sealed interface InputEntry {
    val frame: String

    data class Key(override val frame: String) : InputEntry

    data class Launch(override val frame: String) : InputEntry

    class Snapshot(override var frame: String) : InputEntry

    data class End(override val frame: String) : InputEntry
}

/**
 * Ordered input awaiting the session worker. A live-text snapshot may replace only the still-unsent
 * snapshot at the tail. [InputEntry.End] is a reserved barrier: no later snapshot is accepted until
 * the worker removes it, and discarding text removes both kinds without disturbing controls.
 */
internal class InputQueue(private val controlLimit: Int = CONTROL_LIMIT) {
    private val entries = ArrayDeque<InputEntry>()
    private var liveSnapshot: InputEntry.Snapshot? = null
    private var ending = false

    val isNotEmpty: Boolean get() = entries.isNotEmpty()

    fun add(entry: InputEntry): Boolean {
        require(entry is InputEntry.Key || entry is InputEntry.Launch)
        if (entries.count { it is InputEntry.Key || it is InputEntry.Launch } >= controlLimit) {
            return false
        }
        entries.addLast(entry)
        liveSnapshot = null
        return true
    }

    fun snapshot(frame: String): Boolean {
        if (ending) return false
        val previous = liveSnapshot
        if (previous != null) {
            previous.frame = frame
        } else {
            liveSnapshot = InputEntry.Snapshot(frame).also(entries::addLast)
        }
        return true
    }

    fun end(frame: String): Boolean {
        if (ending) return false
        liveSnapshot = null
        ending = true
        entries.addLast(InputEntry.End(frame))
        return true
    }

    fun removeFirst(): InputEntry = entries.removeFirst().also { entry ->
        if (entry === liveSnapshot) liveSnapshot = null
        if (entry is InputEntry.End) ending = false
    }

    fun discardText() {
        entries.removeAll { it is InputEntry.Snapshot || it is InputEntry.End }
        liveSnapshot = null
        ending = false
    }

    fun clear() {
        entries.clear()
        liveSnapshot = null
        ending = false
    }

    private companion object {
        const val CONTROL_LIMIT = 16
    }
}
