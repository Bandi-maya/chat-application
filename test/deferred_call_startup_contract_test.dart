import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('call signaling has no constructor-time initialization side effect', () {
    final source = File(
      'lib/data/services/call_signaling_service.dart',
    ).readAsStringSync();
    final constructor = source.substring(
      source.indexOf('CallSignalingService({'),
      source.indexOf('ChatyCallSession? get currentSession'),
    );

    expect(constructor, isNot(contains('initialize()')));
  });

  test('service locator does not eagerly resolve call presentation', () {
    final source = File('lib/injection/locator.dart').readAsStringSync();

    expect(
      source,
      isNot(contains('locator<CallPresentationController>();')),
    );
  });
}
