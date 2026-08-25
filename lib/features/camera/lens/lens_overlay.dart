import 'dart:math' as math;

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
      switch (lens.anchor) {
        case LensAnchor.eyes:
          _paintGlasses(canvas, pose);
        case LensAnchor.forehead:
          _paintCrown(canvas, pose);
        case LensAnchor.ears:
          _paintEars(canvas, pose);
        case LensAnchor.mouth:
          _paintMouthGlow(canvas, pose);
        case LensAnchor.noseBridge:
        case LensAnchor.noseTip:
          _paintNoseDot(canvas, pose);
        case LensAnchor.fullHead:
          break;
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

  void _paintGlasses(Canvas canvas, FacePose pose) {
    final mid = pose.eyesMidpoint;
    final unit = pose.interocular * 2.4;
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
