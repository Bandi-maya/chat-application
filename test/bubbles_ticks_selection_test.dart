// Behaviour test: picking a ticks or bubbles style in the inline galleries
// updates the saved preference at once (selection -> preview -> saved id).
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:chat/features/settings/conversation/conversation_bubble_and_ticks_screen.dart';
import 'package:chat/ui/core/design_system/design_system.dart';
import 'package:chat/ui/core/bubbles/bubble_style_id.dart';
import 'package:chat/ui/core/ticks/delivery_icon_style.dart';
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
    // Initialise outside the fake-async test zone so its refresh timer is not
    // reported as a pending test timer.
    try {
      await Supabase.initialize(url: 'http://localhost:54321', anonKey: 'x');
    } catch (_) {}
    await _loadFont('Roboto', 'Roboto-Regular.ttf');
    await _loadFont('MaterialIcons', 'MaterialIcons-Regular.otf');
  });

  Future<ChatyPreferencesController> pumpScreen(WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
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
    if (locator.isRegistered<ThemeController>()) {
      await locator.unregister<ThemeController>();
    }
    final theme = ThemeController()..syncWithSystem(Brightness.light);
    locator.registerSingleton<ThemeController>(theme);
    final prefs = ChatyPreferencesController();
    await tester.pumpWidget(
      MaterialApp(
        theme: _withFont(theme.globalTheme.toThemeData()),
        home: ConversationBubbleAndTicksScreen(preferencesController: prefs),
      ),
    );
    await tester.pump(const Duration(milliseconds: 400));
    return prefs;
  }

  testWidgets('tapping a ticks option saves that style', (tester) async {
    final prefs = await pumpScreen(tester);
    final gallery = find.byType(ChatyOptionGallery<DeliveryIconStyle>);
    expect(gallery, findsOneWidget);
    final target = DeliveryIconStyle.values[1];
    final item = find.descendant(of: gallery, matching: find.text(target.displayName));
    await tester.scrollUntilVisible(
      item,
      100,
      scrollable: find.descendant(of: gallery, matching: find.byType(Scrollable)).first,
    );
    await tester.tap(item);
    await tester.pump();
    expect(DeliveryIconStyleExtension.fromString(prefs.conversation.tickStyle), target);
    await tester.pump(const Duration(seconds: 1));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('tapping a bubbles option saves that style', (tester) async {
    final prefs = await pumpScreen(tester);
    final gallery = find.byType(ChatyOptionGallery<BubbleStyleId>);
    expect(gallery, findsOneWidget);
    final target = BubbleStyleId.values.firstWhere((id) => id != BubbleStyleId.stock);
    final item = find.descendant(of: gallery, matching: find.text(target.displayName));
    await tester.scrollUntilVisible(
      item,
      100,
      scrollable: find.descendant(of: gallery, matching: find.byType(Scrollable)).first,
    );
    await tester.tap(item);
    await tester.pump();
    expect(BubbleStyleIdExtension.fromString(prefs.conversation.bubbleStyle), target);
    await tester.pump(const Duration(seconds: 1));
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
