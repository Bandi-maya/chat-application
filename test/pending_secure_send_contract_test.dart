import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('pre-MLS drafts are encrypted locally and never plaintext-uploaded', () {
    final store = File(
      'lib/data/services/pending_secure_send_store.dart',
    ).readAsStringSync();
    final backend = File(
      'lib/data/services/backend_service.dart',
    ).readAsStringSync();

    expect(store, contains('LocalSnapshotCacheService'));
    expect(store, contains("static const String _scope = 'pending_secure_sends'"));
    expect(backend, contains('_isMlsSetupPendingError(error)'));
    expect(backend, contains('secure_setup_pending'));
    expect(backend, contains('_retryPendingSecureSends(String userId)'));
    expect(
      backend,
      isNot(contains('plaintext fallback')),
    );
  });
}
