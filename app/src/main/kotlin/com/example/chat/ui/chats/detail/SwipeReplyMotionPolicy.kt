package com.example.chat.ui.chats.detail

/** Bounded direct manipulation with continuous resistance after the reply threshold. */
object SwipeReplyMotionPolicy {
    fun offset(raw: Float, threshold: Float, maximum: Float): Float {
        if (threshold <= 0f || maximum <= 0f || !raw.isFinite()) return 0f
        val positive = raw.coerceAtLeast(0f)
        if (positive <= threshold) return positive.coerceAtMost(maximum)
        val excess = positive - threshold
        return (threshold + threshold * 0.35f * kotlin.math.ln(1f + excess / threshold))
            .coerceAtMost(maximum)
    }
}
