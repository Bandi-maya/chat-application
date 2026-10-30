package com.example.chat.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Full-resolution color transform. Original stays read-only; memory pressure refuses
 * the transform rather than silently reducing resolution. No denoise/detail recovery claim.
 *
 * shaderKey effects render here on the capture path: "pixelate" (original
 * nearest-neighbor resample, every API) and "segment_*" (MediaPipe selfie
 * segmenter mask composites via SegmentationBridge). Segmentation fails open:
 * a missing mask returns applied=false and the unfiltered image, so callers
 * can drop the effect tag instead of claiming a filter that never rendered. */
object ImageFxBaker {

    /** Bake result: the output path plus whether the effect truly rendered. */
    data class BakedEffect(val path: String, val applied: Boolean)

    /** Pre-bake decision for one photo capture. Pure and JVM-testable; the
     *  capture screen and its proofs share this exact contract. */
    data class CaptureEffectPlan(
        val bake: Boolean,
        /** Metadata tag after a successful bake: shader pixels carry the look,
         *  so the tag clears to none (never a double-apply downstream). */
        val tagOnSuccess: String,
        val intensityOnSuccess: Double,
    )

    fun resolveCaptureEffectPlan(
        storyMode: Boolean,
        effectId: String,
        hasRenderPath: Boolean,
        isShaderEffect: Boolean,
        intensity: Double,
    ): CaptureEffectPlan {
        val bake = effectId != "none" && hasRenderPath && (storyMode || isShaderEffect)
        return CaptureEffectPlan(
            bake = bake,
            tagOnSuccess = if (isShaderEffect) "none" else effectId,
            intensityOnSuccess = if (isShaderEffect) 1.0 else intensity,
        )
    }

    /** Tag after the bake outcome: fail-open never claims an unrendered filter. */
    fun tagAfterBake(plan: CaptureEffectPlan, applied: Boolean, currentId: String, currentIntensity: Double): Pair<String, Double> =
        if (!plan.bake) currentId to currentIntensity
        else if (!applied) "none" to 1.0
        else plan.tagOnSuccess to plan.intensityOnSuccess

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
        onCreated: (File) -> Unit = {},
    ): BakedEffect = withContext(Dispatchers.IO) {
        val matrix = lerpedMatrix(effect, intensity)

        val image = InlineDocumentProcessor.loadUpright(path, preview = false)
        var rendered: Bitmap? = null
        var output: File? = null
        var applied = true
        try {
            val result = Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888)
            rendered=result
            val canvas = Canvas(result)
            if (effect.shaderKey != null && effect.shaderKey.startsWith("segment_")) {
                // Person mask from the MediaPipe selfie segmenter; fail open.
                val bridge = SegmentationBridge(context)
                val mask = try {
                    bridge.personMask(image)
                } finally {
                    bridge.dispose()
                }
                if (mask == null) {
                    applied = false
                    canvas.drawBitmap(image,0f,0f,Paint())
                } else {
                    drawSegmentedBackground(canvas, image, mask, effect.shaderKey, intensity)
                    mask.recycle()
                }
            } else if (effect.shaderKey == "pixelate") {
                // Original pixelate bake: downscale to the block grid, upscale
                // with nearest-neighbor (filterBitmap = false). Every API level.
                val blocks = pixelateBlocks(intensity).toInt().coerceIn(2, 512)
                val longSide = maxOf(image.width, image.height)
                val scale = blocks.toFloat() / longSide.toFloat()
                val smallW = maxOf(1, (image.width * scale).toInt())
                val smallH = maxOf(1, (image.height * scale).toInt())
                val small = Bitmap.createScaledBitmap(image, smallW, smallH, false)
                val pixelPaint = Paint().apply { isFilterBitmap = false }
                canvas.drawBitmap(small, null, Rect(0, 0, image.width, image.height), pixelPaint)
                small.recycle()
            } else {
                canvas.drawBitmap(image,0f,0f,Paint().apply { colorFilter=ColorMatrixColorFilter(ColorMatrix(matrix)) })
            }
            val file = File.createTempFile("chaty_fx_", ".jpg", context.cacheDir)
            output=file
            file.outputStream().use { check(result.compress(Bitmap.CompressFormat.JPEG,98,it)) }
            onCreated(file)
            BakedEffect(file.absolutePath, applied)
        } catch(t:Throwable) { output?.delete();throw t }
        finally { rendered?.recycle();image.recycle() }
    }

    /** Person-sharp / background-treated composite driven by the confidence mask. */
    private fun drawSegmentedBackground(
        canvas: Canvas,
        image: Bitmap,
        mask: Bitmap,
        shaderKey: String?,
        intensity: Double,
    ) {
        val w = image.width
        val h = image.height
        // Bilinear mask scale doubles as edge feathering.
        val scaledMask = Bitmap.createScaledBitmap(mask, w, h, true)
        val background = when (shaderKey) {
            "segment_bg_gradient" -> studioGradientScene(w, h)
            "segment_bg_office" -> officeScene(w, h)
            else -> approxBlur(image, intensity)
        }
        canvas.drawBitmap(background, 0f, 0f, Paint())
        // Person cutout: sharp image gated by the mask alpha.
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
    }

    /** Free original blur approximation: aggressive downscale + filtered upscale. */
    private fun approxBlur(src: Bitmap, intensity: Double): Bitmap {
        val divisor = (6.0 + 10.0 * intensity.coerceIn(0.0, 1.0)).toInt()
        val small = Bitmap.createScaledBitmap(
            src, maxOf(1, src.width / divisor), maxOf(1, src.height / divisor), true,
        )
        return Bitmap.createScaledBitmap(small, src.width, src.height, true).also {
            if (it !== small) small.recycle()
        }
    }

    /** Original Chaty studio backdrop: cool radial gradient, no assets. */
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

    /** Original Chaty office backdrop: warm wall, floor band, window light. */
    private fun officeScene(w: Int, h: Int): Bitmap {
        val scene = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(scene)
        c.drawColor(0xFFD9C7B2.toInt()) // warm wall
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
