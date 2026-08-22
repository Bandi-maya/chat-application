import 'emoji_registry.dart';
import 'models/parsed_emoji_span.dart';

/// Unicode grapheme-aware parser for emoji recognition in chat messages and text strings.
class ChatyEmojiParser {
  const ChatyEmojiParser._();

  /// Comprehensive Unicode Emoji regex pattern supporting:
  /// - Standard emoticons & pictographs
  /// - Supplemental symbols & pictographs
  /// - Symbols & pictographs extended-A
  /// - Transport & map symbols
  /// - Geometric shapes, dingbats, enclosed alphanumerics
  /// - Skin tone modifiers (U+1F3FB..U+1F3FF)
  /// - Zero-Width Joiner (ZWJ, U+200D) sequences for families, professions, genders
  /// - Regional Indicator symbol sequences (flags)
  /// - Keycap sequences (#\uFE0F\u20E3, digits, etc.)
  /// - Variation selectors (U+FE0E, U+FE0F)
  static final RegExp emojiRegex = RegExp(
    r'(?:'
    r'[\u{1F600}-\u{1F64F}]|' // Emoticons
    r'[\u{1F300}-\u{1F5FF}]|' // Misc Symbols and Pictographs
    r'[\u{1F680}-\u{1F6FF}]|' // Transport and Map
    r'[\u{1F700}-\u{1F77F}]|' // Alchemical Symbols
    r'[\u{1F780}-\u{1F7FF}]|' // Geometric Shapes Extended
    r'[\u{1F800}-\u{1F8FF}]|' // Supplemental Arrows-C
    r'[\u{1F900}-\u{1F9FF}]|' // Supplemental Symbols and Pictographs
    r'[\u{1FA00}-\u{1FA6F}]|' // Chess Symbols
    r'[\u{1FA70}-\u{1FAFF}]|' // Symbols and Pictographs Extended-A
    r'[\u{2600}-\u{26FF}]|' // Misc symbols
    r'[\u{2700}-\u{27BF}]|' // Dingbats
    r'[\u{2B50}\u{2B55}\u{2934}\u{2935}\u{2B05}\u{2B06}\u{2B07}\u{3030}\u{303D}\u{3297}\u{3299}]|'
    r'[\u{1F1E6}-\u{1F1FF}]{2}|' // Regional indicator symbol letters (flags)
    r'[0-9#*]\uFE0F?\u20E3' // Keycaps
    r')'
    r'(?:[\u{1F3FB}-\u{1F3FF}])?' // Skin tone modifier
    r'(?:[\uFE0E\uFE0F])?' // Variation selector
    r'(?:'
    r'\u{200D}' // ZWJ
    r'(?:'
    r'[\u{1F600}-\u{1F64F}]|'
    r'[\u{1F300}-\u{1F5FF}]|'
    r'[\u{1F680}-\u{1F6FF}]|'
    r'[\u{1F900}-\u{1F9FF}]|'
    r'[\u{1FA70}-\u{1FAFF}]|'
    r'[\u{2600}-\u{26FF}]|'
    r'[\u{2700}-\u{27BF}]'
    r')'
    r'(?:[\u{1F3FB}-\u{1F3FF}])?'
    r'(?:[\uFE0E\uFE0F])?'
    r')*',
    unicode: true,
  );

  /// Tokenizes a message into ordered [ParsedEmojiSpan] items.
  static List<ParsedEmojiSpan> parse(String text) {
    if (text.isEmpty) return const <ParsedEmojiSpan>[];

    final List<ParsedEmojiSpan> spans = <ParsedEmojiSpan>[];
    int lastIndex = 0;

    for (final Match match in emojiRegex.allMatches(text)) {
      if (match.start > lastIndex) {
        spans.add(ParsedEmojiSpan.text(text.substring(lastIndex, match.start)));
      }
      final rawEmoji = match.group(0)!;
      final normalized = ChatyEmojiRegistry.normalize(rawEmoji);
      spans.add(
        ParsedEmojiSpan.emoji(rawText: rawEmoji, normalizedUnicode: normalized),
      );
      lastIndex = match.end;
    }

    if (lastIndex < text.length) {
      spans.add(ParsedEmojiSpan.text(text.substring(lastIndex)));
    }

    return spans;
  }

  /// Determines whether [text] contains ONLY emoji characters (ignoring whitespace).
  /// If so, returns the total emoji count. Otherwise returns 0.
  static int emojiOnlyCount(String text) {
    final trimmed = text.trim();
    if (trimmed.isEmpty) return 0;

    final spans = parse(trimmed);
    int emojiCount = 0;

    for (final span in spans) {
      if (!span.isEmoji) {
        // If there is any non-whitespace text, this is not emoji-only
        if (span.rawText.trim().isNotEmpty) {
          return 0;
        }
      } else {
        emojiCount++;
      }
    }

    return emojiCount;
  }

  /// Categorizes an emoji-only text into its expressive [EmojiDisplayMode].
  /// Returns null if the text contains standard text.
  static EmojiDisplayMode? resolveEmojiOnlyDisplayMode(String text) {
    final count = emojiOnlyCount(text);
    if (count == 0) return null;
    if (count == 1) return EmojiDisplayMode.jumboSingle;
    if (count <= 3) return EmojiDisplayMode.mediumFew;
    if (count <= 6) return EmojiDisplayMode.mediumMany;
    return EmojiDisplayMode.inline;
  }
}
