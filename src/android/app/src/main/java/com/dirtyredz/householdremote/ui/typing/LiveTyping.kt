package com.dirtyredz.householdremote.ui.typing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** No dialog or visible input box; the draft dies with this composition. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveTyping(onTextChanged: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var seenIme by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val visible = WindowInsets.isImeVisible
    BackHandler(onBack = onClose)
    LaunchedEffect(visible) {
        if (visible) {
            seenIme = true
        } else if (seenIme) {
            onClose()
        }
    }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    Surface(Modifier.statusBarsPadding()) {
        Row {
            Text("Typing on TV…")
            TextButton(onClick = onClose) { Text("Close") }
            BasicTextField(
                value = text,
                onValueChange = {
                    if (it != text) {
                        text = it
                        onTextChanged(it)
                    }
                },
                modifier = Modifier.size(
                    1.dp
                ).alpha(0f).focusRequester(focus).testTag("live_typing_field"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.None)
            )
        }
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
}
