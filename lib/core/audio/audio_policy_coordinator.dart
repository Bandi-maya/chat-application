import 'dart:async';
import 'package:flutter/foundation.dart';
import 'audio_capability_service.dart';
import 'audio_types.dart';

/// Concurrency-safe state machine managing session ownership and route transitions.
///
/// Precedence Rules:
/// 1. Active Call owns communication session.
/// 2. Explicit User Route selection takes precedence over automatic preference.
/// 3. Connected External Devices handled according to platform routing.
/// 4. Media playback follows playback policy when no higher-priority session owns audio.
/// 5. Notifications NEVER seize ownership from an active call.
/// 6. Every session has deterministic cleanup (releaseSession).
class AudioPolicyCoordinator extends ChangeNotifier {
  final AudioCapabilityService capabilityService;

  AudioSessionState _state = AudioSessionState.idle;
  AudioUseCase? _activeOwner;
  AudioOutputRoute _activeRoute = AudioOutputRoute.systemDefault;
  final List<AudioPolicyDiagnosticEvent> _diagnostics = [];
  bool _isTransitioning = false;
  Completer<void>? _transitionLock;

  AudioPolicyCoordinator({
    required this.capabilityService,
  });

  AudioSessionState get state => _state;
  AudioUseCase? get activeOwner => _activeOwner;
  AudioOutputRoute get activeRoute => _activeRoute;
  List<AudioPolicyDiagnosticEvent> get diagnostics =>
      List.unmodifiable(_diagnostics);

  void _recordEvent(String message, {AudioUseCase? useCase, String? error}) {
    final event = AudioPolicyDiagnosticEvent(
      message: message,
      useCase: useCase ?? _activeOwner ?? AudioUseCase.media,
      route: _activeRoute,
      state: _state,
      error: error,
    );
    _diagnostics.add(event);
    if (_diagnostics.length > 100) {
      _diagnostics.removeAt(0);
    }
  }

  /// Serialized transition lock to prevent race conditions during rapid route requests.
  Future<T> _serialized<T>(Future<T> Function() action) async {
    while (_isTransitioning) {
      await _transitionLock?.future;
    }
    _isTransitioning = true;
    _transitionLock = Completer<void>();
    try {
      return await action();
    } finally {
      _isTransitioning = false;
      _transitionLock?.complete();
      _transitionLock = null;
    }
  }

  /// Acquire audio session for VoIP call.
  Future<bool> startCallSession({
    AudioOutputRoute initialRoute = AudioOutputRoute.receiver,
  }) {
    return _serialized(() async {
      _recordEvent('Starting call audio session', useCase: AudioUseCase.communication);
      _state = AudioSessionState.callStarting;
      _activeOwner = AudioUseCase.communication;
      notifyListeners();

      try {
        final caps = await capabilityService.setPreferredRoute(
          AudioUseCase.communication,
          initialRoute,
        );
        _activeRoute = caps.activeRoute;
        _state = _stateForRoute(_activeRoute);
        _recordEvent('Call audio session established', useCase: AudioUseCase.communication);
        notifyListeners();
        return true;
      } catch (e) {
        _state = AudioSessionState.failed;
        _recordEvent('Call session setup failed', useCase: AudioUseCase.communication, error: e.toString());
        notifyListeners();
        return false;
      }
    });
  }

  /// Change call audio route (receiver, speaker, bluetooth, wired headset).
  Future<bool> setCallRoute(AudioOutputRoute route) {
    return _serialized(() async {
      if (_activeOwner != AudioUseCase.communication) {
        _recordEvent('Rejected route change: call is not active owner');
        return false;
      }
      _recordEvent('Switching call route to $route', useCase: AudioUseCase.communication);
      try {
        final caps = await capabilityService.setPreferredRoute(
          AudioUseCase.communication,
          route,
        );
        _activeRoute = caps.activeRoute;
        _state = _stateForRoute(_activeRoute);
        _recordEvent('Call route successfully updated to $_activeRoute', useCase: AudioUseCase.communication);
        notifyListeners();
        return true;
      } catch (e) {
        _recordEvent('Call route update failed, falling back', useCase: AudioUseCase.communication, error: e.toString());
        // Safe fallback to speakerphone or receiver
        try {
          final fallback = await capabilityService.setPreferredRoute(
            AudioUseCase.communication,
            AudioOutputRoute.speaker,
          );
          _activeRoute = fallback.activeRoute;
          _state = _stateForRoute(_activeRoute);
          notifyListeners();
        } catch (_) {}
        return false;
      }
    });
  }

  /// Handle media playback session acquisition.
  Future<bool> startMediaSession() {
    return _serialized(() async {
      // If a call is active, call has strict precedence; do not override.
      if (_activeOwner == AudioUseCase.communication) {
        _recordEvent('Media playback cannot override active call session');
        return false;
      }
      _activeOwner = AudioUseCase.media;
      _state = AudioSessionState.mediaActive;
      _recordEvent('Media audio session active', useCase: AudioUseCase.media);
      notifyListeners();
      return true;
    });
  }

  /// Play notification sound effect without stealing communication session.
  Future<void> playNotificationSound({
    required Future<void> Function() playerAction,
  }) async {
    // If call is active, follow in-call policy: never reconfigure session.
    if (_activeOwner == AudioUseCase.communication) {
      _recordEvent('Playing in-call notification without reconfiguring communication route', useCase: AudioUseCase.notification);
      try {
        await playerAction();
      } catch (_) {}
      return;
    }

    _recordEvent('Playing notification sound', useCase: AudioUseCase.notification);
    try {
      await playerAction();
    } catch (_) {}
  }

  /// Release call session deterministically.
  Future<void> endCallSession() {
    return _serialized(() async {
      if (_activeOwner != AudioUseCase.communication) return;
      _recordEvent('Releasing call audio session', useCase: AudioUseCase.communication);
      _state = AudioSessionState.releasing;
      notifyListeners();

      try {
        await capabilityService.releaseSession(AudioUseCase.communication);
      } catch (e) {
        _recordEvent('Error releasing session: $e');
      } finally {
        _activeOwner = null;
        _activeRoute = AudioOutputRoute.systemDefault;
        _state = AudioSessionState.idle;
        _recordEvent('Call audio session cleanly released');
        notifyListeners();
      }
    });
  }

  /// Release media session.
  Future<void> stopMediaSession() {
    return _serialized(() async {
      if (_activeOwner != AudioUseCase.media) return;
      _activeOwner = null;
      _state = AudioSessionState.idle;
      _recordEvent('Media audio session stopped');
      notifyListeners();
    });
  }

  /// Handle interruptions (phone call, alarm, audio focus loss).
  void onInterruption({required bool began}) {
    if (began) {
      _recordEvent('Audio interruption began');
      _state = AudioSessionState.interrupted;
      notifyListeners();
    } else {
      _recordEvent('Audio interruption ended, recovering');
      _state = AudioSessionState.recovering;
      if (_activeOwner == AudioUseCase.communication) {
        _state = _stateForRoute(_activeRoute);
      } else if (_activeOwner == AudioUseCase.media) {
        _state = AudioSessionState.mediaActive;
      } else {
        _state = AudioSessionState.idle;
      }
      notifyListeners();
    }
  }

  static AudioSessionState _stateForRoute(AudioOutputRoute route) {
    return switch (route) {
      AudioOutputRoute.receiver => AudioSessionState.receiverCall,
      AudioOutputRoute.speaker => AudioSessionState.speakerphoneCall,
      AudioOutputRoute.bluetooth ||
      AudioOutputRoute.wiredHeadset ||
      AudioOutputRoute.usb =>
        AudioSessionState.externalCall,
      _ => AudioSessionState.receiverCall,
    };
  }
}
