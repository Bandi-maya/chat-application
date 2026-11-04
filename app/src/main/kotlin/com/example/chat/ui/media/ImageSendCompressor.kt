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
        return orientationMatrixFromInt(o)
    }

    internal fun orientationMatrixFromInt(o: Int): Matrix {
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

    /**
     * File-backed image downscaling and JPEG compression.
     * Keeps memory bounded with inSampleSize, honors EXIF orientation,
     * and constrains max dimensions to [maxDimension] pixels (e.g. 1920) at [quality]% JPEG.
     * Returns a new temporary File if compressed, or the original [file] if bypassed/failed.
     */
    fun compressFile(
        file: java.io.File,
        maxDimension: Int = 1920,
        quality: Int = 85,
    ): java.io.File {
        if (!file.isFile || !file.canRead() || file.length() <= 0L) return file
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val origW = bounds.outWidth
            val origH = bounds.outHeight
            val mime = bounds.outMimeType?.lowercase().orEmpty()
            if (origW <= 0 || origH <= 0) return file
            if (mime == "image/gif") return file

            // If already within reasonable dimensions and small (< 400KB), keep as-is
            if (file.length() <= 400 * 1024L && maxOf(origW, origH) <= maxDimension) return file

            var sampleSize = 1
            while (origW / (sampleSize * 2) >= maxDimension && origH / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return file

            val matrix = try {
                val exif = ExifInterface(file.absolutePath)
                val o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                orientationMatrixFromInt(o)
            } catch (_: Throwable) { null }

            var oriented = bmp
            if (matrix != null && !matrix.isIdentity) {
                oriented = try {
                    Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true).also {
                        if (it !== bmp) bmp.recycle()
                    }
                } catch (_: Throwable) { bmp }
            }

            val curW = oriented.width
            val curH = oriented.height
            val scale = minOf(1f, maxDimension.toFloat() / maxOf(curW, curH))
            var scaled = oriented
            if (scale < 0.95f) {
                scaled = try {
                    val targetW = (curW * scale).toInt().coerceAtLeast(1)
                    val targetH = (curH * scale).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(oriented, targetW, targetH, true).also {
                        if (it !== oriented) oriented.recycle()
                    }
                } catch (_: Throwable) { oriented }
            }

            val targetFile = java.io.File(file.parentFile ?: java.io.File("."), "chaty-outgoing-compressed-${java.util.UUID.randomUUID()}.jpg")
            try {
                targetFile.outputStream().use { out ->
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), out)) {
                        targetFile.delete()
                        return file
                    }
                }
                if (targetFile.length() > 0L && (targetFile.length() < file.length() || file.length() > 2 * 1024 * 1024L)) {
                    targetFile
                } else {
                    targetFile.delete()
                    file
                }
            } finally {
                if (scaled !== bmp && scaled !== oriented) scaled.recycle()
                if (oriented !== bmp) oriented.recycle()
                bmp.recycle()
            }
        } catch (_: Throwable) {
            file
        }
    }

    private fun isGif(b: ByteArray) =
        b.size > 5 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()
}
