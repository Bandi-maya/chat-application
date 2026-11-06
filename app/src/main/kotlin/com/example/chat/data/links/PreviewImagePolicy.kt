package com.example.chat.data.links

/** Chaty #34: bounds for preview-image decoding. Pure Kotlin: JVM-testable.
 *  Downsampling is driven by a DECODED-PIXEL BUDGET and a max decoded
 *  dimension, never by width AND height thresholds - so a very wide/short
 *  panorama still shrinks to budget instead of decoding near 48 MB. */
object PreviewImagePolicy {

    /** Source images larger than this are rejected without decoding. */
    const val MAX_SOURCE_PIXELS = 12_000_000L

    /** Decoded thumbnail budget: 512x512 px = 1 MB ARGB at most. */
    const val DECODED_PIXEL_BUDGET = 262_144L

    /** No decoded side may exceed this, however the aspect ratio looks. */
    const val MAX_DECODED_DIMENSION = 1024

    fun accepts(width: Int, height: Int): Boolean =
        width > 0 && height > 0 &&
            width.toLong() * height.toLong() <= MAX_SOURCE_PIXELS

    /** Power-of-two inSampleSize that brings the decode within the pixel
     *  budget AND the dimension cap. Assumes accepts() already passed. */
    fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (true) {
            val w = width / sample
            val h = height / sample
            val withinBudget = w.toLong() * h.toLong() <= DECODED_PIXEL_BUDGET
            val withinDimension = maxOf(w, h) <= MAX_DECODED_DIMENSION
            if (withinBudget && withinDimension) return sample
            sample *= 2
        }
    }

    /** Decoded ARGB byte cost after sampling, for cache sizing checks. */
    fun decodedBytes(width: Int, height: Int): Long {
        val sample = sampleSizeFor(width, height)
        return (width / sample).toLong() * (height / sample).toLong() * 4L
    }
}
