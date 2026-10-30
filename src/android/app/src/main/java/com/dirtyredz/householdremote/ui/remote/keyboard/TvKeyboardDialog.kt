package com.dirtyredz.householdremote.ui.remote.keyboard

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.dirtyredz.householdremote.R
import com.dirtyredz.householdremote.ui.remote.FitText
import com.dirtyredz.householdremote.ui.remote.RemoteButton

@Composable
fun KeyboardButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.tv_keyboard)
    RemoteButton(label, onClick, modifier, enabled, tag = "keyboard") {
        FitText(label, MaterialTheme.typography.labelMedium)
    }
}

/** Draft stays only in this dialog's memory; dismissing never transmits or restores it. */
@Composable
fun TvKeyboardDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() {
        if (submitted) return
        submitted = true
        keyboard?.hide()
        onDone(text)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tv_keyboard)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.tv_keyboard_text)) },
                singleLine = true,
                modifier = Modifier.focusRequester(focus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
            LaunchedEffect(Unit) {
                focus.requestFocus()
                keyboard?.show()
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = !submitted) {
                Text(stringResource(R.string.tv_keyboard_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_keyboard_cancel)) }
        }
    )
}
