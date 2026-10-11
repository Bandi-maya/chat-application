import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('shared component motion uses the canonical reduced-motion resolver', () {
    final tokenSource = File(
      'lib/ui/core/design_system/tokens/app_tokens.dart',
    ).readAsStringSync();
    expect(tokenSource, contains('static Duration duration('));
    expect(tokenSource, contains('MediaQuery.maybeOf(context)?.disableAnimations'));

    for (final path in <String>[
      'lib/ui/core/design_system/components/app_components.dart',
      'lib/ui/core/design_system/components/chaty_kit.dart',
      'lib/ui/core/design_system/components/messaging_components.dart',
      'lib/ui/core/design_system/components/social_components.dart',
    ]) {
      final source = File(path).readAsStringSync();
      expect(
        source,
        contains('ChatyMotion.duration(context'),
        reason: '$path should use the shared reduced-motion duration resolver',
      );
    }
  });

  test('skeleton shimmer stops and resumes when reduced-motion preference changes', () {
    final source = File(
      'lib/ui/core/design_system/components/app_components.dart',
    ).readAsStringSync();

    expect(source, contains('void didChangeDependencies()'));
    expect(source, contains('_syncPulseWithMotionPreference();'));
    expect(source, contains('if (_pulse.isAnimating) _pulse.stop();'));
    expect(source, contains('_pulse.repeat(reverse: true);'));
    expect(source, contains('if (reduceMotion) return block();'));
  });
}
