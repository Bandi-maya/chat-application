// Render harness for the Settings studio and editor screens (baseline for the
// Settings UI pass). Same limits as settings_root_render_test: SDK Roboto and
// MaterialIcons, widget-test renders, not device screenshots.
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:chat/features/settings/conversation/conversation_bubble_and_ticks_screen.dart';
import 'package:chat/features/settings/appearance/component_styles_screen.dart';
import 'package:chat/features/settings/home/header_settings_screen.dart';
import 'package:chat/features/settings/home/home_screen_settings_page.dart';
import 'package:chat/features/settings/templates/templates_settings_screen.dart';
import 'package:chat/features/settings/theme_editor_screen.dart';
import 'package:chat/features/settings/theme_studio_screen.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/controllers/appearance_variant_controller.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/customization/customization_controller.dart';
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

ThemeData _withFont(ThemeData t) => t.copyWith(
  textTheme: t.textTheme.apply(fontFamily: 'Roboto'),
  primaryTextTheme: t.primaryTextTheme.apply(fontFamily: 'Roboto'),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    for (final name in ['com.llfbandit.app_links/messages', 'com.llfbandit.app_links/events']) {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(MethodChannel(name), (call) async => null);
    }
    await _loadFont('Roboto', 'Roboto-Regular.ttf');
    for (final fam in ['sans-serif', 'sans-serif-medium', 'sans-serif-rounded', 'sans-serif-condensed']) {
      await _loadFont(fam, 'Roboto-Regular.ttf');
    }
    await _loadFont('MaterialIcons', 'MaterialIcons-Regular.otf');
  });

  final screens = <String, Widget Function(ThemeController, ChatyPreferencesController)>{
    'templates': (t, p) => const TemplatesSettingsScreen(),
    'theme_studio': (t, p) => ThemeStudioScreen(themeController: t, preferencesController: p),
    'theme_editor': (t, p) => ThemeEditorScreen(themeController: t),
    'home': (t, p) => HomeScreenSettingsPage(preferencesController: p),
    'bubbles_ticks': (t, p) => ConversationBubbleAndTicksScreen(preferencesController: p),
    'component_styles': (t, p) => const ComponentStylesScreen(),
    'header': (t, p) => HeaderSettingsScreen(preferencesController: p),
  };

  for (final entry in screens.entries) {
    for (final variant in ['light', 'dark', 'light_large_text']) {
      final b = variant == 'dark' ? Brightness.dark : Brightness.light;
      final scale = variant.endsWith('large_text') ? 1.6 : 1.0;
      testWidgets('${entry.key} $variant', (tester) async {
        SharedPreferences.setMockInitialValues({});
        try {
          await Supabase.initialize(url: 'http://localhost:54321', anonKey: 'x');
        } catch (_) {}
        if (!locator.isRegistered<AppearanceVariantController>()) {
          locator.registerLazySingleton<AppearanceVariantController>(() => AppearanceVariantController());
        }
        if (!locator.isRegistered<CustomizationController>()) {
          locator.registerLazySingleton<CustomizationController>(() => CustomizationController());
        }
        if (!locator.isRegistered<TemplateController>()) {
          locator.registerLazySingleton<TemplateController>(() => TemplateController());
        }
        tester.view.physicalSize = const Size(800, 3000);
        tester.view.devicePixelRatio = 2;
        addTearDown(tester.view.reset);
        // The app resolves its theme through the locator, so register the same
        // brightness-synced controller the MaterialApp uses.
        if (locator.isRegistered<ThemeController>()) {
          await locator.unregister<ThemeController>();
        }
        final theme = ThemeController()..syncWithSystem(b);
        locator.registerSingleton<ThemeController>(theme);
        final prefs = ChatyPreferencesController();
        final key = GlobalKey();
        try {
          await tester.pumpWidget(
            RepaintBoundary(
              key: key,
              child: MaterialApp(
                debugShowCheckedModeBanner: false,
                theme: _withFont(theme.globalTheme.toThemeData()),
                builder: (ctx, child) => MediaQuery(
                  data: MediaQuery.of(ctx).copyWith(textScaler: TextScaler.linear(scale)),
                  child: child!,
                ),
                home: entry.value(theme, prefs),
              ),
            ),
          );
          await tester.pump(const Duration(milliseconds: 400));
          await tester.runAsync(() async {
            final boundary = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
            final image = await boundary.toImage(pixelRatio: 1);
            final data = await image.toByteData(format: ui.ImageByteFormat.png);
            final dir = Directory('build/settings_render')..createSync(recursive: true);
            File('${dir.path}/studio_${entry.key}_$variant.png').writeAsBytesSync(data!.buffer.asUint8List());
          });
        } catch (e, st) {
          // ignore: avoid_print
          print('RENDER_ERROR ${entry.key} $variant: $e\n${st.toString().split('\n').take(6).join('\n')}');
        }
        await tester.pumpWidget(const SizedBox.shrink());
      });
    }
  }
}
