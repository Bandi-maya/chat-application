package com.example.chat.island

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.DisplayCutout
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets

/**
 * Cutout-aware WindowManager controller for Chaty Island.
 * Accurately reads DisplayCutout bounding boxes (fixing K-6) and configures flags for touch and IME (fixing K-15).
 */
class IslandWindowController(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    var currentView: IslandView? = null
        private set

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

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
        currentView?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    wm.removeViewImmediate(view)
                }
            } catch (_: Exception) {}
            currentView = null
        }
    }

    fun updateBounds(widthPx: Int, heightPx: Int, focusableForIme: Boolean = false) {
        val wm = windowManager ?: return
        val view = currentView ?: return
        if (!view.isAttachedToWindow) return

        windowParams.width = widthPx
        windowParams.height = heightPx
        windowParams.y = getCutoutTopOffset()

        if (focusableForIme) {
            windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            windowParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        } else {
            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
        }

        try {
            wm.updateViewLayout(view, windowParams)
        } catch (_: Exception) {}
    }

    fun getCutoutTopOffset(): Int {
        val wm = windowManager ?: return getStatusBarHeight()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val cutout: DisplayCutout? = metrics.windowInsets.displayCutout
            val rect: Rect? = cutout?.boundingRectTop ?: cutout?.boundingRects?.firstOrNull { it.top < dp(12) }
            if (rect != null && !rect.isEmpty) {
                // Center the 36dp pill vertically over the camera hole
                val cutoutCenterY = rect.centerY()
                val pillHalfHeight = dp(IslandTokens.COMPACT_HEIGHT_DP) / 2
                return (cutoutCenterY - pillHalfHeight).coerceAtLeast(dp(2))
            }
        }
        // Accurate fallback based on status bar center
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
