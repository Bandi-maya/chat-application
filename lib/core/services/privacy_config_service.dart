import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../domain/models/preferences.dart';
import '../../domain/models/chat_message.dart';
import '../../ui/core/controllers/preferences_controller.dart';

/// Centralized service coordinating granular privacy, ghost mode, and receipt suppression.
class PrivacyConfigService extends ChangeNotifier {
  PrivacyConfigService({
    required ChatyPreferencesController preferencesController,
    FlutterSecureStorage? secureStorage,
  })  : _prefs = preferencesController,
        _secureStorage = secureStorage ?? const FlutterSecureStorage() {
    _prefs.addListener(_handlePreferencesChanged);
    _initSecureState();
  }

  static const String _frozenTimestampKey = 'chaty.privacy.frozen_timestamp';

  final ChatyPreferencesController _prefs;
  final FlutterSecureStorage _secureStorage;
  final Map<String, Set<String>> _pendingReadsByThread = <String, Set<String>>{};
  DateTime? _frozenTimestamp;
  bool _disposed = false;

  PrivacyPreferences get _privacy => _prefs.privacy;
  HomePreferences get _home => _prefs.home;

  bool get ghostMode => _home.ghostMode;
  bool get freezeLastSeen => _privacy.freezeLastSeen;
  bool get hideOnlineStatus => _privacy.hideOnlineStatus;
  bool get hideSecondTick => _privacy.hideSecondTick;
  bool get hideBlueTicks => !_privacy.readReceipts;
  bool get blueTicksOnReplyOnly => _privacy.showBlueTicksAfterReply;
  bool get antiDeleteMessages => _privacy.antiDeleteMessages;
  bool get antiViewOnce => _privacy.antiViewOnce;
  bool get hideTypingIndicator => !_privacy.typingIndicators;
  bool get hideRecordingIndicator => !_privacy.recordingIndicators;
  DateTime? get frozenTimestamp => _frozenTimestamp;

  Future<void> _initSecureState() async {
    try {
      final raw = await _secureStorage.read(key: _frozenTimestampKey);
      if (raw != null && raw.isNotEmpty) {
        _frozenTimestamp = DateTime.tryParse(raw);
        if (!_disposed) notifyListeners();
      }
    } catch (_) {}
  }

  void _handlePreferencesChanged() {
    notifyListeners();
  }

  /// Sets master Ghost Mode (suppresses live presence, typing, and read receipts).
  void setGhostMode(bool enabled) {
    _prefs.updateHome(_home.copyWith(ghostMode: enabled));
    if (enabled) {
      _prefs.updatePrivacy(_privacy.copyWith(hideOnlineStatus: true));
    }
  }

  /// Toggles master Ghost Mode.
  void toggleGhostMode() {
    setGhostMode(!ghostMode);
  }

  void setFreezeLastSeen(bool enabled) {
    _prefs.updatePrivacy(_privacy.copyWith(freezeLastSeen: enabled));
    if (enabled) {
      freezeTimestampNow();
    }
  }

  Future<void> freezeTimestampNow() async {
    _frozenTimestamp = DateTime.now();
    try {
      await _secureStorage.write(
        key: _frozenTimestampKey,
        value: _frozenTimestamp!.toIso8601String(),
      );
    } catch (_) {}
    if (!_disposed) notifyListeners();
  }

  void setHideOnlineStatus(bool enabled) {
    _prefs.updatePrivacy(_privacy.copyWith(hideOnlineStatus: enabled));
  }

  void setHideSecondTick(bool enabled) {
    _prefs.updatePrivacy(_privacy.copyWith(hideSecondTick: enabled));
  }

  void setHideBlueTicks(bool enabled) {
    _prefs.updatePrivacy(_privacy.copyWith(readReceipts: !enabled));
  }

  void setBlueTicksOnReply(bool enabled) {
    _prefs.updatePrivacy(_privacy.copyWith(showBlueTicksAfterReply: enabled));
  }

  void setAntiDeleteMessages(bool enabled) {
    _prefs.updatePrivacy(_privacy.copyWith(antiDeleteMessages: enabled));
  }

  void toggleAntiDelete() {
    setAntiDeleteMessages(!antiDeleteMessages);
  }

  void setAntiViewOnce(bool enabled) {
    _prefs.updatePrivacy(_privacy.copyWith(antiViewOnce: enabled));
  }

  void setHideTyping(bool enabled) {
    _prefs.updatePrivacy(_privacy.copyWith(typingIndicators: !enabled));
  }

  void setHideRecording(bool enabled) {
    _prefs.updatePrivacy(_privacy.copyWith(recordingIndicators: !enabled));
  }

  /// Queue a message read receipt for thread when blueTicksOnReplyOnly is active.
  void queuePendingRead(String threadId, String messageId) {
    _pendingReadsByThread.putIfAbsent(threadId, () => <String>{}).add(messageId);
  }

  bool hasPendingReads(String threadId) {
    final set = _pendingReadsByThread[threadId];
    return set != null && set.isNotEmpty;
  }

  Set<String> flushPendingReadsForThread(String threadId) {
    final pending = _pendingReadsByThread.remove(threadId) ?? <String>{};
    return pending;
  }

  /// Clean-room Anti-Revoke handler: preserves deleted/revoked message
  /// locally and marks it with the anti-revoke preserved badge.
  ChatMessage interceptRemoteMessageRevoke(ChatMessage message) {
    if (!antiDeleteMessages) {
      return message;
    }
    return message.copyWith(isRevokedBySender: true);
  }

  @override
  void dispose() {
    _disposed = true;
    _prefs.removeListener(_handlePreferencesChanged);
    super.dispose();
  }
}
