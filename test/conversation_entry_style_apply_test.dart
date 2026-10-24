import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/domain/models/preferences.dart';
import 'package:chat/features/chats/chat_detail_screen.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/icons/chaty_icon_registry.dart';
import 'package:chat/ui/core/templates/template_controller.dart';
import 'package:chat/ui/core/theme/theme_controller.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() {
    if (!locator.isRegistered<TemplateController>()) {
      locator.registerSingleton<TemplateController>(TemplateController());
    }
  });

  Widget buildComposer(ConversationPreferences prefs, Brightness b) {
    final theme = ThemeController()..syncWithSystem(b);
    return MaterialApp(
      home: Scaffold(
        body: ChatComposerForTest(
          theme: theme.globalTheme,
          convPrefs: prefs,
          controller: TextEditingController(),
          focusNode: FocusNode(),
          emojiPickerOpen: false,
          onAttach: () {},
          onCameraTap: () {},
          onEmoji: () {},
          onSend: () {},
          onChanged: (_) {},
          recording: false,
          recordLocked: false,
          voiceBusy: false,
          voiceSeconds: 0,
          onVoiceTap: () {},
          onVoiceHoldStart: () {},
          onVoiceMove: (_) {},
          onVoiceHoldEnd: () {},
          onVoiceCancel: () {},
          onVoiceSend: () {},
        ),
      ),
    );
  }

  const styles = [
    // Legacy aliases that must normalize to 'Stock'.
    'Stock (Chaty Default)',
    'Stock',
    // GB reference entry styles.
    'YOWA Style',
    'WAMOD',
    'Hangouts',
    'Simple',
    'Aran V2',
    'Mood',
    'WANH',
    'Hangouts V2',
    'Dribble',
    'Dribble V2',
    'Allo',
    'Rounded',
    'Old Stock (2016)',
    'RC-MSG Google',
    'RC-Telegram X iOS',
    'RC-IOS 11',
    'RC-Line',
    'Concept',
    'Instagram',
    'iOS 14',
    'FB Messenger',
    'RC Mess',
    // Chaty's own styles.
    'Telegram Modern',
    'iOS Clean Rounded',
    'Hangouts Minimal',
    'Simple Pill',
    'RC-Flat Floating',
    'WhatsApp Classic 3D',
    'Signal Compact Bubble',
  ];

  for (final style in styles) {
    testWidgets('composer renders without exception for "$style"', (
      tester,
    ) async {
      await tester.pumpWidget(
        buildComposer(
          const ConversationPreferences().copyWith(entryStyle: style),
          Brightness.light,
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull);
      expect(find.byType(TextField), findsOneWidget);
      // Composer actions are registry icons with unique ChatyIconId ids.
      expect(find.byType(ChatyIcon), findsWidgets);
    });
  }

  testWidgets('styled layouts differ from stock arrangement', (tester) async {
    // iOS style: filled attach button sits outside the field, so the field
    // has neither prefix nor suffix icons.
    await tester.pumpWidget(
      buildComposer(
        const ConversationPreferences().copyWith(
          entryStyle: 'iOS Clean Rounded',
        ),
        Brightness.light,
      ),
    );
    await tester.pump();
    expect(tester.takeException(), isNull);
    final field = tester.widget<TextField>(find.byType(TextField));
    expect(field.decoration?.prefixIcon, isNull);
    expect(field.decoration?.suffixIcon, isNull);

    // Stock keeps the emoji prefix inside the field.
    await tester.pumpWidget(
      buildComposer(const ConversationPreferences(), Brightness.light),
    );
    await tester.pump();
    final stockField = tester.widget<TextField>(find.byType(TextField));
    expect(stockField.decoration?.prefixIcon, isNotNull);
  });
}
