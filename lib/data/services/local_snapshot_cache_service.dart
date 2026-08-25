import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:cryptography/cryptography.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path_provider/path_provider.dart';

/// Encrypted, account-scoped local snapshot cache used for instant startup.
///
/// This cache is deliberately not a second source of truth. It only stores the
/// most recent server-backed profile/conversation/message snapshots so the UI
/// can render immediately while Supabase refreshes in the background.
/// Snapshots are AES-256-GCM encrypted with a random key kept in platform
/// secure storage. Cache corruption is treated as a miss, never as app state.
class LocalSnapshotCacheService {
  LocalSnapshotCacheService({FlutterSecureStorage? secureStorage})
    : _secureStorage = secureStorage ?? const FlutterSecureStorage();

  static const String _keyName = 'chaty.snapshot_cache.key.v1';
  static const int _version = 1;
  final FlutterSecureStorage _secureStorage;
  final AesGcm _cipher = AesGcm.with256bits();
  SecretKey? _cachedKey;
  Future<SecretKey>? _loadKeyFuture;

  Future<void> writeJson({
    required String userId,
    required String scope,
    required Object value,
  }) async {
    if (userId.isEmpty || scope.isEmpty) return;
    try {
      final key = await _loadOrCreateKey();
      final nonce = _randomBytes(12);
      final payload = utf8.encode(
        jsonEncode(<String, dynamic>{
          'v': _version,
          'saved_at': DateTime.now().toUtc().toIso8601String(),
          'value': value,
        }),
      );
      final box = await _cipher.encrypt(
        payload,
        secretKey: key,
        nonce: nonce,
        aad: utf8.encode('$userId:$scope:v$_version'),
      );
      final envelope = <String, dynamic>{
        'v': _version,
        'n': base64Encode(box.nonce),
        'c': base64Encode(box.cipherText),
        'm': base64Encode(box.mac.bytes),
      };
      final file = await _fileFor(userId, scope);
      final tmp = File('${file.path}.tmp');
      await tmp.writeAsString(jsonEncode(envelope), flush: true);
      if (await file.exists()) await file.delete();
      await tmp.rename(file.path);
    } catch (error, stackTrace) {
      debugPrint('Chaty snapshot write skipped [$scope]: $error\n$stackTrace');
    }
  }

  Future<dynamic> readJson({
    required String userId,
    required String scope,
  }) async {
    if (userId.isEmpty || scope.isEmpty) return null;
    try {
      final file = await _fileFor(userId, scope);
      if (!await file.exists()) return null;
      final decoded = jsonDecode(await file.readAsString());
      if (decoded is! Map) return null;
      final map = decoded.map((key, value) => MapEntry(key.toString(), value));
      if (map['v'] != _version) return null;
      final nonce = base64Decode(map['n']?.toString() ?? '');
      final cipherText = base64Decode(map['c']?.toString() ?? '');
      final mac = Mac(base64Decode(map['m']?.toString() ?? ''));
      final clear = await _cipher.decrypt(
        SecretBox(cipherText, nonce: nonce, mac: mac),
        secretKey: await _loadOrCreateKey(),
        aad: utf8.encode('$userId:$scope:v$_version'),
      );
      final body = jsonDecode(utf8.decode(clear));
      if (body is! Map || body['v'] != _version) return null;
      return body['value'];
    } catch (error, stackTrace) {
      debugPrint('Chaty snapshot read missed [$scope]: $error\n$stackTrace');
      return null;
    }
  }

  Future<void> deleteUser(String userId) async {
    if (userId.isEmpty) return;
    try {
      final directory = await getApplicationSupportDirectory();
      final safeUser = _safe(userId);
      await for (final entity in directory.list(followLinks: false)) {
        if (entity is! File) continue;
        final name = entity.uri.pathSegments.last;
        if (!name.startsWith('chaty_snapshot_${safeUser}_')) continue;
        try {
          await entity.delete();
        } catch (_) {}
      }
    } catch (_) {}
  }

  Future<void> deleteAll() async {
    try {
      final directory = await getApplicationSupportDirectory();
      await for (final entity in directory.list(followLinks: false)) {
        if (entity is! File) continue;
        if (!entity.uri.pathSegments.last.startsWith('chaty_snapshot_'))
          continue;
        try {
          await entity.delete();
        } catch (_) {}
      }
      _cachedKey = null;
      _loadKeyFuture = null;
      await _secureStorage.delete(key: _keyName);
    } catch (_) {}
  }

  Future<SecretKey> _loadOrCreateKey() {
    final cached = _cachedKey;
    if (cached != null) return Future<SecretKey>.value(cached);
    return _loadKeyFuture ??= _performLoadOrCreateKey();
  }

  Future<SecretKey> _performLoadOrCreateKey() async {
    try {
      final existing = await _secureStorage.read(key: _keyName);
      if (existing != null && existing.isNotEmpty) {
        try {
          final bytes = base64Decode(existing);
          if (bytes.length == 32) {
            final key = SecretKey(bytes);
            _cachedKey = key;
            return key;
          }
        } catch (_) {}
      }
      final bytes = _randomBytes(32);
      await _secureStorage.write(key: _keyName, value: base64Encode(bytes));
      final key = SecretKey(bytes);
      _cachedKey = key;
      return key;
    } finally {
      _loadKeyFuture = null;
    }
  }

  Future<File> _fileFor(String userId, String scope) async {
    final directory = await getApplicationSupportDirectory();
    return File(
      '${directory.path}/chaty_snapshot_${_safe(userId)}_${_safe(scope)}.bin',
    );
  }

  String _safe(String value) =>
      value.replaceAll(RegExp(r'[^A-Za-z0-9_-]'), '_');

  Uint8List _randomBytes(int length) {
    final random = Random.secure();
    return Uint8List.fromList(
      List<int>.generate(length, (_) => random.nextInt(256)),
    );
  }
}
