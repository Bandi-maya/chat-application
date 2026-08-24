import '../../domain/models/chat_message.dart';
import '../../domain/models/conversation.dart';

/// Persistence codecs for the offline-first snapshot cache.
///
/// The domain models stay serialization-free; these helpers translate between
/// them and the JSON representation stored by [LocalSnapshotCacheService].
/// Unknown enum values fall back to safe defaults so a snapshot written by a
/// newer build never crashes an older one.

Conversation conversationFromJson(Map<String, dynamic> json) {
  return Conversation(
    id: json['id']?.toString() ?? '',
    type: _conversationType(json['type']?.toString()),
    title: json['title']?.toString() ?? '',
    participantIds: _stringList(json['participant_ids']),
    adminIds: _stringList(json['admin_ids']),
    avatarInitials: json['avatar_initials']?.toString(),
    avatarColorHex: json['avatar_color_hex']?.toString(),
    lastMessageText: json['last_message_text']?.toString() ?? '',
    lastMessageTime:
        DateTime.tryParse(json['last_message_time']?.toString() ?? '') ??
        DateTime.fromMillisecondsSinceEpoch(0),
    lastMessageSenderId: json['last_message_sender_id']?.toString() ?? '',
    unreadCount: int.tryParse('${json['unread_count'] ?? 0}') ?? 0,
    isPinned: json['is_pinned'] == true,
    isArchived: json['is_archived'] == true,
    isMuted: json['is_muted'] == true,
    wallpaperId: json['wallpaper_id']?.toString(),
    disappearingDuration: json['disappearing_seconds'] == null
        ? null
        : Duration(
            seconds: int.tryParse('${json['disappearing_seconds']}') ?? 0,
          ),
    encryptionStatus: _encryptionStatus(json['encryption_status']?.toString()),
    draftText: json['draft_text']?.toString() ?? '',
  );
}

Map<String, dynamic> conversationToJson(Conversation conversation) {
  return <String, dynamic>{
    'id': conversation.id,
    'type': conversation.type.name,
    'title': conversation.title,
    'participant_ids': conversation.participantIds,
    'admin_ids': conversation.adminIds,
    if (conversation.avatarInitials != null)
      'avatar_initials': conversation.avatarInitials,
    if (conversation.avatarColorHex != null)
      'avatar_color_hex': conversation.avatarColorHex,
    'last_message_text': conversation.lastMessageText,
    'last_message_time': conversation.lastMessageTime.toIso8601String(),
    'last_message_sender_id': conversation.lastMessageSenderId,
    'unread_count': conversation.unreadCount,
    'is_pinned': conversation.isPinned,
    'is_archived': conversation.isArchived,
    'is_muted': conversation.isMuted,
    if (conversation.wallpaperId != null)
      'wallpaper_id': conversation.wallpaperId,
    if (conversation.disappearingDuration != null)
      'disappearing_seconds': conversation.disappearingDuration!.inSeconds
          .toString(),
    'encryption_status': conversation.encryptionStatus.name,
    'draft_text': conversation.draftText,
  };
}

ChatMessage chatMessageFromJson(Map<String, dynamic> json) {
  final attachment = json['attachment'] is Map
      ? MessageAttachment(
          id: json['attachment']['id']?.toString() ?? '',
          type: json['attachment']['type']?.toString() ?? 'file',
          name: json['attachment']['name']?.toString() ?? '',
          size: json['attachment']['size']?.toString() ?? '',
          url: json['attachment']['url']?.toString(),
          durationSeconds:
              int.tryParse('${json['attachment']['duration_seconds'] ?? 0}') ??
              0,
        )
      : null;
  final reactions = <MessageReaction>[];
  if (json['reactions'] is List) {
    for (final item in json['reactions'] as List) {
      if (item is! Map) continue;
      reactions.add(
        MessageReaction(
          emoji: item['emoji']?.toString() ?? '',
          userIds: _stringList(item['user_ids']),
        ),
      );
    }
  }
  return ChatMessage(
    id: json['id']?.toString() ?? '',
    conversationId: json['conversation_id']?.toString() ?? '',
    senderId: json['sender_id']?.toString() ?? '',
    type: _messageType(json['type']?.toString()),
    text: json['text']?.toString() ?? '',
    attachment: attachment,
    metadata: json['metadata'] is Map
        ? Map<String, dynamic>.from(json['metadata'] as Map)
        : const <String, dynamic>{},
    replyToMessageId: json['reply_to_message_id']?.toString(),
    replyToPreviewText: json['reply_to_preview_text']?.toString(),
    replyToSenderName: json['reply_to_sender_name']?.toString(),
    linkedTaskId: json['linked_task_id']?.toString(),
    reactions: reactions,
    createdAt:
        DateTime.tryParse(json['created_at']?.toString() ?? '') ??
        DateTime.fromMillisecondsSinceEpoch(0),
    editedAt: json['edited_at'] == null
        ? null
        : DateTime.tryParse(json['edited_at'].toString()),
    deliveryState: _deliveryState(json['delivery_state']?.toString()),
    isPinned: json['is_pinned'] == true,
    isStarred: json['is_starred'] == true,
    isDeletedForEveryone: json['deleted_for_everyone'] == true,
    isDeletedForMe: json['deleted_for_me'] == true,
  );
}

Map<String, dynamic> chatMessageToJson(ChatMessage message) {
  return <String, dynamic>{
    'id': message.id,
    'conversation_id': message.conversationId,
    'sender_id': message.senderId,
    'type': message.type.name,
    'text': message.text,
    if (message.attachment != null)
      'attachment': <String, dynamic>{
        'id': message.attachment!.id,
        'type': message.attachment!.type,
        'name': message.attachment!.name,
        'size': message.attachment!.size,
        if (message.attachment!.url != null) 'url': message.attachment!.url,
        'duration_seconds': message.attachment!.durationSeconds,
      },
    'metadata': message.metadata,
    if (message.replyToMessageId != null)
      'reply_to_message_id': message.replyToMessageId,
    if (message.replyToPreviewText != null)
      'reply_to_preview_text': message.replyToPreviewText,
    if (message.replyToSenderName != null)
      'reply_to_sender_name': message.replyToSenderName,
    if (message.linkedTaskId != null) 'linked_task_id': message.linkedTaskId,
    'reactions': message.reactions
        .map(
          (reaction) => <String, dynamic>{
            'emoji': reaction.emoji,
            'user_ids': reaction.userIds,
          },
        )
        .toList(growable: false),
    'created_at': message.createdAt.toIso8601String(),
    if (message.editedAt != null)
      'edited_at': message.editedAt!.toIso8601String(),
    'delivery_state': message.deliveryState.name,
    'is_pinned': message.isPinned,
    'is_starred': message.isStarred,
    'deleted_for_everyone': message.isDeletedForEveryone,
    'deleted_for_me': message.isDeletedForMe,
  };
}

ConversationType _conversationType(String? value) => switch (value) {
  'group' => ConversationType.group,
  _ => ConversationType.direct,
};

EncryptionStatus _encryptionStatus(String? value) => switch (value) {
  'encrypted' => EncryptionStatus.encrypted,
  'demoMode' => EncryptionStatus.demoMode,
  _ => EncryptionStatus.verificationNeeded,
};

MessageType _messageType(String? value) => switch (value) {
  'image' => MessageType.image,
  'video' => MessageType.video,
  'audio' => MessageType.audio,
  'document' => MessageType.document,
  'location' => MessageType.location,
  'contact' => MessageType.contact,
  'taskCard' => MessageType.taskCard,
  'system' => MessageType.system,
  _ => MessageType.text,
};

DeliveryState _deliveryState(String? value) => switch (value) {
  'queued' => DeliveryState.queued,
  'sending' => DeliveryState.sending,
  'sent' => DeliveryState.sent,
  'read' => DeliveryState.read,
  'failed' => DeliveryState.failed,
  _ => DeliveryState.delivered,
};

List<String> _stringList(Object? source) {
  if (source is! List) return <String>[];
  return source.map((item) => item.toString()).toList(growable: false);
}
