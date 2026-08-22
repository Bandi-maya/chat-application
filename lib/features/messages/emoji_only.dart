import 'dart:collection';

/// Emoji-only message classification that survives complex Unicode.
///
/// Handles: skin-tone modifiers (U+1F3FB–FF), ZWJ sequences (family,
/// profession…), regional-indicator flags 🇮🇳, keycap sequences (1️⃣),
/// variation selectors U+FE0F, tag-spec flags 🏴󠁧󠁢 and hearts/symbols.
/// Clusters follow Unicode joiner rules instead of UTF-16 length, so a
//  👨‍👩‍👧‍👦 family counts as ONE glyph while 😂😂 counts as TWO.
///
/// Results are memoized per source string: chat list previews and bubbles
/// re-classify the same short strings on every rebuild, and this must never
/// run per animation frame.

bool _isEmojiBase(int r) {
  // Fast rejects first (ASCII letters/digits/punct except keycap bases).
  if (r < 0x20) return false;
  if ((r >= 0x30 && r <= 0x39) || r == 0x23 || r == 0x2A) return true; // #*0-9
  if (r < 0x2190) return false;
  return (r >= 0x2190 && r <= 0x21FF) || // arrows ←
      (r >= 0x231A && r <= 0x231B) || // ⌚⌛
      (r == 0x2328) ||
      (r >= 0x23CF && r <= 0x23FA) ||
      (r == 0x24C2) ||
      (r >= 0x25AA && r <= 0x25FE) ||
      (r >= 0x2600 && r <= 0x27BF) || // ☀☁❤✂✈ misc symbols & dingbats
      (r >= 0x2934 && r <= 0x2935) ||
      (r >= 0x2B00 && r <= 0x2BFF) || // ⭐➡
      (r == 0x3030) ||
      (r == 0x303D) ||
      (r == 0x3297) ||
      (r == 0x3299) ||
      (r >= 0x1F000 && r <= 0x1FAFF) || // main emoji planes
      (r >= 0x1F1E6 && r <= 0x1F1FF) || // regional indicators (flags)
      (r >= 0xE0020 && r <= 0xE007F); // tag characters
}

bool _isModifier(int r) =>
    (r >= 0x1F3FB && r <= 0x1F3FF) || // skin tones
    r == 0xFE0F || // variation selector-16
    r == 0x20E3 || // keycap combining mark
    (r >= 0xE0020 && r <= 0xE007F); // tag-spec flags (🏴󠁧󠁢…)

bool _isRegionalIndicator(int r) => r >= 0x1F1E6 && r <= 0x1F1FF;

class EmojiOnlyInfo {
  final bool isEmojiOnly;

  /// Number of rendered emoji glyphs (ZWJ families count as one).
  final int graphemeCount;

  const EmojiOnlyInfo(this.isEmojiOnly, this.graphemeCount);

  /// Telegram-style sizing: one glyph large, runs shrink toward normal text.
  double fontSize(double baseTextSize) {
    switch (graphemeCount) {
      case 1:
        return baseTextSize * 2.9;
      case 2:
        return baseTextSize * 2.4;
      case 3:
        return baseTextSize * 2.0;
      case 4:
        return baseTextSize * 1.7;
      default:
        return baseTextSize * 1.3;
    }
  }
}

final Map<String, EmojiOnlyInfo> _cache = HashMap<String, EmojiOnlyInfo>();

EmojiOnlyInfo classifyEmojiOnly(String text) {
  final cached = _cache[text];
  if (cached != null) return cached;

  final info = _classify(text);
  if (_cache.length > 1024) _cache.clear();
  _cache[text] = info;
  return info;
}

EmojiOnlyInfo _classify(String raw) {
  var sawAnyNonSpace = false;
  var glyphCount = 0;
  var pendingJoiner = false;
  var currentHasGlyph = false;
  var inGlyph = false;
  var prevBase = -1;

  for (final r in raw.runes) {
    if (r == 0x200D) {
      // Zero-width joiner glues the previous and next glyph together.
      pendingJoiner = true;
      continue;
    }
    if (_isModifier(r)) {
      if (inGlyph) currentHasGlyph = true;
      continue;
    }
    if (r == 0xFE0F || r == 0xFE0E) {
      if (inGlyph) currentHasGlyph = true;
      continue;
    }
    if (_isEmojiBase(r)) {
      // A regional indicator directly following another one continues the
      // same flag glyph (🇮🇳 = two indicators, ONE glyph).
      final continuesFlag =
          _isRegionalIndicator(prevBase) && _isRegionalIndicator(r);
      if (!inGlyph || (!pendingJoiner && !continuesFlag)) {
        if (inGlyph) glyphCount++;
        inGlyph = true;
        currentHasGlyph = true;
      }
      prevBase = r;
      pendingJoiner = false;
      sawAnyNonSpace = true;
      continue;
    }
    final c = String.fromCharCode(r);
    if (c.trim().isEmpty) {
      // Whitespace ends the current glyph run but is allowed inside an
      // emoji-only message ("😂😂 ❤️").
      if (inGlyph) {
        glyphCount++;
        inGlyph = false;
        currentHasGlyph = false;
      }
      pendingJoiner = false;
      prevBase = -1;
      continue;
    }
    prevBase = r;
    // Any visible non-emoji character ⇒ ordinary text message.
    return const EmojiOnlyInfo(false, 0);
  }

  if (inGlyph && currentHasGlyph) glyphCount++;
  if (!sawAnyNonSpace || glyphCount == 0) return const EmojiOnlyInfo(false, 0);
  return EmojiOnlyInfo(true, glyphCount);
}
