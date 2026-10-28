package com.dirtyredz.householdremote.ui.remote

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dirtyredz.householdremote.R
import kotlinx.coroutines.delay

/** How long general feedback stays beside the power button. */
const val MESSAGE_VISIBLE_MS = 4_000L

/**
 * At most two lines of feedback in the space beside the power button; empty when there is
 * nothing to say. Errors use the error color. Changes are announced politely.
 */
@Composable
fun NoticeSlot(notice: RemoteNotice?, modifier: Modifier = Modifier) {
    var expired by remember(notice) { mutableStateOf(false) }
    if (notice is RemoteNotice.Message) {
        LaunchedEffect(notice) {
            delay(MESSAGE_VISIBLE_MS)
            expired = true
        }
    }
    val text = when (notice) {
        null -> null
        is RemoteNotice.Error -> notice.text
        is RemoteNotice.Progress -> stringResource(powerLabelText(notice.label))
        RemoteNotice.NotOnWifi -> stringResource(R.string.power_needs_wifi)
        RemoteNotice.StandbyNotConfirmed -> stringResource(R.string.power_standby_not_confirmed)
        is RemoteNotice.Detail -> notice.text
        is RemoteNotice.Message -> notice.text.takeUnless { expired }
    } ?: return
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (notice is RemoteNotice.Error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.testTag(
            if (notice is RemoteNotice.Error) "power_error" else "notice"
        ).semantics { liveRegion = LiveRegionMode.Polite }
    )
}
