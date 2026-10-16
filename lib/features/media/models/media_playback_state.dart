import 'media_file_descriptor.dart';

enum PlaybackStatus {
  idle,
  loading,
  playing,
  paused,
  stopped,
  error,
}

enum ChatyRepeatMode {
  off,
  one,
  all,
}

class MediaPlaybackState {
  final PlaybackStatus status;
  final Duration position;
  final Duration duration;
  final Duration bufferedPosition;
  final double speed;
  final bool isShuffle;
  final ChatyRepeatMode repeatMode;
  final MediaFileDescriptor? activeItem;
  final List<MediaFileDescriptor> queue;
  final int queueIndex;
  final String? errorMessage;

  const MediaPlaybackState({
    this.status = PlaybackStatus.idle,
    this.position = Duration.zero,
    this.duration = Duration.zero,
    this.bufferedPosition = Duration.zero,
    this.speed = 1.0,
    this.isShuffle = false,
    this.repeatMode = ChatyRepeatMode.off,
    this.activeItem,
    this.queue = const <MediaFileDescriptor>[],
    this.queueIndex = 0,
    this.errorMessage,
  });

  bool get isPlaying => status == PlaybackStatus.playing;
  bool get hasActiveItem => activeItem != null;
  bool get hasNext => queue.isNotEmpty && queueIndex < queue.length - 1;
  bool get hasPrevious => queue.isNotEmpty && queueIndex > 0;

  double get progress {
    if (duration.inMilliseconds == 0) return 0.0;
    return (position.inMilliseconds / duration.inMilliseconds).clamp(0.0, 1.0);
  }

  MediaPlaybackState copyWith({
    PlaybackStatus? status,
    Duration? position,
    Duration? duration,
    Duration? bufferedPosition,
    double? speed,
    bool? isShuffle,
    ChatyRepeatMode? repeatMode,
    MediaFileDescriptor? activeItem,
    List<MediaFileDescriptor>? queue,
    int? queueIndex,
    String? errorMessage,
  }) {
    return MediaPlaybackState(
      status: status ?? this.status,
      position: position ?? this.position,
      duration: duration ?? this.duration,
      bufferedPosition: bufferedPosition ?? this.bufferedPosition,
      speed: speed ?? this.speed,
      isShuffle: isShuffle ?? this.isShuffle,
      repeatMode: repeatMode ?? this.repeatMode,
      activeItem: activeItem ?? this.activeItem,
      queue: queue ?? this.queue,
      queueIndex: queueIndex ?? this.queueIndex,
      errorMessage: errorMessage ?? this.errorMessage,
    );
  }
}
