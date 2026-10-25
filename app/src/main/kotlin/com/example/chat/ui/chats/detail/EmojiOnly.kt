package com.example.chat.ui.chats.detail

/** Exact port of lib/features/messages/emoji_only.dart: emoji-only message
 *  classification that survives complex Unicode (skin tones, ZWJ sequences,
 *  regional-indicator flags, keycaps, variation selectors, tag-spec flags).
 *  Results are memoized per source string. */

class EmojiOnlyInfo(val isEmojiOnly: Boolean, val graphemeCount: Int) {
    /** Telegram-style sizing: one glyph large, runs shrink toward normal. */
    fun fontSize(baseTextSize: Double): Double = when (graphemeCount) {
        1 -> baseTextSize * 2.9
        2 -> baseTextSize * 2.4
        3 -> baseTextSize * 2.0
        4 -> baseTextSize * 1.7
        else -> baseTextSize * 1.3
    }
}

private fun isEmojiBase(r: Int): Boolean {
    if (r < 0x20) return false
    if ((r in 0x30..0x39) || r == 0x23 || r == 0x2A) return true // #*0-9
    if (r < 0x2190) return false
    return (r in 0x2190..0x21FF) || // arrows
        (r in 0x231A..0x231B) || // watch/hourglass
        (r == 0x2328) ||
        (r in 0x23CF..0x23FA) ||
        (r == 0x24C2) ||
        (r in 0x25AA..0x25FE) ||
        (r in 0x2600..0x27BF) || // misc symbols & dingbats
        (r in 0x2934..0x2935) ||
        (r in 0x2B00..0x2BFF) || // stars/arrows
        (r == 0x3030) ||
        (r == 0x303D) ||
        (r == 0x3297) ||
        (r == 0x3299) ||
        (r in 0x1F000..0x1FAFF) || // main emoji planes
        (r in 0x1F1E6..0x1F1FF) || // regional indicators (flags)
        (r in 0xE0020..0xE007F) // tag characters
}

private fun isModifier(r: Int): Boolean =
    (r in 0x1F3FB..0x1F3FF) || // skin tones
        r == 0xFE0F || // variation selector-16
        r == 0x20E3 || // keycap combining mark
        (r in 0xE0020..0xE007F) // tag-spec flags

private fun isRegionalIndicator(r: Int): Boolean = r in 0x1F1E6..0x1F1FF

private val cache = HashMap<String, EmojiOnlyInfo>()

fun classifyEmojiOnly(text: String): EmojiOnlyInfo {
    cache[text]?.let { return it }
    val info = classify(text)
    if (cache.size > 1024) cache.clear()
    cache[text] = info
    return info
}

private fun classify(raw: String): EmojiOnlyInfo {
    var sawAnyNonSpace = false
    var glyphCount = 0
    var pendingJoiner = false
    var currentHasGlyph = false
    var inGlyph = false
    var prevBase = -1

    for (r in raw.codePoints().toArray()) {
        if (r == 0x200D) { // zero-width joiner glues glyphs together
            pendingJoiner = true
            continue
        }
        if (isModifier(r)) {
            if (inGlyph) currentHasGlyph = true
            continue
        }
        if (r == 0xFE0F || r == 0xFE0E) {
            if (inGlyph) currentHasGlyph = true
            continue
        }
        if (isEmojiBase(r)) {
            // A regional indicator directly following another one continues
            // the same flag glyph.
            val continuesFlag = isRegionalIndicator(prevBase) && isRegionalIndicator(r)
            if (!inGlyph || (!pendingJoiner && !continuesFlag)) {
                if (inGlyph) glyphCount++
                inGlyph = true
                currentHasGlyph = true
            }
            prevBase = r
            pendingJoiner = false
            sawAnyNonSpace = true
            continue
        }
        if (String(Character.toChars(r)).isBlank()) {
            // Whitespace ends the current glyph run but is allowed inside an
            // emoji-only message.
            if (inGlyph) {
                glyphCount++
                inGlyph = false
                currentHasGlyph = false
            }
            pendingJoiner = false
            prevBase = -1
            continue
        }
        prevBase = r
        // Any visible non-emoji character -> ordinary text message.
        return EmojiOnlyInfo(false, 0)
    }

    if (inGlyph && currentHasGlyph) glyphCount++
    if (!sawAnyNonSpace || glyphCount == 0) return EmojiOnlyInfo(false, 0)
    return EmojiOnlyInfo(true, glyphCount)
}
