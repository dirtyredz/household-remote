package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One labelled up/down key pair, shared by volume and channel. */
@Composable
fun RemoteRocker(
    label: String,
    upIcon: Int,
    upDescription: String,
    upKey: String,
    downIcon: Int,
    downDescription: String,
    downKey: String,
    enabled: Boolean,
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        RemoteIconButton(
            upIcon,
            upDescription,
            { onKey(upKey) },
            Modifier.fillMaxWidth(),
            enabled,
            MaterialTheme.shapes.large,
            tag = "key_$upKey"
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RemoteIconButton(
            downIcon,
            downDescription,
            { onKey(downKey) },
            Modifier.fillMaxWidth(),
            enabled,
            MaterialTheme.shapes.large,
            tag = "key_$downKey"
        )
    }
}
