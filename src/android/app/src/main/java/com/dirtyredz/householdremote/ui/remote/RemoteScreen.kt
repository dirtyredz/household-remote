package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.R
import com.dirtyredz.householdremote.apps.TvApp
import com.dirtyredz.householdremote.ui.RemoteState
import com.dirtyredz.householdremote.ui.theme.RemoteBackground
import com.dirtyredz.householdremote.ui.theme.RemoteBackgroundBottom

/** Everything the remote surface can ask for; the route wires these to the ViewModel. */
class RemoteCallbacks(
    val onKey: (String) -> Unit,
    val onPower: () -> Unit,
    val onLaunchApp: (TvApp) -> Unit,
    val onPickTv: () -> Unit,
    val onOverflow: () -> Unit,
    val onWifiSettings: () -> Unit,
    val onKeyboard: () -> Unit = {}
)

/**
 * Below this height (which grows with the font scale) the volume/channel rockers turn
 * horizontal so everything still fits. The D-pad absorbs the rest in either layout.
 */
fun compactRemote(height: Dp, fontScale: Float): Boolean =
    height < 660.dp + 200.dp * (fontScale - 1f).coerceAtLeast(0f)

/**
 * Stateless remote that always fits its window: nothing scrolls. The header and dedicated
 * power row keep their sizes; the D-pad takes the remaining height (down to its 48 dp key
 * floor), and short windows or large text switch the rockers to one horizontal row.
 */
@Composable
fun RemoteScreen(state: RemoteState, callbacks: RemoteCallbacks, modifier: Modifier = Modifier) {
    val selected = state.selected != null
    // Ordinary keys stay live while reconnecting so one fresh key can wait; app buttons do not.
    val keys = state.keysEnabled
    val power = powerPresentation(selected, state.connection.state, state.power, state.onWifi)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RemoteBackground, RemoteBackgroundBottom)))
            .safeDrawingPadding()
            .padding(horizontal = 16.dp)
            .testTag("remote")
    ) {
        val compact = compactRemote(maxHeight, LocalDensity.current.fontScale)
        val gap = if (compact) 6.dp else 10.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            RemoteHeader(
                tvName = state.selected?.name,
                connection = connectionLabel(selected, state.connection.state, state.onWifi),
                onPickTv = callbacks.onPickTv,
                onWifiSettings = callbacks.onWifiSettings,
                onOverflow = callbacks.onOverflow,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(gap))
            PowerRow(power, callbacks.onPower)
            Spacer(Modifier.height(gap))
            DirectionPad(keys, callbacks.onKey, Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(gap))
            NavigationActions(
                keys,
                callbacks.onKey,
                keyboardEnabled = state.inputEnabled,
                onKeyboard = callbacks.onKeyboard
            )
            Spacer(Modifier.height(gap))
            AudioChannelRow(keys, callbacks.onKey, horizontal = compact)
            Spacer(Modifier.height(gap))
            AppButtons(
                enabled = state::appEnabled,
                install = state::install,
                onLaunch = callbacks.onLaunchApp,
                compact = compact
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AudioChannelRow(enabled: Boolean, onKey: (String) -> Unit, horizontal: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteRocker(
            label = stringResource(R.string.rocker_volume),
            up = RockerKey(
                R.drawable.ic_remote_plus,
                stringResource(R.string.key_volume_up),
                "KEY_VOLUP"
            ),
            down = RockerKey(
                R.drawable.ic_remote_minus,
                stringResource(R.string.key_volume_down),
                "KEY_VOLDOWN"
            ),
            enabled = enabled,
            onKey = onKey,
            horizontal = horizontal,
            modifier = Modifier.weight(1f)
        )
        RemoteIconButton(
            icon = R.drawable.ic_remote_mute,
            description = stringResource(R.string.key_mute),
            onClick = { onKey("KEY_MUTE") },
            enabled = enabled,
            shape = CircleShape,
            tag = "key_KEY_MUTE",
            modifier = Modifier.size(if (horizontal) 48.dp else 64.dp)
        )
        RemoteRocker(
            label = stringResource(R.string.rocker_channel),
            up = RockerKey(
                R.drawable.ic_remote_up,
                stringResource(R.string.key_channel_up),
                "KEY_CHUP"
            ),
            down = RockerKey(
                R.drawable.ic_remote_down,
                stringResource(R.string.key_channel_down),
                "KEY_CHDOWN"
            ),
            enabled = enabled,
            onKey = onKey,
            horizontal = horizontal,
            modifier = Modifier.weight(1f)
        )
    }
}
