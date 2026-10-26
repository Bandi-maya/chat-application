package com.example.chat.ui.core.effects

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Dart ClickParticle (click_particle_overlay.dart). Velocity is in logical px (dp) per second-equivalent. */
class ClickParticle(
    val x: Float, val y: Float, val vx: Float, val vy: Float,
    val symbol: String, val scale: Float, val maxLifetime: Float = 0.6f,
) {
    var age = 0f
    var opacity = 1f
}

object ClickParticleSim {
    /** Per-frame dt used by Dart _tick (fixed 0.016, not wall-clock). */
    const val DT = 0.016f

    /** Dart _spawnParticles: 6 particles, angle 0..2pi, speed (20 + r*40) * multiplier, scale 0.8 + r*0.4. */
    fun spawn(x: Float, y: Float, symbol: String, speedMultiplier: Double, random: Random): List<ClickParticle> =
        List(6) {
            val angle = random.nextDouble() * 2 * Math.PI
            val speed = (20.0 + random.nextDouble() * 40.0) * speedMultiplier
            ClickParticle(
                x, y, (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat(), symbol,
                (0.8 + random.nextDouble() * 0.4).toFloat(),
            )
        }

    /** Dart _tick: age += dt; opacity = clamp(1 - age/maxLifetime); drop when age >= maxLifetime. */
    fun tick(particles: MutableList<ClickParticle>) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += DT
            p.opacity = (1f - p.age / p.maxLifetime).coerceIn(0f, 1f)
            if (p.age >= p.maxLifetime) it.remove()
        }
    }

    /** Dart painter: font 16*scale (1.0 if not positive); offset = position + velocity * (age * 1.5). */
    fun fontSizeDp(p: ClickParticle): Float = (16f * p.scale).let { if (it > 0f) it else 1f }
}

/** Dart FallingItem (falling_particles_overlay.dart): x/y fractions of the canvas. */
class FallingItem(var x: Double, var y: Double, val speed: Double, val size: Double, val opacity: Double, var symbol: String)

object FallingSim {
    fun symbolFor(objectName: String): String = when (objectName) {
        "Hearts" -> "\u2764\uFE0F"
        "Snowflakes" -> "\u2744\uFE0F"
        "Leaves" -> "\uD83C\uDF42"
        else -> "\u2B50" // 'Stars' and default
    }

    /** Dart build() `active`. */
    fun active(enabled: Boolean, scope: String, currentScope: String): Boolean =
        enabled && (scope == "Both" || (scope == "Home only" && currentScope == "Home") || (scope == "Chat only" && currentScope == "Chat"))

    /**
     * Dart _updateParticles, called once per animation tick. Returns true when state changed (repaint).
     * Scope mismatch returns early without touching items (they persist, just not painted).
     */
    fun step(
        items: MutableList<FallingItem>, enabled: Boolean, scope: String, objectName: String,
        currentScope: String, random: Random,
    ): Boolean {
        if (!enabled) {
            if (items.isNotEmpty()) { items.clear(); return true }
            return false
        }
        if (scope == "Home only" && currentScope != "Home") return false
        if (scope == "Chat only" && currentScope != "Chat") return false
        val symbol = symbolFor(objectName)
        if (items.size < 15) {
            items.add(
                FallingItem(
                    x = random.nextDouble(), y = -0.05, speed = 0.002 + random.nextDouble() * 0.003,
                    size = 12.0 + random.nextDouble() * 10.0, opacity = 0.3 + random.nextDouble() * 0.5, symbol = symbol,
                ),
            )
        }
        for (item in items) {
            item.y += item.speed
            item.symbol = symbol
            if (item.y > 1.05) { item.y = -0.05; item.x = random.nextDouble() }
        }
        return true
    }

    /** Dart painter font: size > 0 ? size : 14. */
    fun fontSizeDp(item: FallingItem): Double = if (item.size > 0) item.size else 14.0
}
