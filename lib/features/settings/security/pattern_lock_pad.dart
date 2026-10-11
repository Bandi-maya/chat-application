import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class PatternLockPad extends StatefulWidget {
  final ValueChanged<String> onPatternComplete;
  final VoidCallback? onPatternReset;
  final bool hideTrace;
  final bool enableHaptics;
  final double size;
  final int gridSize;
  final bool clearOnFinish;

  const PatternLockPad({
    super.key,
    required this.onPatternComplete,
    this.onPatternReset,
    this.hideTrace = false,
    this.enableHaptics = true,
    this.size = 280,
    this.gridSize = 3,
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
    final gridSize = widget.gridSize == 4 ? 4 : 3;
    final cellWidth = size.width / gridSize;
    final cellHeight = size.height / gridSize;
    return List<Offset>.generate(gridSize * gridSize, (index) {
      final column = index % gridSize;
      final row = index ~/ gridSize;
      return Offset(cellWidth * (column + 0.5), cellHeight * (row + 0.5));
    });
  }

  int? _hitTest(Offset localPosition, Size size) {
    final centers = _centers(size);
    final radius = math.min(size.width, size.height) / (widget.gridSize == 4 ? 4 : 3) * 0.36;
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

  double _distanceToSegment(Offset point, Offset start, Offset end) {
    final delta = end - start;
    final lengthSquared = delta.dx * delta.dx + delta.dy * delta.dy;
    if (lengthSquared == 0) return (point - start).distance;
    final projection = (((point.dx - start.dx) * delta.dx) +
            ((point.dy - start.dy) * delta.dy)) /
        lengthSquared;
    final t = projection.clamp(0.0, 1.0).toDouble();
    final nearest = Offset(start.dx + delta.dx * t, start.dy + delta.dy * t);
    return (point - nearest).distance;
  }

  void _selectAlongSegment(Offset start, Offset end, Size size) {
    final gridSize = widget.gridSize == 4 ? 4 : 3;
    final centers = _centers(size);
    final hitRadius = math.min(size.width, size.height) / gridSize * 0.30;
    final additions = <int>[];
    for (var index = 0; index < centers.length; index++) {
      if (!_selected.contains(index) &&
          _distanceToSegment(centers[index], start, end) <= hitRadius) {
        additions.add(index);
      }
    }
    final endHit = _hitTest(end, size);
    if (endHit != null && !_selected.contains(endHit) &&
        !additions.contains(endHit)) {
      additions.add(endHit);
    }
    final delta = end - start;
    final lengthSquared = delta.dx * delta.dx + delta.dy * delta.dy;
    additions.sort((a, b) {
      if (lengthSquared == 0) return 0;
      double progress(int index) {
        final offset = centers[index] - start;
        return ((offset.dx * delta.dx) + (offset.dy * delta.dy)) /
            lengthSquared;
      }
      return progress(a).compareTo(progress(b));
    });
    if (widget.enableHaptics && additions.isNotEmpty) {
      HapticFeedback.selectionClick();
    }
    setState(() {
      _selected.addAll(additions);
      _pointer = end;
    });
  }

  void _finish() {
    if (_selected.isNotEmpty) {
      final pattern = _selected.join('-');
      widget.onPatternComplete(pattern);
    }
    if (mounted) {
      setState(() {
        _pointer = null;
        if (widget.clearOnFinish) _selected.clear();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = theme.colorScheme.primary;
    final muted = theme.colorScheme.onSurface.withValues(alpha: 0.35);

    return Semantics(
      label:
          'Pattern lock grid ${widget.gridSize == 4 ? 4 : 3} by ${widget.gridSize == 4 ? 4 : 3}',
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
                final start = _pointer ?? details.localPosition;
                _selectAlongSegment(start, details.localPosition, size);
              },
              onPanEnd: (_) => _finish(),
              // A system interruption must not submit a partial credential.
              onPanCancel: reset,
              child: CustomPaint(
                painter: _PatternPainter(
                  selected: _selected,
                  pointer: _pointer,
                  activeColor: color,
                  inactiveColor: muted,
                  hideTrace: widget.hideTrace,
                  gridSize: widget.gridSize == 4 ? 4 : 3,
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
  final int gridSize;

  const _PatternPainter({
    required this.selected,
    required this.pointer,
    required this.activeColor,
    required this.inactiveColor,
    required this.hideTrace,
    required this.gridSize,
  });

  List<Offset> _centers(Size size) {
    final cellWidth = size.width / gridSize;
    final cellHeight = size.height / gridSize;
    return List<Offset>.generate(gridSize * gridSize, (index) {
      final column = index % gridSize;
      final row = index ~/ gridSize;
      return Offset(cellWidth * (column + 0.5), cellHeight * (row + 0.5));
    });
  }

  @override
  void paint(Canvas canvas, Size size) {
    final centers = _centers(size);
    final cellSize = math.min(size.width, size.height) / gridSize;
    final haloRadius = cellSize * 0.34;
    final ringRadius = cellSize * 0.28;
    final dotRadius = cellSize * 0.085;

    // Draw lines connecting selected dots if trace is NOT hidden
    if (!hideTrace && selected.isNotEmpty) {
      // Glow underlay for trace
      final glowPaint = Paint()
        ..color = activeColor.withValues(alpha: 0.25)
        ..strokeWidth = cellSize * 0.14
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..style = PaintingStyle.stroke;

      final linePaint = Paint()
        ..color = activeColor
        ..strokeWidth = cellSize * 0.065
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
          canvas.drawCircle(centers[index], haloRadius, outerHalo);

          final outerBorder = Paint()
            ..color = activeColor.withValues(alpha: 0.75)
            ..strokeWidth = 2.0
            ..style = PaintingStyle.stroke;
          canvas.drawCircle(centers[index], ringRadius * 1.15, outerBorder);
        }

        // Inner solid dot with core shine
        final centerDot = Paint()
          ..color = (!hideTrace) ? activeColor : inactiveColor
          ..style = PaintingStyle.fill;
        canvas.drawCircle(
          centers[index],
          (!hideTrace) ? dotRadius * 1.45 : dotRadius,
          centerDot,
        );

        if (!hideTrace) {
          final centerCore = Paint()
            ..color = Colors.white.withValues(alpha: 0.8)
            ..style = PaintingStyle.fill;
          canvas.drawCircle(centers[index], dotRadius * 0.5, centerCore);
        }
      } else {
        // Inactive unselected dot with subtle glass ring
        final inactiveRing = Paint()
          ..color = inactiveColor.withValues(alpha: 0.14)
          ..strokeWidth = 1.5
          ..style = PaintingStyle.stroke;
        canvas.drawCircle(centers[index], ringRadius, inactiveRing);

        final inactiveDot = Paint()
          ..color = inactiveColor.withValues(alpha: 0.65)
          ..style = PaintingStyle.fill;
        canvas.drawCircle(centers[index], dotRadius, inactiveDot);
      }
    }
  }

  @override
  bool shouldRepaint(covariant _PatternPainter oldDelegate) {
    return oldDelegate.selected != selected ||
        oldDelegate.pointer != pointer ||
        oldDelegate.activeColor != activeColor ||
        oldDelegate.inactiveColor != inactiveColor ||
        oldDelegate.hideTrace != hideTrace ||
        oldDelegate.gridSize != gridSize;
  }
}
