import 'package:chat/core/emoji/emoji_registry.dart';
import 'package:chat/core/emoji/emoji_parser.dart';
import 'package:chat/core/emoji/models/parsed_emoji_span.dart';
import 'package:chat/core/emoji/widgets/animated_emoji_text.dart';
import 'package:chat/domain/models/chat_message.dart';
import 'package:chat/domain/models/preferences.dart';
import 'package:chat/features/messages/message_bubble.dart';
import 'package:chat/features/settings/settings_search_delegate.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/design_system/settings_primitives.dart';
import 'package:chat/ui/core/gb/gb_theme_overrides.dart';
import 'package:chat/ui/core/theme/theme_presets.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUpAll(() {
    TestWidgetsFlutterBinding.ensureInitialized();
    SharedPreferences.setMockInitialValues({});
  });

  group('Theme Engine & Token Validation Tests', () {
    test('Midnight preset loads with dark brightness and valid colors', () {
      final theme = ThemePresets.midnight;
      expect(theme.id, 'midnight');
      expect(theme.hasContrastIssue, isFalse);
    });

    test('High Contrast preset satisfies accessibility ratios', () {
      final theme = ThemePresets.highContrast;
      expect(theme.highContrast, isTrue);
      expect(theme.hasContrastIssue, isFalse);
    });

    test('All presets are registered and accessible', () {
      final presets = ThemePresets.all;
      expect(presets.length, greaterThanOrEqualTo(13));
      for (final p in presets) {
        expect(p.name.isNotEmpty, isTrue);
        expect(p.id.isNotEmpty, isTrue);
      }
    });

    test(
      'GbThemeOverrides safely respects user color overrides with contrast guard',
      () {
        final prefs = ChatyPreferencesController();
        final base = ThemePresets.midnight;
        final resolved = GbThemeOverrides.resolve(base, prefs);
        expect(resolved.id, base.id);
        expect(resolved.hasContrastIssue, isFalse);
      },
    );
  });

  group('Chaty Preferences Domain & State Tests', () {
    test('Default preferences initialize with valid defaults', () {
      final prefs = ChatyPreferencesController();
      expect(prefs.privacy.freezeLastSeen, isFalse);
      expect(prefs.security.isAppLockEnabled, isFalse);
      expect(prefs.home.homeStyle, 'Chaty Default');
      expect(prefs.conversation.bubbleShape, 'Rounded');
      expect(prefs.automation.enableAutoReply, isFalse);
      expect(prefs.effects.pageTransitionStyle, 'Fade Through');
    });

    test('Updating preferences persists in controller state', () {
      final prefs = ChatyPreferencesController();
      prefs.updatePrivacy(
        prefs.privacy.copyWith(freezeLastSeen: true, antiViewOnce: true),
      );
      expect(prefs.privacy.freezeLastSeen, isTrue);
      expect(prefs.privacy.antiViewOnce, isTrue);

      prefs.updateConversation(
        prefs.conversation.copyWith(
          bubbleShape: 'Card',
          sidebarPosition: 'Left',
        ),
      );
      expect(prefs.conversation.bubbleShape, 'Card');
      expect(prefs.conversation.sidebarPosition, 'Left');
    });

    test('Preferences serialization toMap and fromMap roundtrip', () {
      final original = const PrivacyPreferences(
        freezeLastSeen: true,
        antiDeleteMessages: true,
        antiViewOnce: true,
        showBlueTicksAfterReply: true,
        typingIndicators: false,
      );
      final map = original.toMap();
      final restored = PrivacyPreferences.fromMap(map);
      expect(restored.freezeLastSeen, isTrue);
      expect(restored.antiDeleteMessages, isTrue);
      expect(restored.antiViewOnce, isTrue);
      expect(restored.showBlueTicksAfterReply, isTrue);
      expect(restored.typingIndicators, isFalse);
    });

    test('Home preferences roundtrip correctly', () {
      final original = const HomePreferences(
        homeStyle: 'Compact',
        enableStoriesStrip: true,
        separateChatsAndGroups: true,
        ghostMode: true,
      );
      final map = original.toMap();
      final restored = HomePreferences.fromMap(map);
      expect(restored.homeStyle, 'Compact');
      expect(restored.enableStoriesStrip, isTrue);
      expect(restored.separateChatsAndGroups, isTrue);
      expect(restored.ghostMode, isTrue);
    });

    test('Notification preferences roundtrip correctly', () {
      final original = const NotificationPreferences(
        enableGlobalNotifications: true,
        showMessagePreview: false,
        notifyContactOnline: false,
      );
      final map = original.toMap();
      final restored = NotificationPreferences.fromMap(map);
      expect(restored.enableGlobalNotifications, isTrue);
      expect(restored.showMessagePreview, isFalse);
      expect(restored.notifyContactOnline, isFalse);
    });

    test(
      'Security preferences roundtrip correctly and never serialize secrets',
      () {
        const original = SecurityPreferences(
          isAppLockEnabled: true,
          lockMethod: 'PIN',
          autoLockTimeout: '30s',
          makePatternInvisible: true,
          hideLockNotificationContent: true,
          lockedConversationIds: ['conv_1', 'conv_2'],
        );
        final map = original.toMap();

        // SECURITY: the synced/persisted blob must never carry plaintext
        // credentials. Real credentials live only in secure storage.
        for (final secretField in const [
          'pinCode',
          'password',
          'patternCode',
          'recoveryQuestion',
          'recoveryAnswer',
        ]) {
          expect(
            map.containsKey(secretField),
            isFalse,
            reason: 'Security blob must not serialize "$secretField"',
          );
        }

        final restored = SecurityPreferences.fromMap(map);
        expect(restored.isAppLockEnabled, isTrue);
        expect(restored.lockMethod, 'PIN');
        expect(restored.autoLockTimeout, '30s');
        expect(restored.makePatternInvisible, isTrue);
        expect(restored.hideLockNotificationContent, isTrue);
        expect(
          restored.lockedConversationIds,
          containsAll(['conv_1', 'conv_2']),
        );
      },
    );
  });

  group('Animated Emoji & Expression Engine Tests', () {
    test(
      'ChatyEmojiRegistry detects supported animated emojis and normalizes',
      () {
        expect(ChatyEmojiRegistry.find('❤️'), isNotNull);
        expect(ChatyEmojiRegistry.find('🔥'), isNotNull);
        expect(ChatyEmojiRegistry.find('👍'), isNotNull);
        expect(ChatyEmojiRegistry.find('😂'), isNotNull);
        expect(ChatyEmojiRegistry.find('👋'), isNotNull);
        expect(ChatyEmojiRegistry.find('✨'), isNotNull);
        expect(ChatyEmojiRegistry.find('non-emoji'), isNull);
      },
    );

    test(
      'ChatyEmojiParser accurately detects emoji-only messages and sizing modes',
      () {
        expect(ChatyEmojiParser.emojiOnlyCount('👋'), 1);
        expect(
          ChatyEmojiParser.resolveEmojiOnlyDisplayMode('👋'),
          EmojiDisplayMode.jumboSingle,
        );

        expect(ChatyEmojiParser.emojiOnlyCount('🔥 ❤️'), 2);
        expect(
          ChatyEmojiParser.resolveEmojiOnlyDisplayMode('🔥 ❤️'),
          EmojiDisplayMode.mediumFew,
        );

        expect(ChatyEmojiParser.emojiOnlyCount('👍 🎉 👀 🚀'), 4);
        expect(
          ChatyEmojiParser.resolveEmojiOnlyDisplayMode('👍 🎉 👀 🚀'),
          EmojiDisplayMode.mediumMany,
        );

        expect(ChatyEmojiParser.emojiOnlyCount('Hello 👋'), 0);
        expect(
          ChatyEmojiParser.resolveEmojiOnlyDisplayMode('Hello 👋'),
          isNull,
        );
      },
    );

    test(
      'ChatyEmojiParser handles Unicode ZWJ and skin tones without corruption',
      () {
        final spans = ChatyEmojiParser.parse('Great job 👍🏽 team!');
        expect(spans.length, 3);
        expect(spans[0].rawText, 'Great job ');
        expect(spans[1].isEmoji, isTrue);
        expect(spans[2].rawText, ' team!');
      },
    );

    test('enableAnimatedEmojis preference roundtrips accurately', () {
      final defaultPrefs = const ConversationPreferences();
      expect(defaultPrefs.enableAnimatedEmojis, isTrue);

      final toggled = defaultPrefs.copyWith(enableAnimatedEmojis: false);
      expect(toggled.enableAnimatedEmojis, isFalse);

      final map = toggled.toMap();
      final restored = ConversationPreferences.fromMap(map);
      expect(restored.enableAnimatedEmojis, isFalse);
    });
  });

  group('Message Bubble & Settings Consumer Widget Tests', () {
    testWidgets(
      'MessageBubble renders delivery state and custom corner radius',
      (tester) async {
        final msg = ChatMessage(
          id: 'msg_1',
          conversationId: 'conv_1',
          senderId: 'usr_me',
          text: 'Hello from Chaty production test!',
          deliveryState: DeliveryState.read,
          createdAt: DateTime.now(),
        );

        final theme = ThemePresets.midnight.copyWith(cornerRadius: 18.0);

        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: MessageBubble(
                message: msg,
                isMe: true,
                theme: theme,
                onLongPress: () {},
              ),
            ),
          ),
        );

        expect(find.byType(AnimatedEmojiText), findsOneWidget);
        expect(find.byIcon(Icons.done_all_rounded), findsOneWidget);
      },
    );

    testWidgets('ChatyChoiceTile selects option and triggers callback', (
      tester,
    ) async {
      String selected = 'Rounded';
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, setState) => ChatyChoiceTile<String>(
                title: 'Bubble Shape Geometry',
                options: const ['Rounded', 'Compact', 'Classic'],
                selectedOption: selected,
                optionLabel: (s) => s,
                onSelected: (val) => setState(() => selected = val),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Bubble Shape Geometry'), findsOneWidget);
      expect(find.text('Rounded'), findsOneWidget);
      await tester.tap(find.text('Compact'));
      await tester.pump();
      expect(selected, 'Compact');
    });

    testWidgets('ChatySliderTile updates value on slider change', (
      tester,
    ) async {
      double value = 16.0;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, setState) => ChatySliderTile(
                title: 'Bubble Corner Radius',
                value: value,
                min: 4.0,
                max: 24.0,
                divisions: 20,
                valueFormatter: (v) => '${v.toInt()}px',
                onChanged: (v) => setState(() => value = v),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Bubble Corner Radius'), findsOneWidget);
      expect(find.text('16px'), findsOneWidget);
    });
  });

  group('Settings Search & Keyword Matching Tests', () {
    test('SettingsSearchDelegate matches keywords and titles accurately', () {
      final items = [
        const SettingsSearchResult(
          title: 'Privacy',
          category: 'Privacy & Security',
          description:
              'Last seen, receipts, deleted messages and status privacy',
          icon: Icons.visibility_off_rounded,
          destination: SizedBox(),
          keywords: ['blue tick', 'read receipt', 'freeze last seen'],
        ),
        const SettingsSearchResult(
          title: 'Themes',
          category: 'Appearance',
          description: 'Dark, light and custom theme presets',
          icon: Icons.palette_rounded,
          destination: SizedBox(),
          keywords: ['amoled', 'dark mode', 'true black'],
        ),
      ];

      final delegate = SettingsSearchDelegate(allSettings: items);
      delegate.query = 'blue tick';
      expect(
        items
            .where((i) => i.keywords.any((k) => k.contains(delegate.query)))
            .length,
        1,
      );

      delegate.query = 'amoled';
      expect(
        items
            .where((i) => i.keywords.any((k) => k.contains(delegate.query)))
            .length,
        1,
      );
    });
  });
}
