import 'dart:math' as math;
import 'package:flutter/material.dart';

import '../../../domain/models/connection_health.dart';
import '../theme/semantic_colors.dart';

/// Production 4-state connection strength indicator component.
///
/// States:
/// - Green (Excellent): 3 ascending bars
/// - Yellow (Weak): 2 ascending bars + 1 dimmed
/// - Red (Poor): 1 active bar + 2 dimmed
/// - Dead (Offline): 0 bars with subtle diagonal strike-through / muted glyph
class ConnectionHealthIndicator extends StatelessWidget {
  final ConnectionHealth health;
  final double size;
  final VoidCallback? onTap;
  final bool showLabel;

  const ConnectionHealthIndicator({
    super.key,
    required this.health,
    this.size = 18.0,
    this.onTap,
    this.showLabel = false,
  });

  String get _semanticLabel => switch (health) {
        ConnectionHealth.excellent => 'Connection excellent',
        ConnectionHealth.weak => 'Connection weak',
        ConnectionHealth.poor => 'Connection poor',
        ConnectionHealth.offline => 'No internet connection',
      };

  @override
  Widget build(BuildContext context) {
    final appColors = Theme.of(context).extension<AppColors>();
    final excellentColor = appColors?.connectionExcellent ?? const Color(0xFF10B981);
    final weakColor = appColors?.connectionWeak ?? const Color(0xFFF59E0B);
    final poorColor = appColors?.connectionPoor ?? const Color(0xFFEF4444);
    final offlineColor = appColors?.connectionOffline ?? const Color(0xFF71717A);

    final (color, activeBars) = switch (health) {
      ConnectionHealth.excellent => (excellentColor, 3),
      ConnectionHealth.weak => (weakColor, 2),
      ConnectionHealth.poor => (poorColor, 1),
      ConnectionHealth.offline => (offlineColor, 0),
    };

    final widget = Semantics(
      label: _semanticLabel,
      button: onTap != null,
      child: GestureDetector(
        onTap: onTap,
        behavior: HitTestBehavior.opaque,
        child: Row(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            SizedBox(
              width: size,
              height: size,
              child: CustomPaint(
                painter: _ConnectionBarsPainter(
                  activeBars: activeBars,
                  activeColor: color,
                  dimmedColor: color.withValues(alpha: 0.25),
                  isOffline: health == ConnectionHealth.offline,
                ),
              ),
            ),
            if (showLabel) ...[
              const SizedBox(width: 6),
              Text(
                _labelForHealth(health),
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: color,
                ),
              ),
            ],
          ],
        ),
      ),
    );

    return widget;
  }

  static String _labelForHealth(ConnectionHealth health) => switch (health) {
        ConnectionHealth.excellent => 'Connected',
        ConnectionHealth.weak => 'Weak',
        ConnectionHealth.poor => 'Poor',
        ConnectionHealth.offline => 'Offline',
      };
}

class _ConnectionBarsPainter extends CustomPainter {
  final int activeBars;
  final Color activeColor;
  final Color dimmedColor;
  final bool isOffline;

  const _ConnectionBarsPainter({
    required this.activeBars,
    required this.activeColor,
    required this.dimmedColor,
    required this.isOffline,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final barCount = 3;
    final spacing = size.width * 0.15;
    final totalSpacing = spacing * (barCount - 1);
    final barWidth = (size.width - totalSpacing) / barCount;

    // Heights relative to size.height: 38%, 68%, 100%
    final heights = [
      size.height * 0.38,
      size.height * 0.68,
      size.height * 1.00,
    ];

    final radius = Radius.circular(barWidth * 0.45);

    for (int i = 0; i < barCount; i++) {
      final x = i * (barWidth + spacing);
      final h = heights[i];
      final y = size.height - h;
      final rect = Rect.fromLTWH(x, y, barWidth, h);
      final rrect = RRect.fromRectAndRadius(rect, radius);

      final isBarActive = !isOffline && (i < activeBars);
      final paint = Paint()
        ..color = isBarActive ? activeColor : dimmedColor
        ..style = PaintingStyle.fill;

      canvas.drawRRect(rrect, paint);
    }

    if (isOffline) {
      // Draw subtle diagonal strike-through slash for offline/dead state
      final slashPaint = Paint()
        ..color = activeColor
        ..style = PaintingStyle.stroke
        ..strokeWidth = math.max(1.4, size.width * 0.08)
        ..strokeCap = StrokeCap.round;

      canvas.drawLine(
        Offset(size.width * 0.1, size.height * 0.9),
        Offset(size.width * 0.9, size.height * 0.1),
        slashPaint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _ConnectionBarsPainter oldDelegate) {
    return oldDelegate.activeBars != activeBars ||
        oldDelegate.activeColor != activeColor ||
        oldDelegate.dimmedColor != dimmedColor ||
        oldDelegate.isOffline != isOffline;
  }
}
