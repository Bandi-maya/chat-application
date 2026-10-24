// Render + persistence harness for the 'iOS Animated (Drive)' emoji style:
// 50 bundled WebP smileys imported from the user's private Drive folder.
// Proves selection -> persistence -> asset-backed rendering in the picker
// preview path and the message consumer path, light and dark.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/core/emoji/chaty_emoji_style.dart';
import 'package:chat/core/emoji/chaty_ios_animated_emoji.dart';
import 'package:chat/core/emoji/chaty_motion_emoji.dart';
import 'package:chat/core/emoji/widgets/animated_emoji_view.dart';
import 'package:chat/core/emoji/models/parsed_emoji_span.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';

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

  test('style persists, aliases resolve, and all 50 assets are declared', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = ChatyPreferencesController();
    await Future<void>.delayed(const Duration(milliseconds: 50));
    prefs.updateUniversal(
        prefs.universal.copyWith(emojiStyle: ChatyEmojiStyleId.iosAnimated.title));
    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(prefs.universal.emojiStyle, 'iOS Animated (Drive)');
    final revived = ChatyPreferencesController();
    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(ChatyEmojiStyleId.fromString(revived.universal.emojiStyle),
        ChatyEmojiStyleId.iosAnimated);
    // Legacy aliases keep prior meanings.
    expect(ChatyEmojiStyleId.fromString('iOS'), ChatyEmojiStyleId.chatySoft3D);
    expect(ChatyEmojiStyleId.fromString('iosanimated'),
        ChatyEmojiStyleId.iosAnimated);
    // Pack completeness: 50 unique assets, all present on disk.
    final assets = ChatyIosAnimatedEmoji.assets.values.toSet();
    expect(assets.length, 50);
    for (final a in assets) {
      expect(File(a).existsSync(), isTrue, reason: '$a missing');
    }
    // Coverage sanity: known mappings.
    expect(ChatyIosAnimatedEmoji.isSupported('😂'), isTrue);
    expect(ChatyIosAnimatedEmoji.isSupported('🙃'), isTrue);
    expect(ChatyIosAnimatedEmoji.isSupported('❤️'), isFalse);
  });

  testWidgets('picker preview + message consumer render', (tester) async {
    final dir = Directory('build/ui_renders')..createSync(recursive: true);
    SharedPreferences.setMockInitialValues({
      'universal_preferences':
          '{"emojiStyle":"iOS Animated (Drive)"}',
    });
    final prefs = ChatyPreferencesController();
    await Future<void>.delayed(const Duration(milliseconds: 60));

    Future<void> capture(String name, Widget child, Brightness b) async {
      final key = GlobalKey();
      final isDark = b == Brightness.dark;
      await tester.pumpWidget(
        MaterialApp(
          theme: ThemeData(brightness: b, fontFamily: 'Roboto'),
          home: RepaintBoundary(
            key: key,
            child: Scaffold(
              backgroundColor:
                  isDark ? const Color(0xFF0B141A) : const Color(0xFFFFFFFF),
              body: Padding(padding: const EdgeInsets.all(16), child: child),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 400));
      expect(tester.takeException(), isNull, reason: '$name must render');
      await tester.runAsync(() async {
        final boundary =
            key.currentContext!.findRenderObject() as RenderRepaintBoundary;
        final image = await boundary.toImage(pixelRatio: 1);
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        File('${dir.path}/$name.png')
            .writeAsBytesSync(data!.buffer.asUint8List());
      });
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(milliseconds: 50));
    }

    // Picker preview row: every style renders its sample emojis.
    Widget previewRow(Brightness b) => Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (final style in ChatyEmojiStyleId.values)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Row(
                  children: [
                    SizedBox(
                      width: 150,
                      child: Text(style.title,
                          style: TextStyle(
                              fontSize: 12,
                              color: b == Brightness.dark
                                  ? Colors.white
                                  : Colors.black)),
                    ),
                    ChatyMotionEmoji(
                        unicode: '😂', size: 28, style: style, animate: false),
                    const SizedBox(width: 8),
                    ChatyMotionEmoji(
                        unicode: '😍', size: 28, style: style, animate: false),
                    const SizedBox(width: 8),
                    ChatyMotionEmoji(
                        unicode: '❤️', size: 28, style: style, animate: false),
                  ],
                ),
              ),
          ],
        );

    // Message consumer: pack-covered emojis + fallback, via AnimatedEmojiView.
    Widget messageLike(Brightness b) {
      final bubble = (String e) => Container(
            margin: const EdgeInsets.symmetric(vertical: 4),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: b == Brightness.dark
                  ? const Color(0xFF005C4B)
                  : const Color(0xFFD9FDD3),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              AnimatedEmojiView(unicode: e, size: 34, mode: EmojiDisplayMode.inline),
            ]),
          );
      return Column(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [for (final e in ['😂', '🤣', '😍', '😎', '🙃', '❤️']) bubble(e)],
      );
    }

    for (final b in [Brightness.light, Brightness.dark]) {
      final suffix = b == Brightness.dark ? 'dark' : 'light';
      await capture('gb_ios_emoji_picker_$suffix', previewRow(b), b);
      await capture('gb_ios_emoji_message_$suffix', messageLike(b), b);
    }
  });
}
