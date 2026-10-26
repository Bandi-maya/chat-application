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

/** White/black contrast pairing without changing the user's chosen background.
 * For an opaque sRGB background the brighter ratio wins; the worst possible
 * winning ratio is about 4.58:1, above the normal-text 4.5:1 target. */
internal fun homeSwipeUsesDarkForeground(red: Float, green: Float, blue: Float): Boolean {
    fun linear(channel: Float): Double {
        val value = channel.toDouble().coerceIn(0.0, 1.0)
        return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }
    val luminance = .2126 * linear(red) + .7152 * linear(green) + .0722 * linear(blue)
    return (luminance + .05) / .05 >= 1.05 / (luminance + .05)
}
