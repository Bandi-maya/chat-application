import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('theme editor has no live message preview canvas', () {
    final source = File(
      'lib/features/settings/theme_editor_screen.dart',
    ).readAsStringSync();

    expect(source, isNot(contains('Live theme palette preview')));
    expect(source, isNot(contains("import '../messages/message_bubble.dart'")));
    expect(source, isNot(contains("import '../../domain/models/chat_message.dart'")));

    // Built-in palette choices keep their static selection cards.
    expect(source, contains('ThemePreviewCard('));
    expect(source, contains('onTap: () {'));
  });

  test('Theme Studio does not run an animated live chat preview loop', () {
    final source = File(
      'lib/features/settings/theme_studio_screen.dart',
    ).readAsStringSync();

    for (final obsoletePreviewHook in <String>[
      '_buildLiveInteractivePreview',
      '_particleAnimController',
      '_LiveParticleCanvasPainter',
      '_ParticleItem',
      '_tickParticles',
      '_build3DMotionTab',
      '_active3DTransition',
      '_particleSpeed',
      '_particleDensity',
    ]) {
      expect(
        source,
        isNot(contains(obsoletePreviewHook)),
        reason: 'Unexpected live-preview or non-persistent control: $obsoletePreviewHook',
      );
    }

    // A static applied-appearance snapshot may be used for reference; the
    // editable live-chat canvas itself must not be rendered.
    expect(source, contains('AppliedPairPreview('));
    expect(source, contains('Current applied appearance'));
    expect(source, contains('Apply & Save Theme'));
  });

  test('Theme Studio only offers and persists actual falling-particle types', () {
    final studio = File(
      'lib/features/settings/theme_studio_screen.dart',
    ).readAsStringSync();
    final model = File(
      'lib/domain/models/preferences.dart',
    ).readAsStringSync();
    final consumer = File(
      'lib/ui/core/widgets/falling_particles_overlay.dart',
    ).readAsStringSync();

    expect(
      studio,
      contains("const particleTypes = ['Stars', 'Hearts', 'Snowflakes', 'Leaves'];"),
    );
    expect(
      studio,
      contains('savedEffects.fallingParticleObject'),
    );
    expect(
      studio,
      contains('_enableFallingParticles = savedEffects.enableFallingParticles'),
    );
    expect(
      studio,
      contains('enableFallingParticles: _enableFallingParticles'),
    );

    for (final supported in <String>[
      'Stars',
      'Hearts',
      'Snowflakes',
      'Leaves',
    ]) {
      expect(model, contains("'$supported'"));
      expect(consumer, contains("'$supported'"));
      expect(studio, contains("'$supported'"));
    }

    // These were incorrectly presented as runtime falling effects; the
    // particle selector must not offer values that fall back to Stars.
    for (final unsupported in <String>[
      'Neon Embers',
      'Sakura',
      "'Snow'",
      "'None'",
    ]) {
      expect(studio, isNot(contains(unsupported)));
    }
  });
}
