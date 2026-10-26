package com.example.chat.ui.theme

import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbLuminance
import com.example.chat.core.theme.bubbleStyleIdFromString
import com.example.chat.core.theme.deliveryIconStyleFromString
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.domain.models.ConversationPreferences
import com.example.chat.domain.models.UniversalPreferences

/** The slice of ChatyPreferencesController that GbThemeOverrides reads (lets the pure logic run on the JVM
 *  with a fake; the real controller adapts via [GbThemeOverrides.resolve]). */
interface GbThemeSource {
    fun gbColor(key: String): Long?
    fun gbDouble(key: String, fallback: Double = 0.0): Double
    fun gbString(key: String, fallback: String = ""): String
    val universal: UniversalPreferences
    val conversation: ConversationPreferences
}

/**
 * Port of lib/ui/core/gb/gb_theme_overrides.dart (GbThemeOverrides.resolve, api 3bee780d).
 * Colors are 32-bit ARGB Longs (ThemeConfig convention). Precedence, key lists, contrast repair loop, and the
 * Dart quirks (shadowed `surface`, post-loop surface assignment, alphaBlend integer math, alpha round) are kept.
 */
object GbThemeOverrides {
    private const val MIN_TEXT = 3.5
    private const val WHITE = 0xFFFFFFFFL
    private const val BLACK = 0xFF000000L

    fun resolve(base: ThemeConfig, prefs: ChatyPreferencesController): ThemeConfig = resolve(base, object : GbThemeSource {
        override fun gbColor(key: String) = prefs.gbColor(key)
        override fun gbDouble(key: String, fallback: Double) = prefs.gbDouble(key, fallback)
        override fun gbString(key: String, fallback: String) = prefs.gbString(key, fallback)
        override val universal get() = prefs.universal
        override val conversation get() = prefs.conversation
    })

    fun resolve(base: ThemeConfig, prefs: GbThemeSource): ThemeConfig {
        fun firstColor(keys: List<String>): Long? {
            for (key in keys) prefs.gbColor(key)?.let { return it }
            return null
        }

        val dark = base.isDark
        val universal = prefs.universal
        val conv = prefs.conversation

        val accent = universal.universalColor ?: firstColor(
            listOfNotNull(if (dark) "ModDarkConPickColor" else null, "ModConPickColor", "ModConColor", "tabindicator"),
        )
        val background = conv.convBackgroundColor ?: universal.backgroundColor ?: firstColor(
            listOfNotNull(if (dark) "ModDarkConPickColorNav" else null, "ModConBackColor", "list_bg_color", "ConvoBack"),
        )
        val surface = firstColor(listOf("ModChatColor", "BGColor"))
        val primaryText = universal.universalActionBarTextColor ?: firstColor(listOf("ModConTextColor", "HomeBarText", "ModContactNameColor"))
        val outgoingBubble = conv.rightBubbleColor ?: firstColor(listOf("ModChatRightBubble"))
        val incomingBubble = conv.leftBubbleColor ?: firstColor(listOf("ModChatLeftBubble"))
        val outgoingText = conv.rightChatBubbleTextColor ?: firstColor(listOf("ModChatBubbleRightColor"))
        val incomingText = conv.leftChatBubbleTextColor ?: firstColor(listOf("ModChatBubbleLeftColor"))
        val link = conv.hyperlinksColor ?: firstColor(listOf("ModChatLinkColor"))

        val fontScaleRaw = prefs.gbDouble("font_scale")
        val fontScale = if (fontScaleRaw > 0.0 && fontScaleRaw.isFinite()) fontScaleRaw.coerceIn(0.5, 2.0) else null

        // Typed Conversation selection is canonical; legacy GB key is the fallback; then the base theme.
        val bubbleStyle: BubbleStyleId =
            bubbleStyleOf(conv.bubbleStyle.trim()) ?: bubbleStyleOf(prefs.gbString("bubble_style", "")) ?: base.bubbleStyle

        val deliveryTickStyle = deliveryIconStyleFromString(conv.tickStyle)

        val candidate = base.copyWith(
            accentColor = accent,
            backgroundColor = background,
            surfaceColor = surface,
            cardColor = surface?.let {
                alphaBlend(if (base.isDark) withAlpha(WHITE, 0.045) else withAlpha(BLACK, 0.025), it)
            },
            primaryTextColor = primaryText,
            outgoingBubbleColor = outgoingBubble,
            incomingBubbleColor = incomingBubble,
            outgoingTextColor = outgoingText,
            incomingTextColor = incomingText,
            linkColor = link,
            fontScale = fontScale,
            bubbleStyle = bubbleStyle,
            deliveryTickStyle = deliveryTickStyle,
            tickStyle = conv.tickStyle,
        )

        // Never accept an override that makes core text unreadable.
        if (!candidate.hasContrastIssue) return candidate

        var repaired = candidate.copyWith(
            primaryTextColor = ensureContrastWorst(
                candidate.primaryTextColor, listOf(candidate.backgroundColor, candidate.surfaceColor), base.primaryTextColor,
            ),
            secondaryTextColor = ensureContrast(candidate.secondaryTextColor, candidate.backgroundColor, base.secondaryTextColor),
            outgoingTextColor = ensureContrast(candidate.outgoingTextColor, candidate.outgoingBubbleColor, base.outgoingTextColor),
            incomingTextColor = ensureContrast(candidate.incomingTextColor, candidate.incomingBubbleColor, base.incomingTextColor),
        )

        // Tonally opposite surface vs canvas: pull the SURFACE toward the canvas until one text token reads on both.
        val surfaces = listOf(repaired.backgroundColor, repaired.surfaceColor)
        if (ensureContrastWorst(repaired.primaryTextColor, surfaces, base.primaryTextColor) != repaired.primaryTextColor) {
            var s = repaired.surfaceColor // Dart: local `surface` shadows the override above
            for (i in 0 until 4) {
                s = alphaBlend(withAlpha(repaired.backgroundColor, 0.45), s)
                val attempt = repaired.copyWith(surfaceColor = s)
                if (ThemeConfig.contrastRatio(attempt.primaryTextColor, s) >= MIN_TEXT) {
                    repaired = attempt
                    break
                }
            }
            if (repaired.surfaceColor != s) repaired = repaired.copyWith(surfaceColor = s)
        }
        return repaired
    }

    private fun bubbleStyleOf(raw: String): BubbleStyleId? = if (raw.isEmpty()) null else bubbleStyleIdFromString(raw)

    internal fun ensureContrast(foreground: Long, background: Long, fallback: Long): Long {
        if (ThemeConfig.contrastRatio(foreground, background) >= MIN_TEXT) return foreground
        if (ThemeConfig.contrastRatio(fallback, background) >= MIN_TEXT) return fallback
        return if (argbLuminance(background) > 0.45) BLACK else WHITE
    }

    internal fun ensureContrastWorst(foreground: Long, backgrounds: List<Long>, fallback: Long): Long {
        var minCandidate = Double.POSITIVE_INFINITY
        var minFallback = Double.POSITIVE_INFINITY
        for (bg in backgrounds) {
            val c = ThemeConfig.contrastRatio(foreground, bg)
            val f = ThemeConfig.contrastRatio(fallback, bg)
            if (c < minCandidate) minCandidate = c
            if (f < minFallback) minFallback = f
        }
        if (minCandidate >= MIN_TEXT) return foreground
        if (minFallback >= MIN_TEXT) return fallback
        return if (argbLuminance(backgrounds.first()) > 0.45) BLACK else WHITE
    }

    /** Flutter Color.withValues(alpha:) -> toARGB32: alpha = (a * 255).round(). */
    internal fun withAlpha(argb: Long, alpha: Double): Long {
        val a = Math.round(alpha.coerceIn(0.0, 1.0) * 255.0).toLong() and 0xFF
        return (argb and 0x00FFFFFFL) or (a shl 24)
    }

    /** Flutter Color.alphaBlend (integer channel math, truncating division). */
    internal fun alphaBlend(foreground: Long, background: Long): Long {
        val fa = ((foreground shr 24) and 0xFF).toInt()
        val inv = 0xFF - fa
        var ba = ((background shr 24) and 0xFF).toInt()
        fun ch(c: Long, s: Int) = ((c shr s) and 0xFF).toInt()
        return if (ba == 0xFF) {
            pack(0xFF, (fa * ch(foreground, 16) + inv * ch(background, 16)) / 0xFF,
                (fa * ch(foreground, 8) + inv * ch(background, 8)) / 0xFF,
                (fa * ch(foreground, 0) + inv * ch(background, 0)) / 0xFF)
        } else {
            ba = (ba * inv) / 0xFF
            val oa = fa + ba
            pack(oa, (ch(foreground, 16) * fa + ch(background, 16) * ba) / oa,
                (ch(foreground, 8) * fa + ch(background, 8) * ba) / oa,
                (ch(foreground, 0) * fa + ch(background, 0) * ba) / oa)
        }
    }

    private fun pack(a: Int, r: Int, g: Int, b: Int): Long =
        ((a.toLong() and 0xFF) shl 24) or ((r.toLong() and 0xFF) shl 16) or ((g.toLong() and 0xFF) shl 8) or (b.toLong() and 0xFF)
}
