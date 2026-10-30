package com.example.chat.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Live preview compositor for the segmented background effects (bg_*).
 *
 * The camera ImageAnalysis stream (KEEP_ONLY_LATEST) feeds orient-aware RGB
 * thumbnails from [CameraFrameSampler.toRgbThumbnail]; each frame is mirrored
 * for the front camera, segmented through [SegmentationBridge] (canonical
 * MediaPipe selfie_segmenter, verified at build time) and composited with the
 * same procedural backdrops the capture-time bake uses, so the preview matches
 * the captured photo's look at thumbnail scale.
 *
 * Everything here is best effort and runs off the main thread: a null result
 * (segmenter unavailable, mask failure) simply leaves the viewfinder
 * unfiltered - the capture-time bake in ImageFxBaker remains the exact path.
 */
class LiveSegmentPreview(private val bridge: SegmentationBridge) {

    /**
     * Composite one analysis frame, or null to keep the plain preview.
     * [rotationDegrees] orients the frame upright; [mirrored] matches the
     * front-camera preview transform.
     */
    suspend fun render(
        frame: FrameData,
        rotationDegrees: Int,
        mirrored: Boolean,
        shaderKey: String?,
        intensity: Double,
    ): Bitmap? {
        val thumb = try {
            CameraFrameSampler.toRgbThumbnail(frame, rotationDegrees)
        } catch (_: Exception) {
            return null
        }
        val w = thumb.width
        val h = thumb.height
        if (w <= 0 || h <= 0) return null
        val bytes = thumb.bytes
        val pixels = IntArray(w * h)
        for (i in pixels.indices) {
            val r = bytes[i * 3].toInt() and 0xFF
            val g = bytes[i * 3 + 1].toInt() and 0xFF
            val b = bytes[i * 3 + 2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        var image = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        if (mirrored) {
            val flipped = Bitmap.createBitmap(
                image, 0, 0, w, h, Matrix().apply { postScale(-1f, 1f) }, false,
            )
            if (flipped !== image) {
                image.recycle()
                image = flipped
            }
        }
        val mask = bridge.personMask(image) ?: run {
            image.recycle()
            return null
        }
        val out = composite(image, mask, shaderKey, intensity)
        mask.recycle()
        image.recycle()
        return out
    }

    /** Person-sharp / background-treated composite driven by the mask alpha.
     *  Keep in sync with ImageFxBaker.drawSegmentedBackground. */
    private fun composite(image: Bitmap, mask: Bitmap, shaderKey: String?, intensity: Double): Bitmap {
        val w = image.width
        val h = image.height
        val scaledMask = Bitmap.createScaledBitmap(mask, w, h, true)
        val background = when (shaderKey) {
            "segment_bg_gradient" -> studioGradientScene(w, h)
            "segment_bg_office" -> officeScene(w, h)
            else -> approxBlur(image, intensity)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(background, 0f, 0f, Paint())
        val cutout = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cutCanvas = Canvas(cutout)
        cutCanvas.drawBitmap(image, 0f, 0f, Paint())
        cutCanvas.drawBitmap(
            scaledMask, 0f, 0f,
            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) },
        )
        canvas.drawBitmap(cutout, 0f, 0f, Paint())
        scaledMask.recycle()
        cutout.recycle()
        background.recycle()
        return out
    }

    /** Keep in sync with ImageFxBaker.approxBlur. */
    private fun approxBlur(src: Bitmap, intensity: Double): Bitmap {
        val divisor = (6.0 + 10.0 * intensity.coerceIn(0.0, 1.0)).toInt()
        val small = Bitmap.createScaledBitmap(
            src, maxOf(1, src.width / divisor), maxOf(1, src.height / divisor), true,
        )
        return Bitmap.createScaledBitmap(small, src.width, src.height, true).also {
            if (it !== small) small.recycle()
        }
    }

    /** Keep in sync with ImageFxBaker.studioGradientScene. */
    private fun studioGradientScene(w: Int, h: Int): Bitmap {
        val scene = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(scene)
        val paint = Paint().apply {
            shader = RadialGradient(
                w / 2f, h * 0.38f, maxOf(w, h) * 0.75f,
                intArrayOf(0xFF3DDC97.toInt(), 0xFF0F2E2A.toInt(), 0xFF071B19.toInt()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return scene
    }

    /** Keep in sync with ImageFxBaker.officeScene. */
    private fun officeScene(w: Int, h: Int): Bitmap {
        val scene = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(scene)
        c.drawColor(0xFFD9C7B2.toInt())
        val floor = Paint().apply { color = 0xFFB49A80.toInt() }
        c.drawRect(0f, h * 0.72f, w.toFloat(), h.toFloat(), floor)
        val windowGlow = Paint().apply {
            shader = RadialGradient(
                w * 0.8f, h * 0.22f, w * 0.5f,
                intArrayOf(0xFFFFF6E5.toInt(), 0x00FFF6E5),
                null, Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), windowGlow)
        val frame = Paint().apply { color = 0x33FFFFFF }
        c.drawRect(w * 0.62f, h * 0.06f, w * 0.97f, h * 0.40f, frame)
        return scene
    }
}
