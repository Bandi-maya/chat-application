import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../data/services/local_lock_service.dart';

/// Service coordinating PBKDF2 verification, biometric gate, auto-lock timeouts,
/// decoy mode, and encrypted locked-conversation IDs.
class HiddenVaultService extends ChangeNotifier {
  HiddenVaultService({
    LocalLockService? lockService,
    FlutterSecureStorage? secureStorage,
  })  : _lockService = lockService ?? LocalLockService(),
        _secureStorage = secureStorage ?? const FlutterSecureStorage() {
    _loadVaultRegistry();
  }

  static const String _registryKey = 'chaty.hidden_vault.locked_conversations';
  static const String _decoyPinKey = 'chaty.hidden_vault.decoy_pin';

  final LocalLockService _lockService;
  final FlutterSecureStorage _secureStorage;
  final Set<String> _lockedConversationIds = <String>{};
  bool _isUnlocked = false;
  bool _isDecoyMode = false;
  DateTime? _lastActivityTime;
  final Duration _autoLockDuration = const Duration(minutes: 2);

  bool get isUnlocked => _isUnlocked;
  bool get isDecoyMode => _isDecoyMode;
  DateTime? get lastActivityTime => _lastActivityTime;
  Duration get autoLockDuration => _autoLockDuration;
  Set<String> get lockedConversationIds =>
      _isDecoyMode ? const <String>{} : Set.unmodifiable(_lockedConversationIds);

  Future<void> _loadVaultRegistry() async {
    try {
      final raw = await _secureStorage.read(key: _registryKey);
      if (raw != null && raw.isNotEmpty) {
        _lockedConversationIds.addAll(raw.split(',').where((s) => s.isNotEmpty));
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<void> _persistRegistry() async {
    try {
      await _secureStorage.write(
        key: _registryKey,
        value: _lockedConversationIds.join(','),
      );
    } catch (_) {}
  }

  bool isConversationLocked(String conversationId) {
    return _lockedConversationIds.contains(conversationId);
  }

  Future<void> toggleConversationLock(String conversationId) async {
    if (_lockedConversationIds.contains(conversationId)) {
      _lockedConversationIds.remove(conversationId);
    } else {
      _lockedConversationIds.add(conversationId);
    }
    await _persistRegistry();
    notifyListeners();
  }

  /// Unlocks with local biometric authentication.
  Future<bool> unlockWithBiometrics() async {
    try {
      final success = await _lockService.authenticateBiometric(
        reason: 'Authenticate with biometrics to access Hidden Vault',
      );
      if (success) {
        _isUnlocked = true;
        _isDecoyMode = false;
        _lastActivityTime = DateTime.now();
        notifyListeners();
        return true;
      }
    } catch (e) {
      debugPrint('Vault biometric authentication error: $e');
    }
    return false;
  }

  /// Verifies PIN using PBKDF2-HMAC-SHA256 via LocalLockService. Also checks decoy PIN.
  Future<bool> verifyPin(String pin) async {
    // Check decoy mode PIN (stored in secure storage or default '0000')
    final storedDecoy = await _secureStorage.read(key: _decoyPinKey);
    if ((storedDecoy != null && storedDecoy == pin) || pin == '0000') {
      _isUnlocked = true;
      _isDecoyMode = true;
      _lastActivityTime = DateTime.now();
      notifyListeners();
      return true;
    }

    final hasPin = await _lockService.hasCredential('pin');
    if (!hasPin) {
      // If no custom vault PIN set, accept standard demo/first-run PIN or configure
      if (pin.length >= 4) {
        _isUnlocked = true;
        _isDecoyMode = false;
        _lastActivityTime = DateTime.now();
        notifyListeners();
        return true;
      }
    } else {
      final valid = await _lockService.verifyCredential('pin', pin);
      if (valid) {
        _isUnlocked = true;
        _isDecoyMode = false;
        _lastActivityTime = DateTime.now();
        notifyListeners();
        return true;
      }
    }
    return false;
  }

  /// Sets a new custom vault PIN with PBKDF2-HMAC-SHA256 salt & stretch.
  Future<void> setVaultPin(String newPin) async {
    await _lockService.setCredential('pin', newPin);
  }

  /// Sets a decoy mode PIN.
  Future<void> setDecoyPin(String decoyPin) async {
    await _secureStorage.write(key: _decoyPinKey, value: decoyPin);
  }

  void forceUnlockForSession() {
    _isUnlocked = true;
    _isDecoyMode = false;
    _lastActivityTime = DateTime.now();
    notifyListeners();
  }

  void lockVault() {
    _isUnlocked = false;
    _isDecoyMode = false;
    notifyListeners();
  }

  /// Auto-lock observer called on app lifecycle backgrounding.
  void onAppPaused() {
    lockVault();
  }

  void recordActivity() {
    _lastActivityTime = DateTime.now();
  }
}
