package com.example.chat.core.media

/** One rendering contract used for the preview and exported copy. No hidden multiline clipping. */
object ImageTextOverlayPolicy {
    fun text(value: String): String = value.replace('\n', ' ').replace('\r', ' ').take(120)
    fun scale(value: Float): Float = if (value.isFinite()) value.coerceIn(0.03f, 0.15f) else 0.07f
    fun x(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 0.9f) else 0.1f
    fun y(value: Float): Float = if (value.isFinite()) value.coerceIn(0.1f, 0.95f) else 0.3f
    fun fittedTextSize(width: Float, x: Float, requestedSize: Float, measuredWidth: Float): Float {
        if (!width.isFinite() || width <= 0 || !requestedSize.isFinite() || requestedSize <= 0) return 0f
        val available = width * (1f - x(x)) - width * 0.02f
        return if (measuredWidth.isFinite() && measuredWidth > available) requestedSize * available / measuredWidth else requestedSize
    }
}
