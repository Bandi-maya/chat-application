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
    final radius = math.min(size.width, size.height) / 7.5;
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
    setState(() => _pointer = null);
    if (widget.clearOnFinish) {
      // Small visual delay before clearing if required
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = theme.colorScheme.primary;
    final muted = theme.colorScheme.onSurface.withValues(alpha: 0.25);

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
    final linePaint = Paint()
      ..color = activeColor
      ..strokeWidth = 4.5
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..style = PaintingStyle.stroke;

    if (!hideTrace && selected.isNotEmpty) {
      final path = Path()
        ..moveTo(centers[selected.first].dx, centers[selected.first].dy);
      for (final index in selected.skip(1)) {
        path.lineTo(centers[index].dx, centers[index].dy);
      }
      if (pointer != null) path.lineTo(pointer!.dx, pointer!.dy);
      canvas.drawPath(path, linePaint);
    }

    for (var index = 0; index < centers.length; index++) {
      final isSelected = selected.contains(index);
      final outer = Paint()
        ..color = isSelected
            ? activeColor.withValues(alpha: 0.22)
            : inactiveColor.withValues(alpha: 0.16)
        ..style = PaintingStyle.fill;
      final border = Paint()
        ..color = isSelected ? activeColor : inactiveColor
        ..strokeWidth = isSelected ? 3 : 1.8
        ..style = PaintingStyle.stroke;

      canvas.drawCircle(centers[index], isSelected ? 20 : 16, outer);
      canvas.drawCircle(centers[index], isSelected ? 20 : 16, border);
      if (isSelected) {
        canvas.drawCircle(centers[index], 7, Paint()..color = activeColor);
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
