// Render harness: pumps the REAL SettingsRootScreen. Flutter test uses the
// block "Ahem" font, so text appears as solid blocks: layout is real, glyphs
// are not. Not a device screenshot.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
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

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  for (final b in [Brightness.light, Brightness.dark]) {
    testWidgets('settings root ${b.name}', (tester) async {
      SharedPreferences.setMockInitialValues({});
      try {
        await Supabase.initialize(url: 'http://localhost:54321', anonKey: 'x');
      } catch (_) {}
      if (!locator.isRegistered<AppIconController>()) {
        locator.registerLazySingleton<AppIconController>(() => AppIconController());
      }
      tester.view.physicalSize = const Size(800, 3600);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      final theme = ThemeController()..syncWithSystem(b);
      final key = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: theme.globalTheme.toThemeData(),
            home: SettingsRootScreen(
              preferencesController: ChatyPreferencesController(),
              themeController: theme,
              dataStore: ChatyDataStore(),
              notificationService: ChatyNotificationService(),
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
    });
  }
}
