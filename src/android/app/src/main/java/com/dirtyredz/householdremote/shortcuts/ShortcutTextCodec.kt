package com.dirtyredz.householdremote.shortcuts

object ShortcutTextCodec {
    fun encode(profile: ShortcutProfile): String =
        profile.validate().steps.joinToString("\n") { "${it.key} ${it.delayMs}" }

    fun decode(app: String, text: String): ShortcutProfile = ShortcutProfile(
        app,
        text.lines().filter { it.isNotBlank() }.map { line ->
            val parts = line.trim().split(Regex("\\s+"))
            require(parts.size == 2) { "Each line: KEY_HOME 1000" }
            ShortcutStep(parts[0], parts[1].toLong())
        }
    ).validate()
}
