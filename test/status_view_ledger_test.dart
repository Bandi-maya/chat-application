import 'package:chat/data/services/status_service.dart';
import 'package:chat/features/updates/status_view_session.dart';
import 'package:flutter_test/flutter_test.dart';

StatusRecord status({DateTime? expiresAt, DateTime? deletedAt}) => StatusRecord(
  id: 'status-1',
  userId: 'owner-1',
  text: 'hello',
  mediaType: 'text',
  mediaPath: null,
  mediaName: null,
  mediaSize: 0,
  durationSeconds: 0,
  createdAt: DateTime.utc(2026, 1, 1),
  expiresAt: expiresAt ?? DateTime.utc(2026, 1, 2),
  deletedAt: deletedAt,
);

void main() {
  final now = DateTime.utc(2026, 1, 1, 12);

  test('records one view per valid viewer and status', () {
    final ledger = StatusViewSession();
    final update = status();
    expect(ledger.tryBegin(update, 'viewer-1', now: now), isTrue);
    expect(ledger.tryBegin(update, 'viewer-1', now: now), isFalse);
    expect(ledger.tryBegin(update, 'viewer-2', now: now), isTrue);
  });

  test('rejects owner, expired, and deleted views', () {
    final ledger = StatusViewSession();
    expect(ledger.tryBegin(status(), 'owner-1', now: now), isFalse);
    expect(
      ledger.tryBegin(
        status(expiresAt: DateTime.utc(2026, 1, 1, 11)),
        'viewer-1',
        now: now,
      ),
      isFalse,
    );
    expect(
      ledger.tryBegin(
        status(deletedAt: DateTime.utc(2026, 1, 1, 10)),
        'viewer-1',
        now: now,
      ),
      isFalse,
    );
  });

  test('failed persistence can roll back for retry', () {
    final ledger = StatusViewSession();
    final update = status();
    expect(ledger.tryBegin(update, 'viewer-1', now: now), isTrue);
    ledger.rollback(update, 'viewer-1');
    expect(ledger.tryBegin(update, 'viewer-1', now: now), isTrue);
  });
}
