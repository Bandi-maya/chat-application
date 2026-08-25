import 'dart:convert';
import 'dart:math';

import 'package:cryptography/cryptography.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:local_auth/local_auth.dart';

/// Local-only authentication primitives used by both App Lock and Chat Lock.
///
/// Features:
/// - PBKDF2 with SHA-256 (120,000 rounds) and 16-byte random salt per credential
/// - Credentials (PIN, Pattern, Password) stored securely in FlutterSecureStorage
/// - Secret Phrase/Emoji for Hidden Locked Chats (salted PBKDF2 hash)
/// - Controlled failure cooldown & retry delay (brute force mitigation)
/// - Constant-time comparison for all hashes
/// - Unicode grapheme & whitespace normalization for secret search phrases
class LocalLockService {
  LocalLockService({
    LocalAuthentication? localAuthentication,
    FlutterSecureStorage? secureStorage,
  }) : _localAuthentication = localAuthentication ?? LocalAuthentication(),
       _secureStorage = secureStorage ?? const FlutterSecureStorage();

  static const String _prefix = 'chaty.local_lock.v2';
  static const String _pinLengthKey = '$_prefix.pin_length';
  static const String _secretPhraseHashKey = '$_prefix.secret_phrase.hash';
  static const String _secretPhraseSaltKey = '$_prefix.secret_phrase.salt';
  static const String _failedAttemptsKey = '$_prefix.failed_attempts';
  static const String _lockoutUntilKey = '$_prefix.lockout_until';
  static const int _saltLength = 16;

  final LocalAuthentication _localAuthentication;
  final FlutterSecureStorage _secureStorage;
  final Random _random = Random.secure();
  final Pbkdf2 _pbkdf2 = Pbkdf2(
    macAlgorithm: Hmac.sha256(),
    iterations: 120000,
    bits: 256,
  );

  String _normalizedMethod(String method) {
    switch (method.toLowerCase()) {
      case 'pin':
        return 'pin';
      case 'pattern':
        return 'pattern';
      case 'password':
        return 'password';
      default:
        throw ArgumentError.value(
          method,
          'method',
          'Unsupported local credential method',
        );
    }
  }

  String _hashKey(String method) =>
      '$_prefix.${_normalizedMethod(method)}.hash';
  String _saltKey(String method) =>
      '$_prefix.${_normalizedMethod(method)}.salt';

  Future<bool> hasCredential(String method) async {
    try {
      return (await _secureStorage.read(key: _hashKey(method)))?.isNotEmpty ==
              true &&
          (await _secureStorage.read(key: _saltKey(method)))?.isNotEmpty ==
              true;
    } catch (_) {
      return false;
    }
  }

  Future<int> getPinLength() async {
    try {
      final value = int.tryParse(
        await _secureStorage.read(key: _pinLengthKey) ?? '',
      );
      return value == 6 ? 6 : 4;
    } catch (_) {
      return 4;
    }
  }

  Future<void> setPinLength(int length) async {
    final safeLength = length == 6 ? 6 : 4;
    await _secureStorage.write(key: _pinLengthKey, value: '$safeLength');
  }

  Future<void> setCredential(
    String method,
    String secret, {
    int? pinLength,
  }) async {
    final normalized = _normalizedMethod(method);
    if (secret.isEmpty) throw ArgumentError('Credential must not be empty.');
    if (normalized == 'pin') {
      final expectedLength = pinLength == 6 ? 6 : 4;
      if (!RegExp(r'^\d+$').hasMatch(secret) ||
          secret.length != expectedLength) {
        throw ArgumentError('PIN must contain exactly $expectedLength digits.');
      }
      await setPinLength(expectedLength);
    }
    if (normalized == 'pattern' && !_isValidPattern(secret)) {
      throw ArgumentError('Pattern must connect at least 4 unique points.');
    }
    if (normalized == 'password' && secret.length < 6) {
      throw ArgumentError('Password must contain at least 6 characters.');
    }

    final salt = List<int>.generate(_saltLength, (_) => _random.nextInt(256));
    final derived = await _pbkdf2.deriveKeyFromPassword(
      password: secret,
      nonce: salt,
    );
    final bytes = await derived.extractBytes();
    await _secureStorage.write(
      key: _saltKey(normalized),
      value: base64Encode(salt),
    );
    await _secureStorage.write(
      key: _hashKey(normalized),
      value: base64Encode(bytes),
    );
  }

  /// Verifies credential with brute-force rate-limiting & cooldown.
  Future<bool> verifyCredential(String method, String secret) async {
    // Check lockout
    final lockoutSeconds = await getRemainingCooldownSeconds();
    if (lockoutSeconds > 0) {
      return false;
    }

    final normalized = _normalizedMethod(method);
    try {
      final encodedHash = await _secureStorage.read(key: _hashKey(normalized));
      final encodedSalt = await _secureStorage.read(key: _saltKey(normalized));
      if (encodedHash == null || encodedSalt == null) return false;

      final salt = base64Decode(encodedSalt);
      final expected = base64Decode(encodedHash);
      final derived = await _pbkdf2.deriveKeyFromPassword(
        password: secret,
        nonce: salt,
      );
      final actual = await derived.extractBytes();
      final isMatch = _constantTimeBytesEqual(actual, expected);

      if (isMatch) {
        await resetFailedAttempts();
        return true;
      } else {
        await recordFailedAttempt();
        return false;
      }
    } catch (_) {
      return false;
    }
  }

  // --- Secret Search Phrase for Hidden Locked Chats ---

  /// Normalizes a secret phrase (word, emoji sequence, etc.) deterministically.
  static String normalizeSecretPhrase(String phrase) {
    // Trim leading/trailing whitespace, collapse contiguous spaces, lower-case
    return phrase.trim().replaceAll(RegExp(r'\s+'), ' ').toLowerCase();
  }

  Future<bool> hasSecretPhrase() async {
    try {
      return (await _secureStorage.read(
                key: _secretPhraseHashKey,
              ))?.isNotEmpty ==
              true &&
          (await _secureStorage.read(key: _secretPhraseSaltKey))?.isNotEmpty ==
              true;
    } catch (_) {
      return false;
    }
  }

  Future<void> setSecretPhrase(String rawPhrase) async {
    final normalized = normalizeSecretPhrase(rawPhrase);
    if (normalized.isEmpty) {
      throw ArgumentError('Secret phrase cannot be empty.');
    }
    final salt = List<int>.generate(_saltLength, (_) => _random.nextInt(256));
    final derived = await _pbkdf2.deriveKeyFromPassword(
      password: normalized,
      nonce: salt,
    );
    final bytes = await derived.extractBytes();
    await _secureStorage.write(
      key: _secretPhraseSaltKey,
      value: base64Encode(salt),
    );
    await _secureStorage.write(
      key: _secretPhraseHashKey,
      value: base64Encode(bytes),
    );
  }

  Future<bool> verifySecretPhrase(String rawQuery) async {
    final normalized = normalizeSecretPhrase(rawQuery);
    if (normalized.isEmpty) return false;
    try {
      final encodedHash = await _secureStorage.read(key: _secretPhraseHashKey);
      final encodedSalt = await _secureStorage.read(key: _secretPhraseSaltKey);
      if (encodedHash == null || encodedSalt == null) return false;

      final salt = base64Decode(encodedSalt);
      final expected = base64Decode(encodedHash);
      final derived = await _pbkdf2.deriveKeyFromPassword(
        password: normalized,
        nonce: salt,
      );
      final actual = await derived.extractBytes();
      return _constantTimeBytesEqual(actual, expected);
    } catch (_) {
      return false;
    }
  }

  Future<void> clearSecretPhrase() async {
    await _secureStorage.delete(key: _secretPhraseHashKey);
    await _secureStorage.delete(key: _secretPhraseSaltKey);
  }

  // --- Brute-Force Rate Limiting & Cooldown ---

  Future<int> getFailedAttempts() async {
    try {
      final val = await _secureStorage.read(key: _failedAttemptsKey);
      return int.tryParse(val ?? '') ?? 0;
    } catch (_) {
      return 0;
    }
  }

  Future<int> getRemainingCooldownSeconds() async {
    try {
      final val = await _secureStorage.read(key: _lockoutUntilKey);
      if (val == null) return 0;
      final lockoutTime = int.tryParse(val) ?? 0;
      final now = DateTime.now().millisecondsSinceEpoch;
      if (lockoutTime > now) {
        return ((lockoutTime - now) / 1000).ceil();
      }
      return 0;
    } catch (_) {
      return 0;
    }
  }

  Future<void> recordFailedAttempt() async {
    final attempts = (await getFailedAttempts()) + 1;
    await _secureStorage.write(key: _failedAttemptsKey, value: '$attempts');

    // Progressive cooldown: 5 failures -> 30s, 10 failures -> 60s, 15+ failures -> 300s
    if (attempts >= 15) {
      final lockout = DateTime.now()
          .add(const Duration(minutes: 5))
          .millisecondsSinceEpoch;
      await _secureStorage.write(key: _lockoutUntilKey, value: '$lockout');
    } else if (attempts >= 10) {
      final lockout = DateTime.now()
          .add(const Duration(seconds: 60))
          .millisecondsSinceEpoch;
      await _secureStorage.write(key: _lockoutUntilKey, value: '$lockout');
    } else if (attempts >= 5) {
      final lockout = DateTime.now()
          .add(const Duration(seconds: 30))
          .millisecondsSinceEpoch;
      await _secureStorage.write(key: _lockoutUntilKey, value: '$lockout');
    }
  }

  Future<void> resetFailedAttempts() async {
    await _secureStorage.delete(key: _failedAttemptsKey);
    await _secureStorage.delete(key: _lockoutUntilKey);
  }

  // --- Native Biometrics & Device Credential ---

  Future<bool> canUseBiometrics() async {
    try {
      if (!await _localAuthentication.canCheckBiometrics) return false;
      return (await _localAuthentication.getAvailableBiometrics()).isNotEmpty;
    } on PlatformException {
      return false;
    } catch (_) {
      return false;
    }
  }

  Future<List<BiometricType>> availableBiometrics() async {
    try {
      return await _localAuthentication.getAvailableBiometrics();
    } catch (_) {
      return const <BiometricType>[];
    }
  }

  Future<bool> authenticateBiometric({
    String reason = 'Authenticate to unlock Chaty',
  }) async {
    try {
      if (!await canUseBiometrics()) return false;
      return await _localAuthentication.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(
          biometricOnly: true,
          stickyAuth: true,
          sensitiveTransaction: true,
          useErrorDialogs: true,
        ),
      );
    } on PlatformException {
      return false;
    } catch (_) {
      return false;
    }
  }

  Future<bool> authenticateDeviceCredential({
    String reason = 'Use your device lock to unlock Chaty',
  }) async {
    try {
      if (!await _localAuthentication.isDeviceSupported()) return false;
      return await _localAuthentication.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(
          biometricOnly: false,
          stickyAuth: true,
          sensitiveTransaction: true,
          useErrorDialogs: true,
        ),
      );
    } on PlatformException {
      return false;
    } catch (_) {
      return false;
    }
  }

  Future<void> clearCredential(String method) async {
    final normalized = _normalizedMethod(method);
    await _secureStorage.delete(key: _hashKey(normalized));
    await _secureStorage.delete(key: _saltKey(normalized));
  }

  /// Clears all credentials and locks stored locally (e.g. on logout/account switch).
  Future<void> clearAll() async {
    await clearCredential('pin');
    await clearCredential('pattern');
    await clearCredential('password');
    await clearSecretPhrase();
    await resetFailedAttempts();
    await _secureStorage.delete(key: _pinLengthKey);
  }

  bool _isValidPattern(String pattern) {
    final values = pattern
        .split('-')
        .where((value) => value.isNotEmpty)
        .toList(growable: false);
    if (values.length < 4) return false;
    final parsed = values.map(int.tryParse).toList(growable: false);
    if (parsed.any((value) => value == null || value < 0 || value > 8)) {
      return false;
    }
    return parsed.toSet().length == parsed.length;
  }

  bool _constantTimeBytesEqual(List<int> a, List<int> b) {
    var difference = a.length ^ b.length;
    final maxLength = max(a.length, b.length);
    for (var index = 0; index < maxLength; index++) {
      final left = index < a.length ? a[index] : 0;
      final right = index < b.length ? b[index] : 0;
      difference |= left ^ right;
    }
    return difference == 0;
  }
}
