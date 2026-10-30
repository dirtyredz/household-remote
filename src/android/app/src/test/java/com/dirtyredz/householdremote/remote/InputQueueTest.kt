package com.dirtyredz.householdremote.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputQueueTest {
    @Test fun typedControlsKeepTheirOrderAndShareTheBound() {
        val queue = InputQueue(controlLimit = 2)
        val key = InputEntry.Key("key")
        val launch = InputEntry.Launch("launch")
        assertTrue(queue.add(key))
        assertTrue(queue.add(launch))
        assertFalse(queue.add(InputEntry.Key("overflow")))
        assertEquals(key, queue.removeFirst())
        assertEquals(launch, queue.removeFirst())
    }

    @Test fun snapshotReplacesOnlyTheUnsentTailAndEndIsABarrier() {
        val queue = InputQueue()
        assertTrue(queue.snapshot("a"))
        assertTrue(queue.snapshot("ab"))
        assertTrue(queue.end("end"))
        assertFalse(queue.snapshot("blocked"))
        assertFalse(queue.end("duplicate"))
        assertEquals("ab", queue.removeFirst().frame)
        assertEquals(InputEntry.End("end"), queue.removeFirst())
        assertTrue(queue.snapshot("new session"))
    }

    @Test fun discardingTextPreservesControlsAndRemovesTheEndBarrier() {
        val queue = InputQueue()
        val key = InputEntry.Key("key")
        val launch = InputEntry.Launch("launch")
        queue.add(key)
        queue.snapshot("text")
        queue.end("end")
        queue.add(launch)
        queue.discardText()
        assertEquals(key, queue.removeFirst())
        assertEquals(launch, queue.removeFirst())
        assertTrue(queue.snapshot("fresh"))
    }
}
