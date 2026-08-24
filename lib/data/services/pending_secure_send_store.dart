import 'dart:async';

import 'local_snapshot_cache_service.dart';

/// A user-authored message that cannot be MLS-encrypted yet because one or
/// more conversation devices have not completed MLS registration.
///
/// The payload is never uploaded in cleartext. Persistence is delegated to
/// [LocalSnapshotCacheService], which AES-256-GCM encrypts the complete list at
/// rest with a key held in platform secure storage.
class PendingSecureSend {
  const PendingSecureSend({
    required this.clientMessageId,
    required this.conversationId,
    required this.type,
    required this.text,
    required this.metadata,
    required this.createdAt,
    this.attachment,
    this.replyToMessageId,
    this.replyToPreviewText,
    this.replyToSenderName,
    this.linkedTaskId,
  });

  final String clientMessageId;
  final String conversationId;
  final String type;
  final String text;
  final Map<String, dynamic> metadata;
  final DateTime createdAt;
  final Map<String, dynamic>? attachment;
  final String? replyToMessageId;
  final String? replyToPreviewText;
  final String? replyToSenderName;
  final String? linkedTaskId;

  Map<String, dynamic> toJson() => <String, dynamic>{
    'client_message_id': clientMessageId,
    'conversation_id': conversationId,
    'type': type,
    'text': text,
    'metadata': metadata,
    'created_at': createdAt.toUtc().toIso8601String(),
    if (attachment != null) 'attachment': attachment,
    if (replyToMessageId != null) 'reply_to_message_id': replyToMessageId,
    if (replyToPreviewText != null) 'reply_to_preview_text': replyToPreviewText,
    if (replyToSenderName != null) 'reply_to_sender_name': replyToSenderName,
    if (linkedTaskId != null) 'linked_task_id': linkedTaskId,
  };

  factory PendingSecureSend.fromJson(Map<String, dynamic> json) {
    final metadata = json['metadata'] is Map
        ? Map<String, dynamic>.from(json['metadata'] as Map)
        : <String, dynamic>{};
    final attachment = json['attachment'] is Map
        ? Map<String, dynamic>.from(json['attachment'] as Map)
        : null;
    return PendingSecureSend(
      clientMessageId: json['client_message_id']?.toString() ?? '',
      conversationId: json['conversation_id']?.toString() ?? '',
      type: json['type']?.toString() ?? 'text',
      text: json['text']?.toString() ?? '',
      metadata: metadata,
      createdAt:
          DateTime.tryParse(json['created_at']?.toString() ?? '')?.toLocal() ??
          DateTime.now(),
      attachment: attachment,
      replyToMessageId: json['reply_to_message_id']?.toString(),
      replyToPreviewText: json['reply_to_preview_text']?.toString(),
      replyToSenderName: json['reply_to_sender_name']?.toString(),
      linkedTaskId: json['linked_task_id']?.toString(),
    );
  }
}

class PendingSecureSendStore {
  PendingSecureSendStore({LocalSnapshotCacheService? cache})
    : _cache = cache ?? LocalSnapshotCacheService();

  static const String _scope = 'pending_secure_sends';
  static const int maxPending = 128;
  final LocalSnapshotCacheService _cache;
  Future<void> _mutationBarrier = Future<void>.value();

  Future<List<PendingSecureSend>> read(String userId) async {
    final value = await _cache.readJson(userId: userId, scope: _scope);
    if (value is! List) return <PendingSecureSend>[];
    final byClientMessageId = <String, PendingSecureSend>{};
    for (final item in value) {
      if (item is! Map) continue;
      final pending = PendingSecureSend.fromJson(
        item.map((key, value) => MapEntry(key.toString(), value)),
      );
      if (pending.clientMessageId.isEmpty || pending.conversationId.isEmpty) {
        continue;
      }
      // A client message ID is the idempotency key. Keep only one entry even
      // if an older/corrupt snapshot contains duplicates.
      byClientMessageId[pending.clientMessageId] = pending;
    }
    final result = byClientMessageId.values.toList(growable: true);
    result.sort((a, b) => a.createdAt.compareTo(b.createdAt));
    return result;
  }

  Future<void> put(String userId, PendingSecureSend item) async {
    return _serializeMutation(() async {
      final items = await read(userId);
      final index = items.indexWhere(
        (value) => value.clientMessageId == item.clientMessageId,
      );
      if (index >= 0) {
        items[index] = item;
      } else {
        if (items.length >= maxPending) {
          throw StateError('Secure pending-send queue is full.');
        }
        items.add(item);
      }
      await _write(userId, items);
    });
  }

  Future<void> remove(String userId, String clientMessageId) async {
    return _serializeMutation(() async {
      final items = await read(userId)
        ..removeWhere((item) => item.clientMessageId == clientMessageId);
      await _write(userId, items);
    });
  }

  Future<void> clear(String userId) => _serializeMutation(
    () => _cache.writeJson(userId: userId, scope: _scope, value: const []),
  );

  /// Serializes the queue's read-modify-write operations.
  ///
  /// Multiple composer sends and background retries can run concurrently.
  /// Without this barrier, both can read the same snapshot and the last write
  /// silently drops the other's update. A failed mutation is reported to its
  /// caller but does not poison subsequent queue operations.
  Future<void> _serializeMutation(Future<void> Function() mutation) {
    final completer = Completer<void>();
    _mutationBarrier = _mutationBarrier.then((_) async {
      try {
        await mutation();
        completer.complete();
      } catch (error, stackTrace) {
        completer.completeError(error, stackTrace);
      }
    });
    return completer.future;
  }

  Future<void> _write(String userId, List<PendingSecureSend> items) {
    return _cache.writeJson(
      userId: userId,
      scope: _scope,
      value: items.map((item) => item.toJson()).toList(growable: false),
    );
  }
}

/// Canonical handle to the encrypted outgoing queue for lifecycle events
/// such as permanent account deletion.
class EncryptedMessageOutbox {
  final PendingSecureSendStore _store = PendingSecureSendStore();

  /// Removes every queued encrypted message for [userId].
  Future<void> clear(String userId) => _store.clear(userId);
}
