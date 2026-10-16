import 'package:flutter/foundation.dart';

/// Target use case for audio session routing.
enum AudioUseCase {
  media,
  communication,
  notification,
}

/// Logical audio output route.
enum AudioOutputRoute {
  systemDefault,
  receiver,
  speaker,
  bluetooth,
  wiredHeadset,
  usb,
}

/// Status of dual-output (earpiece + loudspeaker) capability.
enum DualOutputStatus {
  active,
  unavailable,
  unsupported,
  notVerified,
  failed,
}

/// Capability report for an audio use case.
@immutable
class AudioCapabilities {
  final List<AudioOutputRoute> availableRoutes;
  final AudioOutputRoute activeRoute;
  final DualOutputStatus dualOutputStatus;
  final String? failureReason;
  final bool isStereoSupported;
  final String platform;

  const AudioCapabilities({
    required this.availableRoutes,
    required this.activeRoute,
    required this.dualOutputStatus,
    this.failureReason,
    this.isStereoSupported = false,
    this.platform = 'unknown',
  });

  AudioCapabilities copyWith({
    List<AudioOutputRoute>? availableRoutes,
    AudioOutputRoute? activeRoute,
    DualOutputStatus? dualOutputStatus,
    String? failureReason,
    bool? isStereoSupported,
    String? platform,
  }) {
    return AudioCapabilities(
      availableRoutes: availableRoutes ?? this.availableRoutes,
      activeRoute: activeRoute ?? this.activeRoute,
      dualOutputStatus: dualOutputStatus ?? this.dualOutputStatus,
      failureReason: failureReason ?? this.failureReason,
      isStereoSupported: isStereoSupported ?? this.isStereoSupported,
      platform: platform ?? this.platform,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AudioCapabilities &&
          runtimeType == other.runtimeType &&
          listEquals(availableRoutes, other.availableRoutes) &&
          activeRoute == other.activeRoute &&
          dualOutputStatus == other.dualOutputStatus &&
          failureReason == other.failureReason &&
          isStereoSupported == other.isStereoSupported &&
          platform == other.platform;

  @override
  int get hashCode => Object.hash(
        Object.hashAll(availableRoutes),
        activeRoute,
        dualOutputStatus,
        failureReason,
        isStereoSupported,
        platform,
      );

  @override
  String toString() =>
      'AudioCapabilities(active: $activeRoute, dual: $dualOutputStatus, stereo: $isStereoSupported, routes: $availableRoutes)';
}

/// State machine lifecycle states for Chaty audio coordination.
enum AudioSessionState {
  idle,
  mediaActive,
  callStarting,
  receiverCall,
  speakerphoneCall,
  externalCall,
  interrupted,
  recovering,
  failed,
  releasing,
}

/// Diagnostic log event for capability and route transitions.
@immutable
class AudioPolicyDiagnosticEvent {
  final DateTime timestamp;
  final String message;
  final AudioUseCase useCase;
  final AudioOutputRoute? route;
  final AudioSessionState state;
  final String? error;

  AudioPolicyDiagnosticEvent({
    DateTime? timestamp,
    required this.message,
    required this.useCase,
    this.route,
    required this.state,
    this.error,
  }) : timestamp = timestamp ?? DateTime.now();

  @override
  String toString() =>
      '[$timestamp] [$state] ($useCase) $message ${route != null ? "route: $route" : ""} ${error != null ? "err: $error" : ""}';
}

/// User audio preference mode in settings.
enum AudioPreferenceMode {
  automatic,
  preferStereo,
  experimentalDualOutput,
}
