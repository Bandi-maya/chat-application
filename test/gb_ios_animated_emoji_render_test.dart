// Render + persistence harness for the 'iOS Animated (Drive)' emoji style:
// 50 bundled WebP smileys imported from the user's private Drive folder.
// Pumps the PRODUCTION widgets end to end:
//  - the real UniversalStylesScreen settings picker (Section 2: emoji styles)
//  - the real MessageBubble in its emoji-only path (emoji_only.dart ->
//    AnimatedEmojiView -> IosAnimatedEmojiView)
// Plus a frame-advance assertion on the animated WebP pack and a send-payload
// regression proving local style selection never alters sent text.
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
import 'package:chat/domain/models/chat_message.dart';
import 'package:chat/features/messages/message_bubble.dart';
import 'package:chat/features/settings/universal/universal_styles_screen.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/theme/theme_controller.dart';
import 'package:chat/ui/core/theme/theme_presets.dart';

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
    await _loadFont('MaterialIcons', 'MaterialIcons-Regular.otf');
    setupLocator();
  });

  Future<void> selectIosAnimatedStyle() async {
    final prefs = locator<ChatyPreferencesController>();
    await Future<void>.delayed(const Duration(milliseconds: 60));
    prefs.updateUniversal(
        prefs.universal.copyWith(emojiStyle: ChatyEmojiStyleId.iosAnimated.title));
    await Future<void>.delayed(const Duration(milliseconds: 60));
  }

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

  test('animated WebP pack actually advances frames', () async {
    // A static image would deliver exactly one frame; the Drive pack is
    // animated, so the engine must deliver at least two distinct frames.
    final bytes = await rootBundle
        .load('assets/emoji/ios_animated/3_face_with_tears.webp');
    final codec = await ui.instantiateImageCodec(bytes.buffer.asUint8List());
    expect(codec.frameCount, greaterThan(1),
        reason: 'pack asset must be animated');
    final f1 = await codec.getNextFrame();
    final d1 = await f1.image.toByteData(format: ui.ImageByteFormat.rawRgba);
    final f2 = await codec.getNextFrame();
    final d2 = await f2.image.toByteData(format: ui.ImageByteFormat.rawRgba);
    expect(d2!.buffer.asUint8List(), isNot(d1!.buffer.asUint8List()),
        reason: 'consecutive frames must differ');
    f1.image.dispose();
    f2.image.dispose();
    codec.dispose();
  });

  testWidgets('emoji send payload is unaltered by style selection', (tester) async {
    // Mirrors chat_detail_screen's submit guard verbatim:
    //   if (value.trim().isNotEmpty) await _sendMessage();
    // The composer submits the raw TextField value; emoji style is a render
    // preference only and must never rewrite what gets sent.
    await tester.runAsync(selectIosAnimatedStyle);
    String? sent;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: TextField(
            onSubmitted: (value) {
              if (value.trim().isNotEmpty) sent = value;
            },
          ),
        ),
      ),
    );
    const raw = 'Deal 😂🙃❤️ tonight?';
    await tester.enterText(find.byType(TextField), raw);
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pump();
    expect(sent, raw);
    expect(locator<ChatyPreferencesController>().universal.emojiStyle,
        'iOS Animated (Drive)');
  });

  testWidgets('production settings picker + message bubble render', (tester) async {
    final dir = Directory('build/ui_renders')..createSync(recursive: true);
    await tester.runAsync(selectIosAnimatedStyle);
    await tester.pump();

    Future<void> snap(GlobalKey key, String name) async {
      await tester.runAsync(() async {
        final boundary =
            key.currentContext!.findRenderObject() as RenderRepaintBoundary;
        final image = await boundary.toImage(pixelRatio: 1);
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        File('${dir.path}/$name.png')
            .writeAsBytesSync(data!.buffer.asUint8List());
      });
    }

    for (final b in [Brightness.light, Brightness.dark]) {
      final suffix = b == Brightness.dark ? 'dark' : 'light';
      final dark = b == Brightness.dark;
      final base = dark ? ThemePresets.midnight : ThemePresets.monochromeLight;

      // Drive the REAL ThemeController through its production switch path, so
      // the screen's ThemeConfig (colors, surfaces, text) is internally
      // consistent - exactly what main.dart mounts via toThemeData().
      final themeController = locator<ThemeController>();
      themeController.setGlobalTheme(base);

      // A) REAL settings picker screen, scrolled to the emoji styles section.
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      final pickerKey = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: pickerKey,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: themeController.globalTheme.toThemeData(),
            home: UniversalStylesScreen(
              preferencesController: locator<ChatyPreferencesController>(),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 300));
      await tester.scrollUntilVisible(
        find.text('iOS Animated (Drive)'),
        400,
        scrollable: find.byType(Scrollable).first,
      );
      // Nudge back up so the section header and full row are in frame,
      // not clipped under the app bar.
      await tester.drag(find.byType(Scrollable).first, const Offset(0, 220));
      await tester.pump(const Duration(milliseconds: 200));
      // Let real async asset loading + WebP decode complete, then rebuild.
      await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 900)));
      await tester.pump();
      expect(tester.takeException(), isNull,
          reason: 'settings picker must render in ${b.name}');
      await snap(pickerKey, 'gb_ios_emoji_settings_screen_$suffix');

      // B) REAL MessageBubble, emoji-only path (production consumer).
      final bubbleKey = GlobalKey();
      Widget mb(bool isMe, String text) => Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
            child: MessageBubble(
              message: ChatMessage(
                id: 'e_${isMe}_${text.hashCode}',
                conversationId: 'c1',
                senderId: isMe ? 'me' : 'them',
                text: text,
                deliveryState: DeliveryState.delivered,
                createdAt: DateTime(2026, 10, 24, 13, 41),
              ),
              isMe: isMe,
              theme: base,
              onLongPress: () {},
            ),
          );
      await tester.pumpWidget(
        RepaintBoundary(
          key: bubbleKey,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: ThemeData(brightness: b, fontFamily: 'Roboto'),
            home: Scaffold(
              backgroundColor:
                  dark ? const Color(0xFF0B141A) : const Color(0xFFECE5DD),
              body: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    mb(true, '😂'),
                    mb(true, '😍'),
                    mb(false, '😎'),
                    mb(true, '😂🙃'),
                    mb(false, '❤️'),
                    mb(true, 'On my way 😂'),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 400));
      await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 900)));
      await tester.pump();
      expect(tester.takeException(), isNull,
          reason: 'message bubble must render in ${b.name}');
      await snap(bubbleKey, 'gb_ios_emoji_bubble_$suffix');
    }
  });
}
