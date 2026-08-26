import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';

import '../../domain/models/chat_message.dart';
import '../../domain/models/chat_task.dart';
import '../../domain/models/preferences.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/other_models.dart';
import '../../domain/models/user_profile.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/realtime/realtime_event_bus.dart';
import '../../ui/core/validators/input_validators.dart';
import 'connection_health_service.dart';
import 'local_snapshot_cache_service.dart';
import 'message_transport_compatibility_service.dart';
import 'mls_e2ee_service.dart';
import 'outgoing_message_queue_engine.dart';
import 'pending_secure_send_store.dart';
import 'snapshot_codec.dart';

class AuthSession {
  final String userId;
  final String token;
  final DateTime expiresAt;
  final String deviceId;

  const AuthSession({
    required this.userId,
    required this.token,
    required this.expiresAt,
    required this.deviceId,
  });
}

/// A message could not be MLS-encrypted yet because conversation devices are
/// still registering. The content was persisted locally in encrypted form and
/// will be delivered by [_retryPendingSecureSends]; it is never uploaded in
/// plaintext as a fallback.
class SecureSendPendingException implements Exception {
  const SecureSendPendingException(this.code);
  final String code;

  @override
  String toString() => code;
}

/// Server-backed application state for Chaty.
///
/// Supabase Auth is the source of truth for identity/session state. Postgres +
/// RLS-backed RPCs are the source of truth for conversations/messages/tasks.
/// This class keeps only a presentation cache so the existing UI can remain
/// reactive without storing credentials or pretending local JSON is a backend.
class ChatyBackendService extends ChangeNotifier {
  static final ChatyBackendService _instance = ChatyBackendService._internal();
  factory ChatyBackendService() => _instance;
  ChatyBackendService._internal();

  /// Error code surfaced when a send is queued because MLS setup is not
  /// finished for a conversation's devices.
  static const String secureSetupPendingCode = 'secure_setup_pending';

  final RealtimeEventBus eventBus = RealtimeEventBus();
  final Uuid _uuid = const Uuid();

  SupabaseClient get _client => Supabase.instance.client;

  UserProfile? _currentUser;
  AuthSession? _currentSession;
  final Map<String, UserProfile> _usersById = <String, UserProfile>{};
  final Map<String, Conversation> _conversationsById = <String, Conversation>{};
  final Map<String, List<ChatMessage>> _messagesByChatId =
      <String, List<ChatMessage>>{};
  final List<ChatTask> _tasks = <ChatTask>[];
  final List<CallRecord> _calls = <CallRecord>[];
  final List<UpdateStory> _stories = <UpdateStory>[];
  final List<LinkedDevice> _linkedDevices = <LinkedDevice>[];

  RealtimeChannel? _realtimeChannel;
  Timer? _reconcileTimer;
  StreamSubscription<AuthState>? _authSubscription;
  Future<void>? _initializeFuture;

  /// AES-256-GCM snapshot cache (key in platform secure storage) backing
  /// instant shell rendering and the pending secure-send queue.
  final LocalSnapshotCacheService _snapshotCache = LocalSnapshotCacheService();
  final PendingSecureSendStore _pendingSecureSends = PendingSecureSendStore();
  bool _isRetryingPendingSends = false;
  final Set<String> _pendingMessageConversationIds = <String>{};
  final Set<String> _pendingMemberConversationIds = <String>{};
  bool _pendingConversationListRefresh = false;
  bool _pendingTaskRefresh = false;
  bool _isInitialized = false;
  bool _isHydrating = false;

  bool get isInitialized => _isInitialized;
  bool get isAuthenticated =>
      _client.auth.currentSession != null && _currentUser != null;
  UserProfile? get currentUser => _currentUser;
  AuthSession? get currentSession => _currentSession;
  List<UserProfile> get allUsers =>
      List<UserProfile>.unmodifiable(_usersById.values);

  List<Conversation> get conversations {
    final values = _conversationsById.values.toList();
    values.sort((a, b) {
      if (a.isPinned != b.isPinned) return a.isPinned ? -1 : 1;
      return b.lastMessageTime.compareTo(a.lastMessageTime);
    });
    return List<Conversation>.unmodifiable(values);
  }

  List<ChatTask> get tasks => List<ChatTask>.unmodifiable(_tasks);
  List<CallRecord> get calls => List<CallRecord>.unmodifiable(_calls);
  List<UpdateStory> get stories => List<UpdateStory>.unmodifiable(_stories);
  List<LinkedDevice> get currentUserDevices =>
      List<LinkedDevice>.unmodifiable(_linkedDevices);

  void addCall(CallRecord record) {
    _calls.insert(0, record);
    notifyListeners();
  }

  Future<void> initialize() {
    if (_isInitialized) return Future<void>.value();
    return _initializeFuture ??= _initialize();
  }

  Future<void> _initialize() async {
    try {
      _authSubscription ??= _client.auth.onAuthStateChange.listen((
        AuthState state,
      ) {
        unawaited(_handleSession(state.session));
      });

      await _handleSession(_client.auth.currentSession);
      _isInitialized = true;
      notifyListeners();
    } catch (e) {
      // A transient local-storage or platform failure must not permanently
      // poison initialization. Concurrent callers still share this attempt;
      // a later caller may retry once the failed future has completed.
      _initializeFuture = null;
      rethrow;
    }
  }

  Future<void> _handleSession(Session? session) async {
    if (session == null) {
      await _removeRealtimeChannel();
      if (locator.isRegistered<MlsE2eeService>()) {
        await locator<MlsE2eeService>().close();
      }
      _currentUser = null;
      _currentSession = null;
      _usersById.clear();
      _conversationsById.clear();
      _messagesByChatId.clear();
      _tasks.clear();
      _linkedDevices.clear();
      notifyListeners();
      return;
    }

    _currentSession = _mapSession(session);
    if (_currentUser == null || _currentUser!.id != session.user.id) {
      final user = session.user;
      final fallback = UserProfile(
        id: user.id,
        displayName:
            user.userMetadata?['display_name']?.toString() ??
            user.email?.split('@').first ??
            'Chaty User',
        username: user.userMetadata?['username']?.toString() ?? 'user',
        avatarInitials: _initials(user.email ?? 'CU'),
        avatarColorHex: '0xFF6366F1',
        about: '',
        presence: PresenceState.online,
        lastSeenAt: DateTime.now(),
        isVerified: false,
        email: user.email ?? '',
        phone: user.phone ?? '',
        safetyNumber: '',
      );
      _currentUser = fallback;
      _usersById[fallback.id] = fallback;
    }
    // Offline-first: paint the shell from encrypted local snapshots before
    // any network work, then refresh from the backend without blocking.
    await _hydrateCachedState(session.user.id);
    notifyListeners();
    unawaited(_refreshAuthenticatedSession(session));
    await _subscribeRealtime();
  }

  /// Restores cached conversations and message timelines for [userId] so the
  /// UI renders instantly on cold start. Snapshots are AES-256-GCM encrypted
  /// at rest; a corrupt or missing snapshot is skipped, never fatal.
  Future<void> _hydrateCachedState(String userId) async {
    try {
      final cachedConversations = await _snapshotCache.readJson(
        userId: userId,
        scope: 'conversations',
      );
      if (cachedConversations is List) {
        for (final item in cachedConversations) {
          if (item is! Map) continue;
          final conversation = conversationFromJson(
            item.map((key, value) => MapEntry(key.toString(), value)),
          );
          if (conversation.id.isEmpty) continue;
          _conversationsById[conversation.id] = conversation;
        }
      }
      final hydratedIds = List<String>.from(_conversationsById.keys);
      await Future.wait<void>(
        hydratedIds.map((conversationId) async {
          final cachedMessages = await _snapshotCache.readJson(
            userId: userId,
            scope: 'messages_$conversationId',
          );
          if (cachedMessages is! List || cachedMessages.isEmpty) return;
          _messagesByChatId[conversationId] = cachedMessages
              .whereType<Map>()
              .map(
                (item) => chatMessageFromJson(
                  item.map((key, value) => MapEntry(key.toString(), value)),
                ),
              )
              .toList(growable: true);
        }),
      );
    } catch (error) {
      debugPrint('Chaty snapshot hydration skipped: $error');
    }
  }

  /// Network-side session catch-up. Runs after snapshots have restored the
  /// visible state; failures here leave the cached UI intact.
  Future<void> _refreshAuthenticatedSession(Session session) async {
    await _hydrateAuthenticatedState();
    if (locator.isRegistered<OutgoingMessageQueueEngine>()) {
      unawaited(locator<OutgoingMessageQueueEngine>().processQueue());
    } else {
      unawaited(_retryPendingSecureSends(session.user.id));
    }
  }

  AuthSession _mapSession(Session session) {
    final expiresSeconds =
        session.expiresAt ??
        DateTime.now().add(const Duration(hours: 1)).millisecondsSinceEpoch ~/
            1000;
    return AuthSession(
      userId: session.user.id,
      token: session.accessToken,
      expiresAt: DateTime.fromMillisecondsSinceEpoch(
        expiresSeconds * 1000,
        isUtc: true,
      ),
      deviceId: 'device_${session.user.id.substring(0, 8)}',
    );
  }

  Future<void> _hydrateAuthenticatedState() async {
    if (_isHydrating) return;
    _isHydrating = true;
    try {
      await _loadCurrentProfile();
      if (locator.isRegistered<MlsE2eeService>()) {
        final mls = locator<MlsE2eeService>();
        await mls.initializeForCurrentSession();
        if (!mls.isReady) {
          await mls.retryDeviceEnrollment();
        }
        if (!mls.isReady) {
          debugPrint('Chaty MLS secure messaging setup is pending device enrollment.');
        }
      }
      await Future.wait<void>(<Future<void>>[
        _loadConversations(),
        _loadTasks(),
      ]);
      await _refreshLoadedMessageTimelines();
      notifyListeners();
    } finally {
      _isHydrating = false;
    }
  }

  Future<void> _loadCurrentProfile() async {
    final user = _client.auth.currentUser;
    if (user == null) return;

    try {
      final row = await _client
          .from('profiles')
          .select()
          .eq('id', user.id)
          .maybeSingle();
      if (row != null) {
        final profile = _profileFromRow(
          Map<String, dynamic>.from(row),
          email: user.email ?? '',
          phone: user.phone ?? '',
        );
        _currentUser = profile;
        _usersById[profile.id] = profile;
      } else {
        final fallback = UserProfile(
          id: user.id,
          displayName:
              user.userMetadata?['display_name']?.toString() ??
              user.email?.split('@').first ??
              'Chaty User',
          username: user.userMetadata?['username']?.toString() ?? 'user',
          avatarInitials: _initials(user.email ?? 'CU'),
          avatarColorHex: '0xFF6366F1',
          about: '',
          presence: PresenceState.online,
          lastSeenAt: DateTime.now(),
          isVerified: false,
          email: user.email ?? '',
          phone: user.phone ?? '',
          safetyNumber: '',
        );
        _currentUser = fallback;
        _usersById[fallback.id] = fallback;
      }
    } catch (e, stackTrace) {
      debugPrint('Error loading current profile: $e\n$stackTrace');
      final fallback = UserProfile(
        id: user.id,
        displayName:
            user.userMetadata?['display_name']?.toString() ??
            user.email?.split('@').first ??
            'Chaty User',
        username: user.userMetadata?['username']?.toString() ?? 'user',
        avatarInitials: _initials(user.email ?? 'CU'),
        avatarColorHex: '0xFF6366F1',
        about: '',
        presence: PresenceState.online,
        lastSeenAt: DateTime.now(),
        isVerified: false,
        email: user.email ?? '',
        phone: user.phone ?? '',
        safetyNumber: '',
      );
      _currentUser = fallback;
      _usersById[fallback.id] = fallback;
    }

    unawaited(setPresence(PresenceState.online));
  }

  Future<void> _loadConversations({bool loadMembers = true}) async {
    try {
      final raw = await _client.rpc('get_my_conversations');
      final rows = _asRows(raw);
      final next = <String, Conversation>{};

      for (final row in rows) {
        final id = row['conversation_id']?.toString() ?? '';
        if (id.isEmpty) continue;
        final participantIds = _stringList(row['participant_ids']);
        final adminIds = _stringList(row['admin_ids']);

        final conversation = Conversation(
          id: id,
          type: row['kind'] == 'group'
              ? ConversationType.group
              : ConversationType.direct,
          title: row['title']?.toString() ?? 'Conversation',
          participantIds: participantIds,
          adminIds: adminIds,
          avatarInitials: row['avatar_initials']?.toString(),
          avatarColorHex: row['avatar_color_hex']?.toString(),
          lastMessageText: row['last_message']?.toString() ?? '',
          lastMessageTime: _date(row['last_message_at']) ?? DateTime.now(),
          lastMessageSenderId: row['last_message_sender_id']?.toString() ?? '',
          unreadCount: _integer(row['unread_count']),
          isPinned: row['is_pinned'] == true,
          isArchived: row['is_archived'] == true,
          isMuted: row['is_muted'] == true,
          draftText: row['draft_text']?.toString() ?? '',
          encryptionStatus: EncryptionStatus.verificationNeeded,
        );
        next[id] = conversation;
      }

      _conversationsById
        ..clear()
        ..addAll(next);
      _messagesByChatId.removeWhere((id, _) => !next.containsKey(id));
      unawaited(_persistConversationSnapshot());

      if (loadMembers) {
        await Future.wait<void>(next.keys.map(_loadConversationMembers));
      }
    } catch (error, stackTrace) {
      debugPrint('Chaty conversation load failed: $error\n$stackTrace');
      rethrow;
    }
  }

  Future<void> _loadConversationMembers(String conversationId) async {
    final raw = await _client.rpc(
      'get_conversation_members',
      params: <String, dynamic>{'p_conversation_id': conversationId},
    );
    for (final row in _asRows(raw)) {
      final profile = _profileFromRow(row);
      _usersById[profile.id] = profile;
    }
  }

  Future<void> ensureConversationLoaded(String conversationId) async {
    if (!_conversationsById.containsKey(conversationId)) {
      await _loadConversations();
    }
    await _loadConversationMembers(conversationId);
    await _loadMessages(conversationId);
    await markAsRead(conversationId);
  }

  Future<void> _loadMessages(String conversationId) async {
    final raw = await _client.rpc(
      'get_conversation_messages',
      params: <String, dynamic>{
        'p_conversation_id': conversationId,
        'p_limit': 100,
      },
    );
    final rows = _asRows(raw);
    final hydratedRows = <Map<String, dynamic>>[];
    for (final row in rows) {
      if (row['is_hidden'] == true) continue;
      hydratedRows.add(await _hydrateEncryptedMessageRow(conversationId, row));
    }
    final messages = hydratedRows.map(_messageFromRow).toList()
      ..sort((a, b) => a.createdAt.compareTo(b.createdAt));

    // Retain any optimistic / queued messages currently in memory so they don't disappear on reload
    final currentList = _messagesByChatId[conversationId] ?? const <ChatMessage>[];
    for (final current in currentList) {
      if (current.deliveryState == DeliveryState.queued ||
          current.deliveryState == DeliveryState.sending) {
        if (!messages.any((m) => m.id == current.id)) {
          messages.add(current);
        }
      }
    }
    messages.sort((a, b) => a.createdAt.compareTo(b.createdAt));
    _messagesByChatId[conversationId] = messages;

    final conversation = _conversationsById[conversationId];
    if (conversation != null && messages.isNotEmpty) {
      final latest = messages.last;
      _conversationsById[conversationId] = conversation.copyWith(
        lastMessageText: latest.text,
        lastMessageTime: latest.createdAt,
        lastMessageSenderId: latest.senderId,
      );
    }
    unawaited(
      _persistMessageSnapshot(
        conversationId,
        _messagesByChatId[conversationId],
      ),
    );
  }

  /// Persists the conversation list snapshot for the signed-in user.
  Future<void> _persistConversationSnapshot() async {
    final userId = _currentSession?.userId;
    if (userId == null) return;
    try {
      await _snapshotCache.writeJson(
        userId: userId,
        scope: 'conversations',
        value: _conversationsById.values
            .map(conversationToJson)
            .toList(growable: false),
      );
    } catch (error) {
      debugPrint('Chaty conversation snapshot skipped: $error');
    }
  }

  /// Persists one conversation's decrypted timeline snapshot. Content is
  /// encrypted at rest by [LocalSnapshotCacheService]; nothing plaintext is
  /// ever written to disk.
  Future<void> _persistMessageSnapshot(
    String conversationId,
    List<ChatMessage>? messages,
  ) async {
    final userId = _currentSession?.userId;
    if (userId == null || messages == null || messages.isEmpty) return;
    try {
      await _snapshotCache.writeJson(
        userId: userId,
        scope: 'messages_$conversationId',
        value: messages.map(chatMessageToJson).toList(growable: false),
      );
    } catch (error) {
      debugPrint('Chaty message snapshot skipped: $error');
    }
  }

  Future<Map<String, dynamic>> _hydrateEncryptedMessageRow(
    String conversationId,
    Map<String, dynamic> source,
  ) async {
    final row = Map<String, dynamic>.from(source);
    if (row['encryption_protocol'] != MlsE2eeService.protocolSuite) return row;
    if (row['deleted_at'] != null) return row;

    final ciphertext = row['encrypted_payload']?.toString() ?? '';
    if (ciphertext.isEmpty || !locator.isRegistered<MlsE2eeService>()) {
      return _decryptionFailureRow(row);
    }
    try {
      final decrypted = await locator<MlsE2eeService>().decryptPayload(
        conversationId: conversationId,
        ciphertext: ciphertext,
      );
      final decryptedMetadata = _stringDynamicMap(decrypted['metadata']);
      final serverMetadata = _stringDynamicMap(row['metadata']);
      row['body'] = decrypted['text']?.toString() ?? '';
      row['type'] = decrypted['type']?.toString() ?? 'text';
      row['metadata'] = <String, dynamic>{
        ...decryptedMetadata,
        ...serverMetadata,
      };
      return row;
    } catch (error, stackTrace) {
      debugPrint(
        'Chaty MLS decrypt failed for ${row['id']}: $error\n$stackTrace',
      );
      return _decryptionFailureRow(row);
    }
  }

  Map<String, dynamic> _decryptionFailureRow(Map<String, dynamic> source) {
    final row = Map<String, dynamic>.from(source);
    row['type'] = 'system';
    row['body'] = 'Unable to decrypt this message';
    row['metadata'] = <String, dynamic>{
      ..._stringDynamicMap(row['metadata']),
      'decryption_failed': true,
    };
    return row;
  }

  Future<void> _refreshLoadedMessageTimelines() async {
    final ids = _messagesByChatId.keys.toList();
    for (final id in ids) {
      try {
        await _loadMessages(id);
      } catch (error, stackTrace) {
        debugPrint('Chaty message reconciliation failed: $error\n$stackTrace');
      }
    }
  }

  Future<void> _loadTasks() async {
    final raw = await _client.rpc('get_my_tasks');
    final rows = _asRows(raw);
    _tasks
      ..clear()
      ..addAll(rows.map(_taskFromRow));
  }

  Future<void> _subscribeRealtime() async {
    final userId = _client.auth.currentUser?.id;
    if (userId == null) return;
    await _removeRealtimeChannel();

    final channel = _client.channel('chaty-user-$userId');
    channel
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'messages',
          callback: (payload) {
            final row = payload.eventType == PostgresChangeEvent.delete
                ? payload.oldRecord
                : payload.newRecord;
            _scheduleMessageReconciliation(Map<String, dynamic>.from(row));
          },
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'conversation_members',
          callback: (payload) {
            final row = payload.eventType == PostgresChangeEvent.delete
                ? payload.oldRecord
                : payload.newRecord;
            _scheduleMembershipReconciliation(Map<String, dynamic>.from(row));
          },
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'message_reactions',
          callback: (payload) {
            final row = payload.eventType == PostgresChangeEvent.delete
                ? payload.oldRecord
                : payload.newRecord;
            _scheduleMessageRelatedReconciliation(
              Map<String, dynamic>.from(row),
            );
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
            _scheduleMessageRelatedReconciliation(
              Map<String, dynamic>.from(row),
            );
          },
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'tasks',
          callback: (_) => _scheduleTaskReconciliation(),
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'mls_devices',
          callback: (_) => _scheduleKeyExchangeReconciliation(),
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'mls_key_packages',
          callback: (_) => _scheduleKeyExchangeReconciliation(),
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'mls_conversation_groups',
          callback: (_) => _scheduleKeyExchangeReconciliation(),
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'mls_control_messages',
          callback: (_) => _scheduleKeyExchangeReconciliation(),
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'mls_welcomes',
          callback: (_) => _scheduleKeyExchangeReconciliation(),
        )
        .subscribe();
    _realtimeChannel = channel;
  }

  void _scheduleMessageReconciliation(Map<String, dynamic> row) {
    final conversationId =
        row['conversation_id']?.toString() ??
        _conversationIdForLoadedMessage(row['id']?.toString() ?? '');
    if (conversationId != null && conversationId.isNotEmpty) {
      _pendingMessageConversationIds.add(conversationId);
    }
    _pendingConversationListRefresh = true;
    _armRealtimeReconciliation();
  }

  void _scheduleMembershipReconciliation(Map<String, dynamic> row) {
    final conversationId = row['conversation_id']?.toString() ?? '';
    if (conversationId.isNotEmpty) {
      _pendingMemberConversationIds.add(conversationId);
      if (_messagesByChatId.containsKey(conversationId)) {
        _pendingMessageConversationIds.add(conversationId);
      }
    }
    _pendingConversationListRefresh = true;
    _armRealtimeReconciliation();
  }

  void _scheduleMessageRelatedReconciliation(Map<String, dynamic> row) {
    final messageId = row['message_id']?.toString() ?? '';
    final conversationId = _conversationIdForLoadedMessage(messageId);
    if (conversationId != null) {
      _pendingMessageConversationIds.add(conversationId);
      _armRealtimeReconciliation();
    }
  }

  void _scheduleTaskReconciliation() {
    _pendingTaskRefresh = true;
    _armRealtimeReconciliation();
  }

  void _scheduleKeyExchangeReconciliation() {
    if (locator.isRegistered<MessageTransportCompatibilityService>()) {
      locator<MessageTransportCompatibilityService>().invalidateAll();
    }
    _armRealtimeReconciliation();
  }

  String? _conversationIdForLoadedMessage(String messageId) {
    if (messageId.isEmpty) return null;
    for (final entry in _messagesByChatId.entries) {
      if (entry.value.any((message) => message.id == messageId)) {
        return entry.key;
      }
    }
    return null;
  }

  void _armRealtimeReconciliation() {
    _reconcileTimer?.cancel();
    _reconcileTimer = Timer(
      const Duration(milliseconds: 120),
      _flushRealtimeReconciliation,
    );
  }

  Future<void> _flushRealtimeReconciliation() async {
    final refreshConversationList = _pendingConversationListRefresh;
    final refreshTasks = _pendingTaskRefresh;
    final messageConversationIds = Set<String>.from(
      _pendingMessageConversationIds,
    );
    final memberConversationIds = Set<String>.from(
      _pendingMemberConversationIds,
    );

    _pendingConversationListRefresh = false;
    _pendingTaskRefresh = false;
    _pendingMessageConversationIds.clear();
    _pendingMemberConversationIds.clear();

    try {
      if (refreshConversationList) {
        await _loadConversations(loadMembers: false);
      }
      if (refreshTasks) {
        await _loadTasks();
      }
      for (final conversationId in memberConversationIds) {
        if (_conversationsById.containsKey(conversationId)) {
          await _loadConversationMembers(conversationId);
        }
      }
      for (final conversationId in messageConversationIds) {
        if (_conversationsById.containsKey(conversationId) &&
            _messagesByChatId.containsKey(conversationId)) {
          await _loadMessages(conversationId);
        }
      }
      final currentUserId = _currentSession?.userId;
      if (currentUserId != null) {
        if (locator.isRegistered<OutgoingMessageQueueEngine>()) {
          unawaited(locator<OutgoingMessageQueueEngine>().processQueue());
        } else {
          unawaited(_retryPendingSecureSends(currentUserId));
        }
      }
      notifyListeners();
      eventBus.publish(
        RealtimeEvent(type: RealtimeEventType.conversationUpdated),
      );
    } catch (error, stackTrace) {
      debugPrint(
        'Chaty targeted realtime reconciliation failed: $error\n$stackTrace',
      );
      try {
        await _hydrateAuthenticatedState();
      } catch (fallbackError, fallbackStack) {
        debugPrint(
          'Chaty fallback realtime hydration failed: '
          '$fallbackError\n$fallbackStack',
        );
      }
    }
  }

  Future<void> _removeRealtimeChannel() async {
    _reconcileTimer?.cancel();
    _pendingConversationListRefresh = false;
    _pendingTaskRefresh = false;
    _pendingMessageConversationIds.clear();
    _pendingMemberConversationIds.clear();
    final channel = _realtimeChannel;
    _realtimeChannel = null;
    if (channel != null) {
      try {
        await _client.removeChannel(channel);
      } catch (e, stackTrace) {
        debugPrint('Error removing realtime channel: $e\n$stackTrace');
      }
    }
  }

  Future<UserProfile> registerUser({
    required String displayName,
    required String username,
    required String password,
    String email = '',
    String phone = '',
    String about = 'Hey there! I am using Chaty.',
  }) async {
    final usernameError = ChatyValidators.validateUsername(username);
    if (usernameError != null) throw Exception(usernameError);
    final passwordError = ChatyValidators.validatePassword(password);
    if (passwordError != null) throw Exception(passwordError);
    if (displayName.trim().length < 2)
      throw Exception('Display name is required.');
    if (email.trim().isEmpty || !email.contains('@')) {
      throw Exception('A valid email is required for account verification.');
    }
    if (!await isUsernameAvailable(username)) {
      throw Exception('That username is already taken.');
    }

    final initials = _initials(displayName);
    const color = '0xFF6366F1';
    final response = await _client.auth.signUp(
      email: email.trim().toLowerCase(),
      password: password,
      emailRedirectTo: 'chaty://login-callback/',
      data: <String, dynamic>{
        'display_name': displayName.trim(),
        'username': ChatyValidators.normalizeUsername(username),
        'about': about.trim(),
        'phone': phone.trim(),
        'avatar_initials': initials,
        'avatar_color_hex': color,
      },
    );

    final authUser = response.user;
    if (authUser == null) throw Exception('Unable to create the account.');

    final result = UserProfile(
      id: authUser.id,
      displayName: displayName.trim(),
      username: ChatyValidators.normalizeUsername(username),
      avatarInitials: initials,
      avatarColorHex: color,
      about: about.trim(),
      presence: PresenceState.offline,
      lastSeenAt: DateTime.now(),
      isVerified: false,
      email: authUser.email ?? email.trim(),
      phone: authUser.phone ?? phone.trim(),
      safetyNumber: '',
    );

    if (response.session != null) {
      await _handleSession(response.session);
    }
    return result;
  }

  Future<UserProfile> login({
    required String identifier,
    required String password,
  }) async {
    final value = identifier.trim();
    if (!value.contains('@')) {
      throw Exception(
        'Sign in with your registered email address. Username discovery remains available after login.',
      );
    }
    final response = await _client.auth.signInWithPassword(
      email: value.toLowerCase(),
      password: password,
    );
    if (response.session == null || response.user == null) {
      throw Exception('Unable to establish a secure session.');
    }
    await _handleSession(response.session);
    final profile = _currentUser;
    if (profile == null) throw Exception('Your profile could not be loaded.');
    return profile;
  }

  Future<UserProfile> loginWithSocial({
    required String provider,
    String email = '',
    String displayName = '',
  }) async {
    throw Exception(
      '$provider sign-in is not configured on the Chaty Supabase project yet. Use email/password sign-in.',
    );
  }

  Future<void> resetPassword(String email) async {
    await _client.auth.resetPasswordForEmail(
      email.trim().toLowerCase(),
      redirectTo: 'chaty://reset-password/',
    );
  }

  Future<bool> isUsernameAvailable(String username) async {
    final error = ChatyValidators.validateUsername(username);
    if (error != null) return false;
    final raw = await _client.rpc(
      'is_username_available',
      params: <String, dynamic>{
        'p_username': ChatyValidators.normalizeUsername(username),
      },
    );
    return raw == true;
  }

  UserProfile? getUserById(String id) => _usersById[id];

  List<UserProfile> searchUsers(String query, {bool includeSelf = false}) {
    final normalized = query.trim().replaceFirst('@', '').toLowerCase();
    if (normalized.isEmpty) return <UserProfile>[];
    return _usersById.values.where((user) {
      if (!includeSelf && user.id == _currentUser?.id) return false;
      return user.username.toLowerCase().contains(normalized) ||
          user.displayName.toLowerCase().contains(normalized);
    }).toList();
  }

  Future<List<UserProfile>> searchUsersRemote(
    String query, {
    bool includeSelf = false,
  }) async {
    final trimmed = query.trim();
    if (trimmed.length < 2) return <UserProfile>[];
    final raw = await _client.rpc(
      'search_profiles',
      params: <String, dynamic>{'p_query': trimmed},
    );
    final results = _asRows(raw).map(_profileFromRow).toList();
    for (final profile in results) {
      _usersById[profile.id] = profile;
    }
    if (includeSelf && _currentUser != null) results.insert(0, _currentUser!);
    notifyListeners();
    return results;
  }

  Future<Conversation> getOrCreateDirectConversationAsync(
    UserProfile otherUser,
  ) async {
    if (_currentUser == null) throw Exception('Authentication required.');
    final raw = await _client.rpc(
      'create_direct_conversation',
      params: <String, dynamic>{'p_other_user_id': otherUser.id},
    );
    final id = raw?.toString() ?? '';
    if (id.isEmpty) throw Exception('Unable to create conversation.');
    _usersById[otherUser.id] = otherUser;
    await _loadConversations();
    await ensureConversationLoaded(id);
    notifyListeners();
    return _conversationsById[id]!;
  }

  Conversation getOrCreateDirectConversation(UserProfile otherUser) {
    final me = _currentUser;
    if (me != null) {
      for (final conversation in _conversationsById.values) {
        if (conversation.type == ConversationType.direct &&
            conversation.participantIds.contains(me.id) &&
            conversation.participantIds.contains(otherUser.id)) {
          return conversation;
        }
      }
    }
    throw StateError(
      'Conversation is not loaded. Use getOrCreateDirectConversationAsync().',
    );
  }

  Future<Conversation> createGroup({
    required String title,
    required List<String> memberUserIds,
    String? avatarInitials,
    String? avatarColorHex,
  }) async {
    final raw = await _client.rpc(
      'create_group_conversation',
      params: <String, dynamic>{
        'p_title': title.trim(),
        'p_member_ids': memberUserIds,
      },
    );
    final id = raw?.toString() ?? '';
    if (id.isEmpty) throw Exception('Unable to create group.');
    await _loadConversations();
    notifyListeners();
    return _conversationsById[id]!;
  }

  List<ChatMessage> getMessages(String conversationId) =>
      List<ChatMessage>.unmodifiable(
        _messagesByChatId[conversationId] ?? const <ChatMessage>[],
      );

  Future<ChatMessage> sendMessage({
    required String conversationId,
    required String text,
    MessageType type = MessageType.text,
    MessageAttachment? attachment,
    String? replyToMessageId,
    String? replyToPreviewText,
    String? replyToSenderName,
    String? linkedTaskId,
    Map<String, dynamic>? extraMetadata,
    String? clientMessageId,
  }) async {
    final me = _currentUser;
    if (me == null) throw Exception('Authentication required.');
    if (!_conversationsById.containsKey(conversationId))
      throw Exception('Conversation not found.');
    if (!locator.isRegistered<MlsE2eeService>()) {
      throw StateError('Encrypted message transport is unavailable.');
    }

    final effectiveClientMessageId = clientMessageId ?? _uuid.v4();
    final metadata = <String, dynamic>{
      if (replyToMessageId != null) 'reply_to_message_id': replyToMessageId,
      if (replyToPreviewText != null)
        'reply_to_preview_text': replyToPreviewText,
      if (replyToSenderName != null) 'reply_to_sender_name': replyToSenderName,
      if (linkedTaskId != null) 'linked_task_id': linkedTaskId,
      ...?extraMetadata,
      if (attachment != null)
        'attachment': <String, dynamic>{
          'id': attachment.id,
          'type': attachment.type,
          'name': attachment.name,
          'size': attachment.size,
          'url': attachment.url,
          'duration_seconds': attachment.durationSeconds,
        },
    };

    final pendingSend = PendingSecureSend(
      clientMessageId: effectiveClientMessageId,
      conversationId: conversationId,
      type: _messageTypeToDatabase(type),
      text: text.trim(),
      metadata: metadata,
      createdAt: DateTime.now(),
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
    );

    try {
      return await _deliverEncryptedMessage(pendingSend, fallbackType: type);
    } catch (error) {
      if (error is MlsStorageInitializationException &&
          error.failure != MlsStorageInitializationFailure.databaseInUse) {
        // A native compatibility or storage-availability failure cannot be
        // repaired by the pending-send retry loop. Do not show a message as
        // queued when this build is currently unable to encrypt it.
        rethrow;
      }

      // If error is due to MLS group setup or network / backend unavailability:
      // Enqueue to persistent store and insert an optimistic local message in sending / queued state.
      await _pendingSecureSends.put(me.id, pendingSend);

      final optimisticMsg = ChatMessage(
        id: effectiveClientMessageId,
        conversationId: conversationId,
        senderId: me.id,
        type: type,
        text: text.trim(),
        attachment: attachment,
        replyToMessageId: replyToMessageId,
        replyToPreviewText: replyToPreviewText,
        replyToSenderName: replyToSenderName,
        linkedTaskId: linkedTaskId,
        metadata: metadata,
        createdAt: pendingSend.createdAt,
        deliveryState: DeliveryState.queued,
      );

      final list = _messagesByChatId.putIfAbsent(
        conversationId,
        () => <ChatMessage>[],
      );
      if (!list.any((m) => m.id == effectiveClientMessageId)) {
        list.add(optimisticMsg);
      }
      notifyListeners();

      if (locator.isRegistered<ConnectionHealthService>()) {
        final pendingList = await _pendingSecureSends.read(me.id);
        locator<ConnectionHealthService>().updateQueuedCount(
          pendingList.length,
        );
      }

      if (_isMlsSetupPendingError(error)) {
        throw SecureSendPendingException(secureSetupPendingCode);
      }

      // Automatically kick off background retry
      unawaited(_retryPendingSecureSends(me.id));
      return optimisticMsg;
    }
  }

  /// Whether [error] means MLS encryption cannot happen yet because the
  /// conversation's devices have not completed key registration.
  bool _isMlsSetupPendingError(Object error) {
    if (error is MlsMembershipPendingException) return true;
    if (error is MlsE2eeException &&
        (error.message.contains('MLS group is not initialized') ||
         error.message.contains('every conversation member must register an MLS device') ||
         error.message.contains('no key packages available'))) {
      return true;
    }
    final errorStr = error.toString().toLowerCase();
    return errorStr.contains('every conversation member must register an mls device') ||
        errorStr.contains('must register an mls device') ||
        errorStr.contains('mls group is not initialized') ||
        errorStr.contains('no key packages available') ||
        errorStr.contains('mls_membership_pending');
  }

  /// Retries sending a previously enqueued pending secure message directly
  /// without creating duplicate optimistic messages or re-enqueuing into store.
  Future<ChatMessage> retryPendingSecureMessage(
    PendingSecureSend item,
  ) async {
    try {
      return await _deliverEncryptedMessage(
        item,
        fallbackType: _messageTypeFromDatabase(item.type),
      );
    } catch (error) {
      if (_isMlsSetupPendingError(error)) {
        throw SecureSendPendingException(secureSetupPendingCode);
      }
      rethrow;
    }
  }

  /// Encrypts and delivers [send], refreshing local state on success.
  Future<ChatMessage> _deliverEncryptedMessage(
    PendingSecureSend send, {
    MessageType? fallbackType,
  }) async {
    var me = _currentUser;
    if (me == null) {
      final authUser = _client.auth.currentUser;
      if (authUser != null) {
        me = UserProfile(
          id: authUser.id,
          displayName:
              authUser.userMetadata?['display_name']?.toString() ??
              authUser.email?.split('@').first ??
              'Chaty User',
          username: authUser.userMetadata?['username']?.toString() ?? 'user',
          avatarInitials: _initials(authUser.email ?? 'CU'),
          avatarColorHex: '0xFF6366F1',
          about: '',
          presence: PresenceState.online,
          lastSeenAt: DateTime.now(),
          isVerified: false,
          email: authUser.email ?? '',
          phone: authUser.phone ?? '',
          safetyNumber: '',
        );
        _currentUser = me;
        _usersById[me.id] = me;
      }
    }
    if (me == null) throw Exception('Authentication required.');
    final mls = locator<MlsE2eeService>();
    final encrypted = await mls.encryptPayload(
      conversationId: send.conversationId,
      payload: <String, dynamic>{
        'type': send.type,
        'text': send.text,
        'metadata': send.metadata,
      },
    );
    final deviceId = mls.currentDeviceId;
    if (deviceId == null) {
      throw StateError('Encrypted device identity is unavailable.');
    }
    final raw = await _client.rpc(
      'send_mls_message_v1',
      params: <String, dynamic>{
        'p_conversation_id': send.conversationId,
        'p_client_message_id': send.clientMessageId,
        'p_sender_device_id': deviceId,
        'p_group_id': encrypted.groupId,
        'p_epoch': encrypted.epoch,
        'p_ciphertext': encrypted.ciphertext,
      },
    );
    final messageId = raw?.toString() ?? '';
    await Future.wait<void>(<Future<void>>[
      _loadMessages(send.conversationId),
      _loadConversations(loadMembers: false),
    ]);
    final UserProfile senderProfile = me;
    final result = _messagesByChatId[send.conversationId]!.firstWhere(
      (message) => message.id == messageId,
      orElse: () => ChatMessage(
        id: messageId,
        conversationId: send.conversationId,
        senderId: senderProfile.id,
        type: fallbackType ?? _messageTypeFromDatabase(send.type),
        text: send.text,
        attachment: send.attachment == null
            ? null
            : MessageAttachment(
                id: send.attachment!['id']?.toString() ?? '',
                type: send.attachment!['type']?.toString() ?? 'file',
                name: send.attachment!['name']?.toString() ?? '',
                size: send.attachment!['size']?.toString() ?? '',
                url: send.attachment!['url']?.toString(),
                durationSeconds:
                    int.tryParse(
                      '${send.attachment!['duration_seconds'] ?? 0}',
                    ) ??
                    0,
              ),
        createdAt: send.createdAt,
        deliveryState: DeliveryState.sent,
      ),
    );
    eventBus.publish(
      RealtimeEvent(
        type: RealtimeEventType.messageCreated,
        conversationId: send.conversationId,
        userId: me.id,
        payload: <String, dynamic>{'messageId': result.id},
      ),
    );
    return result;
  }

  /// Redelivers locally queued messages whose MLS groups have since finished
  /// setup. Items stay queued until their send succeeds; a still-pending
  /// group stops this pass without dropping anything.
  Future<void> _retryPendingSecureSends(String userId) async {
    if (_isRetryingPendingSends) return;
    _isRetryingPendingSends = true;
    try {
      final items = await _pendingSecureSends.read(userId);
      for (final item in items) {
        if (!_conversationsById.containsKey(item.conversationId)) continue;
        try {
          await _deliverEncryptedMessage(
            item,
            fallbackType: _messageTypeFromDatabase(item.type),
          );
          await _pendingSecureSends.remove(userId, item.clientMessageId);
        } catch (error) {
          if (_isMlsSetupPendingError(error)) break;
          debugPrint(
            'Chaty pending secure send ${item.clientMessageId} deferred: $error',
          );
        }
      }
    } finally {
      _isRetryingPendingSends = false;
    }
  }

  void toggleReaction(String conversationId, String messageId, String emoji) {
    _optimisticToggleReaction(conversationId, messageId, emoji);
    unawaited(_toggleReactionAsync(conversationId, messageId, emoji));
  }

  void _optimisticToggleReaction(
    String conversationId,
    String messageId,
    String emoji,
  ) {
    final currentUserId = currentUser?.id;
    if (currentUserId == null) return;
    final list = _messagesByChatId[conversationId];
    if (list == null) return;
    final msgIndex = list.indexWhere((m) => m.id == messageId);
    if (msgIndex == -1) return;

    final msg = list[msgIndex];
    final updatedReactions = <MessageReaction>[];
    bool userHadSameReaction = false;

    for (final r in msg.reactions) {
      final newUserIds = List<String>.from(r.userIds);
      if (newUserIds.remove(currentUserId)) {
        if (r.emoji == emoji) {
          userHadSameReaction = true;
        }
      }
      if (newUserIds.isNotEmpty) {
        updatedReactions.add(r.copyWith(userIds: newUserIds));
      }
    }

    if (!userHadSameReaction) {
      final existingIndex = updatedReactions.indexWhere(
        (r) => r.emoji == emoji,
      );
      if (existingIndex != -1) {
        final r = updatedReactions[existingIndex];
        updatedReactions[existingIndex] = r.copyWith(
          userIds: [...r.userIds, currentUserId],
        );
      } else {
        updatedReactions.add(
          MessageReaction(emoji: emoji, userIds: [currentUserId]),
        );
      }
    }

    list[msgIndex] = msg.copyWith(reactions: updatedReactions);
    notifyListeners();
  }

  Future<void> _toggleReactionAsync(
    String conversationId,
    String messageId,
    String emoji,
  ) async {
    try {
      await _client.rpc(
        'toggle_message_reaction',
        params: <String, dynamic>{'p_message_id': messageId, 'p_emoji': emoji},
      );
    } catch (e, stackTrace) {
      debugPrint('Error toggling reaction: $e\n$stackTrace');
    }
    await _loadMessages(conversationId);
    notifyListeners();
  }

  Future<void> editMessage({
    required String conversationId,
    required String messageId,
    required String newText,
  }) async {
    final list = _messagesByChatId[conversationId];
    if (list != null) {
      final index = list.indexWhere((m) => m.id == messageId);
      if (index != -1) {
        list[index] = list[index].copyWith(
          text: newText.trim(),
          editedAt: DateTime.now(),
        );
        notifyListeners();
      }
    }
    try {
      await _client.rpc(
        'edit_chat_message',
        params: <String, dynamic>{
          'p_message_id': messageId,
          'p_text': newText.trim(),
        },
      );
    } catch (e, stackTrace) {
      debugPrint('Error editing message via RPC: $e\n$stackTrace');
      try {
        await _client
            .from('messages')
            .update(<String, dynamic>{
              'text': newText.trim(),
              'edited_at': DateTime.now().toUtc().toIso8601String(),
            })
            .eq('id', messageId);
      } catch (e, stackTrace) {
        debugPrint('Error editing message: $e\n$stackTrace');
      }
    }
    await _loadMessages(conversationId);
    notifyListeners();
  }

  void deleteMessage(
    String conversationId,
    String messageId, {
    bool forEveryone = false,
  }) {
    unawaited(_deleteMessageAsync(conversationId, messageId, forEveryone));
  }

  Future<void> _deleteMessageAsync(
    String conversationId,
    String messageId,
    bool forEveryone,
  ) async {
    await _client.rpc(
      'delete_chat_message',
      params: <String, dynamic>{
        'p_message_id': messageId,
        'p_for_everyone': forEveryone,
      },
    );
    await Future.wait<void>(<Future<void>>[
      _loadMessages(conversationId),
      _loadConversations(loadMembers: false),
    ]);
    notifyListeners();
  }

  Future<void> markAsRead(String conversationId) async {
    if (_shouldSendReadReceipts(conversationId)) {
      await _client.rpc(
        'mark_conversation_read',
        params: <String, dynamic>{'p_conversation_id': conversationId},
      );
      await _loadConversations(loadMembers: false);
      if (_messagesByChatId.containsKey(conversationId))
        await _loadMessages(conversationId);
    } else {
      final current = _conversationsById[conversationId];
      if (current != null && current.unreadCount != 0) {
        _conversationsById[conversationId] = current.copyWith(unreadCount: 0);
      }
    }
    notifyListeners();
  }

  bool _shouldSendReadReceipts(String conversationId) {
    if (!locator.isRegistered<ChatyPreferencesController>()) return true;
    final prefs = locator<ChatyPreferencesController>();
    if (prefs.home.ghostMode || prefs.gbBool('yo_want_ghostmode')) return false;
    if (!prefs.privacy.readReceipts) return false;
    if (prefs.privacy.showBlueTicksAfterReply) {
      final myId = _client.auth.currentUser?.id;
      final msgs = _messagesByChatId[conversationId];
      if (myId != null &&
          msgs != null &&
          msgs.isNotEmpty &&
          !msgs.any((m) => m.senderId == myId)) {
        return false;
      }
    }
    return true;
  }

  Future<void> markAsUnread(String conversationId) async {
    final current = _conversationsById[conversationId];
    if (current != null) {
      _conversationsById[conversationId] = current.copyWith(
        unreadCount: current.unreadCount > 0 ? current.unreadCount : 1,
      );
      notifyListeners();
    }
    try {
      await _client.rpc(
        'mark_conversation_unread',
        params: <String, dynamic>{'p_conversation_id': conversationId},
      );
    } catch (e, stackTrace) {
      debugPrint('Error marking conversation as unread: $e\n$stackTrace');
    }
  }

  Future<void> deleteConversation(String conversationId) async {
    _conversationsById.remove(conversationId);
    _messagesByChatId.remove(conversationId);
    notifyListeners();
    try {
      await _client.rpc(
        'delete_conversation',
        params: <String, dynamic>{'p_conversation_id': conversationId},
      );
    } catch (e, stackTrace) {
      debugPrint('Error deleting conversation via RPC: $e\n$stackTrace');
      try {
        await _client.from('conversations').delete().eq('id', conversationId);
      } catch (e, stackTrace) {
        debugPrint('Error deleting conversation directly: $e\n$stackTrace');
      }
    }
    await _loadConversations();
    notifyListeners();
  }

  void setConversationState(String conversationId, String field, bool value) {
    unawaited(_setConversationStateAsync(conversationId, field, value));
  }

  Future<void> _setConversationStateAsync(
    String conversationId,
    String field,
    bool value,
  ) async {
    await _client.rpc(
      'set_conversation_state',
      params: <String, dynamic>{
        'p_conversation_id': conversationId,
        'p_field': field,
        'p_value': value,
      },
    );
    await _loadConversations(loadMembers: false);
    notifyListeners();
  }

  void setMessageState(
    String conversationId,
    String messageId,
    String field,
    bool value,
  ) {
    unawaited(_setMessageStateAsync(conversationId, messageId, field, value));
  }

  Future<void> _setMessageStateAsync(
    String conversationId,
    String messageId,
    String field,
    bool value,
  ) async {
    await _client.rpc(
      'set_message_user_state',
      params: <String, dynamic>{
        'p_message_id': messageId,
        'p_field': field,
        'p_value': value,
      },
    );
    await _loadMessages(conversationId);
    notifyListeners();
  }

  void setDraft(String conversationId, String draft) {
    final current = _conversationsById[conversationId];
    if (current != null) {
      _conversationsById[conversationId] = current.copyWith(draftText: draft);
      notifyListeners();
    }
    _persistDraft(conversationId, draft);
  }

  /// Persists a draft to Supabase silently without notifying Flutter UI listeners.
  /// Used during widget unmount/dispose to avoid triggering rebuilds while the tree is locked.
  void persistDraftSilently(String conversationId, String draft) {
    final current = _conversationsById[conversationId];
    if (current != null) {
      _conversationsById[conversationId] = current.copyWith(draftText: draft);
    }
    _persistDraft(conversationId, draft);
  }

  void _persistDraft(String conversationId, String draft) {
    unawaited(
      _client.rpc(
        'set_conversation_draft',
        params: <String, dynamic>{
          'p_conversation_id': conversationId,
          'p_draft': draft,
        },
      ),
    );
  }

  Future<ChatTask> createTask({
    required String sourceConversationId,
    String? sourceMessageId,
    required String title,
    required String description,
    required List<String> assigneeIds,
    required TaskPriority priority,
    required DateTime dueAt,
    List<String> labels = const <String>[],
  }) async {
    final clientTaskId = _uuid.v4();
    final raw = await _client.rpc(
      'create_chat_task',
      params: <String, dynamic>{
        'p_conversation_id': sourceConversationId,
        'p_client_task_id': clientTaskId,
        'p_title': title.trim(),
        'p_assignee_ids': assigneeIds,
        'p_priority': _taskPriorityToDatabase(priority),
        'p_due_at': dueAt.toUtc().toIso8601String(),
        'p_description': description.trim(),
        'p_labels': labels,
        'p_source_message_id': sourceMessageId,
      },
    );
    final id = raw?.toString() ?? '';
    await Future.wait<void>(<Future<void>>[
      _loadTasks(),
      _loadConversations(loadMembers: false),
      if (_messagesByChatId.containsKey(sourceConversationId))
        _loadMessages(sourceConversationId),
    ]);
    notifyListeners();
    return _tasks.firstWhere((task) => task.id == id);
  }

  Future<void> updateTaskStatus(String taskId, TaskStatus status) {
    return _updateTaskStatusAsync(taskId, status);
  }

  Future<void> _updateTaskStatusAsync(String taskId, TaskStatus status) async {
    final index = _tasks.indexWhere((t) => t.id == taskId);
    if (index != -1) {
      _tasks[index] = _tasks[index].copyWith(
        status: status,
        updatedAt: DateTime.now(),
      );
      notifyListeners();
    }
    await _client.rpc(
      'update_task_status',
      params: <String, dynamic>{
        'p_task_id': taskId,
        'p_status': taskStatusToDatabase(status),
      },
    );
    await _loadTasks();
    notifyListeners();
  }

  Future<void> updateTask({
    required String taskId,
    required String title,
    required String description,
    required List<String> assigneeIds,
    required TaskPriority priority,
    required DateTime dueAt,
    List<String> labels = const <String>[],
  }) async {
    final index = _tasks.indexWhere((t) => t.id == taskId);
    if (index != -1) {
      _tasks[index] = _tasks[index].copyWith(
        title: title.trim(),
        description: description.trim(),
        assigneeIds: assigneeIds,
        priority: priority,
        dueAt: dueAt,
        labels: labels,
        updatedAt: DateTime.now(),
      );
      notifyListeners();
    }
    await _client.rpc(
      'update_chat_task',
      params: <String, dynamic>{
        'p_task_id': taskId,
        'p_title': title.trim(),
        'p_description': description.trim(),
        'p_assignee_ids': assigneeIds,
        'p_priority': _taskPriorityToDatabase(priority),
        'p_due_at': dueAt.toUtc().toIso8601String(),
        'p_labels': labels,
      },
    );
    await _loadTasks();
    notifyListeners();
  }

  Future<void> deleteTask(String taskId) async {
    _tasks.removeWhere((t) => t.id == taskId);
    notifyListeners();
    try {
      await _client.from('tasks').delete().eq('id', taskId);
    } catch (e, stackTrace) {
      debugPrint('Error deleting task: $e\n$stackTrace');
    }
    await _loadTasks();
    notifyListeners();
  }

  Future<void> updateCurrentUser(UserProfile updated) async {
    final authUser = _client.auth.currentUser;
    if (authUser == null || authUser.id != updated.id) {
      throw Exception('You can only update the signed-in profile.');
    }
    final privacy = _currentPrivacy();
    final update = <String, dynamic>{
      'username': ChatyValidators.normalizeUsername(updated.username),
      'display_name': updated.displayName.trim(),
      'about': updated.about.trim(),
      'phone': updated.phone.trim(),
      'avatar_initials': updated.avatarInitials,
      'avatar_color_hex': updated.avatarColorHex,
      if (updated.avatarUrl != null) 'avatar_url': updated.avatarUrl,
      if (updated.bannerUrl != null) 'banner_url': updated.bannerUrl,
      'presence': _presenceToDatabase(
        _effectivePublishedPresence(updated.presence, privacy),
      ),
    };
    if (privacy == null || !privacy.freezeLastSeen) {
      update['last_seen_at'] = DateTime.now().toUtc().toIso8601String();
    }
    await _client.from('profiles').update(update).eq('id', authUser.id);
    await _loadCurrentProfile();
    notifyListeners();
  }

  Future<void> setPresence(PresenceState presence) async {
    final authUser = _client.auth.currentUser;
    if (authUser == null) return;
    final privacy = _currentPrivacy();
    final update = <String, dynamic>{
      'presence': _presenceToDatabase(
        _effectivePublishedPresence(presence, privacy),
      ),
    };
    if (privacy == null || !privacy.freezeLastSeen) {
      update['last_seen_at'] = DateTime.now().toUtc().toIso8601String();
    }
    await _client.from('profiles').update(update).eq('id', authUser.id);
  }

  PrivacyPreferences? _currentPrivacy() {
    if (!locator.isRegistered<ChatyPreferencesController>()) return null;
    return locator<ChatyPreferencesController>().privacy;
  }

  PresenceState _effectivePublishedPresence(
    PresenceState presence,
    PrivacyPreferences? privacy,
  ) {
    if (privacy == null) return presence;
    final onlineHiddenFromAll =
        privacy.hideLastSeenAudience == 'Nobody' &&
        privacy.hideOnlineAudience == 'Same as Last Seen';
    if (onlineHiddenFromAll &&
        (presence == PresenceState.online ||
            presence == PresenceState.typing)) {
      return PresenceState.offline;
    }
    return presence;
  }

  void addStory(String content) {
    throw UnsupportedError(
      'Status publishing requires the production media/status service.',
    );
  }

  void markStoryViewed(String storyId) {}

  void logCall({
    required String receiverId,
    required CallType type,
    required CallDirection direction,
    required int durationSeconds,
  }) {}

  void revokeLinkedDevice(String deviceId) {
    _linkedDevices.removeWhere(
      (device) => device.id == deviceId && !device.isCurrentDevice,
    );
    notifyListeners();
  }

  Future<void> logout() async {
    try {
      await setPresence(PresenceState.offline);
    } catch (e, stackTrace) {
      debugPrint(
        'Error setting presence to offline during logout: $e\n$stackTrace',
      );
    }
    if (locator.isRegistered<MlsE2eeService>()) {
      await locator<MlsE2eeService>().close();
    }
    await _client.auth.signOut();
    await _handleSession(null);
  }

  Future<void> clearStateForTesting() async {
    await logout();
  }

  UserProfile _profileFromRow(
    Map<String, dynamic> row, {
    String email = '',
    String phone = '',
  }) {
    return UserProfile(
      id: row['id']?.toString() ?? '',
      displayName: row['display_name']?.toString() ?? 'Chaty User',
      username: row['username']?.toString() ?? 'user',
      avatarInitials: row['avatar_initials']?.toString() ?? 'CU',
      avatarColorHex: row['avatar_color_hex']?.toString() ?? '0xFF6366F1',
      about: row['about']?.toString() ?? row['bio']?.toString() ?? '',
      presence: _presenceFromDatabase(row['presence']?.toString()),
      lastSeenAt: _date(row['last_seen_at']) ?? DateTime.now(),
      isVerified: row['is_verified'] == true,
      email: email,
      phone: phone.isNotEmpty ? phone : (row['phone']?.toString() ?? ''),
      safetyNumber: '',
      avatarUrl: row['avatar_url']?.toString(),
      bannerUrl: row['banner_url']?.toString(),
    );
  }

  ChatMessage _messageFromRow(Map<String, dynamic> row) {
    final metadata = _stringDynamicMap(row['metadata']);
    final attachmentJson = _stringDynamicMap(metadata['attachment']);
    final reactions = <MessageReaction>[];
    final rawReactions = row['reactions'];
    if (rawReactions is List) {
      for (final raw in rawReactions) {
        final item = _stringDynamicMap(raw);
        reactions.add(
          MessageReaction(
            emoji: item['emoji']?.toString() ?? '',
            userIds: _stringList(item['user_ids']),
          ),
        );
      }
    }

    final deletedAt = _date(row['deleted_at']);
    final senderId = row['sender_id']?.toString() ?? '';
    final isReadByOther = row['is_read_by_other'] == true;
    final isMine = senderId == _currentUser?.id;

    return ChatMessage(
      id: row['id']?.toString() ?? '',
      conversationId: row['conversation_id']?.toString() ?? '',
      senderId: senderId,
      type: _messageTypeFromDatabase(row['type']?.toString()),
      text: deletedAt == null
          ? (row['body']?.toString() ?? '')
          : 'This message was deleted',
      attachment: attachmentJson.isEmpty
          ? null
          : MessageAttachment(
              id: attachmentJson['id']?.toString() ?? '',
              type: attachmentJson['type']?.toString() ?? 'document',
              name: attachmentJson['name']?.toString() ?? 'Attachment',
              size: attachmentJson['size']?.toString() ?? '',
              url: attachmentJson['url']?.toString(),
              durationSeconds: _integer(attachmentJson['duration_seconds']),
            ),
      replyToMessageId: metadata['reply_to_message_id']?.toString(),
      replyToPreviewText: metadata['reply_to_preview_text']?.toString(),
      replyToSenderName: metadata['reply_to_sender_name']?.toString(),
      linkedTaskId:
          metadata['task_id']?.toString() ??
          metadata['linked_task_id']?.toString(),
      reactions: reactions,
      createdAt: _date(row['created_at']) ?? DateTime.now(),
      editedAt: _date(row['edited_at']),
      deliveryState: isMine
          ? (isReadByOther ? DeliveryState.read : DeliveryState.sent)
          : DeliveryState.delivered,
      isPinned: row['is_pinned'] == true,
      isStarred: row['is_starred'] == true,
      isDeletedForEveryone: deletedAt != null,
      isDeletedForMe: row['is_hidden'] == true,
    );
  }

  ChatTask _taskFromRow(Map<String, dynamic> row) {
    final createdAt = _date(row['created_at']) ?? DateTime.now();
    return ChatTask(
      id: row['task_id']?.toString() ?? '',
      sourceConversationId: row['conversation_id']?.toString() ?? '',
      sourceMessageId: row['source_message_id']?.toString(),
      title: row['title']?.toString() ?? '',
      description: row['description']?.toString() ?? '',
      creatorId: row['creator_id']?.toString() ?? '',
      assigneeIds: _stringList(row['assignee_ids']),
      status: taskStatusFromDatabase(row['status']?.toString()),
      priority: _taskPriorityFromDatabase(row['priority']?.toString()),
      dueAt: _date(row['due_at']) ?? createdAt.add(const Duration(days: 3)),
      labels: _stringList(row['labels']),
      createdAt: createdAt,
      updatedAt: _date(row['updated_at']) ?? createdAt,
    );
  }

  static List<Map<String, dynamic>> _asRows(dynamic value) {
    if (value is! List) return <Map<String, dynamic>>[];
    return value
        .whereType<Map>()
        .map((item) => Map<String, dynamic>.from(item))
        .toList();
  }

  static Map<String, dynamic> _stringDynamicMap(dynamic value) {
    if (value is Map) return Map<String, dynamic>.from(value);
    return <String, dynamic>{};
  }

  static List<String> _stringList(dynamic value) {
    if (value is List) return value.map((item) => item.toString()).toList();
    return <String>[];
  }

  static DateTime? _date(dynamic value) {
    if (value == null) return null;
    if (value is DateTime) return value.toLocal();
    return DateTime.tryParse(value.toString())?.toLocal();
  }

  static int _integer(dynamic value) {
    if (value is int) return value;
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }

  static String _initials(String name) {
    final words = name
        .trim()
        .split(RegExp(r'\s+'))
        .where((word) => word.isNotEmpty)
        .toList();
    if (words.isEmpty) return 'CU';
    if (words.length == 1)
      return words.first
          .substring(0, words.first.length >= 2 ? 2 : 1)
          .toUpperCase();
    return '${words.first[0]}${words.last[0]}'.toUpperCase();
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

  static String _presenceToDatabase(PresenceState value) {
    switch (value) {
      case PresenceState.online:
        return 'online';
      case PresenceState.away:
        return 'away';
      case PresenceState.typing:
        return 'typing';
      case PresenceState.offline:
        return 'offline';
    }
  }

  static MessageType _messageTypeFromDatabase(String? value) {
    switch (value) {
      case 'image':
        return MessageType.image;
      case 'video':
        return MessageType.video;
      case 'audio':
        return MessageType.audio;
      case 'document':
        return MessageType.document;
      case 'location':
        return MessageType.location;
      case 'contact':
        return MessageType.contact;
      case 'task':
        return MessageType.taskCard;
      case 'system':
        return MessageType.system;
      default:
        return MessageType.text;
    }
  }

  static String _messageTypeToDatabase(MessageType value) {
    switch (value) {
      case MessageType.image:
        return 'image';
      case MessageType.video:
        return 'video';
      case MessageType.audio:
        return 'audio';
      case MessageType.document:
        return 'document';
      case MessageType.location:
        return 'location';
      case MessageType.contact:
        return 'contact';
      case MessageType.taskCard:
        return 'task';
      case MessageType.system:
        return 'system';
      case MessageType.text:
        return 'text';
    }
  }

  static TaskPriority _taskPriorityFromDatabase(String? value) {
    switch (value) {
      case 'low':
        return TaskPriority.low;
      case 'high':
        return TaskPriority.high;
      case 'urgent':
        return TaskPriority.urgent;
      default:
        return TaskPriority.medium;
    }
  }

  static String _taskPriorityToDatabase(TaskPriority value) {
    switch (value) {
      case TaskPriority.low:
        return 'low';
      case TaskPriority.medium:
        return 'normal';
      case TaskPriority.high:
        return 'high';
      case TaskPriority.urgent:
        return 'urgent';
    }
  }

  static TaskStatus taskStatusFromDatabase(String? value) {
    switch (value) {
      case 'assigned':
        return TaskStatus.assigned;
      case 'in_progress':
        return TaskStatus.inProgress;
      case 'blocked':
        return TaskStatus.blocked;
      case 'completed':
        return TaskStatus.completed;
      case 'cancelled':
      case 'archived':
        return TaskStatus.archived;
      case 'inbox':
      default:
        return TaskStatus.inbox;
    }
  }

  static String taskStatusToDatabase(TaskStatus value) {
    switch (value) {
      case TaskStatus.inProgress:
        return 'in_progress';
      case TaskStatus.completed:
        return 'completed';
      case TaskStatus.archived:
        return 'cancelled';
      case TaskStatus.blocked:
        return 'blocked';
      case TaskStatus.assigned:
        return 'assigned';
      case TaskStatus.inbox:
        return 'inbox';
    }
  }
}
