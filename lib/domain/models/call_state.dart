/// Explicit typed states for the WebRTC & Signaling state machine.
enum CallSessionState {
  idle,
  initiating,
  ringing,
  incoming,
  connecting,
  connected,
  reconnecting,
  declined,
  busy,
  missed,
  ended,
  failed,
}

extension CallSessionStatePolicy on CallSessionState {
  /// Terminal states cannot transition back into a live media session.
  bool get isTerminal =>
      this == CallSessionState.idle ||
      this == CallSessionState.declined ||
      this == CallSessionState.busy ||
      this == CallSessionState.missed ||
      this == CallSessionState.ended ||
      this == CallSessionState.failed;
}

/// Available audio routing outputs.
enum AudioRouteType {
  earpiece,
  speaker,
  wiredHeadset,
  bluetooth,
}

/// Information model representing an active or recent call session.
class ChatyCallSession {
  final String callId;
  final String remoteUserId;
  final String remoteDisplayName;
  final String? remoteAvatarInitials;
  final String? remoteAvatarColorHex;
  final bool isVideo;
  final bool isOutgoing;
  final CallSessionState state;
  final DateTime startedAt;
  final DateTime? connectedAt;
  final DateTime? endedAt;
  final AudioRouteType audioRoute;
  final bool isMuted;
  final bool isCameraOff;
  final bool isFrontCamera;
  final bool isSharingScreen;

  const ChatyCallSession({
    required this.callId,
    required this.remoteUserId,
    required this.remoteDisplayName,
    this.remoteAvatarInitials,
    this.remoteAvatarColorHex,
    required this.isVideo,
    required this.isOutgoing,
    required this.state,
    required this.startedAt,
    this.connectedAt,
    this.endedAt,
    this.audioRoute = AudioRouteType.speaker,
    this.isMuted = false,
    this.isCameraOff = false,
    this.isFrontCamera = true,
    this.isSharingScreen = false,
  });

  ChatyCallSession copyWith({
    CallSessionState? state,
    DateTime? connectedAt,
    DateTime? endedAt,
    AudioRouteType? audioRoute,
    bool? isMuted,
    bool? isCameraOff,
    bool? isFrontCamera,
    bool? isSharingScreen,
  }) {
    return ChatyCallSession(
      callId: callId,
      remoteUserId: remoteUserId,
      remoteDisplayName: remoteDisplayName,
      remoteAvatarInitials: remoteAvatarInitials,
      remoteAvatarColorHex: remoteAvatarColorHex,
      isVideo: isVideo,
      isOutgoing: isOutgoing,
      state: state ?? this.state,
      startedAt: startedAt,
      connectedAt: connectedAt ?? this.connectedAt,
      endedAt: endedAt ?? this.endedAt,
      audioRoute: audioRoute ?? this.audioRoute,
      isMuted: isMuted ?? this.isMuted,
      isCameraOff: isCameraOff ?? this.isCameraOff,
      isFrontCamera: isFrontCamera ?? this.isFrontCamera,
      isSharingScreen: isSharingScreen ?? this.isSharingScreen,
    );
  }

  /// Indicates whether the session is currently active or establishing media.
  bool get isActive =>
      state == CallSessionState.connecting ||
      state == CallSessionState.connected ||
      state == CallSessionState.reconnecting ||
      state == CallSessionState.initiating ||
      (state == CallSessionState.ringing && isOutgoing);
}
