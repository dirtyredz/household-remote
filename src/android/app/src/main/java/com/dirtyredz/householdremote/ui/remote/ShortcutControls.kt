package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.R
import com.dirtyredz.householdremote.shortcuts.ShortcutProfile

/**
 * App shortcuts. Uncalibrated apps stay visibly disabled next to the calibration entry; a
 * running sequence offers cancellation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutControls(
    configured: Set<String>,
    enabled: Boolean,
    running: Boolean,
    canCalibrate: Boolean,
    onShortcut: (String) -> Unit,
    onCalibrate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.shortcuts_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onCalibrate,
                enabled = canCalibrate,
                modifier = Modifier.heightIn(min = 48.dp).testTag("calibrate")
            ) { Text(stringResource(R.string.shortcuts_calibrate)) }
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = ShortcutProfile.apps.size
        ) {
            ShortcutProfile.apps.forEach { app ->
                RemoteButton(
                    description = app,
                    onClick = { onShortcut(app) },
                    enabled = enabled && app in configured,
                    tag = "shortcut_$app",
                    minSize = 48.dp,
                    modifier = Modifier.weight(1f).widthIn(min = 72.dp).heightIn(min = 56.dp)
                ) {
                    Text(
                        app,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
        if (running) {
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("cancel_sequence")
            ) { Text(stringResource(R.string.shortcuts_cancel)) }
        }
    }
}
