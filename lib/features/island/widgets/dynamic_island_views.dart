import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/dynamic_island_payload.dart';
import '../controllers/dynamic_island_controller.dart';

/* ─── Helpers ──────────────────────────────────────────────────────── */
String formatTime(int totalSeconds) {
  final m = totalSeconds ~/ 60;
  final sec = totalSeconds % 60;
  return '$m:${sec.toString().padLeft(2, '0')}';
}

String relativeTime(DateTime timestamp) {
  final diff = DateTime.now().difference(timestamp).inSeconds;
  if (diff < 60) return 'Just now';
  if (diff < 3600) return '${diff ~/ 60}m ago';
  if (diff < 86400) return '${diff ~/ 3600}h ago';
  return '${timestamp.month}/${timestamp.day}';
}

/* ─── Animated 12-bar equalizer waveform matching reference repo ───── */
class IslandWaveform extends StatefulWidget {
  final bool active;
  final Color barColor;
  final double height;

  const IslandWaveform({
    super.key,
    required this.active,
    this.barColor = Colors.white70,
    this.height = 18.0,
  });

  @override
  State<IslandWaveform> createState() => _IslandWaveformState();
}

class _IslandWaveformState extends State<IslandWaveform>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  static const List<double> baseHeights = [
    3, 5, 7, 5, 9, 6, 8, 4, 7, 5, 6, 3
  ];

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    );
    if (widget.active) _controller.repeat(reverse: true);
  }

  @override
  void didUpdateWidget(covariant IslandWaveform oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.active != oldWidget.active) {
      if (widget.active) {
        _controller.repeat(reverse: true);
      } else {
        _controller.stop();
      }
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        return Row(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: List.generate(baseHeights.length, (i) {
            final base = baseHeights[i];
            final factor = widget.active
                ? 1.0 + math.sin((_controller.value * 2 * math.pi) + (i * 0.4)).abs() * 1.1
                : 1.0;
            final barH = (base * factor).clamp(3.0, widget.height);

            return Container(
              width: 2.0,
              height: barH,
              margin: const EdgeInsets.symmetric(horizontal: 1.0),
              decoration: BoxDecoration(
                color: widget.barColor,
                borderRadius: BorderRadius.circular(1.0),
              ),
            );
          }),
        );
      },
    );
  }
}

/* ─── Custom seek slider matching reference repo ───────────────────── */
class IslandSeekBar extends StatelessWidget {
  final double progress; // 0.0 to 1.0
  final void Function(double progress)? onSeek;

  const IslandSeekBar({
    super.key,
    required this.progress,
    this.onSeek,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onHorizontalDragUpdate: (details) {
        final box = context.findRenderObject() as RenderBox?;
        if (box == null || box.size.width <= 0) return;
        final localX = details.localPosition.dx.clamp(0.0, box.size.width);
        onSeek?.call((localX / box.size.width).clamp(0.0, 1.0));
      },
      onTapDown: (details) {
        final box = context.findRenderObject() as RenderBox?;
        if (box == null || box.size.width <= 0) return;
        final localX = details.localPosition.dx.clamp(0.0, box.size.width);
        onSeek?.call((localX / box.size.width).clamp(0.0, 1.0));
      },
      child: Container(
        height: 18,
        alignment: Alignment.center,
        child: Container(
          height: 4.0,
          width: double.infinity,
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.18),
            borderRadius: BorderRadius.circular(2.0),
          ),
          child: Align(
            alignment: Alignment.centerLeft,
            child: FractionallySizedBox(
              widthFactor: progress.clamp(0.0, 1.0),
              child: Container(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(2.0),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/* ─── Collapsed Pill View matching reference repo ─────────────────── */
class CollapsedIslandView extends StatelessWidget {
  final DynamicIslandPayload? payload;
  final int notifCount;
  final VoidCallback onTap;

  const CollapsedIslandView({
    super.key,
    required this.payload,
    required this.notifCount,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final p = payload;
    final isMedia = p?.type == IslandPayloadType.media ||
        p?.type == IslandPayloadType.voiceNotePlayback;
    final isCall = p?.type == IslandPayloadType.incomingCall ||
        p?.type == IslandPayloadType.ongoingCall;

    return Semantics(
      button: true,
      label: p != null ? '${p.title}: ${p.message}' : 'Dynamic Island collapsed',
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () {
          HapticFeedback.lightImpact();
          onTap();
        },
        child: Container(
          height: 42,
          padding: const EdgeInsets.symmetric(horizontal: 12.0),
          child: ClipRect(
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              physics: const NeverScrollableScrollPhysics(),
              child: ConstrainedBox(
                constraints: BoxConstraints(minWidth: isMedia ? 144 : 120),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
              // Leading element
              if (isCall) ...[
                Container(
                  width: 20,
                  height: 20,
                  decoration: const BoxDecoration(
                    color: Color(0xFF22C55E),
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    p?.isVideo == true ? Icons.videocam_rounded : Icons.call_rounded,
                    color: Colors.black,
                    size: 12,
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  p?.durationSeconds != null && p!.durationSeconds > 0
                      ? formatTime(p.durationSeconds)
                      : 'Calling…',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ] else if (isMedia) ...[
                _buildRotatingCover(p),
                const SizedBox(width: 8),
                IslandWaveform(
                  active: p?.isPlaying ?? false,
                  barColor: const Color(0xFF60A5FA),
                  height: 14.0,
                ),
              ] else ...[
                Icon(
                  p?.iconData ?? Icons.notifications_active_rounded,
                  color: p?.accentColor ?? Colors.white,
                  size: 16,
                ),
                const SizedBox(width: 7),
                ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 82),
                  child: Text(
                    p?.title ?? 'Chaty',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],

              const SizedBox(width: 8),

              // Trailing pulse dot or count badge
              if (notifCount > 1)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: Colors.blueAccent.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    '$notifCount',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                )
              else
                Container(
                  width: 6,
                  height: 6,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: isCall
                        ? const Color(0xFF22C55E)
                        : const Color(0xFF60A5FA),
                    boxShadow: [
                      BoxShadow(
                        color: (isCall
                                ? const Color(0xFF22C55E)
                                : const Color(0xFF60A5FA))
                            .withValues(alpha: 0.6),
                        blurRadius: 4,
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ),
    ),
  ),
),
);
}

  Widget _buildRotatingCover(DynamicIslandPayload? p) {
    return Container(
      width: 22,
      height: 22,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: Colors.white.withValues(alpha: 0.12),
        image: p?.avatarUrl != null
            ? DecorationImage(
                image: NetworkImage(p!.avatarUrl!),
                fit: BoxFit.cover,
              )
            : null,
      ),
      child: p?.avatarUrl == null
          ? const Icon(Icons.music_note_rounded, color: Colors.white, size: 13)
          : null,
    );
  }
}

/* ─── Expanded Media Player View matching reference repo ───────────── */
class ExpandedMediaView extends StatelessWidget {
  final DynamicIslandPayload payload;
  final DynamicIslandController controller;

  const ExpandedMediaView({
    super.key,
    required this.payload,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    final hasNotifs = controller.queue.length > 1;

    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Album / Voice Note header
          Row(
            children: [
              Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(14),
                  color: Colors.white.withValues(alpha: 0.1),
                  image: payload.avatarUrl != null
                      ? DecorationImage(
                          image: NetworkImage(payload.avatarUrl!),
                          fit: BoxFit.cover,
                        )
                      : null,
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.4),
                      blurRadius: 10,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: payload.avatarUrl == null
                    ? const Icon(Icons.graphic_eq_rounded, color: Colors.white, size: 28)
                    : null,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      payload.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      payload.mediaArtist ?? payload.message,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.6),
                        fontSize: 12,
                      ),
                    ),
                    const SizedBox(height: 6),
                    IslandWaveform(
                      active: payload.isPlaying,
                      barColor: const Color(0xFF60A5FA),
                      height: 16,
                    ),
                  ],
                ),
              ),
            ],
          ),

          const SizedBox(height: 14),

          // Seek bar
          IslandSeekBar(
            progress: payload.progress,
            onSeek: (val) => payload.onSeek?.call(val),
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                formatTime((payload.progress * payload.durationSeconds).toInt()),
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.4),
                  fontSize: 10,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
              Text(
                formatTime(payload.durationSeconds),
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.4),
                  fontSize: 10,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
            ],
          ),

          const SizedBox(height: 10),

          // Control buttons
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              IconButton(
                icon: const Icon(Icons.replay_10_rounded, color: Colors.white70),
                onPressed: () {
                  HapticFeedback.lightImpact();
                  final prev = (payload.progress - 0.05).clamp(0.0, 1.0);
                  payload.onSeek?.call(prev);
                },
              ),
              const SizedBox(width: 16),
              GestureDetector(
                onTap: () {
                  HapticFeedback.mediumImpact();
                  payload.onTogglePlay?.call();
                },
                child: Container(
                  width: 44,
                  height: 44,
                  decoration: const BoxDecoration(
                    color: Colors.white,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    payload.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                    color: Colors.black,
                    size: 26,
                  ),
                ),
              ),
              const SizedBox(width: 16),
              IconButton(
                icon: const Icon(Icons.forward_10_rounded, color: Colors.white70),
                onPressed: () {
                  HapticFeedback.lightImpact();
                  final next = (payload.progress + 0.05).clamp(0.0, 1.0);
                  payload.onSeek?.call(next);
                },
              ),
            ],
          ),

          // Peek notifications pill button (matching reference repo)
          if (hasNotifs) ...[
            const SizedBox(height: 10),
            GestureDetector(
              onTap: () {
                HapticFeedback.lightImpact();
                controller.setSubView('notifications');
              },
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.06),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.notifications_rounded, color: Colors.white70, size: 14),
                        const SizedBox(width: 6),
                        Text(
                          '${controller.queue.length - 1} unread notification(s)',
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.7),
                            fontSize: 11,
                          ),
                        ),
                      ],
                    ),
                    const Icon(Icons.chevron_right_rounded, color: Colors.white38, size: 16),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/* ─── Expanded Chat Message View with inline Quick Reply ───────────── */
class ExpandedChatMessageView extends StatefulWidget {
  final DynamicIslandPayload payload;
  final DynamicIslandController controller;

  const ExpandedChatMessageView({
    super.key,
    required this.payload,
    required this.controller,
  });

  @override
  State<ExpandedChatMessageView> createState() => _ExpandedChatMessageViewState();
}

class _ExpandedChatMessageViewState extends State<ExpandedChatMessageView> {
  final TextEditingController _replyTextCtrl = TextEditingController();
  bool _showInput = false;

  @override
  void dispose() {
    _replyTextCtrl.dispose();
    super.dispose();
  }

  void _sendReply() {
    final text = _replyTextCtrl.text.trim();
    if (text.isNotEmpty) {
      HapticFeedback.mediumImpact();
      widget.payload.onQuickReply?.call(text);
      _replyTextCtrl.clear();
      setState(() => _showInput = false);
      widget.controller.pinOpen(false);
      widget.controller.dismissActive();
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = widget.payload;

    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Avatar
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: p.accentColor.withValues(alpha: 0.2),
                  image: p.avatarUrl != null
                      ? DecorationImage(image: NetworkImage(p.avatarUrl!), fit: BoxFit.cover)
                      : null,
                ),
                child: p.avatarUrl == null
                    ? Center(
                        child: Text(
                          p.senderInitials ?? p.title.characters.take(1).toString(),
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      )
                    : null,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            p.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                        if (p.isVerified) ...[
                          const SizedBox(width: 4),
                          const Icon(Icons.verified_rounded, color: Color(0xFF60A5FA), size: 14),
                        ],
                        if (p.isE2ee) ...[
                          const SizedBox(width: 4),
                          const Icon(Icons.lock_rounded, color: Color(0xFF34D399), size: 12),
                        ],
                        const SizedBox(width: 8),
                        Text(
                          relativeTime(p.timestamp),
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.4),
                            fontSize: 10,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      p.message,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.8),
                        fontSize: 13,
                        height: 1.25,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),

          const SizedBox(height: 12),

          // Actions / Quick reply input
          if (_showInput) ...[
            Row(
              children: [
                Expanded(
                  child: Container(
                    height: 38,
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(19),
                      border: Border.all(color: Colors.white.withValues(alpha: 0.12)),
                    ),
                    child: TextField(
                      controller: _replyTextCtrl,
                      autofocus: true,
                      style: const TextStyle(color: Colors.white, fontSize: 13),
                      decoration: const InputDecoration(
                        hintText: 'Type a reply…',
                        hintStyle: TextStyle(color: Colors.white38, fontSize: 13),
                        border: InputBorder.none,
                        isDense: true,
                        contentPadding: EdgeInsets.symmetric(vertical: 10),
                      ),
                      onSubmitted: (_) => _sendReply(),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  icon: const Icon(Icons.send_rounded, color: Color(0xFF3B82F6), size: 20),
                  onPressed: _sendReply,
                ),
              ],
            ),
          ] else ...[
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.white,
                      side: BorderSide(color: Colors.white.withValues(alpha: 0.15)),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      padding: const EdgeInsets.symmetric(vertical: 8),
                    ),
                    onPressed: () {
                      HapticFeedback.lightImpact();
                      setState(() => _showInput = true);
                      widget.controller.pinOpen(true);
                    },
                    icon: const Icon(Icons.reply_rounded, size: 16),
                    label: const Text('Quick Reply', style: TextStyle(fontSize: 12)),
                  ),
                ),
                const SizedBox(width: 8),
                TextButton(
                  style: TextButton.styleFrom(
                    foregroundColor: Colors.white60,
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  ),
                  onPressed: () {
                    HapticFeedback.selectionClick();
                    p.onMarkAsRead?.call();
                    widget.controller.dismissActive();
                  },
                  child: const Text('Mark Read', style: TextStyle(fontSize: 12)),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

/* ─── Expanded Call View ───────────────────────────────────────────── */
class ExpandedCallView extends StatelessWidget {
  final DynamicIslandPayload payload;
  final DynamicIslandController controller;

  const ExpandedCallView({
    super.key,
    required this.payload,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    final isIncoming = payload.type == IslandPayloadType.incomingCall;

    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  color: Color(0xFF22C55E),
                ),
                child: Icon(
                  payload.isVideo ? Icons.videocam_rounded : Icons.call_rounded,
                  color: Colors.black,
                  size: 26,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      payload.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      isIncoming
                          ? 'Incoming ${payload.isVideo ? 'Video' : 'Audio'} Call…'
                          : 'Ongoing Call • ${formatTime(payload.durationSeconds)}',
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.6),
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),

          const SizedBox(height: 16),

          // Call action buttons
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              if (isIncoming) ...[
                _CallActionButton(
                  icon: Icons.call_end_rounded,
                  color: const Color(0xFFEF4444),
                  label: 'Decline',
                  onTap: () {
                    HapticFeedback.heavyImpact();
                    payload.onDeclineCall?.call();
                    controller.dismissActive();
                  },
                ),
                _CallActionButton(
                  icon: Icons.call_rounded,
                  color: const Color(0xFF22C55E),
                  label: 'Accept',
                  onTap: () {
                    HapticFeedback.heavyImpact();
                    payload.onAcceptCall?.call();
                  },
                ),
              ] else ...[
                _CallActionButton(
                  icon: payload.isMuted ? Icons.mic_off_rounded : Icons.mic_rounded,
                  color: payload.isMuted ? Colors.white30 : Colors.white12,
                  label: payload.isMuted ? 'Unmute' : 'Mute',
                  onTap: () {
                    HapticFeedback.lightImpact();
                    payload.onTap?.call();
                  },
                ),
                _CallActionButton(
                  icon: payload.isSpeaker ? Icons.volume_up_rounded : Icons.hearing_rounded,
                  color: payload.isSpeaker ? const Color(0xFF3B82F6) : Colors.white12,
                  label: 'Speaker',
                  onTap: () {
                    HapticFeedback.lightImpact();
                    payload.onTap?.call();
                  },
                ),
                _CallActionButton(
                  icon: Icons.call_end_rounded,
                  color: const Color(0xFFEF4444),
                  label: 'End',
                  onTap: () {
                    HapticFeedback.heavyImpact();
                    payload.onDeclineCall?.call();
                    controller.dismissActive();
                  },
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

class _CallActionButton extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String label;
  final VoidCallback onTap;

  const _CallActionButton({
    required this.icon,
    required this.color,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: color,
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: Colors.white, size: 22),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: const TextStyle(color: Colors.white70, fontSize: 11),
          ),
        ],
      ),
    );
  }
}

/* ─── Expanded Notifications Stack View matching reference repo ───── */
class ExpandedNotificationsListView extends StatelessWidget {
  final DynamicIslandController controller;
  final bool showBack;

  const ExpandedNotificationsListView({
    super.key,
    required this.controller,
    this.showBack = false,
  });

  @override
  Widget build(BuildContext context) {
    final list = controller.queue.take(3).toList();

    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Header with Back button, Bell icon, title, Clear All
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  if (showBack)
                    GestureDetector(
                      onTap: () {
                        HapticFeedback.lightImpact();
                        controller.setSubView('primary');
                      },
                      child: const Padding(
                        padding: EdgeInsets.only(right: 6.0),
                        child: Icon(Icons.arrow_back_ios_new_rounded, color: Colors.white70, size: 14),
                      ),
                    ),
                  const Icon(Icons.notifications_rounded, color: Colors.white, size: 16),
                  const SizedBox(width: 8),
                  Text(
                    'Notifications (${controller.queue.length})',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
              GestureDetector(
                onTap: () {
                  HapticFeedback.lightImpact();
                  controller.clearAll();
                },
                child: Container(
                  padding: const EdgeInsets.all(4),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.1),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.close_rounded, color: Colors.white70, size: 14),
                ),
              ),
            ],
          ),

          const SizedBox(height: 12),

          if (list.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 18.0),
              child: Center(
                child: Text(
                  'No notifications',
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.3),
                    fontSize: 12,
                  ),
                ),
              ),
            )
          else
            ...list.map((item) {
              return Container(
                margin: const EdgeInsets.only(bottom: 8.0),
                padding: const EdgeInsets.all(10.0),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.06),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 28,
                      height: 28,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: item.accentColor.withValues(alpha: 0.25),
                      ),
                      child: Center(
                        child: Text(
                          item.iconEmoji ?? '💬',
                          style: const TextStyle(fontSize: 13),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                item.title,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              Text(
                                relativeTime(item.timestamp),
                                style: TextStyle(
                                  color: Colors.white.withValues(alpha: 0.35),
                                  fontSize: 9,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 2),
                          Text(
                            item.message,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: Colors.white.withValues(alpha: 0.6),
                              fontSize: 11,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              );
            }),
        ],
      ),
    );
  }
}
