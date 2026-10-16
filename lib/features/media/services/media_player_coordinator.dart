import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import '../../../core/audio/audio_policy_coordinator.dart';
import '../../../core/audio/audio_types.dart';
import '../models/media_file_descriptor.dart';
import '../models/media_playback_state.dart';

class MediaPlayerCoordinator extends ChangeNotifier {
  final AudioPlayer _audioPlayer;
  final AudioPolicyCoordinator? _policyCoordinator;

  MediaPlaybackState _state = const MediaPlaybackState();
  StreamSubscription<Duration>? _posSub;
  StreamSubscription<Duration?>? _durSub;
  StreamSubscription<PlayerState>? _stateSub;
  StreamSubscription<Duration>? _bufSub;

  MediaPlayerCoordinator({
    AudioPlayer? audioPlayer,
    AudioPolicyCoordinator? policyCoordinator,
  }) : _audioPlayer = audioPlayer ?? AudioPlayer(),
       _policyCoordinator = policyCoordinator {
    _initListeners();
  }

  MediaPlaybackState get state => _state;

  void _initListeners() {
    _posSub = _audioPlayer.positionStream.listen((pos) {
      _state = _state.copyWith(position: pos);
      notifyListeners();
    });

    _durSub = _audioPlayer.durationStream.listen((dur) {
      if (dur != null) {
        _state = _state.copyWith(duration: dur);
        notifyListeners();
      }
    });

    _bufSub = _audioPlayer.bufferedPositionStream.listen((buf) {
      _state = _state.copyWith(bufferedPosition: buf);
      notifyListeners();
    });

    _stateSub = _audioPlayer.playerStateStream.listen((ps) {
      PlaybackStatus status = PlaybackStatus.idle;
      if (ps.processingState == ProcessingState.loading || ps.processingState == ProcessingState.buffering) {
        status = PlaybackStatus.loading;
      } else if (ps.playing) {
        status = PlaybackStatus.playing;
      } else if (ps.processingState == ProcessingState.completed) {
        status = PlaybackStatus.stopped;
        _onTrackCompleted();
      } else {
        status = PlaybackStatus.paused;
      }
      _state = _state.copyWith(status: status);
      notifyListeners();
    });
  }

  Future<void> playTrack(MediaFileDescriptor item, {List<MediaFileDescriptor>? queue}) async {
    try {
      // 1. Check with policy coordinator before playing
      if (_policyCoordinator != null) {
        final acquired = await _policyCoordinator!.startMediaSession();
        if (!acquired) {
          _state = _state.copyWith(
            status: PlaybackStatus.error,
            errorMessage: 'Cannot play audio while an active call owns the session.',
          );
          notifyListeners();
          return;
        }
      }

      final newQueue = queue ?? <MediaFileDescriptor>[item];
      final idx = newQueue.indexWhere((q) => q.path == item.path);

      _state = _state.copyWith(
        activeItem: item,
        queue: newQueue,
        queueIndex: idx != -1 ? idx : 0,
        status: PlaybackStatus.loading,
        errorMessage: null,
      );
      notifyListeners();

      await _audioPlayer.stop();
      await _audioPlayer.setFilePath(item.path);
      await _audioPlayer.setSpeed(_state.speed);
      await _audioPlayer.play();
    } catch (e) {
      _state = _state.copyWith(
        status: PlaybackStatus.error,
        errorMessage: 'Playback error: $e',
      );
      notifyListeners();
    }
  }

  Future<void> togglePlayPause() async {
    if (_state.status == PlaybackStatus.playing) {
      await pause();
    } else {
      await resume();
    }
  }

  Future<void> pause() async {
    await _audioPlayer.pause();
    _state = _state.copyWith(status: PlaybackStatus.paused);
    notifyListeners();
  }

  Future<void> resume() async {
    if (_state.activeItem != null) {
      await _audioPlayer.play();
      _state = _state.copyWith(status: PlaybackStatus.playing);
      notifyListeners();
    }
  }

  Future<void> seek(Duration position) async {
    await _audioPlayer.seek(position);
  }

  Future<void> setSpeed(double speed) async {
    _state = _state.copyWith(speed: speed);
    await _audioPlayer.setSpeed(speed);
    notifyListeners();
  }

  void toggleShuffle() {
    _state = _state.copyWith(isShuffle: !_state.isShuffle);
    notifyListeners();
  }

  void cycleRepeatMode() {
    final next = switch (_state.repeatMode) {
      ChatyRepeatMode.off => ChatyRepeatMode.all,
      ChatyRepeatMode.all => ChatyRepeatMode.one,
      ChatyRepeatMode.one => ChatyRepeatMode.off,
    };
    _state = _state.copyWith(repeatMode: next);
    notifyListeners();
  }

  Future<void> skipNext() async {
    if (_state.hasNext) {
      final nextItem = _state.queue[_state.queueIndex + 1];
      await playTrack(nextItem, queue: _state.queue);
    }
  }

  Future<void> skipPrevious() async {
    if (_state.hasPrevious) {
      final prevItem = _state.queue[_state.queueIndex - 1];
      await playTrack(prevItem, queue: _state.queue);
    }
  }

  void cycleChatyRepeatMode() {
    final nextMode = switch (_state.repeatMode) {
      ChatyRepeatMode.off => ChatyRepeatMode.all,
      ChatyRepeatMode.all => ChatyRepeatMode.one,
      ChatyRepeatMode.one => ChatyRepeatMode.off,
    };
    _state = _state.copyWith(repeatMode: nextMode);
    notifyListeners();
  }

  void _onTrackCompleted() {
    if (_state.repeatMode == ChatyRepeatMode.one && _state.activeItem != null) {
      playTrack(_state.activeItem!, queue: _state.queue);
    } else if (_state.hasNext) {
      skipNext();
    } else if (_state.repeatMode == ChatyRepeatMode.all && _state.queue.isNotEmpty) {
      playTrack(_state.queue.first, queue: _state.queue);
    }
  }

  Future<void> stop() async {
    await _audioPlayer.stop();
    _state = _state.copyWith(
      status: PlaybackStatus.stopped,
      position: Duration.zero,
    );
    if (_policyCoordinator != null) {
      await _policyCoordinator!.stopMediaSession();
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _posSub?.cancel();
    _durSub?.cancel();
    _stateSub?.cancel();
    _bufSub?.cancel();
    _audioPlayer.dispose();
    super.dispose();
  }
}
