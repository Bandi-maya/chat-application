import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme/theme_config.dart';

/// Real consumer for the conversation wallpaper settings.
///
/// Rendered behind the message list in [ChatDetailScreen]. Every branch maps
/// to a real visual driven by persisted settings:
///
/// - `Solid`       flat tint blended from the active theme surfaces.
/// - `Gradient`    three-stop diagonal gradient from theme surfaces.
/// - `Pattern`     deterministic vector pattern chosen by the ACTIVE THEME's
///                 `wallpaperId` ('subtle_dots', 'geometric',
///                 'gradient_mesh', 'constellation'; 'none' = plain).
/// - `Image`       the user-picked picture stored at `wallpaperPath`;
///                 gracefully falls back to the gradient when the file is
///                 missing or unreadable.
/// - `ProfileBlur` soft blurred color fields derived from the contact's
///                 avatar color.
class ChatWallpaper extends StatelessWidget {
  final ThemeConfig theme;
  final String wallpaperType;
  final String patternId;
  final String? profileColorHex;
  final String? imagePath;

  const ChatWallpaper({
    super.key,
    required this.theme,
    required this.wallpaperType,
    this.patternId = 'subtle_dots',
    this.profileColorHex,
    this.imagePath,
  });

  static Color? _tryParseHex(String? hex) {
    if (hex == null || hex.isEmpty) return null;
    try {
      return Color(int.parse(hex));
    } catch (_) {
      return null;
    }
  }

  Widget _gradientBox() {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: <Color>[
            theme.surfaceColor,
            theme.backgroundColor,
            Color.lerp(theme.backgroundColor, theme.cardColor, 0.6)!,
          ],
          stops: const <double>[0.0, 0.55, 1.0],
        ),
      ),
      child: const SizedBox.expand(),
    );
  }

  @override
  Widget build(BuildContext context) {
    switch (wallpaperType) {
      case 'Solid':
        {
          return ColoredBox(
            color: Color.lerp(theme.backgroundColor, theme.surfaceColor, 0.5)!,
            child: const SizedBox.expand(),
          );
        }
      case 'Gradient':
        {
          return _gradientBox();
        }
      case 'Image':
        {
          final path = imagePath;
          if (path != null && path.isNotEmpty && File(path).existsSync()) {
            return Image.file(
              File(path),
              fit: BoxFit.cover,
              width: double.infinity,
              height: double.infinity,
              errorBuilder: (_, __, ___) => _gradientBox(),
            );
          }
          // Nothing picked yet, or the file vanished: honest fallback.
          return _gradientBox();
        }
      case 'ProfileBlur':
        {
          final profile = _tryParseHex(profileColorHex) ?? theme.accentColor;
          return CustomPaint(
            painter: _ProfileGlowPainter(
              base: theme.backgroundColor,
              profile: profile,
              accent: theme.accentColor,
              surface: theme.surfaceColor,
            ),
            child: const SizedBox.expand(),
          );
        }
      case 'Pattern':
      default:
        {
          if (patternId == 'none') {
            return ColoredBox(
              color: theme.backgroundColor,
              child: const SizedBox.expand(),
            );
          }
          return CustomPaint(
            painter: _WallpaperPatternPainter(
              patternId: patternId,
              ink: theme.primaryTextColor,
              accent: theme.accentColor,
              success: theme.successColor,
              bubble: theme.incomingBubbleColor,
              base: theme.backgroundColor,
            ),
            child: const SizedBox.expand(),
          );
        }
    }
  }
}

/// Deterministic hash in [0, 1) so the constellation layout never flickers.
double _hash2(int x, int y) {
  final value = math.sin(x * 127.1 + y * 311.7) * 43758.5453;
  return value - value.floorToDouble();
}

class _WallpaperPatternPainter extends CustomPainter {
  final String patternId;
  final Color ink;
  final Color accent;
  final Color success;
  final Color bubble;
  final Color base;

  _WallpaperPatternPainter({
    required this.patternId,
    required this.ink,
    required this.accent,
    required this.success,
    required this.bubble,
    required this.base,
  });

  @override
  void paint(Canvas canvas, Size size) {
    switch (patternId) {
      case 'subtle_dots':
        {
          final paint = Paint()
            ..color = ink.withValues(alpha: 0.06)
            ..style = PaintingStyle.fill;
          const spacing = 26.0;
          for (double x = spacing / 2; x < size.width; x += spacing) {
            for (double y = spacing / 2; y < size.height; y += spacing) {
              canvas.drawCircle(Offset(x, y), 1.3, paint);
            }
          }
        }
      case 'geometric':
        {
          final paint = Paint()
            ..color = ink.withValues(alpha: 0.05)
            ..style = PaintingStyle.stroke
            ..strokeWidth = 1;
          const step = 34.0;
          for (double x = -size.height; x < size.width; x += step) {
            canvas.drawLine(
              Offset(x, 0),
              Offset(x + size.height, size.height),
              paint,
            );
            canvas.drawLine(
              Offset(x + size.height, 0),
              Offset(x, size.height),
              paint,
            );
          }
        }
      case 'gradient_mesh':
        {
          final rect = Offset.zero & size;
          void blob(Offset center, double radius, Color color) {
            final shader = RadialGradient(
              colors: <Color>[
                color.withValues(alpha: 0.12),
                color.withValues(alpha: 0.0),
              ],
            ).createShader(Rect.fromCircle(center: center, radius: radius));
            canvas.drawRect(rect, Paint()..shader = shader);
          }

          blob(
            Offset(size.width * 0.15, size.height * 0.1),
            size.width * 0.9,
            accent,
          );
          blob(
            Offset(size.width * 0.9, size.height * 0.35),
            size.width * 0.8,
            bubble,
          );
          blob(
            Offset(size.width * 0.4, size.height * 0.95),
            size.width * 0.9,
            success,
          );
        }
      case 'constellation':
        {
          final starPaint = Paint()
            ..color = ink.withValues(alpha: 0.11)
            ..style = PaintingStyle.fill;
          final linkPaint = Paint()
            ..color = ink.withValues(alpha: 0.05)
            ..style = PaintingStyle.stroke
            ..strokeWidth = 0.8;
          const cell = 72.0;
          final cols = (size.width / cell).ceil() + 2;
          final rows = (size.height / cell).ceil() + 2;
          Offset point(int cx, int cy) {
            final jitterX = _hash2(cx, cy);
            final jitterY = _hash2(cy, cx);
            return Offset(
              (cx - 0.5 + jitterX * 0.8) * cell,
              (cy - 0.5 + jitterY * 0.8) * cell,
            );
          }

          final grid = <List<Offset>>[
            for (int cy = 0; cy < rows; cy++)
              <Offset>[for (int cx = 0; cx < cols; cx++) point(cx, cy)],
          ];
          for (int cy = 0; cy < rows; cy++) {
            for (int cx = 0; cx < cols; cx++) {
              final p = grid[cy][cx];
              if (cx + 1 < cols) {
                canvas.drawLine(p, grid[cy][cx + 1], linkPaint);
              }
              if (cy + 1 < rows) {
                canvas.drawLine(p, grid[cy + 1][cx], linkPaint);
              }
              canvas.drawCircle(
                p,
                0.8 + _hash2(cx * 7, cy * 13) * 1.4,
                starPaint,
              );
            }
          }
        }
      default:
        break;
    }
  }

  @override
  bool shouldRepaint(_WallpaperPatternPainter oldDelegate) =>
      oldDelegate.patternId != patternId ||
      oldDelegate.base != base ||
      oldDelegate.ink != ink ||
      oldDelegate.accent != accent;
}

class _ProfileGlowPainter extends CustomPainter {
  final Color base;
  final Color profile;
  final Color accent;
  final Color surface;

  _ProfileGlowPainter({
    required this.base,
    required this.profile,
    required this.accent,
    required this.surface,
  });

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Offset.zero & size, Paint()..color = base);
    void glow(Offset center, double radius, Color color) {
      final paint = Paint()
        ..color = color.withValues(alpha: 0.30)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 60);
      canvas.drawCircle(center, radius, paint);
    }

    final w = size.width;
    final h = size.height;
    glow(Offset(w * 0.5, h * 0.22), w * 0.42, profile);
    glow(
      Offset(w * 0.12, h * 0.78),
      w * 0.30,
      Color.lerp(profile, accent, 0.5)!,
    );
    glow(
      Offset(w * 0.88, h * 0.62),
      w * 0.26,
      Color.lerp(profile, surface, 0.55)!,
    );
  }

  @override
  bool shouldRepaint(_ProfileGlowPainter oldDelegate) =>
      oldDelegate.profile != profile ||
      oldDelegate.base != base ||
      oldDelegate.accent != accent;
}
