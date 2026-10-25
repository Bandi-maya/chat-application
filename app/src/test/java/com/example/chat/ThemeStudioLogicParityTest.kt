package com.example.chat

import com.example.chat.core.theme.studio.ThemeStudioLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior test of theme_studio_screen.dart's state logic (api @ 3bee780d). */
class ThemeStudioLogicParityTest {

    @Test
    fun gradientPresetsMatchDartTable() {
        val p = ThemeStudioLogic.GRADIENT_PRESETS
        assertEquals(6, p.size)
        assertEquals("Neon Horizon", p[0].name)
        assertEquals(0xFF00F2FE, p[0].start)
        assertEquals(0xFF4FACFE, p[0].end)
        assertEquals("Emerald Lux", p[5].name)
        assertEquals(0xFF11998E, p[5].start)
        assertEquals(0xFF38EF7D, p[5].end)
    }

    @Test
    fun particleColorsAlternatePerEffect() {
        assertEquals(0xFFFF5E36, ThemeStudioLogic.particleColor("Neon Embers", 0))
        assertEquals(0xFFFFB300, ThemeStudioLogic.particleColor("Neon Embers", 1))
        assertEquals(0xB3FFFFFF, ThemeStudioLogic.particleColor("Snow", 7))
        assertEquals(0xFF00FF66, ThemeStudioLogic.particleColor("Cyber Matrix", 2))
        assertEquals(0xFFFF2D55, ThemeStudioLogic.particleColor("Hearts", 0))
        assertEquals(0x00000000, ThemeStudioLogic.particleColor("None", 0))
        assertEquals(0x00000000, ThemeStudioLogic.particleColor("Unknown", 3))
    }

    @Test
    fun initParticlesUsesDensityCountAndRanges() {
        var i = 0
        val seq = doubleArrayOf(0.0, 0.5, 1.0, 0.25, 0.75)
        val random: () -> Double = { seq[i++ % seq.size] }
        val particles = ThemeStudioLogic.initParticles(12.7, "Neon Embers", random)
        assertEquals(12, particles.size)
        for ((index, p) in particles.withIndex()) {
            assertTrue(p.size >= 3.0 && p.size <= 8.0)
            assertTrue(p.speed >= 0.15 && p.speed <= 0.6)
            assertTrue(p.opacity >= 0.2 && p.opacity <= 0.9)
            assertEquals(ThemeStudioLogic.particleColor("Neon Embers", index), p.color)
        }
    }

    @Test
    fun risingEffectsWrapBelowTop() {
        val p = ThemeStudioLogic.Particle(0.5, -0.049, 4.0, 1.0, 0.5, 0xFFFF5E36)
        ThemeStudioLogic.tickParticles(listOf(p), "Neon Embers", 1.0) { 0.33 }
        // y = -0.049 - 0.005 = -0.054 < -0.05 -> wraps to 1.05 with new x.
        assertEquals(1.05, p.y, 1e-9)
        assertEquals(0.33, p.x, 1e-9)
    }

    @Test
    fun fallingEffectsWobbleAndWrapBelowBottom() {
        val p = ThemeStudioLogic.Particle(0.5, 1.049, 4.0, 1.0, 0.5, 0xB3FFFFFF)
        ThemeStudioLogic.tickParticles(listOf(p), "Snow", 1.0) { 0.66 }
        // y = 1.049 + 0.005 = 1.054 > 1.05 -> wraps to -0.05 with new x.
        assertEquals(-0.05, p.y, 1e-9)
        assertEquals(0.66, p.x, 1e-9)
    }

    @Test
    fun fallingXWobbleAppliedBeforeWrapCheck() {
        val p = ThemeStudioLogic.Particle(0.5, 0.5, 4.0, 1.0, 0.5, 0xB3FFFFFF)
        ThemeStudioLogic.tickParticles(listOf(p), "Snow", 1.0) { 0.0 }
        val expectedY = 0.505
        val expectedX = 0.5 + kotlin.math.sin(expectedY * 10) * 0.001
        assertEquals(expectedY, p.y, 1e-9)
        assertEquals(expectedX, p.x, 1e-9)
    }

    @Test
    fun noneEffectDoesNotMoveParticles() {
        val p = ThemeStudioLogic.Particle(0.5, 0.5, 4.0, 1.0, 0.5, 0L)
        ThemeStudioLogic.tickParticles(listOf(p), "None", 3.0) { 0.9 }
        assertEquals(0.5, p.y, 1e-9)
        assertEquals(0.5, p.x, 1e-9)
    }

    @Test
    fun tickSyncGuardMatchesDart() {
        assertFalse(ThemeStudioLogic.tickSyncNeeded(false, "Stock", "RC iOS 11"))
        assertFalse(ThemeStudioLogic.tickSyncNeeded(true, "Wings", "Wings"))
        assertTrue(ThemeStudioLogic.tickSyncNeeded(true, "Stock", "Wings"))
    }

    @Test
    fun fallingParticlesEnabledUnlessNone() {
        assertTrue(ThemeStudioLogic.fallingParticlesEnabled("Sakura"))
        assertFalse(ThemeStudioLogic.fallingParticlesEnabled("None"))
    }
}
