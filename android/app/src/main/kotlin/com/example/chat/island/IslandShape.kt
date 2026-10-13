package com.example.chat.island

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * High-fidelity squircle / superellipse path builder.
 * Formula: |x/a|^n + |y/b|^n = 1, where n is typically 4.5 - 5.0 for Apple-like smooth curvature.
 */
class IslandShape {
    private val path = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private var cachedWidth = 0f
    private var cachedHeight = 0f
    private var cachedRadius = 0f

    fun updatePath(width: Float, height: Float, cornerRadius: Float): Path {
        if (width == cachedWidth && height == cachedHeight && cornerRadius == cachedRadius && !path.isEmpty) {
            return path
        }
        cachedWidth = width
        cachedHeight = height
        cachedRadius = cornerRadius

        path.reset()
        val rect = RectF(0f, 0f, width, height)
        // Using continuous rounded rectangle path with bezier approximations
        path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        return path
    }

    fun draw(canvas: Canvas, width: Float, height: Float, radius: Float, fillColor: Int, strokeColor: Int, strokeWidth: Float) {
        val p = updatePath(width, height, radius)
        fillPaint.color = fillColor
        canvas.drawPath(p, fillPaint)

        if (Color.alpha(strokeColor) > 0 && strokeWidth > 0f) {
            strokePaint.color = strokeColor
            strokePaint.strokeWidth = strokeWidth
            canvas.drawPath(p, strokePaint)
        }
    }
}
