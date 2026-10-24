// Render harness: pumps the REAL SettingsRootScreen. Fonts are the Roboto and
// MaterialIcons files bundled with the Flutter SDK (loaded via FontLoader),
// so glyphs are readable. Not a device screenshot: weights are approximate
// (single Roboto face), no device fonts, no real data.
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:chat/data/repositories/chaty_data_store.dart';
import 'package:chat/data/services/notification_service.dart';
import 'package:chat/features/settings/settings_root_screen.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/controllers/app_icon_controller.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
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
    // Supabase.initialize starts the app_links listener; give it a no-op host.
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
  for (final b in [Brightness.light, Brightness.dark]) {
    testWidgets('settings root ${b.name}', (tester) async {
      SharedPreferences.setMockInitialValues({});
      try {
        await Supabase.initialize(url: 'http://localhost:54321', anonKey: 'x');
      } catch (_) {}
      if (!locator.isRegistered<AppIconController>()) {
        locator.registerLazySingleton<AppIconController>(() => AppIconController());
      }
      tester.view.physicalSize = const Size(800, 5200);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      final theme = ThemeController()..syncWithSystem(b);
      final prefs = ChatyPreferencesController();
      final store = ChatyDataStore();
      final notifications = ChatyNotificationService();
      final key = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: _withFont(theme.globalTheme.toThemeData()),
            home: SettingsRootScreen(
              preferencesController: prefs,
              themeController: theme,
              dataStore: store,
              notificationService: notifications,
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 300));
      await tester.runAsync(() async {
        final boundary = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
        final image = await boundary.toImage(pixelRatio: 1);
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        final dir = Directory('build/settings_render')..createSync(recursive: true);
        File('${dir.path}/chaty_settings_${b.name}.png').writeAsBytesSync(data!.buffer.asUint8List());
      });
      await tester.pumpWidget(const SizedBox.shrink());
      store.dispose();
    });
  }
}
