import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import '../../../core/audio/audio_policy_coordinator.dart';
import '../../../core/audio/audio_types.dart';
import '../../../injection/locator.dart';
import '../../island/controllers/dynamic_island_controller.dart';
import '../../island/models/dynamic_island_payload.dart';
import '../models/media_file_descriptor.dart';
import '../models/media_playback_state.dart';

class MediaPlayerCoordinator extends ChangeNotifier {
  final AudioPlayer _audioPlayer;
  final AudioPolicyCoordinator? _policyCoordinator;
  final DynamicIslandController? _islandController;

  MediaPlaybackState _state = const MediaPlaybackState();
  StreamSubscription<Duration>? _posSub;
  StreamSubscription<Duration?>? _durSub;
  StreamSubscription<PlayerState>? _stateSub;
  StreamSubscription<Duration>? _bufSub;
  bool _shouldResumeAfterInterruption = false;
  int _lastSyncedSec = -1;

  MediaPlayerCoordinator({
    AudioPlayer? audioPlayer,
    this._policyCoordinator,
    this._islandController,
  })  : _audioPlayer = audioPlayer ?? AudioPlayer() {
    _initListeners();
  }

  MediaPlaybackState get state => _state;

  void _initListeners() {
    _policyCoordinator?.addListener(_onPolicyCoordinatorChanged);

    _posSub = _audioPlayer.positionStream.listen((pos) {
      _state = _state.copyWith(position: pos);
      if (pos.inSeconds != _lastSyncedSec) {
        _lastSyncedSec = pos.inSeconds;
        _syncIslandPayload();
      }
      notifyListeners();
    });

    _durSub = _audioPlayer.durationStream.listen((dur) {
      if (dur != null) {
        _state = _state.copyWith(duration: dur);
        _syncIslandPayload();
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
      _syncIslandPayload();
      notifyListeners();
    });
  }

  void _onPolicyCoordinatorChanged() {
    final coordinator = _policyCoordinator;
    if (coordinator == null) return;

    if (coordinator.state == AudioSessionState.interrupted ||
        coordinator.activeOwner == AudioUseCase.communication) {
      if (_state.status == PlaybackStatus.playing) {
        _shouldResumeAfterInterruption = true;
        pause();
      }
    } else if (coordinator.state == AudioSessionState.recovering ||
        coordinator.activeOwner == null) {
      if (_shouldResumeAfterInterruption) {
        _shouldResumeAfterInterruption = false;
        resume();
      }
    }
  }

  void _syncIslandPayload() {
    final islandCtrl = _islandController ??
        (locator.isRegistered<DynamicIslandController>()
            ? locator<DynamicIslandController>()
            : null);
    if (islandCtrl == null) return;

    if (_state.status == PlaybackStatus.playing || _state.status == PlaybackStatus.paused) {
      final active = _state.activeItem;
      if (active != null) {
        final durSec = _state.duration.inSeconds;
        final durMs = _state.duration.inMilliseconds;
        final posMs = _state.position.inMilliseconds;
        final progress = durMs > 0 ? (posMs / durMs).clamp(0.0, 1.0) : 0.0;

        islandCtrl.postPayload(
          DynamicIslandPayload(
            id: 'media_active',
            type: IslandPayloadType.media,
            title: active.metadata['title'] as String? ?? active.name,
            mediaArtist: active.metadata['artist'] as String? ?? 'Audio Track',
            durationSeconds: durSec,
            progress: progress,
            isPlaying: _state.status == PlaybackStatus.playing,
            timestamp: DateTime.now(),
            onTogglePlay: () => togglePlayPause(),
            onSeek: (p) {
              final targetMs = (p * durMs).round();
              seek(Duration(milliseconds: targetMs));
            },
          ),
          autoExpand: false,
        );
      }
    } else if (_state.status == PlaybackStatus.stopped || _state.status == PlaybackStatus.idle) {
      islandCtrl.removePayload('media_active');
    }
  }

  Future<void> playTrack(MediaFileDescriptor item, {List<MediaFileDescriptor>? queue}) async {
    try {
      // 1. Check with policy coordinator before playing
      final coordinator = _policyCoordinator;
      if (coordinator != null) {
        final acquired = await coordinator.startMediaSession();
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
    _syncIslandPayload();
    final coordinator = _policyCoordinator;
    if (coordinator != null) {
      await coordinator.stopMediaSession();
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _policyCoordinator?.removeListener(_onPolicyCoordinatorChanged);
    final islandCtrl = _islandController ??
        (locator.isRegistered<DynamicIslandController>()
            ? locator<DynamicIslandController>()
            : null);
    islandCtrl?.removePayload('media_active');
    _posSub?.cancel();
    _durSub?.cancel();
    _stateSub?.cancel();
    _bufSub?.cancel();
    _audioPlayer.dispose();
    super.dispose();
  }
}
