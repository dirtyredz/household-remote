package com.dirtyredz.householdremote.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GridQueueTest {
    @Test fun reopenedKeyboardQueuesBehindPreviousInputEnd() = runTest {
        val frames = mutableListOf<String>()
        val dispatcher = KeyDispatcher(this, { frames.add(it) }, {})
        dispatcher.start()
        dispatcher.grid(listOf(GridPress("KEY_DOWN", "h"), GridPress("KEY_ENTER", "h")))
        runCurrent()
        dispatcher.discardGrid()
        dispatcher.endText()
        assertTrue(dispatcher.grid(listOf(GridPress("KEY_ENTER", "a"))))
        advanceUntilIdle()
        assertEquals(
            listOf(
                SamsungProtocol.key("KEY_DOWN"),
                SamsungProtocol.inputEnd(),
                SamsungProtocol.key("KEY_ENTER")
            ),
            frames
        )
        dispatcher.cancel()
    }

    @Test fun configuredDelayAndFastTypingKeepExactOrder() = runTest {
        for (delayMs in listOf(200L, 400L, 1000L)) {
            val frames = mutableListOf<String>()
            val times = mutableListOf<Long>()
            val notes = mutableListOf<String>()
            val dispatcher =
                KeyDispatcher(this, {
                    frames.add(it)
                    times.add(testScheduler.currentTime)
                    true
                }, {}, gridDelayMs = delayMs, gridAttempt = { _, target -> notes.add(target) })
            assertEquals(delayMs, dispatcher.gridDelayMs)
            dispatcher.start()
            val grid = GridTyping(dispatcher::grid)
            grid.open()
            grid.edit("", "h")
            grid.edit("h", "hi")
            advanceUntilIdle()
            assertEquals(
                listOf("DOWN", "RIGHT", "ENTER", "RIGHT", "ENTER").map {
                    SamsungProtocol.key("KEY_$it")
                },
                frames
            )
            assertEquals(List(4) { delayMs }, times.zipWithNext { a, b -> b - a })
            assertEquals(listOf("h", "h", "h", "i", "i"), notes)
            dispatcher.cancel()
        }
    }

    @Test fun gridPacingHasOneBoundedDefinition() {
        assertEquals(200L, GridPacing.coerce(Long.MIN_VALUE))
        assertEquals(400L, GridPacing.DEFAULT_MS)
        assertEquals(1_000L, GridPacing.coerce(Long.MAX_VALUE))
        assertEquals(50L, GridPacing.STEP_MS)
    }

    @Test fun closeDropsPendingGridIncludingDigitsButPreservesOrdinaryKeys() = runTest {
        val frames = mutableListOf<String>()
        val dispatcher = KeyDispatcher(this, { frames.add(it) }, {})
        dispatcher.start()
        val grid = GridTyping(dispatcher::grid)
        grid.open()
        grid.edit("", "h123")
        runCurrent()
        advanceTimeBy(100)
        dispatcher.discardGrid()
        dispatcher.tap("KEY_HOME")
        advanceUntilIdle()
        assertEquals(listOf("KEY_DOWN", "KEY_HOME").map(SamsungProtocol::key), frames)
        dispatcher.cancel()
    }

    @Test fun teardownDropsQueueAndNeverReplaysAfterReconnect() = runTest {
        val frames = mutableListOf<String>()
        val dispatcher = KeyDispatcher(this, { frames.add(it) }, {})
        assertFalse(dispatcher.grid(listOf(GridPress("KEY_ENTER", "a"))))
        dispatcher.start()
        assertTrue(dispatcher.grid(listOf(GridPress("KEY_DOWN", "h"), GridPress("KEY_ENTER", "h"))))
        runCurrent()
        dispatcher.cancel()
        dispatcher.start()
        advanceUntilIdle()
        assertEquals(listOf(SamsungProtocol.key("KEY_DOWN")), frames)
        dispatcher.cancel()
    }
}
