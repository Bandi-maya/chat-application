package com.example.chat.ui.split

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure, JVM-testable layout computation for Split Chat Workspace.
 */
object SplitChatLayoutPolicy {

    /**
     * Resolves the presentation mode based on pane count and available window dimensions.
     */
    fun resolvePresentationMode(
        paneCount: Int,
        windowWidthDp: Dp,
        windowHeightDp: Dp,
        isImeVisible: Boolean = false,
    ): SplitPresentationMode {
        if (paneCount <= 0) return SplitPresentationMode.COMPACT_SWITCHER
        if (paneCount == 1) return SplitPresentationMode.FLOATING_MINI

        // If IME is visible or height is very constrained, fallback to compact tab switcher
        // so active pane remains usable without being crushed to < 180dp.
        if (isImeVisible || windowHeightDp < 420.dp) {
            return SplitPresentationMode.COMPACT_SWITCHER
        }

        if (paneCount >= 3 && windowWidthDp < 600.dp) {
            return SplitPresentationMode.COMPACT_SWITCHER
        }

        return when (paneCount) {
            2 -> SplitPresentationMode.TWO_PANE_STACK
            3 -> SplitPresentationMode.THREE_PANE_GRID
            4 -> SplitPresentationMode.FOUR_PANE_GRID
            else -> SplitPresentationMode.FOUR_PANE_GRID
        }
    }

    /**
     * Clamps floating mini-window offset within boundaries.
     */
    fun clampOffset(
        currentX: Float,
        currentY: Float,
        windowWidthPx: Float,
        windowHeightPx: Float,
        miniWidthPx: Float,
        miniHeightPx: Float,
        marginPx: Float = 16f,
    ): Pair<Float, Float> {
        val roomX = (windowWidthPx - miniWidthPx).coerceAtLeast(0f)
        val roomY = (windowHeightPx - miniHeightPx).coerceAtLeast(0f)
        val minX = marginPx.coerceIn(0f, roomX / 2f)
        val minY = marginPx.coerceIn(0f, roomY / 2f)
        val clampedX = currentX.coerceIn(minX, roomX - minX)
        val clampedY = currentY.coerceIn(minY, roomY - minY)
        return clampedX to clampedY
    }
}

