import 'dart:convert';

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
  final bool isRevokedBySender;

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
    this.isRevokedBySender = false,
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
    bool? isRevokedBySender,
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
      isRevokedBySender: isRevokedBySender ?? this.isRevokedBySender,
    );
  }

  factory ChatMessage.fromApi(Map<String, dynamic> json) {
    final senderName = json['sender_name']?.toString() ?? '';
    final rawContent = json['content']?.toString() ?? '';
    MessageType parsedType = MessageType.text;
    MessageAttachment? parsedAttachment;
    String effectiveText = rawContent;

    if (rawContent.startsWith('{') && rawContent.endsWith('}')) {
      try {
        final decoded = jsonDecode(rawContent);
        if (decoded is Map<String, dynamic> && decoded.containsKey('url')) {
          final t = decoded['type']?.toString();
          parsedType = messageTypeFromDatabase(t);
          effectiveText = decoded['text']?.toString() ?? '';
          parsedAttachment = MessageAttachment(
            id: json['uid']?.toString() ?? json['id']?.toString() ?? '',
            type: t ?? 'image',
            name: decoded['name']?.toString() ?? 'attachment',
            size: decoded['size']?.toString() ?? '',
            url: decoded['url']?.toString(),
          );
        }
      } catch (_) {}
    } else if (rawContent.startsWith('http') || rawContent.startsWith('/static/uploads/')) {
      final lower = rawContent.toLowerCase();
      if (lower.endsWith('.jpg') ||
          lower.endsWith('.jpeg') ||
          lower.endsWith('.png') ||
          lower.endsWith('.webp') ||
          lower.endsWith('.gif')) {
        parsedType = MessageType.image;
        parsedAttachment = MessageAttachment(
          id: json['uid']?.toString() ?? json['id']?.toString() ?? '',
          type: 'image',
          name: 'Photo',
          size: '',
          url: rawContent,
        );
        effectiveText = '';
      }
    }

    return ChatMessage(
      id: json['uid']?.toString() ?? json['id']?.toString() ?? '',
      conversationId: json['room_id']?.toString() ?? '',
      senderId: json['sender_id']?.toString() ?? '',
      type: parsedType,
      text: effectiveText,
      attachment: parsedAttachment,
      createdAt: json['created_at'] != null
          ? DateTime.tryParse(json['created_at'].toString()) ?? DateTime.now()
          : DateTime.now(),
      deliveryState: DeliveryState.delivered,
      metadata: senderName.isNotEmpty
          ? <String, dynamic>{'sender_name': senderName}
          : const <String, dynamic>{},
    );
  }
}
