import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class PatternLockPad extends StatefulWidget {
  final ValueChanged<String> onPatternComplete;
  final VoidCallback? onPatternReset;
  final bool hideTrace;
  final bool enableHaptics;
  final double size;
  final bool clearOnFinish;

  const PatternLockPad({
    super.key,
    required this.onPatternComplete,
    this.onPatternReset,
    this.hideTrace = false,
    this.enableHaptics = true,
    this.size = 280,
    this.clearOnFinish = true,
  });

  @override
  State<PatternLockPad> createState() => PatternLockPadState();
}

class PatternLockPadState extends State<PatternLockPad> {
  final List<int> _selected = <int>[];
  Offset? _pointer;

  List<int> get currentPattern => List<int>.unmodifiable(_selected);

  void reset() {
    if (!mounted) return;
    setState(() {
      _selected.clear();
      _pointer = null;
    });
    widget.onPatternReset?.call();
  }

  List<Offset> _centers(Size size) {
    final cellWidth = size.width / 3;
    final cellHeight = size.height / 3;
    return List<Offset>.generate(9, (index) {
      final column = index % 3;
      final row = index ~/ 3;
      return Offset(cellWidth * (column + 0.5), cellHeight * (row + 0.5));
    });
  }

  int? _hitTest(Offset localPosition, Size size) {
    final centers = _centers(size);
    final radius = math.min(size.width, size.height) / 7.0;
    for (var index = 0; index < centers.length; index++) {
      if ((centers[index] - localPosition).distance <= radius) return index;
    }
    return null;
  }

  void _selectAt(Offset localPosition, Size size) {
    final hit = _hitTest(localPosition, size);
    if (hit == null || _selected.contains(hit)) return;
    if (widget.enableHaptics) HapticFeedback.selectionClick();
    setState(() {
      _selected.add(hit);
      _pointer = localPosition;
    });
  }

  void _finish() {
    if (_selected.isNotEmpty) {
      widget.onPatternComplete(_selected.join('-'));
    }
    if (mounted) {
      setState(() => _pointer = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = theme.colorScheme.primary;
    final muted = theme.colorScheme.onSurface.withValues(alpha: 0.35);

    return Semantics(
      label: 'Pattern lock grid 3 by 3',
      child: SizedBox.square(
        dimension: widget.size,
        child: LayoutBuilder(
          builder: (context, constraints) {
            final size = Size(constraints.maxWidth, constraints.maxHeight);
            return GestureDetector(
              behavior: HitTestBehavior.opaque,
              onPanStart: (details) {
                if (widget.clearOnFinish) reset();
                _selectAt(details.localPosition, size);
              },
              onPanUpdate: (details) {
                _selectAt(details.localPosition, size);
                if (mounted) setState(() => _pointer = details.localPosition);
              },
              onPanEnd: (_) => _finish(),
              onPanCancel: _finish,
              child: CustomPaint(
                painter: _PatternPainter(
                  selected: _selected,
                  pointer: _pointer,
                  activeColor: color,
                  inactiveColor: muted,
                  hideTrace: widget.hideTrace,
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

class _PatternPainter extends CustomPainter {
  final List<int> selected;
  final Offset? pointer;
  final Color activeColor;
  final Color inactiveColor;
  final bool hideTrace;

  const _PatternPainter({
    required this.selected,
    required this.pointer,
    required this.activeColor,
    required this.inactiveColor,
    required this.hideTrace,
  });

  List<Offset> _centers(Size size) {
    final cellWidth = size.width / 3;
    final cellHeight = size.height / 3;
    return List<Offset>.generate(9, (index) {
      final column = index % 3;
      final row = index ~/ 3;
      return Offset(cellWidth * (column + 0.5), cellHeight * (row + 0.5));
    });
  }

  @override
  void paint(Canvas canvas, Size size) {
    final centers = _centers(size);

    // Draw lines connecting selected dots if trace is NOT hidden
    if (!hideTrace && selected.isNotEmpty) {
      // Glow underlay for trace
      final glowPaint = Paint()
        ..color = activeColor.withValues(alpha: 0.25)
        ..strokeWidth = 10.0
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..style = PaintingStyle.stroke;

      final linePaint = Paint()
        ..color = activeColor
        ..strokeWidth = 4.5
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..style = PaintingStyle.stroke;

      final path = Path()
        ..moveTo(centers[selected.first].dx, centers[selected.first].dy);
      for (final index in selected.skip(1)) {
        path.lineTo(centers[index].dx, centers[index].dy);
      }
      if (pointer != null) {
        path.lineTo(pointer!.dx, pointer!.dy);
      }
      canvas.drawPath(path, glowPaint);
      canvas.drawPath(path, linePaint);
    }

    // Draw all 9 nodes
    for (var index = 0; index < centers.length; index++) {
      final isSelected = selected.contains(index);

      if (isSelected) {
        // Outer glow halo when selected
        if (!hideTrace) {
          final outerHalo = Paint()
            ..color = activeColor.withValues(alpha: 0.18)
            ..style = PaintingStyle.fill;
          canvas.drawCircle(centers[index], 28, outerHalo);

          final outerBorder = Paint()
            ..color = activeColor.withValues(alpha: 0.75)
            ..strokeWidth = 2.0
            ..style = PaintingStyle.stroke;
          canvas.drawCircle(centers[index], 24, outerBorder);
        }

        // Inner solid dot with core shine
        final centerDot = Paint()
          ..color = (!hideTrace) ? activeColor : inactiveColor
          ..style = PaintingStyle.fill;
        canvas.drawCircle(centers[index], (!hideTrace) ? 8.5 : 6.5, centerDot);

        if (!hideTrace) {
          final centerCore = Paint()
            ..color = Colors.white.withValues(alpha: 0.8)
            ..style = PaintingStyle.fill;
          canvas.drawCircle(centers[index], 3.0, centerCore);
        }
      } else {
        // Inactive unselected dot with subtle glass ring
        final inactiveRing = Paint()
          ..color = inactiveColor.withValues(alpha: 0.14)
          ..strokeWidth = 1.5
          ..style = PaintingStyle.stroke;
        canvas.drawCircle(centers[index], 20, inactiveRing);

        final inactiveDot = Paint()
          ..color = inactiveColor.withValues(alpha: 0.65)
          ..style = PaintingStyle.fill;
        canvas.drawCircle(centers[index], 6, inactiveDot);
      }
    }
  }

  @override
  bool shouldRepaint(covariant _PatternPainter oldDelegate) {
    return oldDelegate.selected != selected ||
        oldDelegate.pointer != pointer ||
        oldDelegate.activeColor != activeColor ||
        oldDelegate.inactiveColor != inactiveColor ||
        oldDelegate.hideTrace != hideTrace;
  }
}
