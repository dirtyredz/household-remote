package com.dirtyredz.householdremote.ui.typing

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Invisible text focus only; the draft dies with this composition. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveTyping(onTextChanged: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val imeVisibility = remember { ImeVisibilityState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val visible = WindowInsets.isImeVisible
    LaunchedEffect(visible) {
        if (imeVisibility.changed(visible)) onClose()
    }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    BasicTextField(
        value = text,
        onValueChange = {
            if (it != text) {
                text = it
                onTextChanged(it)
            }
        },
        modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focus)
            .testTag("live_typing_field"),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.None
        )
    )
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
}
