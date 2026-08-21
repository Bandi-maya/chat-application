enum CallType { voice, video }

enum CallDirection { incoming, outgoing, missed }

class CallRecord {
  final String id;
  final String callerId;
  final List<String> participantIds;
  final CallType type;
  final CallDirection direction;
  final DateTime timestamp;
  final int durationSeconds;
  final bool isEncrypted;

  const CallRecord({
    required this.id,
    required this.callerId,
    required this.participantIds,
    required this.type,
    required this.direction,
    required this.timestamp,
    required this.durationSeconds,
    this.isEncrypted = true,
  });
}

class UpdateStory {
  final String id;
  final String userId;
  final String content;
  final String? mediaUrl;
  final String backgroundGradient;
  final DateTime timestamp;
  final bool isViewed;
  final bool isMuted;

  const UpdateStory({
    required this.id,
    required this.userId,
    required this.content,
    this.mediaUrl,
    this.backgroundGradient = 'indigo_purple',
    required this.timestamp,
    this.isViewed = false,
    this.isMuted = false,
  });

  UpdateStory copyWith({
    String? id,
    String? userId,
    String? content,
    String? mediaUrl,
    String? backgroundGradient,
    DateTime? timestamp,
    bool? isViewed,
    bool? isMuted,
  }) {
    return UpdateStory(
      id: id ?? this.id,
      userId: userId ?? this.userId,
      content: content ?? this.content,
      mediaUrl: mediaUrl ?? this.mediaUrl,
      backgroundGradient: backgroundGradient ?? this.backgroundGradient,
      timestamp: timestamp ?? this.timestamp,
      isViewed: isViewed ?? this.isViewed,
      isMuted: isMuted ?? this.isMuted,
    );
  }
}

class LinkedDevice {
  final String id;
  final String deviceName;
  final String
  platform; // 'Android 14', 'iOS 18', 'macOS Sonoma', 'Windows 11', 'Chrome Web'
  final String location;
  final DateTime lastActiveAt;
  final bool isCurrentDevice;

  const LinkedDevice({
    required this.id,
    required this.deviceName,
    required this.platform,
    required this.location,
    required this.lastActiveAt,
    this.isCurrentDevice = false,
  });
}
