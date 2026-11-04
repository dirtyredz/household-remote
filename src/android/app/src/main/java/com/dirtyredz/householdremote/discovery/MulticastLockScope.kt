package com.dirtyredz.householdremote.discovery

import android.content.Context
import android.net.wifi.WifiManager

/** The slice of [WifiManager.MulticastLock] discovery needs, so the lifecycle is JVM-testable. */
interface MulticastLockHandle {
    fun acquire()
    fun release()
}

/** Android drops inbound multicast/broadcast while the screen is off or the radio is power-saving unless a lock is held. */
class AndroidMulticastLock(context: Context) : MulticastLockHandle {
    private val lock = context.applicationContext.getSystemService(WifiManager::class.java)
        .createMulticastLock("household-discovery").apply { setReferenceCounted(false) }

    override fun acquire() = lock.acquire()
    override fun release() {
        if (lock.isHeld) lock.release()
    }
}

/**
 * Holds the lock for the whole of [block] (acquire first, release last, also when [block] throws or is
 * cancelled). A failed acquire never reaches [block] and never releases a lock that was not taken.
 */
inline fun <T> MulticastLockHandle.withLock(block: () -> T): T {
    acquire()
    try {
        return block()
    } finally {
        release()
    }
}
