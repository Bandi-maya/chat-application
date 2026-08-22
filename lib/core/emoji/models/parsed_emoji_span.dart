/// Represents the display format and size level for emojis in various UI contexts.
enum EmojiDisplayMode {
  /// Standard inline emoji aligned with accompanying text line height.
  inline,

  /// Extra-large expressive emoji for single emoji messages (60–68px).
  jumboSingle,

  /// Medium-large expressive emoji for 2–3 emoji-only messages (42–48px).
  mediumFew,

  /// Medium emoji for 4–6 emoji-only messages (32–36px).
  mediumMany,

  /// Reaction chip emoji (18–22px).
  reaction,

  /// Picker preview tile emoji (36–42px).
  picker,
}

/// Token representing either normal text or an animated/static emoji.
class ParsedEmojiSpan {
  final String rawText;
  final bool isEmoji;
  final String normalizedUnicode;

  const ParsedEmojiSpan.text(this.rawText)
    : isEmoji = false,
      normalizedUnicode = rawText;

  const ParsedEmojiSpan.emoji({
    required this.rawText,
    required this.normalizedUnicode,
  }) : isEmoji = true;

  @override
  String toString() => isEmoji
      ? 'EmojiToken($rawText -> $normalizedUnicode)'
      : 'TextToken($rawText)';
}
