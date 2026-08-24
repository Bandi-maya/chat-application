import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/templates/template_models.dart';
import 'package:chat/ui/core/templates/template_registry.dart';
import 'package:chat/ui/core/templates/template_controller.dart';

void main() {
  group('Chaty UI Template System Tests', () {
    late TemplateController controller;

    setUp(() {
      controller = TemplateController();
    });

    test('All 6 original templates exist in registry with non-null components', () {
      expect(ChatyTemplateRegistry.list.length, equals(6));
      for (final tmpl in ChatyTemplateRegistry.list) {
        expect(tmpl.id, isNotNull);
        expect(tmpl.name.isNotEmpty, isTrue);
        expect(tmpl.navigation, isNotNull);
        expect(tmpl.home, isNotNull);
        expect(tmpl.chatList, isNotNull);
        expect(tmpl.conversation, isNotNull);
        expect(tmpl.composer, isNotNull);
        expect(tmpl.updates, isNotNull);
        expect(tmpl.profile, isNotNull);
        expect(tmpl.calls, isNotNull);
      }
    });

    test('Navigation destinations have zero duplicates within any template', () {
      for (final tmpl in ChatyTemplateRegistry.list) {
        final primary = tmpl.navigation.primaryDestinationIds;
        final overflow = tmpl.navigation.overflowDestinationIds;
        final all = [...primary, ...overflow];

        expect(all.length, equals(all.toSet().length),
            reason: ' has duplicate navigation destination IDs');

        // Verify primary XOR overflow disjointness
        final primarySet = primary.toSet();
        for (final ov in overflow) {
          expect(primarySet.contains(ov), isFalse,
              reason: ' overflow contains primary destination ');
        }
      }
    });

    test('Applying full template resets component overrides', () async {
      await controller.applyComponent(
        component: TemplateComponentType.navigation,
        templateId: ChatyTemplateId.cameraFirst,
      );
      expect(controller.config.isOverridden(TemplateComponentType.navigation), isTrue);

      await controller.applyFullTemplate(ChatyTemplateId.visualSocial);
      expect(controller.baseTemplate, equals(ChatyTemplateId.visualSocial));
      expect(controller.config.componentOverrides.isEmpty, isTrue);
      expect(controller.resolveTemplateFor(TemplateComponentType.navigation),
          equals(ChatyTemplateId.visualSocial));
      expect(controller.resolveTemplateFor(TemplateComponentType.composer),
          equals(ChatyTemplateId.visualSocial));
    });

    test('Component-level override modifies ONLY the targeted component', () async {
      await controller.applyFullTemplate(ChatyTemplateId.messageFirst);

      final beforeComposer = controller.resolveTemplateFor(TemplateComponentType.composer);
      final beforeProfile = controller.resolveTemplateFor(TemplateComponentType.profile);
      final beforeHome = controller.resolveTemplateFor(TemplateComponentType.home);

      // Apply Navigation override only
      await controller.applyComponent(
        component: TemplateComponentType.navigation,
        templateId: ChatyTemplateId.cameraFirst,
      );

      expect(controller.resolveTemplateFor(TemplateComponentType.navigation),
          equals(ChatyTemplateId.cameraFirst));
      expect(controller.resolveTemplateFor(TemplateComponentType.composer),
          equals(beforeComposer));
      expect(controller.resolveTemplateFor(TemplateComponentType.profile),
          equals(beforeProfile));
      expect(controller.resolveTemplateFor(TemplateComponentType.home),
          equals(beforeHome));
    });

    test('Resetting a single component falls back to the active base template', () async {
      await controller.applyFullTemplate(ChatyTemplateId.powerChat);
      await controller.applyComponent(
        component: TemplateComponentType.navigation,
        templateId: ChatyTemplateId.stream,
      );

      expect(controller.resolveTemplateFor(TemplateComponentType.navigation),
          equals(ChatyTemplateId.stream));

      await controller.removeComponentOverride(TemplateComponentType.navigation);
      expect(controller.resolveTemplateFor(TemplateComponentType.navigation),
          equals(ChatyTemplateId.powerChat));
    });

    test('Serialization and deserialization round-trips correctly', () {
      final config = UserTemplateConfiguration(
        baseTemplate: ChatyTemplateId.powerChat,
        componentOverrides: {
          TemplateComponentType.navigation: ChatyTemplateId.cameraFirst,
          TemplateComponentType.composer: ChatyTemplateId.visualSocial,
        },
      );

      final map = config.toMap();
      final restored = UserTemplateConfiguration.fromMap(map);

      expect(restored.baseTemplate, equals(ChatyTemplateId.powerChat));
      expect(restored.componentOverrides.length, equals(2));
      expect(restored.resolveFor(TemplateComponentType.navigation),
          equals(ChatyTemplateId.cameraFirst));
      expect(restored.resolveFor(TemplateComponentType.composer),
          equals(ChatyTemplateId.visualSocial));
      expect(restored.resolveFor(TemplateComponentType.profile),
          equals(ChatyTemplateId.powerChat));
    });

    test('Corrupted map input falls back safely without crashing', () {
      final corrupted = UserTemplateConfiguration.fromMap(null);
      expect(corrupted.baseTemplate, equals(ChatyTemplateId.messageFirst));
      expect(corrupted.componentOverrides.isEmpty, isTrue);

      final invalidKeys = UserTemplateConfiguration.fromMap({
        'base': 'unknown_future_template',
        'overrides': {
          'invalid_component': 'random_string',
        },
      });
      expect(invalidKeys.baseTemplate, equals(ChatyTemplateId.messageFirst));
      expect(invalidKeys.componentOverrides.isEmpty, isTrue);
    });
  });
}
