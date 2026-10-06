import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../emoji_registry.dart';

/// Item in the bounded recently-used emoji cache.
class RecentEmojiItem {
  final String unicode;
  final String label;
  final int lastUsedTimestamp;

  const RecentEmojiItem({
    required this.unicode,
    required this.label,
    required this.lastUsedTimestamp,
  });

  Map<String, dynamic> toJson() => {
        'unicode': unicode,
        'label': label,
        'ts': lastUsedTimestamp,
      };

  factory RecentEmojiItem.fromJson(Map<String, dynamic> map) {
    return RecentEmojiItem(
      unicode: map['unicode'] as String? ?? '😊',
      label: map['label'] as String? ?? 'Smiling Face',
      lastUsedTimestamp: map['ts'] as int? ?? 0,
    );
  }
}

/// A safe, bounded local cache for recently-used emojis adhering to Rule 11 & 12:
/// - Maximum 32 entries (bounded storage)
/// - Survives app restarts via SharedPreferences
/// - Fast in-memory lookup without blocking UI
/// - Stores human-readable names, never raw code values
class EmojiRecentCache {
  EmojiRecentCache._();
  static final EmojiRecentCache instance = EmojiRecentCache._();

  static const String _prefKey = 'chaty_recent_emojis_v1';
  static const int maxCapacity = 32;

  final List<RecentEmojiItem> _items = <RecentEmojiItem>[];
  bool _initialized = false;

  List<RecentEmojiItem> get items => List.unmodifiable(_items);

  /// Initializes the cache asynchronously from local storage without blocking the UI thread.
  Future<void> initialize() async {
    if (_initialized) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_prefKey);
      if (raw != null && raw.isNotEmpty) {
        final decoded = jsonDecode(raw) as List<dynamic>;
        _items.clear();
        for (final entry in decoded) {
          if (entry is Map<String, dynamic>) {
            _items.add(RecentEmojiItem.fromJson(entry));
          }
        }
      }
    } catch (e) {
      if (kDebugMode) {
        debugPrint('EmojiRecentCache initialization error: $e');
      }
    } finally {
      _initialized = true;
    }
  }

  /// Records an emoji as recently used, updating LRU order and persisting.
  Future<void> recordUsage(String unicode, {String? label}) async {
    if (!_initialized) await initialize();

    final cleanUnicode = ChatyEmojiRegistry.normalize(unicode);
    String humanLabel = label ?? '';
    if (humanLabel.isEmpty) {
      final animated = ChatyEmojiRegistry.find(cleanUnicode);
      if (animated != null) {
        final match = ChatyEmojiRegistry.entries.firstWhere(
          (e) => e.data.id == animated.id,
          orElse: () => ChatyEmojiEntry(
            data: animated,
            label: 'Emoji',
            aliases: const [],
            keywords: const [],
          ),
        );
        humanLabel = match.label;
      } else {
        humanLabel = _guessHumanName(cleanUnicode);
      }
    }

    _items.removeWhere(
      (item) => ChatyEmojiRegistry.normalize(item.unicode) == cleanUnicode,
    );

    _items.insert(
      0,
      RecentEmojiItem(
        unicode: unicode,
        label: humanLabel,
        lastUsedTimestamp: DateTime.now().millisecondsSinceEpoch,
      ),
    );

    if (_items.length > maxCapacity) {
      _items.removeRange(maxCapacity, _items.length);
    }

    unawaited(_persist());
  }

  Future<void> _persist() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final encoded = jsonEncode(_items.map((i) => i.toJson()).toList());
      await prefs.setString(_prefKey, encoded);
    } catch (e) {
      if (kDebugMode) {
        debugPrint('EmojiRecentCache persist error: $e');
      }
    }
  }

  static String _guessHumanName(String unicode) {
    if (unicode.isEmpty) return 'Emoji';
    return 'Emoji $unicode';
  }
}
