import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../../../domain/models/chat_message.dart';
import 'delivery_icon_style.dart';

/// Renders vectorized status icons for every selectable tick style and preserves real delivery states.
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
      // Stock: classic messaging ticks. One check when sent, two overlapping
      // checks when delivered or read (Chaty-drawn vector, not GB art).
      case DeliveryIconStyle.stock:
        paint.strokeWidth = 1.5;
        final stP1 = Path()
          ..moveTo(size.width * 0.10, size.height * 0.54)
          ..lineTo(size.width * 0.30, size.height * 0.72)
          ..lineTo(size.width * 0.66, size.height * 0.30);
        canvas.drawPath(stP1, paint);
        if (isDelivered || isRead) {
          final stP2 = Path()
            ..moveTo(size.width * 0.44, size.height * 0.64)
            ..lineTo(size.width * 0.50, size.height * 0.72)
            ..lineTo(size.width * 0.90, size.height * 0.30);
          canvas.drawPath(stP2, paint);
        }
        break;

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
        final rcP1 = Path()
          ..moveTo(size.width * 0.12, size.height * 0.52)
          ..lineTo(size.width * 0.34, size.height * 0.74)
          ..lineTo(size.width * 0.68, size.height * 0.32);
        canvas.drawPath(rcP1, paint);
        if (isDelivered || isRead) {
          final rcP2 = Path()
            ..moveTo(size.width * 0.38, size.height * 0.52)
            ..lineTo(size.width * 0.60, size.height * 0.74)
            ..lineTo(size.width * 0.94, size.height * 0.32);
          canvas.drawPath(rcP2, paint);
        }
        break;

      // iOS Ticks: a slimmer, smooth double-check treatment distinct from RC iOS 11.
      case DeliveryIconStyle.ios2:
        paint.strokeWidth = 1.25;
        final iosP1 = Path()
          ..moveTo(size.width * 0.08, size.height * 0.52)
          ..lineTo(size.width * 0.30, size.height * 0.72)
          ..lineTo(size.width * 0.60, size.height * 0.34);
        canvas.drawPath(iosP1, paint);
        if (isDelivered || isRead) {
          final iosP2 = Path()
            ..moveTo(size.width * 0.36, size.height * 0.52)
            ..lineTo(size.width * 0.58, size.height * 0.72)
            ..lineTo(size.width * 0.91, size.height * 0.30);
          canvas.drawPath(iosP2, paint);
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

      // Dedicated settings presets. Each has its own shape painter so the
      // visible selector label matches the indicator used beside every message.
      case DeliveryIconStyle.wings:
        final wingPath = Path()
          ..moveTo(size.width * 0.5, size.height * 0.46)
          ..quadraticBezierTo(
            size.width * 0.27,
            size.height * 0.08,
            size.width * 0.08,
            size.height * 0.22,
          )
          ..lineTo(size.width * 0.18, size.height * 0.66)
          ..lineTo(size.width * 0.36, size.height * 0.54)
          ..lineTo(size.width * 0.5, size.height * 0.80)
          ..lineTo(size.width * 0.64, size.height * 0.54)
          ..lineTo(size.width * 0.82, size.height * 0.66)
          ..lineTo(size.width * 0.92, size.height * 0.22)
          ..quadraticBezierTo(
            size.width * 0.73,
            size.height * 0.08,
            size.width * 0.5,
            size.height * 0.46,
          )
          ..close();
        canvas.drawPath(wingPath, isRead ? fillPaint : paint);
        break;

      case DeliveryIconStyle.trafficLights:
        final trafficCount = isRead
            ? 3
            : isDelivered
            ? 2
            : 1;
        for (var i = 0; i < trafficCount; i++) {
          canvas.drawCircle(
            Offset(size.width * (0.2 + i * 0.3), size.height / 2),
            size.width * 0.105,
            fillPaint,
          );
        }
        break;

      case DeliveryIconStyle.stars:
        final starCount = (isDelivered || isRead) ? 2 : 1;
        for (var i = 0; i < starCount; i++) {
          final center = starCount == 1
              ? Offset(size.width / 2, size.height / 2)
              : Offset(size.width * (i == 0 ? 0.32 : 0.72), size.height / 2);
          canvas.drawPath(
            _starPath(center, size.width * (starCount == 1 ? 0.28 : 0.23)),
            fillPaint,
          );
        }
        break;

      case DeliveryIconStyle.hearts:
        final heartCount = (isDelivered || isRead) ? 2 : 1;
        for (var i = 0; i < heartCount; i++) {
          final center = heartCount == 1
              ? Offset(size.width / 2, size.height * 0.52)
              : Offset(size.width * (i == 0 ? 0.30 : 0.70), size.height * 0.52);
          canvas.drawPath(
            _heartPath(center, size.width * (heartCount == 1 ? 0.24 : 0.20)),
            fillPaint,
          );
        }
        break;

      case DeliveryIconStyle.batman:
        final batPath = _batmanPath(
          Rect.fromLTWH(
            size.width * 0.05,
            size.height * 0.20,
            size.width * 0.90,
            size.height * 0.62,
          ),
        );
        canvas.drawPath(batPath, isRead ? fillPaint : paint);
        break;

      case DeliveryIconStyle.doubleSword:
        final swordPaint = Paint()
          ..color = activeColor
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.55
          ..strokeCap = StrokeCap.round
          ..strokeJoin = StrokeJoin.round;
        final swords = Path()
          ..moveTo(size.width * 0.22, size.height * 0.16)
          ..lineTo(size.width * 0.76, size.height * 0.78)
          ..moveTo(size.width * 0.16, size.height * 0.27)
          ..lineTo(size.width * 0.30, size.height * 0.20)
          ..moveTo(size.width * 0.70, size.height * 0.73)
          ..lineTo(size.width * 0.82, size.height * 0.64)
          ..moveTo(size.width * 0.78, size.height * 0.16)
          ..lineTo(size.width * 0.24, size.height * 0.78)
          ..moveTo(size.width * 0.70, size.height * 0.20)
          ..lineTo(size.width * 0.84, size.height * 0.28)
          ..moveTo(size.width * 0.18, size.height * 0.65)
          ..lineTo(size.width * 0.30, size.height * 0.75);
        canvas.drawPath(swords, swordPaint);
        break;

      case DeliveryIconStyle.glowingDots:
        final glowingCount = isRead
            ? 3
            : isDelivered
            ? 2
            : 1;
        for (var i = 0; i < glowingCount; i++) {
          final center = Offset(size.width * (0.2 + i * 0.3), size.height / 2);
          canvas.drawCircle(
            center,
            size.width * 0.16,
            Paint()
              ..color = activeColor.withValues(alpha: 0.42)
              ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 2.2),
          );
          canvas.drawCircle(center, size.width * 0.085, fillPaint);
        }
        break;

      case DeliveryIconStyle.material3Rounded:
        final shape = RRect.fromRectAndRadius(
          Rect.fromLTWH(
            size.width * 0.08,
            size.height * 0.17,
            size.width * 0.84,
            size.height * 0.66,
          ),
          Radius.circular(size.height * 0.30),
        );
        canvas.drawRRect(shape, isRead ? fillPaint : paint);
        final checkPaint = Paint()
          ..color = isRead ? Colors.white : activeColor
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.3
          ..strokeCap = StrokeCap.round
          ..strokeJoin = StrokeJoin.round;
        final materialCheck = Path()
          ..moveTo(size.width * 0.28, size.height * 0.50)
          ..lineTo(size.width * 0.43, size.height * 0.63)
          ..lineTo(size.width * 0.70, size.height * 0.36);
        canvas.drawPath(materialCheck, checkPaint);
        break;

      // GB reference tick styles: original Chaty vector interpretations of the
      // GB picker designs (GB ships bitmap art; these are re-drawn glyphs in
      // the same spirit, colored by delivery state like every Chaty tick).
      case DeliveryIconStyle.allo:
      case DeliveryIconStyle.alien:
      case DeliveryIconStyle.altCrayonV2:
      case DeliveryIconStyle.altCrayon:
      case DeliveryIconStyle.aran:
      case DeliveryIconStyle.bar:
      case DeliveryIconStyle.bat:
      case DeliveryIconStyle.bbm:
      case DeliveryIconStyle.blend:
      case DeliveryIconStyle.ngAndroidBot:
      case DeliveryIconStyle.bpgCircle:
      case DeliveryIconStyle.gbCircles:
      case DeliveryIconStyle.samyadeepCircles:
      case DeliveryIconStyle.coalfield:
      case DeliveryIconStyle.colorSimple:
      case DeliveryIconStyle.ctoon:
      case DeliveryIconStyle.eclipsis:
      case DeliveryIconStyle.faceSwitch:
      case DeliveryIconStyle.feet:
      case DeliveryIconStyle.flatCircles:
      case DeliveryIconStyle.gifcon:
      case DeliveryIconStyle.googleV2:
      case DeliveryIconStyle.google:
      case DeliveryIconStyle.grw:
      case DeliveryIconStyle.sidzyHd:
      case DeliveryIconStyle.svHeart:
      case DeliveryIconStyle.hike:
      case DeliveryIconStyle.inbox:
      case DeliveryIconStyle.insect:
      case DeliveryIconStyle.gbIos:
      case DeliveryIconStyle.jalopy:
      case DeliveryIconStyle.jessy:
      case DeliveryIconStyle.joaquins:
      case DeliveryIconStyle.jokerTraffic:
      case DeliveryIconStyle.letter:
      case DeliveryIconStyle.like:
      case DeliveryIconStyle.loveSwitch:
      case DeliveryIconStyle.materialV2:
      case DeliveryIconStyle.materialSwitch:
      case DeliveryIconStyle.messenger:
      case DeliveryIconStyle.mickeyHands:
      case DeliveryIconStyle.minions:
      case DeliveryIconStyle.newWaca:
      case DeliveryIconStyle.neyHeart:
      case DeliveryIconStyle.nhsc:
      case DeliveryIconStyle.nazaNh:
      case DeliveryIconStyle.nlCircle:
      case DeliveryIconStyle.oldWaca:
      case DeliveryIconStyle.pacman:
      case DeliveryIconStyle.pixelated:
      case DeliveryIconStyle.pokemon:
      case DeliveryIconStyle.quinn:
      case DeliveryIconStyle.snapChat:
      case DeliveryIconStyle.sDot:
      case DeliveryIconStyle.seasons:
      case DeliveryIconStyle.smile:
      case DeliveryIconStyle.star:
      case DeliveryIconStyle.flatSwitch:
      case DeliveryIconStyle.timeBomb:
      case DeliveryIconStyle.jokerTraffic2:
      case DeliveryIconStyle.treble:
      case DeliveryIconStyle.twitterConcept:
      case DeliveryIconStyle.wbg:
      case DeliveryIconStyle.x:
      case DeliveryIconStyle.pointer:
      case DeliveryIconStyle.ticTicCircle2:
      case DeliveryIconStyle.iceCream:
      case DeliveryIconStyle.pony:
      case DeliveryIconStyle.kitty:
      case DeliveryIconStyle.flower:
        _paintGbStyle(
          canvas,
          size,
          paint,
          fillPaint,
          activeColor,
          isRead,
          isDelivered,
        );
        break;
    }
  }

  Path _starPath(Offset center, double radius) {
    final path = Path();
    for (var i = 0; i < 10; i++) {
      final angle = -math.pi / 2 + i * math.pi / 5;
      final r = i.isEven ? radius : radius * 0.44;
      final point = Offset(
        center.dx + math.cos(angle) * r,
        center.dy + math.sin(angle) * r,
      );
      if (i == 0) {
        path.moveTo(point.dx, point.dy);
      } else {
        path.lineTo(point.dx, point.dy);
      }
    }
    return path..close();
  }

  // --- GB reference tick glyph helpers --------------------------------------
  // All glyphs below are original Chaty vector drawings. They reference the
  // composition of the GB picker's bitmap icons (badge vs switch vs letter,
  // single vs double element) without copying its art. State semantics match
  // every other Chaty tick: [isDelivered] adds the second/full element and
  // [isRead] switches to the read color (and fills where the design has a
  // filled state).

  void _gbCheck(Canvas canvas, double cx, double cy, double sc, Paint p) {
    final path = Path()
      ..moveTo(cx - 3.4 * sc, cy + 0.2 * sc)
      ..lineTo(cx - 0.8 * sc, cy + 2.6 * sc)
      ..lineTo(cx + 4.0 * sc, cy - 2.8 * sc);
    canvas.drawPath(path, p);
  }

  void _gbCircle(Canvas canvas, Offset c, double r, Paint p) =>
      canvas.drawCircle(c, r, p);

  void _gbLetter(
    Canvas canvas,
    Size size,
    String letter,
    Color color, {
    double? fontSize,
  }) {
    final tp = TextPainter(
      text: TextSpan(
        text: letter,
        style: TextStyle(
          color: color,
          fontSize: fontSize ?? size.height * 0.62,
          fontWeight: FontWeight.w700,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(
      canvas,
      Offset(size.width / 2 - tp.width / 2, size.height / 2 - tp.height / 2),
    );
  }

  void _gbSmiley(
    Canvas canvas,
    Offset c,
    double r,
    Paint stroke,
    Paint fill, {
    bool heartEyes = false,
  }) {
    canvas.drawCircle(c, r, stroke);
    if (heartEyes) {
      final e = _heartPath(Offset(c.dx - r * 0.38, c.dy - r * 0.25), r * 0.20);
      final e2 = _heartPath(Offset(c.dx + r * 0.38, c.dy - r * 0.25), r * 0.20);
      canvas.drawPath(e, fill);
      canvas.drawPath(e2, fill);
    } else {
      canvas.drawCircle(
        Offset(c.dx - r * 0.35, c.dy - r * 0.25),
        r * 0.12,
        fill,
      );
      canvas.drawCircle(
        Offset(c.dx + r * 0.35, c.dy - r * 0.25),
        r * 0.12,
        fill,
      );
    }
    final smile = Path()
      ..moveTo(c.dx - r * 0.45, c.dy + r * 0.25)
      ..quadraticBezierTo(
        c.dx,
        c.dy + r * 0.65,
        c.dx + r * 0.45,
        c.dy + r * 0.25,
      );
    canvas.drawPath(smile, stroke);
  }

  void _gbCapsule(
    Canvas canvas,
    Size size,
    Paint stroke,
    Paint fill,
    bool knobEnd,
  ) {
    final rect = RRect.fromRectAndRadius(
      Rect.fromLTWH(
        size.width * 0.06,
        size.height * 0.26,
        size.width * 0.88,
        size.height * 0.48,
      ),
      Radius.circular(size.height * 0.24),
    );
    canvas.drawRRect(rect, stroke);
    final knobX = knobEnd ? size.width * 0.74 : size.width * 0.26;
    canvas.drawCircle(Offset(knobX, size.height / 2), size.height * 0.17, fill);
  }

  void _paintGbStyle(
    Canvas canvas,
    Size size,
    Paint paint,
    Paint fillPaint,
    Color activeColor,
    bool isRead,
    bool isDelivered,
  ) {
    final w = size.width;
    final h = size.height;
    final c = Offset(w / 2, h / 2);
    final r = math.min(w, h) / 2 - 1.5;
    final white = Paint()
      ..color = Colors.white
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.4
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final whiteFill = Paint()
      ..color = Colors.white
      ..style = PaintingStyle.fill;
    final full = isDelivered || isRead;

    switch (style) {
      case DeliveryIconStyle.allo:
        paint.strokeWidth = 2.6;
        _gbCheck(canvas, w * 0.36, h * 0.50, 1.0, paint);
        if (full) _gbCheck(canvas, w * 0.62, h * 0.50, 1.0, paint);
        break;
      case DeliveryIconStyle.alien:
        _gbCircle(canvas, Offset(w * 0.34, h * 0.5), 3.2, fillPaint);
        if (full) _gbCircle(canvas, Offset(w * 0.66, h * 0.5), 3.2, fillPaint);
        break;
      case DeliveryIconStyle.altCrayonV2:
        paint.strokeWidth = 2.0;
        _gbCheck(canvas, w * 0.36, h * 0.50, 0.9, paint);
        if (full) _gbCheck(canvas, w * 0.60, h * 0.50, 0.9, paint);
        break;
      case DeliveryIconStyle.altCrayon:
        paint.strokeWidth = 1.2;
        _gbCheck(canvas, w * 0.34, h * 0.50, 1.0, paint);
        if (full) _gbCheck(canvas, w * 0.64, h * 0.50, 1.0, paint);
        break;
      case DeliveryIconStyle.aran:
        _gbCapsule(canvas, size, paint, fillPaint, isRead);
        break;
      case DeliveryIconStyle.bar:
        for (var i = 0; i < 4; i++) {
          final bh = h * (0.22 + 0.17 * i);
          final filled = isRead || (isDelivered && i < 2) || i == 0;
          canvas.drawRect(
            Rect.fromLTWH(w * (0.12 + 0.22 * i), h * 0.85 - bh, w * 0.14, bh),
            filled ? fillPaint : paint,
          );
        }
        break;
      case DeliveryIconStyle.bat:
        canvas.drawPath(
          _batmanPath(
            Rect.fromCenter(center: c, width: w * 0.8, height: h * 0.6),
          ),
          full ? fillPaint : paint,
        );
        break;
      case DeliveryIconStyle.bbm:
        _gbCircle(canvas, c, r, isRead ? fillPaint : paint);
        _gbLetter(canvas, size, 'R', isRead ? Colors.white : activeColor);
        break;
      case DeliveryIconStyle.blend:
        final rect = RRect.fromRectAndRadius(
          Rect.fromLTWH(w * 0.06, h * 0.30, w * 0.88, h * 0.40),
          Radius.circular(h * 0.12),
        );
        canvas.drawRRect(rect, paint);
        for (var i = 0; i < 3; i++) {
          canvas.drawLine(
            Offset(w * (0.20 + 0.10 * i), h * 0.36),
            Offset(w * (0.20 + 0.10 * i), h * 0.64),
            paint,
          );
        }
        if (full) _gbCheck(canvas, w * 0.66, h * 0.50, 0.9, paint);
        break;
      case DeliveryIconStyle.ngAndroidBot:
        final body = RRect.fromRectAndRadius(
          Rect.fromLTWH(w * 0.22, h * 0.30, w * 0.56, h * 0.46),
          Radius.circular(w * 0.16),
        );
        canvas.drawRRect(body, isRead ? fillPaint : paint);
        canvas.drawLine(
          Offset(w * 0.32, h * 0.30),
          Offset(w * 0.24, h * 0.16),
          paint,
        );
        canvas.drawLine(
          Offset(w * 0.68, h * 0.30),
          Offset(w * 0.76, h * 0.16),
          paint,
        );
        final eyePaint = isRead ? whiteFill : fillPaint;
        canvas.drawCircle(Offset(w * 0.38, h * 0.48), 1.6, eyePaint);
        canvas.drawCircle(Offset(w * 0.62, h * 0.48), 1.6, eyePaint);
        break;
      case DeliveryIconStyle.bpgCircle:
        canvas.drawArc(
          Rect.fromCircle(center: Offset(w * 0.34, h * 0.5), radius: 5),
          0.6,
          5.0,
          false,
          paint,
        );
        canvas.drawArc(
          Rect.fromCircle(center: Offset(w * 0.60, h * 0.5), radius: 5),
          0.6,
          5.0,
          false,
          paint,
        );
        if (full) _gbCheck(canvas, w * 0.74, h * 0.5, 0.8, paint);
        break;
      case DeliveryIconStyle.gbCircles:
        _gbCircle(canvas, Offset(w * 0.34, h * 0.5), 4.5, fillPaint);
        _gbCircle(
          canvas,
          Offset(w * 0.68, h * 0.5),
          4.5,
          full ? fillPaint : paint,
        );
        break;
      case DeliveryIconStyle.samyadeepCircles:
        _gbCircle(canvas, c, r, paint);
        final bolt = Path()
          ..moveTo(w * 0.56, h * 0.18)
          ..lineTo(w * 0.34, h * 0.56)
          ..lineTo(w * 0.50, h * 0.56)
          ..lineTo(w * 0.44, h * 0.84)
          ..lineTo(w * 0.68, h * 0.46)
          ..lineTo(w * 0.52, h * 0.46)
          ..close();
        canvas.drawPath(bolt, full ? fillPaint : paint);
        break;
      case DeliveryIconStyle.coalfield:
        canvas.drawLine(
          Offset(w * 0.34, h * 0.24),
          Offset(w * 0.22, h * 0.76),
          paint..strokeWidth = 2.4,
        );
        if (full) {
          canvas.drawLine(
            Offset(w * 0.62, h * 0.24),
            Offset(w * 0.50, h * 0.76),
            paint,
          );
        }
        break;
      case DeliveryIconStyle.colorSimple:
        _gbCircle(canvas, c, 5.0, fillPaint);
        break;
      case DeliveryIconStyle.ctoon:
        final rect = RRect.fromRectAndRadius(
          Rect.fromLTWH(w * 0.12, h * 0.24, w * 0.76, h * 0.52),
          Radius.circular(h * 0.16),
        );
        canvas.drawRRect(rect, isRead ? fillPaint : paint);
        _gbSmiley(
          canvas,
          c,
          r * 0.55,
          isRead ? white : paint,
          isRead ? whiteFill : fillPaint,
        );
        break;
      case DeliveryIconStyle.eclipsis:
        canvas.drawLine(
          Offset(w * 0.24, h * 0.24),
          Offset(w * 0.24, h * 0.76),
          paint,
        );
        canvas.drawLine(
          Offset(w * 0.76, h * 0.24),
          Offset(w * 0.76, h * 0.76),
          paint,
        );
        canvas.drawLine(
          Offset(w * 0.24, h * 0.24),
          Offset(w * 0.38, h * 0.24),
          paint,
        );
        canvas.drawLine(
          Offset(w * 0.76, h * 0.24),
          Offset(w * 0.62, h * 0.24),
          paint,
        );
        canvas.drawLine(
          Offset(w * 0.24, h * 0.76),
          Offset(w * 0.38, h * 0.76),
          paint,
        );
        canvas.drawLine(
          Offset(w * 0.76, h * 0.76),
          Offset(w * 0.62, h * 0.76),
          paint,
        );
        if (full) _gbCheck(canvas, w * 0.50, h * 0.5, 0.8, paint);
        break;
      case DeliveryIconStyle.faceSwitch:
        _gbCapsule(canvas, size, paint, fillPaint, isRead);
        _gbSmiley(
          canvas,
          Offset(isRead ? w * 0.74 : w * 0.26, h / 2),
          h * 0.13,
          paint,
          fillPaint,
        );
        break;
      case DeliveryIconStyle.feet:
        canvas.drawOval(
          Rect.fromCenter(
            center: Offset(w * 0.36, h * 0.42),
            width: w * 0.20,
            height: h * 0.34,
          ),
          fillPaint,
        );
        canvas.drawOval(
          Rect.fromCenter(
            center: Offset(w * 0.62, h * 0.58),
            width: w * 0.20,
            height: h * 0.34,
          ),
          full ? fillPaint : paint,
        );
        break;
      case DeliveryIconStyle.flatCircles:
        _gbCircle(canvas, c, r, isRead ? fillPaint : paint);
        _gbCheck(canvas, w * 0.5, h * 0.5, 1.1, isRead ? white : paint);
        break;
      case DeliveryIconStyle.gifcon:
        _gbCircle(canvas, c, r, paint);
        _gbCircle(canvas, c, r * 0.72, isRead ? fillPaint : paint);
        _gbCheck(canvas, w * 0.5, h * 0.5, 0.9, isRead ? white : paint);
        break;
      case DeliveryIconStyle.googleV2:
        _gbLetter(canvas, size, 'G', activeColor);
        if (full) _gbCheck(canvas, w * 0.70, h * 0.66, 0.7, paint);
        break;
      case DeliveryIconStyle.google:
        _gbLetter(canvas, size, 'G', activeColor);
        break;
      case DeliveryIconStyle.grw:
        paint.strokeWidth = 2.2;
        final p1 = Path()
          ..moveTo(w * 0.08, h * 0.52)
          ..quadraticBezierTo(w * 0.24, h * 0.78, w * 0.34, h * 0.60)
          ..lineTo(w * 0.58, h * 0.24);
        canvas.drawPath(p1, paint);
        if (full) {
          final p2 = Path()
            ..moveTo(w * 0.42, h * 0.52)
            ..quadraticBezierTo(w * 0.58, h * 0.78, w * 0.68, h * 0.60)
            ..lineTo(w * 0.92, h * 0.24);
          canvas.drawPath(p2, paint);
        }
        break;
      case DeliveryIconStyle.sidzyHd:
        _gbCircle(canvas, c, r, isRead ? fillPaint : paint);
        _gbCheck(canvas, w * 0.5, h * 0.5, 1.1, isRead ? white : paint);
        break;
      case DeliveryIconStyle.svHeart:
        canvas.drawPath(_heartPath(c, r * 0.85), full ? fillPaint : paint);
        break;
      case DeliveryIconStyle.hike:
        paint.strokeWidth = 2.0;
        _gbCheck(canvas, w * 0.36, h * 0.5, 0.9, paint);
        _gbLetter(canvas, size, 'R', activeColor, fontSize: h * 0.4);
        break;
      case DeliveryIconStyle.inbox:
        final env = Rect.fromLTWH(w * 0.14, h * 0.26, w * 0.72, h * 0.48);
        canvas.drawRect(env, isRead ? fillPaint : paint);
        final flap = Path()
          ..moveTo(w * 0.14, h * 0.26)
          ..lineTo(w * 0.5, h * 0.56)
          ..lineTo(w * 0.86, h * 0.26);
        canvas.drawPath(flap, isRead ? white : paint);
        break;
      case DeliveryIconStyle.insect:
        _gbCircle(canvas, c, r * 0.7, isRead ? fillPaint : paint);
        for (var i = -1; i <= 1; i++) {
          canvas.drawLine(
            Offset(w * 0.5 + i * w * 0.22, h * 0.26),
            Offset(w * 0.5 + i * w * 0.34, h * 0.10),
            paint,
          );
          canvas.drawLine(
            Offset(w * 0.5 + i * w * 0.22, h * 0.74),
            Offset(w * 0.5 + i * w * 0.34, h * 0.90),
            paint,
          );
        }
        if (full)
          _gbCheck(canvas, w * 0.5, h * 0.52, 0.8, isRead ? white : paint);
        break;
      case DeliveryIconStyle.gbIos:
        paint.strokeWidth = 1.8;
        _gbCheck(canvas, w * 0.34, h * 0.5, 1.0, paint);
        if (full) _gbCheck(canvas, w * 0.62, h * 0.5, 1.0, paint);
        break;
      case DeliveryIconStyle.jalopy:
        _gbSmiley(canvas, c, r, paint, fillPaint, heartEyes: isRead);
        break;
      case DeliveryIconStyle.jessy:
        _gbCapsule(canvas, size, paint, fillPaint, isRead);
        break;
      case DeliveryIconStyle.joaquins:
        paint.strokeWidth = 2.4;
        _gbCheck(canvas, w * 0.30, h * 0.54, 0.9, paint);
        _gbCheck(
          canvas,
          w * 0.56,
          h * 0.54,
          0.9,
          full ? paint : paint
            ..color = activeColor.withValues(alpha: 0.45),
        );
        break;
      case DeliveryIconStyle.jokerTraffic:
      case DeliveryIconStyle.jokerTraffic2:
        for (var i = 0; i < 3; i++) {
          final on = isRead || (isDelivered && i < 2) || i == 0;
          _gbCircle(
            canvas,
            Offset(w * (0.26 + 0.24 * i), h * 0.5),
            3.6,
            on ? fillPaint : paint,
          );
        }
        break;
      case DeliveryIconStyle.letter:
        _gbLetter(canvas, size, 'R', activeColor);
        break;
      case DeliveryIconStyle.like:
        final thumb = Path()
          ..moveTo(w * 0.30, h * 0.82)
          ..lineTo(w * 0.30, h * 0.42)
          ..lineTo(w * 0.46, h * 0.40)
          ..lineTo(w * 0.56, h * 0.16)
          ..quadraticBezierTo(w * 0.68, h * 0.16, w * 0.64, h * 0.34)
          ..lineTo(w * 0.80, h * 0.36)
          ..quadraticBezierTo(w * 0.90, h * 0.38, w * 0.86, h * 0.50)
          ..lineTo(w * 0.80, h * 0.80)
          ..close();
        canvas.drawPath(thumb, isRead ? fillPaint : paint);
        canvas.drawRect(
          Rect.fromLTWH(w * 0.14, h * 0.42, w * 0.12, h * 0.40),
          isRead ? fillPaint : paint,
        );
        break;
      case DeliveryIconStyle.loveSwitch:
        _gbCapsule(canvas, size, paint, fillPaint, isRead);
        canvas.drawPath(
          _heartPath(Offset(isRead ? w * 0.74 : w * 0.26, h / 2), h * 0.10),
          fillPaint,
        );
        break;
      case DeliveryIconStyle.materialV2:
        _gbCircle(
          canvas,
          Offset(w * 0.34, h * 0.5),
          r * 0.62,
          isRead ? fillPaint : paint,
        );
        _gbCheck(canvas, w * 0.34, h * 0.5, 0.8, isRead ? white : paint);
        for (var i = 0; i < 3; i++) {
          canvas.drawLine(
            Offset(w * 0.66, h * (0.34 + 0.14 * i)),
            Offset(w * 0.88, h * (0.34 + 0.14 * i)),
            paint,
          );
        }
        break;
      case DeliveryIconStyle.materialSwitch:
        _gbCapsule(canvas, size, paint, fillPaint, isRead);
        _gbCheck(canvas, isRead ? w * 0.74 : w * 0.26, h / 2, 0.6, paint);
        break;
      case DeliveryIconStyle.messenger:
        _gbCircle(canvas, c, r, isRead ? fillPaint : paint);
        _gbCheck(canvas, w * 0.5, h * 0.5, 1.1, isRead ? white : paint);
        break;
      case DeliveryIconStyle.mickeyHands:
        // Original mitten thumbs-up (not the licensed character art).
        final mitt = Path()
          ..moveTo(w * 0.34, h * 0.84)
          ..lineTo(w * 0.34, h * 0.46)
          ..quadraticBezierTo(w * 0.34, h * 0.30, w * 0.46, h * 0.32)
          ..lineTo(w * 0.52, h * 0.16)
          ..quadraticBezierTo(w * 0.64, h * 0.14, w * 0.62, h * 0.30)
          ..lineTo(w * 0.60, h * 0.40)
          ..lineTo(w * 0.78, h * 0.42)
          ..quadraticBezierTo(w * 0.90, h * 0.46, w * 0.84, h * 0.58)
          ..lineTo(w * 0.78, h * 0.84)
          ..close();
        canvas.drawPath(mitt, isRead ? fillPaint : paint);
        break;
      case DeliveryIconStyle.minions:
        // Original round goggled face (not the licensed character art).
        _gbCircle(canvas, c, r, paint);
        canvas.drawLine(
          Offset(c.dx - r, c.dy - r * 0.15),
          Offset(c.dx + r, c.dy - r * 0.15),
          paint,
        );
        canvas.drawCircle(
          Offset(c.dx - r * 0.32, c.dy - r * 0.15),
          r * 0.24,
          isRead ? fillPaint : paint,
        );
        canvas.drawCircle(
          Offset(c.dx + r * 0.32, c.dy - r * 0.15),
          r * 0.24,
          isRead ? fillPaint : paint,
        );
        final sm = Path()
          ..moveTo(c.dx - r * 0.3, c.dy + r * 0.4)
          ..quadraticBezierTo(
            c.dx,
            c.dy + r * 0.6,
            c.dx + r * 0.3,
            c.dy + r * 0.4,
          );
        canvas.drawPath(sm, paint);
        break;
      case DeliveryIconStyle.newWaca:
        _gbCircle(canvas, Offset(w * 0.24, h * 0.5), 2.6, fillPaint);
        _gbCircle(
          canvas,
          Offset(w * 0.42, h * 0.5),
          2.6,
          full ? fillPaint : paint,
        );
        if (full) _gbCheck(canvas, w * 0.70, h * 0.5, 0.9, paint);
        break;
      case DeliveryIconStyle.neyHeart:
        canvas.drawPath(_heartPath(c, r * 0.85), isRead ? fillPaint : paint);
        break;
      case DeliveryIconStyle.nhsc:
        _gbCircle(canvas, c, r, paint);
        _gbCheck(canvas, w * 0.5, h * 0.5, 1.0, paint);
        break;
      case DeliveryIconStyle.nazaNh:
        _gbCircle(canvas, c, r, isRead ? fillPaint : paint);
        _gbCheck(canvas, w * 0.5, h * 0.5, 1.0, isRead ? white : paint);
        break;
      case DeliveryIconStyle.nlCircle:
        _gbCircle(canvas, c, r, paint..strokeWidth = 2.6);
        if (full) _gbCircle(canvas, c, r * 0.45, fillPaint);
        break;
      case DeliveryIconStyle.oldWaca:
        _gbCapsule(canvas, size, paint, fillPaint, isRead);
        _gbCheck(canvas, isRead ? w * 0.74 : w * 0.5, h / 2, 0.6, paint);
        break;
      case DeliveryIconStyle.pacman:
        final pm = Path()
          ..moveTo(c.dx, c.dy)
          ..arcTo(
            Rect.fromCircle(center: c, radius: r),
            -0.6,
            2 * math.pi - 1.2 * (isRead ? 0.4 : 1),
            true,
          )
          ..close();
        canvas.drawPath(pm, isRead ? fillPaint : paint);
        canvas.drawCircle(Offset(c.dx, c.dy - r * 0.45), 1.5, fillPaint);
        break;
      case DeliveryIconStyle.pixelated:
        final px = w * 0.16;
        for (var i = 0; i < 4; i++) {
          for (var j = 0; j < 4; j++) {
            if ((i + j) % 2 == (isRead ? 1 : 0)) {
              canvas.drawRect(
                Rect.fromLTWH(
                  w * 0.18 + i * px,
                  h * 0.18 + j * px,
                  px * 0.9,
                  px * 0.9,
                ),
                fillPaint,
              );
            }
          }
        }
        break;
      case DeliveryIconStyle.pokemon:
        _gbCircle(canvas, c, r, paint);
        canvas.drawLine(Offset(c.dx - r, c.dy), Offset(c.dx + r, c.dy), paint);
        _gbCircle(canvas, c, r * 0.28, isRead ? fillPaint : paint);
        break;
      case DeliveryIconStyle.quinn:
        canvas.drawRect(
          Rect.fromLTWH(w * 0.14, h * 0.24, w * 0.40, h * 0.40),
          isRead ? fillPaint : paint,
        );
        canvas.drawRect(
          Rect.fromLTWH(w * 0.42, h * 0.38, w * 0.40, h * 0.40),
          isRead ? fillPaint : paint,
        );
        _gbCheck(canvas, w * 0.34, h * 0.44, 0.7, isRead ? white : paint);
        if (full)
          _gbCheck(canvas, w * 0.62, h * 0.58, 0.7, isRead ? white : paint);
        break;
      case DeliveryIconStyle.snapChat:
        // Original rounded ghost with wavy base (not the app logo art).
        final ghost = Path()
          ..moveTo(w * 0.24, h * 0.78)
          ..lineTo(w * 0.24, h * 0.44)
          ..arcToPoint(
            Offset(w * 0.76, h * 0.44),
            radius: Radius.circular(w * 0.26),
          )
          ..lineTo(w * 0.76, h * 0.78)
          ..quadraticBezierTo(w * 0.66, h * 0.68, w * 0.58, h * 0.78)
          ..quadraticBezierTo(w * 0.50, h * 0.68, w * 0.42, h * 0.78)
          ..quadraticBezierTo(w * 0.34, h * 0.68, w * 0.24, h * 0.78)
          ..close();
        canvas.drawPath(ghost, isRead ? fillPaint : paint);
        canvas.drawCircle(
          Offset(w * 0.42, h * 0.44),
          1.6,
          isRead ? whiteFill : fillPaint,
        );
        canvas.drawCircle(
          Offset(w * 0.58, h * 0.44),
          1.6,
          isRead ? whiteFill : fillPaint,
        );
        break;
      case DeliveryIconStyle.sDot:
        _gbCircle(canvas, c, r * 0.8, fillPaint);
        break;
      case DeliveryIconStyle.seasons:
        _gbCapsule(canvas, size, paint, fillPaint, isRead);
        _gbCircle(
          canvas,
          Offset(isRead ? w * 0.74 : w * 0.26, h / 2),
          h * 0.10,
          paint,
        );
        break;
      case DeliveryIconStyle.smile:
        _gbSmiley(canvas, c, r, paint, fillPaint);
        break;
      case DeliveryIconStyle.star:
        final star = Path();
        for (var i = 0; i < 5; i++) {
          final a = -math.pi / 2 + i * 4 * math.pi / 5;
          final pt = Offset(
            c.dx + r * 0.9 * math.cos(a),
            c.dy + r * 0.9 * math.sin(a),
          );
          if (i == 0) {
            star.moveTo(pt.dx, pt.dy);
          } else {
            star.lineTo(pt.dx, pt.dy);
          }
        }
        star.close();
        canvas.drawPath(star, isRead ? fillPaint : paint);
        break;
      case DeliveryIconStyle.flatSwitch:
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            Rect.fromLTWH(w * 0.06, h * 0.26, w * 0.50, h * 0.48),
            Radius.circular(h * 0.10),
          ),
          isRead ? fillPaint : paint,
        );
        _gbCheck(canvas, w * 0.30, h * 0.5, 0.7, isRead ? white : paint);
        canvas.drawRect(
          Rect.fromLTWH(w * 0.60, h * 0.26, w * 0.34, h * 0.48),
          paint,
        );
        break;
      case DeliveryIconStyle.timeBomb:
        _gbCircle(
          canvas,
          Offset(c.dx, c.dy + r * 0.1),
          r * 0.75,
          isRead ? fillPaint : paint,
        );
        canvas.drawLine(
          Offset(c.dx + r * 0.3, c.dy - r * 0.5),
          Offset(c.dx + r * 0.55, c.dy - r * 0.85),
          paint,
        );
        _gbCircle(
          canvas,
          Offset(c.dx + r * 0.6, c.dy - r * 0.9),
          1.8,
          fillPaint,
        );
        break;
      case DeliveryIconStyle.treble:
        for (var i = 0; i < (full ? 3 : 2); i++) {
          final x0 = w * (0.12 + 0.26 * i);
          final zz = Path()
            ..moveTo(x0, h * 0.62)
            ..lineTo(x0 + w * 0.12, h * 0.38)
            ..lineTo(x0 + w * 0.24, h * 0.62);
          canvas.drawPath(zz, paint);
        }
        break;
      case DeliveryIconStyle.twitterConcept:
        // Original bird mark (not the platform logo art).
        final bird = Path()
          ..moveTo(w * 0.20, h * 0.62)
          ..quadraticBezierTo(w * 0.34, h * 0.44, w * 0.52, h * 0.44)
          ..lineTo(w * 0.66, h * 0.26)
          ..quadraticBezierTo(w * 0.70, h * 0.44, w * 0.62, h * 0.52)
          ..quadraticBezierTo(w * 0.80, h * 0.56, w * 0.76, h * 0.70)
          ..quadraticBezierTo(w * 0.58, h * 0.82, w * 0.38, h * 0.76)
          ..quadraticBezierTo(w * 0.24, h * 0.74, w * 0.20, h * 0.62)
          ..close();
        canvas.drawPath(bird, isRead ? fillPaint : paint);
        break;
      case DeliveryIconStyle.wbg:
        _gbCapsule(canvas, size, paint, fillPaint, isRead);
        break;
      case DeliveryIconStyle.x:
        _gbCircle(canvas, c, r, paint);
        paint.strokeWidth = 2.4;
        _gbCheck(canvas, w * 0.5, h * 0.5, 1.2, paint);
        break;
      case DeliveryIconStyle.pointer:
        for (var i = 0; i < 10; i++) {
          final a = i * math.pi / 5;
          final on = i <= (isRead ? 9 : (isDelivered ? 5 : 2));
          canvas.drawCircle(
            Offset(
              c.dx + r * 0.85 * math.cos(a),
              c.dy + r * 0.85 * math.sin(a),
            ),
            1.6,
            on ? fillPaint : paint,
          );
        }
        break;
      case DeliveryIconStyle.ticTicCircle2:
        _gbCircle(canvas, c, r, paint);
        _gbCircle(canvas, c, r * 0.66, paint);
        if (full) _gbCheck(canvas, w * 0.5, h * 0.5, 0.9, paint);
        break;
      case DeliveryIconStyle.iceCream:
        final scoop = Path()
          ..moveTo(w * 0.30, h * 0.46)
          ..arcToPoint(
            Offset(w * 0.70, h * 0.46),
            radius: Radius.circular(w * 0.20),
          )
          ..close();
        canvas.drawPath(scoop, isRead ? fillPaint : paint);
        final cone = Path()
          ..moveTo(w * 0.32, h * 0.50)
          ..lineTo(w * 0.50, h * 0.86)
          ..lineTo(w * 0.68, h * 0.50)
          ..close();
        canvas.drawPath(cone, paint);
        break;
      case DeliveryIconStyle.pony:
        // Original horse-head silhouette (not any licensed character art).
        final pony = Path()
          ..moveTo(w * 0.30, h * 0.84)
          ..lineTo(w * 0.30, h * 0.40)
          ..lineTo(w * 0.24, h * 0.20)
          ..lineTo(w * 0.38, h * 0.30)
          ..quadraticBezierTo(w * 0.50, h * 0.16, w * 0.64, h * 0.24)
          ..lineTo(w * 0.78, h * 0.44)
          ..lineTo(w * 0.72, h * 0.52)
          ..lineTo(w * 0.56, h * 0.50)
          ..lineTo(w * 0.56, h * 0.84)
          ..close();
        canvas.drawPath(pony, isRead ? fillPaint : paint);
        canvas.drawCircle(
          Offset(w * 0.46, h * 0.36),
          1.5,
          isRead ? whiteFill : fillPaint,
        );
        break;
      case DeliveryIconStyle.kitty:
        // Original plain cat face (no bow; not the licensed character art).
        final head = Path()
          ..moveTo(w * 0.24, h * 0.40)
          ..lineTo(w * 0.30, h * 0.18)
          ..lineTo(w * 0.42, h * 0.32)
          ..lineTo(w * 0.58, h * 0.32)
          ..lineTo(w * 0.70, h * 0.18)
          ..lineTo(w * 0.76, h * 0.40)
          ..arcToPoint(
            Offset(w * 0.24, h * 0.40),
            radius: Radius.circular(w * 0.26),
            clockwise: false,
          )
          ..close();
        canvas.drawPath(head, isRead ? fillPaint : paint);
        canvas.drawCircle(
          Offset(w * 0.40, h * 0.52),
          1.5,
          isRead ? whiteFill : fillPaint,
        );
        canvas.drawCircle(
          Offset(w * 0.60, h * 0.52),
          1.5,
          isRead ? whiteFill : fillPaint,
        );
        break;
      case DeliveryIconStyle.flower:
        for (var i = 0; i < 5; i++) {
          final a = i * 2 * math.pi / 5 - math.pi / 2;
          canvas.drawCircle(
            Offset(
              c.dx + r * 0.55 * math.cos(a),
              c.dy + r * 0.55 * math.sin(a),
            ),
            r * 0.34,
            isRead ? fillPaint : paint,
          );
        }
        _gbCircle(canvas, c, r * 0.26, fillPaint);
        break;
      default:
        _gbCheck(canvas, w * 0.5, h * 0.5, 1.0, paint);
        break;
    }
  }

  Path _heartPath(Offset center, double radius) {
    return Path()
      ..moveTo(center.dx, center.dy + radius * 0.95)
      ..cubicTo(
        center.dx - radius * 1.25,
        center.dy + radius * 0.15,
        center.dx - radius * 1.15,
        center.dy - radius * 0.95,
        center.dx,
        center.dy - radius * 0.30,
      )
      ..cubicTo(
        center.dx + radius * 1.15,
        center.dy - radius * 0.95,
        center.dx + radius * 1.25,
        center.dy + radius * 0.15,
        center.dx,
        center.dy + radius * 0.95,
      )
      ..close();
  }

  Path _batmanPath(Rect rect) {
    final w = rect.width;
    final h = rect.height;
    final x = rect.left;
    final y = rect.top;
    return Path()
      ..moveTo(x + w * 0.50, y + h * 0.96)
      ..lineTo(x + w * 0.35, y + h * 0.62)
      ..lineTo(x + w * 0.15, y + h * 0.80)
      ..lineTo(x + w * 0.05, y + h * 0.23)
      ..lineTo(x + w * 0.31, y + h * 0.45)
      ..lineTo(x + w * 0.30, y + h * 0.05)
      ..lineTo(x + w * 0.50, y + h * 0.27)
      ..lineTo(x + w * 0.70, y + h * 0.05)
      ..lineTo(x + w * 0.69, y + h * 0.45)
      ..lineTo(x + w * 0.95, y + h * 0.23)
      ..lineTo(x + w * 0.85, y + h * 0.80)
      ..lineTo(x + w * 0.65, y + h * 0.62)
      ..close();
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
