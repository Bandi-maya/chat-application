package com.example.chat.ui.core.system

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream

/**
 * Two-pass bitmap decode that never allocates the full-size source: a bounds
 * pass, then a power-of-two sampled decode whose long edge stays at or above
 * [maxEdgePx]. Bitmaps cost width*height*4 bytes, so decoding a 50 MP gallery
 * photo directly allocates ~200 MB of heap and can OOM budget devices; the
 * sampled pass costs a few MB instead. Callers apply their exact final scale
 * afterwards, so visual output is unchanged.
 */
object SampledBitmapDecoder {

    fun decode(bytes: ByteArray, maxEdgePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /** Sampled decode keyed on the SHORT edge: the returned bitmap's short
     *  edge stays at or above [minShortEdgePx]. For callers that crop or
     *  scale against the short side (center-crop squares, avatar
     *  compression), where long-edge sampling could leave the short side too
     *  small. */
    fun decodeShortEdge(bytes: ByteArray, minShortEdgePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val short = minOf(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(short, short, minShortEdgePx)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /** [openStream] is invoked twice (bounds pass, then decode pass), so it
     *  must return a fresh stream each time. */
    fun decode(openStream: () -> InputStream?, maxEdgePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Bounds-only decode returns null by contract; only a missing stream is a failure here.
        val boundsStream = openStream() ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
        }
        return openStream()?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Largest power-of-two downscale that still keeps the long edge at or
     *  above [maxEdgePx], so the caller's final exact scale stays a downscale
     *  (never an upscale) and quality is preserved. */
    fun sampleSize(width: Int, height: Int, maxEdgePx: Int): Int {
        if (maxEdgePx <= 0) return 1
        var sample = 1
        val longEdge = maxOf(width, height)
        while (longEdge / (sample * 2) >= maxEdgePx) sample *= 2
        return sample
    }
}
