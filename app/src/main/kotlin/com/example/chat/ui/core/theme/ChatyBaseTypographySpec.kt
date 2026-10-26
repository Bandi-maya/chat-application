package com.example.chat.ui.core.theme

/**
 * Flutter base text theme of the Dart app, as numbers (JVM-testable).
 * = Typography.englishLike2021 (flutter 3.44.0 material/typography.dart, Android/Roboto) merged with the 8 overrides in
 * theme_config.dart toThemeData() textTheme. Flutter `height` is a multiplier of the font size.
 */
data class ChatyTextStyleSpec(
    val name: String, val fontSize: Double, val weight: Int, val letterSpacing: Double, val heightMultiplier: Double,
)

object ChatyBaseTypographySpec {
    /** englishLike2021 values (size, weight, letterSpacing, height). Verified from typography.dart lines 2096-2111. */
    private val defaults = listOf(
        ChatyTextStyleSpec("displayLarge", 57.0, 400, -0.25, 1.12),
        ChatyTextStyleSpec("displayMedium", 45.0, 400, 0.0, 1.16),
        ChatyTextStyleSpec("displaySmall", 36.0, 400, 0.0, 1.22),
        ChatyTextStyleSpec("headlineLarge", 32.0, 400, 0.0, 1.25),
        ChatyTextStyleSpec("headlineMedium", 28.0, 400, 0.0, 1.29),
        ChatyTextStyleSpec("headlineSmall", 24.0, 400, 0.0, 1.33),
        ChatyTextStyleSpec("titleLarge", 22.0, 400, 0.0, 1.27),
        ChatyTextStyleSpec("titleMedium", 16.0, 500, 0.15, 1.50),
        ChatyTextStyleSpec("titleSmall", 14.0, 500, 0.1, 1.43),
        ChatyTextStyleSpec("labelLarge", 14.0, 500, 0.1, 1.43),
        ChatyTextStyleSpec("labelMedium", 12.0, 500, 0.5, 1.33),
        ChatyTextStyleSpec("labelSmall", 11.0, 500, 0.5, 1.45),
        ChatyTextStyleSpec("bodyLarge", 16.0, 400, 0.5, 1.50),
        ChatyTextStyleSpec("bodyMedium", 14.0, 400, 0.25, 1.43),
        ChatyTextStyleSpec("bodySmall", 12.0, 400, 0.4, 1.33),
    )

    /** theme_config.dart overrides (size is multiplied by ThemeConfig.fontScale; color is applied separately, see ChatyTypography). */
    fun resolve(fontScale: Double): Map<String, ChatyTextStyleSpec> {
        fun d(n: String) = defaults.first { it.name == n }
        val o = mapOf(
            "headlineLarge" to d("headlineLarge").copy(fontSize = 28 * fontScale, weight = 700, letterSpacing = -0.5),
            "headlineMedium" to d("headlineMedium").copy(fontSize = 22 * fontScale, weight = 700, letterSpacing = -0.3),
            "titleLarge" to d("titleLarge").copy(fontSize = 18 * fontScale, weight = 600),
            "titleMedium" to d("titleMedium").copy(fontSize = 15 * fontScale, weight = 600),
            "bodyLarge" to d("bodyLarge").copy(fontSize = 15 * fontScale, heightMultiplier = 1.35),
            "bodyMedium" to d("bodyMedium").copy(fontSize = 13.5 * fontScale, heightMultiplier = 1.35),
            "labelLarge" to d("labelLarge").copy(fontSize = 13.5 * fontScale, weight = 600),
            "labelSmall" to d("labelSmall").copy(fontSize = 11 * fontScale),
        )
        return defaults.associate { it.name to (o[it.name] ?: it) }
    }
}
