import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';

import '../../../domain/models/call_state.dart';
import '../../../ui/core/design_system/chaty_haptics.dart';
import '../../../ui/core/widgets/app_avatar.dart';

/// Floating in-app Picture-in-Picture window for active video/voice calls.
///
/// Approximate proportions ~120x175 dp.
/// Supports smooth dragging with safe snap boundaries and drag-to-top-center to collapse into Call Island.
class InAppCallPip extends StatefulWidget {
  final ChatyCallSession session;
  final RTCVideoRenderer? remoteRenderer;
  final int durationSeconds;
  final VoidCallback onTap;
  final VoidCallback onCollapseToIsland;
  final VoidCallback onEndCall;

  const InAppCallPip({
    super.key,
    required this.session,
    required this.remoteRenderer,
    required this.durationSeconds,
    required this.onTap,
    required this.onCollapseToIsland,
    required this.onEndCall,
  });

  @override
  State<InAppCallPip> createState() => _InAppCallPipState();
}

class _InAppCallPipState extends State<InAppCallPip> {
  Offset? _position;
  bool _isDragging = false;
  bool _showControls = false;

  static const double _pipWidth = 120.0;
  static const double _pipHeight = 175.0;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_position == null) {
      final size = MediaQuery.of(context).size;
      final padding = MediaQuery.of(context).padding;
      _position = Offset(
        size.width - _pipWidth - 16,
        padding.top + 50,
      );
    }
  }

  String _formatDuration(int totalSec) {
    final m = (totalSec ~/ 60).toString().padLeft(2, '0');
    final s = (totalSec % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  void _onPanUpdate(DragUpdateDetails details) {
    setState(() {
      _isDragging = true;
      final current = _position ?? Offset.zero;
      _position = current + details.delta;
    });
  }

  void _onPanEnd(DragEndDetails details) {
    final size = MediaQuery.of(context).size;
    final padding = MediaQuery.of(context).padding;
    final current = _position ?? Offset.zero;

    // Check if dragged to top-center region to collapse into Call Island
    final centerX = current.dx + (_pipWidth / 2);
    final screenCenterX = size.width / 2;
    if (current.dy < padding.top + 60 && (centerX - screenCenterX).abs() < 90) {
      ChatyHaptics.selection();
      widget.onCollapseToIsland();
      return;
    }

    // Otherwise snap smoothly to closest safe horizontal edge
    final snapLeft = 14.0;
    final snapRight = size.width - _pipWidth - 14.0;
    final targetX = (centerX < screenCenterX) ? snapLeft : snapRight;

    final minY = padding.top + 10.0;
    final maxY = size.height - _pipHeight - padding.bottom - 70.0;
    final targetY = current.dy.clamp(minY, math.max(minY, maxY)).toDouble();

    setState(() {
      _isDragging = false;
      _position = Offset(targetX, targetY);
    });
  }

  @override
  Widget build(BuildContext context) {
    final pos = _position ?? const Offset(20, 80);

    return Positioned(
      left: pos.dx,
      top: pos.dy,
      child: GestureDetector(
        onPanUpdate: _onPanUpdate,
        onPanEnd: _onPanEnd,
        onTap: () {
          setState(() => _showControls = !_showControls);
        },
        child: Container(
          width: _pipWidth,
          height: _pipHeight,
          decoration: BoxDecoration(
            color: const Color(0xFF141416),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: Colors.white.withValues(alpha: 0.18),
              width: 1.2,
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: _isDragging ? 0.6 : 0.4),
                blurRadius: _isDragging ? 22 : 14,
                offset: const Offset(0, 6),
              ),
            ],
          ),
          clipBehavior: Clip.antiAlias,
          child: Stack(
            fit: StackFit.expand,
            children: [
              // Remote video or avatar fallback
              if (widget.session.isVideo &&
                  !widget.session.isCameraOff &&
                  widget.remoteRenderer != null &&
                  widget.remoteRenderer!.srcObject != null)
                RTCVideoView(
                  widget.remoteRenderer!,
                  objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
                )
              else
                Container(
                  color: const Color(0xFF1E1E24),
                  child: Center(
                    child: AppAvatar(
                      initials: widget.session.remoteAvatarInitials ??
                          (widget.session.remoteDisplayName.isNotEmpty
                              ? widget.session.remoteDisplayName.substring(0, 1).toUpperCase()
                              : 'U'),
                      colorHex: widget.session.remoteAvatarColorHex,
                      size: 46,
                    ),
                  ),
                ),

              // Duration & name badge
              Positioned(
                top: 8,
                left: 8,
                right: 8,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.65),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(
                        width: 6,
                        height: 6,
                        decoration: const BoxDecoration(
                          shape: BoxShape.circle,
                          color: Color(0xFF22C55E),
                        ),
                      ),
                      const SizedBox(width: 4),
                      Expanded(
                        child: Text(
                          _formatDuration(widget.durationSeconds),
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 10.5,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),

              // Minimal controls overlay when tapped
              if (_showControls)
                Container(
                  color: Colors.black.withValues(alpha: 0.55),
                  child: Center(
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        // Fullscreen expand
                        IconButton(
                          icon: const Icon(Icons.fullscreen_rounded, color: Colors.white, size: 22),
                          tooltip: 'Expand call',
                          onPressed: () {
                            ChatyHaptics.selection();
                            widget.onTap();
                          },
                        ),
                        // Collapse to Island
                        IconButton(
                          icon: const Icon(Icons.minimize_rounded, color: Colors.white70, size: 20),
                          tooltip: 'Minimize to Island',
                          onPressed: () {
                            ChatyHaptics.selection();
                            widget.onCollapseToIsland();
                          },
                        ),
                        // Hang up
                        IconButton(
                          icon: const Icon(Icons.call_end_rounded, color: Color(0xFFEF4444), size: 22),
                          tooltip: 'End call',
                          onPressed: () {
                            ChatyHaptics.warning();
                            widget.onEndCall();
                          },
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
