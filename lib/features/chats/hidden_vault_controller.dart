import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../data/services/local_lock_service.dart';
import '../../core/services/hidden_vault_service.dart';

/// Controller managing Hidden / Locked Chats Vault access and biometric authorization.
/// Backed by HiddenVaultService.
class HiddenVaultController extends ChangeNotifier {
  HiddenVaultController({
    LocalLockService? lockService,
    FlutterSecureStorage? secureStorage,
    HiddenVaultService? vaultService,
  }) : _service = vaultService ??
            HiddenVaultService(
              lockService: lockService,
              secureStorage: secureStorage,
            ) {
    _service.addListener(_onServiceUpdated);
  }

  final HiddenVaultService _service;

  bool get isUnlocked => _service.isUnlocked;
  bool get isDecoyMode => _service.isDecoyMode;
  Set<String> get lockedConversationIds => _service.lockedConversationIds;
  HiddenVaultService get service => _service;

  void _onServiceUpdated() {
    notifyListeners();
  }

  bool isConversationLocked(String conversationId) {
    return _service.isConversationLocked(conversationId);
  }

  Future<bool> unlockVault() {
    return _service.unlockWithBiometrics();
  }

  Future<bool> verifyPin(String pin) {
    return _service.verifyPin(pin);
  }

  void forceUnlockForSession() {
    _service.forceUnlockForSession();
  }

  void lockVault() {
    _service.lockVault();
  }

  Future<void> toggleConversationLock(String conversationId) {
    return _service.toggleConversationLock(conversationId);
  }

  @override
  void dispose() {
    _service.removeListener(_onServiceUpdated);
    super.dispose();
  }
}
