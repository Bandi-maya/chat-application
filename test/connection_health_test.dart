import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/domain/models/connection_health.dart';
import 'package:chat/data/services/connection_health_classifier.dart';
import 'package:chat/ui/core/connection/connection_health_indicator.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('ConnectionHealthClassifier Boundary & Signal Tests', () {
    final baseState = ConnectionHealthState.initial();

    test('Latency boundary tests: <=300ms is Excellent, 301-1500ms is Weak, >1500ms is Poor', () {
      // 299 ms -> Excellent
      final r299 = ConnectionHealthClassifier.evaluate(
        previousState: baseState,
        hasNetwork: true,
        internetReachable: true,
        backendReachable: true,
        realtimeStatus: RealtimeSocketStatus.connected,
        latency: const Duration(milliseconds: 299),
        transport: NetworkTransportType.wifi,
        failureType: NetworkFailureType.none,
      );
      expect(r299.health, ConnectionHealth.excellent);

      // 300 ms -> Excellent
      final r300 = ConnectionHealthClassifier.evaluate(
        previousState: baseState,
        hasNetwork: true,
        internetReachable: true,
        backendReachable: true,
        realtimeStatus: RealtimeSocketStatus.connected,
        latency: const Duration(milliseconds: 300),
        transport: NetworkTransportType.wifi,
        failureType: NetworkFailureType.none,
      );
      expect(r300.health, ConnectionHealth.excellent);

      // 301 ms -> Weak
      final r301 = ConnectionHealthClassifier.evaluate(
        previousState: baseState,
        hasNetwork: true,
        internetReachable: true,
        backendReachable: true,
        realtimeStatus: RealtimeSocketStatus.connected,
        latency: const Duration(milliseconds: 301),
        transport: NetworkTransportType.wifi,
        failureType: NetworkFailureType.none,
      );
      expect(r301.health, ConnectionHealth.weak);

      // 1499 ms -> Weak
      final r1499 = ConnectionHealthClassifier.evaluate(
        previousState: baseState,
        hasNetwork: true,
        internetReachable: true,
        backendReachable: true,
        realtimeStatus: RealtimeSocketStatus.connected,
        latency: const Duration(milliseconds: 1499),
        transport: NetworkTransportType.wifi,
        failureType: NetworkFailureType.none,
      );
      expect(r1499.health, ConnectionHealth.weak);

      // 1500 ms -> Weak
      final r1500 = ConnectionHealthClassifier.evaluate(
        previousState: baseState,
        hasNetwork: true,
        internetReachable: true,
        backendReachable: true,
        realtimeStatus: RealtimeSocketStatus.connected,
        latency: const Duration(milliseconds: 1500),
        transport: NetworkTransportType.wifi,
        failureType: NetworkFailureType.none,
      );
      expect(r1500.health, ConnectionHealth.weak);

      // 1501 ms -> Poor (or soft-buffered on single glitch from excellent)
      final r1501 = ConnectionHealthClassifier.evaluate(
        previousState: r1500, // already weak
        hasNetwork: true,
        internetReachable: true,
        backendReachable: true,
        realtimeStatus: RealtimeSocketStatus.connected,
        latency: const Duration(milliseconds: 1501),
        transport: NetworkTransportType.wifi,
        failureType: NetworkFailureType.none,
      );
      expect(r1501.health, ConnectionHealth.poor);
    });

    test('Offline vs Backend Outage differentiation', () {
      // Test A: No network interface -> Offline / Dead
      final offlineNoNet = ConnectionHealthClassifier.evaluate(
        previousState: baseState,
        hasNetwork: false,
        internetReachable: false,
        backendReachable: false,
        realtimeStatus: RealtimeSocketStatus.disconnected,
        latency: null,
        transport: NetworkTransportType.none,
        failureType: NetworkFailureType.noNetwork,
      );
      expect(offlineNoNet.health, ConnectionHealth.offline);
      expect(offlineNoNet.lastFailureType, NetworkFailureType.noNetwork);

      // Test B: Internet works, but Supabase backend unreachable -> Poor (NOT Dead/Offline)
      final backendDown = ConnectionHealthClassifier.evaluate(
        previousState: baseState,
        hasNetwork: true,
        internetReachable: true,
        backendReachable: false,
        realtimeStatus: RealtimeSocketStatus.disconnected,
        latency: null,
        transport: NetworkTransportType.wifi,
        failureType: NetworkFailureType.backendUnavailable,
      );
      // Soft buffered to weak on 1st failure, then poor on 2nd
      final backendDown2 = ConnectionHealthClassifier.evaluate(
        previousState: backendDown,
        hasNetwork: true,
        internetReachable: true,
        backendReachable: false,
        realtimeStatus: RealtimeSocketStatus.disconnected,
        latency: null,
        transport: NetworkTransportType.wifi,
        failureType: NetworkFailureType.backendUnavailable,
      );
      expect(backendDown2.health, ConnectionHealth.poor);
      expect(backendDown2.health != ConnectionHealth.offline, isTrue);
    });

    test('Realtime socket reconnecting sets state to Weak', () {
      final realtimeReconnecting = ConnectionHealthClassifier.evaluate(
        previousState: baseState,
        hasNetwork: true,
        internetReachable: true,
        backendReachable: true,
        realtimeStatus: RealtimeSocketStatus.reconnecting,
        latency: const Duration(milliseconds: 80),
        transport: NetworkTransportType.cellular,
        failureType: NetworkFailureType.none,
      );
      expect(realtimeReconnecting.health, ConnectionHealth.weak);
    });

    test('Hysteresis: Upgrades require consecutive successes', () {
      final poorState = ConnectionHealthState(
        health: ConnectionHealth.poor,
        hasNetwork: true,
        internetReachable: true,
        backendReachable: false,
        consecutiveFailures: 3,
        consecutiveSuccesses: 0,
        lastCheckedAt: DateTime.now(),
      );

      // 1st success after poor -> stays weak or step up
      final step1 = ConnectionHealthClassifier.evaluate(
        previousState: poorState,
        hasNetwork: true,
        internetReachable: true,
        backendReachable: true,
        realtimeStatus: RealtimeSocketStatus.connected,
        latency: const Duration(milliseconds: 100),
        transport: NetworkTransportType.wifi,
        failureType: NetworkFailureType.none,
      );
      expect(step1.health, ConnectionHealth.weak);
      expect(step1.consecutiveSuccesses, 1);

      // 2nd consecutive success -> upgrades to Excellent
      final step2 = ConnectionHealthClassifier.evaluate(
        previousState: step1,
        hasNetwork: true,
        internetReachable: true,
        backendReachable: true,
        realtimeStatus: RealtimeSocketStatus.connected,
        latency: const Duration(milliseconds: 100),
        transport: NetworkTransportType.wifi,
        failureType: NetworkFailureType.none,
      );
      expect(step2.health, ConnectionHealth.excellent);
      expect(step2.consecutiveSuccesses, 2);
    });
  });

  group('ConnectionHealthIndicator Widget Tests', () {
    testWidgets('Renders all 4 connection states with correct semantics', (tester) async {
      for (final health in ConnectionHealth.values) {
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: Center(
                child: ConnectionHealthIndicator(health: health),
              ),
            ),
          ),
        );

        final semantics = tester.getSemantics(find.byType(ConnectionHealthIndicator));
        expect(semantics.label.isNotEmpty, isTrue);

        switch (health) {
          case ConnectionHealth.excellent:
            expect(semantics.label, 'Connection excellent');
            break;
          case ConnectionHealth.weak:
            expect(semantics.label, 'Connection weak');
            break;
          case ConnectionHealth.poor:
            expect(semantics.label, 'Connection poor');
            break;
          case ConnectionHealth.offline:
            expect(semantics.label, 'No internet connection');
            break;
        }
      }
    });
  });
}
