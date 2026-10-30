package com.dirtyredz.householdremote.ui.remote

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dirtyredz.householdremote.ui.theme.RemoteTokens

/** Primary target size; never below Android's 48 dp minimum. */
val RemoteTarget = 56.dp

/** Brief platform haptic; the platform honours the user's touch-feedback setting. */
@Composable
fun rememberKeyHaptic(): () -> Unit {
    val view = LocalView.current
    return { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
}

/**
 * Shared remote control: minimum target size, ripple press treatment, button semantics with
 * a spoken description, and one haptic per enabled activation.
 */
@Composable
fun RemoteButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = RemoteTokens.surface,
    minSize: Dp = RemoteTarget,
    tag: String? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = RemoteTokens.controlBorder(enabled),
    content: @Composable () -> Unit
) {
    val haptic = rememberKeyHaptic()
    Surface(
        onClick = {
            haptic()
            onClick()
        },
        enabled = enabled,
        shape = shape,
        color = color,
        contentColor = if (enabled) contentColor else RemoteTokens.disabledContent,
        border = border,
        modifier = modifier
            .defaultMinSize(minSize, minSize)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .let { if (tag != null) it.testTag(tag) else it }
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** A [RemoteButton] showing one local vector icon. */
@Composable
fun RemoteIconButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = RemoteTokens.surface,
    tag: String? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = RemoteTokens.controlBorder(enabled)
) {
    RemoteButton(
        description,
        onClick,
        modifier,
        enabled,
        shape,
        color,
        tag = tag,
        contentColor = contentColor,
        border = border
    ) {
        Icon(painterResource(icon), contentDescription = null)
    }
}
