import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';
import '../../../../ui/core/design_system/design_system.dart';
import '../icons/chaty_media_icons.dart';

class ChatyVideoPlayerWidget extends StatefulWidget {
  final String videoPath;
  final bool isNetworkUrl;
  final String? title;
  final bool autoPlay;

  const ChatyVideoPlayerWidget({
    super.key,
    required this.videoPath,
    this.isNetworkUrl = false,
    this.title,
    this.autoPlay = false,
  });

  @override
  State<ChatyVideoPlayerWidget> createState() => _ChatyVideoPlayerWidgetState();
}

class _ChatyVideoPlayerWidgetState extends State<ChatyVideoPlayerWidget> {
  VideoPlayerController? _controller;
  bool _isInitialized = false;
  bool _hasError = false;
  String _errorMessage = '';
  bool _areControlsVisible = true;
  Timer? _controlsDismissTimer;
  bool _isFullscreen = false;
  bool _isFillMode = false;
  double _currentSpeed = 1.0;
  String? _seekFeedbackText;
  Timer? _seekFeedbackTimer;

  @override
  void initState() {
    super.initState();
    _initializePlayer();
  }

  Future<void> _initializePlayer() async {
    try {
      if (widget.isNetworkUrl) {
        _controller = VideoPlayerController.networkUrl(Uri.parse(widget.videoPath));
      } else {
        _controller = VideoPlayerController.file(File(widget.videoPath));
      }

      await _controller!.initialize();
      _controller!.addListener(_onPlayerStateChanged);

      if (mounted) {
        setState(() {
          _isInitialized = true;
        });
        if (widget.autoPlay) {
          await _controller!.play();
        }
        _startControlsTimer();
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _hasError = true;
          _errorMessage = e.toString();
        });
      }
    }
  }

  void _onPlayerStateChanged() {
    if (mounted) setState(() {});
  }

  void _toggleControls() {
    setState(() {
      _areControlsVisible = !_areControlsVisible;
    });
    if (_areControlsVisible) {
      _startControlsTimer();
    } else {
      _controlsDismissTimer?.cancel();
    }
  }

  void _startControlsTimer() {
    _controlsDismissTimer?.cancel();
    _controlsDismissTimer = Timer(const Duration(milliseconds: 3500), () {
      if (mounted && _controller != null && _controller!.value.isPlaying) {
        setState(() {
          _areControlsVisible = false;
        });
      }
    });
  }

  void _togglePlayPause() {
    final c = _controller;
    if (c == null) return;
    HapticFeedback.lightImpact();
    if (c.value.isPlaying) {
      c.pause();
      setState(() => _areControlsVisible = true);
      _controlsDismissTimer?.cancel();
    } else {
      c.play();
      _startControlsTimer();
    }
  }

  void _seekRelative(int seconds) {
    final c = _controller;
    if (c == null) return;
    HapticFeedback.selectionClick();
    final newPos = c.value.position + Duration(seconds: seconds);
    final clamped = newPos.inMilliseconds.clamp(0, c.value.duration.inMilliseconds);
    c.seekTo(Duration(milliseconds: clamped));

    _showSeekFeedback(seconds > 0 ? '+${seconds}s' : '${seconds}s');
    _startControlsTimer();
  }

  void _showSeekFeedback(String text) {
    setState(() => _seekFeedbackText = text);
    _seekFeedbackTimer?.cancel();
    _seekFeedbackTimer = Timer(const Duration(milliseconds: 800), () {
      if (mounted) setState(() => _seekFeedbackText = null);
    });
  }

  Future<void> _toggleFullscreen() async {
    HapticFeedback.mediumImpact();
    final next = !_isFullscreen;
    setState(() => _isFullscreen = next);

    if (next) {
      await SystemChrome.setPreferredOrientations([
        DeviceOrientation.landscapeLeft,
        DeviceOrientation.landscapeRight,
      ]);
      await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    } else {
      await SystemChrome.setPreferredOrientations([
        DeviceOrientation.portraitUp,
      ]);
      await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    }
    _startControlsTimer();
  }

  void _setSpeed(double speed) {
    _controller?.setPlaybackSpeed(speed);
    setState(() => _currentSpeed = speed);
    _startControlsTimer();
  }

  @override
  void dispose() {
    _controlsDismissTimer?.cancel();
    _seekFeedbackTimer?.cancel();
    _controller?.removeListener(_onPlayerStateChanged);
    _controller?.dispose();

    if (_isFullscreen) {
      SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_hasError) {
      return Container(
        height: 220,
        decoration: BoxDecoration(
          color: Colors.black,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.error_outline_rounded, color: Colors.redAccent, size: 36),
                const SizedBox(height: 8),
                const Text('Cannot play this video format', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                const SizedBox(height: 4),
                Text(_errorMessage, style: const TextStyle(color: Colors.white54, fontSize: 11), textAlign: TextAlign.center, maxLines: 2),
              ],
            ),
          ),
        ),
      );
    }

    if (!_isInitialized || _controller == null) {
      return Container(
        height: 220,
        decoration: BoxDecoration(
          color: Colors.black,
          borderRadius: BorderRadius.circular(16),
        ),
        child: const Center(child: CircularProgressIndicator.adaptive()),
      );
    }

    final c = _controller!;
    final aspect = c.value.aspectRatio > 0 ? c.value.aspectRatio : 16 / 9;

    return AspectRatio(
      aspectRatio: _isFullscreen ? MediaQuery.of(context).size.aspectRatio : aspect,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(_isFullscreen ? 0 : 16),
        child: Container(
          color: Colors.black,
          child: Stack(
            fit: StackFit.expand,
            children: [
              // Video Surface
              Center(
                child: FittedBox(
                  fit: _isFillMode ? BoxFit.cover : BoxFit.contain,
                  child: SizedBox(
                    width: c.value.size.width,
                    height: c.value.size.height,
                    child: VideoPlayer(c),
                  ),
                ),
              ),

              // Gesture Areas for Double-Tap Seek and Tap Controls
              Row(
                children: [
                  Expanded(
                    child: GestureDetector(
                      behavior: HitTestBehavior.translucent,
                      onTap: _toggleControls,
                      onDoubleTap: () => _seekRelative(-10),
                    ),
                  ),
                  Expanded(
                    child: GestureDetector(
                      behavior: HitTestBehavior.translucent,
                      onTap: _toggleControls,
                      onDoubleTap: () => _seekRelative(10),
                    ),
                  ),
                ],
              ),

              // Seek Ripple Feedback Pill
              if (_seekFeedbackText != null)
                Center(
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.75),
                      borderRadius: BorderRadius.circular(24),
                      border: Border.all(color: Colors.white24),
                    ),
                    child: Text(
                      _seekFeedbackText!,
                      style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w800),
                    ),
                  ),
                ),

              // Buffering indicator
              if (c.value.isBuffering)
                const Center(
                  child: CircularProgressIndicator(color: Colors.white),
                ),

              // Controls Overlay
              AnimatedOpacity(
                opacity: _areControlsVisible ? 1.0 : 0.0,
                duration: const Duration(milliseconds: 240),
                child: IgnorePointer(
                  ignoring: !_areControlsVisible,
                  child: Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          Colors.black.withValues(alpha: 0.75),
                          Colors.transparent,
                          Colors.black.withValues(alpha: 0.85),
                        ],
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        stops: const [0.0, 0.5, 1.0],
                      ),
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        // Top Bar
                        Padding(
                          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                          child: Row(
                            children: [
                              if (widget.title != null)
                                Expanded(
                                  child: Text(
                                    widget.title!,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 14),
                                  ),
                                ),
                              const Spacer(),
                              // Aspect Ratio Toggle
                              IconButton(
                                icon: Icon(
                                  _isFillMode ? Icons.fit_screen_rounded : Icons.aspect_ratio_rounded,
                                  color: Colors.white,
                                  size: 20,
                                ),
                                tooltip: _isFillMode ? 'Fit inside' : 'Fill frame',
                                onPressed: () {
                                  setState(() => _isFillMode = !_isFillMode);
                                  _startControlsTimer();
                                },
                              ),
                              // Speed selector
                              PopupMenuButton<double>(
                                initialValue: _currentSpeed,
                                tooltip: 'Playback Speed',
                                icon: const Icon(Icons.speed_rounded, color: Colors.white, size: 20),
                                onSelected: _setSpeed,
                                itemBuilder: (_) => [0.5, 0.75, 1.0, 1.25, 1.5, 2.0]
                                    .map((s) => PopupMenuItem(value: s, child: Text('${s}x')))
                                    .toList(),
                              ),
                            ],
                          ),
                        ),

                        // Center Big Play/Pause
                        Center(
                          child: SpringInteractiveScale(
                            scaleDownFactor: 0.92,
                            onTap: _togglePlayPause,
                            child: Container(
                              width: 58,
                              height: 58,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: Colors.black.withValues(alpha: 0.6),
                                border: Border.all(color: Colors.white.withValues(alpha: 0.4), width: 1.5),
                              ),
                              child: Center(
                                child: ChatyMediaIcon(
                                  icon: c.value.isPlaying ? ChatyIconType.pause : ChatyIconType.play,
                                  size: 24,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                          ),
                        ),

                        // Bottom Scrub Bar & Timers
                        Padding(
                          padding: const EdgeInsets.fromLTRB(14, 0, 14, 10),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              SliderTheme(
                                data: SliderTheme.of(context).copyWith(
                                  trackHeight: 3,
                                  thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
                                  activeTrackColor: Theme.of(context).colorScheme.primary,
                                  inactiveTrackColor: Colors.white24,
                                  thumbColor: Theme.of(context).colorScheme.primary,
                                ),
                                child: Slider(
                                  value: c.value.position.inMilliseconds.clamp(0, c.value.duration.inMilliseconds).toDouble(),
                                  max: c.value.duration.inMilliseconds.toDouble() > 0
                                      ? c.value.duration.inMilliseconds.toDouble()
                                      : 1.0,
                                  onChanged: (val) {
                                    c.seekTo(Duration(milliseconds: val.round()));
                                    _startControlsTimer();
                                  },
                                ),
                              ),
                              Row(
                                children: [
                                  Text(
                                    _formatDuration(c.value.position),
                                    style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w600),
                                  ),
                                  const Text(' / ', style: TextStyle(color: Colors.white54, fontSize: 11)),
                                  Text(
                                    _formatDuration(c.value.duration),
                                    style: const TextStyle(color: Colors.white54, fontSize: 11),
                                  ),
                                  const Spacer(),
                                  IconButton(
                                    icon: ChatyMediaIcon(
                                      icon: _isFullscreen ? ChatyIconType.exitFullscreen : ChatyIconType.fullscreen,
                                      size: 18,
                                      color: Colors.white,
                                    ),
                                    onPressed: _toggleFullscreen,
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDuration(Duration d) {
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$m:$s';
  }
}
