import 'package:flutter_test/flutter_test.dart';

import 'package:chat/data/services/call_foreground_service.dart';
import 'package:chat/data/services/call_lifecycle_coordinator.dart';
import 'package:chat/domain/models/call_state.dart';

ChatyCallSession sessionFor(
  CallSessionState state, {
  bool outgoing = true,
  bool video = false,
}) {
  return ChatyCallSession(
    callId: 'call-test',
    remoteUserId: 'remote-user',
    remoteDisplayName: 'Remote user',
    isVideo: video,
    isOutgoing: outgoing,
    state: state,
    startedAt: DateTime(2026, 8, 22),
  );
}

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

  group('ChatyCallForegroundService policy', () {
    test('does not start before local media is authorized', () {
      expect(
        ChatyCallForegroundService.shouldRunFor(
          sessionFor(CallSessionState.initiating),
        ),
        isFalse,
      );
      expect(
        ChatyCallForegroundService.shouldRunFor(
          sessionFor(CallSessionState.incoming, outgoing: false),
        ),
        isFalse,
      );
    });

    test('starts for outgoing ringing after media setup', () {
      expect(
        ChatyCallForegroundService.shouldRunFor(
          sessionFor(CallSessionState.ringing, outgoing: true),
        ),
        isTrue,
      );
      expect(
        ChatyCallForegroundService.shouldRunFor(
          sessionFor(CallSessionState.ringing, outgoing: false),
        ),
        isFalse,
      );
    });

    test('stays active through connected and reconnecting media states', () {
      for (final state in <CallSessionState>{
        CallSessionState.connecting,
        CallSessionState.connected,
        CallSessionState.reconnecting,
      }) {
        expect(
          ChatyCallForegroundService.shouldRunFor(sessionFor(state)),
          isTrue,
          reason: 'Foreground service should remain active for $state',
        );
      }
    });

    test('stops for terminal states and no session', () {
      expect(ChatyCallForegroundService.shouldRunFor(null), isFalse);
      for (final state in <CallSessionState>{
        CallSessionState.idle,
        CallSessionState.declined,
        CallSessionState.busy,
        CallSessionState.missed,
        CallSessionState.ended,
        CallSessionState.failed,
      }) {
        expect(
          ChatyCallForegroundService.shouldRunFor(sessionFor(state)),
          isFalse,
          reason: 'Foreground service should stop for $state',
        );
      }
    });
  });
}
