package com.example.chat.data.media

/** Three-tier photo sending (media matrix, best of WhatsApp/Signal/Telegram):
 *
 *  AUTO - 1600 px adaptive-quality JPEG (Signal-style: quality scales with
 *         measured uplink, floor 50, ceiling 87). The default; data-friendly.
 *  HD   - 4096 px QF 92 (WhatsApp HD class). One tap at send time.
 *  FILE - original bytes, EXIF preserved. The escape hatch every competitor
 *         offers; clearly separated so metadata preservation is a choice.
 *
 *  EXIF is stripped (and orientation baked into pixels) in AUTO and HD;
 *  only FILE keeps the original bytes.
 *
 *  This type is pure policy: JVM-testable, no Android imports.
 */
object ImageSendPolicy {

    enum class SendMode { AUTO, HD, FILE }

    data class Tier(
        val mode: SendMode,
        val maxDimensionPx: Int,
        val jpegQuality: Int,
        val stripExif: Boolean,
        val reencode: Boolean,
    )

    const val AUTO_MAX_DIM = 1600
    const val HD_MAX_DIM = 4096
    const val AUTO_MIN_QUALITY = 50
    const val AUTO_MAX_QUALITY = 87
    const val HD_QUALITY = 92

    /** Adaptive quality from measured uplink bandwidth (Signal's approach):
     *  great on Wi-Fi, still sends on 2G. Null bandwidth = middle of range. */
    fun autoQuality(uplinkKbps: Int?): Int {
        if (uplinkKbps == null || uplinkKbps <= 0) return 70
        return when {
            uplinkKbps < 100 -> AUTO_MIN_QUALITY
            uplinkKbps < 500 -> 58
            uplinkKbps < 2_000 -> 66
            uplinkKbps < 8_000 -> 76
            else -> AUTO_MAX_QUALITY
        }
    }

    fun tier(mode: SendMode, uplinkKbps: Int? = null): Tier = when (mode) {
        SendMode.AUTO -> Tier(mode, AUTO_MAX_DIM, autoQuality(uplinkKbps), stripExif = true, reencode = true)
        SendMode.HD -> Tier(mode, HD_MAX_DIM, HD_QUALITY, stripExif = true, reencode = true)
        SendMode.FILE -> Tier(mode, Int.MAX_VALUE, 100, stripExif = false, reencode = false)
    }

    /** inSampleSize ladder for memory-safe decode: largest power of two that
     *  keeps both dimensions >= the tier target. */
    fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (maxOf(w / 2, h / 2) >= maxDimension && w / 2 > 0 && h / 2 > 0) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    /** Target bounds after scaling, aspect preserved, never upscale. */
    fun targetSize(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return width to height
        val largest = maxOf(width, height)
        if (largest <= maxDimension) return width to height
        val scale = maxDimension.toFloat() / largest
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }
}
