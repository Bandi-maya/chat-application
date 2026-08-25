import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../../../domain/models/chat_message.dart';
import 'delivery_icon_style.dart';

/// Renders vectorized status icons for all 16 tick styles and real message delivery states.
class DeliveryStatusIconPainter extends CustomPainter {
  final DeliveryIconStyle style;
  final DeliveryState state;
  final Color unreadColor;
  final Color readColor;

  const DeliveryStatusIconPainter({
    required this.style,
    required this.state,
    required this.unreadColor,
    required this.readColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (state == DeliveryState.failed) {
      _paintFailed(canvas, size);
      return;
    }
    if (state == DeliveryState.queued || state == DeliveryState.sending) {
      _paintClock(canvas, size);
      return;
    }

    final isRead = state == DeliveryState.read;
    final isDelivered = state == DeliveryState.delivered;
    final activeColor = isRead ? readColor : unreadColor;

    final paint = Paint()
      ..color = activeColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.6
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final fillPaint = Paint()
      ..color = activeColor
      ..style = PaintingStyle.fill;

    switch (style) {
      // 1. Sticker: Solid rounded badge with check
      case DeliveryIconStyle.sticker:
        canvas.drawCircle(
          Offset(size.width / 2, size.height / 2),
          size.width / 2 - 1,
          fillPaint,
        );
        final checkPaint = Paint()
          ..color = Colors.white
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.5
          ..strokeCap = StrokeCap.round;
        final p = Path()
          ..moveTo(size.width * 0.28, size.height * 0.52)
          ..lineTo(size.width * 0.44, size.height * 0.68)
          ..lineTo(size.width * 0.72, size.height * 0.36);
        canvas.drawPath(p, checkPaint);
        break;

      // 2. RC iOS 11: Double chevron check
      case DeliveryIconStyle.rcIos11:
      case DeliveryIconStyle.ios2:
        final p1 = Path()
          ..moveTo(size.width * 0.12, size.height * 0.52)
          ..lineTo(size.width * 0.34, size.height * 0.74)
          ..lineTo(size.width * 0.68, size.height * 0.32);
        canvas.drawPath(p1, paint);
        if (isDelivered || isRead) {
          final p2 = Path()
            ..moveTo(size.width * 0.38, size.height * 0.52)
            ..lineTo(size.width * 0.60, size.height * 0.74)
            ..lineTo(size.width * 0.94, size.height * 0.32);
          canvas.drawPath(p2, paint);
        }
        break;

      // 3. BBM V2: Curved arrows
      case DeliveryIconStyle.bbmV2:
        final p1 = Path()
          ..moveTo(size.width * 0.15, size.height * 0.45)
          ..lineTo(size.width * 0.42, size.height * 0.72)
          ..lineTo(size.width * 0.65, size.height * 0.28);
        canvas.drawPath(p1, paint);
        if (isDelivered || isRead) {
          final p2 = Path()
            ..moveTo(size.width * 0.45, size.height * 0.45)
            ..lineTo(size.width * 0.72, size.height * 0.72)
            ..lineTo(size.width * 0.95, size.height * 0.28);
          canvas.drawPath(p2, paint);
        }
        break;

      // 4. B.W Ticks: High contrast sharp angled check
      case DeliveryIconStyle.bwTicks:
        paint.strokeWidth = 2.0;
        final p1 = Path()
          ..moveTo(size.width * 0.15, size.height * 0.5)
          ..lineTo(size.width * 0.4, size.height * 0.75)
          ..lineTo(size.width * 0.7, size.height * 0.3);
        canvas.drawPath(p1, paint);
        if (isDelivered || isRead) {
          final p2 = Path()
            ..moveTo(size.width * 0.42, size.height * 0.5)
            ..lineTo(size.width * 0.67, size.height * 0.75)
            ..lineTo(size.width * 0.95, size.height * 0.3);
          canvas.drawPath(p2, paint);
        }
        break;

      // 5. CirCheck: Outlined circle containing check
      case DeliveryIconStyle.cirCheck:
        canvas.drawCircle(
          Offset(size.width / 2, size.height / 2),
          size.width / 2 - 1,
          paint,
        );
        final p = Path()
          ..moveTo(size.width * 0.28, size.height * 0.52)
          ..lineTo(size.width * 0.44, size.height * 0.68)
          ..lineTo(size.width * 0.72, size.height * 0.36);
        canvas.drawPath(p, paint);
        break;

      // 6. Circle Print: Concentric status ring
      case DeliveryIconStyle.circlePrint:
        canvas.drawCircle(
          Offset(size.width / 2, size.height / 2),
          size.width / 2 - 1.5,
          paint..strokeWidth = 1.4,
        );
        if (isDelivered || isRead) {
          canvas.drawCircle(
            Offset(size.width / 2, size.height / 2),
            size.width / 4,
            fillPaint,
          );
        }
        break;

      // 7. Gab Circle: Orbital dotted halo
      case DeliveryIconStyle.gabCircle:
        final center = Offset(size.width / 2, size.height / 2);
        final radius = size.width / 2 - 2;
        final count = (isDelivered || isRead) ? 8 : 4;
        for (int i = 0; i < count; i++) {
          final angle = i * 2 * math.pi / count;
          final dotCenter = Offset(
            center.dx + radius * math.cos(angle),
            center.dy + radius * math.sin(angle),
          );
          canvas.drawCircle(dotCenter, 1.3, fillPaint);
        }
        break;

      // 8. Gab Face: Friendly geometric face status
      case DeliveryIconStyle.gabFace:
        canvas.drawCircle(
          Offset(size.width / 2, size.height / 2),
          size.width / 2 - 1,
          paint,
        );
        // Eyes
        canvas.drawCircle(
          Offset(size.width * 0.35, size.height * 0.4),
          1.2,
          fillPaint,
        );
        canvas.drawCircle(
          Offset(size.width * 0.65, size.height * 0.4),
          1.2,
          fillPaint,
        );
        // Smile
        final smile = Path()
          ..arcTo(
            Rect.fromCircle(
              center: Offset(size.width / 2, size.height * 0.45),
              radius: 4,
            ),
            0.2,
            math.pi - 0.4,
            false,
          );
        canvas.drawPath(smile, paint);
        break;

      // 9. Gab iflo: Shield check
      case DeliveryIconStyle.gabIflo:
      case DeliveryIconStyle.rcAlo:
        final shield = Path()
          ..moveTo(size.width * 0.2, size.height * 0.2)
          ..lineTo(size.width * 0.8, size.height * 0.2)
          ..lineTo(size.width * 0.8, size.height * 0.55)
          ..quadraticBezierTo(
            size.width * 0.5,
            size.height * 0.95,
            size.width * 0.2,
            size.height * 0.55,
          )
          ..close();
        canvas.drawPath(shield, isRead ? fillPaint : paint);
        final checkP = Path()
          ..moveTo(size.width * 0.35, size.height * 0.5)
          ..lineTo(size.width * 0.48, size.height * 0.62)
          ..lineTo(size.width * 0.68, size.height * 0.38);
        canvas.drawPath(
          checkP,
          paint..color = isRead ? Colors.white : activeColor,
        );
        break;

      // 10. Green Tick & RC Tick: Bold modern check pair
      case DeliveryIconStyle.greenTick:
      case DeliveryIconStyle.rcTick:
        paint.strokeWidth = 2.2;
        final p1 = Path()
          ..moveTo(size.width * 0.12, size.height * 0.5)
          ..lineTo(size.width * 0.36, size.height * 0.74)
          ..lineTo(size.width * 0.68, size.height * 0.26);
        canvas.drawPath(p1, paint);
        if (isDelivered || isRead) {
          final p2 = Path()
            ..moveTo(size.width * 0.42, size.height * 0.5)
            ..lineTo(size.width * 0.66, size.height * 0.74)
            ..lineTo(size.width * 0.96, size.height * 0.26);
          canvas.drawPath(p2, paint);
        }
        break;

      // 11. Letter Circle: Monogram badge
      case DeliveryIconStyle.letterCircle:
        canvas.drawCircle(
          Offset(size.width / 2, size.height / 2),
          size.width / 2 - 1,
          fillPaint,
        );
        final letterPaint = Paint()
          ..color = Colors.white
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.4;
        final p = Path()
          ..moveTo(size.width * 0.35, size.height * 0.5)
          ..lineTo(size.width * 0.48, size.height * 0.64)
          ..lineTo(size.width * 0.7, size.height * 0.36);
        canvas.drawPath(p, letterPaint);
        break;

      // 12. Triangle: Geometric play indicator
      case DeliveryIconStyle.triangle:
        final triPath = Path()
          ..moveTo(size.width * 0.25, size.height * 0.2)
          ..lineTo(size.width * 0.85, size.height * 0.5)
          ..lineTo(size.width * 0.25, size.height * 0.8)
          ..close();
        if (isRead) {
          canvas.drawPath(triPath, fillPaint);
        } else {
          canvas.drawPath(triPath, paint);
        }
        break;

      // 13. VantCircle: Glow circular target indicator
      case DeliveryIconStyle.vantCircle:
        canvas.drawCircle(
          Offset(size.width / 2, size.height / 2),
          size.width / 2 - 1,
          paint,
        );
        if (isDelivered || isRead) {
          canvas.drawCircle(
            Offset(size.width / 2, size.height / 2),
            3,
            fillPaint,
          );
        }
        break;
    }
  }

  void _paintClock(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = unreadColor.withValues(alpha: 0.7)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.4;
    canvas.drawCircle(
      Offset(size.width / 2, size.height / 2),
      size.width / 2 - 1.5,
      paint,
    );
    // Clock hands
    final hands = Path()
      ..moveTo(size.width / 2, size.height * 0.28)
      ..lineTo(size.width / 2, size.height / 2)
      ..lineTo(size.width * 0.72, size.height / 2);
    canvas.drawPath(hands, paint);
  }

  void _paintFailed(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.redAccent
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.6;
    canvas.drawCircle(
      Offset(size.width / 2, size.height / 2),
      size.width / 2 - 1,
      paint,
    );
    final excl = Path()
      ..moveTo(size.width / 2, size.height * 0.28)
      ..lineTo(size.width / 2, size.height * 0.56);
    canvas.drawPath(excl, paint);
    canvas.drawCircle(
      Offset(size.width / 2, size.height * 0.72),
      1.2,
      Paint()..color = Colors.redAccent,
    );
  }

  @override
  bool shouldRepaint(covariant DeliveryStatusIconPainter oldDelegate) {
    return oldDelegate.style != style ||
        oldDelegate.state != state ||
        oldDelegate.unreadColor != unreadColor ||
        oldDelegate.readColor != readColor;
  }
}
