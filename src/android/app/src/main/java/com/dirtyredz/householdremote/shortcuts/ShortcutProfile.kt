package com.dirtyredz.householdremote.shortcuts

import kotlinx.serialization.Serializable

@Serializable
data class ShortcutStep(val key: String, val delayMs: Long = 500)

@Serializable
data class ShortcutProfile(val app: String, val steps: List<ShortcutStep>) {
    fun validate(): ShortcutProfile {
        require(app in apps)
        require(
            steps.size in 2..40 && steps.first().key == "KEY_HOME" &&
                steps.last().key == "KEY_ENTER"
        ) { "Start at Home and finish with Enter" }
        require(
            steps.all { it.key in navigation && it.delayMs in 100..5000 } &&
                steps.sumOf {
                    it.delayMs
                } <= 60_000
        ) { "Use navigation keys and delays of 100–5000 ms, up to 60 seconds" }
        return this
    }
    companion object {
        val apps = listOf("Netflix", "Prime", "Disney+", "Plex")
        val navigation =
            setOf(
                "KEY_HOME",
                "KEY_UP",
                "KEY_DOWN",
                "KEY_LEFT",
                "KEY_RIGHT",
                "KEY_ENTER",
                "KEY_RETURN"
            )
    }
}
