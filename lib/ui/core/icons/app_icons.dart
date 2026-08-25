import 'package:flutter/material.dart';

/// Centralized original vector icon system for Chaty.
///
/// Standardized on a 24x24 optical grid with 1.8px primary strokes and rounded caps.
class AppIcons {
  AppIcons._();

  // Navigation & Tabs
  static Widget chats({Color? color, double size = 24}) =>
      _vectorIcon(_chatsPath, color: color, size: size);
  static Widget updates({Color? color, double size = 24}) =>
      _vectorIcon(_updatesPath, color: color, size: size);
  static Widget calls({Color? color, double size = 24}) =>
      _vectorIcon(_callsPath, color: color, size: size);
  static Widget profile({Color? color, double size = 24}) =>
      _vectorIcon(_profilePath, color: color, size: size);
  static Widget tasks({Color? color, double size = 24}) =>
      _vectorIcon(_tasksPath, color: color, size: size);

  // Common Actions
  static Widget search({Color? color, double size = 24}) =>
      _vectorIcon(_searchPath, color: color, size: size);
  static Widget pin({Color? color, double size = 24}) =>
      _vectorIcon(_pinPath, color: color, size: size);
  static Widget unpin({Color? color, double size = 24}) =>
      _vectorIcon(_unpinPath, color: color, size: size);
  static Widget archive({Color? color, double size = 24}) =>
      _vectorIcon(_archivePath, color: color, size: size);
  static Widget lock({Color? color, double size = 24}) =>
      _vectorIcon(_lockPath, color: color, size: size);
  static Widget unlock({Color? color, double size = 24}) =>
      _vectorIcon(_unlockPath, color: color, size: size);
  static Widget mute({Color? color, double size = 24}) =>
      _vectorIcon(_mutePath, color: color, size: size);
  static Widget unmute({Color? color, double size = 24}) =>
      _vectorIcon(_unmutePath, color: color, size: size);
  static Widget favourite({Color? color, double size = 24}) =>
      _vectorIcon(_favouritePath, color: color, size: size);
  static Widget favouriteFilled({Color? color, double size = 24}) =>
      _vectorIcon(_favouriteFilledPath, color: color, size: size, fill: true);
  static Widget delete({Color? color, double size = 24}) =>
      _vectorIcon(_deletePath, color: color, size: size);
  static Widget more({Color? color, double size = 24}) =>
      _vectorIcon(_morePath, color: color, size: size, fill: true);
  static Widget settings({Color? color, double size = 24}) =>
      _vectorIcon(_settingsPath, color: color, size: size);
  static Widget camera({Color? color, double size = 24}) =>
      _vectorIcon(_cameraPath, color: color, size: size);
  static Widget send({Color? color, double size = 24}) =>
      _vectorIcon(_sendPath, color: color, size: size);
  static Widget microphone({Color? color, double size = 24}) =>
      _vectorIcon(_micPath, color: color, size: size);
  static Widget attachment({Color? color, double size = 24}) =>
      _vectorIcon(_attachPath, color: color, size: size);

  // Vector render helper
  static Widget _vectorIcon(
    Path Function(Size) pathBuilder, {
    Color? color,
    double size = 24,
    bool fill = false,
  }) {
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: _PathPainter(
          pathBuilder: pathBuilder,
          color: color ?? Colors.white,
          fill: fill,
        ),
      ),
    );
  }

  // Paths
  static Path _chatsPath(Size s) {
    final p = Path();
    p.moveTo(s.width * 0.15, s.height * 0.25);
    p.lineTo(s.width * 0.85, s.height * 0.25);
    p.arcToPoint(
      Offset(s.width * 0.92, s.height * 0.32),
      radius: const Radius.circular(3),
    );
    p.lineTo(s.width * 0.92, s.height * 0.68);
    p.arcToPoint(
      Offset(s.width * 0.85, s.height * 0.75),
      radius: const Radius.circular(3),
    );
    p.lineTo(s.width * 0.45, s.height * 0.75);
    p.lineTo(s.width * 0.25, s.height * 0.90);
    p.lineTo(s.width * 0.25, s.height * 0.75);
    p.lineTo(s.width * 0.15, s.height * 0.75);
    p.arcToPoint(
      Offset(s.width * 0.08, s.height * 0.68),
      radius: const Radius.circular(3),
    );
    p.lineTo(s.width * 0.08, s.height * 0.32);
    p.arcToPoint(
      Offset(s.width * 0.15, s.height * 0.25),
      radius: const Radius.circular(3),
    );
    p.close();
    return p;
  }

  static Path _updatesPath(Size s) {
    final p = Path();
    p.addArc(
      Rect.fromLTWH(
        s.width * 0.12,
        s.height * 0.12,
        s.width * 0.76,
        s.height * 0.76,
      ),
      0.3,
      5.7,
    );
    return p;
  }

  static Path _callsPath(Size s) {
    final p = Path();
    p.moveTo(s.width * 0.25, s.height * 0.15);
    p.quadraticBezierTo(
      s.width * 0.15,
      s.height * 0.35,
      s.width * 0.35,
      s.height * 0.65,
    );
    p.quadraticBezierTo(
      s.width * 0.65,
      s.height * 0.85,
      s.width * 0.85,
      s.height * 0.75,
    );
    p.lineTo(s.width * 0.75, s.height * 0.55);
    p.lineTo(s.width * 0.60, s.height * 0.60);
    p.quadraticBezierTo(
      s.width * 0.40,
      s.height * 0.40,
      s.width * 0.45,
      s.height * 0.25,
    );
    p.close();
    return p;
  }

  static Path _profilePath(Size s) {
    final p = Path();
    p.addOval(
      Rect.fromCircle(
        center: Offset(s.width * 0.5, s.height * 0.35),
        radius: s.width * 0.2,
      ),
    );
    p.moveTo(s.width * 0.15, s.height * 0.85);
    p.quadraticBezierTo(
      s.width * 0.5,
      s.height * 0.65,
      s.width * 0.85,
      s.height * 0.85,
    );
    return p;
  }

  static Path _tasksPath(Size s) {
    final p = Path();
    p.addRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(
          s.width * 0.15,
          s.height * 0.15,
          s.width * 0.7,
          s.height * 0.7,
        ),
        const Radius.circular(4),
      ),
    );
    p.moveTo(s.width * 0.30, s.height * 0.5);
    p.lineTo(s.width * 0.45, s.height * 0.65);
    p.lineTo(s.width * 0.70, s.height * 0.35);
    return p;
  }

  static Path _searchPath(Size s) {
    final p = Path();
    p.addOval(
      Rect.fromLTWH(
        s.width * 0.15,
        s.height * 0.15,
        s.width * 0.55,
        s.height * 0.55,
      ),
    );
    p.moveTo(s.width * 0.60, s.height * 0.60);
    p.lineTo(s.width * 0.85, s.height * 0.85);
    return p;
  }

  static Path _pinPath(Size s) {
    final p = Path();
    p.moveTo(s.width * 0.35, s.height * 0.15);
    p.lineTo(s.width * 0.65, s.height * 0.15);
    p.lineTo(s.width * 0.55, s.height * 0.45);
    p.lineTo(s.width * 0.75, s.height * 0.60);
    p.lineTo(s.width * 0.25, s.height * 0.60);
    p.lineTo(s.width * 0.45, s.height * 0.45);
    p.close();
    p.moveTo(s.width * 0.5, s.height * 0.60);
    p.lineTo(s.width * 0.5, s.height * 0.90);
    return p;
  }

  static Path _unpinPath(Size s) {
    final p = _pinPath(s);
    p.moveTo(s.width * 0.15, s.height * 0.15);
    p.lineTo(s.width * 0.85, s.height * 0.85);
    return p;
  }

  static Path _archivePath(Size s) {
    final p = Path();
    p.addRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(
          s.width * 0.12,
          s.height * 0.15,
          s.width * 0.76,
          s.height * 0.22,
        ),
        const Radius.circular(3),
      ),
    );
    p.moveTo(s.width * 0.20, s.height * 0.37);
    p.lineTo(s.width * 0.20, s.height * 0.82);
    p.lineTo(s.width * 0.80, s.height * 0.82);
    p.lineTo(s.width * 0.80, s.height * 0.37);
    // drawer notch
    p.moveTo(s.width * 0.38, s.height * 0.55);
    p.lineTo(s.width * 0.62, s.height * 0.55);
    return p;
  }

  static Path _lockPath(Size s) {
    final p = Path();
    p.addRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(
          s.width * 0.2,
          s.height * 0.42,
          s.width * 0.6,
          s.height * 0.46,
        ),
        const Radius.circular(4),
      ),
    );
    p.moveTo(s.width * 0.32, s.height * 0.42);
    p.lineTo(s.width * 0.32, s.height * 0.26);
    p.arcToPoint(
      Offset(s.width * 0.68, s.height * 0.26),
      radius: Radius.circular(s.width * 0.18),
    );
    p.lineTo(s.width * 0.68, s.height * 0.42);
    return p;
  }

  static Path _unlockPath(Size s) {
    final p = Path();
    p.addRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(
          s.width * 0.2,
          s.height * 0.42,
          s.width * 0.6,
          s.height * 0.46,
        ),
        const Radius.circular(4),
      ),
    );
    p.moveTo(s.width * 0.32, s.height * 0.42);
    p.lineTo(s.width * 0.32, s.height * 0.24);
    p.arcToPoint(
      Offset(s.width * 0.68, s.height * 0.24),
      radius: Radius.circular(s.width * 0.18),
    );
    p.lineTo(s.width * 0.68, s.height * 0.16);
    return p;
  }

  static Path _mutePath(Size s) {
    final p = Path();
    p.moveTo(s.width * 0.15, s.height * 0.38);
    p.lineTo(s.width * 0.35, s.height * 0.38);
    p.lineTo(s.width * 0.60, s.height * 0.15);
    p.lineTo(s.width * 0.60, s.height * 0.85);
    p.lineTo(s.width * 0.35, s.height * 0.62);
    p.lineTo(s.width * 0.15, s.height * 0.62);
    p.close();
    p.moveTo(s.width * 0.72, s.height * 0.35);
    p.lineTo(s.width * 0.90, s.height * 0.65);
    p.moveTo(s.width * 0.90, s.height * 0.35);
    p.lineTo(s.width * 0.72, s.height * 0.65);
    return p;
  }

  static Path _unmutePath(Size s) {
    final p = Path();
    p.moveTo(s.width * 0.15, s.height * 0.38);
    p.lineTo(s.width * 0.35, s.height * 0.38);
    p.lineTo(s.width * 0.60, s.height * 0.15);
    p.lineTo(s.width * 0.60, s.height * 0.85);
    p.lineTo(s.width * 0.35, s.height * 0.62);
    p.lineTo(s.width * 0.15, s.height * 0.62);
    p.close();
    p.addArc(
      Rect.fromCircle(
        center: Offset(s.width * 0.60, s.height * 0.5),
        radius: s.width * 0.22,
      ),
      -0.8,
      1.6,
    );
    return p;
  }

  static Path _favouritePath(Size s) {
    final p = Path();
    p.moveTo(s.width * 0.5, s.height * 0.82);
    p.cubicTo(
      s.width * 0.2,
      s.height * 0.55,
      s.width * 0.08,
      s.height * 0.35,
      s.width * 0.25,
      s.height * 0.20,
    );
    p.cubicTo(
      s.width * 0.38,
      s.height * 0.10,
      s.width * 0.48,
      s.height * 0.24,
      s.width * 0.5,
      s.height * 0.28,
    );
    p.cubicTo(
      s.width * 0.52,
      s.height * 0.24,
      s.width * 0.62,
      s.height * 0.10,
      s.width * 0.75,
      s.height * 0.20,
    );
    p.cubicTo(
      s.width * 0.92,
      s.height * 0.35,
      s.width * 0.80,
      s.height * 0.55,
      s.width * 0.5,
      s.height * 0.82,
    );
    p.close();
    return p;
  }

  static Path _favouriteFilledPath(Size s) => _favouritePath(s);

  static Path _deletePath(Size s) {
    final p = Path();
    p.moveTo(s.width * 0.2, s.height * 0.25);
    p.lineTo(s.width * 0.8, s.height * 0.25);
    p.moveTo(s.width * 0.38, s.height * 0.25);
    p.lineTo(s.width * 0.38, s.height * 0.15);
    p.lineTo(s.width * 0.62, s.height * 0.15);
    p.lineTo(s.width * 0.62, s.height * 0.25);
    p.moveTo(s.width * 0.26, s.height * 0.25);
    p.lineTo(s.width * 0.30, s.height * 0.85);
    p.lineTo(s.width * 0.70, s.height * 0.85);
    p.lineTo(s.width * 0.74, s.height * 0.25);
    return p;
  }

  static Path _morePath(Size s) {
    final p = Path();
    p.addOval(
      Rect.fromCircle(
        center: Offset(s.width * 0.5, s.height * 0.25),
        radius: 2,
      ),
    );
    p.addOval(
      Rect.fromCircle(
        center: Offset(s.width * 0.5, s.height * 0.50),
        radius: 2,
      ),
    );
    p.addOval(
      Rect.fromCircle(
        center: Offset(s.width * 0.5, s.height * 0.75),
        radius: 2,
      ),
    );
    return p;
  }

  static Path _settingsPath(Size s) {
    final p = Path();
    p.addOval(
      Rect.fromCircle(
        center: Offset(s.width * 0.5, s.height * 0.5),
        radius: s.width * 0.15,
      ),
    );
    p.addOval(
      Rect.fromCircle(
        center: Offset(s.width * 0.5, s.height * 0.5),
        radius: s.width * 0.34,
      ),
    );
    return p;
  }

  static Path _cameraPath(Size s) {
    final p = Path();
    p.addRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(
          s.width * 0.15,
          s.height * 0.25,
          s.width * 0.7,
          s.height * 0.58,
        ),
        const Radius.circular(4),
      ),
    );
    p.addOval(
      Rect.fromCircle(
        center: Offset(s.width * 0.5, s.height * 0.54),
        radius: s.width * 0.14,
      ),
    );
    p.moveTo(s.width * 0.35, s.height * 0.25);
    p.lineTo(s.width * 0.42, s.height * 0.15);
    p.lineTo(s.width * 0.58, s.height * 0.15);
    p.lineTo(s.width * 0.65, s.height * 0.25);
    return p;
  }

  static Path _sendPath(Size s) {
    final p = Path();
    p.moveTo(s.width * 0.15, s.height * 0.2);
    p.lineTo(s.width * 0.85, s.height * 0.5);
    p.lineTo(s.width * 0.15, s.height * 0.8);
    p.lineTo(s.width * 0.32, s.height * 0.5);
    p.close();
    return p;
  }

  static Path _micPath(Size s) {
    final p = Path();
    p.addRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(
          s.width * 0.35,
          s.height * 0.15,
          s.width * 0.3,
          s.height * 0.45,
        ),
        const Radius.circular(8),
      ),
    );
    p.moveTo(s.width * 0.22, s.height * 0.45);
    p.arcToPoint(
      Offset(s.width * 0.78, s.height * 0.45),
      radius: Radius.circular(s.width * 0.28),
      clockwise: false,
    );
    p.moveTo(s.width * 0.5, s.height * 0.73);
    p.lineTo(s.width * 0.5, s.height * 0.88);
    return p;
  }

  static Path _attachPath(Size s) {
    final p = Path();
    p.moveTo(s.width * 0.75, s.height * 0.45);
    p.lineTo(s.width * 0.40, s.height * 0.80);
    p.arcToPoint(
      Offset(s.width * 0.20, s.height * 0.60),
      radius: Radius.circular(s.width * 0.14),
    );
    p.lineTo(s.width * 0.55, s.height * 0.25);
    p.arcToPoint(
      Offset(s.width * 0.75, s.height * 0.45),
      radius: Radius.circular(s.width * 0.14),
    );
    return p;
  }
}

class _PathPainter extends CustomPainter {
  final Path Function(Size) pathBuilder;
  final Color color;
  final bool fill;

  const _PathPainter({
    required this.pathBuilder,
    required this.color,
    this.fill = false,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = fill ? PaintingStyle.fill : PaintingStyle.stroke
      ..strokeWidth = 1.8
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    canvas.drawPath(pathBuilder(size), paint);
  }

  @override
  bool shouldRepaint(covariant _PathPainter oldDelegate) {
    return oldDelegate.color != color || oldDelegate.fill != fill;
  }
}
