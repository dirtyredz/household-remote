package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
 * App shortcuts in one fixed-height row. Uncalibrated apps stay visibly disabled next to the
 * calibration entry; a running sequence swaps the row for its Cancel button, so the remote
 * never grows taller. [compact] uses the 48 dp floor instead of the 56 dp target.
 */
@Composable
fun ShortcutControls(
    configured: Set<String>,
    enabled: Boolean,
    running: Boolean,
    canCalibrate: Boolean,
    onShortcut: (String) -> Unit,
    onCalibrate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val rowHeight = if (compact) 48.dp else RemoteTarget
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.shortcuts_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onCalibrate,
                enabled = canCalibrate,
                modifier = Modifier.heightIn(min = 48.dp).testTag("calibrate")
            ) { Text(stringResource(R.string.shortcuts_calibrate), maxLines = 1) }
        }
        if (running) {
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(rowHeight).testTag("cancel_sequence")
            ) { Text(stringResource(R.string.shortcuts_cancel), maxLines = 1) }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShortcutProfile.apps.forEach { app ->
                    RemoteButton(
                        description = app,
                        onClick = { onShortcut(app) },
                        enabled = enabled && app in configured,
                        tag = "shortcut_$app",
                        minSize = 48.dp,
                        modifier = Modifier.weight(1f).height(rowHeight)
                    ) {
                        FitText(
                            app,
                            MaterialTheme.typography.labelLarge,
                            Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
