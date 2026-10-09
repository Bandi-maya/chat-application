import 'package:flutter/material.dart';

/// Original lightweight vector glyphs used for Chaty's high-frequency controls.
/// Drawn from paths/primitives in the app code; no external icon package or
/// font glyph dependency is required. The parent control owns semantics/tooltips.
enum ChatyGlyph {
  camera,
  qrScan,
  devices,
  palette,
  templates,
  homeLayout,
  navigation,
  settings,
  chevronRight,
  more,
  tune,
  search,
  close,
  exportProfile,
  importProfile,
  reset,
  chatBubble,
  person,
  list,
  updates,
  calls,
}

class ChatyGlyphIcon extends StatelessWidget {
  const ChatyGlyphIcon({
    super.key,
    required this.glyph,
    this.size = 20,
    this.color,
    this.strokeWidth = 1.8,
  });

  final ChatyGlyph glyph;
  final double size;
  final Color? color;
  final double strokeWidth;

  @override
  Widget build(BuildContext context) {
    final resolvedColor = color ?? IconTheme.of(context).color ?? Colors.white;
    return SizedBox.square(
      dimension: size,
      child: CustomPaint(
        painter: _ChatyGlyphPainter(
          glyph: glyph,
          color: resolvedColor,
          strokeWidth: strokeWidth,
        ),
      ),
    );
  }
}

class _ChatyGlyphPainter extends CustomPainter {
  const _ChatyGlyphPainter({
    required this.glyph,
    required this.color,
    required this.strokeWidth,
  });

  final ChatyGlyph glyph;
  final Color color;
  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    canvas.save();
    canvas.scale(size.width / 24, size.height / 24);
    final line = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..isAntiAlias = true;
    final solid = Paint()
      ..color = color
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;

    void stroke(Iterable<Offset> points, {bool close = false}) {
      final path = Path();
      var first = true;
      for (final point in points) {
        if (first) {
          path.moveTo(point.dx, point.dy);
          first = false;
        } else {
          path.lineTo(point.dx, point.dy);
        }
      }
      if (close) path.close();
      canvas.drawPath(path, line);
    }

    switch (glyph) {
      case ChatyGlyph.camera:
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            const Rect.fromLTRB(3.5, 7.5, 20.5, 19.5),
            const Radius.circular(2.6),
          ),
          line,
        );
        stroke(const <Offset>[
          Offset(8, 7.5),
          Offset(9.7, 4.8),
          Offset(14.5, 4.8),
          Offset(16.2, 7.5),
        ]);
        canvas.drawCircle(const Offset(12, 13.5), 3.1, line);
        canvas.drawCircle(const Offset(17.6, 10), 0.8, solid);
        break;
      case ChatyGlyph.qrScan:
        stroke(const <Offset>[
          Offset(4, 9),
          Offset(4, 4),
          Offset(9, 4),
        ]);
        stroke(const <Offset>[
          Offset(15, 4),
          Offset(20, 4),
          Offset(20, 9),
        ]);
        stroke(const <Offset>[
          Offset(20, 15),
          Offset(20, 20),
          Offset(15, 20),
        ]);
        stroke(const <Offset>[
          Offset(9, 20),
          Offset(4, 20),
          Offset(4, 15),
        ]);
        canvas.drawRect(const Rect.fromLTWH(7, 7, 4, 4), line);
        canvas.drawRect(const Rect.fromLTWH(13, 7, 4, 4), line);
        canvas.drawRect(const Rect.fromLTWH(7, 13, 4, 4), line);
        canvas.drawRect(const Rect.fromLTWH(13, 13, 2, 2), line);
        canvas.drawCircle(const Offset(17.5, 17.5), 0.9, solid);
        break;
      case ChatyGlyph.devices:
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            const Rect.fromLTRB(7, 2.7, 17.2, 21.3),
            const Radius.circular(2.1),
          ),
          line,
        );
        stroke(const <Offset>[Offset(9.5, 5.5), Offset(14.7, 5.5)]);
        canvas.drawCircle(const Offset(12.1, 18.5), 0.75, solid);
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            const Rect.fromLTRB(2.4, 8, 6.2, 16.5),
            const Radius.circular(1),
          ),
          line,
        );
        stroke(const <Offset>[Offset(3.5, 10), Offset(5.1, 10)]);
        break;
      case ChatyGlyph.palette:
        final path = Path()
          ..moveTo(12, 3.2)
          ..cubicTo(6.9, 3.2, 3.1, 6.8, 3.1, 11.4)
          ..cubicTo(3.1, 16.3, 7.1, 20.8, 12.1, 20.8)
          ..cubicTo(13.8, 20.8, 14.9, 19.9, 14.9, 18.5)
          ..cubicTo(14.9, 17.5, 14.3, 16.9, 14.3, 16)
          ..cubicTo(14.3, 14.8, 15.2, 14, 16.5, 14)
          ..lineTo(18.4, 14)
          ..cubicTo(20.1, 14, 20.9, 12.6, 20.9, 10.8)
          ..cubicTo(20.9, 6.3, 16.8, 3.2, 12, 3.2)
          ..close();
        canvas.drawPath(path, line);
        canvas.drawCircle(const Offset(7.5, 10), 1.05, solid);
        canvas.drawCircle(const Offset(11.5, 7.4), 1.05, solid);
        canvas.drawCircle(const Offset(16, 8.3), 1.05, solid);
        canvas.drawCircle(const Offset(8.8, 14.2), 1.05, solid);
        break;
      case ChatyGlyph.templates:
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            const Rect.fromLTRB(3.2, 3.2, 20.8, 20.8),
            const Radius.circular(2.5),
          ),
          line,
        );
        stroke(const <Offset>[Offset(3.8, 9), Offset(20.2, 9)]);
        stroke(const <Offset>[Offset(9, 9.5), Offset(9, 20.2)]);
        stroke(const <Offset>[Offset(14.8, 9.5), Offset(14.8, 20.2)]);
        stroke(const <Offset>[Offset(5.5, 6.1), Offset(7, 6.1)]);
        stroke(const <Offset>[Offset(10.8, 6.1), Offset(12.3, 6.1)]);
        break;
      case ChatyGlyph.homeLayout:
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            const Rect.fromLTRB(3.1, 3.5, 20.9, 20.5),
            const Radius.circular(2.5),
          ),
          line,
        );
        stroke(const <Offset>[Offset(8.6, 3.8), Offset(8.6, 20.2)]);
        stroke(const <Offset>[Offset(9.2, 9), Offset(20.4, 9)]);
        stroke(const <Offset>[Offset(9.2, 14), Offset(20.4, 14)]);
        canvas.drawCircle(const Offset(5.9, 7), 0.75, solid);
        break;
      case ChatyGlyph.navigation:
        canvas.drawCircle(const Offset(5, 6), 1.1, solid);
        canvas.drawCircle(const Offset(5, 12), 1.1, solid);
        canvas.drawCircle(const Offset(5, 18), 1.1, solid);
        stroke(const <Offset>[Offset(9, 6), Offset(20, 6)]);
        stroke(const <Offset>[Offset(9, 12), Offset(20, 12)]);
        stroke(const <Offset>[Offset(9, 18), Offset(20, 18)]);
        break;
      case ChatyGlyph.settings:
        stroke(const <Offset>[Offset(4, 7), Offset(20, 7)]);
        stroke(const <Offset>[Offset(4, 12), Offset(20, 12)]);
        stroke(const <Offset>[Offset(4, 17), Offset(20, 17)]);
        canvas.drawCircle(const Offset(9, 7), 2, solid);
        canvas.drawCircle(const Offset(15, 12), 2, solid);
        canvas.drawCircle(const Offset(8, 17), 2, solid);
        canvas.drawCircle(const Offset(9, 7), 0.7, Paint()..color = color.withValues(alpha: 0.2));
        canvas.drawCircle(const Offset(15, 12), 0.7, Paint()..color = color.withValues(alpha: 0.2));
        canvas.drawCircle(const Offset(8, 17), 0.7, Paint()..color = color.withValues(alpha: 0.2));
        break;
      case ChatyGlyph.chevronRight:
        stroke(const <Offset>[
          Offset(9, 4),
          Offset(17, 12),
          Offset(9, 20),
        ]);
        break;
      case ChatyGlyph.more:
        canvas.drawCircle(const Offset(5.2, 12), 1.45, solid);
        canvas.drawCircle(const Offset(12, 12), 1.45, solid);
        canvas.drawCircle(const Offset(18.8, 12), 1.45, solid);
        break;
      case ChatyGlyph.tune:
        stroke(const <Offset>[Offset(3, 6), Offset(21, 6)]);
        stroke(const <Offset>[Offset(3, 12), Offset(21, 12)]);
        stroke(const <Offset>[Offset(3, 18), Offset(21, 18)]);
        canvas.drawCircle(const Offset(8, 6), 1.8, solid);
        canvas.drawCircle(const Offset(15, 12), 1.8, solid);
        canvas.drawCircle(const Offset(10.5, 18), 1.8, solid);
        break;
      case ChatyGlyph.search:
        canvas.drawCircle(const Offset(10.5, 10.5), 6.2, line);
        stroke(const <Offset>[Offset(15, 15), Offset(20.5, 20.5)]);
        break;
      case ChatyGlyph.close:
        stroke(const <Offset>[Offset(6, 6), Offset(18, 18)]);
        stroke(const <Offset>[Offset(18, 6), Offset(6, 18)]);
        break;
      case ChatyGlyph.exportProfile:
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            const Rect.fromLTRB(4, 10, 20, 21),
            const Radius.circular(2),
          ),
          line,
        );
        stroke(const <Offset>[Offset(12, 16), Offset(12, 3)]);
        stroke(const <Offset>[Offset(7.5, 7.5), Offset(12, 3), Offset(16.5, 7.5)]);
        break;
      case ChatyGlyph.importProfile:
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            const Rect.fromLTRB(4, 10, 20, 21),
            const Radius.circular(2),
          ),
          line,
        );
        stroke(const <Offset>[Offset(12, 3), Offset(12, 16)]);
        stroke(const <Offset>[Offset(7.5, 11.5), Offset(12, 16), Offset(16.5, 11.5)]);
        break;
      case ChatyGlyph.reset:
        final resetPath = Path()
          ..moveTo(19, 8)
          ..cubicTo(16.2, 3.5, 9.9, 2.7, 6, 6.5)
          ..cubicTo(2.2, 10.2, 3.2, 16.7, 7.5, 19.2)
          ..cubicTo(11.1, 21.4, 16.1, 20, 18.2, 17);
        canvas.drawPath(resetPath, line);
        stroke(const <Offset>[Offset(18.5, 4), Offset(19, 8), Offset(15, 8)]);
        break;
      case ChatyGlyph.chatBubble:
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            const Rect.fromLTRB(3, 3.5, 21, 17),
            const Radius.circular(4),
          ),
          line,
        );
        stroke(const <Offset>[Offset(7, 17), Offset(6, 21), Offset(12, 17)]);
        stroke(const <Offset>[Offset(7, 8), Offset(17, 8)]);
        stroke(const <Offset>[Offset(7, 12), Offset(14, 12)]);
        break;
      case ChatyGlyph.person:
        canvas.drawCircle(const Offset(12, 7.5), 3.5, line);
        final shoulders = Path()
          ..moveTo(4, 20)
          ..cubicTo(4.7, 15.8, 8, 13.8, 12, 13.8)
          ..cubicTo(16, 13.8, 19.3, 15.8, 20, 20);
        canvas.drawPath(shoulders, line);
        break;
      case ChatyGlyph.list:
        for (final y in <double>[6, 12, 18]) {
          canvas.drawCircle(Offset(4.5, y), 1.1, solid);
          stroke(<Offset>[Offset(8, y), Offset(20, y)]);
        }
        break;
      case ChatyGlyph.updates:
        canvas.drawCircle(const Offset(12, 12), 8.3, line);
        canvas.drawCircle(const Offset(12, 12), 5.8, line);
        canvas.drawCircle(const Offset(18, 6), 2.0, solid);
        break;
      case ChatyGlyph.calls:
        final handset = Path()
          ..moveTo(7, 3.5)
          ..lineTo(4.8, 5.7)
          ..cubicTo(4.2, 6.4, 5.2, 10.8, 9.2, 14.8)
          ..cubicTo(13.2, 18.8, 17.6, 19.8, 18.3, 19.2)
          ..lineTo(20.5, 17)
          ..lineTo(16.2, 13.8)
          ..lineTo(13.9, 15.5)
          ..cubicTo(11.8, 14.6, 9.4, 12.2, 8.5, 10.1)
          ..lineTo(10.2, 7.8)
          ..close();
        canvas.drawPath(handset, line);
        break;
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _ChatyGlyphPainter oldDelegate) =>
      oldDelegate.glyph != glyph ||
      oldDelegate.color != color ||
      oldDelegate.strokeWidth != strokeWidth;
}
