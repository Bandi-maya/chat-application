package com.example.chat.ui.camera

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import java.io.File
import kotlin.math.roundToInt

/** Creates a derived local image; never changes the source or invents detail. */
object CameraPhotoOutput {
    fun process(context: Context, source: String, options: CameraCaptureOptions, fullRatio: Float): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cannot decode capture" }
        // Never silently downsample final images. If full-size processing
        // cannot fit, refuse the derived operation and retain the original.
        val expectedBytes = bounds.outWidth.toLong() * bounds.outHeight * 12L
        val runtime = Runtime.getRuntime()
        val availableBytes = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        check(expectedBytes < availableBytes * 3 / 4) { "Not enough memory for full-resolution photo options" }
        var raw: Bitmap? = null
        var upright: Bitmap? = null
        var output: Bitmap? = null
        var file: File? = null
        try {
            val decoded = BitmapFactory.decodeFile(source) ?: error("Cannot decode capture")
            raw = decoded
            val orientation = ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { postScale(-1f, 1f); postRotate(90f) }
                    ExifInterface.ORIENTATION_TRANSVERSE -> { postScale(-1f, 1f); postRotate(270f) }
                }
            }
            val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            upright = oriented
            val ratio = options.aspect.portraitRatio ?: fullRatio.coerceIn(.3f, 3f)
            val orientedRatio = if (oriented.width > oriented.height && ratio < 1f) 1f / ratio else ratio
            val box = centerCropBounds(oriented.width, oriented.height, orientedRatio)
            if (box[0] == 0 && box[1] == 0 && box[2] == oriented.width && box[3] == oriented.height &&
                !options.clarity && !options.watermark) {
                return source
            }
            val rendered = Bitmap.createBitmap(box[2], box[3], Bitmap.Config.ARGB_8888)
            output = rendered
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (options.clarity) {
                val c = 1.08f
                val offset = 128f * (1 - c)
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(c,0f,0f,0f,offset, 0f,c,0f,0f,offset, 0f,0f,c,0f,offset, 0f,0f,0f,1f,0f)))
            }
            val canvas = Canvas(rendered)
            canvas.drawBitmap(oriented, Rect(box[0],box[1],box[0]+box[2],box[1]+box[3]), Rect(0,0,box[2],box[3]), paint)
            if (options.watermark) {
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = (rendered.width / 35f).coerceAtLeast(14f); setShadowLayer(3f, 0f, 1f, Color.BLACK) }
                canvas.drawText("Chaty", rendered.width * .04f, rendered.height * .95f, text)
            }
            val dest = File.createTempFile("chaty_photo_", ".jpg", context.cacheDir)
            file = dest
            dest.outputStream().use { check(rendered.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            return dest.absolutePath
        } catch (error: Throwable) {
            file?.delete()
            throw error
        } finally {
            output?.takeUnless { it.isRecycled }?.recycle()
            upright?.takeUnless { it === raw || it.isRecycled }?.recycle()
            raw?.takeUnless { it.isRecycled }?.recycle()
        }
    }
}
