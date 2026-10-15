import 'package:flutter/material.dart';

/// Visual display modes for the Dynamic Island matching the 4-state machine.
enum IslandMode {
  idle,
  compact,
  split,
  expanded,
}

/// Payload categorization determining visual priority and layout variants.
enum IslandPayloadType {
  incomingCall,
  ongoingCall,
  chatMessage,
  voiceNotePlayback,
  media,
  systemAlert,
  contactOnline,
  ghostModeStatus,
}

/// Action item displayed within the expanded Island view.
class DynamicIslandAction {
  final String id;
  final String title;
  final IconData? icon;
  final Color? color;
  final bool isDestructive;
  final VoidCallback onTap;

  const DynamicIslandAction({
    required this.id,
    required this.title,
    this.icon,
    this.color,
    this.isDestructive = false,
    required this.onTap,
  });
}

/// Data model representing a Dynamic Island notification or activity.
///
/// Ports the reference repository's Notification and Track models while fully
/// integrating Chaty domain events (chat messages, E2EE calls, voice notes, and status alerts).
class DynamicIslandPayload {
  final String id;
  final IslandPayloadType type;
  final String title;
  final String message;
  final DateTime timestamp;
  final String? iconEmoji;
  final IconData? iconData;
  final String? avatarUrl;
  final String? senderInitials;
  final Color accentColor;
  final bool isVerified;
  final bool isE2ee;

  // Media & Call properties
  final bool isPlaying;
  final int durationSeconds;
  final double progress; // 0.0 to 1.0
  final bool isVideo;
  final bool isMuted;
  final bool isSpeaker;
  final String? mediaArtist;
  final String? mediaAlbum;
  final String? audioSpeed;

  // Interactive capabilities
  final bool canQuickReply;
  final List<DynamicIslandAction> actions;
  final void Function(String replyText)? onQuickReply;
  final VoidCallback? onMarkAsRead;
  final VoidCallback? onAcceptCall;
  final VoidCallback? onDeclineCall;
  final VoidCallback? onTogglePlay;
  final void Function(double progress)? onSeek;
  final VoidCallback? onTap;
  final VoidCallback? onDismiss;

  const DynamicIslandPayload({
    required this.id,
    required this.type,
    required this.title,
    this.message = '',
    required this.timestamp,
    this.iconEmoji,
    this.iconData,
    this.avatarUrl,
    this.senderInitials,
    this.accentColor = const Color(0xFF3B82F6),
    this.isVerified = false,
    this.isE2ee = true,
    this.isPlaying = false,
    this.durationSeconds = 0,
    this.progress = 0.0,
    this.isVideo = false,
    this.isMuted = false,
    this.isSpeaker = false,
    this.mediaArtist,
    this.mediaAlbum,
    this.audioSpeed = '1.0x',
    this.canQuickReply = false,
    this.actions = const [],
    this.onQuickReply,
    this.onMarkAsRead,
    this.onAcceptCall,
    this.onDeclineCall,
    this.onTogglePlay,
    this.onSeek,
    this.onTap,
    this.onDismiss,
  });

  /// Numeric priority resolution (lower value = higher priority).
  ///
  /// Priority hierarchy:
  /// 1: Calls (incomingCall / ongoingCall)
  /// 2: Active Media / Voice playback
  /// 3: Chat messages
  /// 4: System alerts, ghost mode, presence
  int get priority {
    switch (type) {
      case IslandPayloadType.incomingCall:
        return 0;
      case IslandPayloadType.ongoingCall:
        return 1;
      case IslandPayloadType.voiceNotePlayback:
      case IslandPayloadType.media:
        return 2;
      case IslandPayloadType.chatMessage:
        return 3;
      case IslandPayloadType.ghostModeStatus:
      case IslandPayloadType.systemAlert:
      case IslandPayloadType.contactOnline:
        return 4;
    }
  }

  /// Whether this payload represents an ongoing activity that should remain active
  /// in compact mode rather than automatically dismissing completely.
  bool get isOngoingActivity {
    return type == IslandPayloadType.ongoingCall ||
        type == IslandPayloadType.incomingCall ||
        (type == IslandPayloadType.media && isPlaying) ||
        (type == IslandPayloadType.voiceNotePlayback && isPlaying);
  }

  DynamicIslandPayload copyWith({
    String? id,
    IslandPayloadType? type,
    String? title,
    String? message,
    DateTime? timestamp,
    String? iconEmoji,
    IconData? iconData,
    String? avatarUrl,
    String? senderInitials,
    Color? accentColor,
    bool? isVerified,
    bool? isE2ee,
    bool? isPlaying,
    int? durationSeconds,
    double? progress,
    bool? isVideo,
    bool? isMuted,
    bool? isSpeaker,
    String? mediaArtist,
    String? mediaAlbum,
    String? audioSpeed,
    bool? canQuickReply,
    List<DynamicIslandAction>? actions,
    void Function(String replyText)? onQuickReply,
    VoidCallback? onMarkAsRead,
    VoidCallback? onAcceptCall,
    VoidCallback? onDeclineCall,
    VoidCallback? onTogglePlay,
    void Function(double progress)? onSeek,
    VoidCallback? onTap,
    VoidCallback? onDismiss,
  }) {
    return DynamicIslandPayload(
      id: id ?? this.id,
      type: type ?? this.type,
      title: title ?? this.title,
      message: message ?? this.message,
      timestamp: timestamp ?? this.timestamp,
      iconEmoji: iconEmoji ?? this.iconEmoji,
      iconData: iconData ?? this.iconData,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      senderInitials: senderInitials ?? this.senderInitials,
      accentColor: accentColor ?? this.accentColor,
      isVerified: isVerified ?? this.isVerified,
      isE2ee: isE2ee ?? this.isE2ee,
      isPlaying: isPlaying ?? this.isPlaying,
      durationSeconds: durationSeconds ?? this.durationSeconds,
      progress: progress ?? this.progress,
      isVideo: isVideo ?? this.isVideo,
      isMuted: isMuted ?? this.isMuted,
      isSpeaker: isSpeaker ?? this.isSpeaker,
      mediaArtist: mediaArtist ?? this.mediaArtist,
      mediaAlbum: mediaAlbum ?? this.mediaAlbum,
      audioSpeed: audioSpeed ?? this.audioSpeed,
      canQuickReply: canQuickReply ?? this.canQuickReply,
      actions: actions ?? this.actions,
      onQuickReply: onQuickReply ?? this.onQuickReply,
      onMarkAsRead: onMarkAsRead ?? this.onMarkAsRead,
      onAcceptCall: onAcceptCall ?? this.onAcceptCall,
      onDeclineCall: onDeclineCall ?? this.onDeclineCall,
      onTogglePlay: onTogglePlay ?? this.onTogglePlay,
      onSeek: onSeek ?? this.onSeek,
      onTap: onTap ?? this.onTap,
      onDismiss: onDismiss ?? this.onDismiss,
    );
  }
}
