package com.dirtyredz.householdremote.ui.typing

import com.dirtyredz.householdremote.remote.GridTyping
import com.dirtyredz.householdremote.remote.KeyDispatcher
import com.dirtyredz.householdremote.remote.SamsungProtocol
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypingInputTest {
    @Test fun imeStartDiscardsGridAndPreservesFullDraftSnapshots() {
        val snapshots = mutableListOf<String>()
        val gridKeys = mutableListOf<String>()
        val input = TypingInput(
            { snapshots.add(it) },
            GridTyping({ presses -> gridKeys.addAll(presses.map { it.key }) }),
            { gridKeys.clear() }
        )
        input.open(tvImeActive = false)
        input.changed("h")
        assertTrue(gridKeys.isNotEmpty())
        input.tvImeStarted()
        input.changed("h1")
        input.changed("h")
        input.changed("")
        input.changed("é")
        assertTrue(gridKeys.isEmpty())
        assertEquals(listOf("h1", "h", "", "é"), snapshots)
    }

    @Test fun digitWithoutTvImeUsesRemoteKeyWithoutDuplicateTextOrEnter() {
        val frames = mutableListOf<String>()
        val input = TypingInput(
            { frames.add(SamsungProtocol.text(it)) },
            GridTyping({ presses ->
                frames.addAll(presses.map { SamsungProtocol.key(it.key) })
                true
            })
        )

        input.open(tvImeActive = false)
        input.changed("1")

        assertEquals(listOf(SamsungProtocol.key("KEY_1")), frames)
        assertFalse(frames.any { it.contains("KEY_ENTER") })
    }

    @Test fun digitWithTvImeUsesOnlyFullStringSnapshots() {
        val frames = mutableListOf<String>()
        val input = TypingInput(
            { frames.add(SamsungProtocol.text(it)) },
            GridTyping({ presses ->
                frames.addAll(presses.map { SamsungProtocol.key(it.key) })
                true
            })
        )

        input.open(tvImeActive = true)
        input.changed("a")
        input.changed("a1")

        assertEquals(listOf(SamsungProtocol.text("a"), SamsungProtocol.text("a1")), frames)
        assertFalse(frames.any { it.contains("SendRemoteKey") || it.contains("KEY_ENTER") })
    }

    @Test fun reconnectAndNewKeyboardGenerationResetTvImeState() {
        val frames = mutableListOf<String>()
        val input = TypingInput(
            { frames.add(SamsungProtocol.text(it)) },
            GridTyping({ presses ->
                frames.addAll(presses.map { SamsungProtocol.key(it.key) })
                true
            })
        )

        input.open(tvImeActive = true)
        input.changed("1")
        input.reset()
        assertFalse(input.changed("stale"))
        input.open(tvImeActive = false)
        input.changed("2")

        assertEquals(listOf(SamsungProtocol.text("1"), SamsungProtocol.key("KEY_2")), frames)
    }

    @Test fun imeVisibilityClosesExactlyOnceAfterItWasVisible() {
        val visibility = ImeVisibilityState()

        assertFalse(visibility.changed(visible = false))
        assertFalse(visibility.changed(visible = true))
        assertTrue(visibility.changed(visible = false))
        assertFalse(visibility.changed(visible = false))
    }

    @Test fun everyCancellationSourceKeepsGridAndTextQueueSemantics() = runTest {
        for (source in CancellationSource.entries) {
            val sent = mutableListOf<String>()
            val dispatcher = KeyDispatcher(backgroundScope, { sent.add(it) }, {})
            val input = TypingInput(
                dispatcher::text,
                GridTyping(dispatcher::grid),
                dispatcher::discardGrid
            )
            dispatcher.start()
            input.open(tvImeActive = false)
            input.changed("h")
            dispatcher.text("pending")

            when (source) {
                CancellationSource.Close -> {
                    input.reset()
                    dispatcher.endText()
                }

                CancellationSource.Dismiss -> {
                    input.reset()
                    dispatcher.discardText()
                }

                CancellationSource.Stop -> {
                    input.reset()
                    dispatcher.discardText()
                    dispatcher.cancel()
                }

                CancellationSource.Disconnect,
                CancellationSource.TvSwitch -> {
                    dispatcher.cancel()
                    input.reset()
                    dispatcher.discardText()
                }
            }
            runCurrent()

            val expected = if (source == CancellationSource.Close) {
                listOf(SamsungProtocol.text("pending"), SamsungProtocol.inputEnd())
            } else {
                emptyList()
            }
            assertEquals(source.name, expected, sent)
            dispatcher.cancel()
        }
    }

    private enum class CancellationSource {
        Close,
        Dismiss,
        Stop,
        Disconnect,
        TvSwitch
    }
}
