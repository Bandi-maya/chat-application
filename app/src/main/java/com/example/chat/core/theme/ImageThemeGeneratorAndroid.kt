package com.example.chat.core.theme

import java.io.File

    /**
 * Android decode of Dart's instantiateImageCodec(targetWidth: 64, targetHeight: 64): scaled to exactly 64x64
 * (Bitmap.createScaledBitmap filter=true, not byte-identical to Skia's codec scaling) then RGBA bytes.
 * Throws on undecodable input (Dart's caller shows the 'Could not generate a theme' snackbar).
 */
fun ImageThemeGenerator.generateFromImageFile(imageFile: File, isDark: Boolean = true): ThemeConfig {
    val bmp = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath) ?: throw IllegalArgumentException("undecodable image")
    val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 64, 64, true)
    val px = IntArray(64 * 64)
    scaled.getPixels(px, 0, 64, 0, 0, 64, 64)
    val rgba = ByteArray(px.size * 4)
    for (k in px.indices) {
        val p = px[k]
        rgba[k * 4] = ((p shr 16) and 0xFF).toByte(); rgba[k * 4 + 1] = ((p shr 8) and 0xFF).toByte()
        rgba[k * 4 + 2] = (p and 0xFF).toByte(); rgba[k * 4 + 3] = ((p shr 24) and 0xFF).toByte()
    }
    if (scaled !== bmp) scaled.recycle()
    bmp.recycle()
    return fromDominant(dominantFromRgba(rgba), isDark)
}

