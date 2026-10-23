// Render harness for the real chat composer (test render, not a device screenshot).
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
  });
  for (final b in [Brightness.light, Brightness.dark]) {
    testWidgets('composer ${b.name}', (tester) async {
      if (!locator.isRegistered<TemplateController>()) {
        locator.registerSingleton<TemplateController>(TemplateController());
      }
      tester.view.physicalSize = const Size(720, 900);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      final theme = ThemeController()..syncWithSystem(b);
      final key = GlobalKey();
      Widget composer(String text) => ChatComposerForTest(
            theme: theme.globalTheme,
            convPrefs: const ConversationPreferences(),
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
                children: [composer(''), const SizedBox(height: 24), composer('Hello there'), const SizedBox(height: 24), MediaQuery(data: MediaQueryData(size: const Size(320, 900), textScaler: const TextScaler.linear(1.6)), child: SizedBox(width: 320, child: composer('A longer message to check large text'))), const SizedBox(height: 24), MediaQuery(data: MediaQueryData(size: const Size(320, 900), textScaler: const TextScaler.linear(1.6)), child: SizedBox(width: 320, child: composer('')))],
              ),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 400));
      await tester.runAsync(() async {
        final boundary = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
        final image = await boundary.toImage(pixelRatio: 1);
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        final dir = Directory('build/ui_renders')..createSync(recursive: true);
        File('${dir.path}/composer_${b.name}.png').writeAsBytesSync(data!.buffer.asUint8List());
      });
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }
}
