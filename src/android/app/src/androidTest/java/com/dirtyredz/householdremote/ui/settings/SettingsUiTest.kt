package com.dirtyredz.householdremote.ui.settings

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dirtyredz.householdremote.BuildConfig
import com.dirtyredz.householdremote.ui.theme.RemoteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pure Compose coverage: fake callbacks only, with no updater or TV connection. */
@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun overflowContainsQuickActionsAndSettingsButNoUpdates() {
        var settingsOpened = false
        rule.setContent {
            RemoteTheme {
                RemoteSettingsSheet(
                    tvName = "Living Room",
                    exitEnabled = true,
                    onReconnect = {},
                    onPair = {},
                    onExit = {},
                    onSettings = { settingsOpened = true },
                    onDismiss = {}
                )
            }
        }

        listOf("Reconnect", "Pair / re-pair", "Send Exit", "Settings").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
        rule.onNodeWithText("Check for update").assertDoesNotExist()
        rule.onNodeWithText("Settings").performClick()
        rule.runOnIdle { assertTrue(settingsOpened) }
    }

    @Test fun settingsScreenShowsUpdateSectionAndTopBarBack() {
        var backed = false
        rule.setContent {
            RemoteTheme {
                SettingsScreen(
                    onBack = { backed = true },
                    updateContent = { Text("Manual update controls") }
                )
            }
        }

        rule.onNodeWithTag("settings_screen").assertIsDisplayed()
        rule.onNodeWithText("Settings").assertIsDisplayed()
        rule.onNodeWithText("App updates").assertIsDisplayed()
        rule.onNodeWithText("Current version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        rule.onNodeWithText("Manual update controls").assertIsDisplayed()
        rule.onNodeWithTag("settings_back").performClick()
        rule.runOnIdle { assertTrue(backed) }
    }
}
