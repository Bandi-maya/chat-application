// Render harness for nav destination icons: before (template ChatyGlyph) vs
// after (Universal Icons registry) across two selected themes, plus
// selection-update and persistence checks across nav and composer.
// Test renders with SDK fonts, not device screenshots.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:chat/domain/models/preferences.dart';
import 'package:chat/features/chats/chat_detail_screen.dart';
import 'package:chat/features/chats/main_navigation_shell.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/design_system/design_system.dart';
import 'package:chat/ui/core/templates/template_controller.dart';
import 'package:chat/ui/core/templates/template_models.dart';

Future<void> _loadFont(String family, String file) async {
  final root = Platform.environment['FLUTTER_ROOT'] ?? '';
  final f = File('$root/bin/cache/artifacts/material_fonts/$file');
  if (!f.existsSync()) return;
  final bytes = await f.readAsBytes();
  final loader = FontLoader(family)
    ..addFont(Future<ByteData>.value(ByteData.sublistView(Uint8List.fromList(bytes))));
  await loader.load();
}

List<NavDestinationItemForTest> _navItems() => const [
      NavDestinationItemForTest(
        id: ChatyNavigationDestinationIds.chats,
        label: 'Chats',
        icon: ChatyGlyph.chatBubble,
        activeIcon: ChatyGlyph.chatBubble,
      ),
      NavDestinationItemForTest(
        id: ChatyNavigationDestinationIds.groups,
        label: 'Groups',
        icon: ChatyGlyph.groups,
        activeIcon: ChatyGlyph.groups,
      ),
      NavDestinationItemForTest(
        id: ChatyNavigationDestinationIds.updates,
        label: 'Updates',
        icon: ChatyGlyph.updates,
        activeIcon: ChatyGlyph.updates,
      ),
      NavDestinationItemForTest(
        id: ChatyNavigationDestinationIds.tasks,
        label: 'Tasks',
        icon: ChatyGlyph.tasks,
        activeIcon: ChatyGlyph.tasks,
      ),
      NavDestinationItemForTest(
        id: ChatyNavigationDestinationIds.calls,
        label: 'Calls',
        icon: ChatyGlyph.calls,
        activeIcon: ChatyGlyph.calls,
      ),
      NavDestinationItemForTest(
        id: ChatyNavigationDestinationIds.settings,
        label: 'Settings',
        icon: ChatyGlyph.settings,
        activeIcon: ChatyGlyph.settings,
      ),
      NavDestinationItemForTest(
        id: ChatyNavigationDestinationIds.desktop,
        label: 'Desktop',
        icon: ChatyGlyph.devices,
        activeIcon: ChatyGlyph.devices,
      ),
    ];

Widget _navRow({
  required bool useRegistry,
  required Color background,
  required Color iconColor,
}) {
  final items = _navItems();
  return Container(
    width: 700,
    color: background,
    padding: const EdgeInsets.symmetric(vertical: 10),
    child: Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        for (final item in items)
          useRegistry
              ? NavDestinationIconForTest(item, selected: false, size: 26, color: iconColor)
              : ChatyGlyphIcon(glyph: item.icon, size: 26, color: iconColor),
      ],
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    for (final name in ['com.llfbandit.app_links/messages', 'com.llfbandit.app_links/events']) {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(MethodChannel(name), (call) async => null);
    }
    SharedPreferences.setMockInitialValues({});
    try {
      await Supabase.initialize(url: 'http://localhost:54321', anonKey: 'x');
    } catch (_) {}
    for (final fam in ['Roboto', 'sans-serif', 'sans-serif-medium']) {
      await _loadFont(fam, 'Roboto-Regular.ttf');
    }
    await _loadFont('MaterialIcons', 'MaterialIcons-Regular.otf');
    if (!locator.isRegistered<TemplateController>()) {
      locator.registerSingleton<TemplateController>(TemplateController());
    }
  });

  Future<ChatyPreferencesController> freshPrefs() async {
    if (locator.isRegistered<ChatyPreferencesController>()) {
      locator<ChatyPreferencesController>().dispose();
      locator.unregister<ChatyPreferencesController>();
    }
    final prefs = ChatyPreferencesController();
    locator.registerSingleton<ChatyPreferencesController>(prefs);
    // Dispose before test end so the remote-sync debounce Timer is cancelled
    // and not reported as a pending test timer.
    addTearDown(() async {
      if (locator.isRegistered<ChatyPreferencesController>()) {
        locator<ChatyPreferencesController>().dispose();
        locator.unregister<ChatyPreferencesController>();
      }
    });
    return prefs;
  }

  Widget wrap(ThemeController theme, Widget child, {Color? background}) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: theme.globalTheme.toThemeData().copyWith(
            textTheme: theme.globalTheme.toThemeData().textTheme.apply(fontFamily: 'Roboto'),
          ),
      home: Scaffold(backgroundColor: background ?? Colors.white, body: Center(child: child)),
    );
  }

  Future<Uint8List> capture(WidgetTester tester, GlobalKey key) async {
    Uint8List? out;
    await tester.runAsync(() async {
      final boundary = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
      final image = await boundary.toImage(pixelRatio: 1);
      final data = await image.toByteData(format: ui.ImageByteFormat.png);
      out = Uint8List.fromList(data!.buffer.asUint8List());
    });
    return out!;
  }

  testWidgets('nav icons: before glyph vs registry under two themes', (tester) async {
    tester.view.physicalSize = const Size(1400, 300);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.reset);
    final theme = ThemeController()..syncWithSystem(Brightness.light);
    await freshPrefs();
    final dir = Directory('build/ui_renders')..createSync(recursive: true);

    Future<Uint8List> shot({required bool registry, required String file}) async {
      final key = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: wrap(
            theme,
            _navRow(
              useRegistry: registry,
              background: Colors.white,
              iconColor: const Color(0xFF1B1C1F),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 300));
      expect(tester.takeException(), isNull);
      final bytes = await capture(tester, key);
      File('${dir.path}/$file').writeAsBytesSync(bytes);
      return bytes;
    }

    final before = await shot(registry: false, file: 'nav_before_glyph.png');
    final sigil = await shot(registry: true, file: 'nav_after_sigil.png');
    expect(sigil, isNot(before), reason: 'registry default theme must differ from template glyph row');

    // Switch selection to Pixel Precision through the real prefs controller.
    final prefs = locator<ChatyPreferencesController>();
    prefs.updateUniversal(prefs.universal.copyWith(iconTheme: 'Pixel Precision'));
    final pixel = await shot(registry: true, file: 'nav_after_pixel_precision.png');
    expect(pixel, isNot(sigil), reason: 'switching icon theme must change nav pixels');
    expect(pixel, isNot(before));
    // Cancel the persist debounce timer before the invariant check runs.
    locator<ChatyPreferencesController>().dispose();
    locator.unregister<ChatyPreferencesController>();
  });

  testWidgets('icon-theme selection updates and persists across composer and nav', (tester) async {
    tester.view.physicalSize = const Size(1400, 500);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.reset);
    final theme = ThemeController()..syncWithSystem(Brightness.light);
    final prefs = await freshPrefs();
    // Establish a known baseline regardless of what earlier tests persisted
    // into the shared mock store.
    prefs.updateUniversal(prefs.universal.copyWith(iconTheme: 'Chaty Sigil'));

    Widget surface() => Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _navRow(
              useRegistry: true,
              background: Colors.white,
              iconColor: const Color(0xFF1B1C1F),
            ),
            const SizedBox(height: 8),
            ChatComposerForTest(
              theme: theme.globalTheme,
              convPrefs: const ConversationPreferences(),
              controller: TextEditingController(text: 'Hello there'),
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
          ],
        );

    final key = GlobalKey();
    await tester.pumpWidget(RepaintBoundary(key: key, child: wrap(theme, surface())));
    await tester.pump(const Duration(milliseconds: 300));
    expect(tester.takeException(), isNull);
    final beforeSwitch = await capture(tester, key);

    prefs.updateUniversal(prefs.universal.copyWith(iconTheme: 'Pixel Precision'));
    await tester.pumpWidget(RepaintBoundary(key: key, child: wrap(theme, surface())));
    await tester.pump(const Duration(milliseconds: 300));
    expect(tester.takeException(), isNull);
    final afterSwitch = await capture(tester, key);
    expect(afterSwitch, isNot(beforeSwitch),
        reason: 'composer + nav must re-render with the newly selected icon theme');

    // Persistence: a brand-new controller over the same local storage must
    // load the saved selection.
    await tester.runAsync(() async => Future<void>.delayed(const Duration(milliseconds: 100)));
    final reloaded = ChatyPreferencesController();
    await tester.runAsync(() async => Future<void>.delayed(const Duration(milliseconds: 300)));
    expect(reloaded.universal.iconTheme, 'Pixel Precision');
    reloaded.dispose();
    // Cancel the persist debounce timer before the invariant check runs.
    locator<ChatyPreferencesController>().dispose();
    locator.unregister<ChatyPreferencesController>();
  });
}
