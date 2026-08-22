import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/formatting/chat_formatters.dart';
import 'package:chat/features/messages/emoji_only.dart';

void main() {
  group('formatConversationTimestamp', () {
    final now = DateTime(2026, 8, 22, 10, 0); // Saturday

    test('today renders clock time', () {
      expect(
        formatConversationTimestamp(DateTime(2026, 8, 22, 9, 52), now: now),
        '9:52 AM',
      );
    });

    test('yesterday renders the label', () {
      expect(
        formatConversationTimestamp(DateTime(2026, 8, 21, 23, 10), now: now),
        'Yesterday',
      );
    });

    test('within a week renders the weekday', () {
      // Monday same week.
      expect(
        formatConversationTimestamp(DateTime(2026, 8, 17, 8, 0), now: now),
        'Monday',
      );
    });

    test('older messages render dd/mm/yyyy', () {
      expect(
        formatConversationTimestamp(DateTime(2026, 7, 30, 6, 45), now: now),
        '30/07/2026',
      );
    });

    test('midnight boundary: 00:05 today is today', () {
      expect(
        formatConversationTimestamp(DateTime(2026, 8, 22, 0, 5), now: now),
        '12:05 AM',
      );
    });
  });

  group('formatLastSeen', () {
    final now = DateTime(2026, 8, 22, 19, 0);

    test('today includes time', () {
      expect(
        formatLastSeen(DateTime(2026, 8, 22, 9, 52), now: now),
        'last seen today at 9:52 AM',
      );
    });

    test('yesterday includes time', () {
      expect(
        formatLastSeen(DateTime(2026, 8, 21, 20, 16), now: now),
        'last seen yesterday at 8:16 PM',
      );
    });

    test('older dates render date + time', () {
      expect(
        formatLastSeen(DateTime(2026, 8, 19, 18, 45), now: now),
        'last seen 19/08/2026 at 6:45 PM',
      );
    });

    test('null stays empty so privacy rules stay with the caller', () {
      expect(formatLastSeen(null, now: now), '');
    });
  });

  group('classifyEmojiOnly', () {
    test('single emoji', () {
      final info = classifyEmojiOnly('😂');
      expect(info.isEmojiOnly, isTrue);
      expect(info.graphemeCount, 1);
      expect(info.fontSize(14), greaterThan(28));
    });

    test('repeated emoji counts each glyph', () {
      expect(classifyEmojiOnly('😂😂').graphemeCount, 2);
      expect(classifyEmojiOnly('🔥🔥🔥🔥🔥').graphemeCount, 5);
    });

    test('skin-tone modifier stays one glyph', () {
      // Thumbs up + medium-dark tone.
      expect(classifyEmojiOnly('👍🏽').graphemeCount, 1);
    });

    test('ZWJ family emoji stays one glyph', () {
      // 👨‍👩‍👧‍👦 family built from four people + three ZWJs.
      const family = '👨‍👩‍👧‍👦';
      expect(classifyEmojiOnly(family).isEmojiOnly, isTrue);
      expect(classifyEmojiOnly(family).graphemeCount, 1);
    });

    test('regional-indicator flag is one glyph', () {
      expect(classifyEmojiOnly('🇮🇳').isEmojiOnly, isTrue);
      expect(classifyEmojiOnly('🇮🇳').graphemeCount, 1);
    });

    test('keycap sequence is one glyph', () {
      expect(classifyEmojiOnly('1️⃣').isEmojiOnly, isTrue);
    });

    test('emoji run plus heart still classifies', () {
      expect(classifyEmojiOnly('😂😂 ❤️').graphemeCount, 3);
    });

    test('hearts repeat correctly (2600-27BF range)', () {
      expect(classifyEmojiOnly('❤️❤️').graphemeCount, 2);
    });

    test('tag-spec flag (England) is one glyph', () {
      expect(classifyEmojiOnly('🏴󠁧󠁢󠁥󠁮󠁧󠁿').isEmojiOnly, isTrue);
      expect(classifyEmojiOnly('🏴󠁧󠁢󠁥󠁮󠁧󠁿').graphemeCount, 1);
    });

    test('text mixed in breaks emoji-only', () {
      expect(classifyEmojiOnly('text 😂').isEmojiOnly, isFalse);
      expect(classifyEmojiOnly('hello').isEmojiOnly, isFalse);
      expect(classifyEmojiOnly('').isEmojiOnly, isFalse);
    });

    test('results are memoized (same instance for same input)', () {
      identical(classifyEmojiOnly('🚀'), classifyEmojiOnly('🚀'));
    });
  });
}
