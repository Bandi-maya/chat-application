import 'dart:io';

import 'package:flutter/material.dart';

import '../controllers/app_icon_controller.dart';

enum ChatyBrandIconVariant { outline, filled }

/// Canonical Chaty communication brand glyph painter.
///
/// Features an original rounded conversation mark with a signature Chaty
/// dynamic tail and internal connectivity motif. Both outline and filled
/// variants share identical vector geometry.
class ChatyBrandGlyphPainter extends CustomPainter {
  final ChatyBrandIconVariant variant;
  final Color color;
  final double strokeWidth;

  const ChatyBrandGlyphPainter({
    required this.variant,
    required this.color,
    this.strokeWidth = 3.0,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;

    final paint = Paint()
      ..color = color
      ..style = variant == ChatyBrandIconVariant.filled
          ? PaintingStyle.fill
          : PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final cx = w * 0.52;
    final cy = h * 0.44;
    final radius = w * 0.36;

    final bubblePath = Path()
      ..addOval(Rect.fromCircle(center: Offset(cx, cy), radius: radius));

    // Signature tapered triangular dynamic tail at bottom-left
    final tailPath = Path()
      ..moveTo(cx - radius * 0.72, cy + radius * 0.65)
      ..lineTo(w * 0.16, h * 0.88)
      ..lineTo(cx - radius * 0.10, cy + radius * 0.95)
      ..close();

    final combinedPath = Path.combine(
      PathOperation.union,
      bubblePath,
      tailPath,
    );
    canvas.drawPath(combinedPath, paint);

    // Signature 3-dot communicative face / connection motif
    // Top dot (higher center) and two bottom dots (left and right) in inverted triangle formation
    final dotRadius = w * 0.052;
    final dotColor = variant == ChatyBrandIconVariant.filled
        ? (color.computeLuminance() > 0.6
              ? const Color(0xFF1E1E1E)
              : Colors.white)
        : color;

    final dotPaint = Paint()
      ..color = dotColor
      ..style = PaintingStyle.fill;

    // Top center dot
    canvas.drawCircle(Offset(cx, cy + h * 0.04), dotRadius, dotPaint);
    // Bottom-left dot
    canvas.drawCircle(
      Offset(cx - w * 0.12, cy - h * 0.02),
      dotRadius,
      dotPaint,
    );
    // Bottom-right dot
    canvas.drawCircle(
      Offset(cx + w * 0.12, cy - h * 0.02),
      dotRadius,
      dotPaint,
    );
  }

  @override
  bool shouldRepaint(covariant ChatyBrandGlyphPainter oldDelegate) =>
      oldDelegate.variant != variant ||
      oldDelegate.color != color ||
      oldDelegate.strokeWidth != strokeWidth;
}

/// Standalone canonical Chaty brand icon widget.
class ChatyBrandMark extends StatelessWidget {
  final ChatyBrandIconVariant variant;
  final double size;
  final Color? color;

  const ChatyBrandMark({
    super.key,
    this.variant = ChatyBrandIconVariant.filled,
    this.size = 28,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final effectiveColor = color ?? Theme.of(context).colorScheme.primary;
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: ChatyBrandGlyphPainter(
          variant: variant,
          color: effectiveColor,
          strokeWidth: size * 0.08,
        ),
      ),
    );
  }
}

/// Rich launcher icon preview for the six canonical Chaty brand directions.
class LauncherIconPreview extends StatelessWidget {
  final LauncherIconVariant variant;
  final double size;
  final double borderRadius;

  const LauncherIconPreview({
    super.key,
    required this.variant,
    this.size = 48,
    this.borderRadius = 14,
  });

  @override
  Widget build(BuildContext context) {
    final style = _compositionFor(variant);
    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: SizedBox(
        width: size,
        height: size,
        child: DecoratedBox(
          decoration: BoxDecoration(
            gradient: style.gradient,
            color: style.bgColor,
          ),
          child: Center(
            child: SizedBox(
              width: size * 0.62,
              height: size * 0.62,
              child: CustomPaint(
                painter: _LauncherCompositionPainter(
                  variant: variant,
                  iconColor: style.glyphColor,
                  accentColor: style.accentColor,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  static _LauncherStyle _compositionFor(LauncherIconVariant variant) {
    switch (variant) {
      case LauncherIconVariant.warm:
        return const _LauncherStyle(
          bgColor: Color(0xFFF4EFE6),
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFFFAF7F2), Color(0xFFE8E0D2)],
          ),
          glyphColor: Color(0xFFF9F6F0),
          accentColor: Color(0xFF8C6239),
        );
      case LauncherIconVariant.outline:
        return const _LauncherStyle(
          bgColor: Color(0xFF141414),
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFF1E1E1E), Color(0xFF0F0F0F)],
          ),
          glyphColor: Color(0xFFFAF7F2),
          accentColor: Color(0xFF7D756D),
        );
      case LauncherIconVariant.obsidian:
        return const _LauncherStyle(
          bgColor: Color(0xFF141414),
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF1E1E1E), Color(0xFF0F0F0F)],
          ),
          glyphColor: Color(0xFFFAF7F2),
          accentColor: Color(0xFF8C6239),
        );
      case LauncherIconVariant.glass:
        return const _LauncherStyle(
          bgColor: Color(0xFF1C1917),
          gradient: RadialGradient(
            colors: [Color(0xFF292524), Color(0xFF141210)],
          ),
          glyphColor: Colors.white,
          accentColor: Color(0xFFD4A373),
        );
      case LauncherIconVariant.signal:
        return const _LauncherStyle(
          bgColor: Color(0xFF0D1D33),
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF142A47), Color(0xFF07111F)],
          ),
          glyphColor: Color(0xFFF8FBFF),
          accentColor: Color(0xFF60A5FA),
        );
      case LauncherIconVariant.fold:
        return const _LauncherStyle(
          bgColor: Color(0xFF1C1917),
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF292524), Color(0xFF1C1917)],
          ),
          glyphColor: Color(0xFFD4A373),
          accentColor: Color(0xFF8C5E3C),
        );
    }
  }
}

class _LauncherStyle {
  final Color bgColor;
  final Gradient gradient;
  final Color glyphColor;
  final Color accentColor;

  const _LauncherStyle({
    required this.bgColor,
    required this.gradient,
    required this.glyphColor,
    required this.accentColor,
  });
}

class _LauncherCompositionPainter extends CustomPainter {
  final LauncherIconVariant variant;
  final Color iconColor;
  final Color accentColor;

  _LauncherCompositionPainter({
    required this.variant,
    required this.iconColor,
    required this.accentColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;

    switch (variant) {
      case LauncherIconVariant.warm:
        // 01 Warm Signature: Exact 3D-embossed warm balloon with metallic segmented rim
        // Drop shadow
        final shadowPainter = ChatyBrandGlyphPainter(
          variant: ChatyBrandIconVariant.filled,
          color: const Color(0x332B2219),
        );
        canvas.save();
        canvas.translate(0, h * 0.05);
        shadowPainter.paint(canvas, size);
        canvas.restore();

        // 3D base bevel
        final basePainter = ChatyBrandGlyphPainter(
          variant: ChatyBrandIconVariant.filled,
          color: accentColor,
        );
        canvas.save();
        canvas.translate(0, h * 0.02);
        basePainter.paint(canvas, size);
        canvas.restore();

        // Metallic outer stroke rim
        final rimPainter = ChatyBrandGlyphPainter(
          variant: ChatyBrandIconVariant.outline,
          color: const Color(0xFFC89666),
          strokeWidth: w * 0.08,
        );
        rimPainter.paint(canvas, size);

        // Warm ivory front face fill with dark dots
        final frontPainter = ChatyBrandGlyphPainter(
          variant: ChatyBrandIconVariant.filled,
          color: iconColor,
        );
        frontPainter.paint(canvas, size);

      case LauncherIconVariant.outline:
        // 02 Warm Outline: Clean ivory hairline outline on dark surface
        final outlinePainter = ChatyBrandGlyphPainter(
          variant: ChatyBrandIconVariant.outline,
          color: iconColor,
          strokeWidth: w * 0.09,
        );
        outlinePainter.paint(canvas, size);

      case LauncherIconVariant.obsidian:
        // 03 Obsidian / Filled: Solid 3D ivory bubble on dark surface
        final shadowPainter = ChatyBrandGlyphPainter(
          variant: ChatyBrandIconVariant.filled,
          color: const Color(0x66000000),
        );
        canvas.save();
        canvas.translate(0, h * 0.05);
        shadowPainter.paint(canvas, size);
        canvas.restore();

        final basePainter = ChatyBrandGlyphPainter(
          variant: ChatyBrandIconVariant.filled,
          color: accentColor,
        );
        canvas.save();
        canvas.translate(0, h * 0.02);
        basePainter.paint(canvas, size);
        canvas.restore();

        final fillPainter = ChatyBrandGlyphPainter(
          variant: ChatyBrandIconVariant.filled,
          color: iconColor,
        );
        fillPainter.paint(canvas, size);

      case LauncherIconVariant.glass:
        // 04 Spatial Glass: Layered translucent glass facets
        final backdrop = Paint()
          ..color = accentColor.withValues(alpha: 0.25)
          ..style = PaintingStyle.fill;
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            Rect.fromLTWH(w * 0.1, h * 0.1, w * 0.8, h * 0.8),
            Radius.circular(w * 0.28),
          ),
          backdrop,
        );
        final glassPainter = ChatyBrandGlyphPainter(
          variant: ChatyBrandIconVariant.outline,
          color: Colors.white.withValues(alpha: 0.85),
          strokeWidth: w * 0.07,
        );
        glassPainter.paint(canvas, size);

      case LauncherIconVariant.signal:
        // 05 Signal: Communication mark with presence resonance ring
        final ringPaint = Paint()
          ..color = accentColor.withValues(alpha: 0.35)
          ..style = PaintingStyle.stroke
          ..strokeWidth = w * 0.07;
        canvas.drawCircle(Offset(w * 0.5, h * 0.44), w * 0.44, ringPaint);
        final markPainter = ChatyBrandGlyphPainter(
          variant: ChatyBrandIconVariant.filled,
          color: iconColor,
        );
        markPainter.paint(canvas, size);

      case LauncherIconVariant.fold:
        // 06 Fold: Dimensional folded conversation geometry
        final foldPath1 = Path()
          ..moveTo(w * 0.18, h * 0.22)
          ..lineTo(w * 0.82, h * 0.22)
          ..lineTo(w * 0.5, h * 0.54)
          ..close();
        final foldPath2 = Path()
          ..moveTo(w * 0.18, h * 0.22)
          ..lineTo(w * 0.5, h * 0.54)
          ..lineTo(w * 0.18, h * 0.74)
          ..close();
        final foldPath3 = Path()
          ..moveTo(w * 0.82, h * 0.22)
          ..lineTo(w * 0.5, h * 0.54)
          ..lineTo(w * 0.82, h * 0.74)
          ..close();
        final foldPath4 = Path()
          ..moveTo(w * 0.18, h * 0.74)
          ..lineTo(w * 0.5, h * 0.54)
          ..lineTo(w * 0.82, h * 0.74)
          ..lineTo(w * 0.30, h * 0.90)
          ..close();

        canvas.drawPath(foldPath1, Paint()..color = iconColor);
        canvas.drawPath(
          foldPath2,
          Paint()..color = accentColor.withValues(alpha: 0.65),
        );
        canvas.drawPath(
          foldPath3,
          Paint()..color = accentColor.withValues(alpha: 0.85),
        );
        canvas.drawPath(foldPath4, Paint()..color = iconColor);
    }
  }

  @override
  bool shouldRepaint(covariant _LauncherCompositionPainter oldDelegate) =>
      oldDelegate.variant != variant ||
      oldDelegate.iconColor != iconColor ||
      oldDelegate.accentColor != accentColor;
}

class ChatyBrandIcon extends StatelessWidget {
  final AppIconController controller;
  final double size;
  final double borderRadius;

  const ChatyBrandIcon({
    super.key,
    required this.controller,
    this.size = 48,
    this.borderRadius = 14,
  });

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        Widget image;
        final customPath = controller.customBrandIconPath;
        if (controller.brandIconSource == BrandIconSource.custom &&
            customPath != null &&
            customPath.isNotEmpty &&
            File(customPath).existsSync()) {
          image = ClipRRect(
            borderRadius: BorderRadius.circular(borderRadius),
            child: Image.file(
              File(customPath),
              width: size,
              height: size,
              fit: BoxFit.cover,
              gaplessPlayback: true,
              errorBuilder: (_, _, _) => LauncherIconPreview(
                variant: controller.launcherIcon,
                size: size,
                borderRadius: borderRadius,
              ),
            ),
          );
        } else {
          image = LauncherIconPreview(
            variant: controller.launcherIcon,
            size: size,
            borderRadius: borderRadius,
          );
        }

        return Semantics(
          image: true,
          label: 'Chaty app icon',
          child: SizedBox(width: size, height: size, child: image),
        );
      },
    );
  }
}
