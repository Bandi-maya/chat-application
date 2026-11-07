package com.example.chat.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/** Android half of the media-tier pipeline: decode a picked image safely,
 *  bake EXIF orientation into the pixels, scale to the tier, re-encode as
 *  JPEG WITHOUT metadata (AUTO/HD), or pass the original bytes through
 *  (FILE). Also produces the tiny blurred placeholder the receiver shows
 *  while the real attachment downloads; it travels inside the encrypted
 *  message metadata, so the server stays blind.
 */
class ImageSendProcessor(private val context: Context) {

    data class Prepared(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val mimeType: String,
        /** base64 tiny JPEG (24px) shown blurred while downloading; null for FILE. */
        val blurPlaceholder: String?,
    )

    fun prepare(uri: Uri, tier: ImageSendPolicy.Tier): Prepared {
        if (!tier.reencode) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Cannot read image")
            return Prepared(bytes, 0, 0, "application/octet-stream", blurPlaceholder = null)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw IllegalArgumentException("Cannot decode image")
        val sample = ImageSendPolicy.sampleSize(bounds.outWidth, bounds.outHeight, tier.maxDimensionPx)

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: throw IllegalArgumentException("Cannot decode image")

        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val upright = applyOrientation(decoded, orientation)

        val (targetW, targetH) = ImageSendPolicy.targetSize(upright.width, upright.height, tier.maxDimensionPx)
        val scaled = if (targetW != upright.width || targetH != upright.height) {
            Bitmap.createScaledBitmap(upright, targetW, targetH, true)
        } else upright

        val out = ByteArrayOutputStream()
        // Re-encoding writes NO EXIF block: metadata is stripped by construction.
        scaled.compress(Bitmap.CompressFormat.JPEG, tier.jpegQuality, out)
        return Prepared(
            bytes = out.toByteArray(),
            width = scaled.width,
            height = scaled.height,
            mimeType = "image/jpeg",
            blurPlaceholder = blurPlaceholder(scaled),
        )
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun blurPlaceholder(bitmap: Bitmap): String? = runCatching {
        val tiny = Bitmap.createScaledBitmap(bitmap, 24, (24.0 * bitmap.height / bitmap.width).toInt().coerceAtLeast(1), true)
        val out = ByteArrayOutputStream()
        tiny.compress(Bitmap.CompressFormat.JPEG, 60, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}
