package com.example.chat.ui.core.system

/** Resolved system bar paint (ARGB ints). */
data class SystemBarsStyle(val statusBarColor: Long, val navigationBarColor: Long, val lightStatusIcons: Boolean, val lightNavIcons: Boolean)

/** Pure port of main.dart's SystemUiOverlayStyle block. Null prefs fall back to the theme surface colour. */
object SystemBarsPolicy {
    /** Flutter Color.computeLuminance (sRGB -> linear, 0.2126/0.7152/0.0722). */
    fun luminance(argb: Long): Double {
        fun lin(c: Int): Double { val s = c / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        val r = ((argb shr 16) and 0xFF).toInt(); val g = ((argb shr 8) and 0xFF).toInt(); val b = (argb and 0xFF).toInt()
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    fun resolve(statusBarPref: Long?, navBarPref: Long?, themeSurface: Long): SystemBarsStyle {
        val s = statusBarPref ?: themeSurface
        val n = navBarPref ?: themeSurface
        // Dart: icon brightness DARK when luminance > 0.5 (i.e. dark icons on a light bar).
        return SystemBarsStyle(s, n, lightStatusIcons = !(luminance(s) > 0.5), lightNavIcons = !(luminance(n) > 0.5))
    }
}
