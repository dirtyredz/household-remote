package com.dirtyredz.householdremote

import com.dirtyredz.householdremote.remote.KeyDispatcher
import com.dirtyredz.householdremote.shortcuts.ShortcutProfile
import com.dirtyredz.householdremote.shortcuts.ShortcutStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutCancellationTest {
    @Test fun cancelledSequenceCannotClearReplacementOwnership() = runTest {
        val dispatcher = KeyDispatcher(this, { true }, {})
        val profile = ShortcutProfile(
            "Plex",
            listOf(ShortcutStep("KEY_HOME", 1000), ShortcutStep("KEY_ENTER", 100))
        )
        dispatcher.start()
        dispatcher.run(profile)
        runCurrent()
        dispatcher.start()
        dispatcher.run(profile)
        runCurrent()
        try {
            assertTrue("Replacement sequence must still own input", dispatcher.busy)
        } finally {
            dispatcher.cancel()
        }
    }
}
