import 'dart:math' as math;
import 'package:flutter/material.dart';

/// Custom animated vector icon representing the "Bandi Kinetic Crest".
///
/// Cultural & Design Grounding:
/// 1. "Bandi Chhor" (Liberation & Emancipation): The 52-degree radial liberation facets.
/// 2. "Bandi" (Telugu: Chariot / Swift Vehicle): Dynamic kinetic velocity arrows and dual orbital wheels.
/// 3. "Bandh" (Sanskrit: Sacred Bond): Interlocking orbital rings representing unbreakable E2EE security.
///
/// Features Telegram-style elastic micro-spring animation and 144Hz sub-pixel vector rendering.
class BandiKineticCrest extends StatefulWidget {
  final double size;
  final Color primaryColor;
  final Color accentColor;
  final bool isAnimated;
  final VoidCallback? onTap;

  const BandiKineticCrest({
    super.key,
    this.size = 28.0,
    this.primaryColor = const Color(0xFF60A5FA),
    this.accentColor = const Color(0xFF38BDF8),
    this.isAnimated = true,
    this.onTap,
  });

  @override
  State<BandiKineticCrest> createState() => _BandiKineticCrestState();
}

class _BandiKineticCrestState extends State<BandiKineticCrest>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _rotationAnim;
  late final Animation<double> _pulseAnim;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 3200),
    );

    // Continuous smooth breathing rotation
    _rotationAnim = Tween<double>(begin: 0.0, end: 2 * math.pi).animate(
      CurvedAnimation(parent: _controller, curve: Curves.linear),
    );

    // Subtle breathing pulse
    _pulseAnim = TweenSequence<double>([
      TweenSequenceItem(
        tween: Tween<double>(begin: 1.0, end: 1.08)
            .chain(CurveTween(curve: Curves.easeInOutCubic)),
        weight: 50,
      ),
      TweenSequenceItem(
        tween: Tween<double>(begin: 1.08, end: 1.0)
            .chain(CurveTween(curve: Curves.easeInOutCubic)),
        weight: 50,
      ),
    ]).animate(_controller);

    if (widget.isAnimated) {
      _controller.repeat();
    }
  }

  @override
  void didUpdateWidget(covariant BandiKineticCrest oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isAnimated != oldWidget.isAnimated) {
      if (widget.isAnimated) {
        _controller.repeat();
      } else {
        _controller.stop();
      }
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _triggerSnap() {
    // Elastic overshoot snap on tap mimicking Telegram animated sticker dynamics
    _controller.forward(from: 0.0);
    widget.onTap?.call();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: _triggerSnap,
      child: SizedBox(
        width: widget.size,
        height: widget.size,
        child: AnimatedBuilder(
          animation: _controller,
          builder: (context, _) {
            return Transform.scale(
              scale: _pulseAnim.value,
              child: CustomPaint(
                size: Size(widget.size, widget.size),
                painter: _BandiCrestPainter(
                  rotation: _rotationAnim.value,
                  primaryColor: widget.primaryColor,
                  accentColor: widget.accentColor,
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

class _BandiCrestPainter extends CustomPainter {
  final double rotation;
  final Color primaryColor;
  final Color accentColor;

  _BandiCrestPainter({
    required this.rotation,
    required this.primaryColor,
    required this.accentColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width * 0.46;

    final primaryPaint = Paint()
      ..color = primaryColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = math.max(1.4, size.width * 0.065)
      ..strokeCap = StrokeCap.round
      ..isAntiAlias = true;

    final accentPaint = Paint()
      ..color = accentColor.withValues(alpha: 0.85)
      ..style = PaintingStyle.stroke
      ..strokeWidth = math.max(1.0, size.width * 0.05)
      ..strokeCap = StrokeCap.round
      ..isAntiAlias = true;

    final glowPaint = Paint()
      ..color = primaryColor.withValues(alpha: 0.22)
      ..style = PaintingStyle.stroke
      ..strokeWidth = math.max(2.6, size.width * 0.12)
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 2.5)
      ..isAntiAlias = true;

    final fillPaint = Paint()
      ..color = accentColor
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;

    canvas.save();
    canvas.translate(center.dx, center.dy);

    // 1. Draw outer ambient orbital glow
    canvas.drawCircle(Offset.zero, radius * 0.88, glowPaint);

    // 2. Outer liberation ring with 52-degree radial facets
    canvas.save();
    canvas.rotate(rotation);
    for (int i = 0; i < 4; i++) {
      final angle = i * (math.pi / 2);
      canvas.drawArc(
        Rect.fromCircle(center: Offset.zero, radius: radius),
        angle + 0.18,
        (math.pi / 2) - 0.36,
        false,
        primaryPaint,
      );
    }

    // 4 peripheral liberation pips
    for (int i = 0; i < 4; i++) {
      final angle = i * (math.pi / 2) + 0.1;
      final px = radius * math.cos(angle);
      final py = radius * math.sin(angle);
      canvas.drawCircle(Offset(px, py), size.width * 0.04, fillPaint);
    }
    canvas.restore();

    // 3. Interlocking secondary elliptical orbital ring (Bandh bond)
    canvas.save();
    canvas.rotate(-rotation * 1.3);
    final innerRect = Rect.fromCenter(
      center: Offset.zero,
      width: radius * 1.45,
      height: radius * 0.95,
    );
    canvas.drawOval(innerRect, accentPaint);
    canvas.restore();

    // 4. Central kinetic chariot shield (Bandi speed & protection)
    final shieldPath = Path();
    final s = size.width * 0.22;

    shieldPath.moveTo(0, -s * 1.15); // Top tip
    shieldPath.lineTo(s * 0.85, -s * 0.35); // Upper right
    shieldPath.lineTo(s * 0.65, s * 0.65); // Lower right
    shieldPath.lineTo(0, s * 1.05); // Bottom point
    shieldPath.lineTo(-s * 0.65, s * 0.65); // Lower left
    shieldPath.lineTo(-s * 0.85, -s * 0.35); // Upper left
    shieldPath.close();

    final shieldFill = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [
          primaryColor.withValues(alpha: 0.9),
          accentColor.withValues(alpha: 0.7),
        ],
      ).createShader(Rect.fromCircle(center: Offset.zero, radius: s))
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;

    canvas.drawPath(shieldPath, shieldFill);

    // Inner kinetic lightning/velocity slash
    final boltPath = Path();
    boltPath.moveTo(0, -s * 0.6);
    boltPath.lineTo(-s * 0.25, 0.0);
    boltPath.lineTo(s * 0.05, 0.0);
    boltPath.lineTo(-s * 0.1, s * 0.6);
    boltPath.lineTo(s * 0.3, -s * 0.1);
    boltPath.lineTo(0.0, -s * 0.1);
    boltPath.close();

    final boltPaint = Paint()
      ..color = Colors.white
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;

    canvas.drawPath(boltPath, boltPaint);

    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _BandiCrestPainter oldDelegate) {
    return oldDelegate.rotation != rotation ||
        oldDelegate.primaryColor != primaryColor ||
        oldDelegate.accentColor != accentColor;
  }
}
