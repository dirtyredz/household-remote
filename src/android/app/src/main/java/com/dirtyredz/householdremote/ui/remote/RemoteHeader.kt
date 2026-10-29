package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.R

/**
 * Selected-TV picker, compact connection status, and overflow entry on one line. The picker
 * takes the flexible width so a long TV name ellipsizes before the status can wrap.
 */
@Composable
fun RemoteHeader(
    tvName: String?,
    connection: ConnectionLabel?,
    onPickTv: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val name = tvName ?: stringResource(R.string.header_choose_tv)
        RemoteButton(
            description = stringResource(R.string.header_picker_description, name),
            onClick = onPickTv,
            color = Color.Transparent,
            minSize = 48.dp,
            tag = "tv_picker",
            modifier = Modifier.weight(1f)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(painterResource(R.drawable.ic_remote_picker), contentDescription = null)
            }
        }
        connection?.let { ConnectionIndicator(it) }
        RemoteIconButton(
            icon = R.drawable.ic_remote_more,
            description = stringResource(R.string.header_more),
            onClick = onOverflow,
            color = Color.Transparent,
            tag = "overflow",
            modifier = Modifier.size(48.dp)
        )
    }
}
