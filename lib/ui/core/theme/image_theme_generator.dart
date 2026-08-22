import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'theme_config.dart';

/// Analyzes an image and generates a fully balanced, accessible Chaty ThemeConfig
/// using standard Flutter image byte processing without external dependencies.
class ImageThemeGenerator {
  ImageThemeGenerator._();

  /// Generates a semantic ThemeConfig from an image file.
  static Future<ThemeConfig> generateFromImageFile(File imageFile, {bool isDark = true}) async {
    final bytes = await imageFile.readAsBytes();
    final codec = await ui.instantiateImageCodec(bytes, targetWidth: 64, targetHeight: 64);
    final frame = await codec.getNextFrame();
    final image = frame.image;
    final byteData = await image.toByteData(format: ui.ImageByteFormat.rawRgba);

    Color dominant = const Color(0xFF6366F1);
    if (byteData != null) {
      int rSum = 0;
      int gSum = 0;
      int bSum = 0;
      int count = 0;
      for (int i = 0; i < byteData.lengthInBytes; i += 16) {
        final r = byteData.getUint8(i);
        final g = byteData.getUint8(i + 1);
        final b = byteData.getUint8(i + 2);
        final a = byteData.getUint8(i + 3);
        if (a > 128) {
          rSum += r;
          gSum += g;
          bSum += b;
          count++;
        }
      }
      if (count > 0) {
        dominant = Color.fromARGB(255, rSum ~/ count, gSum ~/ count, bSum ~/ count);
      }
    }

    final vibrant = dominant;
    final bgColor = isDark
        ? Color.lerp(const Color(0xFF090D16), dominant, 0.15)!
        : Color.lerp(const Color(0xFFFFFFFF), dominant, 0.05)!;

    final surfaceColor = isDark
        ? Color.lerp(const Color(0xFF131B2E), dominant, 0.25)!
        : Color.lerp(const Color(0xFFF4F6F9), dominant, 0.15)!;

    final cardColor = isDark
        ? Color.lerp(const Color(0xFF1E293B), dominant, 0.30)!
        : const Color(0xFFFFFFFF);

    final primaryText = isDark ? const Color(0xFFF8FAFC) : const Color(0xFF0F172A);
    final secondaryText = isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B);

    final outgoingBubble = vibrant;
    final outgoingText = vibrant.computeLuminance() > 0.5 ? Colors.black : Colors.white;

    final incomingBubble = surfaceColor;
    final incomingText = primaryText;

    return ThemeConfig(
      id: 'image_theme_${DateTime.now().millisecondsSinceEpoch}',
      name: 'Custom Image Theme',
      brightness: isDark ? Brightness.dark : Brightness.light,
      accentColor: vibrant,
      backgroundColor: bgColor,
      surfaceColor: surfaceColor,
      cardColor: cardColor,
      primaryTextColor: primaryText,
      secondaryTextColor: secondaryText,
      outgoingBubbleColor: outgoingBubble,
      incomingBubbleColor: incomingBubble,
      outgoingTextColor: outgoingText,
      incomingTextColor: incomingText,
      linkColor: vibrant,
      dangerColor: const Color(0xFFEF4444),
      successColor: const Color(0xFF10B981),
      cornerRadius: 16.0,
      density: 1.0,
      fontScale: 1.0,
      wallpaperId: 'subtle_dots',
    );
  }
}
