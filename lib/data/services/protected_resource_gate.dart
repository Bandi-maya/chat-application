import 'dart:async';
import 'package:flutter/material.dart';

import 'local_lock_service.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../features/settings/security/app_lock_overlay.dart';
import '../../features/settings/security/lock_credential_setup_modal.dart';

/// Centralized authorization and access gate for protected resources in Chaty.
///
/// Features:
/// - Single-point authorization entry (`ProtectedResourceGate.authorize`)
/// - Session unlock caching (temporary unlock window within configured session)
/// - Lock setup initiation if no credential exists
/// - Route interception & Deep-link defense
class ProtectedResourceGate {
  ProtectedResourceGate._();

  // In-memory unlocked conversation session cache with timestamp
  static final Map<String, DateTime> _unlockedConversations =
      <String, DateTime>{};

  // General unlocked session timestamp for app-level or settings changes
  static DateTime? _lastAppUnlockTime;

  static bool get hasRecentAppUnlock => _lastAppUnlockTime != null;

  /// Checks if a conversation currently has an active unlock session.
  static bool isConversationSessionActive(
    String conversationId,
    ChatyPreferencesController prefs,
  ) {
    if (!prefs.isConversationProtected(conversationId)) return true;
    final unlockedAt = _unlockedConversations[conversationId];
    if (unlockedAt == null) return false;

    final timeout = _parseTimeout(prefs.security.autoLockTimeout);
    if (timeout == Duration.zero) return false; // "Immediately" always locks
    final elapsed = DateTime.now().difference(unlockedAt);
    return elapsed < timeout;
  }

  /// Authorizes access to a protected conversation or resource.
  ///
  /// If the resource is locked:
  /// 1. Checks if credentials exist; if not, triggers setup flow.
  /// 2. If credential exists, opens the unified [AppLockOverlayModal].
  /// 3. Returns true ONLY upon verified local authentication.
  static Future<bool> authorizeConversation(
    BuildContext context, {
    required String conversationId,
    required ChatyPreferencesController preferencesController,
    LocalLockService? lockService,
    String? title,
    String? reason,
  }) async {
    // If conversation is not locked or hidden, allow instantly
    if (!preferencesController.isConversationProtected(conversationId)) {
      return true;
    }

    // Check active session window
    if (isConversationSessionActive(conversationId, preferencesController)) {
      return true;
    }

    final service = lockService ?? locator<LocalLockService>();
    final method = preferencesController.security.lockMethod;

    // Check if current lock method has credentials configured
    final hasCred = await _isMethodConfigured(service, method);
    if (!hasCred) {
      if (!context.mounted) return false;
      // Trigger setup modal
      final pinLen = await service.getPinLength();
      final setupSuccess = await LockCredentialSetupModal.show(
        context,
        method: method,
        pinLength: pinLen,
        lockService: service,
      );
      if (!setupSuccess) return false;
    }

    if (!context.mounted) return false;

    // Show authentication prompt
    final unlocked = await AppLockOverlayModal.show(
      context,
      preferencesController: preferencesController,
      lockService: service,
      title: title ?? 'Locked Chat',
      reason: reason ?? 'Authenticate to open this conversation',
    );

    if (unlocked == true) {
      _unlockedConversations[conversationId] = DateTime.now();
      _lastAppUnlockTime = DateTime.now();
      return true;
    }
    return false;
  }

  /// Authorizes general security modification (e.g. disabling lock or unhiding chat).
  static Future<bool> authorizeGeneralAction(
    BuildContext context, {
    required ChatyPreferencesController preferencesController,
    LocalLockService? lockService,
    required String title,
    required String reason,
  }) async {
    final service = lockService ?? locator<LocalLockService>();
    final method = preferencesController.security.lockMethod;

    final hasCred = await _isMethodConfigured(service, method);
    if (!hasCred) return true; // If no credential ever existed, no check needed

    if (!context.mounted) return false;

    final unlocked = await AppLockOverlayModal.show(
      context,
      preferencesController: preferencesController,
      lockService: service,
      title: title,
      reason: reason,
    );

    if (unlocked == true) {
      _lastAppUnlockTime = DateTime.now();
      return true;
    }
    return false;
  }

  /// Clears all temporary unlock sessions (called when app enters background or locks).
  static void invalidateAllSessions() {
    _unlockedConversations.clear();
    _lastAppUnlockTime = null;
  }

  /// Invalidates a specific conversation session (e.g. when leaving chat).
  static void invalidateConversationSession(String conversationId) {
    _unlockedConversations.remove(conversationId);
  }

  static Future<bool> _isMethodConfigured(
    LocalLockService service,
    String method,
  ) async {
    switch (method) {
      case 'PIN':
      case 'Pattern':
      case 'Password':
        return await service.hasCredential(method);
      case 'Biometric':
        return await service.canUseBiometrics();
      case 'Device Credential':
        return true;
      default:
        return false;
    }
  }

  static Duration _parseTimeout(String value) {
    switch (value) {
      case '15s':
        return const Duration(seconds: 15);
      case '30s':
        return const Duration(seconds: 30);
      case '1m':
        return const Duration(minutes: 1);
      case '5m':
        return const Duration(minutes: 5);
      case '15m':
        return const Duration(minutes: 15);
      default:
        return Duration.zero; // "Immediately"
    }
  }
}
