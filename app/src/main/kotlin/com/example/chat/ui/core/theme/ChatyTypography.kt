package com.example.chat.ui.core.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import com.example.chat.core.templates.AppearanceVariantController

/**
 * Port of main.dart _applyTypographyStyle for Compose Typography. Families are platform generics resolved by name
 * (Flutter fontFamily 'sans-serif-rounded' etc. resolve the same Android system families), so no assets are needed.
 * Dart also applies the change to primaryTextTheme; Compose Material3 has no separate primary text theme.
 */
object ChatyTypography {
    @OptIn(ExperimentalTextApi::class)
    private fun deviceFamily(name: String): FontFamily = FontFamily(
        listOf(100, 200, 300, 400, 500, 600, 700, 800, 900).flatMap { w ->
            listOf(
                Font(DeviceFontFamilyName(name), FontWeight(w), FontStyle.Normal),
                Font(DeviceFontFamilyName(name), FontWeight(w), FontStyle.Italic),
            )
        },
    )

    private fun familyFor(name: String?): FontFamily? = when (name) {
        null -> null
        "serif" -> FontFamily.Serif
        "sans-serif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> deviceFamily(name) // sans-serif-rounded, sans-serif-condensed
    }

    private fun TextStyle.styled(family: FontFamily?, delta: Double): TextStyle {
        val base = if (letterSpacing.type == TextUnitType.Sp) letterSpacing.value else 0f
        return copy(
            fontFamily = family ?: fontFamily,
            letterSpacing = (base + delta.toFloat()).sp,
        )
    }

    fun apply(base: Typography, preset: String, universalPreset: String?): Typography {
        val spec = ChatyTypographySpec.resolve(preset, universalPreset)
        val fam = familyFor(spec.family)
        val d = spec.letterSpacingDelta
        var t = Typography(
            displayLarge = base.displayLarge.styled(fam, d), displayMedium = base.displayMedium.styled(fam, d),
            displaySmall = base.displaySmall.styled(fam, d),
            headlineLarge = base.headlineLarge.styled(fam, d), headlineMedium = base.headlineMedium.styled(fam, d),
            headlineSmall = base.headlineSmall.styled(fam, d),
            titleLarge = base.titleLarge.styled(fam, d), titleMedium = base.titleMedium.styled(fam, d),
            titleSmall = base.titleSmall.styled(fam, d),
            bodyLarge = base.bodyLarge.styled(fam, d), bodyMedium = base.bodyMedium.styled(fam, d),
            bodySmall = base.bodySmall.styled(fam, d),
            labelLarge = base.labelLarge.styled(fam, d), labelMedium = base.labelMedium.styled(fam, d),
            labelSmall = base.labelSmall.styled(fam, d),
        )
        if (spec.monospaceAccent) {
            t = t.copy(
                labelSmall = t.labelSmall.copy(fontFamily = FontFamily.Monospace),
                labelMedium = t.labelMedium.copy(fontFamily = FontFamily.Monospace),
                labelLarge = t.labelLarge.copy(fontFamily = FontFamily.Monospace),
            )
        }
        return t
    }
}

/**
 * main.dart MaterialApp.builder text scale: appearance presets may enlarge system text but never shrink an explicitly
 * enlarged OS font scale (AppearanceVariantController.resolveTextScale). Provide at the app root above content.
 * Note: replaces the density's fontScale with a linear scale (drops Android 14 non-linear font scaling), as Flutter's
 * TextScaler.linear does.
 */
@Composable
fun ChatyTextScaleProvider(appearanceTextScale: Double, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val effective = AppearanceVariantController.resolveTextScale(d.fontScale.toDouble(), appearanceTextScale)
    CompositionLocalProvider(LocalDensity provides Density(d.density, effective.toFloat())) { content() }
}
