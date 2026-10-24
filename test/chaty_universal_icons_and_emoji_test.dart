import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:chat/domain/models/preferences.dart';
import 'package:chat/ui/core/icons/chaty_icon_theme.dart';
import 'package:chat/ui/core/icons/chaty_icon_registry.dart';
import 'package:chat/ui/core/icons/chaty_canonical_overflow_button.dart';
import 'package:chat/core/emoji/chaty_emoji_style.dart';
import 'package:chat/core/emoji/chaty_emoji_playback_manager.dart';
import 'package:chat/core/emoji/chaty_motion_emoji.dart';
import 'package:chat/core/emoji/widgets/animated_emoji_view.dart';
import 'package:chat/core/emoji/widgets/animated_emoji_text.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Universal Chaty Icon Themes Contract (10 Distinct Grammars)', () {
    test('Contains exactly 10 distinct, original icon themes', () {
      expect(ChatyIconThemeId.values.length, 10);
      expect(
        ChatyIconThemeId.values.map((t) => t.title).toList(),
        [
          'Chaty Sigil',
          'Precision Contour',
          'Monolithic Cutout',
          'Origami Geometry',
          'Organic Motion',
          'Pixel Precision',
          'Technical Blueprint',
          'Sculpted Volume',
          'Expressive Ink',
          'Signal Language',
        ],
      );
    });

    test('Chaty Sigil is the canonical default theme and backwards-compatible with Chaty Orbit', () {
      expect(ChatyIconThemeId.fromString(null), ChatyIconThemeId.chatySigil);
      expect(ChatyIconThemeId.fromString(''), ChatyIconThemeId.chatySigil);
      expect(ChatyIconThemeId.fromString('Unknown Theme'), ChatyIconThemeId.chatySigil);
      expect(ChatyIconThemeId.fromString('Chaty Sigil'), ChatyIconThemeId.chatySigil);
      expect(ChatyIconThemeId.fromString('chaty_sigil'), ChatyIconThemeId.chatySigil);

      // Legacy string migration
      expect(ChatyIconThemeId.fromString('Chaty Orbit'), ChatyIconThemeId.chatySigil);
      expect(ChatyIconThemeId.fromString('Precision Line'), ChatyIconThemeId.precisionContour);
      expect(ChatyIconThemeId.fromString('Soft Geometry'), ChatyIconThemeId.monolithicCutout);
      expect(ChatyIconThemeId.fromString('Liquid Motion'), ChatyIconThemeId.organicMotion);
      expect(ChatyIconThemeId.fromString('Sculpted Duotone'), ChatyIconThemeId.sculptedVolume);
      expect(ChatyIconThemeId.fromString('Signal Pulse'), ChatyIconThemeId.signalLanguage);
      expect(ChatyIconThemeId.fromString('Neo Minimal'), ChatyIconThemeId.technicalBlueprint);
      expect(ChatyIconThemeId.fromString('Ink Flow'), ChatyIconThemeId.expressiveInk);
      expect(ChatyIconThemeId.fromString('Prism Edge'), ChatyIconThemeId.origamiGeometry);
    });

    test('UniversalPreferences persists iconTheme correctly', () {
      const prefs = UniversalPreferences();
      // Supports both default and customized icon theme persistence
      final updated = prefs.copyWith(iconTheme: 'Precision Contour');
      expect(updated.iconTheme, 'Precision Contour');

      final map = updated.toMap();
      expect(map['iconTheme'], 'Precision Contour');

      final restored = UniversalPreferences.fromMap(map);
      expect(restored.iconTheme, 'Precision Contour');
    });

    testWidgets('ChatyIcon renders all 14 core actions without errors across all 10 themes', (tester) async {
      const coreIcons = [
        ChatyIconId.search,
        ChatyIconId.back,
        ChatyIconId.more,
        ChatyIconId.camera,
        ChatyIconId.cameraFlip,
        ChatyIconId.cameraShutter,
        ChatyIconId.callAudio,
        ChatyIconId.callVideo,
        ChatyIconId.send,
        ChatyIconId.attachment,
        ChatyIconId.microphone,
        ChatyIconId.favourite,
        ChatyIconId.settings,
        ChatyIconId.messageCheckDouble,
      ];

      for (final theme in ChatyIconThemeId.values) {
        for (final icon in coreIcons) {
          await tester.pumpWidget(
            MaterialApp(
              home: Scaffold(
                body: Center(
                  child: ChatyIcon(
                    icon,
                    size: 24,
                    color: Colors.blue,
                    themeOverride: theme,
                  ),
                ),
              ),
            ),
          );
          expect(find.byType(ChatyIcon), findsOneWidget);
        }
      }
    });

    testWidgets('ChatyCanonicalOverflowButton renders and responds to tap', (tester) async {
      bool tapped = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Center(
              child: ChatyCanonicalOverflowButton(
                onPressed: () => tapped = true,
                color: Colors.black,
                size: 24,
              ),
            ),
          ),
        ),
      );

      expect(find.byType(ChatyCanonicalOverflowButton), findsOneWidget);
      await tester.tap(find.byType(ChatyCanonicalOverflowButton));
      await tester.pumpAndSettle();
      expect(tapped, isTrue);
    });
  });

  group('Emoji Styles Contract (11 Distinct Styles)', () {
    test('Contains exactly 11 distinct emoji styles', () {
      expect(ChatyEmojiStyleId.values.length, 11);
      expect(
        ChatyEmojiStyleId.values.map((s) => s.title).toList(),
        [
          'Chaty Motion',
          'Chaty Ink',
          'Chaty Soft 3D',
          'Chaty Pixel',
          'OpenMoji',
          'Twemoji',
          'Noto Color',
          'Fluent Color',
          'Blobmoji',
          'EmojiTwo',
          'iOS Animated (Drive)',
        ],
      );
    });

    test('Chaty Motion is the canonical default style and backwards-compatible with legacy strings', () {
      expect(ChatyEmojiStyleId.fromString(null), ChatyEmojiStyleId.chatyMotion);
      expect(ChatyEmojiStyleId.fromString(''), ChatyEmojiStyleId.chatyMotion);
      expect(ChatyEmojiStyleId.fromString('Unknown'), ChatyEmojiStyleId.chatyMotion);
      expect(ChatyEmojiStyleId.fromString('Chaty Motion'), ChatyEmojiStyleId.chatyMotion);

      // Legacy string migration
      expect(ChatyEmojiStyleId.fromString('system_default'), ChatyEmojiStyleId.chatyMotion);
      expect(ChatyEmojiStyleId.fromString('Minimal Outline'), ChatyEmojiStyleId.chatyInk);
      expect(ChatyEmojiStyleId.fromString('Twemoji Color'), ChatyEmojiStyleId.twemoji);
      expect(ChatyEmojiStyleId.fromString('iOS-Inspired'), ChatyEmojiStyleId.chatySoft3D);
      expect(ChatyEmojiStyleId.fromString('One UI-Inspired'), ChatyEmojiStyleId.notoColor);
      expect(ChatyEmojiStyleId.fromString('Soft 3D'), ChatyEmojiStyleId.chatySoft3D);
    });

    test('UniversalPreferences default emojiStyle is Chaty Motion and persists correctly', () {
      const prefs = UniversalPreferences();
      expect(prefs.emojiStyle, 'Chaty Motion');

      final updated = prefs.copyWith(emojiStyle: 'Noto Color');
      expect(updated.emojiStyle, 'Noto Color');

      final map = updated.toMap();
      expect(map['emojiStyle'], 'Noto Color');

      final restored = UniversalPreferences.fromMap(map);
      expect(restored.emojiStyle, 'Noto Color');
    });

    testWidgets('ChatyMotionEmoji renders across all 10 emoji styles', (tester) async {
      for (final style in ChatyEmojiStyleId.values) {
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: Center(
                child: ChatyMotionEmoji(
                  unicode: '😂',
                  size: 48,
                  style: style,
                  animate: false,
                ),
              ),
            ),
          ),
        );
        expect(find.byType(ChatyMotionEmoji), findsOneWidget);
      }
    });
  });

  group('Automatic Animated Emoji Playback & Lifecycle Management', () {
    setUp(() {
      ChatyEmojiPlaybackManager.instance.clearAll();
    });

    test('Newly sent or arrived message is eligible for auto-play exactly once', () {
      const messageId = 'msg-101';
      const emojiIndex = 0;

      // First check: must be eligible for automatic play
      expect(ChatyEmojiPlaybackManager.instance.shouldAutoPlay(messageId, emojiIndex), isTrue);

      // Start animation
      ChatyEmojiPlaybackManager.instance.notifyAnimationStarted(messageId, emojiIndex);

      // Complete animation
      ChatyEmojiPlaybackManager.instance.notifyAnimationCompleted(messageId, emojiIndex);

      // Subsequent checks (e.g. scroll or rebuild): must NOT replay automatically!
      expect(ChatyEmojiPlaybackManager.instance.shouldAutoPlay(messageId, emojiIndex), isFalse);
      expect(ChatyEmojiPlaybackManager.instance.isCompleted(messageId, emojiIndex), isTrue);

      // Reset on message retry allows auto-play again
      ChatyEmojiPlaybackManager.instance.resetMessage(messageId);
      expect(ChatyEmojiPlaybackManager.instance.shouldAutoPlay(messageId, emojiIndex), isTrue);
    });

    test('ChatyMotionEmoji supports top expressive emojis with zero external dependencies', () {
      expect(ChatyMotionEmoji.isSupported('😂'), isTrue);
      expect(ChatyMotionEmoji.isSupported('❤️'), isTrue);
      expect(ChatyMotionEmoji.isSupported('🔥'), isTrue);
      expect(ChatyMotionEmoji.isSupported('👍'), isTrue);
      expect(ChatyMotionEmoji.isSupported('🎉'), isTrue);
      expect(ChatyMotionEmoji.isSupported('🤔'), isTrue);
      expect(ChatyMotionEmoji.isSupported('😮'), isTrue);
      expect(ChatyMotionEmoji.isSupported('😢'), isTrue);
      expect(ChatyMotionEmoji.isSupported('👋'), isTrue);

      // Random non-expressive symbol returns false and falls back to Unicode/font
      expect(ChatyMotionEmoji.isSupported('🪑'), isFalse);
    });

    testWidgets('ChatyMotionEmoji renders and animates without errors', (tester) async {
      bool completed = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Center(
              child: ChatyMotionEmoji(
                unicode: '😂',
                size: 64,
                animate: true,
                autoStart: true,
                onCompleted: () => completed = true,
              ),
            ),
          ),
        ),
      );

      expect(find.byType(ChatyMotionEmoji), findsOneWidget);
      await tester.pump(const Duration(milliseconds: 700));
      await tester.pump(const Duration(milliseconds: 800));
      expect(completed, isTrue);
    });

    testWidgets('AnimatedEmojiView automatically starts for new message', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Center(
              child: AnimatedEmojiView(
                unicode: '😂',
                messageId: 'new-msg-test',
                emojiIndex: 0,
                animate: true,
              ),
            ),
          ),
        ),
      );

      expect(find.byType(AnimatedEmojiView), findsOneWidget);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
    });

    testWidgets('AnimatedEmojiText passes messageId down to emojis', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Center(
              child: AnimatedEmojiText(
                text: 'Hello 😂 world ❤️',
                messageId: 'msg-text-test',
              ),
            ),
          ),
        ),
      );

      expect(find.byType(AnimatedEmojiText), findsOneWidget);
      expect(find.byType(AnimatedEmojiView), findsNWidgets(2));
    });
  });
}
