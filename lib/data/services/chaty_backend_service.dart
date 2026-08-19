import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';

import '../../domain/models/chat_message.dart';
import '../../domain/models/chat_task.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/other_models.dart';
import '../../domain/models/user_profile.dart';
import '../../ui/core/realtime/realtime_event_bus.dart';
import '../../ui/core/validators/chaty_validators.dart';

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

  final RealtimeEventBus eventBus = RealtimeEventBus();
  final Uuid _uuid = const Uuid();

  SupabaseClient get _client => Supabase.instance.client;

  UserProfile? _currentUser;
  AuthSession? _currentSession;
  final Map<String, UserProfile> _usersById = <String, UserProfile>{};
  final Map<String, Conversation> _conversationsById = <String, Conversation>{};
  final Map<String, List<ChatMessage>> _messagesByChatId = <String, List<ChatMessage>>{};
  final List<ChatTask> _tasks = <ChatTask>[];
  final List<CallRecord> _calls = <CallRecord>[];
  final List<UpdateStory> _stories = <UpdateStory>[];
  final List<LinkedDevice> _linkedDevices = <LinkedDevice>[];

  RealtimeChannel? _realtimeChannel;
  Timer? _reconcileTimer;
  bool _isInitialized = false;
  bool _isHydrating = false;

  bool get isInitialized => _isInitialized;
  bool get isAuthenticated => _client.auth.currentSession != null && _currentUser != null;
  UserProfile? get currentUser => _currentUser;
  AuthSession? get currentSession => _currentSession;
  List<UserProfile> get allUsers => List<UserProfile>.unmodifiable(_usersById.values);

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
  List<LinkedDevice> get currentUserDevices => List<LinkedDevice>.unmodifiable(_linkedDevices);

  Future<void> initialize() async {
    if (_isInitialized) return;

    _client.auth.onAuthStateChange.listen((AuthState state) {
      unawaited(_handleSession(state.session));
    });

    await _handleSession(_client.auth.currentSession);
    _isInitialized = true;
    notifyListeners();
  }

  Future<void> _handleSession(Session? session) async {
    if (session == null) {
      await _removeRealtimeChannel();
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
    await _hydrateAuthenticatedState();
    await _subscribeRealtime();
  }

  AuthSession _mapSession(Session session) {
    final expiresSeconds = session.expiresAt ??
        DateTime.now().add(const Duration(hours: 1)).millisecondsSinceEpoch ~/ 1000;
    return AuthSession(
      userId: session.user.id,
      token: session.accessToken,
      expiresAt: DateTime.fromMillisecondsSinceEpoch(expiresSeconds * 1000, isUtc: true),
      deviceId: 'device_${session.user.id.substring(0, 8)}',
    );
  }

  Future<void> _hydrateAuthenticatedState() async {
    if (_isHydrating) return;
    _isHydrating = true;
    try {
      await _loadCurrentProfile();
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

    final row = await _client.from('profiles').select().eq('id', user.id).single();
    final profile = _profileFromRow(
      Map<String, dynamic>.from(row),
      email: user.email ?? '',
      phone: user.phone ?? '',
    );
    _currentUser = profile;
    _usersById[profile.id] = profile;

    unawaited(
      _client
          .from('profiles')
          .update(<String, dynamic>{
            'presence': 'online',
            'last_seen_at': DateTime.now().toUtc().toIso8601String(),
          })
          .eq('id', user.id),
    );
  }

  Future<void> _loadConversations() async {
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
        type: row['kind'] == 'group' ? ConversationType.group : ConversationType.direct,
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

    await Future.wait<void>(next.keys.map(_loadConversationMembers));
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
    final messages = rows
        .where((row) => row['is_hidden'] != true)
        .map(_messageFromRow)
        .toList()
      ..sort((a, b) => a.createdAt.compareTo(b.createdAt));
    _messagesByChatId[conversationId] = messages;
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
          callback: (_) => _scheduleReconciliation(),
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'conversation_members',
          callback: (_) => _scheduleReconciliation(),
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'message_reactions',
          callback: (_) => _scheduleReconciliation(),
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'message_receipts',
          callback: (_) => _scheduleReconciliation(),
        )
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'tasks',
          callback: (_) => _scheduleReconciliation(),
        )
        .subscribe();
    _realtimeChannel = channel;
  }

  void _scheduleReconciliation() {
    _reconcileTimer?.cancel();
    _reconcileTimer = Timer(const Duration(milliseconds: 120), () async {
      try {
        await _hydrateAuthenticatedState();
        eventBus.publish(RealtimeEvent(type: RealtimeEventType.conversationUpdated));
      } catch (error, stackTrace) {
        debugPrint('Chaty realtime reconciliation failed: $error\n$stackTrace');
      }
    });
  }

  Future<void> _removeRealtimeChannel() async {
    _reconcileTimer?.cancel();
    final channel = _realtimeChannel;
    _realtimeChannel = null;
    if (channel != null) {
      try {
        await _client.removeChannel(channel);
      } catch (_) {}
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
    if (displayName.trim().length < 2) throw Exception('Display name is required.');
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
      throw Exception('Sign in with your registered email address. Username discovery remains available after login.');
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
    throw Exception('$provider sign-in is not configured on the Chaty Supabase project yet. Use email/password sign-in.');
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
      params: <String, dynamic>{'p_username': ChatyValidators.normalizeUsername(username)},
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

  Future<List<UserProfile>> searchUsersRemote(String query, {bool includeSelf = false}) async {
    final trimmed = query.trim();
    if (trimmed.length < 2) return <UserProfile>[];
    final raw = await _client.rpc('search_profiles', params: <String, dynamic>{'p_query': trimmed});
    final results = _asRows(raw).map(_profileFromRow).toList();
    for (final profile in results) {
      _usersById[profile.id] = profile;
    }
    if (includeSelf && _currentUser != null) results.insert(0, _currentUser!);
    notifyListeners();
    return results;
  }

  Future<Conversation> getOrCreateDirectConversationAsync(UserProfile otherUser) async {
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
    throw StateError('Conversation is not loaded. Use getOrCreateDirectConversationAsync().');
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
      List<ChatMessage>.unmodifiable(_messagesByChatId[conversationId] ?? const <ChatMessage>[]);

  Future<ChatMessage> sendMessage({
    required String conversationId,
    required String text,
    MessageType type = MessageType.text,
    MessageAttachment? attachment,
    String? replyToMessageId,
    String? replyToPreviewText,
    String? replyToSenderName,
    String? linkedTaskId,
  }) async {
    final me = _currentUser;
    if (me == null) throw Exception('Authentication required.');
    if (!_conversationsById.containsKey(conversationId)) throw Exception('Conversation not found.');

    final clientMessageId = _uuid.v4();
    final metadata = <String, dynamic>{
      if (replyToMessageId != null) 'reply_to_message_id': replyToMessageId,
      if (replyToPreviewText != null) 'reply_to_preview_text': replyToPreviewText,
      if (replyToSenderName != null) 'reply_to_sender_name': replyToSenderName,
      if (linkedTaskId != null) 'linked_task_id': linkedTaskId,
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

    final raw = await _client.rpc(
      'send_message',
      params: <String, dynamic>{
        'p_conversation_id': conversationId,
        'p_client_message_id': clientMessageId,
        'p_body': text.trim(),
        'p_type': _messageTypeToDatabase(type),
        'p_metadata': metadata,
      },
    );
    final messageId = raw?.toString() ?? '';
    await Future.wait<void>(<Future<void>>[_loadMessages(conversationId), _loadConversations()]);
    notifyListeners();

    final result = _messagesByChatId[conversationId]!.firstWhere(
      (message) => message.id == messageId,
      orElse: () => ChatMessage(
        id: messageId,
        conversationId: conversationId,
        senderId: me.id,
        type: type,
        text: text.trim(),
        attachment: attachment,
        createdAt: DateTime.now(),
        deliveryState: DeliveryState.sent,
      ),
    );
    eventBus.publish(RealtimeEvent(
      type: RealtimeEventType.messageCreated,
      conversationId: conversationId,
      userId: me.id,
      payload: <String, dynamic>{'messageId': result.id},
    ));
    return result;
  }

  void toggleReaction(String conversationId, String messageId, String emoji) {
    unawaited(_toggleReactionAsync(conversationId, messageId, emoji));
  }

  Future<void> _toggleReactionAsync(String conversationId, String messageId, String emoji) async {
    await _client.rpc('toggle_message_reaction', params: <String, dynamic>{
      'p_message_id': messageId,
      'p_emoji': emoji,
    });
    await _loadMessages(conversationId);
    notifyListeners();
  }

  void deleteMessage(String conversationId, String messageId, {bool forEveryone = false}) {
    unawaited(_deleteMessageAsync(conversationId, messageId, forEveryone));
  }

  Future<void> _deleteMessageAsync(String conversationId, String messageId, bool forEveryone) async {
    await _client.rpc('delete_chat_message', params: <String, dynamic>{
      'p_message_id': messageId,
      'p_for_everyone': forEveryone,
    });
    await Future.wait<void>(<Future<void>>[_loadMessages(conversationId), _loadConversations()]);
    notifyListeners();
  }

  Future<void> markAsRead(String conversationId) async {
    await _client.rpc('mark_conversation_read', params: <String, dynamic>{'p_conversation_id': conversationId});
    await _loadConversations();
    if (_messagesByChatId.containsKey(conversationId)) await _loadMessages(conversationId);
    notifyListeners();
  }

  void setConversationState(String conversationId, String field, bool value) {
    unawaited(_setConversationStateAsync(conversationId, field, value));
  }

  Future<void> _setConversationStateAsync(String conversationId, String field, bool value) async {
    await _client.rpc('set_conversation_state', params: <String, dynamic>{
      'p_conversation_id': conversationId,
      'p_field': field,
      'p_value': value,
    });
    await _loadConversations();
    notifyListeners();
  }

  void setMessageState(String conversationId, String messageId, String field, bool value) {
    unawaited(_setMessageStateAsync(conversationId, messageId, field, value));
  }

  Future<void> _setMessageStateAsync(String conversationId, String messageId, String field, bool value) async {
    await _client.rpc('set_message_user_state', params: <String, dynamic>{
      'p_message_id': messageId,
      'p_field': field,
      'p_value': value,
    });
    await _loadMessages(conversationId);
    notifyListeners();
  }

  void setDraft(String conversationId, String draft) {
    final current = _conversationsById[conversationId];
    if (current != null) {
      _conversationsById[conversationId] = current.copyWith(draftText: draft);
      notifyListeners();
    }
    unawaited(_client.rpc('set_conversation_draft', params: <String, dynamic>{
      'p_conversation_id': conversationId,
      'p_draft': draft,
    }));
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
    final raw = await _client.rpc('create_chat_task', params: <String, dynamic>{
      'p_conversation_id': sourceConversationId,
      'p_client_task_id': clientTaskId,
      'p_title': title.trim(),
      'p_assignee_ids': assigneeIds,
      'p_priority': _taskPriorityToDatabase(priority),
      'p_due_at': dueAt.toUtc().toIso8601String(),
      'p_description': description.trim(),
      'p_labels': labels,
      'p_source_message_id': sourceMessageId,
    });
    final id = raw?.toString() ?? '';
    await Future.wait<void>(<Future<void>>[
      _loadTasks(),
      _loadConversations(),
      if (_messagesByChatId.containsKey(sourceConversationId)) _loadMessages(sourceConversationId),
    ]);
    notifyListeners();
    return _tasks.firstWhere((task) => task.id == id);
  }

  void updateTaskStatus(String taskId, TaskStatus status) {
    unawaited(_updateTaskStatusAsync(taskId, status));
  }

  Future<void> _updateTaskStatusAsync(String taskId, TaskStatus status) async {
    await _client.rpc('update_task_status', params: <String, dynamic>{
      'p_task_id': taskId,
      'p_status': _taskStatusToDatabase(status),
    });
    await _loadTasks();
    await _refreshLoadedMessageTimelines();
    notifyListeners();
  }

  Future<void> updateCurrentUser(UserProfile updated) async {
    final authUser = _client.auth.currentUser;
    if (authUser == null || authUser.id != updated.id) {
      throw Exception('You can only update the signed-in profile.');
    }
    await _client.from('profiles').update(<String, dynamic>{
      'username': ChatyValidators.normalizeUsername(updated.username),
      'display_name': updated.displayName.trim(),
      'about': updated.about.trim(),
      'phone': updated.phone.trim(),
      'avatar_initials': updated.avatarInitials,
      'avatar_color_hex': updated.avatarColorHex,
      'presence': _presenceToDatabase(updated.presence),
      'last_seen_at': DateTime.now().toUtc().toIso8601String(),
    }).eq('id', authUser.id);
    await _loadCurrentProfile();
    notifyListeners();
  }

  Future<void> setPresence(PresenceState presence) async {
    final authUser = _client.auth.currentUser;
    if (authUser == null) return;
    await _client.from('profiles').update(<String, dynamic>{
      'presence': _presenceToDatabase(presence),
      'last_seen_at': DateTime.now().toUtc().toIso8601String(),
    }).eq('id', authUser.id);
  }

  void addStory(String content) {
    throw UnsupportedError('Status publishing requires the production media/status service.');
  }

  void markStoryViewed(String storyId) {}

  void logCall({
    required String receiverId,
    required CallType type,
    required CallDirection direction,
    required int durationSeconds,
  }) {}

  void revokeLinkedDevice(String deviceId) {
    _linkedDevices.removeWhere((device) => device.id == deviceId && !device.isCurrentDevice);
    notifyListeners();
  }

  Future<void> logout() async {
    try {
      await setPresence(PresenceState.offline);
    } catch (_) {}
    await _client.auth.signOut();
    await _handleSession(null);
  }

  Future<void> clearStateForTesting() async {
    await logout();
  }

  UserProfile _profileFromRow(Map<String, dynamic> row, {String email = '', String phone = ''}) {
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
        reactions.add(MessageReaction(
          emoji: item['emoji']?.toString() ?? '',
          userIds: _stringList(item['user_ids']),
        ));
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
      text: deletedAt == null ? (row['body']?.toString() ?? '') : 'This message was deleted',
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
      linkedTaskId: metadata['task_id']?.toString() ?? metadata['linked_task_id']?.toString(),
      reactions: reactions,
      createdAt: _date(row['created_at']) ?? DateTime.now(),
      editedAt: _date(row['edited_at']),
      deliveryState: isMine ? (isReadByOther ? DeliveryState.read : DeliveryState.sent) : DeliveryState.delivered,
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
      status: _taskStatusFromDatabase(row['status']?.toString()),
      priority: _taskPriorityFromDatabase(row['priority']?.toString()),
      dueAt: _date(row['due_at']) ?? createdAt.add(const Duration(days: 3)),
      labels: _stringList(row['labels']),
      createdAt: createdAt,
      updatedAt: _date(row['updated_at']) ?? createdAt,
    );
  }

  static List<Map<String, dynamic>> _asRows(dynamic value) {
    if (value is! List) return <Map<String, dynamic>>[];
    return value.whereType<Map>().map((item) => Map<String, dynamic>.from(item)).toList();
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
    final words = name.trim().split(RegExp(r'\s+')).where((word) => word.isNotEmpty).toList();
    if (words.isEmpty) return 'CU';
    if (words.length == 1) return words.first.substring(0, words.first.length >= 2 ? 2 : 1).toUpperCase();
    return '${words.first[0]}${words.last[0]}'.toUpperCase();
  }

  static PresenceState _presenceFromDatabase(String? value) {
    switch (value) {
      case 'online': return PresenceState.online;
      case 'away': return PresenceState.away;
      case 'typing': return PresenceState.typing;
      default: return PresenceState.offline;
    }
  }

  static String _presenceToDatabase(PresenceState value) {
    switch (value) {
      case PresenceState.online: return 'online';
      case PresenceState.away: return 'away';
      case PresenceState.typing: return 'typing';
      case PresenceState.offline: return 'offline';
    }
  }

  static MessageType _messageTypeFromDatabase(String? value) {
    switch (value) {
      case 'image': return MessageType.image;
      case 'video': return MessageType.video;
      case 'audio': return MessageType.audio;
      case 'document': return MessageType.document;
      case 'location': return MessageType.location;
      case 'contact': return MessageType.contact;
      case 'task': return MessageType.taskCard;
      case 'system': return MessageType.system;
      default: return MessageType.text;
    }
  }

  static String _messageTypeToDatabase(MessageType value) {
    switch (value) {
      case MessageType.image: return 'image';
      case MessageType.video: return 'video';
      case MessageType.audio: return 'audio';
      case MessageType.document: return 'document';
      case MessageType.location: return 'location';
      case MessageType.contact: return 'contact';
      case MessageType.taskCard: return 'task';
      case MessageType.system: return 'system';
      case MessageType.text: return 'text';
    }
  }

  static TaskPriority _taskPriorityFromDatabase(String? value) {
    switch (value) {
      case 'low': return TaskPriority.low;
      case 'high': return TaskPriority.high;
      case 'urgent': return TaskPriority.urgent;
      default: return TaskPriority.medium;
    }
  }

  static String _taskPriorityToDatabase(TaskPriority value) {
    switch (value) {
      case TaskPriority.low: return 'low';
      case TaskPriority.medium: return 'normal';
      case TaskPriority.high: return 'high';
      case TaskPriority.urgent: return 'urgent';
    }
  }

  static TaskStatus _taskStatusFromDatabase(String? value) {
    switch (value) {
      case 'in_progress': return TaskStatus.inProgress;
      case 'completed': return TaskStatus.completed;
      case 'cancelled': return TaskStatus.archived;
      default: return TaskStatus.inbox;
    }
  }

  static String _taskStatusToDatabase(TaskStatus value) {
    switch (value) {
      case TaskStatus.inProgress: return 'in_progress';
      case TaskStatus.completed: return 'completed';
      case TaskStatus.archived: return 'cancelled';
      case TaskStatus.blocked: return 'in_progress';
      case TaskStatus.assigned: return 'todo';
      case TaskStatus.inbox: return 'todo';
    }
  }
}
