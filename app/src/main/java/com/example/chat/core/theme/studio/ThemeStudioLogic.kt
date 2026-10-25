package com.example.chat.core.theme.studio

import kotlin.math.sin

/**
 * Context-free port of the state/particle logic in
 * lib/features/settings/theme_studio_screen.dart (api @ 3bee780d):
 * gradient presets, particle init/tick math and the save-decision rules.
 * No android/compose imports so the JVM harness can compile and test it.
 */
object ThemeStudioLogic {

    val PARTICLE_TYPES = listOf("Neon Embers", "Sakura", "Snow", "Cyber Matrix", "Hearts", "None")

    val TRANSITIONS = listOf(
        "Cube 3D" to "Perspective rotation along 3D cube edges",
        "Accordion" to "Fold & unfold accordion sheet depth",
        "Card Flip" to "Full Y-axis 3D spatial flip animation",
        "Zoom Out" to "Cinematic scale-down perspective depth",
        "Shared Axis" to "Smooth Material Motion Z-axis glide",
    )

    data class GradientPreset(val name: String, val start: Long, val end: Long)

    /** Exact _gradientPresets table. */
    val GRADIENT_PRESETS = listOf(
        GradientPreset("Neon Horizon", 0xFF00F2FE, 0xFF4FACFE),
        GradientPreset("Cyber Matrix", 0xFF00F5A0, 0xFF00D9F5),
        GradientPreset("Aurora Violet", 0xFF7F00FF, 0xFFE100FF),
        GradientPreset("Sunset Ember", 0xFFFA709A, 0xFFFEE140),
        GradientPreset("Midnight Obsidian", 0xFF232526, 0xFF414345),
        GradientPreset("Emerald Lux", 0xFF11998E, 0xFF38EF7D),
    )

    /** Exact _getParticleColor switch (Colors.white70 = 0xB3FFFFFF,
     *  default Colors.transparent = 0x00000000). */
    fun particleColor(effect: String, index: Int): Long = when (effect) {
        "Neon Embers" -> if (index % 2 == 0) 0xFFFF5E36 else 0xFFFFB300
        "Sakura" -> if (index % 2 == 0) 0xFFFFB7B2 else 0xFFFF69B4
        "Snow" -> 0xB3FFFFFF
        "Cyber Matrix" -> if (index % 2 == 0) 0xFF00FF66 else 0xFF00E5FF
        "Hearts" -> if (index % 2 == 0) 0xFFFF2D55 else 0xFFFF375F
        else -> 0x00000000
    }

    class Particle(
        var x: Double,
        var y: Double,
        val size: Double,
        val speed: Double,
        val opacity: Double,
        val color: Long,
    )

    /** Exact _initParticles: count = density.toInt(); size 3+r*5;
     *  speed 0.15+r*0.45; opacity 0.2+r*0.7; x/y random in [0,1). */
    fun initParticles(density: Double, effect: String, random: () -> Double): List<Particle> {
        val count = density.toInt()
        val list = ArrayList<Particle>(count)
        for (i in 0 until count) {
            list.add(
                Particle(
                    x = random(),
                    y = random(),
                    size = 3.0 + random() * 5.0,
                    speed = 0.15 + random() * 0.45,
                    opacity = 0.2 + random() * 0.7,
                    color = particleColor(effect, i),
                ),
            )
        }
        return list
    }

    /** Exact _tickParticles: no-op on 'None'; 'Neon Embers'/'Hearts' rise
     *  (y -= speed*0.005*speedMultiplier, wrap below -0.05 to 1.05 with new
     *  x); everything else falls with x wobble sin(y*10)*0.001 (wrap above
     *  1.05 to -0.05 with new x). Mutates [particles] in place. */
    fun tickParticles(
        particles: List<Particle>,
        effect: String,
        speedMultiplier: Double,
        random: () -> Double,
    ) {
        if (effect == "None") return
        val rising = effect == "Neon Embers" || effect == "Hearts"
        for (p in particles) {
            if (rising) {
                p.y -= (p.speed * 0.005) * speedMultiplier
                if (p.y < -0.05) {
                    p.y = 1.05
                    p.x = random()
                }
            } else {
                p.y += (p.speed * 0.005) * speedMultiplier
                p.x += sin(p.y * 10) * 0.001
                if (p.y > 1.05) {
                    p.y = -0.05
                    p.x = random()
                }
            }
        }
    }

    /** Exact _saveAndApplyTheme tick-sync guard: only when a tick was
     *  picked AND the typed conversation tick differs from the theme's. */
    fun tickSyncNeeded(tickChanged: Boolean, conversationTickStyle: String, themeTickStyle: String): Boolean =
        tickChanged && conversationTickStyle != themeTickStyle

    /** Exact effects persistence: enabled unless the effect is 'None'. */
    fun fallingParticlesEnabled(effect: String): Boolean = effect != "None"

    // Slider ranges/divisions from the Dart Slider widgets.
    const val CORNER_RADIUS_MIN = 6.0
    const val CORNER_RADIUS_MAX = 32.0
    const val CORNER_RADIUS_DIVISIONS = 13
    const val FONT_SCALE_MIN = 0.85
    const val FONT_SCALE_MAX = 1.3
    const val FONT_SCALE_DIVISIONS = 9
    const val PARTICLE_SPEED_MIN = 0.5
    const val PARTICLE_SPEED_MAX = 3.0
    const val PARTICLE_SPEED_DIVISIONS = 10
    const val PARTICLE_DENSITY_MIN = 10.0
    const val PARTICLE_DENSITY_MAX = 60.0
    const val PARTICLE_DENSITY_DIVISIONS = 10
}
