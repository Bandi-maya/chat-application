#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'lib/data/services/backend_service.dart'
text = path.read_text(encoding='utf-8')

if "import 'pending_secure_send_store.dart';" not in text:
    text = text.replace(
        "import 'local_snapshot_cache_service.dart';",
        "import 'local_snapshot_cache_service.dart';\nimport 'pending_secure_send_store.dart';",
        1,
    )
if 'final PendingSecureSendStore _pendingSecureSends' not in text:
    text = text.replace(
        '  final LocalSnapshotCacheService _snapshots = LocalSnapshotCacheService();',
        '  final LocalSnapshotCacheService _snapshots = LocalSnapshotCacheService();\n'
        '  final PendingSecureSendStore _pendingSecureSends = PendingSecureSendStore();\n'
        '  bool _retryingPendingSecureSends = false;',
        1,
    )

# Restore locally encrypted pending drafts into the timeline during cache-first
# hydration so they remain visible across process death/offline launches.
hydrate_tail = '''        for (final conversationId in _conversationsById.keys) {
          await _hydrateCachedMessages(userId, conversationId);
        }
      }
      notifyListeners();'''
if hydrate_tail in text and 'await _restorePendingSecureSends(userId);' not in text:
    text = text.replace(
        hydrate_tail,
        '''        for (final conversationId in _conversationsById.keys) {
          await _hydrateCachedMessages(userId, conversationId);
        }
      }
      await _restorePendingSecureSends(userId);
      notifyListeners();''',
        1,
    )

# Retry after remote hydration/member loading has had a chance to register and
# discover all MLS devices.
refresh_marker = '''      await _subscribeRealtime();
      unawaited(_flushEncryptedOutbox());'''
if refresh_marker in text and 'unawaited(_retryPendingSecureSends(session.user.id));' not in text:
    text = text.replace(
        refresh_marker,
        '''      await _subscribeRealtime();
      unawaited(_flushEncryptedOutbox());
      unawaited(_retryPendingSecureSends(session.user.id));''',
        1,
    )

# Add optional internal retry controls without changing existing callers.
signature = '''    Map<String, dynamic>? extraMetadata,
  }) async {'''
if signature in text and 'String? clientMessageIdOverride' not in text[text.find('Future<ChatMessage> sendMessage'):text.find('Future<String> _sendEncryptedEnvelope')]:
    text = text.replace(
        signature,
        '''    Map<String, dynamic>? extraMetadata,
    String? clientMessageIdOverride,
    bool fromSecureRetry = false,
  }) async {''',
        1,
    )
text = text.replace(
    '    final clientMessageId = _uuid.v4();',
    '    final clientMessageId = clientMessageIdOverride ?? _uuid.v4();',
    1,
)

# Optimistic message appears before MLS/network work starts.
metadata_end = '''    };

    final encrypted = await locator<MlsE2eeService>().encryptPayload(
      conversationId: conversationId,
      payload: <String, dynamic>{
        'type': _messageTypeToDatabase(type),
        'text': text.trim(),
        'metadata': metadata,
      },
    );'''
if metadata_end in text:
    replacement = '''    };

    final optimistic = ChatMessage(
      id: 'pending:$clientMessageId',
      conversationId: conversationId,
      senderId: me.id,
      type: type,
      text: text.trim(),
      attachment: attachment,
      metadata: <String, dynamic>{...metadata, 'secure_setup_pending': false},
      replyToMessageId: replyToMessageId,
      replyToPreviewText: replyToPreviewText,
      replyToSenderName: replyToSenderName,
      linkedTaskId: linkedTaskId,
      createdAt: DateTime.now(),
      deliveryState: DeliveryState.sending,
    );
    _upsertLocalMessage(optimistic);
    notifyListeners();

    MlsEncryptedPayload encrypted;
    try {
      encrypted = await locator<MlsE2eeService>().encryptPayload(
        conversationId: conversationId,
        payload: <String, dynamic>{
          'type': _messageTypeToDatabase(type),
          'text': text.trim(),
          'metadata': metadata,
        },
      );
    } catch (error) {
      if (_isMlsSetupPendingError(error)) {
        final pending = PendingSecureSend(
          clientMessageId: clientMessageId,
          conversationId: conversationId,
          type: _messageTypeToDatabase(type),
          text: text.trim(),
          metadata: metadata,
          attachment: attachment == null
              ? null
              : <String, dynamic>{
                  'id': attachment.id,
                  'type': attachment.type,
                  'name': attachment.name,
                  'size': attachment.size,
                  'url': attachment.url,
                  'duration_seconds': attachment.durationSeconds,
                },
          replyToMessageId: replyToMessageId,
          replyToPreviewText: replyToPreviewText,
          replyToSenderName: replyToSenderName,
          linkedTaskId: linkedTaskId,
          createdAt: optimistic.createdAt,
        );
        if (!fromSecureRetry) {
          await _pendingSecureSends.put(me.id, pending);
        }
        _upsertLocalMessage(
          optimistic.copyWith(
            metadata: <String, dynamic>{
              ...optimistic.metadata,
              'secure_setup_pending': true,
            },
            deliveryState: DeliveryState.queued,
          ),
        );
        notifyListeners();
        return optimistic.copyWith(
          metadata: <String, dynamic>{
            ...optimistic.metadata,
            'secure_setup_pending': true,
          },
          deliveryState: DeliveryState.queued,
        );
      }
      _removeLocalMessage(conversationId, optimistic.id);
      notifyListeners();
      rethrow;
    }'''
    text = text.replace(metadata_end, replacement, 1)

# When ciphertext transport becomes durable, the clear application draft no
# longer needs to exist in the pre-MLS encrypted queue.
transient_return = '''      _upsertLocalMessage(queued);
      notifyListeners();'''
if transient_return in text and 'await _pendingSecureSends.remove(me.id, clientMessageId);' not in text[text.find('Future<ChatMessage> sendMessage'):text.find('Future<String> _sendEncryptedEnvelope')]:
    text = text.replace(
        transient_return,
        '''      _upsertLocalMessage(queued);
      if (fromSecureRetry) {
        await _pendingSecureSends.remove(me.id, clientMessageId);
      }
      notifyListeners();''',
        1,
    )

success_refresh = '''    await Future.wait<void>(<Future<void>>[
      _loadMessages(conversationId),
      _loadConversations(loadMembers: false),
    ]);
    notifyListeners();'''
if success_refresh in text:
    text = text.replace(
        success_refresh,
        '''    await Future.wait<void>(<Future<void>>[
      _loadMessages(conversationId),
      _loadConversations(loadMembers: false),
    ]);
    if (fromSecureRetry) {
      await _pendingSecureSends.remove(me.id, clientMessageId);
    }
    notifyListeners();''',
        1,
    )

# Helpers are kept close to local timeline mutation logic.
marker = '  void _upsertLocalMessage(ChatMessage message) {'
if 'Future<void> _restorePendingSecureSends(String userId)' not in text:
    helpers = r'''  bool _isMlsSetupPendingError(Object error) {
    final value = error.toString().toLowerCase();
    return value.contains('every conversation member must register an mls device') ||
        value.contains('keypackage') ||
        value.contains('key package') ||
        value.contains('mls device registration');
  }

  MessageAttachment? _pendingAttachment(Map<String, dynamic>? value) {
    if (value == null || value.isEmpty) return null;
    return MessageAttachment(
      id: value['id']?.toString() ?? '',
      type: value['type']?.toString() ?? 'document',
      name: value['name']?.toString() ?? 'Attachment',
      size: value['size']?.toString() ?? '',
      url: value['url']?.toString(),
      durationSeconds: _integer(value['duration_seconds']),
    );
  }

  ChatMessage _pendingSecureMessage(String userId, PendingSecureSend item) {
    return ChatMessage(
      id: 'pending:${item.clientMessageId}',
      conversationId: item.conversationId,
      senderId: userId,
      type: _messageTypeFromDatabase(item.type),
      text: item.text,
      attachment: _pendingAttachment(item.attachment),
      metadata: <String, dynamic>{
        ...item.metadata,
        'secure_setup_pending': true,
      },
      replyToMessageId: item.replyToMessageId,
      replyToPreviewText: item.replyToPreviewText,
      replyToSenderName: item.replyToSenderName,
      linkedTaskId: item.linkedTaskId,
      createdAt: item.createdAt,
      deliveryState: DeliveryState.queued,
    );
  }

  Future<void> _restorePendingSecureSends(String userId) async {
    final items = await _pendingSecureSends.read(userId);
    for (final item in items) {
      if (!_conversationsById.containsKey(item.conversationId)) continue;
      _upsertLocalMessage(_pendingSecureMessage(userId, item));
    }
  }

  Future<void> _retryPendingSecureSends(String userId) async {
    if (_retryingPendingSecureSends) return;
    _retryingPendingSecureSends = true;
    try {
      final items = await _pendingSecureSends.read(userId);
      for (final item in items) {
        if (_client.auth.currentUser?.id != userId) return;
        try {
          await sendMessage(
            conversationId: item.conversationId,
            text: item.text,
            type: _messageTypeFromDatabase(item.type),
            attachment: _pendingAttachment(item.attachment),
            replyToMessageId: item.replyToMessageId,
            replyToPreviewText: item.replyToPreviewText,
            replyToSenderName: item.replyToSenderName,
            linkedTaskId: item.linkedTaskId,
            extraMetadata: item.metadata,
            clientMessageIdOverride: item.clientMessageId,
            fromSecureRetry: true,
          );
        } catch (error, stackTrace) {
          if (!_isMlsSetupPendingError(error)) {
            debugPrint(
              'Chaty pending secure send retry deferred: $error\n$stackTrace',
            );
          }
        }
      }
    } finally {
      _retryingPendingSecureSends = false;
    }
  }

  void _removeLocalMessage(String conversationId, String messageId) {
    final timeline = _messagesByChatId[conversationId];
    if (timeline == null) return;
    _messagesByChatId[conversationId] = List<ChatMessage>.from(timeline)
      ..removeWhere((message) => message.id == messageId);
  }

'''
    if marker not in text:
        raise SystemExit('local message helper marker missing')
    text = text.replace(marker, helpers + marker, 1)

# Clear both encrypted retry layers on explicit logout.
logout_clear = '''    if (userId != null) {
      await _encryptedOutbox.clear(userId);
    }'''
if logout_clear in text and 'await _pendingSecureSends.clear(userId);' not in text:
    text = text.replace(
        logout_clear,
        '''    if (userId != null) {
      await _encryptedOutbox.clear(userId);
      await _pendingSecureSends.clear(userId);
    }''',
        1,
    )

for needle in (
    'PendingSecureSendStore _pendingSecureSends',
    "id: 'pending:$clientMessageId'",
    '_isMlsSetupPendingError(error)',
    '_retryPendingSecureSends(String userId)',
):
    if needle not in text:
        raise SystemExit(f'secure pending-send invariant missing: {needle}')

path.write_text(text, encoding='utf-8')
print('Secure pending-send UX applied.')
