package com.dirtyredz.householdremote.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.dirtyredz.householdremote.R
import com.dirtyredz.householdremote.ui.typing.TypingLayout

/** Manual override of the TV keyboard grid; app buttons also set it automatically. */
@Composable
internal fun TypingLayoutControl(layout: TypingLayout, onLayout: (TypingLayout) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Text(stringResource(R.string.typing_layout_title))
        TypingLayout.entries.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("typing_layout_${option.name}")
                    .selectable(
                        selected = option == layout,
                        onClick = { onLayout(option) },
                        role = Role.RadioButton
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = option == layout, onClick = null)
                Text(stringResource(option.label()))
            }
        }
    }
}

private fun TypingLayout.label(): Int = when (this) {
    TypingLayout.Shared -> R.string.typing_layout_shared
    TypingLayout.Jellyfin -> R.string.typing_layout_jellyfin
}
