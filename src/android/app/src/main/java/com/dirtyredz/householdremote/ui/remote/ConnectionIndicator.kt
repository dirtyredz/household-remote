package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.R

/**
 * Fixed-size connection slot: a filled dot when connected, a small spinner while an attempt
 * is in progress, an outlined ring when offline. Text always accompanies the color. Changes
 * are announced politely.
 */
@Composable
fun ConnectionIndicator(label: ConnectionLabel, modifier: Modifier = Modifier) {
    val text = stringResource(
        when (label) {
            ConnectionLabel.SelectTv -> R.string.connection_select_tv
            ConnectionLabel.Connected -> R.string.connection_connected
            ConnectionLabel.Connecting -> R.string.connection_connecting
            ConnectionLabel.Reconnecting -> R.string.connection_reconnecting
            ConnectionLabel.ApproveOnTv -> R.string.connection_approve
            ConnectionLabel.Unavailable -> R.string.connection_unavailable
            ConnectionLabel.Offline -> R.string.connection_offline
        }
    )
    Row(
        modifier
            .heightIn(min = 24.dp)
            .testTag("connection")
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
            when (label) {
                ConnectionLabel.Connected -> Box(
                    Modifier.size(10.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape)
                )

                ConnectionLabel.Connecting, ConnectionLabel.Reconnecting,
                ConnectionLabel.ApproveOnTv ->
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)

                ConnectionLabel.Offline, ConnectionLabel.Unavailable,
                ConnectionLabel.SelectTv -> Box(
                    Modifier.size(
                        10.dp
                    ).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (label == ConnectionLabel.Connected) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
