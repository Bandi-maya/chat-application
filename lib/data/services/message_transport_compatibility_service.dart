import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../domain/models/chat_message.dart';
import '../../injection/locator.dart';
import 'backend_service.dart';
import 'pending_secure_send_store.dart';

/// Handles capability detection between MLS E2EE transport and legacy transport
/// when one or more members of a conversation lack active MLS device credentials.
class MessageTransportCompatibilityService {
  MessageTransportCompatibilityService({
    SupabaseClient? client,
    ChatyBackendService? backend,
  })  : _client = client ?? Supabase.instance.client,
        _backend = backend ?? locator<ChatyBackendService>();

  final SupabaseClient _client;
  final ChatyBackendService _backend;
  final Map<String, bool> _legacyTransportCache = <String, bool>{};

  /// Probes whether [conversationId] has non-MLS participants requiring legacy transport.
  Future<bool> conversationRequiresLegacyTransport(String conversationId) async {
    if (_legacyTransportCache.containsKey(conversationId)) {
      return _legacyTransportCache[conversationId]!;
    }
    try {
      final res = await _client.rpc(
        'conversation_requires_legacy_transport',
        params: <String, dynamic>{'p_conversation_id': conversationId},
      );
      final requiresLegacy = res == true;
      _legacyTransportCache[conversationId] = requiresLegacy;
      return requiresLegacy;
    } catch (e) {
      debugPrint('Error probing legacy transport capability for $conversationId: $e');
      return false;
    }
  }

  /// Invalidates the capability cache for a conversation (e.g. when new devices register).
  void invalidateCache(String conversationId) {
    _legacyTransportCache.remove(conversationId);
  }

  /// Clears all cached transport capability states across all conversations.
  void invalidateAll() {
    _legacyTransportCache.clear();
  }

  /// Delivers a message via the authenticated legacy `send_message` RPC,
  /// preserving [send.clientMessageId] for idempotency.
  Future<ChatMessage> deliverLegacyMessage(
    PendingSecureSend send, {
    MessageType? fallbackType,
  }) async {
    final me = _backend.currentUser ??
        (throw StateError('Authentication required for legacy delivery.'));

    final raw = await _client.rpc(
      'send_message',
      params: <String, dynamic>{
        'p_conversation_id': send.conversationId,
        'p_client_message_id': send.clientMessageId,
        'p_body': send.text,
        'p_type': send.type,
        'p_metadata': send.metadata,
      },
    );

    final messageId = raw?.toString() ?? send.clientMessageId;
    await _backend.ensureConversationLoaded(send.conversationId);

    final existingMessages = _backend.getMessages(send.conversationId);
    return existingMessages.firstWhere(
      (m) => m.id == messageId || m.id == send.clientMessageId,
      orElse: () => ChatMessage(
        id: messageId,
        conversationId: send.conversationId,
        senderId: me.id,
        type: fallbackType ?? MessageType.text,
        text: send.text,
        attachment: send.attachment == null
            ? null
            : MessageAttachment(
                id: send.attachment!['id']?.toString() ?? '',
                type: send.attachment!['type']?.toString() ?? 'file',
                name: send.attachment!['name']?.toString() ?? '',
                size: send.attachment!['size']?.toString() ?? '',
                url: send.attachment!['url']?.toString(),
                durationSeconds: int.tryParse(
                      '${send.attachment!['duration_seconds'] ?? 0}',
                    ) ??
                    0,
              ),
        createdAt: send.createdAt,
        deliveryState: DeliveryState.sent,
      ),
    );
  }
}
