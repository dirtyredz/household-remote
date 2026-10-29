package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.R

/**
 * Compact connection status for the header: a filled dot when connected, a small spinner
 * while an attempt is in progress, and an outlined ring otherwise. The short label stays
 * on one line; explanatory text and actions live in the notice slot.
 */
@Composable
fun ConnectionIndicator(label: ConnectionLabel, modifier: Modifier = Modifier) {
    val text = stringResource(
        when (label) {
            ConnectionLabel.Connected -> R.string.connection_connected
            ConnectionLabel.Connecting -> R.string.connection_connecting
            ConnectionLabel.Reconnecting -> R.string.connection_reconnecting
            ConnectionLabel.ApproveOnTv -> R.string.connection_approve
            ConnectionLabel.Unavailable -> R.string.connection_unavailable_short
            ConnectionLabel.Offline -> R.string.connection_offline
            ConnectionLabel.NotOnWifi -> R.string.connection_no_wifi_short
        }
    )
    Row(
        modifier
            .testTag("connection")
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
            when (label) {
                ConnectionLabel.Connected -> Box(
                    Modifier.size(10.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                )

                ConnectionLabel.Connecting, ConnectionLabel.Reconnecting,
                ConnectionLabel.ApproveOnTv ->
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)

                ConnectionLabel.Offline, ConnectionLabel.Unavailable,
                ConnectionLabel.NotOnWifi -> Box(
                    Modifier.size(10.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            color = if (label == ConnectionLabel.Connected) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
