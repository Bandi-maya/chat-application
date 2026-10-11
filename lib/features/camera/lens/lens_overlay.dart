import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import 'lens_definition.dart';

/// Renders a lens at its SOLVED facial anchor.
///
/// This replaces the legacy behavior of painting accessories at fixed screen
/// coordinates: every element here is positioned by tracked geometry and
/// rotates/scales with the face (requirements 18–21). Pure painter — no
/// camera, platform or stateful dependencies.
class LensOverlay extends StatelessWidget {
  const LensOverlay({
    super.key,
    required this.lens,
    required this.poses,
    required this.health,
    this.opacity = 1.0,
  });

  final LensDefinition lens;
  final List<FacePose> poses;

  /// Lenses fade rather than freeze when tracking drops (requirement 25).
  final TrackingHealth health;
  final double opacity;

  @override
  Widget build(BuildContext context) {
    if (lens.isNoLens || poses.isEmpty || health == TrackingHealth.lost) {
      return const SizedBox.expand();
    }
    final effectiveOpacity =
        opacity * (health == TrackingHealth.temporarilyLost ? 0.55 : 1.0);

    return IgnorePointer(
      child: CustomPaint(
        key: const ValueKey('lens_anchor_paint'),
        size: Size.infinite,
        painter: _LensAnchorPainter(
          lens: lens,
          poses: poses,
          opacity: effectiveOpacity,
        ),
      ),
    );
  }
}


/// Bakes an active lens onto a captured still without a third-party image
/// package. [poses] are in the live, cover-cropped preview coordinate space;
/// they are inverse-mapped onto the output image before painting.
Future<Uint8List> composeLensIntoImage({
  required Uint8List sourceBytes,
  required Size previewSize,
  required LensDefinition lens,
  required List<FacePose> poses,
}) async {
  final codec = await ui.instantiateImageCodec(sourceBytes, targetWidth: 2048);
  final frame = await codec.getNextFrame();
  final image = frame.image;
  try {
    final imageSize = Size(image.width.toDouble(), image.height.toDouble());
    final scale = math.max(
      previewSize.width / imageSize.width,
      previewSize.height / imageSize.height,
    );
    final cropLeft = (previewSize.width - imageSize.width * scale) / 2;
    final cropTop = (previewSize.height - imageSize.height * scale) / 2;

    Offset toImage(Offset point) => Offset(
      (point.dx - cropLeft) / scale,
      (point.dy - cropTop) / scale,
    );

    final mappedPoses = poses.map((pose) {
      final first = toImage(pose.headBounds.topLeft);
      final second = toImage(pose.headBounds.bottomRight);
      return FacePose(
        trackingId: pose.trackingId,
        headBounds: Rect.fromPoints(first, second),
        leftEye: toImage(pose.leftEye),
        rightEye: toImage(pose.rightEye),
        noseBase: toImage(pose.noseBase),
        mouthCenter: toImage(pose.mouthCenter),
        rollRadians: pose.rollRadians,
        yawRadians: pose.yawRadians,
        pitchRadians: pose.pitchRadians,
        confidence: pose.confidence,
      );
    }).toList(growable: false);

    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    canvas.drawImage(image, Offset.zero, Paint());
    _LensAnchorPainter(lens: lens, poses: mappedPoses, opacity: 1).paint(
      canvas,
      imageSize,
    );
    final rendered = await recorder.endRecording().toImage(image.width, image.height);
    try {
      final data = await rendered.toByteData(format: ui.ImageByteFormat.png);
      if (data == null) throw StateError('Lens image encoding failed.');
      return data.buffer.asUint8List();
    } finally {
      rendered.dispose();
    }
  } finally {
    image.dispose();
    codec.dispose();
  }
}

class _LensAnchorPainter extends CustomPainter {
  _LensAnchorPainter({
    required this.lens,
    required this.poses,
    required this.opacity,
  });

  final LensDefinition lens;
  final List<FacePose> poses;
  final double opacity;

  @override
  void paint(Canvas canvas, Size size) {
    // Respect per-lens multi-face capability (requirement 26): each tracked
    // face receives an independent transform — never everything on face #0.
    for (final pose in poses.take(math.max(1, lens.maxFaces))) {
      canvas.save();
      if (lens.id == 'lens_hearts_float') {
        _paintHearts(canvas, pose);
      } else {
        switch (lens.anchor) {
          case LensAnchor.eyes:
            if (lens.id == 'lens_sunglasses_aviator') {
              _paintAviators(canvas, pose);
            } else {
              _paintGlasses(canvas, pose);
            }
          case LensAnchor.forehead:
            if (lens.id == 'lens_angel_halo') {
              _paintAngelHalo(canvas, pose);
            } else if (lens.id == 'lens_sparkles') {
              _paintSparkles(canvas, pose);
            } else if (lens.id == 'lens_neon_crown') {
              _paintNeonCrown(canvas, pose);
            } else {
              _paintCrown(canvas, pose);
            }
          case LensAnchor.ears:
            _paintEars(canvas, pose);
            if (lens.id == 'lens_dog_ears') _paintDogNose(canvas, pose);
          case LensAnchor.mouth:
            _paintMouthGlow(canvas, pose);
          case LensAnchor.noseBridge:
          case LensAnchor.noseTip:
            _paintNoseDot(canvas, pose);
          case LensAnchor.fullHead:
            break;
        }
      }
      canvas.restore();
    }
  }

  /// Common transform: origin at [origin], rotated by head roll, scaled so the
  /// accessory grows/shrinks with the face instead of staying screen-fixed.
  void _withFaceTransform(
    Canvas canvas,
    FacePose pose,
    Offset origin,
    double unit,
    VoidCallback draw,
  ) {
    canvas.translate(origin.dx, origin.dy);
    canvas.rotate(pose.rollRadians);
    canvas.scale(unit / 100.0); // art authored against a 100px reference
    draw();
  }

  void _paintAviators(Canvas canvas, FacePose pose) {
    final mid = pose.eyesMidpoint;
    final unit = pose.interocular * 1.08;
    _withFaceTransform(canvas, pose, mid, unit, () {
      final fill = Paint()
        ..color = const Color(0xFF0F172A).withValues(alpha: 0.90 * opacity)
        ..style = PaintingStyle.fill;
      final rim = Paint()
        ..color = lens.accentColor.withValues(alpha: opacity)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 5
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round;

      final leftLens = Path()
        ..moveTo(-91, -22)
        ..quadraticBezierTo(-50, -34, -15, -17)
        ..lineTo(-25, 20)
        ..quadraticBezierTo(-48, 42, -70, 22)
        ..close();
      final rightLens = Path()
        ..moveTo(15, -17)
        ..quadraticBezierTo(50, -34, 91, -22)
        ..lineTo(70, 22)
        ..quadraticBezierTo(48, 42, 25, 20)
        ..close();
      canvas.drawPath(leftLens, fill);
      canvas.drawPath(rightLens, fill);
      canvas.drawPath(leftLens, rim);
      canvas.drawPath(rightLens, rim);
      canvas.drawLine(const Offset(-16, -16), const Offset(16, -16), rim);
      canvas.drawLine(const Offset(-90, -20), const Offset(-108, -26), rim);
      canvas.drawLine(const Offset(90, -20), const Offset(108, -26), rim);
    });
  }

  void _paintNeonCrown(Canvas canvas, FacePose pose) {
    final origin = Offset(
      pose.headBounds.center.dx,
      pose.headBounds.top + pose.headBounds.height * 0.08,
    );
    final unit = pose.headBounds.width * 0.72;
    _withFaceTransform(canvas, pose, origin, unit, () {
      final glow = Paint()
        ..color = lens.accentColor.withValues(alpha: 0.45 * opacity)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 11
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 7);
      final line = Paint()
        ..color = lens.accentColor.withValues(alpha: opacity)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 4
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round;
      final path = Path()
        ..moveTo(-60, 20)
        ..lineTo(-45, -22)
        ..lineTo(-20, 2)
        ..lineTo(0, -40)
        ..lineTo(20, 2)
        ..lineTo(45, -22)
        ..lineTo(60, 20);
      canvas.drawPath(path, glow);
      canvas.drawPath(path, line);
      canvas.drawLine(const Offset(-60, 20), const Offset(60, 20), line);
    });
  }

  void _paintAngelHalo(Canvas canvas, FacePose pose) {
    final origin = Offset(
      pose.headBounds.center.dx,
      pose.headBounds.top + pose.headBounds.height * 0.05,
    );
    final unit = pose.headBounds.width * 0.68;
    _withFaceTransform(canvas, pose, origin, unit, () {
      final glow = Paint()
        ..color = lens.accentColor.withValues(alpha: 0.45 * opacity)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 12
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 8);
      final halo = Paint()
        ..color = lens.accentColor.withValues(alpha: opacity)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 5;
      canvas.drawOval(
        Rect.fromCenter(center: const Offset(0, -28), width: 112, height: 24),
        glow,
      );
      canvas.drawOval(
        Rect.fromCenter(center: const Offset(0, -28), width: 112, height: 24),
        halo,
      );
      for (final x in <double>[-42, 0, 42]) {
        canvas.drawCircle(
          Offset(x, -29),
          x == 0 ? 3.5 : 2.2,
          Paint()..color = Colors.white.withValues(alpha: opacity),
        );
      }
    });
  }

  void _paintSparkles(Canvas canvas, FacePose pose) {
    final origin = pose.eyesMidpoint;
    final unit = pose.interocular * 2.15;
    _withFaceTransform(canvas, pose, origin, unit, () {
      void star(Offset center, double radius, double alpha) {
        final path = Path();
        for (var i = 0; i < 8; i++) {
          final angle = -math.pi / 2 + i * math.pi / 4;
          final r = i.isEven ? radius : radius * 0.28;
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
        path.close();
        canvas.drawPath(
          path,
          Paint()
            ..color = lens.accentColor.withValues(alpha: alpha * opacity)
            ..style = PaintingStyle.fill,
        );
      }

      star(const Offset(-70, -26), 17, 1.0);
      star(const Offset(74, -34), 13, 0.9);
      star(const Offset(-48, 42), 9, 0.75);
      star(const Offset(53, 37), 8, 0.8);
    });
  }

  void _paintHearts(Canvas canvas, FacePose pose) {
    final origin = Offset(
      pose.headBounds.center.dx,
      pose.headBounds.top + pose.headBounds.height * 0.18,
    );
    final unit = pose.headBounds.width * 0.9;
    _withFaceTransform(canvas, pose, origin, unit, () {
      void heart(Offset center, double scale, double alpha) {
        final path = Path()
          ..moveTo(center.dx, center.dy + 24 * scale)
          ..cubicTo(
            center.dx - 40 * scale,
            center.dy - 1 * scale,
            center.dx - 26 * scale,
            center.dy - 25 * scale,
            center.dx,
            center.dy - 10 * scale,
          )
          ..cubicTo(
            center.dx + 26 * scale,
            center.dy - 25 * scale,
            center.dx + 40 * scale,
            center.dy - 1 * scale,
            center.dx,
            center.dy + 24 * scale,
          )
          ..close();
        canvas.drawPath(
          path,
          Paint()..color = lens.accentColor.withValues(alpha: alpha * opacity),
        );
      }

      heart(const Offset(-48, -6), 0.40, 0.90);
      heart(const Offset(44, -28), 0.30, 0.78);
      heart(const Offset(78, 36), 0.22, 0.66);
    });
  }

  void _paintDogNose(Canvas canvas, FacePose pose) {
    _withFaceTransform(canvas, pose, pose.noseBase, pose.interocular * 1.75, () {
      final outer = Paint()
        ..color = const Color(0xFF1F2937).withValues(alpha: 0.95 * opacity)
        ..style = PaintingStyle.fill;
      final inner = Paint()
        ..color = const Color(0xFF111827).withValues(alpha: opacity)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2;
      canvas.drawOval(
        Rect.fromCenter(center: Offset.zero, width: 32, height: 22),
        outer,
      );
      canvas.drawLine(const Offset(0, 9), const Offset(0, 17), inner);
      canvas.drawArc(
        Rect.fromCenter(center: const Offset(-5, 17), width: 10, height: 7),
        0,
        math.pi,
        false,
        inner,
      );
      canvas.drawArc(
        Rect.fromCenter(center: const Offset(5, 17), width: 10, height: 7),
        0,
        math.pi,
        false,
        inner,
      );
    });
  }

  void _paintGlasses(Canvas canvas, FacePose pose) {
    final mid = pose.eyesMidpoint;
    final unit = pose.interocular * 1.08;
    _withFaceTransform(canvas, pose, mid, unit, () {
      final frame = Paint()
        ..color = Colors.black.withValues(alpha: 0.85 * opacity)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 6;
      final bridge = Paint()..color = Colors.black.withValues(alpha: 0.85 * opacity)
        ..strokeWidth = 6 ..style = PaintingStyle.stroke;

      canvas.drawCircle(const Offset(-50, 0), 34, frame);
      canvas.drawCircle(const Offset(50, 0), 34, frame);
      canvas.drawLine(const Offset(-16, 0), const Offset(16, 0), bridge);
      // Temples reach toward the ears so rotation reads naturally.
      canvas.drawLine(const Offset(-84, 0), const Offset(-110, -6), bridge);
      canvas.drawLine(const Offset(84, 0), const Offset(110, -6), bridge);
    });
  }

  void _paintCrown(Canvas canvas, FacePose pose) {
    // Forehead anchor sits above the eye line, following head bounds top.
    final origin = Offset(
      pose.headBounds.center.dx,
      pose.headBounds.top + pose.headBounds.height * 0.08,
    );
    final unit = pose.headBounds.width * 0.72;
    _withFaceTransform(canvas, pose, origin, unit, () {
      final paint = Paint()
        ..color = const Color(0xFFFBBF24).withValues(alpha: opacity)
        ..style = PaintingStyle.fill;
      final path = Path()
        ..moveTo(-60, 20)
        ..lineTo(-45, -22)
        ..lineTo(-20, 2)
        ..lineTo(0, -40)
        ..lineTo(20, 2)
        ..lineTo(45, -22)
        ..lineTo(60, 20)
        ..close();
      canvas.drawPath(path, paint);
    });
  }

  void _paintEars(Canvas canvas, FacePose pose) {
    // Ear anchor derives from head bounds width and yaw offset so turning the
    // head slides the ears with it rather than leaving them behind.
    final yawShift =
        pose.headBounds.width * 0.10 * math.sin(pose.yawRadians).clamp(-1.0, 1.0);
    final origin = Offset(
      pose.headBounds.center.dx + yawShift,
      pose.headBounds.top + pose.headBounds.height * 0.12,
    );
    final unit = pose.headBounds.width * 0.9;
    _withFaceTransform(canvas, pose, origin, unit, () {
      final outer = Paint()
        ..color = const Color(0xFF8D5B4C).withValues(alpha: opacity)
        ..style = PaintingStyle.fill;
      final inner = Paint()
        ..color = const Color(0xFFFBB6CE).withValues(alpha: opacity)
        ..style = PaintingStyle.fill;

      for (final side in <double>[-1, 1]) {
        canvas.save();
        canvas.translate(side * 52, -6);
        canvas.rotate(side * 0.28);
        canvas.drawOval(Rect.fromCenter(center: Offset.zero, width: 34, height: 58), outer);
        canvas.drawOval(
          Rect.fromCenter(center: Offset.zero, width: 22, height: 38),
          inner,
        );
        canvas.restore();
      }
    });
  }

  void _paintMouthGlow(Canvas canvas, FacePose pose) {
    _withFaceTransform(canvas, pose, pose.mouthCenter, pose.interocular * 1.6, () {
      final glow = Paint()
        ..color = const Color(0xFFF472B6).withValues(alpha: 0.30 * opacity)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 18);
      canvas.drawCircle(Offset.zero, 26, glow);
      final core = Paint()
        ..color = Colors.white.withValues(alpha: 0.35 * opacity);
      canvas.drawCircle(Offset.zero, 8, core);
    });
  }

  void _paintNoseDot(Canvas canvas, FacePose pose) {
    _withFaceTransform(canvas, pose, pose.noseBase, pose.interocular * 0.8, () {
      final paint = Paint()
        ..color = Colors.white.withValues(alpha: 0.5 * opacity);
      canvas.drawCircle(Offset.zero, 9, paint);
    });
  }

  @override
  bool shouldRepaint(covariant _LensAnchorPainter old) =>
      old.lens != lens ||
      old.opacity != opacity ||
      old.poses != poses;
}
