package com.dirtyredz.householdremote.remote

import android.content.Context

/** One definition of the user-configurable delay between app-grid key presses. */
object GridPacing {
    const val MIN_MS = 200L
    const val MAX_MS = 1_000L
    const val DEFAULT_MS = 400L
    const val STEP_MS = 50L

    fun coerce(delayMs: Long): Long = delayMs.coerceIn(MIN_MS, MAX_MS)
}

/** Owns the persisted grid-pacing setting and its private preference names. */
class GridPacingSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): Long = GridPacing.coerce(preferences.getLong(KEY_DELAY_MS, GridPacing.DEFAULT_MS))

    fun save(delayMs: Long) {
        preferences.edit().putLong(KEY_DELAY_MS, GridPacing.coerce(delayMs)).apply()
    }

    private companion object {
        const val PREFERENCES = "typing"
        const val KEY_DELAY_MS = "gridDelayMs"
    }
}
