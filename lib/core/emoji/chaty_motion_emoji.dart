import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'chaty_emoji_style.dart';
import 'chaty_ios_animated_emoji.dart';

/// High-performance, offline-first vector animated emoji engine for Chaty.
/// Renders 10 genuinely distinct emoji visual styles with 60/120fps procedural
/// vector rendering and zero external network or asset dependencies.
class ChatyMotionEmoji extends StatefulWidget {
  final String unicode;
  final double size;
  final bool animate;
  final bool autoStart;
  final ChatyEmojiStyleId style;
  final VoidCallback? onCompleted;

  const ChatyMotionEmoji({
    super.key,
    required this.unicode,
    this.size = 64.0,
    this.animate = true,
    this.autoStart = true,
    this.style = ChatyEmojiStyleId.chatyMotion,
    this.onCompleted,
  });

  /// Identifies if this Unicode emoji has a dedicated vector rendering pipeline.
  static bool isSupported(String unicode) {
    final raw = unicode.trim();
    final clean = raw.replaceAll('\uFE0F', '').replaceAll('\uFE0E', '');
    const set = {
      '😂', '🤣', '❤️', '\u2764', '😍', '💖', '🔥', '👍', '🎉', '🥳', '🤔', '😮', '😲', '😢', '😭', '👋'
    };
    return set.contains(clean) || set.contains(raw);
  }

  @override
  State<ChatyMotionEmoji> createState() => _ChatyMotionEmojiState();
}

class _ChatyMotionEmojiState extends State<ChatyMotionEmoji>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    );

    _controller.addStatusListener((status) {
      if (status == AnimationStatus.completed) {
        widget.onCompleted?.call();
      }
    });

    if (widget.animate && widget.autoStart) {
      _controller.forward(from: 0.0);
    }
  }

  @override
  void didUpdateWidget(covariant ChatyMotionEmoji oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.animate != oldWidget.animate) {
      if (widget.animate) {
        _controller.forward(from: 0.0);
      } else {
        _controller.stop();
      }
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void replay() {
    _controller.forward(from: 0.0);
  }

  @override
  Widget build(BuildContext context) {
    final clean = widget.unicode.replaceAll('\uFE0F', '').replaceAll('\uFE0E', '').trim();

    // iOS Animated (Drive) style renders its bundled WebP pack when the
    // emoji is covered; anything outside the 50-emoji pack falls back to the
    // Chaty Motion procedural art so it never paints blank.
    if (widget.style == ChatyEmojiStyleId.iosAnimated &&
        ChatyIosAnimatedEmoji.isSupported(clean)) {
      return IosAnimatedEmojiView(
        unicode: clean,
        size: widget.size,
        animate: widget.animate,
      );
    }
    final effectiveStyle = widget.style == ChatyEmojiStyleId.iosAnimated
        ? ChatyEmojiStyleId.chatyMotion
        : widget.style;

    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        return SizedBox(
          width: widget.size,
          height: widget.size,
          child: CustomPaint(
            painter: _ChatyMotionEmojiPainter(
              unicode: clean,
              progress: _controller.value,
              animate: widget.animate,
              style: effectiveStyle,
            ),
          ),
        );
      },
    );
  }
}

class _ChatyMotionEmojiPainter extends CustomPainter {
  final String unicode;
  final double progress;
  final bool animate;
  final ChatyEmojiStyleId style;

  const _ChatyMotionEmojiPainter({
    required this.unicode,
    required this.progress,
    required this.animate,
    required this.style,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / 64.0;
    canvas.save();
    canvas.scale(scale, scale);

    final t = animate ? progress : 0.0;

    switch (unicode) {
      case '😂':
      case '🤣':
        _paintJoy(canvas, t);
        break;
      case '❤️':
      case '💖':
        _paintHeart(canvas, t);
        break;
      case '🔥':
        _paintFire(canvas, t);
        break;
      case '👍':
        _paintThumbsUp(canvas, t);
        break;
      case '🎉':
      case '🥳':
        _paintCelebration(canvas, t);
        break;
      case '🤔':
        _paintThinking(canvas, t);
        break;
      case '😮':
      case '😲':
        _paintSurprise(canvas, t);
        break;
      case '😢':
      case '😭':
        _paintSad(canvas, t);
        break;
      case '👋':
        _paintWaving(canvas, t);
        break;
      default:
        _paintJoy(canvas, t);
    }

    canvas.restore();
  }

  // =========================================================================
  // 1. JOY / LAUGHING WITH TEARS (😂) ACROSS 10 DISTINCT STYLES
  // =========================================================================

  void _paintJoy(Canvas canvas, double t) {
    final nod = math.sin(t * math.pi * 4) * 2.2;
    final center = Offset(32, 32 + nod);

    switch (style) {
      case ChatyEmojiStyleId.iosAnimated:
      case ChatyEmojiStyleId.chatyMotion:
        // Original Chaty Motion: vibrant yellow with animated spurting teardrops
        final facePaint = Paint()..color = const Color(0xFFFFCC4D);
        canvas.drawCircle(center, 26, facePaint);
        _drawJoyEyes(canvas, center, const Color(0xFF664500), 3.2);
        _drawJoyMouth(canvas, center, const Color(0xFF664500), const Color(0xFFFF5252));
        _drawTears(canvas, center, t, const Color(0xFF5DADEC), false);
        break;

      case ChatyEmojiStyleId.chatyInk:
        // Original Chaty Ink: 3.0px bold manga black ink contour + comic hatching
        final base = Paint()..color = const Color(0xFFFFFDE7);
        canvas.drawCircle(center, 25.5, base);
        final inkBorder = Paint()
          ..color = const Color(0xFF1A1A1A)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 3.2
          ..strokeCap = StrokeCap.round;
        canvas.drawCircle(center, 25.5, inkBorder);
        // Manga eyes
        _drawJoyEyes(canvas, center, const Color(0xFF1A1A1A), 3.8);
        // Comic cheek blush hatching
        final hatch = Paint()..color = const Color(0xFFE53935)..strokeWidth = 1.4;
        canvas.drawLine(Offset(center.dx - 18, center.dy + 1), Offset(center.dx - 14, center.dy + 5), hatch);
        canvas.drawLine(Offset(center.dx - 21, center.dy + 1), Offset(center.dx - 17, center.dy + 5), hatch);
        canvas.drawLine(Offset(center.dx + 14, center.dy + 1), Offset(center.dx + 18, center.dy + 5), hatch);
        canvas.drawLine(Offset(center.dx + 17, center.dy + 1), Offset(center.dx + 21, center.dy + 5), hatch);
        // Wide ink mouth
        _drawJoyMouth(canvas, center, const Color(0xFF1A1A1A), const Color(0xFFFF1744));
        _drawTears(canvas, center, t, const Color(0xFF29B6F6), true);
        break;

      case ChatyEmojiStyleId.chatySoft3D:
        // Original Chaty Soft 3D: multi-stop radial gradient sphere + specular highlight pill
        final grad = RadialGradient(
          center: const Alignment(-0.35, -0.4),
          radius: 0.9,
          colors: const [Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFFA000), Color(0xFFE65100)],
        ).createShader(Rect.fromCircle(center: center, radius: 26));
        canvas.drawCircle(center, 26, Paint()..shader = grad);
        // Specular highlight bubble
        final glint = Paint()..color = Colors.white.withValues(alpha: 0.45);
        canvas.drawOval(Rect.fromLTWH(center.dx - 14, center.dy - 20, 14, 8), glint);
        // Soft volumetric vitreous eyes & mouth
        _drawJoyEyes(canvas, center, const Color(0xFF4E342E), 3.4);
        _drawJoyMouth(canvas, center, const Color(0xFF3E2723), const Color(0xFFFF5252));
        _drawTears(canvas, center, t, const Color(0xFF00B0FF), false);
        break;

      case ChatyEmojiStyleId.chatyPixel:
        // Original Chaty Pixel: authentic 8-bit stepped pixel grid
        _drawPixelJoyFace(canvas, center, t);
        break;

      case ChatyEmojiStyleId.openMoji:
        // OpenMoji (CC BY-SA 4.0): 2.8px solid black contour enclosing flat unshaded canary yellow
        final yellow = Paint()..color = const Color(0xFFFCEA2B);
        canvas.drawCircle(center, 25, yellow);
        final blackOutline = Paint()
          ..color = Colors.black
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2.8;
        canvas.drawCircle(center, 25, blackOutline);
        _drawJoyEyes(canvas, center, Colors.black, 3.0);
        _drawJoyMouth(canvas, center, Colors.black, const Color(0xFFEA5A47));
        _drawTears(canvas, center, t, const Color(0xFF1E88E5), true);
        break;

      case ChatyEmojiStyleId.twemoji:
        // Twemoji (CC BY 4.0): pure flat Twitter yellow, pill capsule eyes, clean horizontal smile
        final twYellow = Paint()..color = const Color(0xFFFFCC4D);
        canvas.drawCircle(center, 26, twYellow);
        _drawJoyEyes(canvas, center, const Color(0xFF664500), 3.2);
        _drawJoyMouth(canvas, center, const Color(0xFF664500), const Color(0xFFDD2E44));
        _drawTears(canvas, center, t, const Color(0xFF5DADEC), false);
        break;

      case ChatyEmojiStyleId.notoColor:
        // Noto Color (SIL OFL 1.1): Google Material soft vertical linear gradient
        final notoGrad = const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFFA000)],
        ).createShader(Rect.fromCircle(center: center, radius: 26));
        canvas.drawCircle(center, 26, Paint()..shader = notoGrad);
        _drawJoyEyes(canvas, center, const Color(0xFF42210B), 3.0);
        _drawJoyMouth(canvas, center, const Color(0xFF42210B), const Color(0xFFD32F2F));
        _drawTears(canvas, center, t, const Color(0xFF42A5F5), false);
        break;

      case ChatyEmojiStyleId.fluentColor:
        // Fluent Color (MIT): Microsoft Fluent soft 3D dimensional gradient with peach cheek blush
        final fluentGrad = const RadialGradient(
          center: Alignment(-0.2, -0.3),
          radius: 0.95,
          colors: [Color(0xFFFFE082), Color(0xFFFFB74D), Color(0xFFFB8C00)],
        ).createShader(Rect.fromCircle(center: center, radius: 26));
        canvas.drawCircle(center, 26, Paint()..shader = fluentGrad);
        // Peach cheek blush
        final blush = Paint()..color = const Color(0xFFFF7043).withValues(alpha: 0.35);
        canvas.drawCircle(Offset(center.dx - 16, center.dy + 3), 5.5, blush);
        canvas.drawCircle(Offset(center.dx + 16, center.dy + 3), 5.5, blush);
        _drawJoyEyes(canvas, center, const Color(0xFF3E2723), 3.2);
        _drawJoyMouth(canvas, center, const Color(0xFF3E2723), const Color(0xFFFF5252));
        _drawTears(canvas, center, t, const Color(0xFF29B6F6), false);
        break;

      case ChatyEmojiStyleId.blobmoji:
        // Blobmoji (Apache 2.0 / OFL 1.1): Classic Android asymmetric teardrop jelly-blob silhouette
        final blobPath = Path()
          ..moveTo(center.dx - 23, center.dy + 12)
          ..cubicTo(center.dx - 27, center.dy + 25, center.dx + 27, center.dy + 25, center.dx + 23, center.dy + 12)
          ..cubicTo(center.dx + 23, center.dy - 12, center.dx + 14, center.dy - 24, center.dx, center.dy - 22)
          ..cubicTo(center.dx - 14, center.dy - 24, center.dx - 23, center.dy - 12, center.dx - 23, center.dy + 12)
          ..close();
        final blobGrad = const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFFFFF176), Color(0xFFFFCA28)],
        ).createShader(Rect.fromCircle(center: center, radius: 26));
        canvas.drawPath(blobPath, Paint()..shader = blobGrad);
        _drawJoyEyes(canvas, Offset(center.dx, center.dy - 3), const Color(0xFF4527A0), 3.4);
        _drawJoyMouth(canvas, Offset(center.dx, center.dy - 2), const Color(0xFF4527A0), const Color(0xFFE91E63));
        _drawTears(canvas, center, t, const Color(0xFF00E5FF), false);
        break;

      case ChatyEmojiStyleId.emojiTwo:
        // EmojiTwo (CC BY 4.0): Classic open-source illustrated emoji with top crescent glossy rim
        final e2Grad = const RadialGradient(
          center: Alignment(0.0, -0.2),
          colors: [Color(0xFFFFD54F), Color(0xFFFF9800)],
        ).createShader(Rect.fromCircle(center: center, radius: 26));
        canvas.drawCircle(center, 26, Paint()..shader = e2Grad);
        // Top crescent gloss
        final gloss = Path()
          ..addArc(Rect.fromCircle(center: Offset(center.dx, center.dy - 4), radius: 24), -2.4, 1.6);
        canvas.drawPath(gloss, Paint()..color = Colors.white.withValues(alpha: 0.45)..style = PaintingStyle.stroke..strokeWidth = 3.0);
        _drawJoyEyes(canvas, center, const Color(0xFF42210B), 3.2);
        _drawJoyMouth(canvas, center, const Color(0xFF42210B), const Color(0xFFD81B60));
        _drawTears(canvas, center, t, const Color(0xFF03A9F4), false);
        break;
    }
  }

  void _drawJoyEyes(Canvas canvas, Offset center, Color color, double strokeWidth) {
    final eyePaint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round;

    final leftEye = Path()
      ..moveTo(center.dx - 16, center.dy - 6)
      ..quadraticBezierTo(center.dx - 10, center.dy - 12, center.dx - 4, center.dy - 6);
    canvas.drawPath(leftEye, eyePaint);

    final rightEye = Path()
      ..moveTo(center.dx + 4, center.dy - 6)
      ..quadraticBezierTo(center.dx + 10, center.dy - 12, center.dx + 16, center.dy - 6);
    canvas.drawPath(rightEye, eyePaint);
  }

  void _drawJoyMouth(Canvas canvas, Offset center, Color strokeColor, Color tongueColor) {
    final mouthPath = Path()
      ..moveTo(center.dx - 15, center.dy + 3)
      ..quadraticBezierTo(center.dx, center.dy + 23, center.dx + 15, center.dy + 3)
      ..close();
    canvas.drawPath(mouthPath, Paint()..color = strokeColor);

    final tonguePath = Path()
      ..moveTo(center.dx - 8, center.dy + 12)
      ..quadraticBezierTo(center.dx, center.dy + 22, center.dx + 8, center.dy + 12)
      ..close();
    canvas.drawPath(tonguePath, Paint()..color = tongueColor);

    final teethPath = Path()
      ..moveTo(center.dx - 13.5, center.dy + 3)
      ..quadraticBezierTo(center.dx, center.dy + 9, center.dx + 13.5, center.dy + 3)
      ..close();
    canvas.drawPath(teethPath, Paint()..color = Colors.white);
  }

  void _drawTears(Canvas canvas, Offset center, double t, Color color, bool outline) {
    final phase = (t * 2) % 1.0;
    final alpha = (math.sin(phase * math.pi)).clamp(0.0, 1.0);
    final tearPaint = Paint()..color = color.withValues(alpha: alpha);

    final l = Offset(center.dx - 22 - (phase * 4), center.dy + 2 + (phase * 12));
    final r = Offset(center.dx + 22 + (phase * 4), center.dy + 2 + (phase * 12));

    canvas.drawOval(Rect.fromCenter(center: l, width: 7, height: 11), tearPaint);
    canvas.drawOval(Rect.fromCenter(center: r, width: 7, height: 11), tearPaint);

    if (outline) {
      final border = Paint()..color = Colors.black.withValues(alpha: alpha)..style = PaintingStyle.stroke..strokeWidth = 1.4;
      canvas.drawOval(Rect.fromCenter(center: l, width: 7, height: 11), border);
      canvas.drawOval(Rect.fromCenter(center: r, width: 7, height: 11), border);
    }
  }

  void _drawPixelJoyFace(Canvas canvas, Offset center, double t) {
    final fill = Paint()..color = const Color(0xFFFFD700);
    final black = Paint()..color = Colors.black;
    final blue = Paint()..color = const Color(0xFF00B0FF);

    // 16x16 Pixel Face Matrix
    canvas.drawRect(Rect.fromCenter(center: center, width: 44, height: 44), fill);
    canvas.drawRect(Rect.fromCenter(center: center, width: 48, height: 36), fill);
    canvas.drawRect(Rect.fromCenter(center: center, width: 36, height: 48), fill);

    // Stepped eyes (2x2 pixel blocks)
    canvas.drawRect(Rect.fromLTWH(center.dx - 14, center.dy - 8, 4, 4), black);
    canvas.drawRect(Rect.fromLTWH(center.dx - 10, center.dy - 12, 4, 4), black);
    canvas.drawRect(Rect.fromLTWH(center.dx + 10, center.dy - 8, 4, 4), black);
    canvas.drawRect(Rect.fromLTWH(center.dx + 6, center.dy - 12, 4, 4), black);

    // Pixel smile
    canvas.drawRect(Rect.fromLTWH(center.dx - 12, center.dy + 4, 24, 8), black);
    canvas.drawRect(Rect.fromLTWH(center.dx - 8, center.dy + 4, 16, 4), Paint()..color = Colors.white);

    // Pixel tears
    final tearY = center.dy - 2 + (t * 12);
    canvas.drawRect(Rect.fromLTWH(center.dx - 18, tearY, 4, 6), blue);
    canvas.drawRect(Rect.fromLTWH(center.dx + 14, tearY, 4, 6), blue);
  }

  // =========================================================================
  // 2. HEART (❤️) ACROSS DISTINCT STYLES
  // =========================================================================

  void _paintHeart(Canvas canvas, double t) {
    final beat = (math.sin(t * math.pi * 6) * 0.12).clamp(-0.15, 0.15);
    final scale = 1.0 + beat;

    canvas.save();
    canvas.translate(32, 32);
    canvas.scale(scale, scale);
    canvas.translate(-32, -32);

    final heartPath = Path()
      ..moveTo(32, 52)
      ..cubicTo(12, 38, 4, 25, 12, 13)
      ..cubicTo(18, 5, 27, 8, 32, 17)
      ..cubicTo(37, 8, 46, 5, 52, 13)
      ..cubicTo(60, 25, 52, 38, 32, 52)
      ..close();

    switch (style) {
      case ChatyEmojiStyleId.iosAnimated:
      case ChatyEmojiStyleId.chatyPixel:
        // 8-bit stepped pixel heart
        final red = Paint()..color = const Color(0xFFFF0044);
        canvas.drawRect(const Rect.fromLTWH(18, 14, 10, 8), red);
        canvas.drawRect(const Rect.fromLTWH(36, 14, 10, 8), red);
        canvas.drawRect(const Rect.fromLTWH(14, 22, 36, 10), red);
        canvas.drawRect(const Rect.fromLTWH(18, 32, 28, 8), red);
        canvas.drawRect(const Rect.fromLTWH(24, 40, 16, 6), red);
        canvas.drawRect(const Rect.fromLTWH(28, 46, 8, 5), red);
        break;

      case ChatyEmojiStyleId.openMoji:
        canvas.drawPath(heartPath, Paint()..color = const Color(0xFFEA5A47));
        canvas.drawPath(heartPath, Paint()..color = Colors.black..style = PaintingStyle.stroke..strokeWidth = 2.8);
        break;

      case ChatyEmojiStyleId.chatyInk:
        canvas.drawPath(heartPath, Paint()..color = const Color(0xFFFF1744));
        canvas.drawPath(heartPath, Paint()..color = const Color(0xFF1A1A1A)..style = PaintingStyle.stroke..strokeWidth = 3.2);
        break;

      case ChatyEmojiStyleId.chatySoft3D:
        final grad = const RadialGradient(
          center: Alignment(-0.3, -0.4),
          radius: 0.9,
          colors: [Color(0xFFFF5252), Color(0xFFD50000), Color(0xFF880E4F)],
        ).createShader(const Rect.fromLTWH(0, 0, 64, 64));
        canvas.drawPath(heartPath, Paint()..shader = grad);
        // Specular highlight bubble
        canvas.drawOval(const Rect.fromLTWH(18, 14, 10, 6), Paint()..color = Colors.white.withValues(alpha: 0.45));
        break;

      default:
        final grad = const RadialGradient(
          center: Alignment(-0.2, -0.3),
          colors: [Color(0xFFFF2A55), Color(0xFFD6002A)],
        ).createShader(const Rect.fromLTWH(0, 0, 64, 64));
        canvas.drawPath(heartPath, Paint()..shader = grad);
        break;
    }

    canvas.restore();
  }

  // =========================================================================
  // 3. FIRE (🔥) ACROSS DISTINCT STYLES
  // =========================================================================

  void _paintFire(Canvas canvas, double t) {
    final flicker = math.sin(t * math.pi * 8) * 1.8;

    final outerFlame = Path()
      ..moveTo(32, 56)
      ..cubicTo(16, 56, 12, 42, 18, 30)
      ..cubicTo(20, 25, 26, 26, 24, 16)
      ..cubicTo(24 + flicker, 8, 34, 4, 38, 14)
      ..cubicTo(42, 24, 48, 22, 50, 32)
      ..cubicTo(54, 42, 48, 56, 32, 56)
      ..close();

    final innerFlame = Path()
      ..moveTo(32, 53)
      ..cubicTo(22, 53, 20, 44, 24, 36)
      ..cubicTo(26, 32, 30, 32, 30, 24)
      ..cubicTo(32, 18, 36, 22, 38, 28)
      ..cubicTo(40, 34, 44, 36, 42, 44)
      ..cubicTo(40, 52, 38, 53, 32, 53)
      ..close();

    if (style == ChatyEmojiStyleId.chatyPixel) {
      final red = Paint()..color = const Color(0xFFFF3D00);
      final yellow = Paint()..color = const Color(0xFFFFD600);
      canvas.drawRect(const Rect.fromLTWH(20, 32, 24, 20), red);
      canvas.drawRect(const Rect.fromLTWH(24, 18, 16, 16), red);
      canvas.drawRect(const Rect.fromLTWH(28, 8, 8, 12), red);
      canvas.drawRect(const Rect.fromLTWH(26, 36, 12, 12), yellow);
      canvas.drawRect(const Rect.fromLTWH(28, 26, 8, 12), yellow);
      return;
    }

    canvas.drawPath(outerFlame, Paint()..color = const Color(0xFFFF5722));
    canvas.drawPath(innerFlame, Paint()..color = const Color(0xFFFFEB3B));

    if (style == ChatyEmojiStyleId.openMoji || style == ChatyEmojiStyleId.chatyInk) {
      final strokeW = style == ChatyEmojiStyleId.chatyInk ? 3.0 : 2.4;
      canvas.drawPath(outerFlame, Paint()..color = Colors.black..style = PaintingStyle.stroke..strokeWidth = strokeW);
    }
  }

  // =========================================================================
  // 4. THUMBS UP (👍)
  // =========================================================================

  void _paintThumbsUp(Canvas canvas, double t) {
    final thumbBounce = (math.sin(t * math.pi * 4) * 2.0).clamp(-2.0, 2.0);

    canvas.save();
    canvas.translate(0, thumbBounce);

    final hand = Path()
      ..moveTo(14, 30)..lineTo(22, 30)..lineTo(22, 14)
      ..quadraticBezierTo(22, 8, 27, 8)
      ..quadraticBezierTo(32, 8, 32, 14)
      ..lineTo(32, 28)..lineTo(46, 28)
      ..quadraticBezierTo(52, 28, 52, 34)
      ..quadraticBezierTo(52, 40, 46, 40)
      ..quadraticBezierTo(50, 43, 46, 46)
      ..quadraticBezierTo(49, 49, 44, 52)
      ..lineTo(22, 52)..lineTo(14, 52)..close();

    final skinColor = const Color(0xFFFFCC4D);
    canvas.drawPath(hand, Paint()..color = skinColor);

    if (style == ChatyEmojiStyleId.openMoji || style == ChatyEmojiStyleId.chatyInk) {
      canvas.drawPath(hand, Paint()..color = Colors.black..style = PaintingStyle.stroke..strokeWidth = 2.6);
    } else {
      canvas.drawPath(hand, Paint()..color = const Color(0xFF664500).withValues(alpha: 0.2)..style = PaintingStyle.stroke..strokeWidth = 1.4);
    }

    canvas.restore();
  }

  // =========================================================================
  // 5. CELEBRATION (🎉)
  // =========================================================================

  void _paintCelebration(Canvas canvas, double t) {
    // Party popper cone
    final cone = Path()
      ..moveTo(14, 50)..lineTo(28, 36)..lineTo(14, 22)..close();
    canvas.drawPath(cone, Paint()..color = const Color(0xFFFF9800));

    // Confetti particles
    final r = Paint()..color = const Color(0xFFE91E63);
    final b = Paint()..color = const Color(0xFF2196F3);
    final g = Paint()..color = const Color(0xFF4CAF50);
    final y = Paint()..color = const Color(0xFFFFEB3B);

    final burst = (t * 14);
    canvas.drawCircle(Offset(28 + burst, 26 - burst), 3.0, r);
    canvas.drawCircle(Offset(36 + burst, 32 - (burst * 0.5)), 2.6, b);
    canvas.drawCircle(Offset(32 + (burst * 0.8), 42 + burst), 2.8, g);
    canvas.drawCircle(Offset(44 + burst, 20 - burst), 3.2, y);
  }

  // =========================================================================
  // 6. THINKING (🤔)
  // =========================================================================

  void _paintThinking(Canvas canvas, double t) {
    final center = const Offset(32, 28);
    canvas.drawCircle(center, 22, Paint()..color = const Color(0xFFFFCC4D));

    // One raised eyebrow
    final browPaint = Paint()..color = const Color(0xFF664500)..style = PaintingStyle.stroke..strokeWidth = 2.4;
    canvas.drawLine(Offset(center.dx - 12, center.dy - 12), Offset(center.dx - 4, center.dy - 10), browPaint);
    canvas.drawLine(Offset(center.dx + 4, center.dy - 14), Offset(center.dx + 12, center.dy - 10), browPaint);

    // Eyes looking up-right
    canvas.drawCircle(Offset(center.dx - 8, center.dy - 4), 2.4, Paint()..color = const Color(0xFF664500));
    canvas.drawCircle(Offset(center.dx + 8, center.dy - 4), 2.4, Paint()..color = const Color(0xFF664500));

    // Skeptical smirk mouth
    canvas.drawLine(Offset(center.dx - 6, center.dy + 8), Offset(center.dx + 6, center.dy + 6), browPaint);

    // Hand on chin
    final hand = Path()
      ..moveTo(24, 48)..lineTo(36, 48)..lineTo(36, 40)..lineTo(32, 34)..lineTo(28, 40)..close();
    canvas.drawPath(hand, Paint()..color = const Color(0xFFFFB74D));
  }

  // =========================================================================
  // 7. SURPRISE (😮)
  // =========================================================================

  void _paintSurprise(Canvas canvas, double t) {
    final center = const Offset(32, 32);
    canvas.drawCircle(center, 26, Paint()..color = const Color(0xFFFFCC4D));

    // Round open eyes
    final eyePaint = Paint()..color = const Color(0xFF664500);
    canvas.drawCircle(Offset(center.dx - 9, center.dy - 6), 4.2, eyePaint);
    canvas.drawCircle(Offset(center.dx + 9, center.dy - 6), 4.2, eyePaint);
    canvas.drawCircle(Offset(center.dx - 7.5, center.dy - 7.5), 1.2, Paint()..color = Colors.white);
    canvas.drawCircle(Offset(center.dx + 10.5, center.dy - 7.5), 1.2, Paint()..color = Colors.white);

    // Big round open 'O' mouth
    final mouthScale = 1.0 + math.sin(t * math.pi * 4) * 0.15;
    canvas.drawOval(Rect.fromCenter(center: Offset(center.dx, center.dy + 10), width: 13 * mouthScale, height: 17 * mouthScale), eyePaint);
  }

  // =========================================================================
  // 8. SAD (😢)
  // =========================================================================

  void _paintSad(Canvas canvas, double t) {
    final center = const Offset(32, 32);
    canvas.drawCircle(center, 26, Paint()..color = const Color(0xFFFFCC4D));

    // Downcast curved eyes
    final eye = Paint()..color = const Color(0xFF664500)..style = PaintingStyle.stroke..strokeWidth = 2.8;
    canvas.drawArc(Rect.fromLTWH(center.dx - 14, center.dy - 8, 8, 6), 0, math.pi, false, eye);
    canvas.drawArc(Rect.fromLTWH(center.dx + 6, center.dy - 8, 8, 6), 0, math.pi, false, eye);

    // Frowning mouth
    canvas.drawArc(Rect.fromLTWH(center.dx - 8, center.dy + 8, 16, 12), math.pi, math.pi, false, eye);

    // Single tear sliding down left cheek
    final tearY = center.dy - 2 + (t * 16);
    canvas.drawOval(Rect.fromCenter(center: Offset(center.dx - 9, tearY), width: 5.5, height: 8.5), Paint()..color = const Color(0xFF29B6F6));
  }

  // =========================================================================
  // 9. WAVING HAND (👋)
  // =========================================================================

  void _paintWaving(Canvas canvas, double t) {
    final waveAngle = math.sin(t * math.pi * 6) * 0.35;

    canvas.save();
    canvas.translate(32, 46);
    canvas.rotate(waveAngle);
    canvas.translate(-32, -46);

    final hand = Path()
      ..moveTo(22, 46)
      ..lineTo(22, 28) // Pinky
      ..lineTo(26, 22) // Ring
      ..lineTo(32, 16) // Middle
      ..lineTo(38, 20) // Index
      ..lineTo(44, 30) // Thumb
      ..quadraticBezierTo(48, 40, 38, 48)
      ..close();

    canvas.drawPath(hand, Paint()..color = const Color(0xFFFFCC4D));
    canvas.drawPath(hand, Paint()..color = const Color(0xFF664500).withValues(alpha: 0.25)..style = PaintingStyle.stroke..strokeWidth = 1.4);

    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _ChatyMotionEmojiPainter oldDelegate) {
    return oldDelegate.unicode != unicode ||
        oldDelegate.progress != progress ||
        oldDelegate.animate != animate ||
        oldDelegate.style != style;
  }
}
