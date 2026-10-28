package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * One-line control label that steps its font down (never below 10 sp) instead of wrapping,
 * so large font scales cannot make a fixed-height control grow taller than its row.
 */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    BasicText(
        text,
        modifier,
        style = style.copy(color = color, textAlign = TextAlign.Center),
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize)
    )
}
