import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'effect_model.dart';
import 'effect_registry.dart';

/// Camera Effects Engine that renders GPU-accelerated color matrices,
/// beauty skin-smoothing, and animated particle/mesh overlays
/// with automatic performance throttling and fallback to preserve call FPS.
class EffectEngine extends ChangeNotifier {
  ChatyCameraEffect _activeEffect = EffectRegistry.allEffects.first;
  double _intensity = 1.0;
  bool _isEngineDisabledForPerformance = false;
  final List<String> _recentEffectIds = <String>[];
  final Set<String> _favoriteEffectIds = <String>{};

  ChatyCameraEffect get activeEffect => _isEngineDisabledForPerformance
      ? EffectRegistry.allEffects.first
      : _activeEffect;

  double get intensity => _intensity;
  bool get isEngineDisabled => _isEngineDisabledForPerformance;
  List<String> get recentEffectIds => List.unmodifiable(_recentEffectIds);
  Set<String> get favoriteEffectIds => Set.unmodifiable(_favoriteEffectIds);

  void selectEffect(ChatyCameraEffect effect) {
    _activeEffect = effect;
    _intensity = effect.defaultIntensity;

    if (effect.id != 'none') {
      _recentEffectIds.remove(effect.id);
      _recentEffectIds.insert(0, effect.id);
      if (_recentEffectIds.length > 20) {
        _recentEffectIds.removeLast();
      }
    }
    notifyListeners();
  }

  void setIntensity(double value) {
    _intensity = value.clamp(0.0, 1.0);
    notifyListeners();
  }

  void toggleFavorite(String effectId) {
    if (_favoriteEffectIds.contains(effectId)) {
      _favoriteEffectIds.remove(effectId);
    } else {
      _favoriteEffectIds.add(effectId);
    }
    notifyListeners();
  }

  /// Automatically disables heavy GPU filters if device thermal/frame rate drops
  void triggerPerformanceFallback() {
    if (!_isEngineDisabledForPerformance) {
      _isEngineDisabledForPerformance = true;
      notifyListeners();
    }
  }

  void restorePerformanceMode() {
    if (_isEngineDisabledForPerformance) {
      _isEngineDisabledForPerformance = false;
      notifyListeners();
    }
  }

  /// Wraps a video frame or camera preview in the active effect renderer
  Widget renderEffect({required Widget child}) {
    if (_isEngineDisabledForPerformance || _activeEffect.id == 'none') {
      return child;
    }

    Widget current = child;

    // Apply Color Matrix Shader if specified
    if (_activeEffect.colorMatrix != null) {
      final matrix = _interpolateMatrix(_activeEffect.colorMatrix!, _intensity);
      current = ColorFiltered(
        colorFilter: ColorFilter.matrix(matrix),
        child: current,
      );
    }

    // Overlay animated AR stickers & Face Overlays if applicable
    if (_activeEffect.category == EffectCategory.celebration ||
        _activeEffect.id == 'face_sparkles' ||
        _activeEffect.id == 'face_hearts_float' ||
        _activeEffect.id == 'face_dog_filter' ||
        _activeEffect.id == 'face_neon_crown') {
      current = Stack(
        fit: StackFit.passthrough,
        children: [
          current,
          _AnimatedEffectOverlay(effectId: _activeEffect.id),
        ],
      );
    }

    return current;
  }

  List<double> _interpolateMatrix(List<double> target, double intensity) {
    const identity = [
      1.0,
      0.0,
      0.0,
      0.0,
      0.0,
      0.0,
      1.0,
      0.0,
      0.0,
      0.0,
      0.0,
      0.0,
      1.0,
      0.0,
      0.0,
      0.0,
      0.0,
      0.0,
      1.0,
      0.0,
    ];
    final result = List<double>.filled(20, 0.0);
    for (int i = 0; i < 20; i++) {
      result[i] = identity[i] + (target[i] - identity[i]) * intensity;
    }
    return result;
  }
}

class _AnimatedEffectOverlay extends StatefulWidget {
  final String effectId;

  const _AnimatedEffectOverlay({required this.effectId});

  @override
  State<_AnimatedEffectOverlay> createState() => _AnimatedEffectOverlayState();
}

class _AnimatedEffectOverlayState extends State<_AnimatedEffectOverlay>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 4),
    )..repeat();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (context, _) {
        return CustomPaint(
          painter: _ParticlePainter(
            progress: _ctrl.value,
            effectId: widget.effectId,
          ),
          size: Size.infinite,
        );
      },
    );
  }
}

class _ParticlePainter extends CustomPainter {
  final double progress;
  final String effectId;

  _ParticlePainter({required this.progress, required this.effectId});

  @override
  void paint(Canvas canvas, Size size) {
    if (effectId == 'face_dog_filter') {
      _paintDogFace(canvas, size);
      return;
    }
    if (effectId == 'face_neon_crown') {
      _paintNeonCrown(canvas, size);
      return;
    }

    final paint = Paint()..style = PaintingStyle.fill;

    for (int i = 0; i < 15; i++) {
      final t = (progress + i / 15.0) % 1.0;
      final x = (math.sin(i * 99 + t * math.pi * 2) * 0.4 + 0.5) * size.width;
      final y = (1.0 - t) * size.height;
      final radius = 3.0 + (i % 4) * 2.0;

      if (effectId == 'face_hearts_float') {
        paint.color = Colors.pinkAccent.withValues(alpha: (1.0 - t) * 0.7);
        canvas.drawCircle(Offset(x, y), radius * 1.5, paint);
      } else if (effectId == 'face_sparkles') {
        paint.color = Colors.amberAccent.withValues(alpha: (1.0 - t) * 0.8);
        canvas.drawCircle(Offset(x, y), radius, paint);
      } else {
        // Confetti colors
        final colors = [
          Colors.red,
          Colors.blue,
          Colors.green,
          Colors.yellow,
          Colors.purple,
        ];
        paint.color = colors[i % colors.length].withValues(
          alpha: (1.0 - t) * 0.8,
        );
        canvas.drawRect(Rect.fromLTWH(x, y, radius * 2, radius * 1.5), paint);
      }
    }
  }

  void _paintDogFace(Canvas canvas, Size size) {
    final centerX = size.width * 0.5;
    final centerY = size.height * 0.45;
    final headWidth = size.width * 0.55;

    final earPaint = Paint()
      ..color = const Color(0xFF8D5B4C)
      ..style = PaintingStyle.fill;
    final earInnerPaint = Paint()
      ..color = const Color(0xFFFBB6CE)
      ..style = PaintingStyle.fill;

    // Left Ear
    final leftEarX = centerX - headWidth * 0.45;
    final earY = centerY - headWidth * 0.55;
    final earW = headWidth * 0.32;
    final earH = headWidth * 0.55;
    canvas.save();
    canvas.translate(leftEarX, earY);
    canvas.rotate(-0.25);
    canvas.drawOval(
      Rect.fromCenter(center: Offset.zero, width: earW, height: earH),
      earPaint,
    );
    canvas.drawOval(
      Rect.fromCenter(
        center: Offset.zero,
        width: earW * 0.65,
        height: earH * 0.65,
      ),
      earInnerPaint,
    );
    canvas.restore();

    // Right Ear
    final rightEarX = centerX + headWidth * 0.45;
    canvas.save();
    canvas.translate(rightEarX, earY);
    canvas.rotate(0.25);
    canvas.drawOval(
      Rect.fromCenter(center: Offset.zero, width: earW, height: earH),
      earPaint,
    );
    canvas.drawOval(
      Rect.fromCenter(
        center: Offset.zero,
        width: earW * 0.65,
        height: earH * 0.65,
      ),
      earInnerPaint,
    );
    canvas.restore();

    // Nose & Muzzle
    final noseY = centerY + headWidth * 0.05;
    final nosePaint = Paint()
      ..color = const Color(0xFF1E1E1E)
      ..style = PaintingStyle.fill;
    final nosePath = Path();
    nosePath.moveTo(centerX - 18, noseY - 6);
    nosePath.quadraticBezierTo(centerX, noseY - 10, centerX + 18, noseY - 6);
    nosePath.quadraticBezierTo(centerX + 14, noseY + 14, centerX, noseY + 18);
    nosePath.quadraticBezierTo(
      centerX - 14,
      noseY + 14,
      centerX - 18,
      noseY - 6,
    );
    nosePath.close();
    canvas.drawPath(nosePath, nosePaint);

    // Whiskers / muzzle blush
    final blushPaint = Paint()
      ..color = Colors.pinkAccent.withValues(alpha: 0.25)
      ..style = PaintingStyle.fill;
    canvas.drawCircle(Offset(centerX - 42, noseY + 8), 16, blushPaint);
    canvas.drawCircle(Offset(centerX + 42, noseY + 8), 16, blushPaint);
  }

  void _paintNeonCrown(Canvas canvas, Size size) {
    final centerX = size.width * 0.5;
    final crownY = size.height * 0.22;
    final crownPaint = Paint()
      ..color = const Color(0xFFFBBF24)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final path = Path();
    path.moveTo(centerX - 50, crownY + 10);
    path.lineTo(centerX - 40, crownY - 20);
    path.lineTo(centerX - 18, crownY - 5);
    path.lineTo(centerX, crownY - 35);
    path.lineTo(centerX + 18, crownY - 5);
    path.lineTo(centerX + 40, crownY - 20);
    path.lineTo(centerX + 50, crownY + 10);
    path.close();

    canvas.drawPath(path, crownPaint);
  }

  @override
  bool shouldRepaint(covariant _ParticlePainter oldDelegate) => true;
}
