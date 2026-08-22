import 'dart:math';
import 'package:flutter/material.dart';
import '../controllers/preferences_controller.dart';

class FallingItem {
  double x;
  double y;
  double speed;
  double size;
  double opacity;
  String symbol;

  FallingItem({
    required this.x,
    required this.y,
    required this.speed,
    required this.size,
    required this.opacity,
    required this.symbol,
  });
}

class FallingParticlesOverlay extends StatefulWidget {
  final ChatyPreferencesController preferencesController;
  final Widget child;
  final String currentScope; // 'Home' or 'Chat'

  const FallingParticlesOverlay({
    super.key,
    required this.preferencesController,
    required this.child,
    required this.currentScope,
  });

  @override
  State<FallingParticlesOverlay> createState() =>
      _FallingParticlesOverlayState();
}

class _FallingParticlesOverlayState extends State<FallingParticlesOverlay>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  final List<FallingItem> _items = [];
  final Random _random = Random();

  @override
  void initState() {
    super.initState();
    _controller =
        AnimationController(vsync: this, duration: const Duration(seconds: 10))
          ..addListener(_updateParticles);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _syncMotion();
  }

  /// Honors the platform "reduce motion" setting: the effect stays
  /// available but stops its infinite loop, rendering a static scatter
  /// instead of continuously falling particles.
  void _syncMotion() {
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    if (reduceMotion) {
      if (_controller.isAnimating) _controller.stop();
    } else if (!_controller.isAnimating) {
      _controller.repeat();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  String _getSymbol(String objectName) {
    switch (objectName) {
      case 'Hearts':
        return '❤️';
      case 'Snowflakes':
        return '❄️';
      case 'Leaves':
        return '🍂';
      case 'Stars':
      default:
        return '⭐';
    }
  }

  void _updateParticles() {
    final fx = widget.preferencesController.effects;
    if (!fx.enableFallingParticles) {
      if (_items.isNotEmpty) setState(() => _items.clear());
      return;
    }

    final scope = fx.fallingParticleScope;
    if (scope == 'Home only' && widget.currentScope != 'Home') return;
    if (scope == 'Chat only' && widget.currentScope != 'Chat') return;

    final symbol = _getSymbol(fx.fallingParticleObject);

    if (_items.length < 15) {
      _items.add(
        FallingItem(
          x: _random.nextDouble(),
          y: -0.05,
          speed: 0.002 + _random.nextDouble() * 0.003,
          size: 12.0 + _random.nextDouble() * 10.0,
          opacity: 0.3 + _random.nextDouble() * 0.5,
          symbol: symbol,
        ),
      );
    }

    setState(() {
      for (final item in _items) {
        item.y += item.speed;
        item.symbol = symbol;
        if (item.y > 1.05) {
          item.y = -0.05;
          item.x = _random.nextDouble();
        }
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final fx = widget.preferencesController.effects;
    final bool active =
        fx.enableFallingParticles &&
        (fx.fallingParticleScope == 'Both' ||
            (fx.fallingParticleScope == 'Home only' &&
                widget.currentScope == 'Home') ||
            (fx.fallingParticleScope == 'Chat only' &&
                widget.currentScope == 'Chat'));

    return Stack(
      children: [
        widget.child,
        if (active && _items.isNotEmpty)
          IgnorePointer(
            child: CustomPaint(
              size: Size.infinite,
              painter: _FallingPainter(items: _items),
            ),
          ),
      ],
    );
  }
}

class _FallingPainter extends CustomPainter {
  final List<FallingItem> items;

  _FallingPainter({required this.items});

  @override
  void paint(Canvas canvas, Size size) {
    for (final item in items) {
      final fontSize = item.size > 0 ? item.size : 14.0;
      final textPainter = TextPainter(
        text: TextSpan(
          text: item.symbol,
          style: TextStyle(
            fontSize: fontSize,
            color: Colors.white.withValues(alpha: item.opacity),
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();

      textPainter.paint(
        canvas,
        Offset(item.x * size.width, item.y * size.height),
      );
    }
  }

  @override
  bool shouldRepaint(covariant _FallingPainter oldDelegate) => true;
}
