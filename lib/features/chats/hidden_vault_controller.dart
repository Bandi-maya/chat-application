import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../data/services/local_lock_service.dart';

/// Controller managing Hidden / Locked Chats Vault access and biometric authorization.
class HiddenVaultController extends ChangeNotifier {
  HiddenVaultController({
    LocalLockService? lockService,
    FlutterSecureStorage? secureStorage,
  })  : _lockService = lockService ?? LocalLockService(),
        _secureStorage = secureStorage ?? const FlutterSecureStorage() {
    _loadLockedConversations();
  }

  static const String _storageKey = 'chaty.hidden_vault.locked_conversations';

  final LocalLockService _lockService;
  final FlutterSecureStorage _secureStorage;
  final Set<String> _lockedConversationIds = <String>{};
  bool _isUnlocked = false;

  bool get isUnlocked => _isUnlocked;
  Set<String> get lockedConversationIds => Set.unmodifiable(_lockedConversationIds);

  Future<void> _loadLockedConversations() async {
    try {
      final raw = await _secureStorage.read(key: _storageKey);
      if (raw != null && raw.isNotEmpty) {
        _lockedConversationIds.addAll(raw.split(',').where((s) => s.isNotEmpty));
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<void> _persistLockedConversations() async {
    try {
      await _secureStorage.write(
        key: _storageKey,
        value: _lockedConversationIds.join(','),
      );
    } catch (_) {}
  }

  bool isConversationLocked(String conversationId) {
    return _lockedConversationIds.contains(conversationId);
  }

  Future<bool> unlockVault() async {
    try {
      final authed = await _lockService.authenticateBiometric(
        reason: 'Authenticate to access Hidden Vault',
      );
      if (authed) {
        _isUnlocked = true;
        notifyListeners();
        return true;
      }
    } catch (e) {
      debugPrint('Biometric unlock note: $e');
    }
    return false;
  }

  void forceUnlockForSession() {
    _isUnlocked = true;
    notifyListeners();
  }

  void lockVault() {
    _isUnlocked = false;
    notifyListeners();
  }

  Future<void> toggleConversationLock(String conversationId) async {
    if (_lockedConversationIds.contains(conversationId)) {
      _lockedConversationIds.remove(conversationId);
    } else {
      _lockedConversationIds.add(conversationId);
    }
    await _persistLockedConversations();
    notifyListeners();
  }
}
