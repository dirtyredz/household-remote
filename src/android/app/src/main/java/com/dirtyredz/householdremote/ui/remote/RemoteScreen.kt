package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.R
import com.dirtyredz.householdremote.ui.RemoteState

/** Everything the remote surface can ask for; the route wires these to the ViewModel. */
class RemoteCallbacks(
    val onKey: (String) -> Unit,
    val onPower: () -> Unit,
    val onShortcut: (String) -> Unit,
    val onCancelSequence: () -> Unit,
    val onCalibrate: () -> Unit,
    val onPickTv: () -> Unit,
    val onOverflow: () -> Unit
)

/**
 * Stateless remote. Header, connection status and power stay fixed; the lower controls
 * scroll only when height or font scaling requires it, so targets never shrink to fit.
 */
@Composable
fun RemoteScreen(state: RemoteState, callbacks: RemoteCallbacks, modifier: Modifier = Modifier) {
    val selected = state.selected != null
    val enabled = state.inputEnabled
    // Ordinary keys stay live while reconnecting so one fresh key can wait; shortcuts do not.
    val keys = state.keysEnabled
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp)
    ) {
        RemoteHeader(
            tvName = state.selected?.name,
            connection = connectionLabel(selected, state.connection.state),
            onPickTv = callbacks.onPickTv,
            onOverflow = callbacks.onOverflow,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        PowerControl(
            powerPresentation(selected, state.connection.state, state.power),
            callbacks.onPower
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag(
                "remote_controls"
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DirectionPad(keys, callbacks.onKey)
            NavigationActions(keys, callbacks.onKey)
            AudioChannelRow(keys, callbacks.onKey)
            ShortcutControls(
                configured = state.configured,
                enabled = enabled,
                running = state.busy,
                canCalibrate = selected,
                onShortcut = callbacks.onShortcut,
                onCalibrate = callbacks.onCalibrate,
                onCancel = callbacks.onCancelSequence
            )
            if (state.message.isNotBlank()) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("message")
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}

@Composable
private fun AudioChannelRow(enabled: Boolean, onKey: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteRocker(
            label = stringResource(R.string.rocker_volume),
            upIcon = R.drawable.ic_remote_plus,
            upDescription = stringResource(R.string.key_volume_up),
            upKey = "KEY_VOLUP",
            downIcon = R.drawable.ic_remote_minus,
            downDescription = stringResource(R.string.key_volume_down),
            downKey = "KEY_VOLDOWN",
            enabled = enabled,
            onKey = onKey,
            modifier = Modifier.weight(1f)
        )
        RemoteIconButton(
            icon = R.drawable.ic_remote_mute,
            description = stringResource(R.string.key_mute),
            onClick = { onKey("KEY_MUTE") },
            enabled = enabled,
            shape = CircleShape,
            tag = "key_KEY_MUTE",
            modifier = Modifier.size(64.dp)
        )
        RemoteRocker(
            label = stringResource(R.string.rocker_channel),
            upIcon = R.drawable.ic_remote_up,
            upDescription = stringResource(R.string.key_channel_up),
            upKey = "KEY_CHUP",
            downIcon = R.drawable.ic_remote_down,
            downDescription = stringResource(R.string.key_channel_down),
            downKey = "KEY_CHDOWN",
            enabled = enabled,
            onKey = onKey,
            modifier = Modifier.weight(1f)
        )
    }
}
