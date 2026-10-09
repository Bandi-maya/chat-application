import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:chat/ui/core/customization/customization.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('ChatyComponentRegistry Tests', () {
    test('Registry contains all canonical ComponentIds with cataloged variants', () {
      final registry = ChatyComponentRegistry.instance;

      expect(registry.getVariantsFor(ComponentId.navigation).length, greaterThanOrEqualTo(8));
      expect(registry.getVariantsFor(ComponentId.header).length, greaterThanOrEqualTo(6));
      expect(registry.getVariantsFor(ComponentId.composer).length, greaterThanOrEqualTo(6));
      expect(registry.getVariantsFor(ComponentId.bubble).length, greaterThanOrEqualTo(6));
      expect(registry.getVariantsFor(ComponentId.tick).length, greaterThanOrEqualTo(4));
      expect(registry.getVariantsFor(ComponentId.modal).length, greaterThanOrEqualTo(4));
    });

    test('Registry returns default spec when variant id not found', () {
      final registry = ChatyComponentRegistry.instance;
      final defaultId = registry.getDefaultVariantId(ComponentId.navigation);
      expect(defaultId, isNotEmpty);
      final spec = registry.getSpec(ComponentId.navigation, defaultId);
      expect(spec, isNotNull);
      expect(spec!.componentId, equals(ComponentId.navigation));
    });
  });

  group('ComponentVariantResolver Tests', () {
    test('Resolver respects variant context resolution for navigation', () {
      final resolver = ComponentVariantResolver();

      const normalCtx = VariantContext(
        availableWidth: 390.0,
        availableHeight: 844.0,
        brightness: Brightness.light,
        isReducedMotion: false,
      );

      final normalSpec = resolver.resolve(
        componentId: ComponentId.navigation,
        snapshot: CustomizationSnapshot.defaultSnapshot,
        context: normalCtx,
      );
      expect(normalSpec.componentId, equals(ComponentId.navigation));
      expect(normalSpec.variantId, isNotEmpty);
    });

    test('Resolver respects conversation overrides over global snapshot', () {
      final resolver = ComponentVariantResolver();

      const context = VariantContext(
        availableWidth: 390.0,
        availableHeight: 844.0,
        brightness: Brightness.dark,
      );

      final snapshotWithOverride = CustomizationSnapshot.defaultSnapshot.copyWith(
        conversationOverrides: {
          'conv_123': {
            ComponentId.bubble.name: 'ios_like',
          },
        },
      );

      final resolved = resolver.resolve(
        componentId: ComponentId.bubble,
        snapshot: snapshotWithOverride,
        context: context,
        scope: ComponentScope.conversation('conv_123'),
      );

      expect(resolved.componentId, equals(ComponentId.bubble));
      expect(resolved.variantId, equals('ios_like'));
    });
  });

  group('CustomizationSnapshot Serialization & Migration Tests', () {
    test('Snapshot round-trips to and from JSON Map accurately', () {
      const snapshot = CustomizationSnapshot(
        schemaVersion: 1,
        globalThemeId: 'emerald_matrix',
        navigationVariant: 'floating_pill_nav',
        composerVariant: 'glass_float_composer',
        conversationOverrides: {
          'conv_123': {
            'bubble': 'cyberpunk_neon',
          },
        },
      );

      final json = snapshot.toJson();
      final restored = CustomizationSnapshot.fromJson(json);

      expect(restored.schemaVersion, equals(1));
      expect(restored.globalThemeId, equals('emerald_matrix'));
      expect(restored.navigationVariant, equals('floating_pill_nav'));
      expect(restored.composerVariant, equals('glass_float_composer'));
      expect(restored.conversationOverrides['conv_123']?['bubble'], equals('cyberpunk_neon'));
    });

    test('CustomizationMigration upgrades legacy maps gracefully', () {
      final legacyMap = {
        'schemaVersion': 0,
        'globalThemeId': 'chaty_emerald',
      };

      final migrated = CustomizationMigration.migrate(legacyMap);
      expect(migrated.schemaVersion, equals(1));
      expect(migrated.globalThemeId, equals('chaty_emerald'));
      expect(migrated.navigationVariant, equals('classic_label_bar'));
    });
  });

  group('CustomizationHistory Transaction Tests', () {
    test('History bounds stack at 10 items and correctly tracks undo', () {
      final history = CustomizationHistory();
      expect(history.canUndo, isFalse);

      const s0 = CustomizationSnapshot.defaultSnapshot;

      for (int i = 1; i <= 15; i++) {
        history.record(
          scope: ComponentScope.globalUser,
          previous: s0.copyWith(globalThemeId: 'theme_${i - 1}'),
          next: s0.copyWith(globalThemeId: 'theme_$i'),
        );
      }

      // Can undo, bounded
      expect(history.canUndo, isTrue);
      expect(history.items.length, equals(10));

      final popped = history.popUndo();
      expect(popped, isNotNull);
      expect(popped!.next.globalThemeId, equals('theme_15'));
      expect(popped.previous.globalThemeId, equals('theme_14'));
    });
  });

  group('CustomizationController Staging & Commit Tests', () {
    test('Staging isolated from active snapshot until committed', () async {
      SharedPreferences.setMockInitialValues({});
      final controller = CustomizationController();

      expect(controller.snapshot.navigationVariant, equals('classic_label_bar'));

      // Stage a preview
      await controller.setVariant(ComponentId.navigation, 'floating_pill_nav', immediate: false);
      expect(controller.isDirty, isTrue);
      expect(controller.previewSnapshot?.navigationVariant, equals('floating_pill_nav'));
      // Snapshot remains unchanged
      expect(controller.snapshot.navigationVariant, equals('classic_label_bar'));

      // Cancel preview
      controller.cancelPreview();
      expect(controller.isDirty, isFalse);
      expect(controller.previewSnapshot, isNull);
      expect(controller.snapshot.navigationVariant, equals('classic_label_bar'));

      // Stage and commit
      await controller.setVariant(ComponentId.navigation, 'floating_pill_nav', immediate: true);
      expect(controller.isDirty, isFalse);
      expect(controller.snapshot.navigationVariant, equals('floating_pill_nav'));

      // Undo
      expect(controller.canUndo, isTrue);
      await controller.undo();
      expect(controller.snapshot.navigationVariant, equals('classic_label_bar'));
    });
  });
}
