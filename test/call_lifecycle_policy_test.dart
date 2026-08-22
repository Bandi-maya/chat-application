import 'package:flutter_test/flutter_test.dart';

import 'package:chat/data/services/call_lifecycle_coordinator.dart';
import 'package:chat/domain/models/call_state.dart';

void main() {
  group('CallLifecycleCoordinator policy', () {
    test('only reconnecting state receives a reconnect deadline', () {
      for (final state in CallSessionState.values) {
        expect(
          CallLifecycleCoordinator.needsReconnectDeadline(state),
          state == CallSessionState.reconnecting,
          reason: 'Unexpected reconnect deadline policy for $state',
        );
      }
    });

    test('terminal states are never ended again on app detach', () {
      const terminal = <CallSessionState>{
        CallSessionState.idle,
        CallSessionState.declined,
        CallSessionState.busy,
        CallSessionState.missed,
        CallSessionState.ended,
        CallSessionState.failed,
      };

      for (final state in CallSessionState.values) {
        expect(
          CallLifecycleCoordinator.isTerminalState(state),
          terminal.contains(state),
          reason: 'Unexpected terminal-state policy for $state',
        );
      }
    });

    test('live signaling and media states remain non-terminal', () {
      const live = <CallSessionState>{
        CallSessionState.initiating,
        CallSessionState.ringing,
        CallSessionState.incoming,
        CallSessionState.connecting,
        CallSessionState.connected,
        CallSessionState.reconnecting,
      };

      for (final state in live) {
        expect(CallLifecycleCoordinator.isTerminalState(state), isFalse);
      }
    });
  });
}
