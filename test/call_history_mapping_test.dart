import 'package:flutter_test/flutter_test.dart';

import 'package:chat/data/services/call_history_service.dart';
import 'package:chat/domain/models/other_models.dart';

void main() {
  group('call history row mapping', () {
    test('unconnected inbound call ending while ringing is missed', () {
      final record = mapCallHistoryRow(
        <String, dynamic>{
          'id': 'call-inbound-no-answer',
          'caller_id': 'remote-user',
          'callee_id': 'current-user',
          'kind': 'audio',
          'status': 'ended',
          'started_at': '2026-10-09T10:00:00Z',
          'connected_at': null,
          'ended_at': '2026-10-09T10:00:35Z',
        },
        'current-user',
      );

      expect(record, isNotNull);
      expect(record!.direction, CallDirection.missed);
      expect(record.durationSeconds, 0);
    });

    test('connected inbound call ending normally is incoming', () {
      final record = mapCallHistoryRow(
        <String, dynamic>{
          'id': 'call-inbound-connected',
          'caller_id': 'remote-user',
          'callee_id': 'current-user',
          'kind': 'video',
          'status': 'ended',
          'started_at': '2026-10-09T10:00:00Z',
          'connected_at': '2026-10-09T10:00:10Z',
          'ended_at': '2026-10-09T10:01:25Z',
        },
        'current-user',
      );

      expect(record, isNotNull);
      expect(record!.direction, CallDirection.incoming);
      expect(record.type, CallType.video);
      expect(record.durationSeconds, 75);
    });

    test('active or unrelated call rows are not shown as history', () {
      final active = mapCallHistoryRow(
        <String, dynamic>{
          'id': 'call-active',
          'caller_id': 'remote-user',
          'callee_id': 'current-user',
          'kind': 'audio',
          'status': 'accepted',
          'started_at': '2026-10-09T10:00:00Z',
        },
        'current-user',
      );
      final unrelated = mapCallHistoryRow(
        <String, dynamic>{
          'id': 'call-unrelated',
          'caller_id': 'someone-else',
          'callee_id': 'another-user',
          'kind': 'audio',
          'status': 'ended',
          'started_at': '2026-10-09T10:00:00Z',
        },
        'current-user',
      );

      expect(active, isNull);
      expect(unrelated, isNull);
    });
  });
}
