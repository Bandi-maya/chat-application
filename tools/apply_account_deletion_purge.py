#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# ---------------------------------------------------------------------------
# MLS: close live engine, cryptographically erase secure keys, and delete the
# encrypted SQLCipher database and WAL/SHM sidecars for the deleted account.
# ---------------------------------------------------------------------------
mls_path = ROOT / 'lib/data/services/mls_e2ee_service.dart'
mls = mls_path.read_text(encoding='utf-8')
if "import 'dart:io';" not in mls:
    mls = mls.replace("import 'dart:convert';", "import 'dart:convert';\nimport 'dart:io';", 1)

if 'Future<void> purgeLocalIdentityForUser(String userId)' not in mls:
    marker = "  Future<void> close() async {\n"
    if marker not in mls:
        raise SystemExit('MLS close() marker not found')
    method = r'''  Future<void> purgeLocalIdentityForUser(String userId) async {
    final normalizedUserId = userId.trim();
    if (normalizedUserId.isEmpty) return;

    if (_userId == normalizedUserId) {
      await close();
    }

    final prefix = 'chaty.mls.$normalizedUserId';
    for (final key in <String>[
      '$prefix.device_id.v1',
      '$prefix.db_key.v1',
      '$prefix.signer_private.v1',
      '$prefix.signer_public.v1',
    ]) {
      await _secureStorage.delete(key: key);
    }

    final support = await getApplicationSupportDirectory();
    final safeUser = normalizedUserId.replaceAll(
      RegExp(r'[^A-Za-z0-9_-]'),
      '_',
    );
    final basePath = '${support.path}/chaty_mls_$safeUser.db';
    for (final path in <String>[basePath, '$basePath-wal', '$basePath-shm']) {
      try {
        final file = File(path);
        if (await file.exists()) await file.delete();
      } catch (_) {
        // Key deletion above makes any surviving SQLCipher bytes unreadable.
      }
    }
  }

'''
    mls = mls.replace(marker, method + marker, 1)
mls_path.write_text(mls, encoding='utf-8')

# ---------------------------------------------------------------------------
# Media: clear process cache and best-effort delete cleartext/ciphertext temp
# files created by attachment resolving, compression, download, and voice note
# recording. Storage objects on the server are handled separately.
# ---------------------------------------------------------------------------
media_path = ROOT / 'lib/data/services/chat_media_service.dart'
media = media_path.read_text(encoding='utf-8')
if 'Future<void> purgeLocalTemporaryFiles() async' not in media:
    marker = "  Future<void> deleteOwnAttachment(String objectPath) async {\n"
    if marker not in media:
        raise SystemExit('ChatMediaService deletion marker not found')
    method = r'''  Future<void> purgeLocalTemporaryFiles() async {
    _localFileCache.clear();
    final temp = await getTemporaryDirectory();
    try {
      await for (final entity in temp.list(followLinks: false)) {
        if (entity is! File) continue;
        final name = entity.uri.pathSegments.isEmpty
            ? ''
            : entity.uri.pathSegments.last;
        final isChatyTemp =
            name.startsWith('chaty_clear_') ||
            name.startsWith('chaty_download_') ||
            name.startsWith('chaty_encrypted_') ||
            name.startsWith('chaty_voice_') ||
            name.startsWith('chaty_');
        if (!isChatyTemp) continue;
        try {
          await entity.delete();
        } catch (_) {}
      }
    } catch (_) {}
  }

'''
    media = media.replace(marker, method + marker, 1)
media_path.write_text(media, encoding='utf-8')

# ---------------------------------------------------------------------------
# Account delete UI: after the server confirms deletion, purge sensitive local
# state. We capture user id before invoking the destructive edge function.
# ---------------------------------------------------------------------------
profile_path = ROOT / 'lib/features/profile/profile_actions.dart'
profile = profile_path.read_text(encoding='utf-8')
imports = {
    "import 'package:flutter/material.dart';": "import 'package:flutter/material.dart';\nimport 'package:flutter_secure_storage/flutter_secure_storage.dart';",
    "import '../../data/services/backend_service.dart';": "import '../../data/services/backend_service.dart';\nimport '../../data/services/chat_media_service.dart';\nimport '../../data/services/encrypted_message_outbox.dart';\nimport '../../data/services/mls_e2ee_service.dart';",
}
for old, new in imports.items():
    if new.split('\n')[-1] not in profile:
        if old not in profile:
            raise SystemExit(f'Profile import marker missing: {old}')
        profile = profile.replace(old, new, 1)

if 'final deletingUserId = Supabase.instance.client.auth.currentUser?.id;' not in profile:
    marker = "  try {\n    if (locator.isRegistered<PushTokenService>()) {\n      await locator<PushTokenService>().revokeTokenOnLogout();\n    }\n    final response = await Supabase.instance.client.functions.invoke(\n"
    if marker not in profile:
        raise SystemExit('Delete-account try marker not found')
    replacement = "  try {\n    final deletingUserId = Supabase.instance.client.auth.currentUser?.id;\n    if (deletingUserId == null || deletingUserId.isEmpty) {\n      throw StateError('No authenticated account is available to delete.');\n    }\n    if (locator.isRegistered<PushTokenService>()) {\n      await locator<PushTokenService>().revokeTokenOnLogout();\n    }\n    final response = await Supabase.instance.client.functions.invoke(\n"
    profile = profile.replace(marker, replacement, 1)

if 'await EncryptedMessageOutbox().clear(deletingUserId);' not in profile:
    marker = "    if (locator.isRegistered<PushTokenService>()) {\n      await locator<PushTokenService>().clearLocalRegistration();\n    }\n    try {\n      await locator<ChatyBackendService>().logout();\n"
    if marker not in profile:
        raise SystemExit('Post-delete local cleanup marker not found')
    replacement = r'''    if (locator.isRegistered<PushTokenService>()) {
      await locator<PushTokenService>().clearLocalRegistration();
    }

    await EncryptedMessageOutbox().clear(deletingUserId);
    if (locator.isRegistered<MlsE2eeService>()) {
      await locator<MlsE2eeService>().purgeLocalIdentityForUser(deletingUserId);
    }
    await ChatMediaService().purgeLocalTemporaryFiles();

    // Account deletion is the only flow that performs a full secure-storage
    // wipe. Normal logout preserves device identity and local lock choices.
    await const FlutterSecureStorage().deleteAll();

    try {
      await locator<ChatyBackendService>().logout();
'''
    profile = profile.replace(marker, replacement, 1)
profile_path.write_text(profile, encoding='utf-8')

for path, markers in {
    mls_path: [
        'purgeLocalIdentityForUser',
        "'$prefix.db_key.v1'",
        "'$basePath-wal'",
    ],
    media_path: ['purgeLocalTemporaryFiles', "name.startsWith('chaty_clear_')"],
    profile_path: [
        'EncryptedMessageOutbox().clear(deletingUserId)',
        'purgeLocalIdentityForUser(deletingUserId)',
        'FlutterSecureStorage().deleteAll()',
    ],
}.items():
    text = path.read_text(encoding='utf-8')
    for marker in markers:
        if marker not in text:
            raise SystemExit(f'Account purge invariant missing in {path}: {marker}')

print('Account deletion local purge applied.')
