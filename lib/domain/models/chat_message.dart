enum MessageType {
  text,
  image,
  video,
  audio,
  document,
  location,
  contact,
  taskCard,
  system,
}

String messageTypeToDatabase(MessageType type) => switch (type) {
      MessageType.image => 'image',
      MessageType.video => 'video',
      MessageType.audio => 'audio',
      MessageType.document => 'document',
      MessageType.location => 'location',
      MessageType.contact => 'contact',
      MessageType.taskCard => 'task',
      MessageType.system => 'system',
      MessageType.text => 'text',
    };

MessageType messageTypeFromDatabase(String? type) => switch (type) {
      'image' => MessageType.image,
      'video' => MessageType.video,
      'audio' => MessageType.audio,
      'document' => MessageType.document,
      'location' => MessageType.location,
      'contact' => MessageType.contact,
      'task' => MessageType.taskCard,
      'system' => MessageType.system,
      _ => MessageType.text,
    };

enum DeliveryState { queued, sending, sent, delivered, read, failed }

class MessageReaction {
  final String emoji;
  final List<String> userIds;

  const MessageReaction({required this.emoji, required this.userIds});

  MessageReaction copyWith({String? emoji, List<String>? userIds}) {
    return MessageReaction(
      emoji: emoji ?? this.emoji,
      userIds: userIds ?? this.userIds,
    );
  }
}

class MessageAttachment {
  final String id;
  final String type;
  final String name;
  final String size;
  final String? url;
  final int durationSeconds;

  const MessageAttachment({
    required this.id,
    required this.type,
    required this.name,
    required this.size,
    this.url,
    this.durationSeconds = 0,
  });
}

class ChatMessage {
  final String id;
  final String conversationId;
  final String senderId;
  final MessageType type;
  final String text;
  final MessageAttachment? attachment;
  final Map<String, dynamic> metadata;
  final String? replyToMessageId;
  final String? replyToPreviewText;
  final String? replyToSenderName;
  final String? linkedTaskId;
  final List<MessageReaction> reactions;
  final DateTime createdAt;
  final DateTime? editedAt;
  final DeliveryState deliveryState;
  final bool isPinned;
  final bool isStarred;
  final bool isDeletedForEveryone;
  final bool isDeletedForMe;

  const ChatMessage({
    required this.id,
    required this.conversationId,
    required this.senderId,
    this.type = MessageType.text,
    required this.text,
    this.attachment,
    this.metadata = const <String, dynamic>{},
    this.replyToMessageId,
    this.replyToPreviewText,
    this.replyToSenderName,
    this.linkedTaskId,
    this.reactions = const [],
    required this.createdAt,
    this.editedAt,
    this.deliveryState = DeliveryState.delivered,
    this.isPinned = false,
    this.isStarred = false,
    this.isDeletedForEveryone = false,
    this.isDeletedForMe = false,
  });

  ChatMessage copyWith({
    String? id,
    String? conversationId,
    String? senderId,
    MessageType? type,
    String? text,
    MessageAttachment? attachment,
    Map<String, dynamic>? metadata,
    String? replyToMessageId,
    String? replyToPreviewText,
    String? replyToSenderName,
    String? linkedTaskId,
    List<MessageReaction>? reactions,
    DateTime? createdAt,
    DateTime? editedAt,
    DeliveryState? deliveryState,
    bool? isPinned,
    bool? isStarred,
    bool? isDeletedForEveryone,
    bool? isDeletedForMe,
  }) {
    return ChatMessage(
      id: id ?? this.id,
      conversationId: conversationId ?? this.conversationId,
      senderId: senderId ?? this.senderId,
      type: type ?? this.type,
      text: text ?? this.text,
      attachment: attachment ?? this.attachment,
      metadata: metadata ?? this.metadata,
      replyToMessageId: replyToMessageId ?? this.replyToMessageId,
      replyToPreviewText: replyToPreviewText ?? this.replyToPreviewText,
      replyToSenderName: replyToSenderName ?? this.replyToSenderName,
      linkedTaskId: linkedTaskId ?? this.linkedTaskId,
      reactions: reactions ?? this.reactions,
      createdAt: createdAt ?? this.createdAt,
      editedAt: editedAt ?? this.editedAt,
      deliveryState: deliveryState ?? this.deliveryState,
      isPinned: isPinned ?? this.isPinned,
      isStarred: isStarred ?? this.isStarred,
      isDeletedForEveryone: isDeletedForEveryone ?? this.isDeletedForEveryone,
      isDeletedForMe: isDeletedForMe ?? this.isDeletedForMe,
    );
  }
}
