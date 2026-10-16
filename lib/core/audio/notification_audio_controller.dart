import 'package:flutter/foundation.dart';
import 'audio_policy_coordinator.dart';

/// Handles short in-app notification sounds without reconfiguring active call audio routes.
class NotificationAudioController extends ChangeNotifier {
  final AudioPolicyCoordinator coordinator;

  NotificationAudioController({required this.coordinator});

  Future<void> playInAppNotificationSound(Future<void> Function() playAction) async {
    await coordinator.playNotificationSound(playerAction: playAction);
  }
}
