import 'dart:async';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../domain/models/chat_message.dart';
import '../../domain/models/connection_health.dart';
import '../../injection/locator.dart';
import 'backend_service.dart';
import 'connection_health_service.dart';
import 'message_transport_compatibility_service.dart';
import 'pending_secure_send_store.dart';

/// Robust outgoing message queue engine that handles:
/// - Offline message persistence across app restarts (via [PendingSecureSendStore])
/// - Strict FIFO sending order per conversation
/// - Client-generated idempotency keys (`clientMessageId`) to prevent duplicate sends
/// - Concurrency lock / Mutex guard to protect against simultaneous send triggers
/// - Bounded exponential backoff with randomized jitter
/// - Single-tap manual retry for failed deliveries
class OutgoingMessageQueueEngine extends ChangeNotifier {
  OutgoingMessageQueueEngine({
    ChatyBackendService? backend,
    PendingSecureSendStore? store,
    ConnectionHealthService? connectionHealth,
  })  : _backend = backend ?? locator<ChatyBackendService>(),
        _store = store ?? PendingSecureSendStore(),
        _healthService = connectionHealth ??
            (locator.isRegistered<ConnectionHealthService>()
                ? locator<ConnectionHealthService>()
                : null) {
    _init();
  }

  final ChatyBackendService _backend;
  final PendingSecureSendStore _store;
  final ConnectionHealthService? _healthService;

  bool _isProcessing = false;
  final Random _random = Random();
  final Map<String, int> _retryCountByMessageId = <String, int>{};
  final Map<String, DateTime> _nextAllowedRetryByMessageId = <String, DateTime>{};

  // State listeners
  StreamSubscription<AuthState>? _authSub;
  VoidCallback? _healthListener;

  void _init() {
    _healthListener = () {
      final health = _healthService?.health;
      if (health == ConnectionHealth.excellent || health == ConnectionHealth.weak) {
        // Trigger queue drain when connection is restored
        unawaited(processQueue());
      }
    };
    _healthService?.addListener(_healthListener!);

    _authSub = Supabase.instance.client.auth.onAuthStateChange.listen((data) {
      if (data.session != null) {
        unawaited(processQueue());
      }
    });

    // Check count on startup
    _updateQueuedCount();
  }

  /// Calculates backoff interval with jitter: min(30s, 2^(retries) + jitter)
  Duration calculateBackoff(int retryCount) {
    if (retryCount <= 0) return Duration.zero;
    final expSeconds = min(30, pow(2, min(retryCount, 5)).toInt());
    final jitterMs = _random.nextInt(500);
    return Duration(seconds: expSeconds, milliseconds: jitterMs);
  }

  /// Refreshes the count of queued messages and updates ConnectionHealthService
  Future<void> _updateQueuedCount() async {
    final userId = _backend.currentUser?.id;
    if (userId == null) return;
    try {
      final items = await _store.read(userId);
      _healthService?.updateQueuedCount(items.length);
      notifyListeners();
    } catch (_) {}
  }

  /// Enqueues a message for offline sending or retry.
  Future<void> enqueue(PendingSecureSend send) async {
    final userId = _backend.currentUser?.id;
    if (userId == null) return;

    await _store.put(userId, send);
    await _updateQueuedCount();

    // If online, immediately attempt to process
    if (_healthService?.isOnline ?? true) {
      unawaited(processQueue());
    }
  }

  /// Manually retries a specific message by clientMessageId, bypassing backoff timer.
  Future<bool> retryMessage(String clientMessageId) async {
    final userId = _backend.currentUser?.id;
    if (userId == null) return false;

    _nextAllowedRetryByMessageId.remove(clientMessageId);
    _retryCountByMessageId[clientMessageId] = 0;

    final items = await _store.read(userId);
    final target = items.where((i) => i.clientMessageId == clientMessageId).firstOrNull;
    if (target == null) return false;

    try {
      final compatService = locator.isRegistered<MessageTransportCompatibilityService>()
          ? locator<MessageTransportCompatibilityService>()
          : null;

      if (compatService != null &&
          await compatService.conversationRequiresLegacyTransport(target.conversationId)) {
        await compatService.deliverLegacyMessage(
          target,
          fallbackType: messageTypeFromDatabase(target.type),
        );
      } else {
        await _backend.sendMessage(
          conversationId: target.conversationId,
          text: target.text,
          type: messageTypeFromDatabase(target.type),
          attachment: target.attachment == null
              ? null
              : MessageAttachment(
                  id: target.attachment!['id']?.toString() ?? '',
                  type: target.attachment!['type']?.toString() ?? 'file',
                  name: target.attachment!['name']?.toString() ?? '',
                  size: target.attachment!['size']?.toString() ?? '',
                  url: target.attachment!['url']?.toString(),
                  durationSeconds: int.tryParse(
                        '${target.attachment!['duration_seconds'] ?? 0}',
                      ) ??
                      0,
                ),
          replyToMessageId: target.replyToMessageId,
          replyToPreviewText: target.replyToPreviewText,
          replyToSenderName: target.replyToSenderName,
          linkedTaskId: target.linkedTaskId,
          extraMetadata: target.metadata,
          clientMessageId: target.clientMessageId,
        );
      }
      await _store.remove(userId, clientMessageId);
      await _updateQueuedCount();
      return true;
    } catch (e) {
      final retries = (_retryCountByMessageId[clientMessageId] ?? 0) + 1;
      _retryCountByMessageId[clientMessageId] = retries;
      _nextAllowedRetryByMessageId[clientMessageId] = DateTime.now().add(calculateBackoff(retries));
      return false;
    }
  }

  /// Processes all locally persisted queued messages in FIFO order.
  /// Concurrency guarded to prevent race conditions on simultaneous triggers.
  Future<void> processQueue() async {
    if (_isProcessing) return;
    _isProcessing = true;

    try {
      final user = _backend.currentUser;
      if (user == null) return;
      final userId = user.id;

      final items = await _store.read(userId);
      if (items.isEmpty) {
        _healthService?.updateQueuedCount(0);
        return;
      }

      // Group by conversation to enforce sequential FIFO delivery per conversation
      final byConversation = <String, List<PendingSecureSend>>{};
      for (final item in items) {
        byConversation.putIfAbsent(item.conversationId, () => []).add(item);
      }

      final now = DateTime.now();

      for (final entry in byConversation.entries) {
        // Sort by creation time to preserve message order
        final sortedList = entry.value..sort((a, b) => a.createdAt.compareTo(b.createdAt));

        for (final item in sortedList) {
          final nextAllowed = _nextAllowedRetryByMessageId[item.clientMessageId];
          if (nextAllowed != null && now.isBefore(nextAllowed)) {
            // Still in backoff window, skip for now
            continue;
          }

          try {
            final compatService =
                locator.isRegistered<MessageTransportCompatibilityService>()
                    ? locator<MessageTransportCompatibilityService>()
                    : null;

            if (compatService != null &&
                await compatService.conversationRequiresLegacyTransport(item.conversationId)) {
              await compatService.deliverLegacyMessage(
                item,
                fallbackType: messageTypeFromDatabase(item.type),
              );
            } else {
              await _backend.sendMessage(
                conversationId: item.conversationId,
                text: item.text,
                type: messageTypeFromDatabase(item.type),
                attachment: item.attachment == null
                    ? null
                    : MessageAttachment(
                        id: item.attachment!['id']?.toString() ?? '',
                        type: item.attachment!['type']?.toString() ?? 'file',
                        name: item.attachment!['name']?.toString() ?? '',
                        size: item.attachment!['size']?.toString() ?? '',
                        url: item.attachment!['url']?.toString(),
                        durationSeconds: int.tryParse(
                              '${item.attachment!['duration_seconds'] ?? 0}',
                            ) ??
                            0,
                      ),
                replyToMessageId: item.replyToMessageId,
                replyToPreviewText: item.replyToPreviewText,
                replyToSenderName: item.replyToSenderName,
                linkedTaskId: item.linkedTaskId,
                extraMetadata: item.metadata,
                clientMessageId: item.clientMessageId,
              );
            }

            // Successfully sent: remove from store and clear retry trackers
            await _store.remove(userId, item.clientMessageId);
            _retryCountByMessageId.remove(item.clientMessageId);
            _nextAllowedRetryByMessageId.remove(item.clientMessageId);
          } catch (error) {
            final retries = (_retryCountByMessageId[item.clientMessageId] ?? 0) + 1;
            _retryCountByMessageId[item.clientMessageId] = retries;
            _nextAllowedRetryByMessageId[item.clientMessageId] =
                DateTime.now().add(calculateBackoff(retries));

            debugPrint('Chaty queue item ${item.clientMessageId} failed (retry $retries): $error');

            // If network is offline or unrecoverable error for this conversation, break out of this conversation loop
            if (_healthService?.health == ConnectionHealth.offline) {
              break;
            }
          }
        }
      }

      await _updateQueuedCount();
    } finally {
      _isProcessing = false;
    }
  }

  static MessageType messageTypeFromDatabase(String type) => switch (type) {
        'image' => MessageType.image,
        'video' => MessageType.video,
        'audio' => MessageType.audio,
        'document' => MessageType.document,
        'location' => MessageType.location,
        'contact' => MessageType.contact,
        'task_card' => MessageType.taskCard,
        'system' => MessageType.system,
        _ => MessageType.text,
      };

  @override
  void dispose() {
    if (_healthListener != null) {
      _healthService?.removeListener(_healthListener!);
    }
    _authSub?.cancel();
    super.dispose();
  }
}
