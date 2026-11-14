package com.example.chat

import com.example.chat.ui.core.theme.ChatyTypographySpec
import com.example.chat.domain.models.UniversalPreferences
import org.junit.Assert.*
import org.junit.Test

class FontWiringRegressionTest {
    @Test fun systemDefaultAppearanceDoesNotMaskFontChoice() {
        assertEquals("ofl-comfortaa", ChatyTypographySpec.resolve("System Default", "Comfortaa").family)
    }
    @Test fun universalWeightsAndItalicMatchTheChosenPreset() {
        assertEquals(300, ChatyTypographySpec.resolve("System Default", "Roboto-Light").weight)
        assertEquals(600, ChatyTypographySpec.resolve("Default", "Roboto-Medium").weight)
        assertTrue(ChatyTypographySpec.resolve("Default", "HappyGiraffe").italic)
        assertEquals(1.5, ChatyTypographySpec.resolve("Default", "BEBASNEUE").letterSpacingDelta, 0.0)
    }
    @Test fun customFontPreferenceRejectsArbitraryPaths() {
        assertNull(UniversalPreferences.fromMap(mapOf("customFontFileName" to "../custom_font_bad.ttf")).customFontFileName)
        val value = UniversalPreferences(customFontFileName = "custom_font_good.ttf", loadFontEnabled = true)
        assertEquals(value, UniversalPreferences.fromMap(value.toMap()))
    }
}
