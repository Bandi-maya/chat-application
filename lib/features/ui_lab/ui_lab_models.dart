enum UiLabScenario {
  loading,
  empty,
  normal,
  dense,
  error,
  offline,
  permissionDenied,
  uploading,
  failed,
  selected,
  locked,
}

class UiLabConversationFixture {
  const UiLabConversationFixture({
    required this.id,
    required this.title,
    required this.preview,
    required this.timeLabel,
    required this.unreadCount,
    required this.isPinned,
    required this.isMuted,
    required this.isOnline,
  });

  final String id;
  final String title;
  final String preview;
  final String timeLabel;
  final int unreadCount;
  final bool isPinned;
  final bool isMuted;
  final bool isOnline;
}

class UiLabMessageFixture {
  const UiLabMessageFixture({
    required this.id,
    required this.text,
    required this.isMine,
    required this.timeLabel,
    required this.delivery,
    this.replyTo,
    this.reaction,
  });

  final String id;
  final String text;
  final bool isMine;
  final String timeLabel;
  final String delivery;
  final String? replyTo;
  final String? reaction;
}
