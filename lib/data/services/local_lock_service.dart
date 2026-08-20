import 'dart:convert';
import 'dart:math';

import 'package:cryptography/cryptography.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:local_auth/local_auth.dart';

/// Local-only authentication primitives used by both App Lock and Chat Lock.
///
/// Secrets are never persisted in application preferences or sent to Supabase.
/// PINs, passwords, and patterns are stored as salted PBKDF2 hashes inside the
/// platform secure storage. Biometrics/device credentials are verified by the
/// operating system through Flutter's open-source `local_auth` plugin.
class LocalLockService {
  LocalLockService({
    LocalAuthentication? localAuthentication,
    FlutterSecureStorage? secureStorage,
  })  : _localAuthentication = localAuthentication ?? LocalAuthentication(),
        _secureStorage = secureStorage ?? const FlutterSecureStorage();

  static const String _prefix = 'chaty.local_lock.v2';
  static const String _pinLengthKey = '$_prefix.pin_length';
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
        throw ArgumentError.value(method, 'method', 'Unsupported local credential method');
    }
  }

  String _hashKey(String method) => '$_prefix.${_normalizedMethod(method)}.hash';
  String _saltKey(String method) => '$_prefix.${_normalizedMethod(method)}.salt';

  Future<bool> hasCredential(String method) async {
    try {
      return (await _secureStorage.read(key: _hashKey(method)))?.isNotEmpty == true &&
          (await _secureStorage.read(key: _saltKey(method)))?.isNotEmpty == true;
    } catch (_) {
      return false;
    }
  }

  Future<int> getPinLength() async {
    try {
      final value = int.tryParse(await _secureStorage.read(key: _pinLengthKey) ?? '');
      return value == 6 ? 6 : 4;
    } catch (_) {
      return 4;
    }
  }

  Future<void> setPinLength(int length) async {
    final safeLength = length == 6 ? 6 : 4;
    await _secureStorage.write(key: _pinLengthKey, value: '$safeLength');
  }

  Future<void> setCredential(String method, String secret, {int? pinLength}) async {
    final normalized = _normalizedMethod(method);
    if (secret.isEmpty) throw ArgumentError('Credential must not be empty.');
    if (normalized == 'pin') {
      final expectedLength = pinLength == 6 ? 6 : 4;
      if (!RegExp(r'^\d+$').hasMatch(secret) || secret.length != expectedLength) {
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
    final derived = await _pbkdf2.deriveKeyFromPassword(password: secret, nonce: salt);
    final bytes = await derived.extractBytes();
    await _secureStorage.write(key: _saltKey(normalized), value: base64Encode(salt));
    await _secureStorage.write(key: _hashKey(normalized), value: base64Encode(bytes));
  }

  Future<bool> verifyCredential(
    String method,
    String secret, {
    String? legacySecret,
  }) async {
    final normalized = _normalizedMethod(method);
    try {
      final encodedHash = await _secureStorage.read(key: _hashKey(normalized));
      final encodedSalt = await _secureStorage.read(key: _saltKey(normalized));

      // One-way migration path for installs that used the previous plaintext
      // preference fields. New writes never use those fields.
      if ((encodedHash == null || encodedSalt == null) && legacySecret != null && legacySecret.isNotEmpty) {
        if (_constantTimeEquals(secret, legacySecret)) {
          await setCredential(
            normalized,
            secret,
            pinLength: normalized == 'pin' ? secret.length : null,
          );
          return true;
        }
        return false;
      }

      if (encodedHash == null || encodedSalt == null) return false;
      final salt = base64Decode(encodedSalt);
      final expected = base64Decode(encodedHash);
      final derived = await _pbkdf2.deriveKeyFromPassword(password: secret, nonce: salt);
      final actual = await derived.extractBytes();
      return _constantTimeBytesEqual(actual, expected);
    } catch (_) {
      return false;
    }
  }

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

  Future<bool> authenticateBiometric({String reason = 'Authenticate to unlock Chaty'}) async {
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

  Future<bool> authenticateDeviceCredential({String reason = 'Use your device lock to unlock Chaty'}) async {
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

  bool _isValidPattern(String pattern) {
    final values = pattern.split('-').where((value) => value.isNotEmpty).toList(growable: false);
    if (values.length < 4) return false;
    final parsed = values.map(int.tryParse).toList(growable: false);
    if (parsed.any((value) => value == null || value! < 0 || value > 8)) return false;
    return parsed.toSet().length == parsed.length;
  }

  bool _constantTimeEquals(String a, String b) => _constantTimeBytesEqual(utf8.encode(a), utf8.encode(b));

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
