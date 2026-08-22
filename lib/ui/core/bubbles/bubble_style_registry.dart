import 'package:flutter/material.dart';
import 'bubble_geometry.dart';
import 'bubble_style_id.dart';

/// Central registry that maps every BubbleStyleId to its geometric layout,
/// custom painter properties, stroke styles, and metadata.
class BubbleStyleRegistry {
  BubbleStyleRegistry._();

  static final Map<BubbleStyleId, BubbleGeometry> _geometries = {
    for (final id in BubbleStyleId.values) id: _buildGeometry(id),
  };

  static BubbleGeometry getGeometry(BubbleStyleId id) {
    return _geometries[id] ?? _geometries[BubbleStyleId.stock]!;
  }

  static BubbleGeometry _buildGeometry(BubbleStyleId id) {
    switch (id) {
      case BubbleStyleId.threeD:
      case BubbleStyleId.threeDV2:
        return BubbleGeometry(
          styleId: id,
          hasDropShadow: true,
          shadowElevation: 3.5,
          contentPadding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
        );

      case BubbleStyleId.gabiOutline:
      case BubbleStyleId.rcSamsBord:
      case BubbleStyleId.rcLine:
      case BubbleStyleId.aranbor:
        return BubbleGeometry(
          styleId: id,
          isOutlined: true,
          strokeWidth: 1.5,
          contentPadding: const EdgeInsets.fromLTRB(13, 9, 13, 9),
        );

      case BubbleStyleId.transparent:
        return BubbleGeometry(
          styleId: id,
          isOutlined: true,
          strokeWidth: 1.2,
          contentPadding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
        );

      case BubbleStyleId.kitty:
      case BubbleStyleId.amor:
      case BubbleStyleId.gabiDot:
      case BubbleStyleId.gabiDot2:
      case BubbleStyleId.ilkhang:
        return BubbleGeometry(
          styleId: id,
          hasCustomPainter: true,
          contentPadding: const EdgeInsets.fromLTRB(14, 9, 14, 9),
        );

      case BubbleStyleId.roundle:
      case BubbleStyleId.gabyRon:
      case BubbleStyleId.rcGoogleAssistan:
        return BubbleGeometry(
          styleId: id,
          contentPadding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
        );

      case BubbleStyleId.fold:
      case BubbleStyleId.foldV2:
      case BubbleStyleId.waPaperRedesigned:
        return BubbleGeometry(
          styleId: id,
          hasCustomPainter: true,
          contentPadding: const EdgeInsets.fromLTRB(13, 10, 13, 10),
        );

      default:
        return BubbleGeometry(
          styleId: id,
          contentPadding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
        );
    }
  }
}
