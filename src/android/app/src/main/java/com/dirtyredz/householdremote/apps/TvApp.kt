package com.dirtyredz.householdremote.apps

/**
 * The four app buttons, launched directly by their Samsung Tizen app ID. The IDs were read
 * from both household TVs (GET /api/v2/applications/<id>, 2026-10-28); older store IDs for
 * Netflix and Plex return 404 there. [launchType] is the `action_type` sent with
 * `ed.apps.launch`.
 */
enum class TvApp(val label: String, val appId: String, val launchType: String = DEEP_LINK) {
    Netflix("Netflix", "3201907018807"),
    Prime("Prime", "3201910019365"),
    Disney("Disney+", "3201901017640"),
    Plex("Plex", "3201512006963");

    companion object {
        const val DEEP_LINK = "DEEP_LINK"
        const val NATIVE_LAUNCH = "NATIVE_LAUNCH"
    }
}

/** What the TV reported for one app. Only a definite "not installed" disables its button. */
enum class AppInstall {
    Installed,
    NotInstalled,
    Unknown;

    companion object {
        /** Maps the TV's reply to GET /api/v2/applications/<id>; anything unclear is Unknown. */
        fun fromHttpStatus(code: Int): AppInstall = when (code) {
            200 -> Installed
            404 -> NotInstalled
            else -> Unknown
        }
    }
}
