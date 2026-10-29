package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dirtyredz.householdremote.R
import com.dirtyredz.householdremote.apps.AppInstall
import com.dirtyredz.householdremote.apps.TvApp
import com.dirtyredz.householdremote.inventory.Tv
import com.dirtyredz.householdremote.power.PowerPhase
import com.dirtyredz.householdremote.power.PowerState
import com.dirtyredz.householdremote.remote.ConnectionState
import com.dirtyredz.householdremote.remote.ConnectionStatus
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
        apps = mapOf(TvApp.Plex to AppInstall.NotInstalled)
    )
    private var state by mutableStateOf(connected)
    private val keys = mutableListOf<String>()
    private val events = mutableListOf<String>()
    private val callbacks = RemoteCallbacks(
        onKey = { keys += it },
        onPower = { events += "power" },
        onLaunchApp = { events += "app:${it.name}" },
        onPickTv = { events += "pick" },
        onOverflow = { events += "overflow" },
        onWifiSettings = { events += "wifi" }
    )
    private val mappedKeys = listOf(
        "KEY_UP", "KEY_DOWN", "KEY_LEFT", "KEY_RIGHT", "KEY_ENTER",
        "KEY_RETURN", "KEY_HOME", "KEY_SOURCE",
        "KEY_VOLUP", "KEY_VOLDOWN", "KEY_MUTE", "KEY_CHUP", "KEY_CHDOWN"
    )
    private val allTargets = listOf("tv_picker", "overflow", "power") +
        mappedKeys.map { "key_$it" } + TvApp.entries.map { "app_${it.name}" }

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

    /** Every target is on screen, inside the window, above the 48 dp floor, in remote order. */
    private fun assertFits(width: Int, height: Int) {
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .assertCountEquals(0)
        allTargets.forEach { tag ->
            val node = rule.onNodeWithTag(tag).assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$tag above the top", bounds.top >= 0.dp)
            assertTrue("$tag below ${height}dp: ${bounds.bottom}", bounds.bottom <= height.dp)
            assertTrue("$tag beyond ${width}dp: ${bounds.right}", bounds.right <= width.dp)
        }
        val order = listOf(
            "tv_picker",
            "direction_pad",
            "key_KEY_RETURN",
            "key_KEY_VOLDOWN",
            "app_Netflix"
        )
        order.zipWithNext().forEach { (above, below) ->
            val upper = rule.onNodeWithTag(above).getUnclippedBoundsInRoot()
            val lower = rule.onNodeWithTag(below).getUnclippedBoundsInRoot()
            assertTrue("$above overlaps $below", upper.bottom <= lower.top)
        }
    }

    @Test fun everyControlSendsItsMappedKeyAndMeetsTargetSize() {
        mount()
        mappedKeys.forEach { key ->
            rule.onNodeWithTag("key_$key").assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp).performClick()
        }
        assertEquals(mappedKeys, keys)
        rule.onNodeWithTag("power").performClick()
        rule.onNodeWithTag("tv_picker").performClick()
        rule.onNodeWithTag("overflow").performClick()
        assertEquals(listOf("power", "pick", "overflow"), events)
    }

    @Test fun remoteHasNoScrollAndFitsSmallAndOwnerPhones() {
        mount(fontScale = 1f, width = 360, height = 640)
        assertFits(360, 640)
    }

    @Test fun remoteFits360x640AtLargeText() {
        mount(fontScale = 1.3f, width = 360, height = 640)
        assertFits(360, 640)
    }

    @Test fun remoteFitsOwnerPhoneAtDefaultText() {
        mount(fontScale = 1f, width = 411, height = 891)
        assertFits(411, 891)
    }

    @Test fun remoteFitsOwnerPhoneAtLargeText() {
        mount(fontScale = 1.3f, width = 411, height = 891)
        assertFits(411, 891)
    }

    @Test fun remoteFitsWithTheWifiActionAtLargeText() {
        state = connected.copy(
            onWifi = false,
            connection = ConnectionStatus(ConnectionState.Unavailable, ConnectionStatus.NO_WIFI)
        )
        mount(fontScale = 1.3f, width = 360, height = 640)
        assertFits(360, 640)
    }

    @Test fun connectionStatusIsShownOnceWithNoBottomMessageLine() {
        mount()
        rule.onAllNodesWithText(text(R.string.connection_connected)).assertCountEquals(1)
        rule.onAllNodesWithText("Apps").assertCountEquals(0)
        rule.onNodeWithTag("notice", useUnmergedTree = true).assertDoesNotExist()
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
        indicator.assertTextContains(text(R.string.connection_unavailable_short))
        show(connected.copy(connection = ConnectionStatus.offline("Disconnected")))
        indicator.assertTextContains(text(R.string.connection_offline))
        show(connected.copy(selected = null))
        indicator.assertDoesNotExist()
    }

    @Test fun noWifiShowsTheHintTheSettingsActionAndDisablesPowerAndKeys() {
        mount()
        show(
            connected.copy(
                onWifi = false,
                connection = ConnectionStatus(ConnectionState.Unavailable, ConnectionStatus.NO_WIFI)
            )
        )
        rule.onNodeWithTag("connection")
            .assertTextContains(text(R.string.connection_no_wifi_short)).performClick()
        rule.onNodeWithTag("power").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("key_KEY_UP").assertIsNotEnabled().performClick()
        assertEquals(listOf("wifi"), events)
        assertTrue(keys.isEmpty())
    }

    @Test fun tvNameGetsPriorityWhilePowerStaysBelowTheHeader() {
        state = connected.copy(
            selected = tv.copy(name = "family room tv"),
            connection = ConnectionStatus(
                ConnectionState.Unavailable,
                ConnectionStatus.UNAVAILABLE
            )
        )
        mount(fontScale = 1.3f, width = 360, height = 640)
        val picker = rule.onNodeWithTag("tv_picker").getUnclippedBoundsInRoot()
        val status = rule.onNodeWithTag("connection").assertIsDisplayed()
            .assertTextContains(text(R.string.connection_unavailable_short))
            .getUnclippedBoundsInRoot()
        val power = rule.onNodeWithTag("power").getUnclippedBoundsInRoot()
        val overflow = rule.onNodeWithTag("overflow").getUnclippedBoundsInRoot()
        assertTrue("TV picker overlaps status", picker.right <= status.left)
        assertTrue("status overlaps overflow", status.right <= overflow.left)
        assertTrue(
            "TV picker did not get priority width",
            picker.right - picker.left > status.right - status.left
        )
        assertTrue("power is not beneath the picker", power.top >= picker.bottom)
        assertEquals(picker.left, power.left)
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
                rule.onNodeWithTag("key_$key").assertIsNotEnabled().performClick()
            }
        }
        assertTrue(keys.isEmpty())
    }

    @Test fun ordinaryKeysStayLiveWhileReconnectingButAppButtonsDoNot() {
        mount()
        show(
            connected.copy(
                connection = ConnectionStatus(ConnectionState.Reconnecting, "Reconnecting…")
            )
        )
        rule.onNodeWithTag("key_KEY_UP").assertIsEnabled().performClick()
        rule.onNodeWithTag("app_Netflix").assertIsNotEnabled()
        assertEquals(listOf("KEY_UP"), keys)
    }

    @Test fun appButtonsLaunchDirectlyAndGreyOutOnlyWhenNotInstalled() {
        mount()
        listOf(TvApp.Netflix, TvApp.Prime, TvApp.Disney).forEach { app ->
            rule.onNodeWithTag("app_${app.name}").assertIsEnabled().performClick()
        }
        rule.onNodeWithTag("app_Plex").assertIsNotEnabled().performClick()
        rule.onNodeWithContentDescription("Plex, ${text(R.string.app_not_installed)}")
            .assertExists()
        rule.onNodeWithTag("calibrate").assertDoesNotExist()
        rule.onNodeWithTag("cancel_sequence").assertDoesNotExist()
        assertEquals(listOf("app:Netflix", "app:Prime", "app:Disney"), events)
    }

    @Test fun compactWidthAndLargeTextKeepEveryTargetOnScreen() {
        mount(fontScale = 2f, width = 320, height = 640)
        assertFits(320, 640)
    }
}
