import 'package:animated_emoji/animated_emoji.dart';

/// Central registry mapping Unicode emojis, skin tones, ZWJ sequences,
/// and variation selectors to vector-animated emoji assets.
class ChatyEmojiRegistry {
  ChatyEmojiRegistry._();

  static final ChatyEmojiRegistry instance = ChatyEmojiRegistry._();

  static final Map<String, AnimatedEmojiData> _lookup =
      <String, AnimatedEmojiData>{};
  static bool _initialized = false;

  /// Ensures all animated emoji entries are mapped to normalized Unicode representations.
  static void ensureInitialized() {
    if (_initialized) return;
    _initialized = true;

    for (final emoji in AnimatedEmojis.values) {
      final unicode = emoji.toUnicodeEmoji();
      if (unicode.isNotEmpty) {
        _lookup[unicode] = emoji;
        final clean = normalize(unicode);
        if (clean != unicode) {
          _lookup[clean] = emoji;
        }
      }
    }
  }

  /// Normalizes Unicode emoji string by stripping variation selectors (\uFE0E, \uFE0F)
  /// and standardizing skin tone / sequence lookups.
  static String normalize(String emoji) {
    if (emoji.isEmpty) return emoji;
    return emoji.replaceAll('\uFE0F', '').replaceAll('\uFE0E', '').trim();
  }

  /// Finds the corresponding [AnimatedEmojiData] for a Unicode sequence, if available.
  static AnimatedEmojiData? find(String unicode) {
    ensureInitialized();
    final direct = _lookup[unicode];
    if (direct != null) return direct;
    final normalized = normalize(unicode);
    return _lookup[normalized];
  }

  /// All supported animated emojis in the registry.
  static List<AnimatedEmojiData> get allAnimated {
    ensureInitialized();
    return AnimatedEmojis.values;
  }
}
