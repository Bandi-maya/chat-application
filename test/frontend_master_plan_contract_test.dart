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
    final patch = File('tools/apply_frontend_master_plan.py').readAsStringSync();
    expect(patch, contains('late final Listenable _rootSignals;'));
    expect(patch, contains('late final Listenable _navigationSignals;'));
    expect(patch, contains('late final Listenable _homeSignals;'));
    expect(patch, contains('listenable: _rootSignals'));
    expect(patch, contains('listenable: _navigationSignals'));
    expect(patch, contains('listenable: _homeSignals'));
  });

  test('UI Lab is deterministic and isolated from production repositories', () {
    final screen = File('lib/features/ui_lab/ui_lab_screen.dart').readAsStringSync();
    final repository = File(
      'lib/features/ui_lab/ui_lab_repository.dart',
    ).readAsStringSync();
    expect(screen, contains("assert(kDebugMode"));
    expect(repository, contains('Deterministic UI-only fixtures'));
    expect(repository, isNot(contains('Supabase')));
    expect(repository, isNot(contains('ChatyBackendService')));
  });

  test('canonical design system exports the master-plan component families', () {
    final source = File(
      'lib/ui/core/design_system/design_system.dart',
    ).readAsStringSync();
    for (final export in <String>[
      'chaty_motion.dart',
      'chaty_haptics.dart',
      'chaty_adaptive.dart',
      'component_state.dart',
      'messaging_components.dart',
      'social_components.dart',
      'settings_components.dart',
    ]) {
      expect(source, contains(export), reason: 'Missing design-system export: $export');
    }
  });
}
