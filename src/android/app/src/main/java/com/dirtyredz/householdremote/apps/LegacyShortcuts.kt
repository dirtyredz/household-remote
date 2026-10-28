package com.dirtyredz.householdremote.apps

import android.content.Context

/**
 * v0.2.0 and earlier kept per-TV calibrated key sequences in the "shortcuts" preferences.
 * Nothing reads them any more; this deletes the file once so it does not linger. Failure is
 * harmless (the data is inert), so it is never surfaced.
 */
object LegacyShortcuts {
    private const val PREFERENCES = "shortcuts"

    fun discard(context: Context) {
        runCatching { context.deleteSharedPreferences(PREFERENCES) }
    }
}
