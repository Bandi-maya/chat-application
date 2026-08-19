import 'dart:async';

import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../domain/models/chat_message.dart';
import '../../domain/models/chat_task.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/other_models.dart';
import '../../domain/models/user_profile.dart';
import '../services/chaty_backend_service.dart';

/// Compatibility adapter used by the existing presentation layer.
///
/// The historical class name is kept so every existing screen/option can stay
/// intact, but its data now comes from [ChatyBackendService] and Supabase rather
/// than seeded/demo records.
class MockDataStore extends ChangeNotifier {
  final ChatyBackendService _backend = ChatyBackendService();

  MockDataStore() {
    _backend.addListener(_onBackendChanged);
  }

  void _onBackendChanged() => notifyListeners();

  @override
  void dispose() {
    _backend.removeListener(_onBackendChanged);
    super.dispose();
  }

  UserProfile get currentUser => _backend.currentUser ?? UserProfile(
        id: 'usr_guest',
        displayName: 'Chaty User',
        username: 'guest',
        avatarInitials: 'CU',
        avatarColorHex: '0xFF6366F1',
        about: '',
        presence: PresenceState.offline,
        lastSeenAt: DateTime.now(),
        isVerified: false,
        safetyNumber: '',
      );

  bool get isAuthenticated => _backend.isAuthenticated;
  List<UserProfile> get seededAccounts => _backend.allUsers;
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
  }) =>
      _backend.searchUsersRemote(query, includeSelf: includeSelf);

  Future<Conversation> getOrCreateDirectConversation(UserProfile user) =>
      _backend.getOrCreateDirectConversationAsync(user);

  bool isTypingInChat(String conversationId) => false;
  bool isUserTyping(String conversationId, [String? userId]) => false;
  void setTyping(String conversationId, bool isTyping) {}

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
    );
  }

  void toggleReaction(String conversationId, String messageId, String emoji) =>
      _backend.toggleReaction(conversationId, messageId, emoji);

  void deleteMessage(
    String conversationId,
    String messageId, {
    bool forEveryone = false,
  }) =>
      _backend.deleteMessage(
        conversationId,
        messageId,
        forEveryone: forEveryone,
      );

  void markAsRead(String conversationId) =>
      unawaited(_backend.markAsRead(conversationId));

  void togglePinConversation(String conversationId) {
    final conversation =
        conversations.where((item) => item.id == conversationId).firstOrNull;
    if (conversation != null) {
      _backend.setConversationState(
        conversationId,
        'pinned',
        !conversation.isPinned,
      );
    }
  }

  void toggleArchiveConversation(String conversationId) {
    final conversation =
        conversations.where((item) => item.id == conversationId).firstOrNull;
    if (conversation != null) {
      _backend.setConversationState(
        conversationId,
        'archived',
        !conversation.isArchived,
      );
    }
  }

  void toggleMuteConversation(String conversationId) {
    final conversation =
        conversations.where((item) => item.id == conversationId).firstOrNull;
    if (conversation != null) {
      _backend.setConversationState(
        conversationId,
        'muted',
        !conversation.isMuted,
      );
    }
  }

  void togglePinMessage(String conversationId, String messageId) {
    final message = getMessages(conversationId)
        .where((item) => item.id == messageId)
        .firstOrNull;
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
    final message = getMessages(conversationId)
        .where((item) => item.id == messageId)
        .firstOrNull;
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
  }) =>
      _backend.createGroup(
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
  }) =>
      _backend.createTask(
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

  void updateTaskStatus(String taskId, TaskStatus status) =>
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

  /// Kept only for binary/source compatibility with old screens. It no longer
  /// changes identity. Supabase Auth owns the active session.
  void switchDemoAccount(UserProfile user) {
    if (user.id == _backend.currentUser?.id) notifyListeners();
  }

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
