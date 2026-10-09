import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('global call capsule never requires a Tooltip overlay', () {
    final source = File(
      'lib/ui/core/design_system/components/call_activity_capsule.dart',
    ).readAsStringSync();
    expect(source, isNot(contains('tooltip:')));
    expect(source, contains('Semantics('));
    expect(source, contains('width: 48'));
    expect(source, contains('height: 48'));
  });

  test('frontend master patch owns stable listenable groups', () {
    final patch = File(
      'tools/apply_frontend_master_plan.py',
    ).readAsStringSync();
    expect(patch, contains('late final Listenable _rootSignals;'));
    expect(patch, contains('late final Listenable _navigationSignals;'));
    expect(patch, contains('late final Listenable _homeSignals;'));
    expect(patch, contains('listenable: _rootSignals'));
    expect(patch, contains('listenable: _navigationSignals'));
    expect(patch, contains('listenable: _homeSignals'));
  });

  test('UI Lab is deterministic and isolated from production repositories', () {
    final screen = File(
      'lib/features/ui_lab/ui_lab_screen.dart',
    ).readAsStringSync();
    final repository = File(
      'lib/features/ui_lab/ui_lab_repository.dart',
    ).readAsStringSync();
    expect(screen, contains("assert(kDebugMode"));
    expect(repository, contains('Deterministic UI-only fixtures'));
    expect(repository, isNot(contains('Supabase')));
    expect(repository, isNot(contains('ChatyBackendService')));
  });

  test(
    'canonical design system exports the master-plan component families',
    () {
      final source = File(
        'lib/ui/core/design_system/design_system.dart',
      ).readAsStringSync();
      for (final export in <String>[
        'chaty_motion.dart',
        'chaty_glyph.dart',
        'chaty_haptics.dart',
        'chaty_adaptive.dart',
        'component_state.dart',
        'messaging_components.dart',
        'social_components.dart',
        'settings_components.dart',
      ]) {
        expect(
          source,
          contains(export),
          reason: 'Missing design-system export: $export',
        );
      }
    },
  );

  test('home overflow uses adaptive presentation and a shared action handler', () {
    final source = File(
      'lib/features/chats/chats_home_screen.dart',
    ).readAsStringSync();

    expect(source, contains('final useOverflowSheet = availableWidth < 600;'));
    expect(source, contains('onSelected: _handleHomeOverflowAction'));
    expect(source, contains('if (useOverflowSheet)'));
    expect(source, contains('_handleHomeOverflowAction(value);'));
    for (final route in <String>[
      "case 'effects':",
      "case 'qr':",
      "case 'linked':",
      "case 'themes':",
      "case 'templates':",
      "case 'starred':",
      "case 'home':",
      "case 'navigation':",
      "case 'settings':",
    ]) {
      expect(source, contains(route), reason: 'Missing overflow action $route');
    }
  });

  test('template studio searches presets and independent component overrides', () {
    final source = File(
      'lib/features/settings/templates/templates_settings_screen.dart',
    ).readAsStringSync();
    expect(source, contains('Search templates and components'));
    expect(source, contains('...matchingTemplates.map'));
    expect(source, contains('...matchingComponents.map'));
    expect(source, contains('No template or component found'));
  });

  test('starred messages screen reads saved state and offers open/unstar actions', () {
    final screen = File(
      'lib/features/messages/starred_messages_screen.dart',
    ).readAsStringSync();
    final home = File(
      'lib/features/chats/chats_home_screen.dart',
    ).readAsStringSync();

    expect(screen, contains('widget.dataStore.conversations'));
    expect(screen, contains('message.isStarred'));
    expect(screen, contains('ensureConversationLoaded'));
    expect(screen, contains("value: 'open'"));
    expect(screen, contains("value: 'unstar'"));
    expect(screen, contains('toggleStarMessage'));
    expect(home, contains("case 'starred':"));
    expect(home, contains("title: Text('Starred messages')"));
  });

  test('component template previews do not display placeholder copy', () {
    final source = File(
      'lib/features/settings/templates/component_override_screen.dart',
    ).readAsStringSync();
    expect(source, isNot(contains("' configuration ()'")));
    expect(source, isNot(contains("'Message ()...'")));
    expect(source, contains('case TemplateComponentType.navigation:'));
    expect(source, contains('case TemplateComponentType.calls:'));
  });
}
