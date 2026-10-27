package com.dirtyredz.householdremote.shortcuts

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ShortcutStore(context: Context) {
    private val preferences = context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
    fun get(mac: String, app: String): ShortcutProfile? = try {
        preferences.getString("$mac/$app", null)?.let {
            Json.decodeFromString<ShortcutProfile>(it).validate()
        }
    } catch (
        _: Exception
    ) {
        null
    }
    fun save(mac: String, profile: ShortcutProfile) {
        check(
            preferences.edit().putString(
                "$mac/${profile.app}",
                Json.encodeToString(profile.validate())
            ).commit()
        )
    }
    fun remove(mac: String, app: String) {
        preferences.edit().remove("$mac/$app").apply()
    }
}
