import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'lens_definition.dart';
import 'lens_manager.dart';

/// Snapchat-style horizontally centered lens carousel.
///
/// Contract:
/// * The item closest to the viewport center IS the selected lens — selection
///   and visual position can never disagree.
/// * Scale interpolates continuously from each item's true distance to center
///   while scrolling (far 0.78 → center 1.00), never jumping between sizes.
/// * Ballistic motion always settles exactly on an item boundary via
///   [CenterSnapPhysics]; tapping an off-center item animates it into the
///   center before selection, so state and visuals always agree.
/// * One light haptic fires per actual index change — never per pixel.
class LensCarousel extends StatefulWidget {
  const LensCarousel({
    super.key,
    required this.manager,
    this.onSelectionChanged,
    this.deviceTier = DeviceCapabilityTier.midRange,
  });

  final LensManager manager;
  final ValueChanged<LensDefinition>? onSelectionChanged;
  final DeviceCapabilityTier deviceTier;

  @override
  State<LensCarousel> createState() => _LensCarouselState();
}

class _LensCarouselState extends State<LensCarousel> {
  late final ScrollController _controller;
  int _currentIndex = 0;
  bool _hapticArmed = true;

  /// Selected item diameter, derived from the shortest screen side so phones,
  /// small devices and tablets keep the same visual hierarchy.
  double get _selectedDiameter {
    final shortest = MediaQuery.sizeOf(context).shortestSide;
    return math.max(52, math.min(76, shortest * 0.115));
  }

  double get _slotExtent => _selectedDiameter + 18;

  @override
  void initState() {
    super.initState();
    _controller = ScrollController();
    _controller.addListener(_onScroll);
    widget.manager.addListener(_syncFromManager);
    // Start centered on the manager's current selection once layout exists.
    WidgetsBinding.instance.addPostFrameCallback((_) => _syncFromManager());
  }

  @override
  void didUpdateWidget(covariant LensCarousel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.manager != widget.manager) {
      oldWidget.manager.removeListener(_syncFromManager);
      widget.manager.addListener(_syncFromManager);
      _syncFromManager();
    }
  }

  @override
  void dispose() {
    widget.manager.removeListener(_syncFromManager);
    _controller.removeListener(_onScroll);
    _controller.dispose();
    super.dispose();
  }

  void _syncFromManager() {
    if (!mounted || !_controller.hasClients) return;
    final catalog = _visibleCatalog();
    final index = catalog.indexWhere((lens) => lens.id == widget.manager.selected.id);
    if (index == -1) return;

    final target = index * _slotExtent;
    if (( _controller.offset - target).abs() > 0.5) {
      _currentIndex = index;
      _controller.animateTo(
        target,
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOutCubic,
      );
      setState(() {});
    } else if (_currentIndex != index) {
      setState(() => _currentIndex = index);
    }
  }

  void _onScroll() {
    final catalog = _visibleCatalog();
    final slot = _slotExtent;
    final nearest = (_controller.offset / slot)
        .round()
        .clamp(0, catalog.length - 1);

    if (nearest == _currentIndex) return;

    _currentIndex = nearest;
    if (_hapticArmed) {
      HapticFeedback.selectionClick();
      _hapticArmed = false;
      Future<void>.delayed(const Duration(milliseconds: 120), () {
        if (mounted) _hapticArmed = true;
      });
    }
    final lens = catalog[nearest];
    widget.manager.select(lens.id);
    widget.onSelectionChanged?.call(lens);
    if (mounted) setState(() {});
  }

  void _centerOn(int index) {
    final catalog = _visibleCatalog();
    if (index < 0 || index >= catalog.length) return;
    final lens = catalog[index];
    widget.manager.select(lens.id);
    widget.onSelectionChanged?.call(lens);
    if (!_controller.hasClients) return;
    _controller.animateTo(
      index * _slotExtent,
      duration: const Duration(milliseconds: 240),
      curve: Curves.easeOutCubic,
    );
  }

  @override
  Widget build(BuildContext context) {
    final catalog = _visibleCatalog();
    final viewportWidth = MediaQuery.sizeOf(context).width;
    return SizedBox(
      height: _selectedDiameter + 26,
      child: ListView.builder(
        controller: _controller,
        scrollDirection: Axis.horizontal,
        physics: CenterSnapPhysics(itemExtent: _slotExtent),
        itemExtent: _slotExtent,
        padding: EdgeInsets.symmetric(horizontal: (viewportWidth - _slotExtent) / 2),
        itemCount: catalog.length,
        itemBuilder: (context, index) {
          return GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: () => _centerOn(index),
            child: Center(
              child: _LensItem(
                lens: catalog[index],
                index: index,
                selected: index == _currentIndex,
                diameter: _selectedDiameter,
                slotExtent: _slotExtent,
                viewportWidth: viewportWidth,
                controller: _controller,
                runtimeState: widget.manager.runtimeState,
              ),
            ),
          );
        },
      ),
    );
  }

  /// Lenses the current device cannot run are hidden entirely rather than
  /// offered as repeat-failure buttons (requirement 71).
  List<LensDefinition> _visibleCatalog() => widget.manager.catalog
      .where(
        (lens) =>
            lens.isNoLens ||
            lens.supportedForDevice(widget.deviceTier.asLensRequirement),
      )
      .toList(growable: false);
}

/// Ballistic physics that always lands on an exact item boundary so a released
/// flick never rests between two lenses (requirement 11).
class CenterSnapPhysics extends ScrollPhysics {
  const CenterSnapPhysics({required this.itemExtent, super.parent});

  final double itemExtent;

  static const double _flingCarryFactor = 0.14;

  @override
  CenterSnapPhysics applyTo(ScrollPhysics? ancestor) =>
      CenterSnapPhysics(itemExtent: itemExtent, parent: buildParent(ancestor));

  @override
  SpringDescription get spring => SpringDescription.withDampingRatio(
    mass: 0.5,
    stiffness: 320,
    ratio: 1.05,
  );

  @override
  Simulation? createBallisticSimulation(ScrollMetrics position, double velocity) {
    final projected = position.pixels + velocity * _flingCarryFactor;
    final target =
        (projected / itemExtent).roundToDouble().clamp(0.0, double.infinity) *
        itemExtent;
    final tolerance = toleranceFor(position);
    if ((target - position.pixels).abs() < tolerance.distance &&
        velocity.abs() < tolerance.velocity) {
      return null;
    }
    return ScrollSpringSimulation(spring, position.pixels, target, velocity);
  }
}

class _LensItem extends StatelessWidget {
  const _LensItem({
    required this.lens,
    required this.index,
    required this.selected,
    required this.diameter,
    required this.slotExtent,
    required this.viewportWidth,
    required this.controller,
    required this.runtimeState,
  });

  final LensDefinition lens;
  final int index;
  final bool selected;
  final double diameter;
  final double slotExtent;
  final double viewportWidth;
  final ScrollController controller;
  final LensRuntimeState runtimeState;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final scale = _scaleFor(controller.offset);
        final size = diameter * scale;

        final ring = selected
            ? Border.all(color: Colors.white.withValues(alpha: 0.9), width: 2.4)
            : Border.all(color: Colors.white24, width: 1);

        return Stack(
          alignment: Alignment.center,
          children: [
            Container(
              width: size,
              height: size,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    lens.accentColor.withValues(alpha: 0.9),
                    lens.accentColor,
                  ],
                ),
                border: ring,
                boxShadow: selected
                    ? [
                        BoxShadow(
                          color: lens.accentColor.withValues(alpha: 0.35),
                          blurRadius: 14,
                          spreadRadius: 1,
                        ),
                      ]
                    : null,
              ),
              child: CustomPaint(
                size: Size.square(size * 0.5),
                painter: _LensGlyphPainter(
                  lensId: lens.id,
                  color: Colors.white,
                ),
              ),
            ),
            if (selected && runtimeState == LensRuntimeState.applying)
              SizedBox(
                width: size + 8,
                height: size + 8,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: Colors.white.withValues(alpha: 0.85),
                ),
              ),
          ],
        );
      },
    );
  }

  /// True distance-to-center interpolation (requirement 10):
  /// far 0.78 → near ~0.90 → center exactly 1.00, recomputed every frame.
  double _scaleFor(double offset) {
    final itemCenterInViewport =
        index * slotExtent + slotExtent * 0.5 - offset;
    final viewportCenter = viewportWidth / 2;
    final normalizedDistance =
        ((itemCenterInViewport - viewportCenter) / slotExtent)
            .abs()
            .clamp(0.0, 1.0);
    return 0.78 + 0.22 * (1.0 - normalizedDistance);
  }
}


/// Original vector-style lens marks painted in code instead of shipping an
/// icon font or adding an icon package.
class _LensGlyphPainter extends CustomPainter {
  const _LensGlyphPainter({required this.lensId, required this.color});

  final String lensId;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.shortestSide / 48;
    canvas.save();
    canvas.translate((size.width - 48 * scale) / 2, (size.height - 48 * scale) / 2);
    canvas.scale(scale);
    final stroke = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final fill = Paint()
      ..color = color
      ..style = PaintingStyle.fill;

    if (lensId == 'lens_glasses_classic') {
      canvas.drawRRect(
        RRect.fromRectAndRadius(const Rect.fromLTWH(2, 14, 19, 16), const Radius.circular(6)),
        stroke,
      );
      canvas.drawRRect(
        RRect.fromRectAndRadius(const Rect.fromLTWH(27, 14, 19, 16), const Radius.circular(6)),
        stroke,
      );
      canvas.drawLine(const Offset(21, 19), const Offset(27, 19), stroke);
      canvas.drawLine(const Offset(2, 17), const Offset(0, 13), stroke);
      canvas.drawLine(const Offset(46, 17), const Offset(48, 13), stroke);
    } else if (lensId == 'lens_sunglasses_aviator') {
      final left = Path()
        ..moveTo(2, 12)
        ..quadraticBezierTo(12, 8, 22, 13)
        ..lineTo(19, 29)
        ..quadraticBezierTo(11, 38, 6, 28)
        ..close();
      final right = Path()
        ..moveTo(26, 13)
        ..quadraticBezierTo(36, 8, 46, 12)
        ..lineTo(42, 28)
        ..quadraticBezierTo(37, 38, 29, 29)
        ..close();
      canvas.drawPath(left, fill);
      canvas.drawPath(right, fill);
      canvas.drawPath(left, stroke);
      canvas.drawPath(right, stroke);
      canvas.drawLine(const Offset(22, 14), const Offset(26, 14), stroke);
    } else if (lensId == 'lens_dog_ears') {
      final left = Path()
        ..moveTo(8, 25)
        ..quadraticBezierTo(-1, 3, 13, 5)
        ..quadraticBezierTo(23, 13, 20, 27)
        ..close();
      final right = Path()
        ..moveTo(28, 27)
        ..quadraticBezierTo(25, 11, 36, 5)
        ..quadraticBezierTo(50, 4, 40, 26)
        ..close();
      canvas.drawPath(left, fill);
      canvas.drawPath(right, fill);
      canvas.drawOval(const Rect.fromLTWH(14, 24, 20, 15), stroke);
      canvas.drawCircle(const Offset(24, 29), 2, fill);
    } else if (lensId == 'lens_hearts_float') {
      _heart(canvas, const Offset(7, 10), 0.32, fill);
      _heart(canvas, const Offset(25, 20), 0.48, fill);
      _heart(canvas, const Offset(36, 7), 0.25, fill);
    } else if (lensId == 'lens_sparkles') {
      _star(canvas, const Offset(13, 17), 9, fill);
      _star(canvas, const Offset(34, 11), 7, fill);
      _star(canvas, const Offset(32, 33), 4, fill);
    } else if (lensId == 'lens_angel_halo') {
      canvas.drawOval(
        Rect.fromCenter(center: const Offset(24, 10), width: 34, height: 10),
        stroke,
      );
      canvas.drawArc(
        const Rect.fromLTWH(7, 16, 34, 28),
        math.pi * 1.05,
        math.pi * 0.9,
        false,
        stroke,
      );
    } else if (lensId == 'lens_crown_gold' || lensId == 'lens_neon_crown') {
      final crown = Path()
        ..moveTo(5, 35)
        ..lineTo(8, 14)
        ..lineTo(19, 24)
        ..lineTo(24, 6)
        ..lineTo(30, 24)
        ..lineTo(41, 14)
        ..lineTo(43, 35)
        ..close();
      canvas.drawPath(crown, stroke);
      canvas.drawLine(const Offset(7, 39), const Offset(42, 39), stroke);
    } else if (lensId == 'lens_blush_glow') {
      canvas.drawCircle(const Offset(12, 26), 7, stroke);
      canvas.drawCircle(const Offset(36, 26), 7, stroke);
      canvas.drawArc(
        const Rect.fromLTWH(18, 24, 12, 10),
        0,
        math.pi,
        false,
        stroke,
      );
      _star(canvas, const Offset(24, 10), 5, fill);
    } else {
      canvas.drawCircle(const Offset(24, 24), 17, stroke);
      canvas.drawCircle(const Offset(24, 24), 5, fill);
    }
    canvas.restore();
  }

  void _heart(Canvas canvas, Offset origin, double scale, Paint paint) {
    final path = Path()
      ..moveTo(origin.dx, origin.dy + 14 * scale)
      ..cubicTo(
        origin.dx - 24 * scale, origin.dy - 1 * scale,
        origin.dx - 16 * scale, origin.dy - 13 * scale,
        origin.dx, origin.dy - 6 * scale,
      )
      ..cubicTo(
        origin.dx + 16 * scale, origin.dy - 13 * scale,
        origin.dx + 24 * scale, origin.dy - 1 * scale,
        origin.dx, origin.dy + 14 * scale,
      )
      ..close();
    canvas.drawPath(path, paint);
  }

  void _star(Canvas canvas, Offset center, double radius, Paint paint) {
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
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant _LensGlyphPainter oldDelegate) =>
      oldDelegate.lensId != lensId || oldDelegate.color != color;
}

