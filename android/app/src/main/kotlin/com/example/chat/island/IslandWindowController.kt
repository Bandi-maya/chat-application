package com.example.chat.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.DisplayCutout
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator

/**
 * Cutout-aware WindowManager controller for Chaty Dynamic Island.
 * Accurately reads DisplayCutout bounding boxes across OEMs (Samsung, Pixel, Xiaomi, OnePlus, Nothing, Vivo/OPPO),
 * ensures hardware acceleration, spring physics layout transitions, and touch/IME focus flags.
 */
class IslandWindowController(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    var currentView: IslandView? = null
        private set

    private var boundsAnimator: ValueAnimator? = null

    val windowParams: WindowManager.LayoutParams by lazy {
        @Suppress("DEPRECATION")
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val params = WindowManager.LayoutParams(
            dp(IslandTokens.COMPACT_DEFAULT_WIDTH_DP),
            dp(IslandTokens.COMPACT_HEIGHT_DP),
            windowType,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = getCutoutTopOffset()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        params
    }

    fun attachIfNeeded(): IslandView? {
        val wm = windowManager ?: return null
        if (currentView == null) {
            val view = IslandView(context)
            currentView = view
            try {
                wm.addView(view, windowParams)
            } catch (_: Exception) {
                currentView = null
            }
        }
        return currentView
    }

    fun detach() {
        val wm = windowManager ?: return
        boundsAnimator?.cancel()
        currentView?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    wm.removeViewImmediate(view)
                }
            } catch (_: Exception) {}
            currentView = null
        }
    }

    fun updateBounds(widthPx: Int, heightPx: Int, focusableForIme: Boolean = false, animate: Boolean = true) {
        val wm = windowManager ?: return
        val view = currentView ?: return
        if (!view.isAttachedToWindow) return

        if (focusableForIme) {
            windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            windowParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        } else {
            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
        }

        windowParams.y = getCutoutTopOffset()

        if (!animate) {
            windowParams.width = widthPx
            windowParams.height = heightPx
            try {
                wm.updateViewLayout(view, windowParams)
            } catch (_: Exception) {}
            return
        }

        val startWidth = windowParams.width
        val startHeight = windowParams.height

        if (startWidth == widthPx && startHeight == heightPx) return

        boundsAnimator?.cancel()
        boundsAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240
            interpolator = OvershootInterpolator(0.72f) // Spring physics matching reference
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                val currentW = (startWidth + (widthPx - startWidth) * progress).toInt()
                val currentH = (startHeight + (heightPx - startHeight) * progress).toInt()
                windowParams.width = currentW
                windowParams.height = currentH
                try {
                    wm.updateViewLayout(view, windowParams)
                } catch (_: Exception) {}
            }
            start()
        }
    }

    fun getCutoutTopOffset(): Int {
        val wm = windowManager ?: return getStatusBarHeight()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val cutout: DisplayCutout? = metrics.windowInsets.displayCutout
            val rect: Rect? = cutout?.boundingRectTop ?: cutout?.boundingRects?.firstOrNull { it.top < dp(14) }
            if (rect != null && !rect.isEmpty) {
                val cutoutCenterY = rect.centerY()
                val pillHalfHeight = dp(IslandTokens.COMPACT_HEIGHT_DP) / 2
                return (cutoutCenterY - pillHalfHeight).coerceAtLeast(dp(2))
            }
        }
        val statusBar = getStatusBarHeight()
        return ((statusBar - dp(IslandTokens.COMPACT_HEIGHT_DP)) / 2).coerceAtLeast(dp(2))
    }

    fun getCutoutWidth(): Int {
        val wm = windowManager ?: return dp(32)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val cutout = metrics.windowInsets.displayCutout
            val rect = cutout?.boundingRectTop ?: cutout?.boundingRects?.firstOrNull()
            if (rect != null && !rect.isEmpty) {
                return rect.width()
            }
        }
        return dp(32)
    }

    private fun getStatusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
