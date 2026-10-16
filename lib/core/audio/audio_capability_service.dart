import 'dart:async';
import 'package:flutter/foundation.dart';
import 'audio_types.dart';
import 'chaty_audio_platform.dart';

/// Service responsible for querying, caching, and streaming hardware and OS audio capabilities.
class AudioCapabilityService extends ChangeNotifier {
  final ChatyAudioPlatform _platform;

  final Map<AudioUseCase, AudioCapabilities> _cachedCapabilities = {};
  StreamSubscription<AudioCapabilities>? _capabilitySub;
  StreamSubscription<AudioOutputRoute>? _routeSub;

  AudioCapabilityService({
    ChatyAudioPlatform? platform,
  }) : _platform = platform ?? MethodChannelChatyAudioPlatform() {
    _initStreams();
  }

  void _initStreams() {
    _capabilitySub = _platform.capabilityChanges.listen(
      (caps) {
        _cachedCapabilities[AudioUseCase.communication] = caps;
        notifyListeners();
      },
      onError: (_) {},
    );
    _routeSub = _platform.routeChanges.listen(
      (route) {
        final current = _cachedCapabilities[AudioUseCase.communication];
        if (current != null) {
          _cachedCapabilities[AudioUseCase.communication] =
              current.copyWith(activeRoute: route);
          notifyListeners();
        }
      },
      onError: (_) {},
    );
  }

  /// Get capabilities for a use case, returning cache if available or querying platform.
  Future<AudioCapabilities> getCapabilities(AudioUseCase useCase) async {
    final cached = _cachedCapabilities[useCase];
    if (cached != null) return cached;
    final queried = await _platform.getCapabilities(useCase);
    _cachedCapabilities[useCase] = queried;
    notifyListeners();
    return queried;
  }

  /// Active route for communication use case.
  AudioOutputRoute get activeCommunicationRoute =>
      _cachedCapabilities[AudioUseCase.communication]?.activeRoute ??
      AudioOutputRoute.systemDefault;

  /// Current dual output status for communication.
  DualOutputStatus get communicationDualStatus =>
      _cachedCapabilities[AudioUseCase.communication]?.dualOutputStatus ??
      DualOutputStatus.notVerified;

  /// Check whether independent dual-speaker output is verified active.
  bool get isDualOutputActive =>
      communicationDualStatus == DualOutputStatus.active;

  /// Available routes for communication.
  List<AudioOutputRoute> get availableCommunicationRoutes =>
      _cachedCapabilities[AudioUseCase.communication]?.availableRoutes ??
      const [
        AudioOutputRoute.systemDefault,
        AudioOutputRoute.speaker,
        AudioOutputRoute.receiver,
      ];

  /// Request route selection through the platform.
  Future<AudioCapabilities> setPreferredRoute(
    AudioUseCase useCase,
    AudioOutputRoute route,
  ) async {
    final updated = await _platform.setPreferredRoute(useCase, route);
    _cachedCapabilities[useCase] = updated;
    notifyListeners();
    return updated;
  }

  /// Release native session.
  Future<void> releaseSession(AudioUseCase useCase) async {
    await _platform.releaseSession(useCase);
    _cachedCapabilities.remove(useCase);
    notifyListeners();
  }

  @override
  void dispose() {
    _capabilitySub?.cancel();
    _routeSub?.cancel();
    super.dispose();
  }
}
