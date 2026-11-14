package com.example.chat.core.theme

import com.example.chat.ui.core.system.SampledBitmapDecoder
import java.io.File

    /**
 * Android decode of Dart's instantiateImageCodec(targetWidth: 64, targetHeight: 64): scaled to exactly 64x64
 * (Bitmap.createScaledBitmap filter=true, not byte-identical to Skia's codec scaling) then RGBA bytes.
 * Throws on undecodable input (Dart's caller shows the 'Could not generate a theme' snackbar).
 * The source is bounds-then-sampled first: the palette only needs 64x64, so
 * decoding a full-res wallpaper photo (a 50 MP shot is ~200 MB of ARGB_8888)
 * is pure OOM risk on budget devices. Sampling keeps the long edge at or
 * above 128 px, so the exact 64x64 scale below is still a downscale.
 */
fun ImageThemeGenerator.generateFromImageFile(imageFile: File, isDark: Boolean = true): ThemeConfig {
    val bmp = SampledBitmapDecoder.decode({ imageFile.inputStream() }, 128) ?: throw IllegalArgumentException("undecodable image")
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

