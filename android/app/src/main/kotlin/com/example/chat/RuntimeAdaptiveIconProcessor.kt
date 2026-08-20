package com.example.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import java.io.File
import java.io.FileOutputStream

internal object RuntimeAdaptiveIconProcessor {
    private const val OUTPUT_SIZE = 512
    private const val SAFE_ZONE_FRACTION = 0.82f

    fun process(context: Context, sourcePath: String): File {
        val source = BitmapFactory.decodeFile(sourcePath)
            ?: error("Unable to decode the selected custom icon image.")

        val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.drawColor(Color.TRANSPARENT)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val safeSize = OUTPUT_SIZE * SAFE_ZONE_FRACTION
            val left = (OUTPUT_SIZE - safeSize) / 2f
            val top = (OUTPUT_SIZE - safeSize) / 2f
            val destination = RectF(left, top, left + safeSize, top + safeSize)

            val sourceRatio = source.width.toFloat() / source.height.toFloat()
            val crop = if (sourceRatio > 1f) {
                val cropWidth = source.height
                val startX = (source.width - cropWidth) / 2
                Rect(startX, 0, startX + cropWidth, source.height)
            } else {
                val cropHeight = source.width
                val startY = (source.height - cropHeight) / 2
                Rect(0, startY, source.width, startY + cropHeight)
            }
            canvas.drawBitmap(source, crop, destination, paint)

            val directory = File(context.filesDir, "custom_launcher/runtime").apply { mkdirs() }
            val target = File(directory, "active_custom_icon.webp")
            val temporary = File(directory, "active_custom_icon.webp.tmp")

            FileOutputStream(temporary).use { stream ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                check(output.compress(format, 100, stream)) {
                    "Unable to encode the launcher icon as WebP."
                }
                stream.fd.sync()
            }

            if (target.exists() && !target.delete()) {
                temporary.delete()
                error("Unable to replace the previous runtime launcher icon.")
            }
            check(temporary.renameTo(target)) {
                temporary.delete()
                "Unable to finalize the runtime launcher icon."
            }
            return target
        } finally {
            source.recycle()
            output.recycle()
        }
    }
}
