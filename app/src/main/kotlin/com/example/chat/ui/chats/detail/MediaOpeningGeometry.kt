package com.example.chat.ui.chats.detail

/** Pure, bounded transform. Uniform scale avoids stretching image pixels. */
data class MediaOpeningTransform(val scale: Float, val x: Float, val y: Float)
fun mediaOpeningTransform(sourceWidth: Float, sourceCenterX: Float, sourceCenterY: Float,
    targetWidth: Float, targetCenterX: Float, targetCenterY: Float, progress: Float): MediaOpeningTransform {
    val p = progress.coerceIn(0f, 1f)
    // Canonical completed endpoint: the established identity contract MediaOpeningTransform(1f, 0f, 0f)
    // (same literal MediaViewerScreen already uses). Also avoids IEEE -0.0 offsets from
    // negative deltas * 0f. Intermediate geometry is unchanged, bit for bit.
    if (p >= 1f) return MediaOpeningTransform(1f, 0f, 0f)
    val initial = if (sourceWidth > 0f && targetWidth > 0f) (sourceWidth / targetWidth).coerceIn(0.05f, 1f) else 0.94f
    return MediaOpeningTransform(initial + (1f - initial) * p,
        (sourceCenterX - targetCenterX) * (1f - p), (sourceCenterY - targetCenterY) * (1f - p))
}
