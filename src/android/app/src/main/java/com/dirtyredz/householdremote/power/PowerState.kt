package com.dirtyredz.householdremote.power

/** The power operation in progress, independent of general messages and shortcut ownership. */
enum class PowerPhase { Idle, Waking, Connecting, SendingStandby }

/** What one power activation will do. Decided when the activation is accepted. */
enum class PowerIntent { Standby, Wake }

/**
 * Power state for the selected TV. [standbySent] is the requested-off intent: standby was
 * accepted by the session, which is not proof the panel turned off. [error] persists until
 * the next activation or a reset, so unrelated status updates cannot erase it.
 */
data class PowerState(
    val phase: PowerPhase = PowerPhase.Idle,
    val standbySent: Boolean = false,
    val error: String? = null
) {
    val idle: Boolean get() = phase == PowerPhase.Idle

    /** A usable session is inferred to mean "on"; anything else means "off or unreachable". */
    fun intent(connected: Boolean): PowerIntent =
        if (connected && !standbySent) PowerIntent.Standby else PowerIntent.Wake
}
