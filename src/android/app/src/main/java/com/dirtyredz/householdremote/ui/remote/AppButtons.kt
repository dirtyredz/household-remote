package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.R
import com.dirtyredz.householdremote.apps.AppInstall
import com.dirtyredz.householdremote.apps.TvApp

/**
 * The four app buttons in one fixed-height row; each opens its app directly. A button the
 * TV reported as not installed stays visibly disabled and says so to TalkBack. [compact]
 * uses the 48 dp floor instead of the 56 dp target.
 */
@Composable
fun AppButtons(
    enabled: (TvApp) -> Boolean,
    install: (TvApp) -> AppInstall,
    onLaunch: (TvApp) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val rowHeight = if (compact) 48.dp else RemoteTarget
    val notInstalled = stringResource(R.string.app_not_installed)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.apps_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvApp.entries.forEach { app ->
                val missing = install(app) == AppInstall.NotInstalled
                RemoteButton(
                    description = if (missing) "${app.label}, $notInstalled" else app.label,
                    onClick = { onLaunch(app) },
                    enabled = enabled(app),
                    tag = "app_${app.name}",
                    minSize = 48.dp,
                    modifier = Modifier.weight(1f).height(rowHeight)
                ) {
                    FitText(
                        app.label,
                        MaterialTheme.typography.labelLarge,
                        Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}
