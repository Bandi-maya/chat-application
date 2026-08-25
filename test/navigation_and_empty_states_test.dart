import 'package:chat/domain/models/preferences.dart';
import 'package:chat/ui/core/design_system/components/chaty_kit.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUpAll(() {
    TestWidgetsFlutterBinding.ensureInitialized();
    SharedPreferences.setMockInitialValues({});
  });

  group('Empty & No Results State Components Tests', () {
    testWidgets('ChatyEmptyState renders title, message, and action button', (
      tester,
    ) async {
      var actionTapped = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ChatyEmptyState(
              icon: Icons.chat_bubble_outline_rounded,
              title: 'No Conversations',
              message: 'Start a chat by inviting a friend.',
              actionLabel: 'Find friends',
              onAction: () => actionTapped = true,
            ),
          ),
        ),
      );

      expect(find.text('No Conversations'), findsOneWidget);
      expect(find.text('Start a chat by inviting a friend.'), findsOneWidget);
      expect(find.byIcon(Icons.chat_bubble_outline_rounded), findsOneWidget);
      expect(find.text('Find friends'), findsOneWidget);

      await tester.tap(find.text('Find friends'));
      expect(actionTapped, isTrue);
    });

    testWidgets('ChatyNoResultsState renders clear action and query title', (
      tester,
    ) async {
      var cleared = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ChatyNoResultsState(
              query: 'xyz123',
              onClear: () => cleared = true,
            ),
          ),
        ),
      );

      expect(find.text('No results for “xyz123”'), findsOneWidget);
      expect(find.text('Clear search'), findsOneWidget);

      await tester.tap(find.text('Clear search'));
      expect(cleared, isTrue);
    });
  });

  group('HomePreferences showDesktopIcon & Navigation Tests', () {
    test('showDesktopIcon defaults to true and serializes accurately', () {
      const prefs = HomePreferences();
      expect(prefs.showDesktopIcon, isTrue);

      final modified = prefs.copyWith(showDesktopIcon: false);
      expect(modified.showDesktopIcon, isFalse);

      final map = modified.toMap();
      expect(map['showDesktopIcon'], isFalse);

      final restored = HomePreferences.fromMap(map);
      expect(restored.showDesktopIcon, isFalse);
    });
  });
}
