package com.dirtyredz.householdremote.ui.typing

import android.content.Context
import com.dirtyredz.householdremote.apps.TvApp
import com.dirtyredz.householdremote.remote.grid.GridKeyboard
import com.dirtyredz.householdremote.remote.grid.JellyfinGridLayout

/** Which TV letter grid no-IME typing walks. The phone cannot see the TV's foreground app. */
enum class TypingLayout(val keyboard: GridKeyboard) {
    /** The owner-verified Prime Video / Netflix / YouTube grid. */
    Shared(GridKeyboard.household),

    /** Jellyfin's Samsung-style keyboard: letters, shift and symbols page 1/2. */
    Jellyfin(JellyfinGridLayout.keyboard);

    companion object {
        /** The layout an app button implies; the owner can still override it in Settings. */
        fun forApp(app: TvApp): TypingLayout = if (app == TvApp.Jellyfin) Jellyfin else Shared

        fun parse(name: String?): TypingLayout = entries.firstOrNull { it.name == name } ?: Shared
    }
}

/** Owns the persisted typing-layout choice, beside the grid delay in the same preferences. */
class TypingLayoutSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): TypingLayout = TypingLayout.parse(preferences.getString(KEY_LAYOUT, null))

    fun save(layout: TypingLayout) {
        preferences.edit().putString(KEY_LAYOUT, layout.name).apply()
    }

    private companion object {
        const val PREFERENCES = "typing"
        const val KEY_LAYOUT = "gridLayout"
    }
}
