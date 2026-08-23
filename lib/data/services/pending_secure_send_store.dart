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

  Future<List<PendingSecureSend>> read(String userId) async {
    final value = await _cache.readJson(userId: userId, scope: _scope);
    if (value is! List) return <PendingSecureSend>[];
    final result = <PendingSecureSend>[];
    for (final item in value) {
      if (item is! Map) continue;
      final pending = PendingSecureSend.fromJson(
        item.map((key, value) => MapEntry(key.toString(), value)),
      );
      if (pending.clientMessageId.isEmpty || pending.conversationId.isEmpty) {
        continue;
      }
      result.add(pending);
    }
    result.sort((a, b) => a.createdAt.compareTo(b.createdAt));
    return result;
  }

  Future<void> put(String userId, PendingSecureSend item) async {
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
  }

  Future<void> remove(String userId, String clientMessageId) async {
    final items = await read(userId)
      ..removeWhere((item) => item.clientMessageId == clientMessageId);
    await _write(userId, items);
  }

  Future<void> clear(String userId) =>
      _cache.writeJson(userId: userId, scope: _scope, value: const []);

  Future<void> _write(String userId, List<PendingSecureSend> items) {
    return _cache.writeJson(
      userId: userId,
      scope: _scope,
      value: items.map((item) => item.toJson()).toList(growable: false),
    );
  }
}
