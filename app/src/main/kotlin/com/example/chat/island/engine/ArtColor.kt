package com.example.chat.island.engine

import android.graphics.Bitmap
import android.graphics.Color

/** Selects an accent from a populated, colorful region rather than blending unrelated hues. */
fun dominantColor(src: Bitmap): Int? {
    if (src.isRecycled || src.width < 1 || src.height < 1) return null
    return try {
        val buckets = HashMap<Int, AccentBin>()
        val sample = FloatArray(3)
        val rows = minOf(32, src.height)
        val columns = minOf(32, src.width)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val pixel = src.getPixel(column * (src.width - 1) / maxOf(1, columns - 1), row * (src.height - 1) / maxOf(1, rows - 1))
                if (Color.alpha(pixel) >= 128) {
                    Color.colorToHSV(pixel, sample)
                    val key = (sample[0] / 20f).toInt().coerceIn(0, 17)
                    buckets.getOrPut(key) { AccentBin() }.add(pixel, sample[1], sample[2])
                }
            }
        }
        val selected = buckets.values.maxByOrNull { it.score } ?: return null
        Color.colorToHSV(selected.mean(), sample)
        sample[1] = sample[1].coerceAtLeast(0.55f)
        sample[2] = sample[2].coerceAtLeast(0.85f)
        Color.HSVToColor(sample)
    } catch (_: Exception) {
        null
    }
}

private class AccentBin {
    private var count = 0
    private val channels = LongArray(3)
    var score: Double = 0.0
        private set

    fun add(pixel: Int, saturation: Float, brightness: Float) {
        count += 1
        channels[0] += Color.red(pixel)
        channels[1] += Color.green(pixel)
        channels[2] += Color.blue(pixel)
        score += 0.05 + saturation * brightness
    }

    fun mean(): Int = Color.rgb(
        (channels[0] / count).toInt(),
        (channels[1] / count).toInt(),
        (channels[2] / count).toInt()
    )
}
