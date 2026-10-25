package com.example.chat

import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.DeliveryIconStyle
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.bubbleStyleIdFromString
import com.example.chat.core.theme.deliveryIconStyleFromString
import com.example.chat.core.theme.gbBubbleVectorInterpretations
import com.example.chat.core.theme.gbTickVectorInterpretations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigParityTest {

    private fun base() = ThemeConfig.fromMap(emptyMap())

    @Test
    fun fromMapEmptyGivesMonochromeDarkDefaults() {
        val c = base()
        assertEquals("monochromeDark", c.id)
        assertEquals("Monochrome Dark", c.name)
        assertTrue(c.isDark)
        assertEquals(0xFFFFFFFFL, c.accentColor)
        assertEquals(0xFF000000L, c.backgroundColor)
        assertEquals(0xFF121212L, c.surfaceColor)
        assertEquals(0xFF1C1C1EL, c.cardColor)
        assertEquals(0xFF27272AL, c.outgoingBubbleColor)
        assertEquals(0xFF18181BL, c.incomingBubbleColor)
        assertEquals(0xFF60A5FAL, c.linkColor)
        assertEquals(0xFFEF4444L, c.dangerColor)
        assertEquals(0xFF22C55EL, c.successColor)
        assertEquals(16.0, c.cornerRadius, 0.0)
        assertEquals(1.0, c.density, 0.0)
        assertEquals(1.0, c.fontScale, 0.0)
        assertEquals(AppNavigationMode.bottomNav, c.navigationMode)
        assertEquals(BubbleStyleId.stock, c.bubbleStyle)
        // Dart: tickStyleRaw is null -> fromString(null) returns stock, not the field default
        assertEquals(DeliveryIconStyle.stock, c.deliveryTickStyle)
        assertEquals("RC iOS 11", c.tickStyle)
        assertEquals("subtle_dots", c.wallpaperId)
        assertFalse(c.highContrast)
    }

    @Test
    fun toMapFromMapRoundTrips() {
        val c = base().copyWith(
            id = "custom", name = "Custom", isDark = false,
            accentColor = 0xFF00FF00L, layoutMode = com.example.chat.core.theme.UILayoutMode.focus,
            bubbleStyle = BubbleStyleId.oldHangouts,
            deliveryTickStyle = DeliveryIconStyle.hearts,
            animationLevel = 0.4, cornerRadius = 8.0, highContrast = true,
        )
        val re = ThemeConfig.fromMap(c.toMap())
        // Dart quirk: toMap writes deliveryTickStyle.name; fromMap keeps that raw
        // string as tickStyle, so the roundtrip rewrites tickStyle verbatim.
        assertEquals(c.copyWith(tickStyle = "hearts"), re)
        assertEquals("light", c.toMap()["brightness"])
    }

    @Test
    fun toggleBrightnessDarkToLightAdaptsColors() {
        val light = base().toggleBrightness()
        assertFalse(light.isDark)
        assertEquals(0xFFFFFFFFL, light.backgroundColor)
        assertEquals(0xFF09090BL, light.primaryTextColor)
        // white accent (luminance 1.0 > 0.85) adapts to near-black
        assertEquals(0xFF09090BL, light.accentColor)
        assertEquals(0xFF09090BL, light.outgoingBubbleColor)
        assertEquals(0xFFFFFFFFL, light.outgoingTextColor)
        // toggling back restores the dark family
        val dark = light.toggleBrightness()
        assertTrue(dark.isDark)
        assertEquals(0xFF000000L, dark.backgroundColor)
    }

    @Test
    fun toggleBrightnessLightDarkAccentAdapts() {
        // dark accent in light mode: luminance < 0.15 -> becomes white in dark
        val c = base().copyWith(isDark = false, accentColor = 0xFF101010L)
        val dark = c.toggleBrightness()
        assertTrue(dark.isDark)
        assertEquals(0xFFFFFFFFL, dark.accentColor)
    }

    @Test
    fun bubbleMaxWidthFactorPerLayout() {
        fun w(m: com.example.chat.core.theme.UILayoutMode) = base().copyWith(layoutMode = m).bubbleMaxWidthFactor
        assertEquals(0.80, w(com.example.chat.core.theme.UILayoutMode.classic), 0.0)
        assertEquals(0.72, w(com.example.chat.core.theme.UILayoutMode.compact), 0.0)
        assertEquals(0.86, w(com.example.chat.core.theme.UILayoutMode.expressive), 0.0)
        assertEquals(0.66, w(com.example.chat.core.theme.UILayoutMode.focus), 0.0)
        assertEquals(0.80, w(com.example.chat.core.theme.UILayoutMode.tabletDesktop), 0.0)
    }

    @Test
    fun bubbleStyleFromStringLegacyAliases() {
        assertEquals(BubbleStyleId.ios, bubbleStyleIdFromString("iosflat"))
        assertEquals(BubbleStyleId.telegram, bubbleStyleIdFromString("TelegramArc"))
        assertEquals(BubbleStyleId.threeD, bubbleStyleIdFromString("3dshadow"))
        assertEquals(BubbleStyleId.gabiOutline, bubbleStyleIdFromString("minimalborder"))
        assertEquals(BubbleStyleId.oldHangouts, bubbleStyleIdFromString("retrobubble"))
        assertEquals(BubbleStyleId.oldHangouts, bubbleStyleIdFromString("sharptail"))
        assertEquals(BubbleStyleId.foldV2, bubbleStyleIdFromString("futuristicclip"))
        assertEquals(BubbleStyleId.materialized, bubbleStyleIdFromString("card"))
        assertEquals(BubbleStyleId.materialized, bubbleStyleIdFromString("minimal"))
        assertEquals(BubbleStyleId.stock, bubbleStyleIdFromString(null))
        assertEquals(BubbleStyleId.stock, bubbleStyleIdFromString(""))
        assertEquals(BubbleStyleId.stock, bubbleStyleIdFromString("nonsense-xyz"))
        // direct enum name with punctuation normalized away
        assertEquals(BubbleStyleId.oldHangouts, bubbleStyleIdFromString("Old Hangouts"))
    }

    @Test
    fun deliveryIconFromStringLegacyAliasesAndQuirks() {
        assertEquals(DeliveryIconStyle.stock, deliveryIconStyleFromString(null))
        assertEquals(DeliveryIconStyle.rcIos11, deliveryIconStyleFromString("default"))
        // gbIos displayName is 'iOS' but is skipped in the displayName loop,
        // so 'ios' falls to the legacy switch -> rcIos11
        assertEquals(DeliveryIconStyle.rcIos11, deliveryIconStyleFromString("iOS"))
        assertEquals(DeliveryIconStyle.ios2, deliveryIconStyleFromString("iosticks"))
        assertEquals(DeliveryIconStyle.cirCheck, deliveryIconStyleFromString("circles"))
        assertEquals(DeliveryIconStyle.greenTick, deliveryIconStyleFromString("doublecheck"))
        assertEquals(DeliveryIconStyle.bwTicks, deliveryIconStyleFromString("minimal"))
        assertEquals(DeliveryIconStyle.vantCircle, deliveryIconStyleFromString("neon"))
        assertEquals(DeliveryIconStyle.rcIos11, deliveryIconStyleFromString("nonsense-xyz"))
        // gbIos / gbCircles still match by enum name
        assertEquals(DeliveryIconStyle.gbIos, deliveryIconStyleFromString("gbios"))
        assertEquals(DeliveryIconStyle.gbCircles, deliveryIconStyleFromString("gbcircles"))
        // displayName matching works for non-skipped styles
        assertEquals(DeliveryIconStyle.hearts, deliveryIconStyleFromString("Hearts"))
        assertEquals(DeliveryIconStyle.material3Rounded, deliveryIconStyleFromString("Material 3 Rounded"))
    }

    @Test
    fun gbVectorInterpretationSetsContainExpectedMembers() {
        assertTrue(gbBubbleVectorInterpretations.contains(BubbleStyleId.cvIos))
        assertTrue(gbTickVectorInterpretations.contains(DeliveryIconStyle.bbm))
        assertTrue(gbTickVectorInterpretations.contains(DeliveryIconStyle.flower))
        assertFalse(gbTickVectorInterpretations.contains(DeliveryIconStyle.stock))
        assertFalse(gbTickVectorInterpretations.contains(DeliveryIconStyle.hearts))
        assertFalse(gbBubbleVectorInterpretations.contains(BubbleStyleId.stock))
        assertEquals(59, BubbleStyleId.entries.size)
        assertEquals(95, DeliveryIconStyle.entries.size)
        assertEquals(70, gbTickVectorInterpretations.size)
        assertEquals(11, gbBubbleVectorInterpretations.size)
    }

    @Test
    fun fontScaleClampedInFromMapAndCopyWith() {
        assertEquals(0.5, ThemeConfig.fromMap(mapOf("fontScale" to 0.1)).fontScale, 0.0)
        assertEquals(2.0, ThemeConfig.fromMap(mapOf("fontScale" to 9.0)).fontScale, 0.0)
        assertEquals(0.5, base().copyWith(fontScale = 0.2).fontScale, 0.0)
        // Dart copyWith guard: non-positive fontScale keeps the current value
        assertEquals(1.0, base().copyWith(fontScale = -3.0).fontScale, 0.0)
    }

    @Test
    fun contrastIssueChecksAllFivePairs() {
        assertFalse(base().hasContrastIssue)
        // invisible secondary text triggers it
        val bad = base().copyWith(secondaryTextColor = 0xFF000000L)
        assertTrue(bad.hasContrastIssue)
        // low-contrast outgoing bubble pair triggers it
        val bad2 = base().copyWith(outgoingBubbleColor = 0xFFFFFFFFL, outgoingTextColor = 0xFFF0F0F0L)
        assertTrue(bad2.hasContrastIssue)
    }

    @Test
    fun corruptValuesFallBackToDefaults() {
        val c = ThemeConfig.fromMap(mapOf(
            "accentColor" to "not-a-color",
            "cornerRadius" to mapOf("x" to 1),
            "navigationMode" to "sideCar",
            "brightness" to "light",
            "accentColor2" to 5,
        ))
        assertEquals(0xFFFFFFFFL, c.accentColor)
        assertEquals(16.0, c.cornerRadius, 0.0)
        assertEquals(AppNavigationMode.bottomNav, c.navigationMode)
        assertFalse(c.isDark)
        // string-encoded numbers parse (Dart int.tryParse / double.tryParse)
        val s = ThemeConfig.fromMap(mapOf("accentColor" to "4294967295", "density" to "1.5"))
        assertEquals(4294967295L, s.accentColor)
        assertEquals(1.5, s.density, 0.0)
    }
}
