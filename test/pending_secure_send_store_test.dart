import 'package:chaty/data/services/local_snapshot_cache_service.dart';
import 'package:chaty/data/services/pending_secure_send_store.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('PendingSecureSendStore', () {
    test('does not lose concurrent enqueues', () async {
      final cache = _MemorySnapshotCache(writeDelay: const Duration(milliseconds: 5));
      final store = PendingSecureSendStore(cache: cache);

      await Future.wait(<Future<void>>[
        store.put('user', _send('one', seconds: 1)),
        store.put('user', _send('two', seconds: 2)),
        store.put('user', _send('three', seconds: 3)),
      ]);

      expect(
        (await store.read('user')).map((item) => item.clientMessageId),
        <String>['one', 'two', 'three'],
      );
    });

    test('upserts by idempotency key and repairs duplicate snapshots', () async {
      final cache = _MemorySnapshotCache();
      final store = PendingSecureSendStore(cache: cache);
      await cache.writeJson(
        userId: 'user',
        scope: 'pending_secure_sends',
        value: <Map<String, dynamic>>[
          _send('same', seconds: 1).toJson(),
          _send('same', seconds: 2, text: 'latest').toJson(),
        ],
      );

      final repaired = await store.read('user');
      expect(repaired, hasLength(1));
      expect(repaired.single.text, 'latest');

      await Future.wait(<Future<void>>[
        store.put('user', _send('same', seconds: 3, text: 'retry')),
        store.put('user', _send('other', seconds: 4)),
      ]);
      final result = await store.read('user');
      expect(result, hasLength(2));
      expect(result.first.text, 'retry');
    });
  });
}

PendingSecureSend _send(
  String id, {
  required int seconds,
  String text = 'message',
}) => PendingSecureSend(
  clientMessageId: id,
  conversationId: 'conversation',
  type: 'text',
  text: text,
  metadata: const <String, dynamic>{},
  createdAt: DateTime.utc(2026, 1, 1, 0, 0, seconds),
);

class _MemorySnapshotCache extends LocalSnapshotCacheService {
  _MemorySnapshotCache({this.writeDelay = Duration.zero});

  final Duration writeDelay;
  final Map<String, Object> _values = <String, Object>{};

  String _key(String userId, String scope) => '$userId:$scope';

  @override
  Future<dynamic> readJson({
    required String userId,
    required String scope,
  }) async => _values[_key(userId, scope)];

  @override
  Future<void> writeJson({
    required String userId,
    required String scope,
    required Object value,
  }) async {
    if (writeDelay > Duration.zero) await Future<void>.delayed(writeDelay);
    _values[_key(userId, scope)] = value;
  }
}
