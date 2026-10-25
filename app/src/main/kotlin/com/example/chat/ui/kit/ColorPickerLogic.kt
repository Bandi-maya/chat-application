package com.example.chat.ui.kit

import kotlin.math.pow

/** Pure logic of lib/ui/core/design_system/color_picker.dart + ThemeConfig.calculateContrastRatio. */
object ColorPickerLogic {
    val presetSwatches: List<Long> = listOf(
        0xFF6366F1, 0xFF3B82F6, 0xFF10B981, 0xFFEC4899, 0xFF8B5CF6, 0xFFF59E0B,
        0xFFEF4444, 0xFF14B8A6, 0xFF06B6D4, 0xFF64748B, 0xFF000000, 0xFFFFFFFF,
    )

    private fun lin(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    /** Flutter Color.computeLuminance. */
    fun luminance(argb: Long): Double {
        val r = lin(((argb shr 16) and 0xFF) / 255.0)
        val g = lin(((argb shr 8) and 0xFF) / 255.0)
        val b = lin((argb and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun contrastRatio(fg: Long, bg: Long): Double {
        val l1 = luminance(fg); val l2 = luminance(bg)
        val bright = if (l1 > l2) l1 else l2
        val dark = if (l1 > l2) l2 else l1
        return (bright + 0.05) / (dark + 0.05)
    }

    /** _colorToHex: toARGB32 hex, padded to 8, upper, drop alpha. */
    fun toHex(argb: Long): String = (argb and 0xFFFFFFFFL).toString(16).padStart(8, '0').uppercase().substring(2)

    /** _updateFromHex: returns the new colour, or null when the text is not a valid 6 digit hex. */
    fun fromHex(text: String): Long? {
        val clean = text.replace("#", "").trim()
        if (clean.length != 6) return null
        val v = clean.toLongOrNull(16) ?: return null
        return 0xFF000000L or v
    }

    fun hasContrastWarning(ratio: Double?): Boolean = ratio != null && ratio < 4.5

    /** _autoFixContrast: bgLum > 0.5 -> foreground token else onPrimary token. */
    fun autoFix(background: Long, foregroundToken: Long, onPrimaryToken: Long): Long =
        if (luminance(background) > 0.5) foregroundToken else onPrimaryToken
}
