package com.example.chat.ui.navigation

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/** Pure geometry in dp, in the overlay's already inset-safe local coordinates. */
internal object RadialMenuGeometry {
    data class Point(val x: Float, val y: Float)
    data class Layout(
        val anchor: Point,
        val radius: Float,
        val centers: List<Point>,
        val pageSize: Int,
    )

    const val targetSize = 56f
    const val edge = 8f
    private const val gap = 8f

    fun layout(width: Float, height: Float, itemCount: Int): Layout {
        val half = targetSize / 2f
        // Keep the SAME anchor for closed/open states, including compact windows.
        val anchor = Point(min(edge + half, width / 2f), min(edge + half, height / 2f))
        val available = min(width - anchor.x - half - edge, height - anchor.y - half - edge)
        val radius = available.coerceIn(0f, 200f)
        val minimumStep = if (radius >= (targetSize + gap) / 2f)
            2.0 * asin((targetSize + gap) / (2.0 * radius)) else PI
        val capacity = (floor((PI / 2.0) / minimumStep).toInt() + 1).coerceAtLeast(1)
        val size = min(itemCount.coerceAtLeast(0), capacity)
        val centers = (0 until size).map { index ->
            val angle = if (size == 1) PI / 4.0 else index * (PI / 2.0) / (size - 1)
            Point(anchor.x + radius * cos(angle).toFloat(), anchor.y + radius * sin(angle).toFloat())
        }
        return Layout(anchor, radius, centers, capacity)
    }
}
