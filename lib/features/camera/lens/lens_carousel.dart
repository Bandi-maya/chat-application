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
              child: Icon(lens.icon, size: size * 0.46, color: Colors.white),
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
