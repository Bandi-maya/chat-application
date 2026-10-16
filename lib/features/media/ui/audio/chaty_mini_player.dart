import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import '../../../../ui/core/design_system/design_system.dart';
import '../../models/media_playback_state.dart';
import '../../services/media_player_coordinator.dart';
import '../icons/chaty_media_icons.dart';
import 'chaty_audio_player_screen.dart';

class ChatyMiniPlayer extends StatelessWidget {
  final MediaPlayerCoordinator coordinator;

  const ChatyMiniPlayer({
    super.key,
    required this.coordinator,
  });

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: coordinator,
      builder: (context, _) {
        final state = coordinator.state;
        if (!state.hasActiveItem || state.status == PlaybackStatus.idle || state.status == PlaybackStatus.stopped) {
          return const SizedBox.shrink();
        }

        final theme = Theme.of(context);
        final isDark = theme.brightness == Brightness.dark;
        final track = state.activeItem!;

        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
          child: SpringInteractiveScale(
            scaleDownFactor: 0.98,
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute<void>(
                  fullscreenDialog: true,
                  builder: (_) => ChatyAudioPlayerScreen(coordinator: coordinator),
                ),
              );
            },
            child: ClipRRect(
              borderRadius: BorderRadius.circular(18),
              child: BackdropFilter(
                filter: ui.ImageFilter.blur(sigmaX: 18, sigmaY: 18),
                child: Container(
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF1B2232).withValues(alpha: 0.92) : Colors.white.withValues(alpha: 0.94),
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(
                      color: isDark ? Colors.white.withValues(alpha: 0.12) : Colors.black.withValues(alpha: 0.08),
                      width: 1,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: isDark ? 0.35 : 0.08),
                        blurRadius: 16,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                        child: Row(
                          children: [
                            // Spinning vinyl icon or wave
                            Container(
                              width: 38,
                              height: 38,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: theme.colorScheme.primaryContainer,
                              ),
                              child: Center(
                                child: ChatyMediaIcon(
                                  icon: ChatyIconType.audio,
                                  size: 18,
                                  color: theme.colorScheme.onPrimaryContainer,
                                ),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    track.name,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    '${_formatDuration(state.position)} / ${_formatDuration(state.duration)}',
                                    style: TextStyle(
                                      fontSize: 11,
                                      color: isDark ? Colors.white60 : Colors.black54,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            IconButton(
                              icon: ChatyMediaIcon(
                                icon: state.isPlaying ? ChatyIconType.pause : ChatyIconType.play,
                                size: 18,
                                color: theme.colorScheme.primary,
                              ),
                              onPressed: () => coordinator.togglePlayPause(),
                            ),
                            IconButton(
                              icon: const Icon(Icons.close_rounded, size: 18),
                              onPressed: () => coordinator.stop(),
                            ),
                          ],
                        ),
                      ),
                      // Progress Bar
                      LinearProgressIndicator(
                        value: state.progress,
                        minHeight: 2.5,
                        backgroundColor: isDark ? Colors.white12 : Colors.black12,
                        valueColor: AlwaysStoppedAnimation<Color>(theme.colorScheme.primary),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  String _formatDuration(Duration d) {
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$m:$s';
  }
}
