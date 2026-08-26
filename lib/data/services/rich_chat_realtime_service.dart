import 'dart:async';

import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../domain/models/chat_message.dart';
import '../../domain/models/connection_health.dart';
import '../../domain/models/user_profile.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import 'backend_service.dart';
import 'connection_health_service.dart';
import 'notification_service.dart';

class ContactActivityState {
  final bool isTyping;
  final bool isRecording;
  final DateTime updatedAt;

  const ContactActivityState({
    this.isTyping = false,
    this.isRecording = false,
    required this.updatedAt,
  });
}

class RichChatRealtimeService extends ChangeNotifier {
  RichChatRealtimeService({
    required ChatyPreferencesController preferencesController,
    required ChatyNotificationService notificationService,
    required ChatyBackendService backendService,
    SupabaseClient? client,
  }) : _preferences = preferencesController,
       _notifications = notificationService,
       _backend = backendService,
       _client = client ?? Supabase.instance.client {
    _authSubscription = _client.auth.onAuthStateChange.listen((state) {
      if (state.session == null) {
        unawaited(_reset());
      } else {
        unawaited(_initializeForSession());
      }
    });
    if (_client.auth.currentSession != null) unawaited(_initializeForSession());
  }

  final ChatyPreferencesController _preferences;
  final ChatyNotificationService _notifications;
  final ChatyBackendService _backend;
  final SupabaseClient _client;

  RealtimeChannel? _channel;
  StreamSubscription<AuthState>? _authSubscription;
  final Map<String, PresenceState> _presenceByUserId =
      <String, PresenceState>{};
  final Map<String, DateTime?> _lastSeenByUserId = <String, DateTime?>{};
  final Map<String, ContactActivityState> _activityByConversationAndUser =
      <String, ContactActivityState>{};
  final Map<String, Map<String, dynamic>> _metadataByMessageId =
      <String, Map<String, dynamic>>{};
  final Map<String, DeliveryState> _deliveryStateByMessageId =
      <String, DeliveryState>{};
  final Map<String, String> _senderByMessageId = <String, String>{};
  final Set<String> _trackedConversationIds = <String>{};
  final Set<String> _revokeAlerted = <String>{};
  final Map<String, String> _profileFingerprints = <String, String>{};
  bool _disposed = false;
  bool _initializing = false;

  String? get _currentUserId => _client.auth.currentUser?.id;

  bool get isConnected => _channel != null;

  PresenceState presenceFor(String userId) =>
      _presenceByUserId[userId] ?? PresenceState.offline;

  bool isOnline(String userId) {
    if (_isMyOnlineHidden) return false;
    final state = presenceFor(userId);
    return state == PresenceState.online || state == PresenceState.typing;
  }

  DateTime? lastSeenFor(String userId) {
    if (_isMyLastSeenHidden) return null;
    return _lastSeenByUserId[userId];
  }

  bool get _isMyLastSeenHidden {
    final p = _preferences.privacy;
    return p.freezeLastSeen || p.hideLastSeenAudience == 'Nobody';
  }

  bool get _isMyOnlineHidden {
    final p = _preferences.privacy;
    return p.hideLastSeenAudience == 'Nobody' &&
        p.hideOnlineAudience == 'Same as Last Seen';
  }

  ContactActivityState activityFor(String conversationId, String userId) =>
      _activityByConversationAndUser['$conversationId:$userId'] ??
      ContactActivityState(updatedAt: DateTime.fromMillisecondsSinceEpoch(0));

  Map<String, dynamic> metadataFor(String messageId) =>
      Map<String, dynamic>.unmodifiable(
        _metadataByMessageId[messageId] ?? const <String, dynamic>{},
      );

  DeliveryState? deliveryStateFor(String messageId) =>
      _deliveryStateByMessageId[messageId];

  ChatMessage hydrateMessage(ChatMessage message) {
    final metadata = _metadataByMessageId[message.id];
    final delivery = _deliveryStateByMessageId[message.id];
    if (metadata == null && delivery == null) return message;
    return message.copyWith(
      metadata: metadata ?? message.metadata,
      deliveryState: delivery ?? message.deliveryState,
    );
  }

  Future<void> _initializeForSession() async {
    if (_initializing || _disposed || _currentUserId == null) return;
    _initializing = true;
    try {
      await _loadPresenceProjection(emitNotifications: false);
      await _subscribeRealtime();
      for (final conversationId in _trackedConversationIds.toList(
        growable: false,
      )) {
        await _loadConversationRuntime(conversationId);
      }
      if (!_disposed) notifyListeners();
    } catch (error, stackTrace) {
      debugPrint(
        'Chaty rich realtime initialization failed: $error\n$stackTrace',
      );
    } finally {
      _initializing = false;
    }
  }

  Future<void> trackConversation(String conversationId) async {
    if (conversationId.isEmpty) return;
    _trackedConversationIds.add(conversationId);
    if (_currentUserId == null) return;
    try {
      await _loadConversationRuntime(conversationId);
      if (!_disposed) notifyListeners();
    } catch (error, stackTrace) {
      debugPrint('Chaty conversation runtime load failed: $error\n$stackTrace');
    }
  }

  Future<void> _loadConversationRuntime(String conversationId) async {
    await Future.wait<void>(<Future<void>>[
      _loadMessageRuntime(conversationId),
      _loadActivity(conversationId, emitNotifications: false),
    ]);
  }

  Future<void> _loadPresenceProjection({
    required bool emitNotifications,
  }) async {
    final userId = _currentUserId;
    if (userId == null) return;
    final raw = await _client.from('contact_presence_visibility').select();
    for (final item in raw) {
      final row = Map<String, dynamic>.from(item);
      _applyPresenceRow(row, emitNotification: emitNotifications);
    }
  }

  void _applyPresenceRow(
    Map<String, dynamic> row, {
    required bool emitNotification,
  }) {
    final userId = row['owner_user_id']?.toString() ?? '';
    if (userId.isEmpty) return;
    final previous = _presenceByUserId[userId] ?? PresenceState.offline;
    final next = _presenceFromDatabase(row['presence']?.toString());
    _presenceByUserId[userId] = next;
    _lastSeenByUserId[userId] = _date(row['last_seen_at']);

    if (emitNotification &&
        previous == PresenceState.offline &&
        next == PresenceState.online &&
        (_preferences.notification.notifyContactOnline ||
            _preferences.gbBool('abu_saleh_toast_online'))) {
      final profile = _backend.getUserById(userId);
      _notifications.triggerEventNotification(
        title: '${profile?.displayName ?? 'Contact'} is online',
        body: 'Now active in Chaty',
        icon: Icons.online_prediction_rounded,
        color:
            _preferences.gbColor('abu_saleh_toast_online_bc') ??
            Colors.greenAccent,
        textColor: _preferences.gbColor('abu_saleh_toast_online_tc'),
        userId: userId,
        avatarInitials: profile?.avatarInitials,
        avatarColorHex: profile?.avatarColorHex,
      );
    }
  }

  Future<void> _loadActivity(
    String conversationId, {
    required bool emitNotifications,
  }) async {
    final rows = await _client
        .from('typing_states')
        .select()
        .eq('conversation_id', conversationId);
    final activeKeys = <String>{};
    for (final item in rows) {
      final row = Map<String, dynamic>.from(item);
      final key = _applyActivityRow(row, emitNotification: emitNotifications);
      if (key != null) activeKeys.add(key);
    }
    final prefix = '$conversationId:';
    final stale = _activityByConversationAndUser.keys
        .where((key) => key.startsWith(prefix) && !activeKeys.contains(key))
        .toList();
    for (final key in stale) {
      _activityByConversationAndUser.remove(key);
    }
  }

  String? _applyActivityRow(
    Map<String, dynamic> row, {
    required bool emitNotification,
  }) {
    final conversationId = row['conversation_id']?.toString() ?? '';
    final userId = row['user_id']?.toString() ?? '';
    if (conversationId.isEmpty || userId.isEmpty || userId == _currentUserId)
      return null;
    final key = '$conversationId:$userId';
    final previous = _activityByConversationAndUser[key];
    final next = ContactActivityState(
      isTyping: row['is_typing'] == true,
      isRecording: row['is_recording'] == true,
      updatedAt: _date(row['updated_at']) ?? DateTime.now(),
    );
    if (!next.isTyping && !next.isRecording) {
      _activityByConversationAndUser.remove(key);
      return key;
    }
    _activityByConversationAndUser[key] = next;

    final profile = _backend.getUserById(userId);
    if (emitNotification &&
        next.isTyping &&
        previous?.isTyping != true &&
        (_preferences.notification.notifyTypingStarted ||
            _preferences.gbBool('abu_saleh_toast_typing'))) {
      _notifications.triggerEventNotification(
        title: '${profile?.displayName ?? 'Contact'} is typing',
        body: 'Typing in a conversation',
        icon: Icons.keyboard_alt_outlined,
        color:
            _preferences.gbColor('abu_saleh_toast_typing_bc') ??
            Colors.blueAccent,
        textColor: _preferences.gbColor('abu_saleh_toast_typing_tc'),
        userId: userId,
        avatarInitials: profile?.avatarInitials,
        avatarColorHex: profile?.avatarColorHex,
      );
    }
    if (emitNotification &&
        next.isRecording &&
        previous?.isRecording != true &&
        _preferences.gbBool('notify_recording_started', fallback: true)) {
      _notifications.triggerEventNotification(
        title: '${profile?.displayName ?? 'Contact'} is recording',
        body: 'Recording a voice message',
        icon: Icons.mic_none_rounded,
        color: Colors.redAccent,
        userId: userId,
        avatarInitials: profile?.avatarInitials,
        avatarColorHex: profile?.avatarColorHex,
      );
    }
    return key;
  }

  Future<void> _loadMessageRuntime(String conversationId) async {
    final rows = await _client
        .from('messages')
        .select('id,sender_id,metadata')
        .eq('conversation_id', conversationId)
        .order('created_at', ascending: false)
        .limit(200);
    final messageIds = <String>[];
    for (final item in rows) {
      final row = Map<String, dynamic>.from(item);
      final messageId = row['id']?.toString() ?? '';
      if (messageId.isEmpty) continue;
      messageIds.add(messageId);
      _senderByMessageId[messageId] = row['sender_id']?.toString() ?? '';
      _metadataByMessageId[messageId] = _map(row['metadata']);
      if (_senderByMessageId[messageId] == _currentUserId) {
        _deliveryStateByMessageId[messageId] = DeliveryState.sent;
      } else {
        _deliveryStateByMessageId[messageId] = DeliveryState.delivered;
      }
    }
    if (messageIds.isEmpty) return;
    final receipts = await _client
        .from('message_receipts')
        .select('message_id,user_id,delivered_at,read_at')
        .inFilter('message_id', messageIds);
    for (final item in receipts) {
      _applyReceiptRow(Map<String, dynamic>.from(item));
    }
  }

  void _applyReceiptRow(Map<String, dynamic> row) {
    final messageId = row['message_id']?.toString() ?? '';
    if (messageId.isEmpty || _senderByMessageId[messageId] != _currentUserId)
      return;
    if (row['read_at'] != null) {
      _deliveryStateByMessageId[messageId] = DeliveryState.read;
    } else if (row['delivered_at'] != null &&
        _deliveryStateByMessageId[messageId] != DeliveryState.read) {
      _deliveryStateByMessageId[messageId] = DeliveryState.delivered;
    }
  }

  void _maybeAlertRevokedMessage(String id, Map<String, dynamic> row) {
    if (id.isEmpty || row['deleted_at'] == null) return;
    final senderId = _senderByMessageId[id] ?? '';
    if (senderId.isEmpty || senderId == _currentUserId) return;
    if (!_preferences.privacy.messageRevokeAlert ||
        !_preferences.notification.notifyMessageDeleted)
      return;
    if (!_revokeAlerted.add(id)) return;
    final profile = _backend.getUserById(senderId);
    _notifications.triggerEventNotification(
      title: '${profile?.displayName ?? 'A contact'} revoked a message',
      body: 'A message was deleted for everyone',
      icon: Icons.undo_rounded,
      color: Colors.orangeAccent,
      userId: senderId,
      avatarInitials: profile?.avatarInitials,
      avatarColorHex: profile?.avatarColorHex,
    );
  }

  Future<void> markConversationDelivered(String conversationId) async {
    if (_currentUserId == null) return;
    try {
      await _client.rpc(
        'mark_conversation_delivered',
        params: <String, dynamic>{'p_conversation_id': conversationId},
      );
    } catch (error) {
      debugPrint('Unable to mark Chaty conversation delivered: $error');
    }
  }

  Future<void> setRecording(String conversationId, bool isRecording) async {
    if (_currentUserId == null) return;
    if (isRecording &&
        (!_preferences.privacy.recordingIndicators ||
            _preferences.home.ghostMode ||
            _preferences.gbBool('yo_want_ghostmode'))) {
      return;
    }
    try {
      await _client.rpc(
        'set_recording_state',
        params: <String, dynamic>{
          'p_conversation_id': conversationId,
          'p_is_recording': isRecording,
        },
      );
    } catch (error) {
      debugPrint('Unable to publish recording state: $error');
    }
  }

  Future<void> _subscribeRealtime() async {
    final userId = _currentUserId;
    if (userId == null) return;
    final old = _channel;
    _channel = null;
    if (old != null) await _client.removeChannel(old);

    final channel = _client.channel('chaty-rich-runtime-$userId');
    channel
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'contact_presence_visibility',
          callback: (payload) {
            final row = payload.eventType == PostgresChangeEvent.delete
                ? payload.oldRecord
                : payload.newRecord;
            final owner = row['owner_user_id']?.toString() ?? '';
            if (payload.eventType == PostgresChangeEvent.delete) {
              _presenceByUserId.remove(owner);
              _lastSeenByUserId.remove(owner);
            } else {
              _applyPresenceRow(
                Map<String, dynamic>.from(row),
                emitNotification: true,
              );
            }
            if (!_disposed) notifyListeners();
          },
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'typing_states',
          callback: (payload) {
            final row = payload.eventType == PostgresChangeEvent.delete
                ? payload.oldRecord
                : payload.newRecord;
            final conversationId = row['conversation_id']?.toString() ?? '';
            if (!_trackedConversationIds.contains(conversationId)) return;
            if (payload.eventType == PostgresChangeEvent.delete) {
              final key = '$conversationId:${row['user_id']}';
              _activityByConversationAndUser.remove(key);
            } else {
              _applyActivityRow(
                Map<String, dynamic>.from(row),
                emitNotification: true,
              );
            }
            if (!_disposed) notifyListeners();
          },
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'message_receipts',
          callback: (payload) {
            final row = payload.eventType == PostgresChangeEvent.delete
                ? payload.oldRecord
                : payload.newRecord;
            _applyReceiptRow(Map<String, dynamic>.from(row));
            if (!_disposed) notifyListeners();
          },
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'messages',
          callback: (payload) {
            final row = payload.eventType == PostgresChangeEvent.delete
                ? payload.oldRecord
                : payload.newRecord;
            final conversationId = row['conversation_id']?.toString() ?? '';
            final senderId = row['sender_id']?.toString() ?? '';
            final id = row['id']?.toString() ?? '';

            if (payload.eventType == PostgresChangeEvent.insert &&
                senderId.isNotEmpty &&
                senderId != _currentUserId &&
                conversationId.isNotEmpty) {
              unawaited(markConversationDelivered(conversationId));
            }

            if (!_trackedConversationIds.contains(conversationId)) return;
            if (payload.eventType == PostgresChangeEvent.delete) {
              _metadataByMessageId.remove(id);
              _senderByMessageId.remove(id);
              _deliveryStateByMessageId.remove(id);
            } else {
              _metadataByMessageId[id] = _map(row['metadata']);
              _senderByMessageId[id] =
                  row['sender_id']?.toString() ?? _senderByMessageId[id] ?? '';
              if (_senderByMessageId[id] == _currentUserId)
                _deliveryStateByMessageId.putIfAbsent(
                  id,
                  () => DeliveryState.sent,
                );
              _maybeAlertRevokedMessage(id, row);
            }
            if (!_disposed) notifyListeners();
          },
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'profiles',
          callback: (payload) {
            final row = payload.eventType == PostgresChangeEvent.delete
                ? payload.oldRecord
                : payload.newRecord;
            _handleProfileChange(Map<String, dynamic>.from(row));
          },
        )
        .subscribe((status, [error]) {
          if (locator.isRegistered<ConnectionHealthService>()) {
            final socketStatus = switch (status) {
              RealtimeSubscribeStatus.subscribed =>
                RealtimeSocketStatus.connected,
              RealtimeSubscribeStatus.timedOut =>
                RealtimeSocketStatus.reconnecting,
              RealtimeSubscribeStatus.closed =>
                RealtimeSocketStatus.disconnected,
              RealtimeSubscribeStatus.channelError =>
                RealtimeSocketStatus.disconnected,
            };
            locator<ConnectionHealthService>().notifyRealtimeStatus(socketStatus);
          }
        });
    _channel = channel;
  }

  void _handleProfileChange(Map<String, dynamic> row) {
    final userId = row['id']?.toString() ?? '';
    if (userId.isEmpty || userId == _currentUserId) return;
    if (_backend.getUserById(userId) == null) return;
    final name = row['display_name']?.toString() ?? '';
    final about = row['about']?.toString() ?? row['bio']?.toString() ?? '';
    final avatar = row['avatar_url']?.toString() ?? '';
    final banner = row['banner_url']?.toString() ?? '';
    final fingerprint = '$name|$about|$avatar|$banner';
    final previous = _profileFingerprints[userId];
    _profileFingerprints[userId] = fingerprint;
    if (previous == null || previous == fingerprint) return;
    if (_disposed) return;
    if (!_preferences.notification.enableGlobalNotifications) return;
    if (!_preferences.gbBool('abu_saleh_toast_profile')) return;
    final profile = _backend.getUserById(userId);
    _notifications.triggerEventNotification(
      title:
          '${name.isNotEmpty ? name : profile?.displayName ?? 'Contact'}'
          ' updated their profile',
      body: about.isNotEmpty ? about : 'Profile details changed',
      icon: Icons.person_outline_rounded,
      color:
          _preferences.gbColor('abu_saleh_toast_profile_bc') ??
          const Color(0xFF6366F1),
      textColor: _preferences.gbColor('abu_saleh_toast_profile_tc'),
      userId: userId,
      avatarInitials: profile?.avatarInitials,
      avatarColorHex: profile?.avatarColorHex,
    );
  }

  Future<void> _reset() async {
    final channel = _channel;
    _channel = null;
    if (channel != null) {
      try {
        await _client.removeChannel(channel);
      } catch (e) {
        debugPrint('Chaty realtime: channel removal notice: $e');
      }
    }
    _presenceByUserId.clear();
    _lastSeenByUserId.clear();
    _profileFingerprints.clear();
    _activityByConversationAndUser.clear();
    _metadataByMessageId.clear();
    _deliveryStateByMessageId.clear();
    _senderByMessageId.clear();
    _revokeAlerted.clear();
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    unawaited(_authSubscription?.cancel());
    final channel = _channel;
    if (channel != null) unawaited(_client.removeChannel(channel));
    super.dispose();
  }

  static PresenceState _presenceFromDatabase(String? value) {
    switch (value) {
      case 'online':
        return PresenceState.online;
      case 'away':
        return PresenceState.away;
      case 'typing':
        return PresenceState.typing;
      default:
        return PresenceState.offline;
    }
  }

  static Map<String, dynamic> _map(dynamic value) =>
      value is Map ? Map<String, dynamic>.from(value) : <String, dynamic>{};

  static DateTime? _date(dynamic value) {
    if (value == null) return null;
    if (value is DateTime) return value.toLocal();
    return DateTime.tryParse(value.toString())?.toLocal();
  }
}
