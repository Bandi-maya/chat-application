import 'preferences.dart';

/// Privacy-sensitive activity that could be published to another account.
enum PrivacyPublication {
  readReceipt,
  typing,
  recording,
  statusView,
  presence,
  lastSeen,
}

/// Resolves publication before a network operation is created.
///
/// This policy deliberately answers whether an event may leave the device. It
/// must not be used merely to hide an indicator after the event was sent.
class PrivacyPublicationPolicy {
  const PrivacyPublicationPolicy();

  bool allows(PrivacyPublication publication, PrivacyPreferences preferences) {
    switch (publication) {
      case PrivacyPublication.readReceipt:
        return preferences.readReceipts;
      case PrivacyPublication.typing:
        return preferences.typingIndicators;
      case PrivacyPublication.recording:
        return preferences.recordingIndicators;
      case PrivacyPublication.statusView:
        return !preferences.hideViewStatus;
      case PrivacyPublication.presence:
        return !(preferences.hideLastSeenAudience == 'Nobody' &&
            preferences.hideOnlineAudience == 'Same as Last Seen');
      case PrivacyPublication.lastSeen:
        return !preferences.freezeLastSeen &&
            preferences.hideLastSeenAudience != 'Nobody';
    }
  }
}
