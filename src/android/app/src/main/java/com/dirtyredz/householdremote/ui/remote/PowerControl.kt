package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.R
import com.dirtyredz.householdremote.ui.theme.RemotePowerRed

/** The power button's diameter: a physical remote's red key, above the 48 dp floor. */
val PowerButtonSize = 56.dp

fun powerLabelText(label: PowerLabel): Int = when (label) {
    PowerLabel.Power -> R.string.power_label
    PowerLabel.TurnOff -> R.string.power_turn_off
    PowerLabel.TurnOn -> R.string.power_turn_on
    PowerLabel.TurningOn -> R.string.power_turning_on
    PowerLabel.Connecting -> R.string.power_connecting
    PowerLabel.TurningOff -> R.string.power_turning_off
}

/**
 * Round red power key. Its white power icon never changes; TalkBack speaks the current
 * action and inferred TV state. Disabled state may reduce the control's alpha.
 */
@Composable
fun PowerControl(power: PowerPresentation, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(powerLabelText(power.label))
    val status = stringResource(
        when (power.status) {
            PowerStatus.SelectTv -> R.string.power_status_select_tv
            PowerStatus.OnConnected -> R.string.power_status_on
            PowerStatus.OffOrUnreachable -> R.string.power_status_off
            PowerStatus.StandbySent -> R.string.power_status_standby_sent
            PowerStatus.InProgress -> R.string.power_status_in_progress
            PowerStatus.NotOnWifi -> R.string.power_status_no_wifi
        }
    )
    RemoteButton(
        description = label,
        onClick = onToggle,
        enabled = power.enabled,
        shape = CircleShape,
        color = if (power.enabled) {
            RemotePowerRed
        } else {
            RemotePowerRed.copy(alpha = 0.38f)
        },
        minSize = PowerButtonSize,
        tag = "power",
        modifier = modifier.size(PowerButtonSize).semantics { stateDescription = status }
    ) {
        Icon(
            painterResource(R.drawable.ic_remote_power),
            contentDescription = null,
            modifier = Modifier.testTag("power_icon"),
            tint = if (power.enabled) Color.White else Color.White.copy(alpha = 0.6f)
        )
    }
}
