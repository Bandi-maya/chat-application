import 'dart:async';

import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../domain/models/chat_message.dart';
import '../../domain/models/user_profile.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import 'backend_service.dart';
import 'contact_relationship_service.dart';
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

/// An incoming call invite that PASSED the recipient's Who-Can-Call-Me
/// gate and is currently ringing.
class IncomingCall {
  final String callId;
  final String fromUserId;
  final bool isVideo;
  final String displayName;
  final String? avatarInitials;
  final String? avatarColorHex;
  final DateTime receivedAt;

  const IncomingCall({
    required this.callId,
    required this.fromUserId,
    required this.isVideo,
    required this.displayName,
    this.avatarInitials,
    this.avatarColorHex,
    required this.receivedAt,
  });
}

/// Caller-side response to an outgoing call invite.
class CallResponseEvent {
  final String callId;
  // accepted | declined | busy | cancelled
  final String response;

  const CallResponseEvent({required this.callId, required this.response});
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
  RealtimeChannel? _callsChannel;
  IncomingCall? _incomingCall;
  Timer? _incomingCallTimer;
  final StreamController<CallResponseEvent> _callResponseController =
      StreamController<CallResponseEvent>.broadcast();
  String? _activeOutgoingCallId;
  String? _activeOutgoingCalleeId;
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
  // Message IDs we've already surfaced a "revoked" alert for, so a contact
  // deleting a message never produces duplicate alerts on repeated payloads.
  final Set<String> _revokeAlerted = <String>{};
  // Profile fingerprints for the profile-change alert (name|about per user).
  final Map<String, String> _profileFingerprints = <String, String>{};
  bool _disposed = false;
  bool _initializing = false;

  String? get _currentUserId => _client.auth.currentUser?.id;

  /// Currently ringing incoming call (already passed the Who-Can-Call-Me
  /// gate), or null. Drives the app-level ringing overlay.
  IncomingCall? get incomingCall => _incomingCall;

  /// Caller-side stream of responses to our outgoing invites.
  Stream<CallResponseEvent> get callResponses =>
      _callResponseController.stream;

  PresenceState presenceFor(String userId) =>
      _presenceByUserId[userId] ?? PresenceState.offline;
  bool isOnline(String userId) {
    // Reciprocity: when the user hides their own online status, Chaty must not
    // reveal contacts' online status to them either.
    if (_isMyOnlineHidden) return false;
    final state = presenceFor(userId);
    return state == PresenceState.online || state == PresenceState.typing;
  }

  DateTime? lastSeenFor(String userId) {
    // Reciprocity: hiding your own last seen (frozen, or audience "Nobody")
    // also hides contacts' last seen from you — the standard messaging rule.
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
            // Real consumer: presence-alert master toggle.
            _preferences.gbBool('abu_saleh_toast_online'))) {
      final profile = _backend.getUserById(userId);
      _notifications.triggerEventNotification(
        title: '${profile?.displayName ?? 'Contact'} is online',
        body: 'Now active in Chaty',
        icon: Icons.online_prediction_rounded,
        color: _preferences.gbColor('abu_saleh_toast_online_bc') ??
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
            // Real consumer: typing-alert master toggle.
            _preferences.gbBool('abu_saleh_toast_typing'))) {
      _notifications.triggerEventNotification(
        title: '${profile?.displayName ?? 'Contact'} is typing',
        body: 'Typing in a conversation',
        icon: Icons.keyboard_alt_outlined,
        color: _preferences.gbColor('abu_saleh_toast_typing_bc') ??
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

  /// Surfaces a "message revoked" alert when a contact deletes a message for
  /// everyone (soft-delete → the row gains a deleted_at). Requires BOTH the
  /// Message Revoke Alert privacy capability and the Deleted-message
  /// notification toggle; either being off suppresses the alert. De-duplicated
  /// per message id so repeated realtime payloads never re-notify.
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
    // Respect the Recording Indicators privacy toggle and Ghost Mode, mirroring
    // how typing indicators are gated before publishing. Always allow clearing
    // the state (isRecording == false).
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
            if (!_trackedConversationIds.contains(conversationId)) return;
            final id = row['id']?.toString() ?? '';
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
        .subscribe();
    _channel = channel;

    _callsChannel = _subscribeCallSignals(userId);
  }

  /// Personal call-signaling channel: carries broadcast invite/response
  /// events for Who Can Call Me enforcement and ringing.
  RealtimeChannel _subscribeCallSignals(String userId) {
    final callsChannel = _client.channel('chaty_calls_v1_$userId');
    callsChannel
        .onBroadcast(
          event: 'chaty_call_invite',
          callback: (payload) {
            unawaited(
              _handleIncomingInvite(Map<String, dynamic>.from(payload)),
            );
          },
        )
        .onBroadcast(
          event: 'chaty_call_response',
          callback: (payload) {
            _handleCallResponse(Map<String, dynamic>.from(payload));
          },
        )
        .subscribe();
    return callsChannel;
  }

  /// Real consumer for `abu_saleh_toast_profile` (+ `_bc`/`_tc` styling):
  /// diffs contact profile rows on realtime change and fires an alert when
  /// the display name or about text actually changed. The first sighting of
  /// a contact only establishes the baseline — never alerts.
  void _handleProfileChange(Map<String, dynamic> row) {
    final userId = row['id']?.toString() ?? '';
    if (userId.isEmpty || userId == _currentUserId) return;
    if (_backend.getUserById(userId) == null) return; // not a known contact
    final name = row['display_name']?.toString() ?? '';
    final about = row['about']?.toString() ?? row['bio']?.toString() ?? '';
    final fingerprint = '$name|$about';
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
      color: _preferences.gbColor('abu_saleh_toast_profile_bc') ??
          const Color(0xFF6366F1),
      textColor: _preferences.gbColor('abu_saleh_toast_profile_tc'),
      userId: userId,
      avatarInitials: profile?.avatarInitials,
      avatarColorHex: profile?.avatarColorHex,
    );
  }

  Future<void> _handleIncomingInvite(Map<String, dynamic> payload) async {
    final callId = payload['call_id']?.toString() ?? '';
    final fromUserId = payload['from']?.toString() ?? '';
    if (callId.isEmpty || fromUserId.isEmpty || fromUserId == _currentUserId) {
      return;
    }
    if (_incomingCall != null) {
      await _broadcastToUser(fromUserId, <String, dynamic>{
        'call_id': callId,
        'response': 'busy',
      });
      return;
    }
    // THE GATE: Who Can Call Me is enforced at ring time on the recipient,
    // mirroring how read receipts / recording indicators are enforced here.
    final allowed = await _callerMayRing(fromUserId);
    if (!allowed) {
      await _broadcastToUser(fromUserId, <String, dynamic>{
        'call_id': callId,
        'response': 'declined',
      });
      return;
    }
    final profile = _backend.getUserById(fromUserId);
    if (_disposed) return;
    _incomingCall = IncomingCall(
      callId: callId,
      fromUserId: fromUserId,
      isVideo: payload['is_video'] == true,
      displayName: profile?.displayName ?? 'Incoming call',
      avatarInitials: profile?.avatarInitials,
      avatarColorHex: profile?.avatarColorHex,
      receivedAt: DateTime.now(),
    );
    notifyListeners();
    _incomingCallTimer?.cancel();
    _incomingCallTimer = Timer(const Duration(seconds: 35), () {
      // Missed call: silently dismiss the ringing overlay.
      if (_incomingCall?.callId == callId) _clearIncomingCall();
    });
  }

  void _clearIncomingCall() {
    if (_incomingCall == null) return;
    _incomingCallTimer?.cancel();
    _incomingCall = null;
    if (!_disposed) notifyListeners();
  }

  Future<bool> _callerMayRing(String callerId) async {
    switch (_preferences.privacy.whoCanCallMe) {
      case 'Nobody':
        return false;
      case 'My Contacts':
      case 'My Contacts Except…':
        try {
          final status = await locator<ContactRelationshipService>()
              .connectionStatus(callerId);
          if (!status.callsAllowed) return false;
        } catch (_) {
          // When connectivity fails, fail closed: do not ring.
          return false;
        }
        if (_preferences.privacy.whoCanCallMe == 'My Contacts Except…' &&
            _preferences.privacy.whoCanCallMeExceptions.contains(callerId)) {
          return false;
        }
        return true;
      default:
        return true; // 'Everyone'
    }
  }

  void _handleCallResponse(Map<String, dynamic> payload) {
    final callId = payload['call_id']?.toString() ?? '';
    final response = payload['response']?.toString() ?? '';
    if (callId.isEmpty) return;
    // Caller cancelled while it was still ringing here: dismiss overlay.
    if (response == 'cancelled' && _incomingCall?.callId == callId) {
      _clearIncomingCall();
      return;
    }
    if (callId == _activeOutgoingCallId) {
      _callResponseController.add(
        CallResponseEvent(callId: callId, response: response),
      );
    }
  }

  /// Called by the ringing overlay UI.
  void respondToIncomingCall(bool accept) {
    final call = _incomingCall;
    if (call == null) return;
    _clearIncomingCall();
    unawaited(
      _broadcastToUser(call.fromUserId, <String, dynamic>{
        'call_id': call.callId,
        'response': accept ? 'accepted' : 'declined',
      }),
    );
  }

  /// Caller side: announce a new outgoing call to the callee's channel.
  Future<void> placeCall({
    required String calleeId,
    required String callId,
    required bool isVideo,
  }) async {
    _activeOutgoingCallId = callId;
    _activeOutgoingCalleeId = calleeId;
    await _broadcastToUser(calleeId, <String, dynamic>{
      'call_id': callId,
      'from': _currentUserId,
      'is_video': isVideo,
    });
  }

  /// Caller side: we hung up before an answer (or after leaving the screen).
  Future<void> cancelCall(String callId) async {
    final calleeId = _activeOutgoingCalleeId;
    _activeOutgoingCallId = null;
    _activeOutgoingCalleeId = null;
    if (calleeId == null) return;
    await _broadcastToUser(calleeId, <String, dynamic>{
      'call_id': callId,
      'response': 'cancelled',
    });
  }

  /// Joins the RECIPIENT's personal signaling channel just long enough to
  /// broadcast one event, then leaves it.
  Future<void> _broadcastToUser(
    String toUserId,
    Map<String, dynamic> payload,
  ) async {
    final completer = Completer<void>();
    final channel = _client.channel('chaty_calls_v1_$toUserId');
    try {
      channel.subscribe((status, error) {
        if (completer.isCompleted) return;
        if (status == RealtimeSubscribeStatus.subscribed) {
          completer.complete();
        } else if (status == RealtimeSubscribeStatus.channelError ||
            status == RealtimeSubscribeStatus.timedOut) {
          completer.completeError(error ?? StateError('channel $status'));
        }
      });
      await completer.future.timeout(const Duration(seconds: 6));
      await channel.sendBroadcastMessage(
        event: payload.containsKey('response')
            ? 'chaty_call_response'
            : 'chaty_call_invite',
        payload: payload,
      );
    } catch (_) {
      // Signaling failures must never crash the app; the caller just sees
      // no answer.
    } finally {
      try {
        await _client.removeChannel(channel);
      } catch (_) {}
    }
  }

  Future<void> _reset() async {
    final channel = _channel;
    _channel = null;
    if (channel != null) {
      try {
        await _client.removeChannel(channel);
      } catch (_) {}
    }
    final callsChannel = _callsChannel;
    _callsChannel = null;
    if (callsChannel != null) {
      try {
        await _client.removeChannel(callsChannel);
      } catch (_) {}
    }
    _incomingCallTimer?.cancel();
    _incomingCall = null;
    _activeOutgoingCallId = null;
    _activeOutgoingCalleeId = null;
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
    final callsChannel = _callsChannel;
    if (callsChannel != null) unawaited(_client.removeChannel(callsChannel));
    _incomingCallTimer?.cancel();
    unawaited(_callResponseController.close());
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
