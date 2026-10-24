enum ConversationType { direct, group }

enum EncryptionStatus { encrypted, verificationNeeded, demoMode }

class Conversation {
  final String id;
  final ConversationType type;
  final String title;
  final List<String> participantIds;
  final List<String> adminIds;
  final String? avatarInitials;
  final String? avatarColorHex;
  final String lastMessageText;
  final DateTime lastMessageTime;
  final String lastMessageSenderId;
  final int unreadCount;
  final bool isPinned;
  final bool isArchived;
  final bool isMuted;
  final String? wallpaperId;
  final Duration? disappearingDuration;
  final EncryptionStatus encryptionStatus;
  final String draftText;
  final String? roomType;
  final String? taskId;

  bool get isTask => roomType == 'task' || (taskId != null && taskId!.isNotEmpty);
  bool get isGroup => type == ConversationType.group;
  bool get isDirect => type == ConversationType.direct;

  const Conversation({
    required this.id,
    required this.type,
    required this.title,
    required this.participantIds,
    this.adminIds = const [],
    this.avatarInitials,
    this.avatarColorHex,
    required this.lastMessageText,
    required this.lastMessageTime,
    required this.lastMessageSenderId,
    this.unreadCount = 0,
    this.isPinned = false,
    this.isArchived = false,
    this.isMuted = false,
    this.wallpaperId,
    this.disappearingDuration,
    this.roomType,
    this.taskId,
    // Chaty must not claim E2EE until audited device-to-device encryption is
    // actually implemented and verified. The option remains in the UI.
    this.encryptionStatus = EncryptionStatus.verificationNeeded,
    this.draftText = '',
  });

  Conversation copyWith({
    String? id,
    ConversationType? type,
    String? title,
    List<String>? participantIds,
    List<String>? adminIds,
    String? avatarInitials,
    String? avatarColorHex,
    String? lastMessageText,
    DateTime? lastMessageTime,
    String? lastMessageSenderId,
    int? unreadCount,
    bool? isPinned,
    bool? isArchived,
    bool? isMuted,
    String? wallpaperId,
    Duration? disappearingDuration,
    EncryptionStatus? encryptionStatus,
    String? draftText,
    String? roomType,
    String? taskId,
  }) {
    return Conversation(
      id: id ?? this.id,
      type: type ?? this.type,
      title: title ?? this.title,
      participantIds: participantIds ?? this.participantIds,
      adminIds: adminIds ?? this.adminIds,
      avatarInitials: avatarInitials ?? this.avatarInitials,
      avatarColorHex: avatarColorHex ?? this.avatarColorHex,
      lastMessageText: lastMessageText ?? this.lastMessageText,
      lastMessageTime: lastMessageTime ?? this.lastMessageTime,
      lastMessageSenderId: lastMessageSenderId ?? this.lastMessageSenderId,
      unreadCount: unreadCount ?? this.unreadCount,
      isPinned: isPinned ?? this.isPinned,
      isArchived: isArchived ?? this.isArchived,
      isMuted: isMuted ?? this.isMuted,
      wallpaperId: wallpaperId ?? this.wallpaperId,
      disappearingDuration: disappearingDuration ?? this.disappearingDuration,
      roomType: roomType ?? this.roomType,
      taskId: taskId ?? this.taskId,
      encryptionStatus: encryptionStatus ?? this.encryptionStatus,
      draftText: draftText ?? this.draftText,
    );
  }

  factory Conversation.fromApi(Map<String, dynamic> json) {
    String lastText = '';
    DateTime lastTime = json['updated_at'] != null
        ? DateTime.tryParse(json['updated_at'].toString()) ?? DateTime.now()
        : DateTime.now();
    String lastSender = '';

    final lastMsg = json['last_message'];
    if (lastMsg is Map<String, dynamic>) {
      final raw = lastMsg['content']?.toString() ?? '';
      if (raw.startsWith('{') && raw.contains('"image"')) {
        lastText = '📷 Photo';
      } else if (raw.startsWith('{') && raw.contains('"type"')) {
        lastText = '📎 Attachment';
      } else if (raw.startsWith('http') && (raw.endsWith('.jpg') || raw.endsWith('.png') || raw.endsWith('.jpeg') || raw.endsWith('.webp'))) {
        lastText = '📷 Photo';
      } else {
        lastText = raw;
      }
      if (lastMsg['created_at'] != null) {
        lastTime = DateTime.tryParse(lastMsg['created_at'].toString()) ?? lastTime;
      }
      lastSender = lastMsg['sender_name']?.toString() ?? '';
    }

    final otherUser = json['other_user'];
    final participantIds = <String>[];
    if (json['created_by_id'] != null) {
      participantIds.add(json['created_by_id'].toString());
    }
    if (otherUser is Map && otherUser['id'] != null) {
      final otherId = otherUser['id'].toString();
      if (!participantIds.contains(otherId)) {
        participantIds.add(otherId);
      }
    }

    final rawTitle = json['display_name']?.toString() ?? json['name']?.toString() ?? 'Chat';
    final effectiveTitle = rawTitle.trim().isNotEmpty ? rawTitle.trim() : 'Chat';
    final compact = effectiveTitle.replaceAll(RegExp(r'[^A-Za-z0-9]'), '');
    final initials = compact.isEmpty
        ? 'C'
        : compact.substring(0, compact.length.clamp(1, 2)).toUpperCase();

    final rType = json['room_type']?.toString();
    final tId = json['task_id']?.toString();

    return Conversation(
      id: json['uid']?.toString() ?? json['id']?.toString() ?? '',
      type: rType == 'group' ? ConversationType.group : ConversationType.direct,
      title: effectiveTitle,
      participantIds: participantIds,
      avatarInitials: initials,
      avatarColorHex: '0xFF6366F1',
      lastMessageText: lastText,
      lastMessageTime: lastTime,
      lastMessageSenderId: lastSender,
      roomType: rType,
      taskId: tId,
    );
  }
}

