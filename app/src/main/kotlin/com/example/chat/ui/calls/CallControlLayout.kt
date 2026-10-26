package com.example.chat.ui.calls

/** 54dp buttons with at least 8dp separation. Hangup always stays visible. */
internal fun visibleCallActionCount(availableWidthDp: Int, actionCount: Int): Int {
    if (availableWidthDp < 116) return 0 // Host stacks More and End vertically.
    val slots = ((availableWidthDp + 8) / 62).coerceAtLeast(2)
    return if (actionCount + 1 <= slots) actionCount else (slots - 2).coerceAtLeast(0)
}
