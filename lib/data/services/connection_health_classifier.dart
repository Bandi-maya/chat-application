import '../../domain/models/connection_health.dart';

/// Pure, deterministic classifier that evaluates raw network signals into a
/// stable, hysteresis-protected [ConnectionHealth] state.
class ConnectionHealthClassifier {
  static const int excellentLatencyMaxMs = 300;
  static const int weakLatencyMaxMs = 1500;
  static const int minSuccessesToUpgrade = 2;
  static const int minFailuresToDowngrade = 2;

  /// Classifies connection health given the latest sample and historical state.
  static ConnectionHealthState evaluate({
    required ConnectionHealthState previousState,
    required bool hasNetwork,
    required bool internetReachable,
    required bool backendReachable,
    required RealtimeSocketStatus realtimeStatus,
    required Duration? latency,
    required NetworkTransportType transport,
    required NetworkFailureType failureType,
    int queuedMessagesCount = 0,
    DateTime? timestamp,
  }) {
    final now = timestamp ?? DateTime.now();

    // 1. DEAD / OFFLINE check:
    // If there is no network interface or internet reachability test conclusively fails:
    if (!hasNetwork || !internetReachable) {
      return ConnectionHealthState(
        health: ConnectionHealth.offline,
        transport: transport,
        hasNetwork: hasNetwork,
        internetReachable: internetReachable,
        backendReachable: false,
        realtimeStatus: RealtimeSocketStatus.disconnected,
        latency: null,
        consecutiveFailures: previousState.consecutiveFailures + 1,
        consecutiveSuccesses: 0,
        queuedMessagesCount: queuedMessagesCount,
        lastFailureType: !hasNetwork
            ? NetworkFailureType.noNetwork
            : (failureType != NetworkFailureType.none
                ? failureType
                : NetworkFailureType.dnsFailure),
        lastCheckedAt: now,
      );
    }

    // 2. Authentication failure check:
    // Auth failures are routed to auth recovery, but network classification
    // shouldn't mark the physical connection as completely dead if internet is OK.
    if (failureType == NetworkFailureType.authenticationFailure) {
      return ConnectionHealthState(
        health: ConnectionHealth.weak,
        transport: transport,
        hasNetwork: hasNetwork,
        internetReachable: internetReachable,
        backendReachable: false,
        realtimeStatus: realtimeStatus,
        latency: latency,
        consecutiveFailures: previousState.consecutiveFailures + 1,
        consecutiveSuccesses: 0,
        queuedMessagesCount: queuedMessagesCount,
        lastFailureType: failureType,
        statusMessage: 'Authentication required',
        lastCheckedAt: now,
      );
    }

    // 3. Raw target health determination based on current signals:
    final ConnectionHealth targetHealth;
    final int consecutiveFailures;
    final int consecutiveSuccesses;

    if (!backendReachable) {
      // Internet is available, but Chaty backend / Supabase is unreachable (e.g. 5xx or server down)
      targetHealth = ConnectionHealth.poor;
      consecutiveFailures = previousState.consecutiveFailures + 1;
      consecutiveSuccesses = 0;
    } else {
      consecutiveFailures = 0;
      consecutiveSuccesses = previousState.consecutiveSuccesses + 1;

      final latMs = latency?.inMilliseconds ?? 0;
      if (latMs > weakLatencyMaxMs) {
        targetHealth = ConnectionHealth.poor;
      } else if (latMs > excellentLatencyMaxMs ||
          realtimeStatus == RealtimeSocketStatus.reconnecting ||
          realtimeStatus == RealtimeSocketStatus.connecting ||
          realtimeStatus == RealtimeSocketStatus.disconnected) {
        targetHealth = ConnectionHealth.weak;
      } else {
        targetHealth = ConnectionHealth.excellent;
      }
    }

    // 4. Apply Hysteresis to prevent rapid flickering:
    // - Immediate downgrade to Offline if network drops.
    // - Immediate downgrade to Poor if consecutive failures >= minFailuresToDowngrade.
    // - Requires minSuccessesToUpgrade to climb back to Excellent from Weak/Poor.
    final ConnectionHealth effectiveHealth;

    if (targetHealth == ConnectionHealth.excellent) {
      if (previousState.health == ConnectionHealth.poor ||
          previousState.health == ConnectionHealth.offline) {
        // Upgrade step-by-step: first to weak if not enough consecutive successes
        effectiveHealth = consecutiveSuccesses >= minSuccessesToUpgrade
            ? ConnectionHealth.excellent
            : ConnectionHealth.weak;
      } else if (previousState.health == ConnectionHealth.weak) {
        effectiveHealth = consecutiveSuccesses >= minSuccessesToUpgrade
            ? ConnectionHealth.excellent
            : ConnectionHealth.weak;
      } else {
        effectiveHealth = ConnectionHealth.excellent;
      }
    } else if (targetHealth == ConnectionHealth.weak) {
      if (previousState.health == ConnectionHealth.poor) {
        effectiveHealth = consecutiveSuccesses >= minSuccessesToUpgrade
            ? ConnectionHealth.weak
            : ConnectionHealth.poor;
      } else {
        effectiveHealth = ConnectionHealth.weak;
      }
    } else {
      // target is poor
      if (previousState.health == ConnectionHealth.excellent &&
          consecutiveFailures < minFailuresToDowngrade) {
        // Soft-buffer a single glitch to weak instead of immediately flashing poor
        effectiveHealth = ConnectionHealth.weak;
      } else {
        effectiveHealth = ConnectionHealth.poor;
      }
    }

    return ConnectionHealthState(
      health: effectiveHealth,
      transport: transport,
      hasNetwork: hasNetwork,
      internetReachable: internetReachable,
      backendReachable: backendReachable,
      realtimeStatus: realtimeStatus,
      latency: latency,
      consecutiveFailures: consecutiveFailures,
      consecutiveSuccesses: consecutiveSuccesses,
      queuedMessagesCount: queuedMessagesCount,
      lastFailureType: failureType,
      lastCheckedAt: now,
    );
  }
}
