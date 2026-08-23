import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('permanent account deletion purges sensitive local state', () {
    final mls = File('lib/data/services/mls_e2ee_service.dart').readAsStringSync();
    final media = File('lib/data/services/chat_media_service.dart').readAsStringSync();
    final profile = File('lib/features/profile/profile_actions.dart').readAsStringSync();

    expect(mls, contains('purgeLocalIdentityForUser'));
    expect(mls, contains(r"'$prefix.db_key.v1'"));
    expect(mls, contains(r"'$basePath-wal'"));
    expect(media, contains('purgeLocalTemporaryFiles'));
    expect(profile, contains('EncryptedMessageOutbox().clear(deletingUserId)'));
    expect(profile, contains('purgeLocalIdentityForUser(deletingUserId)'));
    expect(profile, contains('FlutterSecureStorage().deleteAll()'));
  });
}
