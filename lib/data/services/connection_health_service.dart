import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../domain/models/connection_health.dart';
import 'connection_health_classifier.dart';

/// Centralized networking health service that aggregates multiple signals:
/// - Transport state (NetworkInterface detection)
/// - Passive / Active internet reachability
/// - Backend Supabase ping & latency measurements
/// - Supabase Realtime channel status
/// - Hysteresis filtering and failure categorization
class ConnectionHealthService extends ChangeNotifier {
  ConnectionHealthService({SupabaseClient? client})
      : _client = client ?? _resolveSupabaseClient() {
    _state = ConnectionHealthState.initial();
    _init();
  }

  static SupabaseClient? _resolveSupabaseClient() {
    try {
      return Supabase.instance.client;
    } catch (_) {
      return null;
    }
  }

  final SupabaseClient? _client;
  ConnectionHealthState _state = ConnectionHealthState.initial();
  Timer? _periodicCheckTimer;
  Timer? _debounceTimer;
  bool _isProbing = false;
  bool _disposed = false;
  AppLifecycleListener? _lifecycleListener;
  int _queuedCount = 0;

  // Sliding latency ring buffer for median/average calculation
  final List<int> _recentLatenciesMs = <int>[];
  static const int _maxLatencySamples = 5;

  ConnectionHealthState get state => _state;
  ConnectionHealth get health => _state.health;
  bool get isOnline => _state.health != ConnectionHealth.offline;
  bool get isHealthy => _state.health == ConnectionHealth.excellent;

  void _init() {
    // Probe on startup
    probeConnection();

    // Periodic check every 30 seconds when in foreground (battery efficient)
    _periodicCheckTimer = Timer.periodic(
      const Duration(seconds: 30),
      (_) => probeConnection(),
    );

    // Lifecycle aware
    _lifecycleListener = AppLifecycleListener(
      onResume: () => probeConnection(),
      onPause: () => _debounceTimer?.cancel(),
    );
  }

  /// Updates the pending/queued messages count displayed in diagnostics and UI.
  void updateQueuedCount(int count) {
    if (_queuedCount == count) return;
    _queuedCount = count;
    _state = _state.copyWith(queuedMessagesCount: count);
    notifyListeners();
  }

  /// Notifies the health service of a Supabase Realtime status transition.
  void notifyRealtimeStatus(RealtimeSocketStatus status) {
    if (_disposed) return;
    if (_state.realtimeStatus == status) return;

    _state = ConnectionHealthClassifier.evaluate(
      previousState: _state,
      hasNetwork: _state.hasNetwork,
      internetReachable: _state.internetReachable,
      backendReachable: _state.backendReachable,
      realtimeStatus: status,
      latency: _state.latency,
      transport: _state.transport,
      failureType: status == RealtimeSocketStatus.disconnected
          ? NetworkFailureType.realtimeDisconnected
          : NetworkFailureType.none,
      queuedMessagesCount: _queuedCount,
    );
    notifyListeners();
  }

  /// Triggers a debounced network health re-check (useful after network switch).
  void scheduleProbe({Duration delay = const Duration(milliseconds: 500)}) {
    _debounceTimer?.cancel();
    _debounceTimer = Timer(delay, () => probeConnection());
  }

  /// Performs a lightweight multi-signal health check.
  Future<ConnectionHealthState> probeConnection() async {
    if (_isProbing || _disposed) return _state;
    _isProbing = true;

    try {
      // 1. Detect Network Interfaces
      NetworkTransportType transport = NetworkTransportType.none;
      bool hasNetwork = false;

      try {
        final interfaces = await NetworkInterface.list(
          includeLoopback: false,
          type: InternetAddressType.any,
        ).timeout(const Duration(seconds: 2));

        if (interfaces.isNotEmpty) {
          hasNetwork = true;
          final names = interfaces.map((i) => i.name.toLowerCase()).join(' ');
          if (names.contains('wlan') || names.contains('wi-fi') || names.contains('wifi')) {
            transport = NetworkTransportType.wifi;
          } else if (names.contains('rmnet') || names.contains('cell') || names.contains('pdp')) {
            transport = NetworkTransportType.cellular;
          } else if (names.contains('eth')) {
            transport = NetworkTransportType.ethernet;
          } else if (names.contains('tun') || names.contains('vpn')) {
            transport = NetworkTransportType.vpn;
          } else {
            transport = NetworkTransportType.other;
          }
        }
      } catch (e) {
        // Fallback: if network interface inspection fails on platform, assume network is present
        // and let subsequent reachability test verify.
        hasNetwork = true;
        transport = NetworkTransportType.other;
      }

      if (!hasNetwork) {
        _state = ConnectionHealthClassifier.evaluate(
          previousState: _state,
          hasNetwork: false,
          internetReachable: false,
          backendReachable: false,
          realtimeStatus: RealtimeSocketStatus.disconnected,
          latency: null,
          transport: NetworkTransportType.none,
          failureType: NetworkFailureType.noNetwork,
          queuedMessagesCount: _queuedCount,
        );
        notifyListeners();
        return _state;
      }

      // 2. Internet Reachability Test (Fast DNS lookup with 2.5s timeout)
      bool internetReachable = false;
      NetworkFailureType failureType = NetworkFailureType.none;

      try {
        final dnsResult = await InternetAddress.lookup('one.one.one.one')
            .timeout(const Duration(milliseconds: 2500));
        internetReachable = dnsResult.isNotEmpty && dnsResult.first.rawAddress.isNotEmpty;
      } on SocketException catch (_) {
        internetReachable = false;
        failureType = NetworkFailureType.dnsFailure;
      } on TimeoutException catch (_) {
        internetReachable = false;
        failureType = NetworkFailureType.timeout;
      } catch (_) {
        internetReachable = false;
        failureType = NetworkFailureType.unknown;
      }

      if (!internetReachable) {
        _state = ConnectionHealthClassifier.evaluate(
          previousState: _state,
          hasNetwork: true,
          internetReachable: false,
          backendReachable: false,
          realtimeStatus: RealtimeSocketStatus.disconnected,
          latency: null,
          transport: transport,
          failureType: failureType,
          queuedMessagesCount: _queuedCount,
        );
        notifyListeners();
        return _state;
      }

      // 3. Backend (Supabase) Reachability & Latency Check
      bool backendReachable = false;
      Duration? sampleLatency;

      if (_client != null) {
        final stopwatch = Stopwatch()..start();
        try {
          // Perform a fast, non-mutating head count or auth ping
          await _client.from('profiles').select('id').limit(1).timeout(
            const Duration(milliseconds: 3000),
          );
          stopwatch.stop();
          sampleLatency = stopwatch.elapsed;
          backendReachable = true;
          failureType = NetworkFailureType.none;
        } on PostgrestException catch (pe) {
          stopwatch.stop();
          if (pe.code == 'PGRST301' || pe.message.contains('JWT') || pe.code == '401') {
            backendReachable = true; // Service is up, auth needs refresh
            failureType = NetworkFailureType.authenticationFailure;
          } else {
            backendReachable = false;
            failureType = NetworkFailureType.backendUnavailable;
          }
        } on TimeoutException {
          stopwatch.stop();
          backendReachable = false;
          failureType = NetworkFailureType.timeout;
        } catch (e) {
          stopwatch.stop();
          final errStr = e.toString().toLowerCase();
          if (errStr.contains('429')) {
            backendReachable = true;
            failureType = NetworkFailureType.rateLimited;
          } else {
            backendReachable = false;
            failureType = NetworkFailureType.backendUnavailable;
          }
        }
      } else {
        // Mock / development standalone fallback
        backendReachable = true;
        sampleLatency = const Duration(milliseconds: 45);
      }

      // Calculate sliding median latency
      if (sampleLatency != null) {
        _recentLatenciesMs.add(sampleLatency.inMilliseconds);
        if (_recentLatenciesMs.length > _maxLatencySamples) {
          _recentLatenciesMs.removeAt(0);
        }
      }

      Duration? effectiveLatency;
      if (_recentLatenciesMs.isNotEmpty) {
        final sorted = List<int>.from(_recentLatenciesMs)..sort();
        final medianMs = sorted[sorted.length ~/ 2];
        effectiveLatency = Duration(milliseconds: medianMs);
      }

      _state = ConnectionHealthClassifier.evaluate(
        previousState: _state,
        hasNetwork: hasNetwork,
        internetReachable: internetReachable,
        backendReachable: backendReachable,
        realtimeStatus: backendReachable
            ? RealtimeSocketStatus.connected
            : RealtimeSocketStatus.disconnected,
        latency: effectiveLatency,
        transport: transport,
        failureType: failureType,
        queuedMessagesCount: _queuedCount,
      );

      if (kDebugMode) {
        debugPrint(
          '[Connection] transport=${_state.transport.name} '
          'internet=${_state.internetReachable} '
          'backend=${_state.backendReachable} '
          'latency=${_state.latency?.inMilliseconds}ms '
          'health=${_state.health.name}',
        );
      }

      notifyListeners();
      return _state;
    } finally {
      _isProbing = false;
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _periodicCheckTimer?.cancel();
    _debounceTimer?.cancel();
    _lifecycleListener?.dispose();
    super.dispose();
  }
}
