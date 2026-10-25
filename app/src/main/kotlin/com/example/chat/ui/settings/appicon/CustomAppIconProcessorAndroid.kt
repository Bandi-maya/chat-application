package com.example.chat.ui.settings.appicon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exact port of lib/ui/core/controllers/custom_app_icon_processor.dart.
 *
 *  Decodes the picked PNG, center-crops to a square, scales it into the
 *  adaptive safe zone (0.82 of a 512px canvas, centered, transparent
 *  elsewhere), and persists atomically (tmp + rename) under
 *  filesDir/branding/custom_icons/<safeId>.launcher.png.
 */
class AndroidCustomIconProcessor(
    context: Context,
    private val brandingRoot: File = File(context.filesDir, "branding/custom_icons"),
) : CustomIconProcessor {

    companion object {
        const val OUTPUT_SIZE = 512
        const val ADAPTIVE_SAFE_ZONE_FRACTION = 0.82f
    }

    override suspend fun persistSquarePng(sourcePng: ByteArray, presetId: String?): File =
        withContext(Dispatchers.IO) {
            require(sourcePng.isNotEmpty()) { "Custom icon data is empty." }

            val source = BitmapFactory.decodeByteArray(sourcePng, 0, sourcePng.size)
                ?: throw IllegalStateException("Unable to decode custom icon image.")
            try {
                val sourceWidth = source.width
                val sourceHeight = source.height
                val squareSide = minOf(sourceWidth, sourceHeight)
                val left = (sourceWidth - squareSide) / 2
                val top = (sourceHeight - squareSide) / 2
                val sourceRect = Rect(left, top, left + squareSide, top + squareSide)

                val safeSize = OUTPUT_SIZE * ADAPTIVE_SAFE_ZONE_FRACTION
                val safeOffset = ((OUTPUT_SIZE - safeSize) / 2).toInt()
                val destinationRect = Rect(
                    safeOffset,
                    safeOffset,
                    safeOffset + safeSize.toInt(),
                    safeOffset + safeSize.toInt(),
                )

                val launcher = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(launcher)
                    // Dart: canvas.drawColor(transparent, BlendMode.src) - the
                    // fresh bitmap is already transparent; draw with a high
                    // quality anti-aliased paint (FilterQuality.high).
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                    canvas.drawBitmap(source, sourceRect, destinationRect, paint)
                } catch (e: Exception) {
                    launcher.recycle()
                    throw e
                }

                val normalized = java.io.ByteArrayOutputStream().use { out ->
                    if (!launcher.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        launcher.recycle()
                        throw IllegalStateException("Unable to encode the processed adaptive launcher icon.")
                    }
                    launcher.recycle()
                    out.toByteArray()
                }
                if (normalized.isEmpty()) {
                    throw IllegalStateException("Unable to encode the processed adaptive launcher icon.")
                }

                if (!brandingRoot.exists()) brandingRoot.mkdirs()
                val safeId = (presetId ?: "custom_${System.currentTimeMillis() * 1000}")
                    .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val target = File(brandingRoot, "$safeId.launcher.png")
                val temporary = File("${target.path}.tmp")

                temporary.writeBytes(normalized)
                if (target.exists()) target.delete()
                if (!temporary.renameTo(target)) {
                    temporary.delete()
                    throw IllegalStateException("Unable to persist the processed launcher icon.")
                }
                target
            } finally {
                source.recycle()
            }
        }
}
