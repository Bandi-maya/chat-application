package com.example.chat.core.theme

import java.io.File

/** Port of lib/ui/core/theme/image_theme_generator.dart. Pixel math is pure; decoding is Android (see generateFromImageFile). */
object ImageThemeGenerator {
    private const val defaultDominant = 0xFF6366F1L

    /** Flutter Color.lerp on opaque ARGB: per-channel (a + (b - a) * t).round(). */
    fun lerpColor(a: Long, b: Long, t: Double): Long {
        fun ch(shift: Int): Long {
            val x = ((a shr shift) and 0xFF).toDouble()
            val y = ((b shr shift) and 0xFF).toDouble()
            return Math.round(x + (y - x) * t).coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * Dart loop: every 16 bytes of the 64x64 RGBA buffer (every 4th pixel), alpha > 128 only;
     * integer mean per channel; falls back to 0xFF6366F1 when no pixel qualifies.
     */
    fun dominantFromRgba(rgba: ByteArray): Long {
        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0
        var i = 0
        while (i + 3 < rgba.size) {
            val a = rgba[i + 3].toInt() and 0xFF
            if (a > 128) {
                rSum += rgba[i].toInt() and 0xFF
                gSum += rgba[i + 1].toInt() and 0xFF
                bSum += rgba[i + 2].toInt() and 0xFF
                count++
            }
            i += 16
        }
        if (count == 0) return defaultDominant
        return 0xFF000000L or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
    }

    fun fromDominant(dominant: Long, isDark: Boolean, nowMillis: Long = System.currentTimeMillis()): ThemeConfig {
        val vibrant = dominant
        val bg = if (isDark) lerpColor(0xFF090D16, dominant, 0.15) else lerpColor(0xFFFFFFFF, dominant, 0.05)
        val surface = if (isDark) lerpColor(0xFF131B2E, dominant, 0.25) else lerpColor(0xFFF4F6F9, dominant, 0.15)
        val card = if (isDark) lerpColor(0xFF1E293B, dominant, 0.30) else 0xFFFFFFFFL
        val primaryText = if (isDark) 0xFFF8FAFCL else 0xFF0F172AL
        val secondaryText = if (isDark) 0xFF94A3B8L else 0xFF64748BL
        val outgoingText = if (ThemeConfig.contrastRatio(0xFF000000L, vibrant) >= 4.5) 0xFF000000L else 0xFFFFFFFFL
        val incomingText = if (ThemeConfig.contrastRatio(primaryText, surface) >= 3.5) primaryText
        else if (isDark) 0xFFFFFFFFL else 0xFF000000L
        return ThemeConfig(
            id = "image_theme_$nowMillis",
            name = "Custom Image Theme",
            isDark = isDark,
            accentColor = vibrant,
            backgroundColor = bg,
            surfaceColor = surface,
            cardColor = card,
            primaryTextColor = primaryText,
            secondaryTextColor = secondaryText,
            outgoingBubbleColor = vibrant,
            incomingBubbleColor = surface,
            outgoingTextColor = outgoingText,
            incomingTextColor = incomingText,
            linkColor = vibrant,
            dangerColor = 0xFFEF4444L,
            successColor = 0xFF10B981L,
            cornerRadius = 16.0,
            density = 1.0,
            fontScale = 1.0,
            wallpaperId = "subtle_dots",
        )
    }
}
