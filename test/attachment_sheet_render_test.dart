// Render harness for AttachmentSheet (test render, not a device screenshot).
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/core/emoji/widgets/chaty_emoji_picker.dart';
import 'package:chat/features/messages/attachment_sheet.dart';
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
    for (final fam in ['Roboto', 'sans-serif', 'sans-serif-medium', 'sans-serif-rounded']) {
      await _loadFont(fam, 'Roboto-Regular.ttf');
    }
    await _loadFont('MaterialIcons', 'MaterialIcons-Regular.otf');
  });
  for (final custom in [false, true]) {
    testWidgets('emoji picker ${custom ? "custom colours" : "defaults"}', (tester) async {
      tester.view.physicalSize = const Size(800, 1400);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      final theme = ThemeController()..syncWithSystem(Brightness.light);
      final key = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: theme.globalTheme.toThemeData().copyWith(
              textTheme: theme.globalTheme.toThemeData().textTheme.apply(fontFamily: 'Roboto'),
            ),
            home: Builder(
              builder: (context) => Scaffold(
                body: Center(
                  child: TextButton(
                    onPressed: () => ChatyEmojiPicker.show(
                      context,
                      headerColor: custom ? const Color(0xFF7B1FA2) : null,
                      headerIconsColor: custom ? Colors.white : null,
                      backgroundColor: custom ? const Color(0xFFFFF3E0) : null,
                    ),
                    child: const Text('open'),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      for (var i = 0; i < 12; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      await tester.runAsync(() async {
        final boundary = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
        final image = await boundary.toImage(pixelRatio: 1);
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        final dir = Directory('build/ui_renders')..createSync(recursive: true);
        File('${dir.path}/emoji_picker_${custom ? "custom" : "default"}.png').writeAsBytesSync(data!.buffer.asUint8List());
      });
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 5));
    });
  }

  for (final b in [Brightness.light, Brightness.dark]) {
    testWidgets('attachment sheet ${b.name}', (tester) async {
      tester.view.physicalSize = const Size(800, 1400);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      final theme = ThemeController()..syncWithSystem(b);
      final key = GlobalKey();
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
              body: Align(
                alignment: Alignment.bottomCenter,
                child: AttachmentSheet(
                  theme: theme.globalTheme,
                  onMediaRequested: (_) {},
                  onLocationRequested: () {},
                  onContactRequested: () {},
                  onPollRequested: () {},
                  onTaskOption: () {},
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 300));
      await tester.runAsync(() async {
        final boundary = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
        final image = await boundary.toImage(pixelRatio: 1);
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        final dir = Directory('build/ui_renders')..createSync(recursive: true);
        File('${dir.path}/attachment_sheet_${b.name}.png').writeAsBytesSync(data!.buffer.asUint8List());
      });
    });
  }
}
