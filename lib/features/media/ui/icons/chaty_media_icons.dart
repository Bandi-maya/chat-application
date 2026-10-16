import 'dart:math' as math;
import 'package:flutter/material.dart';

enum ChatyIconType {
  audio,
  video,
  pdf,
  document,
  archive,
  folder,
  play,
  pause,
  seekForward,
  seekBackward,
  queue,
  volumeHigh,
  volumeMute,
  fullscreen,
  exitFullscreen,
  subtitles,
  search,
  convert,
  bookmark,
  rotateRight,
  pageExtract,
  merge,
  split,
  share,
  delete,
}

class ChatyMediaIcon extends StatelessWidget {
  final ChatyIconType icon;
  final double size;
  final Color? color;
  final double strokeWidth;

  const ChatyMediaIcon({
    super.key,
    required this.icon,
    this.size = 24.0,
    this.color,
    this.strokeWidth = 2.0,
  });

  @override
  Widget build(BuildContext context) {
    final themeColor = color ?? Theme.of(context).colorScheme.onSurface;
    return CustomPaint(
      size: Size(size, size),
      painter: _ChatyMediaIconPainter(
        icon: icon,
        color: themeColor,
        strokeWidth: strokeWidth,
      ),
    );
  }
}

class _ChatyMediaIconPainter extends CustomPainter {
  final ChatyIconType icon;
  final Color color;
  final double strokeWidth;

  _ChatyMediaIconPainter({
    required this.icon,
    required this.color,
    required this.strokeWidth,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final strokePaint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final fillPaint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;

    final w = size.width;
    final h = size.height;

    switch (icon) {
      case ChatyIconType.audio:
        // Waveform bars with headphones arch
        final headPath = Path()
          ..addArc(Rect.fromLTWH(w * 0.15, h * 0.15, w * 0.7, h * 0.7), math.pi, math.pi);
        canvas.drawPath(headPath, strokePaint);
        // Left ear cup
        canvas.drawRRect(
          RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.1, h * 0.45, w * 0.15, h * 0.35), const Radius.circular(3)),
          strokePaint,
        );
        // Right ear cup
        canvas.drawRRect(
          RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.75, h * 0.45, w * 0.15, h * 0.35), const Radius.circular(3)),
          strokePaint,
        );
        // Sound waves inside
        canvas.drawLine(Offset(w * 0.4, h * 0.65), Offset(w * 0.4, h * 0.45), strokePaint);
        canvas.drawLine(Offset(w * 0.5, h * 0.72), Offset(w * 0.5, h * 0.38), strokePaint);
        canvas.drawLine(Offset(w * 0.6, h * 0.65), Offset(w * 0.6, h * 0.45), strokePaint);
        break;

      case ChatyIconType.video:
        // Camera rectangle + projection triangle
        canvas.drawRRect(
          RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.1, h * 0.22, w * 0.55, h * 0.56), const Radius.circular(4)),
          strokePaint,
        );
        final triPath = Path()
          ..moveTo(w * 0.65, h * 0.4)
          ..lineTo(w * 0.88, h * 0.25)
          ..lineTo(w * 0.88, h * 0.75)
          ..lineTo(w * 0.65, h * 0.6)
          ..close();
        canvas.drawPath(triPath, strokePaint);
        break;

      case ChatyIconType.pdf:
        // Document page with folded top-right corner and 'P'
        final docPath = Path()
          ..moveTo(w * 0.2, h * 0.12)
          ..lineTo(w * 0.58, h * 0.12)
          ..lineTo(w * 0.8, h * 0.34)
          ..lineTo(w * 0.8, h * 0.88)
          ..lineTo(w * 0.2, h * 0.88)
          ..close();
        canvas.drawPath(docPath, strokePaint);
        // Fold
        canvas.drawLine(Offset(w * 0.58, h * 0.12), Offset(w * 0.58, h * 0.34), strokePaint);
        canvas.drawLine(Offset(w * 0.58, h * 0.34), Offset(w * 0.8, h * 0.34), strokePaint);
        // Red dot or mark
        canvas.drawCircle(Offset(w * 0.5, h * 0.62), w * 0.1, fillPaint);
        break;

      case ChatyIconType.document:
        // Clean text document with lines
        final docPath = Path()
          ..addRRect(RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.2, h * 0.12, w * 0.6, h * 0.76), const Radius.circular(4)));
        canvas.drawPath(docPath, strokePaint);
        canvas.drawLine(Offset(w * 0.32, h * 0.35), Offset(w * 0.68, h * 0.35), strokePaint);
        canvas.drawLine(Offset(w * 0.32, h * 0.52), Offset(w * 0.68, h * 0.52), strokePaint);
        canvas.drawLine(Offset(w * 0.32, h * 0.69), Offset(w * 0.52, h * 0.69), strokePaint);
        break;

      case ChatyIconType.archive:
        // Box with buckle zipper
        canvas.drawRRect(
          RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.15, h * 0.2, w * 0.7, h * 0.65), const Radius.circular(4)),
          strokePaint,
        );
        canvas.drawLine(Offset(w * 0.15, h * 0.38), Offset(w * 0.85, h * 0.38), strokePaint);
        canvas.drawRRect(
          RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.42, h * 0.48, w * 0.16, h * 0.16), const Radius.circular(2)),
          strokePaint,
        );
        break;

      case ChatyIconType.folder:
        final folderPath = Path()
          ..moveTo(w * 0.12, h * 0.25)
          ..lineTo(w * 0.4, h * 0.25)
          ..lineTo(w * 0.5, h * 0.35)
          ..lineTo(w * 0.88, h * 0.35)
          ..lineTo(w * 0.88, h * 0.82)
          ..lineTo(w * 0.12, h * 0.82)
          ..close();
        canvas.drawPath(folderPath, strokePaint);
        break;

      case ChatyIconType.play:
        final p = Path()
          ..moveTo(w * 0.32, h * 0.2)
          ..lineTo(w * 0.78, h * 0.5)
          ..lineTo(w * 0.32, h * 0.8)
          ..close();
        canvas.drawPath(p, fillPaint);
        break;

      case ChatyIconType.pause:
        canvas.drawRRect(
          RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.26, h * 0.2, w * 0.16, h * 0.6), const Radius.circular(2)),
          fillPaint,
        );
        canvas.drawRRect(
          RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.58, h * 0.2, w * 0.16, h * 0.6), const Radius.circular(2)),
          fillPaint,
        );
        break;

      case ChatyIconType.seekForward:
        // Arrow pointing right with arc
        canvas.drawLine(Offset(w * 0.3, h * 0.3), Offset(w * 0.52, h * 0.5), strokePaint);
        canvas.drawLine(Offset(w * 0.52, h * 0.5), Offset(w * 0.3, h * 0.7), strokePaint);
        canvas.drawLine(Offset(w * 0.52, h * 0.3), Offset(w * 0.74, h * 0.5), strokePaint);
        canvas.drawLine(Offset(w * 0.74, h * 0.5), Offset(w * 0.52, h * 0.7), strokePaint);
        break;

      case ChatyIconType.seekBackward:
        canvas.drawLine(Offset(w * 0.48, h * 0.3), Offset(w * 0.26, h * 0.5), strokePaint);
        canvas.drawLine(Offset(w * 0.26, h * 0.5), Offset(w * 0.48, h * 0.7), strokePaint);
        canvas.drawLine(Offset(w * 0.7, h * 0.3), Offset(w * 0.48, h * 0.5), strokePaint);
        canvas.drawLine(Offset(w * 0.48, h * 0.5), Offset(w * 0.7, h * 0.7), strokePaint);
        break;

      case ChatyIconType.queue:
        canvas.drawLine(Offset(w * 0.15, h * 0.25), Offset(w * 0.85, h * 0.25), strokePaint);
        canvas.drawLine(Offset(w * 0.15, h * 0.5), Offset(w * 0.65, h * 0.5), strokePaint);
        canvas.drawLine(Offset(w * 0.15, h * 0.75), Offset(w * 0.55, h * 0.75), strokePaint);
        // Play note indicator
        final p = Path()
          ..moveTo(w * 0.72, h * 0.6)
          ..lineTo(w * 0.88, h * 0.7)
          ..lineTo(w * 0.72, h * 0.8)
          ..close();
        canvas.drawPath(p, fillPaint);
        break;

      case ChatyIconType.volumeHigh:
        final spk = Path()
          ..moveTo(w * 0.15, h * 0.38)
          ..lineTo(w * 0.35, h * 0.38)
          ..lineTo(w * 0.55, h * 0.2)
          ..lineTo(w * 0.55, h * 0.8)
          ..lineTo(w * 0.35, h * 0.62)
          ..lineTo(w * 0.15, h * 0.62)
          ..close();
        canvas.drawPath(spk, strokePaint);
        // Two sound waves
        canvas.drawArc(Rect.fromLTWH(w * 0.45, h * 0.32, w * 0.3, h * 0.36), -math.pi / 3, 2 * math.pi / 3, false, strokePaint);
        canvas.drawArc(Rect.fromLTWH(w * 0.45, h * 0.2, w * 0.5, h * 0.6), -math.pi / 3, 2 * math.pi / 3, false, strokePaint);
        break;

      case ChatyIconType.volumeMute:
        final spk = Path()
          ..moveTo(w * 0.15, h * 0.38)
          ..lineTo(w * 0.35, h * 0.38)
          ..lineTo(w * 0.55, h * 0.2)
          ..lineTo(w * 0.55, h * 0.8)
          ..lineTo(w * 0.35, h * 0.62)
          ..lineTo(w * 0.15, h * 0.62)
          ..close();
        canvas.drawPath(spk, strokePaint);
        canvas.drawLine(Offset(w * 0.68, h * 0.38), Offset(w * 0.88, h * 0.62), strokePaint);
        canvas.drawLine(Offset(w * 0.88, h * 0.38), Offset(w * 0.68, h * 0.62), strokePaint);
        break;

      case ChatyIconType.fullscreen:
        // 4 corner expansion brackets
        canvas.drawLine(Offset(w * 0.15, h * 0.35), Offset(w * 0.15, h * 0.15), strokePaint);
        canvas.drawLine(Offset(w * 0.15, h * 0.15), Offset(w * 0.35, h * 0.15), strokePaint);

        canvas.drawLine(Offset(w * 0.85, h * 0.35), Offset(w * 0.85, h * 0.15), strokePaint);
        canvas.drawLine(Offset(w * 0.85, h * 0.15), Offset(w * 0.65, h * 0.15), strokePaint);

        canvas.drawLine(Offset(w * 0.15, h * 0.65), Offset(w * 0.15, h * 0.85), strokePaint);
        canvas.drawLine(Offset(w * 0.15, h * 0.85), Offset(w * 0.35, h * 0.85), strokePaint);

        canvas.drawLine(Offset(w * 0.85, h * 0.65), Offset(w * 0.85, h * 0.85), strokePaint);
        canvas.drawLine(Offset(w * 0.85, h * 0.85), Offset(w * 0.65, h * 0.85), strokePaint);
        break;

      case ChatyIconType.exitFullscreen:
        canvas.drawLine(Offset(w * 0.35, h * 0.15), Offset(w * 0.35, h * 0.35), strokePaint);
        canvas.drawLine(Offset(w * 0.35, h * 0.35), Offset(w * 0.15, h * 0.35), strokePaint);

        canvas.drawLine(Offset(w * 0.65, h * 0.15), Offset(w * 0.65, h * 0.35), strokePaint);
        canvas.drawLine(Offset(w * 0.65, h * 0.35), Offset(w * 0.85, h * 0.35), strokePaint);

        canvas.drawLine(Offset(w * 0.35, h * 0.85), Offset(w * 0.35, h * 0.65), strokePaint);
        canvas.drawLine(Offset(w * 0.35, h * 0.65), Offset(w * 0.15, h * 0.65), strokePaint);

        canvas.drawLine(Offset(w * 0.65, h * 0.85), Offset(w * 0.65, h * 0.65), strokePaint);
        canvas.drawLine(Offset(w * 0.65, h * 0.65), Offset(w * 0.85, h * 0.65), strokePaint);
        break;

      case ChatyIconType.subtitles:
        canvas.drawRRect(
          RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.12, h * 0.22, w * 0.76, h * 0.56), const Radius.circular(4)),
          strokePaint,
        );
        canvas.drawLine(Offset(w * 0.25, h * 0.52), Offset(w * 0.45, h * 0.52), strokePaint);
        canvas.drawLine(Offset(w * 0.55, h * 0.52), Offset(w * 0.75, h * 0.52), strokePaint);
        canvas.drawLine(Offset(w * 0.3, h * 0.65), Offset(w * 0.7, h * 0.65), strokePaint);
        break;

      case ChatyIconType.search:
        canvas.drawCircle(Offset(w * 0.42, h * 0.42), w * 0.25, strokePaint);
        canvas.drawLine(Offset(w * 0.6, h * 0.6), Offset(w * 0.85, h * 0.85), strokePaint);
        break;

      case ChatyIconType.convert:
        // Two rotating arrows forming a circle
        canvas.drawArc(Rect.fromLTWH(w * 0.2, h * 0.2, w * 0.6, h * 0.6), 0, math.pi * 0.75, false, strokePaint);
        canvas.drawArc(Rect.fromLTWH(w * 0.2, h * 0.2, w * 0.6, h * 0.6), math.pi, math.pi * 0.75, false, strokePaint);
        // Arrow heads
        canvas.drawLine(Offset(w * 0.8, h * 0.45), Offset(w * 0.8, h * 0.6), strokePaint);
        canvas.drawLine(Offset(w * 0.8, h * 0.6), Offset(w * 0.65, h * 0.6), strokePaint);
        canvas.drawLine(Offset(w * 0.2, h * 0.55), Offset(w * 0.2, h * 0.4), strokePaint);
        canvas.drawLine(Offset(w * 0.2, h * 0.4), Offset(w * 0.35, h * 0.4), strokePaint);
        break;

      case ChatyIconType.bookmark:
        final bPath = Path()
          ..moveTo(w * 0.25, h * 0.15)
          ..lineTo(w * 0.75, h * 0.15)
          ..lineTo(w * 0.75, h * 0.85)
          ..lineTo(w * 0.5, h * 0.65)
          ..lineTo(w * 0.25, h * 0.85)
          ..close();
        canvas.drawPath(bPath, strokePaint);
        break;

      case ChatyIconType.rotateRight:
        canvas.drawArc(Rect.fromLTWH(w * 0.15, h * 0.15, w * 0.7, h * 0.7), -math.pi / 2, 1.5 * math.pi, false, strokePaint);
        canvas.drawLine(Offset(w * 0.5, h * 0.15), Offset(w * 0.65, h * 0.15), strokePaint);
        canvas.drawLine(Offset(w * 0.65, h * 0.15), Offset(w * 0.55, h * 0.05), strokePaint);
        break;

      case ChatyIconType.pageExtract:
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.15, h * 0.25, w * 0.5, h * 0.65), const Radius.circular(3)), strokePaint);
        canvas.drawLine(Offset(w * 0.5, h * 0.4), Offset(w * 0.85, h * 0.4), strokePaint);
        canvas.drawLine(Offset(w * 0.75, h * 0.3), Offset(w * 0.85, h * 0.4), strokePaint);
        canvas.drawLine(Offset(w * 0.75, h * 0.5), Offset(w * 0.85, h * 0.4), strokePaint);
        break;

      case ChatyIconType.merge:
        // Two rectangles merging into one
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.15, h * 0.2, w * 0.3, h * 0.4), const Radius.circular(2)), strokePaint);
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.55, h * 0.2, w * 0.3, h * 0.4), const Radius.circular(2)), strokePaint);
        canvas.drawLine(Offset(w * 0.5, h * 0.55), Offset(w * 0.5, h * 0.85), strokePaint);
        canvas.drawLine(Offset(w * 0.4, h * 0.75), Offset(w * 0.5, h * 0.85), strokePaint);
        canvas.drawLine(Offset(w * 0.6, h * 0.75), Offset(w * 0.5, h * 0.85), strokePaint);
        break;

      case ChatyIconType.split:
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.35, h * 0.15, w * 0.3, h * 0.4), const Radius.circular(2)), strokePaint);
        canvas.drawLine(Offset(w * 0.4, h * 0.65), Offset(w * 0.25, h * 0.85), strokePaint);
        canvas.drawLine(Offset(w * 0.6, h * 0.65), Offset(w * 0.75, h * 0.85), strokePaint);
        break;

      case ChatyIconType.share:
        canvas.drawCircle(Offset(w * 0.75, h * 0.25), w * 0.08, strokePaint);
        canvas.drawCircle(Offset(w * 0.25, h * 0.5), w * 0.08, strokePaint);
        canvas.drawCircle(Offset(w * 0.75, h * 0.75), w * 0.08, strokePaint);
        canvas.drawLine(Offset(w * 0.32, h * 0.46), Offset(w * 0.68, h * 0.28), strokePaint);
        canvas.drawLine(Offset(w * 0.32, h * 0.54), Offset(w * 0.68, h * 0.72), strokePaint);
        break;

      case ChatyIconType.delete:
        // Trash can
        canvas.drawLine(Offset(w * 0.2, h * 0.3), Offset(w * 0.8, h * 0.3), strokePaint);
        canvas.drawLine(Offset(w * 0.4, h * 0.2), Offset(w * 0.6, h * 0.2), strokePaint);
        final canPath = Path()
          ..moveTo(w * 0.28, h * 0.3)
          ..lineTo(w * 0.32, h * 0.82)
          ..lineTo(w * 0.68, h * 0.82)
          ..lineTo(w * 0.72, h * 0.3);
        canvas.drawPath(canPath, strokePaint);
        canvas.drawLine(Offset(w * 0.42, h * 0.42), Offset(w * 0.42, h * 0.7), strokePaint);
        canvas.drawLine(Offset(w * 0.58, h * 0.42), Offset(w * 0.58, h * 0.7), strokePaint);
        break;
    }
  }

  @override
  bool shouldRepaint(covariant _ChatyMediaIconPainter oldDelegate) {
    return oldDelegate.icon != icon ||
           oldDelegate.color != color ||
           oldDelegate.strokeWidth != strokeWidth;
  }
}
