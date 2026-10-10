import 'package:chat/core/emoji/widgets/animated_emoji_text.dart';
import 'package:chat/features/settings/theme_editor_screen.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/gb/gb_theme_overrides.dart';
import 'package:chat/ui/core/theme/chaty_theme_manager.dart';
import 'package:chat/ui/core/theme/theme_config.dart';
import 'package:chat/ui/core/theme/theme_controller.dart';
import 'package:chat/ui/core/theme/theme_presets.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUpAll(() {
    TestWidgetsFlutterBinding.ensureInitialized();
    SharedPreferences.setMockInitialValues({});
  });

  group('Theme System & GBThemes Comprehensive Tests', () {
    test('All registered presets have unique IDs and valid contrast', () {
      final presets = ThemePresets.all;
      final ids = <String>{};
      for (final p in presets) {
        expect(ids.add(p.id), isTrue, reason: 'Duplicate preset id ${p.id}');
        expect(p.hasContrastIssue, isFalse, reason: 'Preset ${p.name} has contrast issue');
      }
    });

    test('ThemeConfig serialization and deserialization preserves all tokens', () {
      const original = ThemePresets.midnight;
      final map = original.toMap();
      final restored = ThemeConfig.fromMap(map);

      expect(restored.id, original.id);
      expect(restored.name, original.name);
      expect(restored.brightness, original.brightness);
      expect(restored.accentColor.toARGB32(), original.accentColor.toARGB32());
      expect(restored.backgroundColor.toARGB32(), original.backgroundColor.toARGB32());
      expect(restored.surfaceColor.toARGB32(), original.surfaceColor.toARGB32());
      expect(restored.cardColor.toARGB32(), original.cardColor.toARGB32());
      expect(restored.outgoingBubbleColor.toARGB32(), original.outgoingBubbleColor.toARGB32());
      expect(restored.incomingBubbleColor.toARGB32(), original.incomingBubbleColor.toARGB32());
      expect(restored.cornerRadius, original.cornerRadius);
      expect(restored.fontScale, original.fontScale);
      expect(restored.density, original.density);
      expect(restored.deliveryTickStyle, original.deliveryTickStyle);
    });

    test('ChatyThemeManager rejects empty, oversized, or unreadable theme JSON', () {
      // Empty
      expect(
        () => ChatyThemeManager.validateAndImportTheme(''),
        throwsA(isA<FormatException>()),
      );

      // Oversized (>1 MB)
      final huge = '{"theme": ${" " * (1024 * 1025)}}';
      expect(
        () => ChatyThemeManager.validateAndImportTheme(huge),
        throwsA(isA<FormatException>()),
      );

      // Unreadable contrast (text color == background color)
      final badContrastTheme = {
        'schemaVersion': 1,
        'theme': {
          'id': 'bad_theme',
          'name': 'Bad Theme',
          'brightness': 'dark',
          'backgroundColor': Colors.black.toARGB32(),
          'primaryTextColor': Colors.black.toARGB32(),
        },
      };
      expect(
        () => ChatyThemeManager.validateAndImportTheme(
          badContrastTheme.toString(),
        ),
        throwsA(isA<FormatException>()),
      );
    });

    test('ChatyThemeManager correctly exports and roundtrips a valid ThemeConfig', () {
      final exported = ChatyThemeManager.exportTheme(ThemePresets.paper);
      expect(exported, contains('"schemaVersion": 1'));
      expect(exported, contains('"name": "Paper"'));

      final imported = ChatyThemeManager.validateAndImportTheme(exported);
      expect(imported.id, ThemePresets.paper.id);
      expect(imported.name, ThemePresets.paper.name);
      expect(imported.brightness, Brightness.light);
    });

    test('ThemeController updates, persists, and resets to system defaults cleanly', () async {
      final controller = ThemeController();
      await controller.init();

      // Update to a non-default theme
      controller.setGlobalTheme(ThemePresets.ocean);
      expect(controller.globalTheme.id, ThemePresets.ocean.id);

      // Reset
      controller.resetToDefaults();
      final def = ThemePresets.getSystemDefaultTheme();
      expect(controller.globalTheme.id, def.id);
      expect(controller.fontScale, 1.0);
      expect(controller.density, 1.0);
    });

    test('GbThemeOverrides preserves readability when user selects extreme colors', () {
      final prefs = ChatyPreferencesController();
      // Set an unreadable text color override matching the canvas
      prefs.updateGbFeature('ModConTextColor', Colors.black.toARGB32());
      prefs.updateGbFeature('ModConBackColor', Colors.black.toARGB32());

      final base = ThemePresets.monochromeDark;
      final resolved = GbThemeOverrides.resolve(base, prefs);

      // Verify that contrast auto-repair prevented zero contrast
      expect(resolved.hasContrastIssue, isFalse);
      expect(
        ThemeConfig.calculateContrastRatio(
          resolved.primaryTextColor,
          resolved.backgroundColor,
        ),
        greaterThanOrEqualTo(3.5),
      );
    });

    testWidgets('ThemeEditorScreen renders presets, fine-tuning controls, and preview', (tester) async {
      tester.view.physicalSize = const Size(800, 1400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final controller = ThemeController();
      await controller.init();

      await tester.pumpWidget(
        MaterialApp(
          home: ThemeEditorScreen(themeController: controller),
        ),
      );
      await tester.pumpAndSettle();

      // Live bubble preview exists (rendered via AnimatedEmojiText)
      expect(find.byType(AnimatedEmojiText), findsAtLeastNWidgets(2));

      // Fine-tuning section exists
      expect(find.text('FINE-TUNING & GEOMETRY'), findsOneWidget);
      expect(find.byType(Slider), findsNWidgets(3)); // corner radius, font scale, density

      // Preset grid exists
      expect(find.textContaining('THEME PRESETS'), findsOneWidget);
      expect(find.text('Reset to Default System Theme'), findsOneWidget);
    });
  });
}
