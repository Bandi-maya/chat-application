package com.example.chat.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Matches the api branch: re-encode as JPEG at the user's Img_highres_seek quality,
 * NO resize. Returns null (caller keeps the original bytes, same as api's catch)
 * when: undecodable, the result is not smaller, or the source is GIF/PNG with alpha
 * (re-encoding would lose animation/transparency). All 8 EXIF orientations are baked
 * into pixels (JPEG re-encode drops the tag), including mirrored/transposed ones.
 * Output is never written to disk. android.media.ExifInterface(InputStream) needs API 24 = minSdk.
 */
object ImageSendCompressor {
    fun compress(bytes: ByteArray, quality: Int): ByteArray? = try {
        val q = quality.coerceIn(10, 100)
        if (q >= 100 || isGif(bytes)) null else doCompress(bytes, q, requireSmaller = true)
    } catch (_: Throwable) { null } // includes OutOfMemoryError: fall back to original

    /** [requireSmaller]=false is for the orientation fixture test only. */
    internal fun doCompress(bytes: ByteArray, q: Int, requireSmaller: Boolean): ByteArray? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        var out: Bitmap = bmp
        try {
            if (bmp.hasAlpha()) return null
            val matrix = orientationMatrix(bytes) ?: return null // unreadable tag: keep original
            if (!matrix.isIdentity) {
                out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            }
            val sink = ByteArrayOutputStream(bytes.size / 2)
            if (!out.compress(Bitmap.CompressFormat.JPEG, q, sink)) return null
            val result = sink.toByteArray()
            return if (result.isNotEmpty() && (!requireSmaller || result.size < bytes.size)) result else null
        } finally {
            if (out !== bmp) out.recycle()
            bmp.recycle()
        }
    }

    /** Matrix for all 8 EXIF orientations; null if the EXIF block cannot be read at all
     *  (then the original is kept rather than risk changing the subject). A file with
     *  no tag is NORMAL and yields the identity matrix. */
    internal fun orientationMatrix(bytes: ByteArray): Matrix? {
        val o = try {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Throwable) { return null }
        return Matrix().apply {
            when (o) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                else -> Unit // NORMAL/UNDEFINED
            }
        }
    }

    private fun isGif(b: ByteArray) =
        b.size > 5 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()
}
