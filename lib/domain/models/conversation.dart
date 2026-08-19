enum ConversationType {
  direct,
  group,
}

enum EncryptionStatus {
  encrypted,
  verificationNeeded,
  demoMode,
}

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
      encryptionStatus: encryptionStatus ?? this.encryptionStatus,
      draftText: draftText ?? this.draftText,
    );
  }
}
