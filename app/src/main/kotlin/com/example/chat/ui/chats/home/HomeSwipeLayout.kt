package com.example.chat.ui.chats.home

import kotlin.math.abs
import kotlin.math.min

/** Geometry in one consistent unit (dp for widths, px for gesture settling). */
internal fun homeSwipeExtent(rowWidth: Float, actionCount: Int, preferredExtent: Float): Float =
    if (actionCount <= 0 || rowWidth <= 0f) 0f
    else min(preferredExtent.coerceAtLeast(0f), rowWidth * 0.60f / actionCount)

internal fun homeSwipeSettleTarget(
    offset: Float, velocity: Float, leading: Float, trailing: Float,
    flingThreshold: Float, minimumFlingDistance: Float,
): Float {
    val limit = if (offset < 0f) leading else trailing
    if (limit <= 0f) return 0f
    val movingOpen = if (offset < 0f) velocity < -flingThreshold else velocity > flingThreshold
    val open = abs(offset) > limit / 2f || (movingOpen && abs(offset) > minimumFlingDistance)
    return if (!open) 0f else if (offset < 0f) -limit else limit
}
