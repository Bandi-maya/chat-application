import 'package:flutter/foundation.dart';
import '../../domain/models/preferences.dart';
import '../../ui/core/controllers/preferences_controller.dart';

/// Centralized service coordinating granular privacy, ghost mode, and receipt suppression.
class PrivacyConfigService extends ChangeNotifier {
  PrivacyConfigService({required ChatyPreferencesController preferencesController})
      : _prefs = preferencesController {
    _prefs.addListener(_handlePreferencesChanged);
  }

  final ChatyPreferencesController _prefs;

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

  @override
  void dispose() {
    _prefs.removeListener(_handlePreferencesChanged);
    super.dispose();
  }
}
