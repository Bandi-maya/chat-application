package com.example.chat.island.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

val AccentPresets: List<Pair<String, Int>> = listOf(
    "Violet" to 0xFF7B3DF5.toInt(), "Blue" to 0xFF346BEB.toInt(),
    "Cyan" to 0xFF0099B8.toInt(), "Teal" to 0xFF008D7B.toInt(),
    "Green" to 0xFF238647.toInt(), "Lime" to 0xFF7C9F25.toInt(),
    "Amber" to 0xFFCF9200.toInt(), "Orange" to 0xFFE96D19.toInt(),
    "Red" to 0xFFD84343.toInt(), "Pink" to 0xFFD43C8D.toInt(),
)

fun hueOf(argb: Int): Float = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }[0]

fun seedScheme(seed: Int, dark: Boolean): ColorScheme = dynamicColorScheme(
    seedColor = Color(seed), isDark = dark, isAmoled = false, style = PaletteStyle.TonalSpot)

fun withBackground(scheme: ColorScheme, style: Int, custom: Int, dark: Boolean): ColorScheme {
    if (style !in 1..2) return scheme
    val base = if (style == 2) Color(custom) else lerp(scheme.background, scheme.primary, if (dark) 0.10f else 0.06f)
    val light = base.luminance() > 0.42f
    val ink = if (light) Color(0xFF17171B) else Color(0xFFF4F2F7)
    val raised = if (light) Color.Black else Color.White
    fun elevation(amount: Float) = lerp(base, raised, amount)
    return scheme.copy(
        background = base, surface = base, onBackground = ink, onSurface = ink,
        onSurfaceVariant = ink.copy(alpha = 0.78f),
        surfaceDim = lerp(base, Color.Black, 0.08f), surfaceBright = elevation(0.18f),
        surfaceContainerLowest = base, surfaceContainerLow = elevation(0.025f),
        surfaceContainer = elevation(0.055f), surfaceContainerHigh = elevation(0.085f),
        surfaceContainerHighest = elevation(0.125f),
    )
}
