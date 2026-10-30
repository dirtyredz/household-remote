package com.dirtyredz.householdremote.ui.remote

import androidx.compose.foundation.shape.GenericShape

/** Decorative heart; the OK control retains its full circular touch target. */
val HeartShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, h * 0.22f)
    cubicTo(w * 0.25f, -h * 0.08f, -w * 0.08f, h * 0.18f, w * 0.06f, h * 0.48f)
    cubicTo(w * 0.14f, h * 0.67f, w * 0.36f, h * 0.85f, w * 0.5f, h * 0.94f)
    cubicTo(w * 0.64f, h * 0.85f, w * 0.86f, h * 0.67f, w * 0.94f, h * 0.48f)
    cubicTo(w * 1.08f, h * 0.18f, w * 0.75f, -h * 0.08f, w * 0.5f, h * 0.22f)
    close()
}
