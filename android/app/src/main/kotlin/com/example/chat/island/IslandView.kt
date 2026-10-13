package com.example.chat.island

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Single persistent Island View root attached once to WindowManager (fixing K-1).
 * Mutates child contents in place without destructive rebuilds, keeping IME focus & scroll state intact (fixing K-2, K-3).
 */
class IslandView(context: Context) : FrameLayout(context) {
    private val shape = IslandShape()

    // Inner container for animated contents
    val container: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        clipChildren = true
    }

    private var currentFillColor: Int = IslandTokens.COLOR_COMPACT_BG
    private var currentStrokeColor: Int = Color.TRANSPARENT
    private var currentCornerRadius: Float = dp(18).toFloat()

    init {
        addView(
            container,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
        setWillNotDraw(false)
        applyBackgroundStyle(IslandTokens.COLOR_COMPACT_BG, Color.TRANSPARENT, dp(18).toFloat())
    }

    fun applyBackgroundStyle(fillColor: Int, strokeColor: Int, cornerRadiusPx: Float) {
        currentFillColor = fillColor
        currentStrokeColor = strokeColor
        currentCornerRadius = cornerRadiusPx

        background = GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = cornerRadiusPx
            if (Color.alpha(strokeColor) > 0) {
                setStroke(dp(1), strokeColor)
            }
        }
        elevation = dp(8).toFloat()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
