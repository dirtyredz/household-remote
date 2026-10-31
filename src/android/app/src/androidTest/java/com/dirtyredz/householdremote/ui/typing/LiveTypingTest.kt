package com.dirtyredz.householdremote.ui.typing

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import com.dirtyredz.householdremote.ui.theme.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Fake callbacks only; no ViewModel, network or TV. */
class LiveTypingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun editsEmitFullSnapshotsWithoutAVisibleStrip() {
        val changes = mutableListOf<String>()
        var closes = 0
        rule.setContent {
            RemoteTheme { LiveTyping(onTextChanged = { changes += it }, onClose = { closes++ }) }
        }
        rule.runOnIdle { assertEquals(emptyList<String>(), changes) }
        rule.onNodeWithText("Typing on TV…").assertDoesNotExist()
        rule.onNodeWithText("Close").assertDoesNotExist()
        for (value in listOf("a", "ab", "")) {
            rule.onNodeWithTag("live_typing_field").performTextReplacement(value)
        }
        rule.runOnIdle {
            assertEquals(listOf("a", "ab", ""), changes)
            assertEquals(0, closes)
        }
    }
}
