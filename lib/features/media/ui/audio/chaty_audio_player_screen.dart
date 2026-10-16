import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../../ui/core/design_system/design_system.dart';
import '../../models/media_playback_state.dart';
import '../../services/media_player_coordinator.dart';
import '../icons/chaty_media_icons.dart';

class ChatyAudioPlayerScreen extends StatefulWidget {
  final MediaPlayerCoordinator coordinator;

  const ChatyAudioPlayerScreen({
    super.key,
    required this.coordinator,
  });

  @override
  State<ChatyAudioPlayerScreen> createState() => _ChatyAudioPlayerScreenState();
}

class _ChatyAudioPlayerScreenState extends State<ChatyAudioPlayerScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _discAnim;

  @override
  void initState() {
    super.initState();
    _discAnim = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 14),
    );
    if (widget.coordinator.state.isPlaying) {
      _discAnim.repeat();
    }
    widget.coordinator.addListener(_onCoordinatorUpdate);
  }

  void _onCoordinatorUpdate() {
    if (widget.coordinator.state.isPlaying) {
      if (!_discAnim.isAnimating) _discAnim.repeat();
    } else {
      if (_discAnim.isAnimating) _discAnim.stop();
    }
  }

  @override
  void dispose() {
    widget.coordinator.removeListener(_onCoordinatorUpdate);
    _discAnim.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return ListenableBuilder(
      listenable: widget.coordinator,
      builder: (context, _) {
        final state = widget.coordinator.state;
        final track = state.activeItem;

        return Scaffold(
          backgroundColor: isDark ? const Color(0xFF0C1017) : const Color(0xFFF8FAFC),
          appBar: AppBar(
            backgroundColor: Colors.transparent,
            elevation: 0,
            leading: IconButton(
              icon: const Icon(Icons.keyboard_arrow_down_rounded, size: 30),
              onPressed: () => Navigator.pop(context),
            ),
            title: const Text(
              'Now Playing',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
            ),
            centerTitle: true,
            actions: [
              IconButton(
                icon: ChatyMediaIcon(icon: ChatyIconType.queue, size: 20, color: theme.colorScheme.onSurface),
                tooltip: 'Play Queue',
                onPressed: () => _showQueueSheet(context, state),
              ),
              const SizedBox(width: 8),
            ],
          ),
          body: track == null
              ? const Center(child: Text('No audio playing'))
              : SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 28),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        // Large Spinning Vinyl Disc
                        AnimatedBuilder(
                          animation: _discAnim,
                          builder: (context, child) {
                            return Transform.rotate(
                              angle: _discAnim.value * 2 * math.pi,
                              child: child,
                            );
                          },
                          child: Center(
                            child: Container(
                              width: 240,
                              height: 240,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                gradient: RadialGradient(
                                  colors: [
                                    const Color(0xFF1E2530),
                                    const Color(0xFF11161F),
                                    theme.colorScheme.primary.withValues(alpha: 0.35),
                                    const Color(0xFF0D1117),
                                  ],
                                  stops: const [0.3, 0.6, 0.85, 1.0],
                                ),
                                boxShadow: [
                                  BoxShadow(
                                    color: theme.colorScheme.primary.withValues(alpha: 0.25),
                                    blurRadius: 36,
                                    spreadRadius: 4,
                                  ),
                                ],
                              ),
                              child: Center(
                                child: Container(
                                  width: 70,
                                  height: 70,
                                  decoration: BoxDecoration(
                                    shape: BoxShape.circle,
                                    color: theme.colorScheme.primary,
                                  ),
                                  child: const Center(
                                    child: ChatyMediaIcon(
                                      icon: ChatyIconType.audio,
                                      size: 32,
                                      color: Colors.white,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),

                        // Track Title & Details
                        Column(
                          children: [
                            Text(
                              track.name,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              textAlign: TextAlign.center,
                              style: theme.textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.w800,
                                letterSpacing: -0.3,
                              ),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              '${track.humanReadableSize} • Audio Recording',
                              style: theme.textTheme.bodyMedium?.copyWith(
                                color: isDark ? Colors.white60 : Colors.black54,
                              ),
                            ),
                          ],
                        ),

                        // Seek Bar & Timers
                        Column(
                          children: [
                            SliderTheme(
                              data: SliderTheme.of(context).copyWith(
                                trackHeight: 4,
                                thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
                                activeTrackColor: theme.colorScheme.primary,
                                inactiveTrackColor: isDark ? Colors.white12 : Colors.black12,
                                thumbColor: theme.colorScheme.primary,
                                overlayColor: theme.colorScheme.primary.withValues(alpha: 0.2),
                              ),
                              child: Slider(
                                value: state.duration.inMilliseconds > 0
                                    ? state.position.inMilliseconds.clamp(0, state.duration.inMilliseconds).toDouble()
                                    : 0.0,
                                max: state.duration.inMilliseconds > 0
                                    ? state.duration.inMilliseconds.toDouble()
                                    : 1.0,
                                onChanged: (val) {
                                  widget.coordinator.seek(Duration(milliseconds: val.round()));
                                },
                              ),
                            ),
                            Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 14),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(
                                    _formatDuration(state.position),
                                    style: TextStyle(
                                      fontSize: 12,
                                      fontWeight: FontWeight.w600,
                                      color: isDark ? Colors.white54 : Colors.black54,
                                    ),
                                  ),
                                  Text(
                                    '- ${_formatDuration(state.duration - state.position)}',
                                    style: TextStyle(
                                      fontSize: 12,
                                      fontWeight: FontWeight.w600,
                                      color: isDark ? Colors.white54 : Colors.black54,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),

                        // Main Controls Row
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                          children: [
                            IconButton(
                              icon: Icon(
                                Icons.shuffle_rounded,
                                color: state.isShuffle ? theme.colorScheme.primary : (isDark ? Colors.white38 : Colors.black38),
                              ),
                              onPressed: () => widget.coordinator.toggleShuffle(),
                            ),
                            IconButton(
                              icon: ChatyMediaIcon(
                                icon: ChatyIconType.seekBackward,
                                size: 24,
                                color: isDark ? Colors.white : Colors.black87,
                              ),
                              onPressed: state.hasPrevious ? () => widget.coordinator.skipPrevious() : null,
                            ),
                            SpringInteractiveScale(
                              scaleDownFactor: 0.94,
                              onTap: () {
                                HapticFeedback.mediumImpact();
                                widget.coordinator.togglePlayPause();
                              },
                              child: Container(
                                width: 68,
                                height: 68,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: theme.colorScheme.primary,
                                  boxShadow: [
                                    BoxShadow(
                                      color: theme.colorScheme.primary.withValues(alpha: 0.35),
                                      blurRadius: 18,
                                      offset: const Offset(0, 4),
                                    ),
                                  ],
                                ),
                                child: Center(
                                  child: ChatyMediaIcon(
                                    icon: state.isPlaying ? ChatyIconType.pause : ChatyIconType.play,
                                    size: 28,
                                    color: Colors.white,
                                  ),
                                ),
                              ),
                            ),
                            IconButton(
                              icon: ChatyMediaIcon(
                                icon: ChatyIconType.seekForward,
                                size: 24,
                                color: isDark ? Colors.white : Colors.black87,
                              ),
                              onPressed: state.hasNext ? () => widget.coordinator.skipNext() : null,
                            ),
                            IconButton(
                              icon: Icon(
                                state.repeatMode == ChatyRepeatMode.one
                                    ? Icons.repeat_one_rounded
                                    : Icons.repeat_rounded,
                                color: state.repeatMode != ChatyRepeatMode.off
                                    ? theme.colorScheme.primary
                                    : (isDark ? Colors.white38 : Colors.black38),
                              ),
                              onPressed: () => widget.coordinator.cycleRepeatMode(),
                            ),
                          ],
                        ),

                        // Speed Pill Row
                        _buildSpeedSelector(state, theme, isDark),
                      ],
                    ),
                  ),
                ),
        );
      },
    );
  }

  Widget _buildSpeedSelector(MediaPlaybackState state, ThemeData theme, bool isDark) {
    final speeds = <double>[0.75, 1.0, 1.25, 1.5, 2.0];

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF161C26) : const Color(0xFFEDF2F7),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: speeds.map((s) {
          final isSelected = (state.speed - s).abs() < 0.01;
          return SpringInteractiveScale(
            scaleDownFactor: 0.95,
            onTap: () {
              HapticFeedback.selectionClick();
              widget.coordinator.setSpeed(s);
            },
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
              decoration: BoxDecoration(
                color: isSelected ? theme.colorScheme.primary : Colors.transparent,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Text(
                '${s}x',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: isSelected ? FontWeight.w800 : FontWeight.w500,
                  color: isSelected
                      ? Colors.white
                      : (isDark ? Colors.white70 : Colors.black87),
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }

  void _showQueueSheet(BuildContext context, MediaPlaybackState state) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) {
        final isDark = Theme.of(sheetContext).brightness == Brightness.dark;
        return Container(
          height: MediaQuery.of(sheetContext).size.height * 0.6,
          decoration: BoxDecoration(
            color: isDark ? const Color(0xFF141923) : Colors.white,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
          ),
          child: Column(
            children: [
              const SizedBox(height: 12),
              Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.grey.withValues(alpha: 0.4),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 14),
              const Text(
                'Play Queue',
                style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
              ),
              const SizedBox(height: 10),
              Expanded(
                child: ListView.separated(
                  itemCount: state.queue.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (_, index) {
                    final item = state.queue[index];
                    final isCurrent = index == state.queueIndex;
                    return ListTile(
                      leading: Icon(
                        isCurrent ? Icons.play_arrow_rounded : Icons.music_note_rounded,
                        color: isCurrent ? Theme.of(sheetContext).colorScheme.primary : null,
                      ),
                      title: Text(
                        item.name,
                        style: TextStyle(
                          fontWeight: isCurrent ? FontWeight.w700 : FontWeight.w500,
                        ),
                      ),
                      subtitle: Text(item.humanReadableSize),
                      onTap: () {
                        widget.coordinator.playTrack(item, queue: state.queue);
                        Navigator.pop(sheetContext);
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  String _formatDuration(Duration d) {
    if (d.isNegative) return '00:00';
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$m:$s';
  }
}
