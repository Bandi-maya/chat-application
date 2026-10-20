/// State of an individual animated emoji instance.
enum ChatyEmojiPlaybackState {
  notYetShown,
  eligibleToAnimate,
  playing,
  completed,
  availableForReplay,
  unsupported,
}

/// Lifecycle-aware playback manager for animated emojis.
/// Ensures newly sent and incoming messages automatically play their animation once,
/// while preventing animation storms on scroll or list rebuilds.
class ChatyEmojiPlaybackManager {
  ChatyEmojiPlaybackManager._();
  static final ChatyEmojiPlaybackManager instance = ChatyEmojiPlaybackManager._();

  /// Set of message emoji instance keys that have already played their automatic animation.
  final Set<String> _playedInstanceKeys = <String>{};

  /// Set of instance keys currently actively animating their first play.
  final Set<String> _activelyPlayingKeys = <String>{};

  /// Key generation helper combining message ID and emoji index.
  String _makeKey(String messageId, int emojiIndex) => '${messageId}_$emojiIndex';

  /// Evaluates whether an emoji is eligible for automatic first-play.
  bool shouldAutoPlay(String messageId, int emojiIndex) {
    if (messageId.isEmpty) return true;
    final key = _makeKey(messageId, emojiIndex);
    return !_playedInstanceKeys.contains(key);
  }

  /// Called when an automatic animation starts.
  void notifyAnimationStarted(String messageId, int emojiIndex) {
    if (messageId.isEmpty) return;
    final key = _makeKey(messageId, emojiIndex);
    _activelyPlayingKeys.add(key);
  }

  /// Called when the initial animation finishes.
  void notifyAnimationCompleted(String messageId, int emojiIndex) {
    if (messageId.isEmpty) return;
    final key = _makeKey(messageId, emojiIndex);
    _activelyPlayingKeys.remove(key);
    _playedInstanceKeys.add(key);
  }

  /// Returns whether this emoji instance has already completed its auto-animation.
  bool isCompleted(String messageId, int emojiIndex) {
    if (messageId.isEmpty) return false;
    final key = _makeKey(messageId, emojiIndex);
    return _playedInstanceKeys.contains(key);
  }

  /// Resets state for a message if retried.
  void resetMessage(String messageId) {
    _playedInstanceKeys.removeWhere((k) => k.startsWith('${messageId}_'));
    _activelyPlayingKeys.removeWhere((k) => k.startsWith('${messageId}_'));
  }

  /// Clear all playback records (e.g. on user logout / full cache reset).
  void clearAll() {
    _playedInstanceKeys.clear();
    _activelyPlayingKeys.clear();
  }
}
