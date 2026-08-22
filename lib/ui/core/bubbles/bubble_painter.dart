import 'package:flutter/material.dart';
import 'bubble_style_id.dart';
import 'bubble_style_registry.dart';

/// CustomPainter that renders vector paths, contours, outlines, folded corners,
/// and accent marks for any of the 48 BubbleStyleId options.
class BubblePainter extends CustomPainter {
  final BubbleStyleId styleId;
  final bool isMe;
  final Color fillColor;
  final Color strokeColor;
  final Color accentColor;

  const BubblePainter({
    required this.styleId,
    required this.isMe,
    required this.fillColor,
    required this.strokeColor,
    required this.accentColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final geometry = BubbleStyleRegistry.getGeometry(styleId);
    final path = geometry.getBubblePath(rect, isMe: isMe);

    // 1. Drop shadow if applicable
    if (geometry.hasDropShadow) {
      final shadowPaint = Paint()
        ..color = Colors.black.withValues(alpha: 0.15)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 3.0);
      canvas.drawPath(path.shift(const Offset(0, 2)), shadowPaint);
    }

    // 2. Fill background
    final fillPaint = Paint()
      ..color = fillColor
      ..style = PaintingStyle.fill;
    canvas.drawPath(path, fillPaint);

    // 3. Stroke outline if configured
    if (geometry.isOutlined) {
      final strokePaint = Paint()
        ..color = strokeColor
        ..style = PaintingStyle.stroke
        ..strokeWidth = geometry.strokeWidth;
      canvas.drawPath(path, strokePaint);
    }

    // 4. Custom decorative vector accents (e.g. Kitty ears, Amor heart, Fold corner, Dots)
    _paintAccents(canvas, rect);
  }

  void _paintAccents(Canvas canvas, Rect rect) {
    switch (styleId) {
      // Kitty: subtle cute ear outline at top corner
      case BubbleStyleId.kitty:
        final earPaint = Paint()
          ..color = accentColor.withValues(alpha: 0.7)
          ..style = PaintingStyle.fill;
        if (isMe) {
          final earPath = Path()
            ..moveTo(rect.right - 28, rect.top)
            ..lineTo(rect.right - 22, rect.top - 5)
            ..lineTo(rect.right - 16, rect.top)
            ..close();
          canvas.drawPath(earPath, earPaint);
        } else {
          final earPath = Path()
            ..moveTo(rect.left + 16, rect.top)
            ..lineTo(rect.left + 22, rect.top - 5)
            ..lineTo(rect.left + 28, rect.top)
            ..close();
          canvas.drawPath(earPath, earPaint);
        }
        break;

      // Amor: small pink heart crest
      case BubbleStyleId.amor:
        final heartPaint = Paint()
          ..color = Colors.pinkAccent.withValues(alpha: 0.85)
          ..style = PaintingStyle.fill;
        final center = isMe
            ? Offset(rect.right - 14, rect.top + 4)
            : Offset(rect.left + 14, rect.top + 4);
        canvas.drawCircle(center, 3, heartPaint);
        break;

      // Gabi Dot & Gabi Dot 2 & Ilkhang: satellite dot at the corner
      case BubbleStyleId.gabiDot:
      case BubbleStyleId.gabiDot2:
      case BubbleStyleId.ilkhang:
        final dotPaint = Paint()
          ..color = accentColor
          ..style = PaintingStyle.fill;
        final dotCenter = isMe
            ? Offset(rect.right + 2, rect.top + 4)
            : Offset(rect.left - 2, rect.top + 4);
        canvas.drawCircle(dotCenter, 3.5, dotPaint);
        break;

      // Fold / Fold v2 / WA+ Paper: folded paper corner triangle
      case BubbleStyleId.fold:
      case BubbleStyleId.foldV2:
      case BubbleStyleId.waPaperRedesigned:
        final foldPaint = Paint()
          ..color = Colors.black.withValues(alpha: 0.15)
          ..style = PaintingStyle.fill;
        if (isMe) {
          final foldPath = Path()
            ..moveTo(rect.right - 12, rect.top)
            ..lineTo(rect.right, rect.top + 12)
            ..lineTo(rect.right - 12, rect.top + 12)
            ..close();
          canvas.drawPath(foldPath, foldPaint);
        } else {
          final foldPath = Path()
            ..moveTo(rect.left + 12, rect.top)
            ..lineTo(rect.left, rect.top + 12)
            ..lineTo(rect.left + 12, rect.top + 12)
            ..close();
          canvas.drawPath(foldPath, foldPaint);
        }
        break;

      default:
        break;
    }
  }

  @override
  bool shouldRepaint(covariant BubblePainter oldDelegate) {
    return oldDelegate.styleId != styleId ||
        oldDelegate.isMe != isMe ||
        oldDelegate.fillColor != fillColor ||
        oldDelegate.strokeColor != strokeColor ||
        oldDelegate.accentColor != accentColor;
  }
}
