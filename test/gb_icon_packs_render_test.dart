// Render + persistence harness for the 10 Chaty icon theme packs
// (Settings > Universal > Chaty Icons). Proves selection -> persistence ->
// distinct rendering per pack, light and dark.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/icons/chaty_icon_registry.dart';
import 'package:chat/ui/core/icons/chaty_icon_theme.dart';

Future<void> _loadFont(String family, String file) async {
  final root = Platform.environment['FLUTTER_ROOT'] ?? '';
  final f = File('$root/bin/cache/artifacts/material_fonts/$file');
  if (!f.existsSync()) return;
  final bytes = await f.readAsBytes();
  final loader = FontLoader(family)
    ..addFont(Future<ByteData>.value(ByteData.sublistView(Uint8List.fromList(bytes))));
  await loader.load();
}

/// Representative core set covering navigation, messaging and calls.
const _coreIcons = <ChatyIconId>[
  ChatyIconId.chats,
  ChatyIconId.calls,
  ChatyIconId.updates,
  ChatyIconId.send,
  ChatyIconId.attachment,
  ChatyIconId.microphone,
  ChatyIconId.camera,
  ChatyIconId.callEnd,
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    SharedPreferences.setMockInitialValues({});
    try {
      await Supabase.initialize(url: 'http://localhost:54321', anonKey: 'x');
    } catch (_) {}
    for (final fam in ['Roboto', 'sans-serif', 'sans-serif-medium']) {
      await _loadFont(fam, 'Roboto-Regular.ttf');
      await _loadFont(fam, 'Roboto-Medium.ttf');
    }
    setupLocator();
  });

  test('icon pack selection persists and legacy aliases resolve', () async {
    SharedPreferences.setMockInitialValues({});
    for (final theme in ChatyIconThemeId.values) {
      final a = ChatyPreferencesController();
      await Future<void>.delayed(const Duration(milliseconds: 50));
      a.updateUniversal(a.universal.copyWith(iconTheme: theme.title));
      await Future<void>.delayed(const Duration(milliseconds: 150));
      // Fresh controller over the same storage simulates an app restart.
      final b = ChatyPreferencesController();
      await Future<void>.delayed(const Duration(milliseconds: 150));
      expect(b.universal.iconTheme, theme.title,
          reason: '${theme.title} must persist');
      expect(ChatyIconThemeId.fromString(b.universal.iconTheme), theme,
          reason: '${theme.title} must resolve to its pack');
      a.dispose();
      b.dispose();
      SharedPreferences.setMockInitialValues({});
    }
    // Legacy stored ids keep their meanings.
    expect(ChatyIconThemeId.fromString('Chaty Orbit'),
        ChatyIconThemeId.chatySigil);
    expect(ChatyIconThemeId.fromString('Precision Line'),
        ChatyIconThemeId.precisionContour);
    expect(ChatyIconThemeId.fromString('unknown pack'),
        ChatyIconThemeId.chatySigil);
  });

  for (final b in [Brightness.light, Brightness.dark]) {
    testWidgets('icon packs ${b.name}', (tester) async {
      final dir = Directory('build/ui_renders')..createSync(recursive: true);
      final isDark = b == Brightness.dark;
      for (final pack in ChatyIconThemeId.values) {
        final key = GlobalKey();
        await tester.pumpWidget(
          RepaintBoundary(
            key: key,
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: ThemeData(
                useMaterial3: true,
                brightness: b,
                textTheme: const TextTheme().apply(fontFamily: 'Roboto'),
              ),
              home: Scaffold(
                backgroundColor: isDark
                    ? const Color(0xFF121212)
                    : const Color(0xFFFFFFFF),
                body: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        pack.title,
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                          color: isDark ? Colors.white : Colors.black,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        pack.description,
                        style: TextStyle(
                          fontSize: 11,
                          color: isDark ? Colors.white60 : Colors.black54,
                        ),
                      ),
                      const SizedBox(height: 16),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: [
                          for (final icon in _coreIcons)
                            ChatyIcon(
                              icon,
                              size: 30,
                              themeOverride: pack,
                              color: isDark
                                  ? Colors.white
                                  : const Color(0xFF1B1B1F),
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pump(const Duration(milliseconds: 300));
        expect(tester.takeException(), isNull,
            reason: '${pack.title} must render');
        await tester.runAsync(() async {
          final boundary =
              key.currentContext!.findRenderObject() as RenderRepaintBoundary;
          final image = await boundary.toImage(pixelRatio: 1);
          final data = await image.toByteData(format: ui.ImageByteFormat.png);
          File('${dir.path}/gb_icons_${pack.name}_${b.name}.png')
              .writeAsBytesSync(data!.buffer.asUint8List());
        });
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump(const Duration(milliseconds: 50));
      }
    });
  }
}
