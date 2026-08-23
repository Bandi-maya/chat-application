import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('backend restores encrypted snapshots before remote refresh', () {
    final backend = File('lib/data/services/backend_service.dart').readAsStringSync();
    expect(backend, contains('LocalSnapshotCacheService'));
    expect(backend, contains('_hydrateCachedState(session.user.id)'));
    expect(backend, contains('unawaited(_refreshAuthenticatedSession(session))'));
    expect(backend, contains("scope: 'conversations'"));
    expect(backend, contains("scope: 'messages_\$conversationId'"));
  });

  test('chat timeline renders cache first and always positions newest message', () {
    final chat = File('lib/features/chats/chat_detail_screen.dart').readAsStringSync();
    expect(chat, contains('final cachedMessages = widget.dataStore.getMessages'));
    expect(chat, contains('_loadingMessages = cachedMessages.isEmpty'));
    expect(chat, contains('_scrollToBottom(animate: false)'));
    expect(chat, contains('Waiting for secure chat setup'));
    expect(chat, isNot(contains("SnackBar(content: Text('Unable to send voice note: \$error'))")));
  });

  test('startup does not wait for noncritical housekeeping', () {
    final main = File('lib/main.dart').readAsStringSync();
    expect(main, contains('runApp(const ChatyApp());'));
    expect(main, contains('unawaited(locator<AppIconController>().initialize())'));
    expect(main, contains('late final Listenable _rootListenable'));
    expect(main, contains('listenable: _rootListenable'));
  });

  test('push registration no longer impersonates an FCM token', () {
    final push = File('lib/data/services/push_token_service.dart').readAsStringSync();
    expect(push, isNot(contains('device_\${platformName}_\${_uuid.v4()}')));
    expect(push, isNot(contains("token = 'fcm_")));
    expect(push, contains('registerPlatformToken'));
    expect(push, contains('hasRealPushTransport'));
  });

  test('Android backup is disabled for cached sensitive state', () {
    final manifest = File('android/app/src/main/AndroidManifest.xml').readAsStringSync();
    expect(manifest, contains('android:allowBackup="false"'));
    expect(manifest, contains('android:fullBackupContent="false"'));
  });
}
