package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dirtyredz.householdremote.R
import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.power.PowerPhase
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionState
import com.dirtyredz.householdremote.remote.ConnectionStatus
import com.dirtyredz.householdremote.shortcuts.ShortcutProfile
import com.dirtyredz.householdremote.ui.RemoteState
import com.dirtyredz.householdremote.ui.theme.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Mounts the stateless screen with fake state and recording callbacks; no TV is contacted. */
@RunWith(AndroidJUnit4::class)
class RemoteScreenTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tv = Tv("living", "Living Room", "Test", "AA:BB:CC:DD:EE:FF", "192.168.1.5")
    private val connected = RemoteState(
        tvs = listOf(tv),
        selected = tv,
        message = "",
        connection = ConnectionStatus(ConnectionState.Connected, "Connected"),
        configured = setOf("Netflix")
    )
    private var state by mutableStateOf(connected)
    private val keys = mutableListOf<String>()
    private val events = mutableListOf<String>()
    private val callbacks = RemoteCallbacks(
        onKey = { keys += it },
        onPower = { events += "power" },
        onShortcut = { events += "shortcut:$it" },
        onCancelSequence = { events += "cancel" },
        onCalibrate = { events += "calibrate" },
        onPickTv = { events += "pick" },
        onOverflow = { events += "overflow" }
    )
    private val mappedKeys = listOf(
        "KEY_UP", "KEY_DOWN", "KEY_LEFT", "KEY_RIGHT", "KEY_ENTER",
        "KEY_RETURN", "KEY_HOME", "KEY_SOURCE",
        "KEY_VOLUP", "KEY_VOLDOWN", "KEY_MUTE", "KEY_CHUP", "KEY_CHDOWN"
    )

    private fun mount(fontScale: Float = 1f, width: Int = 360, height: Int = 760) =
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                RemoteTheme {
                    Box(Modifier.size(width.dp, height.dp)) { RemoteScreen(state, callbacks) }
                }
            }
        }

    private fun show(value: RemoteState) {
        state = value
        rule.waitForIdle()
    }

    private fun text(id: Int) = context.getString(id)

    @Test fun everyControlSendsItsMappedKeyAndMeetsTargetSize() {
        mount()
        mappedKeys.forEach { key ->
            rule.onNodeWithTag("key_$key").performScrollTo().assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp).performClick()
        }
        assertEquals(mappedKeys, keys)
        rule.onNodeWithTag("power").performClick()
        rule.onNodeWithTag("tv_picker").performClick()
        rule.onNodeWithTag("overflow").performClick()
        assertEquals(listOf("power", "pick", "overflow"), events)
    }

    @Test fun connectionIndicatorFollowsTypedState() {
        mount()
        val indicator = rule.onNodeWithTag("connection")
        indicator.assertTextContains(text(R.string.connection_connected))
        show(
            connected.copy(connection = ConnectionStatus(ConnectionState.Connecting, "Connecting"))
        )
        indicator.assertTextContains(text(R.string.connection_connecting))
        show(
            connected.copy(
                connection = ConnectionStatus(ConnectionState.AwaitingApproval, "Approve")
            )
        )
        indicator.assertTextContains(text(R.string.connection_approve))
        show(
            connected.copy(
                connection = ConnectionStatus(ConnectionState.Reconnecting, "Reconnecting…")
            )
        )
        indicator.assertTextContains(text(R.string.connection_reconnecting))
        show(
            connected.copy(
                connection = ConnectionStatus(ConnectionState.Unavailable, "Unavailable")
            )
        )
        indicator.assertTextContains(text(R.string.connection_unavailable))
        show(connected.copy(connection = ConnectionStatus.offline("Disconnected")))
        indicator.assertTextContains(text(R.string.connection_offline))
        show(connected.copy(selected = null))
        indicator.assertTextContains(text(R.string.connection_select_tv))
    }

    @Test fun inputIsUnavailableOfflineAndUnderPowerWork() {
        mount()
        for (blocked in listOf(
            connected.copy(connection = ConnectionStatus.offline("Disconnected")),
            connected.copy(power = PowerState(standbySent = true)),
            connected.copy(power = PowerState(phase = PowerPhase.Waking))
        )) {
            show(blocked)
            mappedKeys.forEach { key ->
                rule.onNodeWithTag("key_$key").performScrollTo().assertIsNotEnabled().performClick()
            }
        }
        assertTrue(keys.isEmpty())
    }

    @Test fun ordinaryKeysStayLiveWhileReconnectingButShortcutsDoNot() {
        mount()
        show(
            connected.copy(
                connection = ConnectionStatus(ConnectionState.Reconnecting, "Reconnecting…")
            )
        )
        rule.onNodeWithTag("key_KEY_UP").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag("shortcut_Netflix").performScrollTo().assertIsNotEnabled()
        assertEquals(listOf("KEY_UP"), keys)
    }

    @Test fun uncalibratedShortcutsAreDisabledBesideCalibrationAndSequencesCancel() {
        mount()
        rule.onNodeWithTag("shortcut_Netflix").performScrollTo().assertIsEnabled().performClick()
        ShortcutProfile.apps.filter { it != "Netflix" }.forEach { app ->
            rule.onNodeWithTag("shortcut_$app").performScrollTo().assertIsNotEnabled()
        }
        rule.onNodeWithTag("calibrate").performScrollTo().assertIsEnabled().performClick()
        show(connected.copy(busy = true))
        rule.onNodeWithTag("shortcut_Netflix").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("cancel_sequence").performScrollTo().performClick()
        assertEquals(listOf("shortcut:Netflix", "calibrate", "cancel"), events)
    }

    @Test fun compactWidthAndLargeTextKeepPowerVisibleAndTargetsFull() {
        mount(fontScale = 2f, width = 320, height = 640)
        rule.onNodeWithTag("tv_picker").assertIsDisplayed()
        rule.onNodeWithTag("connection").assertIsDisplayed()
        rule.onNodeWithTag("power").assertIsDisplayed().assertHeightIsAtLeast(64.dp)
        (
            mappedKeys.map {
                "key_$it"
            } + ShortcutProfile.apps.map { "shortcut_$it" }
            ).forEach { tag ->
            rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }
}
