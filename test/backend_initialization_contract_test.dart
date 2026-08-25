import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('backend initialization is guarded by one in-flight future', () {
    final backend = File(
      'lib/data/services/backend_service.dart',
    ).readAsStringSync();

    expect(backend, contains('Future<void>? _initializeFuture;'));
    expect(backend, contains('return _initializeFuture ??= _initialize();'));
    expect(backend, contains('Future<void> _initialize() async'));
    expect(backend, contains('_initializeFuture = null;'));
  });

  test('backend owns only one authentication subscription', () {
    final backend = File(
      'lib/data/services/backend_service.dart',
    ).readAsStringSync();

    expect(
      backend,
      contains('StreamSubscription<AuthState>? _authSubscription;'),
    );
    expect(
      backend,
      contains('_authSubscription ??= _client.auth.onAuthStateChange.listen('),
    );
    expect(
      RegExp(
        r'_client\.auth\.onAuthStateChange\.listen\(',
      ).allMatches(backend).length,
      1,
    );
  });
}
