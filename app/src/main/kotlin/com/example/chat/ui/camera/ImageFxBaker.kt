package com.example.chat.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Exact port of bakeEffectIntoFile in camera_capture_screen.dart.
 *
 *  Pipeline: decode with target width 1440 -> draw onto same-size canvas with
 *  ColorFilter.matrix(lerped) -> stage PNG -> re-encode JPEG quality 90 ->
 *  delete staged PNG (best effort) -> return JPEG path (PNG path on failure).
 *
 *  Native deviations (documented):
 *  - Flutter instantiateImageCodec(targetWidth: 1440) decodes to the nearest
 *    supported sample size; Android BitmapFactory uses inSampleSize then an
 *    exact aspect-preserving scale to width 1440. Output pixels equivalent.
 *  - flutter_image_compress.compressAndGetFile(quality 90) maps to
 *    Bitmap.compress(JPEG, 90). */
object ImageFxBaker {

    /** Matrix lerp shared with the engine (identity -> target by intensity). */
    fun lerpedMatrix(effect: ChatyCameraEffect, intensity: Double): FloatArray {
        val m = effect.colorMatrix ?: return FloatArray(20) { i -> if (i % 6 == 0) 1f else 0f }
        val lerped = EffectEngine.interpolateMatrix(m, intensity)
        return FloatArray(20) { lerped[it].toFloat() }
    }

    suspend fun bakeEffectIntoFile(
        context: Context,
        path: String,
        effect: ChatyCameraEffect,
        intensity: Double,
    ): String = withContext(Dispatchers.IO) {
        val matrix = lerpedMatrix(effect, intensity)

        // Decode downsampled toward 1440px width, then exact-scale.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1440) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(path, opts)
            ?: throw IllegalStateException("decode failed: $path")
        val targetW = 1440
        val image = if (decoded.width > targetW) {
            val h = (decoded.height.toLong() * targetW / decoded.width).toInt()
            Bitmap.createScaledBitmap(decoded, targetW, h, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else decoded

        val rendered = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(rendered)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
        canvas.drawBitmap(image, 0f, 0f, paint)
        image.recycle()

        val temp = context.cacheDir
        val staged = File(temp, "chaty_fx_${System.currentTimeMillis()}.png")
        FileOutputStream(staged).use { rendered.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val out = File(temp, "chaty_fx_${System.currentTimeMillis()}.jpg")
        val ok = try {
            FileOutputStream(out).use { rendered.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        } catch (_: Exception) { false }
        rendered.recycle()
        try { staged.delete() } catch (_: Exception) { /* best effort, mirrors Dart catchError */ }
        if (ok) out.absolutePath else staged.absolutePath
    }
}
