import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/design_system/components/chaty_modal.dart';
import 'package:chat/core/emoji/services/emoji_recent_cache.dart';
import 'package:chat/data/services/chaty_share_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('ChatyModal Primitives', () {
    test('kChatyModalRadius is strictly 10.0 px', () {
      expect(kChatyModalRadius, 10.0);
    });

    testWidgets('ChatyModal renders header, content, and footer with 10px radius', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ChatyModal(
              header: const ChatyModalHeader(
                title: 'Test Modal',
                subtitle: 'A test modal subtitle',
              ),
              content: const ChatyModalContent(
                child: Text('Modal Body Text'),
              ),
              footer: ChatyModalFooter(
                primaryAction: TextButton(
                  onPressed: () {},
                  child: const Text('Confirm'),
                ),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Test Modal'), findsOneWidget);
      expect(find.text('A test modal subtitle'), findsOneWidget);
      expect(find.text('Modal Body Text'), findsOneWidget);
      expect(find.text('Confirm'), findsOneWidget);

      final containerFinder = find.byType(Container).first;
      final Container container = tester.widget(containerFinder);
      final decoration = container.decoration as BoxDecoration;
      expect(decoration.borderRadius, BorderRadius.circular(10.0));
    });

    testWidgets('ChatyToggleRow toggles value via entire row click without double-fire', (tester) async {
      bool toggleValue = false;
      int callCount = 0;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, setState) {
                return ChatyToggleRow(
                  title: 'Test Setting',
                  subtitle: 'Enable feature',
                  value: toggleValue,
                  onChanged: (val) {
                    callCount++;
                    setState(() {
                      toggleValue = val;
                    });
                  },
                );
              },
            ),
          ),
        ),
      );

      expect(find.text('Test Setting'), findsOneWidget);
      expect(toggleValue, isFalse);
      expect(callCount, 0);

      // Tap on title text (entire row)
      await tester.tap(find.text('Test Setting'));
      await tester.pumpAndSettle();

      expect(toggleValue, isTrue);
      expect(callCount, 1);

      // Tap on the Switch widget
      await tester.tap(find.byType(Switch));
      await tester.pumpAndSettle();

      expect(toggleValue, isFalse);
      expect(callCount, 2);
    });
  });

  group('EmojiRecentCache', () {
    test('Stores and retrieves emojis, enforcing max bounded capacity of 32', () async {
      final cache = EmojiRecentCache.instance;

      // Add 40 different emojis
      for (int i = 0; i < 40; i++) {
        await cache.recordUsage('emoji_$i', label: 'Emoji $i');
      }

      final recents = cache.items;
      expect(recents.length, 32);
      // Most recently added is first
      expect(recents.first.unicode, 'emoji_39');
      expect(recents.first.label, 'Emoji 39');
      // Oldest retained is emoji_8
      expect(recents.last.unicode, 'emoji_8');
    });
  });

  group('ChatyShareService', () {
    test('Default Play Store URL and share copy are well-formed', () {
      expect(ChatyShareService.playStoreUrl, contains('play.google.com/store/apps/details?id='));
      expect(ChatyShareService.shareMessage, contains('Chaty'));
      expect(ChatyShareService.shareMessage, contains(ChatyShareService.playStoreUrl));
    });
  });
}
