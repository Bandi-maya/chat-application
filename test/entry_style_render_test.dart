// Render harness for the styled conversation entry bars (test render, not a device screenshot).
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/domain/models/preferences.dart';
import 'package:chat/features/chats/chat_detail_screen.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/templates/template_controller.dart';
import 'package:chat/ui/core/theme/theme_controller.dart';

Future<void> _loadFont(String family, String file) async {
  final root = Platform.environment['FLUTTER_ROOT'] ?? '';
  final f = File('$root/bin/cache/artifacts/material_fonts/$file');
  if (!f.existsSync()) return;
  final bytes = await f.readAsBytes();
  final loader = FontLoader(family)
    ..addFont(Future<ByteData>.value(ByteData.sublistView(Uint8List.fromList(bytes))));
  await loader.load();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    for (final fam in ['Roboto', 'sans-serif', 'sans-serif-medium']) {
      await _loadFont(fam, 'Roboto-Regular.ttf');
    }
    await _loadFont('MaterialIcons', 'MaterialIcons-Regular.otf');
    if (!locator.isRegistered<TemplateController>()) {
      locator.registerSingleton<TemplateController>(TemplateController());
    }
  });

  const styles = [
    'Stock (Chaty Default)',
    'Telegram Modern',
    'iOS Clean Rounded',
    'Hangouts Minimal',
    'Simple Pill',
    'RC-Flat Floating',
    'WhatsApp Classic 3D',
    'Signal Compact Bubble',
  ];

  for (final b in [Brightness.light, Brightness.dark]) {
    testWidgets('entry styles ${b.name}', (tester) async {
      tester.view.physicalSize = const Size(720, 3200);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      final theme = ThemeController()..syncWithSystem(b);
      final key = GlobalKey();
      Widget composer(String style, [String text = 'Hello there']) => ChatComposerForTest(
            theme: theme.globalTheme,
            convPrefs: const ConversationPreferences().copyWith(entryStyle: style),
            controller: TextEditingController(text: text),
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
          );
      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: theme.globalTheme.toThemeData().copyWith(
              textTheme: theme.globalTheme.toThemeData().textTheme.apply(fontFamily: 'Roboto'),
            ),
            home: Scaffold(
              backgroundColor: b == Brightness.dark ? const Color(0xFF0B0B0C) : Colors.white,
              body: Column(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  for (final s in styles) ...[
                    Padding(
                      padding: const EdgeInsets.fromLTRB(8, 10, 8, 0),
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: Text(s, style: const TextStyle(fontSize: 12)),
                      ),
                    ),
                    composer(s),
                    const SizedBox(height: 2),
                    composer(s, ''),
                  ],
                ],
              ),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 400));
      expect(tester.takeException(), isNull);
      await tester.runAsync(() async {
        final boundary = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
        final image = await boundary.toImage(pixelRatio: 1);
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        final dir = Directory('build/ui_renders')..createSync(recursive: true);
        File('${dir.path}/entry_styles_${b.name}.png').writeAsBytesSync(data!.buffer.asUint8List());
      });
    });
  }
}
