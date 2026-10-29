package com.example.chat.core.theme.studio

/**
 * Context-free settings rules used by the native Theme Studio.
 * Keep particle choices aligned with FallingSim.symbolFor; unsupported legacy
 * values normalize to Stars instead of silently showing the wrong effect.
 */
object ThemeStudioLogic {
    val PARTICLE_TYPES = listOf("Stars", "Hearts", "Snowflakes", "Leaves")

    /** Must remain aligned with MainNavigationShell.pagerTransition. */
    val TRANSITION_TYPES = listOf(
        "Default", "Cube 3D", "Accordion Fold", "Card Flip", "Depth Zoom", "Stack Rotate",
        "Accordion", "Background To Foreground", "Cube In", "Depth Page", "Draw From Back",
        "Flip Horizontal", "Flip Vertical", "Foreground To Background", "Tablet",
        "Rotate Down", "Rotate Up", "Stack", "Zoom Out Slide",
    )

    data class GradientPreset(val name: String, val start: Long, val end: Long)

    val GRADIENT_PRESETS = listOf(
        GradientPreset("Neon Horizon", 0xFF00F2FE, 0xFF4FACFE),
        GradientPreset("Cyber Matrix", 0xFF00F5A0, 0xFF00D9F5),
        GradientPreset("Aurora Violet", 0xFF7F00FF, 0xFFE100FF),
        GradientPreset("Sunset Ember", 0xFFFA709A, 0xFFFEE140),
        GradientPreset("Midnight Obsidian", 0xFF232526, 0xFF414345),
        GradientPreset("Emerald Lux", 0xFF11998E, 0xFF38EF7D),
    )

    fun normalizeParticleType(value: String?): String =
        value?.takeIf { it in PARTICLE_TYPES } ?: "Stars"

    fun tickSyncNeeded(
        tickChanged: Boolean,
        conversationTickStyle: String,
        themeTickStyle: String,
    ): Boolean = tickChanged && conversationTickStyle != themeTickStyle

    const val CORNER_RADIUS_MIN = 6.0
    const val CORNER_RADIUS_MAX = 32.0
    const val CORNER_RADIUS_DIVISIONS = 13
    const val FONT_SCALE_MIN = 0.85
    const val FONT_SCALE_MAX = 1.3
    const val FONT_SCALE_DIVISIONS = 9
}
