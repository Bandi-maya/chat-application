import 'package:flutter/foundation.dart';
import '../../data/services/call_signaling_service.dart';
import '../../domain/models/call_state.dart';
import 'audio_capability_service.dart';
import 'audio_policy_coordinator.dart';
import 'audio_types.dart';

/// Integrates Chaty's existing [CallSignalingService] and WebRTC media with the native audio subsystem.
///
/// Preserves acoustic echo cancellation, microphone noise suppression, and voice processing,
/// while providing capability-aware route switching (earpiece, speaker, bluetooth, wired).
class CallAudioController extends ChangeNotifier {
  final AudioPolicyCoordinator coordinator;
  final AudioCapabilityService capabilityService;
  final CallSignalingService? signalingService;

  CallAudioController({
    required this.coordinator,
    required this.capabilityService,
    this.signalingService,
  });

  AudioOutputRoute get activeRoute => coordinator.activeRoute;
  AudioSessionState get sessionState => coordinator.state;
  bool get isDualActive => capabilityService.isDualOutputActive;

  /// Start audio session coordinated with call engine.
  Future<bool> initializeCallAudio({bool isVideo = false}) async {
    final defaultRoute =
        isVideo ? AudioOutputRoute.speaker : AudioOutputRoute.receiver;
    final ok = await coordinator.startCallSession(initialRoute: defaultRoute);
    if (ok && signalingService != null) {
      final targetSignalingRoute = isVideo
          ? AudioRouteType.speaker
          : AudioRouteType.earpiece;
      try {
        await signalingService!.setAudioRoute(targetSignalingRoute);
      } catch (_) {}
    }
    notifyListeners();
    return ok;
  }

  /// Switch audio route during an active call.
  Future<bool> switchRoute(AudioOutputRoute targetRoute) async {
    final success = await coordinator.setCallRoute(targetRoute);
    if (success && signalingService != null) {
      if (targetRoute == AudioOutputRoute.speaker) {
        await signalingService!.setAudioRoute(AudioRouteType.speaker);
      } else if (targetRoute == AudioOutputRoute.receiver) {
        await signalingService!.setAudioRoute(AudioRouteType.earpiece);
      }
    }
    notifyListeners();
    return success;
  }

  /// Teardown call audio session deterministically.
  Future<void> terminateCallAudio() async {
    await coordinator.endCallSession();
    notifyListeners();
  }
}
