// Render harness for the six GB home layouts (ui_home_styleV3). Pumps the
// real MainNavigationShell with the Home style persisted in preferences,
// proving selection -> persistence -> shell presentation, light and dark.
// Test renders with SDK fonts, not device screenshots.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:chat/features/chats/main_navigation_shell.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/data/repositories/chaty_data_store.dart';
import 'package:chat/data/services/backend_service.dart';
import 'package:chat/data/services/connection_health_service.dart';
import 'package:chat/data/services/rich_chat_realtime_service.dart';
import 'package:chat/data/services/notification_service.dart';
import 'package:chat/ui/core/controllers/appearance_variant_controller.dart';
import 'package:chat/ui/core/controllers/home_preset_normalizer.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/templates/template_controller.dart';
import 'package:chat/ui/core/theme/theme_config.dart';
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


/// Returns [style] with its textStyle forced to the Roboto family, mirroring
/// the device system-font fallback that flutter_test lacks.
ButtonStyle? _withRobotoButtonStyle(ButtonStyle? style) {
  if (style == null) return null;
  final textStyle = style.textStyle;
  if (textStyle == null) return style;
  return style.copyWith(
    textStyle: WidgetStatePropertyAll<TextStyle?>(
      textStyle.resolve(const <WidgetState>{})?.copyWith(fontFamily: 'Roboto'),
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const gbHomeStyles = <String, AppNavigationMode>{
    'WhatsApp OLD UI': AppNavigationMode.topWhatsAppBar,
    'ONE UI': AppNavigationMode.oneUi,
    'IOS STYLE': AppNavigationMode.iosStyle,
    'BUBBLES TAB STYLE': AppNavigationMode.gestureTabs,
    'BASIC TAB STYLE': AppNavigationMode.basicTabStyle,
    'WhatsApp UI Stock': AppNavigationMode.bottomNav,
  };

  setUpAll(() async {
    for (final name in ['com.llfbandit.app_links/messages', 'com.llfbandit.app_links/events']) {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(MethodChannel(name), (call) async => null);
    }
    // permission_handler: report notifications granted so the permission
    // sheet never schedules its retry timers in tests.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('flutter.baseflow.com/permissions/methods'),
      (call) async => 1,
    );
    SharedPreferences.setMockInitialValues({});
    try {
      await Supabase.initialize(url: 'http://localhost:54321', anonKey: 'x');
    } catch (_) {}
    // Register Medium as the LAST face for each family: FontLoader maps
    // every face to weight 400 regardless of metadata, so w500-w700 styles
    // (chip labels, CTA buttons) otherwise find no matching face and paint
    // missing-glyph boxes. Last registration wins the fallback.
    for (final fam in ['Roboto', 'sans-serif', 'sans-serif-medium']) {
      await _loadFont(fam, 'Roboto-Regular.ttf');
      await _loadFont(fam, 'Roboto-Medium.ttf');
    }
    await _loadFont('MaterialIcons', 'MaterialIcons-Regular.otf');
    setupLocator();
  });

  ChatyPreferencesController _setHomeStyle(String homeStyle) {
    // Mutate the single registered controller in place: other lazy services
    // hold this instance, so unregister/re-register races are avoided.
    final prefs = locator<ChatyPreferencesController>();
    prefs.updateHome(prefs.home.copyWith(homeStyle: homeStyle));
    return prefs;
  }

  test('home style selection persists and resolves to the GB shell mode', () async {
    SharedPreferences.setMockInitialValues({});
    for (final entry in gbHomeStyles.entries) {
      final a = ChatyPreferencesController();
      await Future<void>.delayed(const Duration(milliseconds: 50));
      a.updateHome(a.home.copyWith(homeStyle: entry.key));
      await Future<void>.delayed(const Duration(milliseconds: 150));
      // Fresh controller over the same storage simulates an app restart.
      final b = ChatyPreferencesController();
      await Future<void>.delayed(const Duration(milliseconds: 150));
      expect(b.home.homeStyle, entry.key, reason: '${entry.key} must persist');
      expect(
        HomePresetNormalizer.navigationMode(b.home.homeStyle),
        entry.value,
        reason: '${entry.key} must resolve to its shell mode',
      );
      a.dispose();
      b.dispose();
      SharedPreferences.setMockInitialValues({});
    }
  });

  for (final b in [Brightness.light, Brightness.dark]) {
    testWidgets('home layouts ${b.name}', (tester) async {
      tester.view.physicalSize = const Size(800, 1700);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      final theme = locator<ThemeController>()..syncWithSystem(b);
      final dir = Directory('build/ui_renders')..createSync(recursive: true);

      for (final style in gbHomeStyles.keys) {
        final prefs = _setHomeStyle(style);
        final key = GlobalKey();
        await tester.pumpWidget(
          RepaintBoundary(
            key: key,
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: () {
                final td = theme.globalTheme.toThemeData();
                // Tests ship no platform font: chip labels resolve to the
                // chipTheme.labelStyle family verbatim (replace semantics),
                // so a familyless production labelStyle paints boxes. On
                // device the same null family resolves to the system font.
                // Mirror that here so renders show what users see.
                return td.copyWith(
                  textTheme: td.textTheme.apply(fontFamily: 'Roboto'),
                  primaryTextTheme: td.primaryTextTheme.apply(
                    fontFamily: 'Roboto',
                  ),
                  chipTheme: td.chipTheme.copyWith(
                    labelStyle: (td.chipTheme.labelStyle ??
                            const TextStyle())
                        .copyWith(fontFamily: 'Roboto'),
                  ),
                  // Same replace semantics for button labels: production
                  // button themes set familyless w700 textStyles that resolve
                  // to the system font on device only.
                  elevatedButtonTheme: ElevatedButtonThemeData(
                    style: _withRobotoButtonStyle(
                      td.elevatedButtonTheme.style,
                    ),
                  ),
                  outlinedButtonTheme: OutlinedButtonThemeData(
                    style: _withRobotoButtonStyle(
                      td.outlinedButtonTheme.style,
                    ),
                  ),
                  textButtonTheme: TextButtonThemeData(
                    style: _withRobotoButtonStyle(
                      td.textButtonTheme.style,
                    ),
                  ),
                  filledButtonTheme: FilledButtonThemeData(
                    style: _withRobotoButtonStyle(
                      td.filledButtonTheme.style,
                    ),
                  ),
                );
              }(),
              home: const MainNavigationShell(),
            ),
          ),
        );
        await tester.pump(const Duration(milliseconds: 600));
        expect(tester.takeException(), isNull);
        expect(prefs.home.homeStyle, style);
        await tester.runAsync(() async {
          final boundary = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
          final image = await boundary.toImage(pixelRatio: 1);
          final data = await image.toByteData(format: ui.ImageByteFormat.png);
          final safe = style.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]+'), '_');
          File('${dir.path}/gb_home_${safe}_${b.name}.png')
              .writeAsBytesSync(data!.buffer.asUint8List());
        });
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump(const Duration(milliseconds: 100));
      }

      // Cancel periodic service timers (typing expiry, connection health)
      // so the pending-timer invariant passes at test end.
      // Dispose to cancel periodic timers, then re-register lazily so the
      // next brightness test resolves fresh instances.
      for (final t in [
        locator<ChatyDataStore>(),
        locator<ConnectionHealthService>(),
        locator<RichChatRealtimeService>(),
      ]) {
        t.dispose();
      }
      await locator.unregister<ChatyDataStore>();
      await locator.unregister<ConnectionHealthService>();
      await locator.unregister<RichChatRealtimeService>();
      locator.registerLazySingleton<ChatyDataStore>(() => ChatyDataStore());
      locator.registerLazySingleton<ConnectionHealthService>(
        () => ConnectionHealthService(),
      );
      locator.registerLazySingleton<RichChatRealtimeService>(
        () => RichChatRealtimeService(
          preferencesController: locator<ChatyPreferencesController>(),
          notificationService: locator<ChatyNotificationService>(),
          backendService: locator<ChatyBackendService>(),
        ),
      );
      await tester.pump(const Duration(seconds: 3));
    });
  }
}
