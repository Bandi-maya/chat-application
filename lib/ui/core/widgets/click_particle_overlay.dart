import 'dart:math';
import 'package:flutter/material.dart';
import '../controllers/preferences_controller.dart';

class ClickParticle {
  final Offset position;
  final Offset velocity;
  final String symbol;
  double opacity;
  double scale;
  final double maxLifetime;
  double age;

  ClickParticle({
    required this.position,
    required this.velocity,
    required this.symbol,
    this.opacity = 1.0,
    this.scale = 1.0,
    this.maxLifetime = 0.6,
    this.age = 0.0,
  });
}

class ClickParticleOverlay extends StatefulWidget {
  final ChatyPreferencesController preferencesController;
  final Widget child;

  const ClickParticleOverlay({
    super.key,
    required this.preferencesController,
    required this.child,
  });

  @override
  State<ClickParticleOverlay> createState() => _ClickParticleOverlayState();
}

class _ClickParticleOverlayState extends State<ClickParticleOverlay>
    with SingleTickerProviderStateMixin {
  late AnimationController _animController;
  final List<ClickParticle> _particles = [];
  final Random _random = Random();

  @override
  void initState() {
    super.initState();
    _animController =
        AnimationController(vsync: this, duration: const Duration(seconds: 1))
          ..addListener(_tick)
          ..repeat();
  }

  @override
  void dispose() {
    _animController.dispose();
    super.dispose();
  }

  void _tick() {
    if (_particles.isEmpty) return;
    setState(() {
      final double dt = 0.016;
      _particles.removeWhere((p) {
        p.age += dt;
        p.opacity = (1.0 - (p.age / p.maxLifetime)).clamp(0.0, 1.0);
        return p.age >= p.maxLifetime;
      });
    });
  }

  void _spawnParticles(Offset globalPos) {
    final fx = widget.preferencesController.effects;
    if (!fx.enableClickParticles) return;
    // Decorative burst motion is suppressed when the platform requests
    // reduced motion, even if the effect itself is enabled.
    if (MediaQuery.disableAnimationsOf(context)) return;

    final symbol = fx.clickParticleSymbol;
    final speedMultiplier = fx.clickParticleSpeed;

    for (int i = 0; i < 6; i++) {
      final angle = _random.nextDouble() * 2 * pi;
      final speed = (20.0 + _random.nextDouble() * 40.0) * speedMultiplier;
      _particles.add(
        ClickParticle(
          position: globalPos,
          velocity: Offset(cos(angle) * speed, sin(angle) * speed),
          symbol: symbol,
          scale: 0.8 + _random.nextDouble() * 0.4,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (event) => _spawnParticles(event.position),
      child: Stack(
        children: [
          widget.child,
          if (_particles.isNotEmpty)
            IgnorePointer(
              child: CustomPaint(
                size: Size.infinite,
                painter: _ClickParticlePainter(particles: _particles),
              ),
            ),
        ],
      ),
    );
  }
}

class _ClickParticlePainter extends CustomPainter {
  final List<ClickParticle> particles;

  _ClickParticlePainter({required this.particles});

  @override
  void paint(Canvas canvas, Size size) {
    for (final p in particles) {
      final calculatedSize = (16.0 * p.scale).toDouble();
      final fontSize = calculatedSize > 0.0 ? calculatedSize : 1.0;
      final textPainter = TextPainter(
        text: TextSpan(
          text: p.symbol,
          style: TextStyle(
            fontSize: fontSize,
            color: Colors.white.withValues(alpha: p.opacity),
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();

      final currentOffset = p.position + p.velocity * (p.age * 1.5);
      textPainter.paint(canvas, currentOffset);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
