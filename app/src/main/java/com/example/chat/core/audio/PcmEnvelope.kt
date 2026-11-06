package com.example.chat.core.audio

import kotlin.math.abs

/** Bounded, sound-derived peaks. Silence is zero, never fabricated/random bars. */
class PcmEnvelope(private val bins: Int = 48, private val durationUs: Long) {
    init { require(bins in 1..256); require(durationUs > 0) }
    private val peaks = FloatArray(bins)
    fun accept(timeUs: Long, amplitude: Float) {
        if (timeUs < 0 || timeUs > durationUs || !amplitude.isFinite()) return
        val bucket = ((timeUs.toDouble() / durationUs) * bins).toInt().coerceIn(0, bins - 1)
        peaks[bucket] = maxOf(peaks[bucket], abs(amplitude).coerceIn(0f, 1f))
    }
    fun values(): List<Float> = peaks.toList()
}
