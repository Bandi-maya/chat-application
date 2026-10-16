import 'package:flutter/foundation.dart';
import 'audio_policy_coordinator.dart';

/// Coordinates media audio playback, ensuring stereo preservation and audio focus rules.
class MediaAudioController extends ChangeNotifier {
  final AudioPolicyCoordinator coordinator;

  MediaAudioController({required this.coordinator});

  Future<bool> requestMediaFocus() async {
    return await coordinator.startMediaSession();
  }

  Future<void> abandonMediaFocus() async {
    await coordinator.stopMediaSession();
  }
}
