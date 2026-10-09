import 'package:flutter/material.dart';

/// Vector CustomPainter rendering the flying bird silhouette from Image 1.
///
/// Features an aerodynamic bird in dynamic flight with extended curved wings,
/// a streamlined body, sharp pointed beak, and forked tail.
class FlyingBirdPainter extends CustomPainter {
  final Color color;

  const FlyingBirdPainter({required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;

    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;

    // Coordinate space normalized to 100x100
    // Bird in Image 1 is flying upwards-right:
    // Left wing reaches back-left: tip around (18, 42)
    // Beak points to right: tip around (82, 53)
    // Right (upper) wing reaches upwards-right: tip around (72, 41)
    // Tail is at bottom-center: tip around (40, 59)
    final path = Path();

    // Start at top-left wing tip
    path.moveTo(w * 0.18, h * 0.42);

    // Leading edge of left wing sweeping down and right to breast/chest
    path.cubicTo(
      w * 0.36,
      h * 0.46,
      w * 0.48,
      h * 0.52,
      w * 0.53,
      h * 0.57,
    );

    // Underbelly to tail fork
    path.cubicTo(
      w * 0.49,
      h * 0.58,
      w * 0.45,
      h * 0.58,
      w * 0.40,
      h * 0.59,
    );

    // Tail outer edge to lower body
    path.cubicTo(
      w * 0.48,
      h * 0.56,
      w * 0.52,
      h * 0.54,
      w * 0.55,
      h * 0.52,
    );

    // Underbelly towards head
    path.cubicTo(
      w * 0.62,
      h * 0.58,
      w * 0.70,
      h * 0.57,
      w * 0.74,
      h * 0.53,
    );

    // Sharp beak pointing right
    path.lineTo(w * 0.82, h * 0.53);
    path.lineTo(w * 0.75, h * 0.51);

    // Crown of head and back towards upper wing
    path.cubicTo(
      w * 0.70,
      h * 0.50,
      w * 0.67,
      h * 0.47,
      w * 0.66,
      h * 0.44,
    );

    // Upper/right wing leading edge going up
    path.cubicTo(
      w * 0.67,
      h * 0.41,
      w * 0.70,
      h * 0.38,
      w * 0.74,
      h * 0.41,
    );

    // Upper wing tip and trailing edge curving down to back
    path.cubicTo(
      w * 0.69,
      h * 0.46,
      w * 0.64,
      h * 0.48,
      w * 0.60,
      h * 0.50,
    );

    // Back of bird curving into left wing trailing edge
    path.cubicTo(
      w * 0.54,
      h * 0.47,
      w * 0.42,
      h * 0.43,
      w * 0.18,
      h * 0.42,
    );

    path.close();
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant FlyingBirdPainter oldDelegate) =>
      oldDelegate.color != color;
}

/// Standalone Flying Bird Icon matching Image 1
class FlyingBirdIcon extends StatelessWidget {
  final double size;
  final Color? color;
  final Color? backgroundColor;
  final double borderRadius;

  const FlyingBirdIcon({
    super.key,
    this.size = 40,
    this.color,
    this.backgroundColor,
    this.borderRadius = 12,
  });

  @override
  Widget build(BuildContext context) {
    final effectiveColor =
        color ?? (Theme.of(context).brightness == Brightness.dark ? Colors.white : Colors.black);
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final bird = SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: FlyingBirdPainter(color: effectiveColor),
      ),
    );

    if (backgroundColor == null && borderRadius == 0) {
      return bird;
    }

    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: backgroundColor ?? (isDark ? const Color(0xFF1E2024) : Colors.white),
        borderRadius: BorderRadius.circular(borderRadius),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.35 : 0.08),
            blurRadius: 8,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: Center(
        child: SizedBox(
          width: size * 0.82,
          height: size * 0.82,
          child: CustomPaint(
            painter: FlyingBirdPainter(color: effectiveColor),
          ),
        ),
      ),
    );
  }
}
