import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../domain/models/other_models.dart';

/// RLS-backed source of truth for the recent-calls list.
///
/// Active media/signaling state remains owned by CallSignalingService. This
/// service exposes only terminal call_sessions rows so the Calls screen never
/// depends on locally seeded or presentation-only records.
class CallHistoryService extends ChangeNotifier {
  CallHistoryService({SupabaseClient? client})
    : _client = client ?? Supabase.instance.client;

  final SupabaseClient _client;
  StreamSubscription<List<Map<String, dynamic>>>? _subscription;
  List<CallRecord> _records = const <CallRecord>[];
  String? _userId;
  Object? _lastError;

  List<CallRecord> get records => List<CallRecord>.unmodifiable(_records);
  Object? get lastError => _lastError;

  Future<void> start() async {
    final userId = _client.auth.currentUser?.id;
    if (userId == null) {
      await stop();
      return;
    }
    if (_userId == userId && _subscription != null) return;

    await _subscription?.cancel();
    _subscription = null;
    _userId = userId;
    _lastError = null;

    _subscription = _client
        .from('call_sessions')
        .stream(primaryKey: const <String>['id'])
        .order('started_at', ascending: false)
        .limit(100)
        .listen(
          (rows) {
            if (_userId != userId) return;
            final next = <CallRecord>[];
            for (final row in rows) {
              final record = _mapRow(row, userId);
              if (record != null) next.add(record);
            }
            _records = List<CallRecord>.unmodifiable(next);
            _lastError = null;
            notifyListeners();
          },
          onError: (Object error, StackTrace stackTrace) {
            _lastError = error;
            debugPrint(
              'Chaty call history realtime failed: $error\n$stackTrace',
            );
            notifyListeners();
          },
        );
  }

  Future<void> retry() => start();

  Future<void> stop() async {
    await _subscription?.cancel();
    _subscription = null;
    _userId = null;
    _records = const <CallRecord>[];
    _lastError = null;
    notifyListeners();
  }

  CallRecord? _mapRow(Map<String, dynamic> row, String currentUserId) {
    final callerId = row['caller_id']?.toString() ?? '';
    final calleeId = row['callee_id']?.toString() ?? '';
    if (callerId.isEmpty || calleeId.isEmpty) return null;
    if (callerId != currentUserId && calleeId != currentUserId) return null;

    final status = row['status']?.toString() ?? '';
    const terminal = <String>{'ended', 'declined', 'busy', 'missed', 'failed'};
    if (!terminal.contains(status)) return null;

    final startedAt =
        DateTime.tryParse(row['started_at']?.toString() ?? '')?.toLocal() ??
        DateTime.now();
    final connectedAt = DateTime.tryParse(
      row['connected_at']?.toString() ?? '',
    )?.toLocal();
    final endedAt = DateTime.tryParse(
      row['ended_at']?.toString() ?? '',
    )?.toLocal();
    final durationSeconds = connectedAt != null && endedAt != null
        ? endedAt.difference(connectedAt).inSeconds.clamp(0, 86400)
        : 0;

    final outgoing = callerId == currentUserId;
    final missedInbound =
        !outgoing &&
        (status == 'missed' ||
            status == 'busy' ||
            (connectedAt == null && status != 'ended'));

    return CallRecord(
      id: row['id']?.toString() ?? '',
      callerId: callerId,
      participantIds: <String>[callerId, calleeId],
      type: row['kind']?.toString() == 'video'
          ? CallType.video
          : CallType.voice,
      direction: outgoing
          ? CallDirection.outgoing
          : missedInbound
          ? CallDirection.missed
          : CallDirection.incoming,
      timestamp: startedAt,
      durationSeconds: durationSeconds,
      isEncrypted: true,
    );
  }

  @override
  void dispose() {
    unawaited(_subscription?.cancel());
    super.dispose();
  }
}
