import 'dart:math' as math;
import 'package:flutter/material.dart';

import '../../../domain/models/call_state.dart';
import '../../../ui/core/design_system/chaty_haptics.dart';

/// Compact, premium iOS-Dynamic-Island-inspired call indicator.
///
/// Designed to sit near top center with approximate dimensions ~130x38 dp,
/// showing caller avatar/initials, duration, green active indicator, and live animated audio waveform.
/// Single tap expands smoothly into Floating PiP / Full Call screen.
class ChatyCallIsland extends StatefulWidget {
  final ChatyCallSession session;
  final int durationSeconds;
  final VoidCallback onTap;
  final VoidCallback? onExpand;

  const ChatyCallIsland({
    super.key,
    required this.session,
    required this.durationSeconds,
    required this.onTap,
    this.onExpand,
  });

  @override
  State<ChatyCallIsland> createState() => _ChatyCallIslandState();
}

class _ChatyCallIslandState extends State<ChatyCallIsland>
    with SingleTickerProviderStateMixin {
  late final AnimationController _waveCtrl;

  @override
  void initState() {
    super.initState();
    _waveCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    )..repeat();
  }

  @override
  void dispose() {
    _waveCtrl.dispose();
    super.dispose();
  }

  String _formatDuration(int totalSec) {
    final m = (totalSec ~/ 60).toString().padLeft(2, '0');
    final s = (totalSec % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    final topPadding = MediaQuery.of(context).padding.top;

    return Positioned(
      top: math.max(topPadding + 6, 12),
      left: 0,
      right: 0,
      child: Center(
        child: Semantics(
          button: true,
          label:
              'Call with ${widget.session.remoteDisplayName}, ${_formatDuration(widget.durationSeconds)}. Tap to open.',
          child: GestureDetector(
            onTap: () {
              ChatyHaptics.selection();
              widget.onTap();
            },
            child: Container(
              height: 38,
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: const Color(0xFF0F0F11),
                borderRadius: BorderRadius.circular(19),
                border: Border.all(
                  color: Colors.white.withValues(alpha: 0.12),
                  width: 0.8,
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.45),
                    blurRadius: 16,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Green live call dot or caller avatar
                  Container(
                    width: 22,
                    height: 22,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      color: Color(0xFF22C55E),
                    ),
                    alignment: Alignment.center,
                    child: Icon(
                      widget.session.isVideo
                          ? Icons.videocam_rounded
                          : Icons.call_rounded,
                      color: Colors.black,
                      size: 13,
                    ),
                  ),
                  const SizedBox(width: 8),

                  // Call duration or status
                  Text(
                    widget.session.state == CallSessionState.connected
                        ? _formatDuration(widget.durationSeconds)
                        : (widget.session.state == CallSessionState.reconnecting
                            ? 'Reconnecting'
                            : 'Calling…'),
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 12.5,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.2,
                    ),
                  ),
                  const SizedBox(width: 8),

                  // Live audio waveform bars
                  AnimatedBuilder(
                    animation: _waveCtrl,
                    builder: (context, _) {
                      return Row(
                        mainAxisSize: MainAxisSize.min,
                        children: List.generate(4, (index) {
                          final phase = (_waveCtrl.value + (index * 0.25)) % 1.0;
                          final height = 4.0 + math.sin(phase * math.pi) * 10.0;
                          return Container(
                            margin: const EdgeInsets.symmetric(horizontal: 1.2),
                            width: 2.5,
                            height: height,
                            decoration: BoxDecoration(
                              color: const Color(0xFF22C55E),
                              borderRadius: BorderRadius.circular(1.5),
                            ),
                          );
                        }),
                      );
                    },
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
