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

      // Dome styles: reserve the dome cap as top content padding so text
      // always starts inside the rectangular body below the painted arc.
      case BubbleStyleId.rcPal:
      case BubbleStyleId.rcImess:
        return BubbleGeometry(
          styleId: id,
          contentPadding: const EdgeInsets.fromLTRB(13, 30, 13, 9),
        );

      case BubbleStyleId.rcImLine:
      case BubbleStyleId.rcTranLine:
        return BubbleGeometry(
          styleId: id,
          isOutlined: true,
          strokeWidth: 1.5,
          contentPadding: const EdgeInsets.fromLTRB(13, 28, 13, 9),
        );

      // Outlined styles whose tails paint outside the widget bounds: reserve
      // margin so the overhang lands in empty space instead of overpainting
      // adjacent messages or timestamps.
      case BubbleStyleId.rcIosLine:
      case BubbleStyleId.gabiMess:
        return BubbleGeometry(
          styleId: id,
          isOutlined: true,
          strokeWidth: 1.5,
          contentPadding: const EdgeInsets.fromLTRB(13, 9, 13, 9),
          bubbleMargin: const EdgeInsets.fromLTRB(16, 2, 16, 10),
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
      case BubbleStyleId.rcFancy:
        return BubbleGeometry(
          styleId: id,
          hasCustomPainter: true,
          contentPadding: const EdgeInsets.fromLTRB(14, 9, 14, 9),
        );

      // Go SMS tails paint outside the widget bounds; reserve margin.
      case BubbleStyleId.goSms:
      case BubbleStyleId.rcGoSms:
        return BubbleGeometry(
          styleId: id,
          contentPadding: const EdgeInsets.fromLTRB(14, 9, 14, 9),
          bubbleMargin: const EdgeInsets.fromLTRB(16, 2, 16, 10),
        );

      // Textra's stem paints below the widget bounds; reserve margin.
      case BubbleStyleId.textra:
        return BubbleGeometry(
          styleId: id,
          hasCustomPainter: true,
          contentPadding: const EdgeInsets.fromLTRB(14, 9, 14, 9),
          bubbleMargin: const EdgeInsets.fromLTRB(16, 2, 16, 10),
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
