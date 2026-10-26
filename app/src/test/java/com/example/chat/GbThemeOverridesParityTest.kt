package com.example.chat

import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.ThemePresets
import com.example.chat.core.theme.bubbleStyleIdFromString
import com.example.chat.core.theme.deliveryIconStyleFromString
import com.example.chat.domain.models.ConversationPreferences
import com.example.chat.domain.models.UniversalPreferences
import com.example.chat.ui.theme.GbThemeOverrides
import com.example.chat.ui.theme.GbThemeSource
import org.junit.Assert.*
import org.junit.Test

/** Expectations derived from lib/ui/core/gb/gb_theme_overrides.dart (api 3bee780d) by reading the source. */
class GbThemeOverridesParityTest {
    private class Src(
        val colors: Map<String, Long> = emptyMap(),
        val doubles: Map<String, Double> = emptyMap(),
        val strings: Map<String, String> = emptyMap(),
        override val universal: UniversalPreferences = UniversalPreferences(),
        override val conversation: ConversationPreferences = ConversationPreferences(),
    ) : GbThemeSource {
        override fun gbColor(key: String) = colors[key]
        override fun gbDouble(key: String, fallback: Double) = doubles[key] ?: fallback
        override fun gbString(key: String, fallback: String) = strings[key] ?: fallback
    }

    private val dark = ThemePresets.getSystemDefaultTheme(false)
    private val light = ThemePresets.getSystemDefaultTheme(true)

    @Test fun noOverridesKeepsBaseColors() {
        val r = GbThemeOverrides.resolve(dark, Src())
        assertEquals(dark.accentColor, r.accentColor); assertEquals(dark.backgroundColor, r.backgroundColor)
        assertEquals(dark.surfaceColor, r.surfaceColor); assertEquals(dark.cardColor, r.cardColor)
        assertEquals(dark.primaryTextColor, r.primaryTextColor); assertEquals(dark.fontScale, r.fontScale, 0.0)
        assertEquals(deliveryIconStyleFromString(ConversationPreferences().tickStyle), r.deliveryTickStyle)
        assertEquals(ConversationPreferences().tickStyle, r.tickStyle)
        assertEquals(bubbleStyleIdFromString(ConversationPreferences().bubbleStyle.trim()), r.bubbleStyle)
    }

    @Test fun accentPrecedenceAndDarkOnlyKey() {
        val gb = mapOf("ModDarkConPickColor" to 0xFF111111L, "ModConPickColor" to 0xFF222222L, "ModConColor" to 0xFF333333L, "tabindicator" to 0xFF444444L)
        assertEquals(0xFF111111L, GbThemeOverrides.resolve(dark, Src(colors = gb)).accentColor)
        assertEquals(0xFF222222L, GbThemeOverrides.resolve(light, Src(colors = gb)).accentColor)
        assertEquals(0xFF333333L, GbThemeOverrides.resolve(light, Src(colors = gb - "ModConPickColor")).accentColor)
        assertEquals(0xFF444444L, GbThemeOverrides.resolve(light, Src(colors = mapOf("tabindicator" to 0xFF444444L))).accentColor)
        // universal color beats every GB key
        assertEquals(0xFF010203L, GbThemeOverrides.resolve(dark, Src(colors = gb, universal = UniversalPreferences(universalColor = 0xFF010203L))).accentColor)
    }

    @Test fun backgroundPrecedenceConvThenUniversalThenGb() {
        val gb = mapOf("ModDarkConPickColorNav" to 0xFF0A0A0AL, "ModConBackColor" to 0xFF0B0B0BL, "list_bg_color" to 0xFF0C0C0CL, "ConvoBack" to 0xFF0D0D0DL)
        assertEquals(0xFF0A0A0AL, GbThemeOverrides.resolve(dark, Src(colors = gb)).backgroundColor)
        assertEquals(0xFF0B0B0BL, GbThemeOverrides.resolve(light, Src(colors = gb)).backgroundColor)
        assertEquals(0xFF0D0D0DL, GbThemeOverrides.resolve(light, Src(colors = mapOf("ConvoBack" to 0xFF0D0D0DL))).backgroundColor)
        val u = UniversalPreferences(backgroundColor = 0xFF121212L)
        assertEquals(0xFF121212L, GbThemeOverrides.resolve(dark, Src(colors = gb, universal = u)).backgroundColor)
        assertEquals(0xFF131313L, GbThemeOverrides.resolve(dark, Src(colors = gb, universal = u, conversation = ConversationPreferences(convBackgroundColor = 0xFF131313L))).backgroundColor)
    }

    @Test fun surfaceAndCardBlend() {
        // dark: white @ round(0.045*255)=11 over surface 0xFF202020 -> (11*255+244*32)/255 = 41 = 0x29
        val rd = GbThemeOverrides.resolve(dark, Src(colors = mapOf("ModChatColor" to 0xFF202020L)))
        assertEquals(0xFF202020L, rd.surfaceColor); assertEquals(0xFF292929L, rd.cardColor)
        // BGColor is the second key; ModChatColor wins when both exist
        assertEquals(0xFF202020L, GbThemeOverrides.resolve(dark, Src(colors = mapOf("BGColor" to 0xFF303030L, "ModChatColor" to 0xFF202020L))).surfaceColor)
        assertEquals(0xFF303030L, GbThemeOverrides.resolve(dark, Src(colors = mapOf("BGColor" to 0xFF303030L))).surfaceColor)
        // light: black @ round(0.025*255)=6 over 0xFFF0F0F0 -> (6*0+249*240)/255 = 234 = 0xEA
        val rl = GbThemeOverrides.resolve(light, Src(colors = mapOf("ModChatColor" to 0xFFF0F0F0L)))
        assertEquals(0xFFEAEAEAL, rl.cardColor)
    }

    @Test fun bubbleAndTextColorPrecedence() {
        val gb = mapOf("ModChatRightBubble" to 0xFF111111L, "ModChatLeftBubble" to 0xFF222222L, "ModChatBubbleRightColor" to 0xFFFFFFFFL,
            "ModChatBubbleLeftColor" to 0xFFEEEEEEL, "ModChatLinkColor" to 0xFF8888FFL)
        val r = GbThemeOverrides.resolve(dark, Src(colors = gb))
        assertEquals(0xFF111111L, r.outgoingBubbleColor); assertEquals(0xFF222222L, r.incomingBubbleColor)
        assertEquals(0xFFFFFFFFL, r.outgoingTextColor); assertEquals(0xFFEEEEEEL, r.incomingTextColor); assertEquals(0xFF8888FFL, r.linkColor)
        val c = ConversationPreferences(rightBubbleColor = 0xFF1A1A1AL, leftBubbleColor = 0xFF2A2A2AL, rightChatBubbleTextColor = 0xFFFAFAFAL,
            leftChatBubbleTextColor = 0xFFF0F0F0L, hyperlinksColor = 0xFF7777FFL)
        val r2 = GbThemeOverrides.resolve(dark, Src(colors = gb, conversation = c))
        assertEquals(0xFF1A1A1AL, r2.outgoingBubbleColor); assertEquals(0xFF2A2A2AL, r2.incomingBubbleColor)
        assertEquals(0xFFFAFAFAL, r2.outgoingTextColor); assertEquals(0xFFF0F0F0L, r2.incomingTextColor); assertEquals(0xFF7777FFL, r2.linkColor)
    }

    @Test fun fontScaleGuardAndClamp() {
        assertEquals(2.0, GbThemeOverrides.resolve(dark, Src(doubles = mapOf("font_scale" to 5.0))).fontScale, 0.0)
        assertEquals(0.5, GbThemeOverrides.resolve(dark, Src(doubles = mapOf("font_scale" to 0.1))).fontScale, 0.0)
        assertEquals(1.25, GbThemeOverrides.resolve(dark, Src(doubles = mapOf("font_scale" to 1.25))).fontScale, 0.0)
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals("bad=$bad", dark.fontScale, GbThemeOverrides.resolve(dark, Src(doubles = mapOf("font_scale" to bad))).fontScale, 0.0)
        }
    }

    @Test fun bubbleStyleTypedBeatsLegacyThenBaseFallback() {
        val kitty = bubbleStyleIdFromString("Kitty"); val amor = bubbleStyleIdFromString("Amor")
        assertNotEquals(kitty, amor)
        val typed = ConversationPreferences(bubbleStyle = "  Kitty  ")
        assertEquals(kitty, GbThemeOverrides.resolve(dark, Src(strings = mapOf("bubble_style" to "Amor"), conversation = typed)).bubbleStyle)
        assertEquals(amor, GbThemeOverrides.resolve(dark, Src(strings = mapOf("bubble_style" to "Amor"), conversation = ConversationPreferences(bubbleStyle = ""))).bubbleStyle)
        val b = dark.copyWith(bubbleStyle = BubbleStyleId.kitty)
        assertEquals(BubbleStyleId.kitty, GbThemeOverrides.resolve(b, Src(conversation = ConversationPreferences(bubbleStyle = "   "))).bubbleStyle)
    }

    @Test fun tickStyleMapsFromConversation() {
        val c = ConversationPreferences(tickStyle = "RC iOS 11")
        val r = GbThemeOverrides.resolve(dark, Src(conversation = c))
        assertEquals(deliveryIconStyleFromString("RC iOS 11"), r.deliveryTickStyle); assertEquals("RC iOS 11", r.tickStyle)
    }

    @Test fun unreadableTextIsRepairedToBaseToken() {
        // primary text == background: unreadable. Repaired to base token (readable on base bg) while other overrides stay.
        val bg = 0xFF101010L
        val r = GbThemeOverrides.resolve(dark, Src(colors = mapOf("ModConBackColor" to bg, "ModConTextColor" to bg)))
        assertEquals(bg, r.backgroundColor)
        assertTrue(ThemeConfig.contrastRatio(r.primaryTextColor, r.backgroundColor) >= 3.5)
        assertEquals(dark.primaryTextColor, r.primaryTextColor)
        assertFalse(r.hasContrastIssue)
    }

    @Test fun badBubbleTextRepairedUsingBaseThenBlackOrWhite() {
        val bubble = 0xFF202020L
        val r = GbThemeOverrides.resolve(dark, Src(colors = mapOf("ModChatRightBubble" to bubble, "ModChatBubbleRightColor" to bubble)))
        assertTrue(ThemeConfig.contrastRatio(r.outgoingTextColor, bubble) >= 3.5)
        assertEquals(bubble, r.outgoingBubbleColor)
        // neither candidate nor fallback readable -> luminance rule: bg luminance > 0.45 -> black, else white
        assertEquals(0xFF000000L, GbThemeOverrides.ensureContrast(0xFFB8B8B8L, 0xFFB9B9B9L, 0xFFBABABAL))
        assertEquals(0xFFFFFFFFL, GbThemeOverrides.ensureContrast(0xFF111111L, 0xFF101010L, 0xFF121212L))
        assertEquals(0xFF777777L, GbThemeOverrides.ensureContrast(0xFF777777L, 0xFF000000L, 0xFF000000L)) // readable keeps foreground
    }

    /** Dart quirk (kept): the "pull surface toward canvas" branch is unreachable, because after the first repair
     *  ensureContrastWorst(repaired.primary, same surfaces, base.primary) always returns repaired.primary
     *  (a passing fallback is returned by the first call, a failed one falls to the same black/white). So a
     *  near-white canvas over a near-black surface keeps the user's surface and ends with the luminance fallback. */
    @Test fun oppositeSurfaceBranchIsDeadInDartSoSurfaceIsKept() {
        val r = GbThemeOverrides.resolve(light, Src(colors = mapOf("ModConBackColor" to 0xFFFFFFFFL, "ModChatColor" to 0xFF000000L)))
        assertEquals(0xFFFFFFFFL, r.backgroundColor)
        assertEquals(0xFF000000L, r.surfaceColor)
        assertEquals(0xFF000000L, r.primaryTextColor) // backgrounds.first() is white -> black
    }

    private fun argbR(c: Long) = ((c shr 16) and 0xFF).toInt()

    @Test fun colorMathMatchesFlutter() {
        assertEquals(0xFF808080L, GbThemeOverrides.alphaBlend(0x80FFFFFFL, 0xFF000000L))
        assertEquals(0xFFFFFFFFL, GbThemeOverrides.alphaBlend(0xFFFFFFFFL, 0xFF000000L))
        assertEquals(0xFF123456L, GbThemeOverrides.alphaBlend(0x00FFFFFFL, 0xFF123456L))
        assertEquals(0x73FFFFFFL, GbThemeOverrides.withAlpha(0xFFFFFFFFL, 0.45)) // round(114.75)=115=0x73 (truncation would give 0x72)
        assertEquals(0x0BFFFFFFL, GbThemeOverrides.withAlpha(0xFFFFFFFFL, 0.045))
        assertEquals(0x06000000L, GbThemeOverrides.withAlpha(0xFF000000L, 0.025))
    }
}
