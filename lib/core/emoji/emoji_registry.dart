import 'package:animated_emoji/animated_emoji.dart';

/// Searchable, user-facing metadata for one animated emoji.
class ChatyEmojiEntry {
  const ChatyEmojiEntry({
    required this.data,
    required this.label,
    required this.aliases,
    required this.keywords,
  });

  final AnimatedEmojiData data;
  final String label;
  final List<String> aliases;
  final List<String> keywords;

  String get unicode => data.toUnicodeEmoji();

  bool matches(String rawQuery) {
    final query = rawQuery.trim().toLowerCase();
    if (query.isEmpty) return true;
    return <String>[
      label,
      data.id,
      unicode,
      ...aliases,
      ...keywords,
    ].join(' ').toLowerCase().contains(query);
  }
}

/// Central registry mapping Unicode emojis, skin tones, ZWJ sequences,
/// and variation selectors to vector-animated emoji assets.
class ChatyEmojiRegistry {
  ChatyEmojiRegistry._();

  static final ChatyEmojiRegistry instance = ChatyEmojiRegistry._();

  static final Map<String, AnimatedEmojiData> _lookup =
      <String, AnimatedEmojiData>{};
  static final List<ChatyEmojiEntry> _entries = <ChatyEmojiEntry>[];
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
      final words = _wordsFromId(emoji.id);
      final enrichment = _metadataByUnicode[normalize(unicode)];
      _entries.add(
        ChatyEmojiEntry(
          data: emoji,
          label: enrichment?.label ?? _titleCase(words),
          aliases: enrichment?.aliases ?? const <String>[],
          keywords: <String>[...words, ...?enrichment?.keywords],
        ),
      );
    }
  }

  static List<String> _wordsFromId(String id) => id
      .replaceAllMapped(RegExp(r'([a-z])([A-Z])'), (m) => '${m[1]} ${m[2]}')
      .replaceAll(RegExp(r'[_\-]+'), ' ')
      .toLowerCase()
      .split(RegExp(r'\s+'))
      .where((word) => word.isNotEmpty)
      .toList(growable: false);

  static String _titleCase(List<String> words) => words
      .map((word) => '${word[0].toUpperCase()}${word.substring(1)}')
      .join(' ');

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

  /// User-facing entries used by search, labels and accessibility semantics.
  static List<ChatyEmojiEntry> get entries {
    ensureInitialized();
    return List<ChatyEmojiEntry>.unmodifiable(_entries);
  }

  static const Map<String, _EmojiMetadata> _metadataByUnicode = {
    '😂': _EmojiMetadata(
      'Face with tears of joy',
      ['joy', 'lol'],
      ['laugh', 'funny'],
    ),
    '❤️': _EmojiMetadata('Red heart', ['love'], ['heart', 'favorite']),
    '👍': _EmojiMetadata('Thumbs up', ['like', 'yes'], ['approve', 'good']),
    '🙏': _EmojiMetadata(
      'Folded hands',
      ['pray', 'thanks'],
      ['please', 'gratitude'],
    ),
    '🔥': _EmojiMetadata('Fire', ['lit', 'hot'], ['trending', 'flame']),
    '🎉': _EmojiMetadata(
      'Party popper',
      ['party'],
      ['celebrate', 'congratulations'],
    ),
  };
}

class _EmojiMetadata {
  const _EmojiMetadata(this.label, this.aliases, this.keywords);
  final String label;
  final List<String> aliases;
  final List<String> keywords;
}
