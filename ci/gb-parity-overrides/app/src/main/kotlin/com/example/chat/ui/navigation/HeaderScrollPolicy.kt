package com.example.chat.ui.navigation

/** Pure drag policy: negative Y collapses, positive Y expands, no overshoot. */
fun referenceHeaderScrollOffset(current: Float, availableY: Float, range: Float): Float =
    (current + availableY).coerceIn(-range.coerceAtLeast(0f), 0f)
