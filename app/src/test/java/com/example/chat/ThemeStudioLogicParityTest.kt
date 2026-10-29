package com.example.chat

import com.example.chat.core.theme.studio.ThemeStudioLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStudioLogicParityTest {
    @Test
    fun gradientPresetsRemainStable() {
        val presets = ThemeStudioLogic.GRADIENT_PRESETS
        assertEquals(6, presets.size)
        assertEquals("Neon Horizon", presets.first().name)
        assertEquals(0xFF00F2FE, presets.first().start)
        assertEquals(0xFF4FACFE, presets.first().end)
        assertEquals("Emerald Lux", presets.last().name)
        assertEquals(0xFF11998E, presets.last().start)
        assertEquals(0xFF38EF7D, presets.last().end)
    }

    @Test
    fun particleChoicesMatchRuntimeSupportedValues() {
        assertEquals(listOf("Stars", "Hearts", "Snowflakes", "Leaves"), ThemeStudioLogic.PARTICLE_TYPES)
        for (supported in ThemeStudioLogic.PARTICLE_TYPES) {
            assertEquals(supported, ThemeStudioLogic.normalizeParticleType(supported))
        }
    }

    @Test
    fun unsupportedLegacyParticleValuesFallBackSafely() {
        for (legacy in listOf("Neon Embers", "Sakura", "Snow", "Cyber Matrix", "None", null)) {
            assertEquals("Stars", ThemeStudioLogic.normalizeParticleType(legacy))
        }
    }

    @Test
    fun tickSyncGuardOnlySyncsChangedSelectedStyle() {
        assertFalse(ThemeStudioLogic.tickSyncNeeded(false, "Stock", "RC iOS 11"))
        assertFalse(ThemeStudioLogic.tickSyncNeeded(true, "Wings", "Wings"))
        assertTrue(ThemeStudioLogic.tickSyncNeeded(true, "Stock", "Wings"))
    }

    @Test
    fun themeSliderBoundsStayValid() {
        assertTrue(ThemeStudioLogic.CORNER_RADIUS_MIN < ThemeStudioLogic.CORNER_RADIUS_MAX)
        assertTrue(ThemeStudioLogic.FONT_SCALE_MIN < ThemeStudioLogic.FONT_SCALE_MAX)
    }
}
