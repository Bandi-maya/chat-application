import 'dart:async';
import 'package:flutter/services.dart';
import 'audio_types.dart';

/// Platform channel abstraction for native Android/iOS audio session and route control.
abstract interface class ChatyAudioPlatform {
  Future<AudioCapabilities> getCapabilities(AudioUseCase useCase);
  Future<AudioOutputRoute> getCurrentRoute(AudioUseCase useCase);
  Future<AudioCapabilities> setPreferredRoute(
    AudioUseCase useCase,
    AudioOutputRoute route,
  );
  Future<DualOutputStatus> getDualOutputStatus(AudioUseCase useCase);
  Future<void> releaseSession(AudioUseCase useCase);
  Stream<AudioCapabilities> get capabilityChanges;
  Stream<AudioOutputRoute> get routeChanges;
}

/// Production MethodChannel implementation of [ChatyAudioPlatform].
class MethodChannelChatyAudioPlatform implements ChatyAudioPlatform {
  static const MethodChannel _channel = MethodChannel('chaty/audio_routing');
  static const EventChannel _capabilitiesEventChannel =
      EventChannel('chaty/audio_capabilities_stream');
  static const EventChannel _routesEventChannel =
      EventChannel('chaty/audio_routes_stream');

  Stream<AudioCapabilities>? _capabilityStream;
  Stream<AudioOutputRoute>? _routeStream;

  @override
  Future<AudioCapabilities> getCapabilities(AudioUseCase useCase) async {
    try {
      final res = await _channel.invokeMapMethod<String, dynamic>(
        'getCapabilities',
        {'useCase': useCase.name},
      );
      if (res == null) return _defaultCapabilities(useCase);
      return _parseCapabilities(res);
    } catch (_) {
      return _defaultCapabilities(useCase);
    }
  }

  @override
  Future<AudioOutputRoute> getCurrentRoute(AudioUseCase useCase) async {
    try {
      final res = await _channel.invokeMethod<String>(
        'getCurrentRoute',
        {'useCase': useCase.name},
      );
      return _parseRoute(res);
    } catch (_) {
      return AudioOutputRoute.systemDefault;
    }
  }

  @override
  Future<AudioCapabilities> setPreferredRoute(
    AudioUseCase useCase,
    AudioOutputRoute route,
  ) async {
    try {
      final res = await _channel.invokeMapMethod<String, dynamic>(
        'setPreferredRoute',
        {
          'useCase': useCase.name,
          'route': route.name,
        },
      );
      if (res == null) return _defaultCapabilities(useCase);
      return _parseCapabilities(res);
    } catch (_) {
      return _defaultCapabilities(useCase);
    }
  }

  @override
  Future<DualOutputStatus> getDualOutputStatus(AudioUseCase useCase) async {
    try {
      final res = await _channel.invokeMethod<String>(
        'getDualOutputStatus',
        {'useCase': useCase.name},
      );
      return _parseDualStatus(res);
    } catch (_) {
      return DualOutputStatus.notVerified;
    }
  }

  @override
  Future<void> releaseSession(AudioUseCase useCase) async {
    try {
      await _channel.invokeMethod<void>(
        'releaseSession',
        {'useCase': useCase.name},
      );
    } catch (_) {
      // Ignored for release
    }
  }

  @override
  Stream<AudioCapabilities> get capabilityChanges {
    _capabilityStream ??= _capabilitiesEventChannel
        .receiveBroadcastStream()
        .where((event) => event is Map)
        .map((event) => _parseCapabilities(Map<String, dynamic>.from(event as Map)))
        .handleError((_) => _defaultCapabilities(AudioUseCase.communication));
    return _capabilityStream!;
  }

  @override
  Stream<AudioOutputRoute> get routeChanges {
    _routeStream ??= _routesEventChannel
        .receiveBroadcastStream()
        .where((event) => event is String)
        .map((event) => _parseRoute(event as String))
        .handleError((_) => AudioOutputRoute.systemDefault);
    return _routeStream!;
  }

  static AudioCapabilities _defaultCapabilities(AudioUseCase useCase) {
    return const AudioCapabilities(
      availableRoutes: [
        AudioOutputRoute.systemDefault,
        AudioOutputRoute.speaker,
        AudioOutputRoute.receiver,
      ],
      activeRoute: AudioOutputRoute.systemDefault,
      dualOutputStatus: DualOutputStatus.notVerified,
      isStereoSupported: false,
      platform: 'flutter',
    );
  }

  static AudioCapabilities _parseCapabilities(Map<String, dynamic> map) {
    final routesRaw = map['availableRoutes'] as List? ?? [];
    final routes = routesRaw.map((r) => _parseRoute(r?.toString())).toList();
    if (routes.isEmpty) {
      routes.add(AudioOutputRoute.systemDefault);
    }
    return AudioCapabilities(
      availableRoutes: routes,
      activeRoute: _parseRoute(map['activeRoute']?.toString()),
      dualOutputStatus: _parseDualStatus(map['dualOutputStatus']?.toString()),
      isStereoSupported: map['isStereoSupported'] as bool? ?? false,
      failureReason: map['failureReason']?.toString(),
      platform: map['platform']?.toString() ?? 'native',
    );
  }

  static AudioOutputRoute _parseRoute(String? val) {
    return switch (val) {
      'receiver' => AudioOutputRoute.receiver,
      'speaker' => AudioOutputRoute.speaker,
      'bluetooth' => AudioOutputRoute.bluetooth,
      'wiredHeadset' => AudioOutputRoute.wiredHeadset,
      'usb' => AudioOutputRoute.usb,
      _ => AudioOutputRoute.systemDefault,
    };
  }

  static DualOutputStatus _parseDualStatus(String? val) {
    return switch (val) {
      'active' => DualOutputStatus.active,
      'unavailable' => DualOutputStatus.unavailable,
      'unsupported' => DualOutputStatus.unsupported,
      'failed' => DualOutputStatus.failed,
      _ => DualOutputStatus.notVerified,
    };
  }
}
