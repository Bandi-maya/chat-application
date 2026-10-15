import 'package:flutter_test/flutter_test.dart';
import 'package:chat/core/services/privacy_config_service.dart';
import 'package:chat/domain/models/preferences.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('PrivacyPreferences & Ghost Mode Engine Tests', () {
    test('Default privacy preferences enforce expected baseline', () {
      const prefs = PrivacyPreferences();
      expect(prefs.freezeLastSeen, isFalse);
      expect(prefs.hideOnlineStatus, isFalse);
      expect(prefs.hideSecondTick, isFalse);
      expect(prefs.readReceipts, isTrue); // blue ticks visible by default
      expect(prefs.showBlueTicksAfterReply, isFalse);
      expect(prefs.antiDeleteMessages, isTrue);
      expect(prefs.antiViewOnce, isTrue);
      expect(prefs.typingIndicators, isTrue);
      expect(prefs.recordingIndicators, isTrue);
    });

    test('PrivacyPreferences copyWith modifies flags correctly', () {
      var prefs = const PrivacyPreferences();
      prefs = prefs.copyWith(
        freezeLastSeen: true,
        hideOnlineStatus: true,
        hideSecondTick: true,
        showBlueTicksAfterReply: true,
        antiDeleteMessages: false,
      );

      expect(prefs.freezeLastSeen, isTrue);
      expect(prefs.hideOnlineStatus, isTrue);
      expect(prefs.hideSecondTick, isTrue);
      expect(prefs.showBlueTicksAfterReply, isTrue);
      expect(prefs.antiDeleteMessages, isFalse);
    });

    test('PrivacyConfigService reflects preferences controller state', () {
      final controller = ChatyPreferencesController();
      final service = PrivacyConfigService(preferencesController: controller);

      expect(service.ghostMode, isFalse);
      expect(service.freezeLastSeen, isFalse);
      expect(service.hideOnlineStatus, isFalse);
      expect(service.hideSecondTick, isFalse);
      expect(service.hideBlueTicks, isFalse);
      expect(service.blueTicksOnReplyOnly, isFalse);
      expect(service.antiDeleteMessages, isTrue);

      // Toggle ghost mode
      service.setGhostMode(true);
      expect(service.ghostMode, isTrue);
      expect(service.hideOnlineStatus, isTrue);

      // Toggle individual toggles
      service.setFreezeLastSeen(true);
      expect(service.freezeLastSeen, isTrue);

      service.setHideSecondTick(true);
      expect(service.hideSecondTick, isTrue);

      service.setHideBlueTicks(true);
      expect(service.hideBlueTicks, isTrue);

      service.setBlueTicksOnReply(true);
      expect(service.blueTicksOnReplyOnly, isTrue);

      service.setAntiDeleteMessages(false);
      expect(service.antiDeleteMessages, isFalse);

      service.dispose();
    });

    test('PrivacyConfigService notifies listeners on state changes', () {
      final controller = ChatyPreferencesController();
      final service = PrivacyConfigService(preferencesController: controller);

      int notifyCount = 0;
      service.addListener(() {
        notifyCount++;
      });

      service.toggleGhostMode();
      expect(notifyCount, greaterThan(0));
      expect(service.ghostMode, isTrue);

      service.dispose();
    });
  });
}
