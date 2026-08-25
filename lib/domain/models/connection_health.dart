import 'package:flutter/foundation.dart';

/// The four core connection health states in Chaty.
enum ConnectionHealth {
  /// Green (3 bars): Network active, internet reachable, backend healthy,
  /// realtime connected, latency <= 300ms, low failure rate.
  excellent,

  /// Yellow (2 bars): Elevated latency (300-1500ms), temporary socket reconnect,
  /// intermittent packet failure, or network transition.
  weak,

  /// Red (1 bar): Internet reachable but Chaty backend unreachable,
  /// latency > 1500ms, or multiple consecutive backend request failures.
  poor,

  /// Dead (0 bars / disabled slash): No network transport or no internet reachability.
  offline,
}

/// Transport technology currently available on the device.
enum NetworkTransportType {
  wifi,
  cellular,
  ethernet,
  vpn,
  other,
  none,
}

/// Status of the Supabase Realtime WebSocket connection.
enum RealtimeSocketStatus {
  connected,
  connecting,
  reconnecting,
  disconnected,
}

/// Detailed classification of network failure reasons.
enum NetworkFailureType {
  none,
  noNetwork,
  timeout,
  dnsFailure,
  backendUnavailable,
  realtimeDisconnected,
  authenticationFailure,
  rateLimited,
  unknown,
}

/// Rich, immutable snapshot of the device's connection diagnostics.
@immutable
class ConnectionHealthState {
  final ConnectionHealth health;
  final NetworkTransportType transport;
  final bool hasNetwork;
  final bool internetReachable;
  final bool backendReachable;
  final RealtimeSocketStatus realtimeStatus;
  final Duration? latency;
  final int consecutiveFailures;
  final int consecutiveSuccesses;
  final int queuedMessagesCount;
  final NetworkFailureType lastFailureType;
  final String? statusMessage;
  final DateTime lastCheckedAt;

  const ConnectionHealthState({
    required this.health,
    this.transport = NetworkTransportType.none,
    this.hasNetwork = false,
    this.internetReachable = false,
    this.backendReachable = false,
    this.realtimeStatus = RealtimeSocketStatus.disconnected,
    this.latency,
    this.consecutiveFailures = 0,
    this.consecutiveSuccesses = 0,
    this.queuedMessagesCount = 0,
    this.lastFailureType = NetworkFailureType.none,
    this.statusMessage,
    required this.lastCheckedAt,
  });

  /// Factory for the default initial state before first probe completes.
  factory ConnectionHealthState.initial() => ConnectionHealthState(
        health: ConnectionHealth.excellent,
        hasNetwork: true,
        internetReachable: true,
        backendReachable: true,
        realtimeStatus: RealtimeSocketStatus.connected,
        transport: NetworkTransportType.wifi,
        lastCheckedAt: DateTime.now(),
      );

  ConnectionHealthState copyWith({
    ConnectionHealth? health,
    NetworkTransportType? transport,
    bool? hasNetwork,
    bool? internetReachable,
    bool? backendReachable,
    RealtimeSocketStatus? realtimeStatus,
    Duration? latency,
    int? consecutiveFailures,
    int? consecutiveSuccesses,
    int? queuedMessagesCount,
    NetworkFailureType? lastFailureType,
    String? statusMessage,
    DateTime? lastCheckedAt,
  }) {
    return ConnectionHealthState(
      health: health ?? this.health,
      transport: transport ?? this.transport,
      hasNetwork: hasNetwork ?? this.hasNetwork,
      internetReachable: internetReachable ?? this.internetReachable,
      backendReachable: backendReachable ?? this.backendReachable,
      realtimeStatus: realtimeStatus ?? this.realtimeStatus,
      latency: latency ?? this.latency,
      consecutiveFailures: consecutiveFailures ?? this.consecutiveFailures,
      consecutiveSuccesses: consecutiveSuccesses ?? this.consecutiveSuccesses,
      queuedMessagesCount: queuedMessagesCount ?? this.queuedMessagesCount,
      lastFailureType: lastFailureType ?? this.lastFailureType,
      statusMessage: statusMessage ?? this.statusMessage,
      lastCheckedAt: lastCheckedAt ?? this.lastCheckedAt,
    );
  }

  /// User-friendly label for display in UI headers and status sheets.
  String get displayTitle => switch (health) {
        ConnectionHealth.excellent => 'Connected',
        ConnectionHealth.weak => 'Weak connection',
        ConnectionHealth.poor => 'Poor connection',
        ConnectionHealth.offline => 'No internet connection',
      };

  /// User-friendly explanation string.
  String get displaySubtitle => switch (health) {
        ConnectionHealth.excellent => 'Chat service is active and fast',
        ConnectionHealth.weak => 'Messages may take longer to deliver',
        ConnectionHealth.poor => backendReachable
            ? 'High latency or intermittent failures'
            : 'Chat service is temporarily unreachable',
        ConnectionHealth.offline => 'Messages will send when you\'re back online',
      };

  /// Screen reader semantic label.
  String get accessibilityLabel => switch (health) {
        ConnectionHealth.excellent => 'Connection excellent',
        ConnectionHealth.weak => 'Connection weak',
        ConnectionHealth.poor => 'Connection poor',
        ConnectionHealth.offline => 'No internet connection',
      };

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ConnectionHealthState &&
          runtimeType == other.runtimeType &&
          health == other.health &&
          transport == other.transport &&
          hasNetwork == other.hasNetwork &&
          internetReachable == other.internetReachable &&
          backendReachable == other.backendReachable &&
          realtimeStatus == other.realtimeStatus &&
          latency == other.latency &&
          consecutiveFailures == other.consecutiveFailures &&
          consecutiveSuccesses == other.consecutiveSuccesses &&
          queuedMessagesCount == other.queuedMessagesCount &&
          lastFailureType == other.lastFailureType;

  @override
  int get hashCode => Object.hash(
        health,
        transport,
        hasNetwork,
        internetReachable,
        backendReachable,
        realtimeStatus,
        latency,
        consecutiveFailures,
        consecutiveSuccesses,
        queuedMessagesCount,
        lastFailureType,
      );
}
