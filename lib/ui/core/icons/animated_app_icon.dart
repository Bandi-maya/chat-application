import 'package:flutter/material.dart';

enum IconMotionState { idle, pressed, activated, deactivated, success }

enum SemanticIconType { task, pin, archive, lock, favourite, mute, search }

/// Dynamic semantic icon animator providing rich micro-interactions (circle->tick, pin settle, archive drawer slide, lock shackle pivot).
class AnimatedAppIcon extends StatefulWidget {
  final SemanticIconType type;
  final bool isActivated;
  final VoidCallback? onTap;
  final Color? color;
  final Color? activeColor;
  final double size;
  final String? semanticLabel;

  const AnimatedAppIcon({
    super.key,
    required this.type,
    this.isActivated = false,
    this.onTap,
    this.color,
    this.activeColor,
    this.size = 24.0,
    this.semanticLabel,
  });

  @override
  State<AnimatedAppIcon> createState() => _AnimatedAppIconState();
}

class _AnimatedAppIconState extends State<AnimatedAppIcon>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 320),
      value: widget.isActivated ? 1.0 : 0.0,
    );
    _animation = CurvedAnimation(
      parent: _ctrl,
      curve: Curves.easeOutBack,
      reverseCurve: Curves.easeIn,
    );
  }

  @override
  void didUpdateWidget(covariant AnimatedAppIcon oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.isActivated != widget.isActivated) {
      if (widget.isActivated) {
        _ctrl.forward(from: 0.0);
      } else {
        _ctrl.reverse(from: 1.0);
      }
    }
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final effectiveColor = widget.isActivated
        ? (widget.activeColor ?? Theme.of(context).colorScheme.primary)
        : (widget.color ?? Theme.of(context).iconTheme.color ?? Colors.white);

    final content = AnimatedBuilder(
      animation: _animation,
      builder: (context, child) {
        return CustomPaint(
          size: Size(widget.size, widget.size),
          painter: _SemanticIconPainter(
            type: widget.type,
            progress: _animation.value,
            color: effectiveColor,
          ),
        );
      },
    );

    if (widget.onTap == null) {
      return Semantics(label: widget.semanticLabel, child: content);
    }

    return Semantics(
      label: widget.semanticLabel,
      button: true,
      child: GestureDetector(
        onTap: widget.onTap,
        behavior: HitTestBehavior.opaque,
        child: content,
      ),
    );
  }
}

class _SemanticIconPainter extends CustomPainter {
  final SemanticIconType type;
  final double progress;
  final Color color;

  const _SemanticIconPainter({
    required this.type,
    required this.progress,
    required this.color,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.8
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final fillPaint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;

    switch (type) {
      // 1. Task: Circle draws -> Checkmark draws with subtle settle
      case SemanticIconType.task:
        canvas.drawCircle(
          Offset(size.width / 2, size.height / 2),
          size.width * 0.42,
          paint,
        );
        if (progress > 0.0) {
          final checkPath = Path();
          final start = Offset(size.width * 0.30, size.height * 0.52);
          final mid = Offset(size.width * 0.46, size.height * 0.68);
          final end = Offset(size.width * 0.72, size.height * 0.36);

          if (progress <= 0.5) {
            final t = progress / 0.5;
            checkPath.moveTo(start.dx, start.dy);
            checkPath.lineTo(
              start.dx + (mid.dx - start.dx) * t,
              start.dy + (mid.dy - start.dy) * t,
            );
          } else {
            final t = (progress - 0.5) / 0.5;
            checkPath.moveTo(start.dx, start.dy);
            checkPath.lineTo(mid.dx, mid.dy);
            checkPath.lineTo(
              mid.dx + (end.dx - mid.dx) * t,
              mid.dy + (end.dy - mid.dy) * t,
            );
          }
          canvas.drawPath(checkPath, paint);
        }
        break;

      // 2. Pin: Drops and settles
      case SemanticIconType.pin:
        final dy = (1.0 - progress) * -4.0;
        final p = Path()
          ..moveTo(size.width * 0.35, size.height * 0.18 + dy)
          ..lineTo(size.width * 0.65, size.height * 0.18 + dy)
          ..lineTo(size.width * 0.55, size.height * 0.45 + dy)
          ..lineTo(size.width * 0.75, size.height * 0.60 + dy)
          ..lineTo(size.width * 0.25, size.height * 0.60 + dy)
          ..lineTo(size.width * 0.45, size.height * 0.45 + dy)
          ..close();
        p.moveTo(size.width * 0.5, size.height * 0.60 + dy);
        p.lineTo(size.width * 0.5, size.height * 0.88 + dy);
        canvas.drawPath(p, paint);
        break;

      // 3. Archive: Box drawer slides
      case SemanticIconType.archive:
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            Rect.fromLTWH(
              size.width * 0.12,
              size.height * 0.15,
              size.width * 0.76,
              size.height * 0.22,
            ),
            const Radius.circular(3),
          ),
          paint,
        );
        final body = Path()
          ..moveTo(size.width * 0.20, size.height * 0.37)
          ..lineTo(size.width * 0.20, size.height * 0.82)
          ..lineTo(size.width * 0.80, size.height * 0.82)
          ..lineTo(size.width * 0.80, size.height * 0.37);
        canvas.drawPath(body, paint);
        // Animated drawer notch
        final notchY = size.height * (0.55 + progress * 0.06);
        canvas.drawLine(
          Offset(size.width * 0.38, notchY),
          Offset(size.width * 0.62, notchY),
          paint,
        );
        break;

      // 4. Lock / Unlock: Shackle pivots open/shut
      case SemanticIconType.lock:
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            Rect.fromLTWH(
              size.width * 0.2,
              size.height * 0.44,
              size.width * 0.6,
              size.height * 0.44,
            ),
            const Radius.circular(4),
          ),
          paint,
        );
        final shackleH = size.height * (0.24 - progress * 0.08);
        final shackle = Path()
          ..moveTo(size.width * 0.34, size.height * 0.44)
          ..lineTo(size.width * 0.34, shackleH)
          ..arcToPoint(
            Offset(size.width * 0.66, shackleH),
            radius: Radius.circular(size.width * 0.16),
          )
          ..lineTo(
            size.width * 0.66,
            size.height * 0.44 * (1.0 - progress * 0.4),
          );
        canvas.drawPath(shackle, paint);
        break;

      // 5. Favourite: Heart outline fills & springs
      case SemanticIconType.favourite:
        final p = Path()
          ..moveTo(size.width * 0.5, size.height * 0.82)
          ..cubicTo(
            size.width * 0.2,
            size.height * 0.55,
            size.width * 0.08,
            size.height * 0.35,
            size.width * 0.25,
            size.height * 0.20,
          )
          ..cubicTo(
            size.width * 0.38,
            size.height * 0.10,
            size.width * 0.48,
            size.height * 0.24,
            size.width * 0.5,
            size.height * 0.28,
          )
          ..cubicTo(
            size.width * 0.52,
            size.height * 0.24,
            size.width * 0.62,
            size.height * 0.10,
            size.width * 0.75,
            size.height * 0.20,
          )
          ..cubicTo(
            size.width * 0.92,
            size.height * 0.35,
            size.width * 0.80,
            size.height * 0.55,
            size.width * 0.5,
            size.height * 0.82,
          )
          ..close();
        if (progress > 0.0) {
          canvas.drawPath(
            p,
            fillPaint..color = color.withValues(alpha: progress),
          );
        }
        canvas.drawPath(p, paint);
        break;

      // 6. Mute: Speaker with animated slash
      case SemanticIconType.mute:
        final p = Path()
          ..moveTo(size.width * 0.15, size.height * 0.38)
          ..lineTo(size.width * 0.35, size.height * 0.38)
          ..lineTo(size.width * 0.60, size.height * 0.15)
          ..lineTo(size.width * 0.60, size.height * 0.85)
          ..lineTo(size.width * 0.35, size.height * 0.62)
          ..lineTo(size.width * 0.15, size.height * 0.62)
          ..close();
        canvas.drawPath(p, paint);
        if (progress > 0.0) {
          canvas.drawLine(
            Offset(size.width * 0.72, size.height * 0.35),
            Offset(
              size.width * (0.72 + 0.18 * progress),
              size.height * (0.35 + 0.30 * progress),
            ),
            paint,
          );
        }
        break;

      // 7. Search: Lens expands subtly
      case SemanticIconType.search:
        final radius = size.width * (0.24 + progress * 0.04);
        canvas.drawCircle(
          Offset(size.width * 0.42, size.height * 0.42),
          radius,
          paint,
        );
        canvas.drawLine(
          Offset(size.width * 0.60, size.height * 0.60),
          Offset(size.width * 0.86, size.height * 0.86),
          paint,
        );
        break;
    }
  }

  @override
  bool shouldRepaint(covariant _SemanticIconPainter oldDelegate) {
    return oldDelegate.progress != progress ||
        oldDelegate.color != color ||
        oldDelegate.type != type;
  }
}
