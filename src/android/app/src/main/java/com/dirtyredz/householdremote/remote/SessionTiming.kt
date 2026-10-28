package com.dirtyredz.householdremote.remote

import kotlin.math.min

/**
 * Foreground connection timing. Defaults are proposed, not measured Samsung requirements
 * (see docs/DECISIONS.md); tests shrink them. The heartbeat is the WebSocket ping/pong of the
 * TV client only, never an application key.
 */
data class SessionTiming(
    /** WebSocket ping interval; a missing pong fails the socket within about two intervals. */
    val pingIntervalMs: Long = 15_000,
    /** Whole saved-credential attempt (TCP, TLS, upgrade, channel connect). */
    val attemptTimeoutMs: Long = 10_000,
    /** Explicit pairing keeps the TV's on-screen approval window. */
    val approvalMs: Long = SamsungProtocol.APPROVAL_MS,
    /** Wait before each automatic attempt; the last value repeats. */
    val backoffMs: List<Long> = listOf(1_000, 2_000, 4_000, 8_000, 15_000),
    /** One outage gets this long to recover before the remote reports Unavailable. */
    val recoveryWindowMs: Long = 60_000,
    /** A connection this old resets the failure streak, so a flapping link keeps backing off. */
    val stableMs: Long = 30_000
) {
    init {
        require(backoffMs.isNotEmpty())
    }

    fun backoff(failures: Int): Long = backoffMs[min(failures, backoffMs.lastIndex)]
}
