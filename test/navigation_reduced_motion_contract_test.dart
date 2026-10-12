import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('navigation variants suppress transitions for reduced-motion users', () {
    final source = File(
      'lib/ui/core/components/navigation/chaty_navigation_renderer.dart',
    ).readAsStringSync();

    expect(source, contains('MediaQuery.disableAnimationsOf(context)'));
    expect(source, contains('? Duration.zero : preferred'));
    expect(source, isNot(contains('duration: ChatyMotion.fast')));
    expect(source, isNot(contains('duration: ChatyMotion.instant')));
  });
}
