package com.example.chat.ui.kit

enum class ChatyWidthBucket { Compact, Medium, Expanded }
enum class ChatyHeightBucket { Compact, Medium, Expanded }

/** Pure rules so they are unit-testable without Compose (R2, R3, R7). */
object ChatyAdaptiveLogic {
    const val MEDIUM_MIN_DP = 600
    const val EXPANDED_MIN_DP = 840
    const val HEIGHT_MEDIUM_MIN_DP = 480
    const val HEIGHT_EXPANDED_MIN_DP = 900
    const val AUTH_COLUMN_MAX_DP = 460
    const val LIST_COLUMN_MAX_DP = 720
    const val BUBBLE_MAX_DP = 520
    const val BUBBLE_WIDTH_FRACTION = 0.78f
    /** Home list pane in the expanded list-detail layout (R7). */
    const val HOME_PANE_MIN_DP = 360
    const val HOME_PANE_MAX_DP = 400

    fun bucketOf(widthDp: Int): ChatyWidthBucket = when {
        widthDp < MEDIUM_MIN_DP -> ChatyWidthBucket.Compact
        widthDp < EXPANDED_MIN_DP -> ChatyWidthBucket.Medium
        else -> ChatyWidthBucket.Expanded
    }

    fun heightBucketOf(heightDp: Int): ChatyHeightBucket = when {
        heightDp < HEIGHT_MEDIUM_MIN_DP -> ChatyHeightBucket.Compact
        heightDp < HEIGHT_EXPANDED_MIN_DP -> ChatyHeightBucket.Medium
        else -> ChatyHeightBucket.Expanded
    }

    /** Returns container metric when available (>0), else falls back safely for test fixtures. */
    fun effectiveDimension(metricsDp: Int, fallbackDp: Int): Int =
        if (metricsDp > 0) metricsDp else fallbackDp

    fun adaptiveModalWidth(windowWidthDp: Int, desiredMaxDp: Int = 520, marginDp: Int = 24): Int =
        (windowWidthDp - marginDp).coerceAtLeast(160).coerceAtMost(desiredMaxDp)

    fun adaptiveSheetHeight(windowHeightDp: Int, fraction: Float = 0.8f, minDp: Int = 180): Int =
        (windowHeightDp * fraction).toInt().coerceAtLeast(minOf(minDp, windowHeightDp))

    /** R3: auth forms cap at 460dp, list/settings rows at 720dp. */
    fun authColumnCap(widthDp: Int): Int = minOf(widthDp, AUTH_COLUMN_MAX_DP)
    fun listColumnCap(widthDp: Int): Int = minOf(widthDp, LIST_COLUMN_MAX_DP)

    /** R3: bubbles max 78% of the chat pane, hard-capped at 520dp. */
    fun bubbleCap(paneWidthDp: Int): Int =
        minOf((paneWidthDp * BUBBLE_WIDTH_FRACTION).toInt(), BUBBLE_MAX_DP)

    /** R7: expanded Home is list pane + chat pane. */
    fun isListDetail(widthDp: Int): Boolean = bucketOf(widthDp) == ChatyWidthBucket.Expanded

    fun homePaneWidth(windowWidthDp: Int): Int =
        windowWidthDp.coerceIn(HOME_PANE_MIN_DP, HOME_PANE_MAX_DP)
}

