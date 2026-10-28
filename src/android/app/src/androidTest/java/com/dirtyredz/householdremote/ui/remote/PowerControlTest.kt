package com.dirtyredz.householdremote.ui.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dirtyredz.householdremote.R
import com.dirtyredz.householdremote.ui.theme.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fake presentations only; no ViewModel, session or TV is involved. */
@RunWith(AndroidJUnit4::class)
class PowerControlTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var presentation by mutableStateOf(
        PowerPresentation(PowerLabel.Power, PowerStatus.SelectTv, enabled = false, busy = false)
    )
    private var clicks = 0

    private fun mount() = rule.setContent {
        RemoteTheme { PowerControl(presentation, onToggle = { clicks++ }) }
    }

    private fun show(value: PowerPresentation) {
        presentation = value
        rule.waitForIdle()
    }

    private fun assertState(label: Int, status: Int, enabled: Boolean) {
        val node = rule.onNodeWithTag("power")
        node.assertTextContains(context.getString(label))
        node.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(status)
            )
        )
        if (enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
        node.assertHeightIsAtLeast(64.dp)
    }

    @Test fun everyTableStateRendersItsActionStatusAndAvailability() {
        mount()
        assertState(R.string.power_label, R.string.power_status_select_tv, enabled = false)

        show(PowerPresentation(PowerLabel.TurnOff, PowerStatus.OnConnected, true, false))
        assertState(R.string.power_turn_off, R.string.power_status_on, enabled = true)

        show(PowerPresentation(PowerLabel.TurnOn, PowerStatus.OffOrUnreachable, true, false))
        assertState(R.string.power_turn_on, R.string.power_status_off, enabled = true)

        for ((label, text) in listOf(
            PowerLabel.TurningOn to R.string.power_turning_on,
            PowerLabel.Connecting to R.string.power_connecting,
            PowerLabel.TurningOff to R.string.power_turning_off
        )) {
            show(PowerPresentation(label, PowerStatus.InProgress, enabled = false, busy = true))
            assertState(text, R.string.power_status_in_progress, enabled = false)
            rule.onNodeWithTag("power_progress", useUnmergedTree = true).assertExists()
        }

        show(
            PowerPresentation(
                PowerLabel.TurnOn,
                PowerStatus.StandbySent,
                enabled = true,
                busy = false,
                notConfirmed = true
            )
        )
        assertState(R.string.power_turn_on, R.string.power_status_standby_sent, enabled = true)
        rule.onNodeWithText(context.getString(R.string.power_not_confirmed)).assertExists()
        rule.onNodeWithTag("power_progress", useUnmergedTree = true).assertDoesNotExist()

        show(
            PowerPresentation(
                PowerLabel.TurnOn,
                PowerStatus.OffOrUnreachable,
                enabled = true,
                busy = false,
                error = "TV not ready; check Wi-Fi wake settings"
            )
        )
        assertState(R.string.power_turn_on, R.string.power_status_off, enabled = true)
        rule.onNodeWithTag("power_error").assertTextEquals(
            "TV not ready; check Wi-Fi wake settings"
        )
    }

    @Test fun enabledPressCallsOnceAndBusyPressesDoNothing() {
        presentation = PowerPresentation(PowerLabel.TurnOff, PowerStatus.OnConnected, true, false)
        mount()
        rule.onNodeWithTag("power").performClick()
        assertEquals(1, clicks)
        show(PowerPresentation(PowerLabel.TurningOff, PowerStatus.InProgress, false, true))
        repeat(3) { rule.onNodeWithTag("power").performClick() }
        assertEquals(1, clicks)
        show(PowerPresentation(PowerLabel.Power, PowerStatus.SelectTv, false, false))
        rule.onNodeWithTag("power").performClick()
        assertEquals(1, clicks)
    }
}
