import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../domain/models/chat_message.dart';
import '../../domain/models/chat_task.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/other_models.dart';
import '../../domain/models/user_profile.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../services/backend_service.dart';
import '../services/gb_feature_backend_service.dart';

/// Compatibility adapter used by the existing presentation layer.
///
/// The historical class name is kept so every existing screen/option can stay
/// intact, but its data now comes from [ChatyBackendService] and Supabase rather
/// than seeded/demo records.
class ChatyDataStore extends ChangeNotifier {
  final ChatyBackendService _backend = ChatyBackendService();
  final GbFeatureBackendService _gbBackend = GbFeatureBackendService();
  final Map<String, Map<String, DateTime>> _typingByConversation =
      <String, Map<String, DateTime>>{};
  StreamSubscription<List<Map<String, dynamic>>>? _typingSubscription;
  StreamSubscription<AuthState>? _authSubscription;
  Timer? _typingExpiryTimer;

  ChatyDataStore() {
    _backend.addListener(_onBackendChanged);
    _authSubscription = Supabase.instance.client.auth.onAuthStateChange.listen((
      state,
    ) {
      if (state.session == null) {
        _typingByConversation.clear();
        unawaited(_typingSubscription?.cancel());
        _typingSubscription = null;
        notifyListeners();
      } else {
        _startTypingWatch();
      }
    });
    if (Supabase.instance.client.auth.currentSession != null) {
      _startTypingWatch();
    }
    _typingExpiryTimer = Timer.periodic(
      const Duration(seconds: 3),
      (_) => _purgeExpiredTyping(),
    );
  }

  void _onBackendChanged() => notifyListeners();

  void _startTypingWatch() {
    unawaited(_typingSubscription?.cancel());
    _typingSubscription = Supabase.instance.client
        .from('typing_states')
        .stream(primaryKey: const <String>['conversation_id', 'user_id'])
        .listen(
          (rows) {
            final next = <String, Map<String, DateTime>>{};
            for (final row in rows) {
              if (row['is_typing'] != true) continue;
              final conversationId = row['conversation_id']?.toString() ?? '';
              final userId = row['user_id']?.toString() ?? '';
              final updatedAt = DateTime.tryParse(
                row['updated_at']?.toString() ?? '',
              )?.toLocal();
              if (conversationId.isEmpty || userId.isEmpty || updatedAt == null) {
                continue;
              }
              (next[conversationId] ??= <String, DateTime>{})[userId] = updatedAt;
            }
            _typingByConversation
              ..clear()
              ..addAll(next);
            notifyListeners();
          },
          onError: (Object error, StackTrace stackTrace) {
            debugPrint('Chaty typing realtime failed: $error\n$stackTrace');
          },
        );
  }

  void _purgeExpiredTyping() {
    final cutoff = DateTime.now().subtract(const Duration(seconds: 10));
    var changed = false;
    for (final conversationId in _typingByConversation.keys.toList()) {
      final users = _typingByConversation[conversationId]!;
      users.removeWhere((_, updatedAt) {
        final expired = updatedAt.isBefore(cutoff);
        if (expired) changed = true;
        return expired;
      });
      if (users.isEmpty) _typingByConversation.remove(conversationId);
    }
    if (changed) notifyListeners();
  }

  @override
  void dispose() {
    _backend.removeListener(_onBackendChanged);
    _typingExpiryTimer?.cancel();
    unawaited(_typingSubscription?.cancel());
    unawaited(_authSubscription?.cancel());
    super.dispose();
  }

  UserProfile get currentUser {
    final hydrated = _backend.currentUser;
    if (hydrated != null) return hydrated;

    final authUser = Supabase.instance.client.auth.currentUser;
    if (authUser == null) {
      throw StateError('No authenticated Chaty user is available.');
    }
    final displayName =
        authUser.userMetadata?['display_name']?.toString().trim();
    final username = authUser.userMetadata?['username']?.toString().trim();
    final effectiveName = displayName != null && displayName.isNotEmpty
        ? displayName
        : (authUser.email?.split('@').first ?? 'Chaty User');
    final effectiveUsername = username != null && username.isNotEmpty
        ? username
        : (authUser.email?.split('@').first ?? 'user');
    final compact = effectiveName.replaceAll(RegExp(r'[^A-Za-z0-9]'), '');
    final initials = compact.isEmpty
        ? 'CU'
        : compact.substring(0, compact.length.clamp(1, 2)).toUpperCase();

    return UserProfile(
      id: authUser.id,
      displayName: effectiveName,
      username: effectiveUsername,
      avatarInitials: initials,
      avatarColorHex:
          authUser.userMetadata?['avatar_color_hex']?.toString() ??
          '0xFF6366F1',
      about: authUser.userMetadata?['about']?.toString() ?? '',
      presence: PresenceState.offline,
      lastSeenAt: DateTime.now(),
      isVerified: false,
      email: authUser.email ?? '',
      phone: authUser.phone ?? '',
      safetyNumber: '',
    );
  }

  bool get isAuthenticated => _backend.isAuthenticated;
  List<UserProfile> get contacts =>
      _backend.allUsers.where((user) => user.id != currentUser.id).toList();
  List<Conversation> get conversations => _backend.conversations;
  List<ChatTask> get tasks => _backend.tasks;
  List<CallRecord> get calls => _backend.calls;
  List<UpdateStory> get stories => _backend.stories;
  List<LinkedDevice> get linkedDevices => _backend.currentUserDevices;

  UserProfile? getUser(String userId) {
    if (_backend.currentUser?.id == userId) return _backend.currentUser;
    return _backend.getUserById(userId);
  }

  UserProfile? getUserById(String userId) => getUser(userId);
  UserProfile? getContact(String userId) => getUser(userId);

  List<ChatMessage> getMessages(String conversationId) =>
      _backend.getMessages(conversationId);

  Future<void> ensureConversationLoaded(String conversationId) =>
      _backend.ensureConversationLoaded(conversationId);

  Future<List<UserProfile>> searchUsersRemote(
    String query, {
    bool includeSelf = false,
  }) => _backend.searchUsersRemote(query, includeSelf: includeSelf);

  Future<Conversation> getOrCreateDirectConversation(UserProfile user) =>
      _backend.getOrCreateDirectConversationAsync(user);

  bool isTypingInChat(String conversationId) {
    final currentId = _backend.currentUser?.id;
    final cutoff = DateTime.now().subtract(const Duration(seconds: 10));
    return (_typingByConversation[conversationId] ?? const <String, DateTime>{})
        .entries
        .any((entry) => entry.key != currentId && entry.value.isAfter(cutoff));
  }

  bool isUserTyping(String conversationId, [String? userId]) {
    final target = userId;
    if (target == null) return isTypingInChat(conversationId);
    final updated = _typingByConversation[conversationId]?[target];
    return updated != null &&
        updated.isAfter(DateTime.now().subtract(const Duration(seconds: 10)));
  }

  void setTyping(String conversationId, bool isTyping) {
    var shouldPublish = isTyping;
    if (locator.isRegistered<ChatyPreferencesController>()) {
      final prefs = locator<ChatyPreferencesController>();
      shouldPublish =
          isTyping &&
          prefs.privacy.typingIndicators &&
          !prefs.home.ghostMode &&
          !prefs.gbBool('yo_want_ghostmode');
    }
    unawaited(_gbBackend.setTyping(conversationId, shouldPublish));
  }

  void logout() => unawaited(_backend.logout());

  Future<void> sendMessage({
    required String conversationId,
    required String text,
    MessageType type = MessageType.text,
    MessageAttachment? attachment,
    String? replyToMessageId,
    String? replyToPreviewText,
    String? replyToSenderName,
    String? linkedTaskId,
    Map<String, dynamic>? extraMetadata,
  }) async {
    await _backend.sendMessage(
      conversationId: conversationId,
      text: text,
      type: type,
      attachment: attachment,
      replyToMessageId: replyToMessageId,
      replyToPreviewText: replyToPreviewText,
      replyToSenderName: replyToSenderName,
      linkedTaskId: linkedTaskId,
      extraMetadata: extraMetadata,
    );
  }

  void toggleReaction(String conversationId, String messageId, String emoji) =>
      _backend.toggleReaction(conversationId, messageId, emoji);

  void deleteMessage(
    String conversationId,
    String messageId, {
    bool forEveryone = false,
  }) => _backend.deleteMessage(
    conversationId,
    messageId,
    forEveryone: forEveryone,
  );

  void markAsRead(String conversationId) =>
      unawaited(_backend.markAsRead(conversationId));

  void markAsUnread(String conversationId) =>
      unawaited(_backend.markAsUnread(conversationId));

  void deleteConversation(String conversationId) =>
      unawaited(_backend.deleteConversation(conversationId));

  void addCallRecord(CallRecord record) {
    _backend.addCall(record);
    notifyListeners();
  }

  void togglePinConversation(String conversationId) {
    final conversation = conversations
        .where((item) => item.id == conversationId)
        .firstOrNull;
    if (conversation != null) {
      _backend.setConversationState(
        conversationId,
        'pinned',
        !conversation.isPinned,
      );
    }
  }

  static const String _chatWallpaperKey = 'chaty_chat_wallpapers_v1';
  Map<String, String>? _chatWallpapers;

  Future<Map<String, String>> _loadChatWallpapers() async {
    if (_chatWallpapers != null) return _chatWallpapers!;
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_chatWallpaperKey);
      final decoded = raw == null ? null : jsonDecode(raw);
      _chatWallpapers = decoded is Map
          ? decoded.map((k, v) => MapEntry(k.toString(), v.toString()))
          : <String, String>{};
    } catch (error, stackTrace) {
      debugPrint('Chaty wallpaper preferences failed to load: $error\n$stackTrace');
      _chatWallpapers = <String, String>{};
    }
    return _chatWallpapers!;
  }

  Future<void> _saveChatWallpapers() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(
        _chatWallpaperKey,
        jsonEncode(_chatWallpapers ?? <String, String>{}),
      );
    } catch (error, stackTrace) {
      debugPrint('Chaty wallpaper preferences failed to save: $error\n$stackTrace');
    }
  }

  Future<String?> loadChatWallpaper(String conversationId) async {
    final map = await _loadChatWallpapers();
    return map[conversationId];
  }

  String? chatWallpaperSync(String conversationId) =>
      _chatWallpapers?[conversationId];

  void setChatWallpaper(String conversationId, String patternId) {
    _chatWallpapers ??= <String, String>{};
    _chatWallpapers![conversationId] = patternId;
    unawaited(_saveChatWallpapers());
    notifyListeners();
  }

  void toggleArchiveConversation(String conversationId) {
    final conversation = conversations
        .where((item) => item.id == conversationId)
        .firstOrNull;
    if (conversation != null) {
      _backend.setConversationState(
        conversationId,
        'archived',
        !conversation.isArchived,
      );
    }
  }

  void toggleMuteConversation(String conversationId) {
    final conversation = conversations
        .where((item) => item.id == conversationId)
        .firstOrNull;
    if (conversation != null) {
      _backend.setConversationState(
        conversationId,
        'muted',
        !conversation.isMuted,
      );
    }
  }

  void togglePinMessage(String conversationId, String messageId) {
    final message = getMessages(
      conversationId,
    ).where((item) => item.id == messageId).firstOrNull;
    if (message != null) {
      _backend.setMessageState(
        conversationId,
        messageId,
        'pinned',
        !message.isPinned,
      );
    }
  }

  void toggleStarMessage(String conversationId, String messageId) {
    final message = getMessages(
      conversationId,
    ).where((item) => item.id == messageId).firstOrNull;
    if (message != null) {
      _backend.setMessageState(
        conversationId,
        messageId,
        'starred',
        !message.isStarred,
      );
    }
  }

  void setDraft(String conversationId, String draft) =>
      _backend.setDraft(conversationId, draft);

  void createGroup({
    required String title,
    required List<String> memberIds,
    String? avatarInitials,
    String? avatarColorHex,
  }) {
    unawaited(
      _backend.createGroup(
        title: title,
        memberUserIds: memberIds,
        avatarInitials: avatarInitials,
        avatarColorHex: avatarColorHex,
      ),
    );
  }

  Future<Conversation> createGroupAsync({
    required String title,
    required List<String> memberIds,
    String? avatarInitials,
    String? avatarColorHex,
  }) => _backend.createGroup(
    title: title,
    memberUserIds: memberIds,
    avatarInitials: avatarInitials,
    avatarColorHex: avatarColorHex,
  );

  void createTask({
    required String sourceConversationId,
    String? sourceMessageId,
    required String title,
    required String description,
    required List<String> assigneeIds,
    required TaskPriority priority,
    required DateTime dueAt,
    List<String> labels = const <String>[],
  }) {
    unawaited(
      _backend.createTask(
        sourceConversationId: sourceConversationId,
        sourceMessageId: sourceMessageId,
        title: title,
        description: description,
        assigneeIds: assigneeIds,
        priority: priority,
        dueAt: dueAt,
        labels: labels,
      ),
    );
  }

  Future<ChatTask> createTaskAsync({
    required String sourceConversationId,
    String? sourceMessageId,
    required String title,
    required String description,
    required List<String> assigneeIds,
    required TaskPriority priority,
    required DateTime dueAt,
    List<String> labels = const <String>[],
  }) => _backend.createTask(
    sourceConversationId: sourceConversationId,
    sourceMessageId: sourceMessageId,
    title: title,
    description: description,
    assigneeIds: assigneeIds,
    priority: priority,
    dueAt: dueAt,
    labels: labels,
  );

  Future<void> updateTaskAsync({
    required String taskId,
    required String title,
    required String description,
    required List<String> assigneeIds,
    required TaskPriority priority,
    required DateTime dueAt,
    List<String> labels = const <String>[],
  }) async {
    await Supabase.instance.client.rpc(
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
  }

  Future<void> updateTaskStatus(String taskId, TaskStatus status) =>
      _backend.updateTaskStatus(taskId, status);

  void addStory(String content) => _backend.addStory(content);
  void markStoryViewed(String storyId) => _backend.markStoryViewed(storyId);

  void logCall({
    required String receiverId,
    required CallType type,
    required CallDirection direction,
    required int durationSeconds,
  }) {
    _backend.logCall(
      receiverId: receiverId,
      type: type,
      direction: direction,
      durationSeconds: durationSeconds,
    );
  }

  void revokeLinkedDevice(String deviceId) =>
      _backend.revokeLinkedDevice(deviceId);

  void updateProfile(UserProfile updated) =>
      unawaited(_backend.updateCurrentUser(updated));
  void updateCurrentUser(UserProfile updated) => updateProfile(updated);
  Future<void> updateUser(UserProfile updated) =>
      _backend.updateCurrentUser(updated);

  static String _taskPriorityToDatabase(TaskPriority priority) {
    switch (priority) {
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
}
