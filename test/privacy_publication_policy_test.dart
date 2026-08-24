import 'dart:io';

import 'package:chat/domain/models/preferences.dart';
import 'package:chat/domain/models/privacy_publication_policy.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const policy = PrivacyPublicationPolicy();

  test('disabled activity preferences suppress publication', () {
    const preferences = PrivacyPreferences(
      readReceipts: false,
      typingIndicators: false,
      recordingIndicators: false,
      hideViewStatus: true,
    );

    expect(policy.allows(PrivacyPublication.readReceipt, preferences), isFalse);
    expect(policy.allows(PrivacyPublication.typing, preferences), isFalse);
    expect(policy.allows(PrivacyPublication.recording, preferences), isFalse);
    expect(policy.allows(PrivacyPublication.statusView, preferences), isFalse);
  });

  test('hidden presence and frozen last seen are not publishable', () {
    const preferences = PrivacyPreferences(
      freezeLastSeen: true,
      hideLastSeenAudience: 'Nobody',
      hideOnlineAudience: 'Same as Last Seen',
    );

    expect(policy.allows(PrivacyPublication.presence, preferences), isFalse);
    expect(policy.allows(PrivacyPublication.lastSeen, preferences), isFalse);
  });

  test('status view suppression happens before the network RPC', () {
    final service = File(
      'lib/data/services/status_service.dart',
    ).readAsStringSync();
    final guard = service.indexOf('if (!mayPublish) return;');
    final rpc = service.indexOf("'mark_status_viewed'", guard);

    expect(guard, greaterThanOrEqualTo(0));
    expect(rpc, greaterThan(guard));
  });
}
