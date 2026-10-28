package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.R

/**
 * Full-width power action. The label names what a press will do; the status line says what
 * is inferred about the TV. Progress replaces the icon while work runs, and an error stays
 * visible below the button until the next press.
 */
@Composable
fun PowerControl(power: PowerPresentation, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(
        when (power.label) {
            PowerLabel.Power -> R.string.power_label
            PowerLabel.TurnOff -> R.string.power_turn_off
            PowerLabel.TurnOn -> R.string.power_turn_on
            PowerLabel.TurningOn -> R.string.power_turning_on
            PowerLabel.Connecting -> R.string.power_connecting
            PowerLabel.TurningOff -> R.string.power_turning_off
        }
    )
    val status = stringResource(
        when (power.status) {
            PowerStatus.SelectTv -> R.string.power_status_select_tv
            PowerStatus.OnConnected -> R.string.power_status_on
            PowerStatus.OffOrUnreachable -> R.string.power_status_off
            PowerStatus.StandbySent -> R.string.power_status_standby_sent
            PowerStatus.InProgress -> R.string.power_status_in_progress
        }
    )
    val accent = power.label == PowerLabel.TurnOn
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RemoteButton(
            description = label,
            onClick = onToggle,
            enabled = power.enabled,
            color = if (accent && power.enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            minSize = 64.dp,
            tag = "power",
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics {
                stateDescription = status
            }
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (power.busy) {
                    CircularProgressIndicator(
                        Modifier.size(24.dp).testTag("power_progress"),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(painterResource(R.drawable.ic_remote_power), contentDescription = null)
                }
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        if (power.notConfirmed) {
            Text(
                stringResource(R.string.power_not_confirmed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        power.error?.let { error ->
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("power_error").semantics {
                    liveRegion = LiveRegionMode.Polite
                }
            )
        }
    }
}
