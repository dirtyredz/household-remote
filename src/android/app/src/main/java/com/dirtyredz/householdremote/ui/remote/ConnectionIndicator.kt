package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.R

/**
 * Centred connection status: a filled dot when connected, a small spinner while an attempt
 * is in progress, an outlined ring otherwise. Text always accompanies the color, and
 * "Phone not on Wi-Fi" carries a button to the system Wi-Fi settings. Changes are
 * announced politely.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionIndicator(
    label: ConnectionLabel,
    onWifiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val text = stringResource(
        when (label) {
            ConnectionLabel.SelectTv -> R.string.connection_select_tv
            ConnectionLabel.Connected -> R.string.connection_connected
            ConnectionLabel.Connecting -> R.string.connection_connecting
            ConnectionLabel.Reconnecting -> R.string.connection_reconnecting
            ConnectionLabel.ApproveOnTv -> R.string.connection_approve
            ConnectionLabel.Unavailable -> R.string.connection_unavailable
            ConnectionLabel.Offline -> R.string.connection_offline
            ConnectionLabel.NotOnWifi -> R.string.connection_no_wifi
        }
    )
    // The Wi-Fi action wraps under the status only when large text leaves no room beside it.
    FlowRow(
        modifier.heightIn(min = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .testTag("connection")
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                when (label) {
                    ConnectionLabel.Connected -> Box(
                        Modifier.size(
                            10.dp
                        ).background(MaterialTheme.colorScheme.tertiary, CircleShape)
                    )

                    ConnectionLabel.Connecting, ConnectionLabel.Reconnecting,
                    ConnectionLabel.ApproveOnTv ->
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)

                    ConnectionLabel.Offline, ConnectionLabel.Unavailable,
                    ConnectionLabel.SelectTv, ConnectionLabel.NotOnWifi -> Box(
                        Modifier.size(
                            10.dp
                        ).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (label == ConnectionLabel.Connected) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (label == ConnectionLabel.NotOnWifi) {
            TextButton(
                onClick = onWifiSettings,
                modifier = Modifier.heightIn(min = 48.dp).testTag("wifi_settings")
            ) { Text(stringResource(R.string.connection_wifi_settings), maxLines = 1) }
        }
    }
}
