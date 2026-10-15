import 'dart:math' as math;
import 'dart:ui';
import 'package:flutter/material.dart';

/// Resolves hardware camera cutout geometry, status bar heights, and display features
/// to position the Dynamic Island across diverse Android and iOS hardware.
class CutoutAnchorMetrics {
  final double topOffset;
  final double cutoutWidth;
  final double cutoutHeight;
  final Alignment alignment;
  final bool hasCutout;

  const CutoutAnchorMetrics({
    required this.topOffset,
    required this.cutoutWidth,
    required this.cutoutHeight,
    required this.alignment,
    required this.hasCutout,
  });
}

class CutoutAnchorResolver {
  /// Resolves hardware cutout metrics from MediaQuery context.
  static CutoutAnchorMetrics resolve(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final topPadding = mediaQuery.viewPadding.top > 0
        ? mediaQuery.viewPadding.top
        : mediaQuery.padding.top;

    final displayFeatures = mediaQuery.displayFeatures;
    Rect? cutoutRect;

    for (final feature in displayFeatures) {
      if (feature.type == DisplayFeatureType.cutout) {
        if (feature.bounds.top < topPadding + 16) {
          cutoutRect = feature.bounds;
          break;
        }
      }
    }

    if (cutoutRect != null && !cutoutRect.isEmpty) {
      final screenWidth = mediaQuery.size.width;
      final centerX = cutoutRect.center.dx;
      Alignment align = Alignment.topCenter;

      if (centerX < screenWidth * 0.35) {
        align = Alignment.topLeft;
      } else if (centerX > screenWidth * 0.65) {
        align = Alignment.topRight;
      }

      // Vertical centering over the hardware punch-hole or notch
      final verticalCenter = cutoutRect.center.dy;
      final topOffset = math.max(2.0, verticalCenter - 18.0);

      return CutoutAnchorMetrics(
        topOffset: topOffset,
        cutoutWidth: cutoutRect.width,
        cutoutHeight: cutoutRect.height,
        alignment: align,
        hasCutout: true,
      );
    }

    // Default status-bar centered fallback
    final effectiveTop = topPadding > 0
        ? math.max(6.0, (topPadding - 36.0) / 2 + 3.0)
        : 10.0;

    return CutoutAnchorMetrics(
      topOffset: effectiveTop,
      cutoutWidth: 32.0,
      cutoutHeight: 32.0,
      alignment: Alignment.topCenter,
      hasCutout: false,
    );
  }
}
