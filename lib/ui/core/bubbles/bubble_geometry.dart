import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'bubble_style_id.dart';

/// Immutable geometric layout configuration for message bubbles.
class BubbleGeometry {
  final BubbleStyleId styleId;
  final EdgeInsets contentPadding;
  final EdgeInsets bubbleMargin;
  final double tailWidth;
  final double tailHeight;
  final bool hasCustomPainter;
  final bool isOutlined;
  final double strokeWidth;
  final bool hasDropShadow;
  final double shadowElevation;

  const BubbleGeometry({
    required this.styleId,
    this.contentPadding = const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
    this.bubbleMargin = const EdgeInsets.symmetric(vertical: 2, horizontal: 8),
    this.tailWidth = 6.0,
    this.tailHeight = 10.0,
    this.hasCustomPainter = false,
    this.isOutlined = false,
    this.strokeWidth = 1.5,
    this.hasDropShadow = false,
    this.shadowElevation = 2.0,
  });

  /// Computes the exact vector clipping/painting Path for this geometry given the bounds and direction.
  Path getBubblePath(Rect rect, {required bool isMe}) {
    final path = Path();
    final left = rect.left;
    final top = rect.top;
    final right = rect.right;
    final bottom = rect.bottom;
    final height = rect.height;

    switch (styleId) {
      // 1. Cr Messenger: Super-ellipse pill with subtle pinched tail
      case BubbleStyleId.crMessenger:
        final r = Radius.circular(math.min(18.0, height / 2));
        if (isMe) {
          path.addRRect(
            RRect.fromRectAndCorners(
              rect,
              topLeft: r,
              topRight: r,
              bottomLeft: r,
              bottomRight: const Radius.circular(4),
            ),
          );
        } else {
          path.addRRect(
            RRect.fromRectAndCorners(
              rect,
              topLeft: r,
              topRight: r,
              bottomLeft: const Radius.circular(4),
              bottomRight: r,
            ),
          );
        }
        break;

      // 2. RC BURBUJA 2: Balloon speech bubble with teardrop corner
      case BubbleStyleId.rcBurbuja2:
        final r = Radius.circular(math.min(16.0, height / 2));
        if (isMe) {
          path.moveTo(left + 16, top);
          path.lineTo(right - 16, top);
          path.arcToPoint(Offset(right, top + 16), radius: r);
          path.lineTo(right, bottom - 10);
          // Teardrop tail pointing bottom right
          path.quadraticBezierTo(right + 6, bottom + 4, right - 12, bottom);
          path.lineTo(left + 16, bottom);
          path.arcToPoint(Offset(left, bottom - 16), radius: r);
          path.lineTo(left, top + 16);
          path.arcToPoint(Offset(left + 16, top), radius: r);
        } else {
          path.moveTo(left + 16, top);
          path.lineTo(right - 16, top);
          path.arcToPoint(Offset(right, top + 16), radius: r);
          path.lineTo(right, bottom - 16);
          path.arcToPoint(Offset(right - 16, bottom), radius: r);
          path.lineTo(left + 12, bottom);
          // Teardrop tail pointing bottom left
          path.quadraticBezierTo(left - 6, bottom + 4, left, bottom - 10);
          path.lineTo(left, top + 16);
          path.arcToPoint(Offset(left + 16, top), radius: r);
        }
        break;

      // 3. RC BURBUJA 5: Leaf / pebble organic asymmetrical curve
      case BubbleStyleId.rcBurbuja5:
        if (isMe) {
          path.addRRect(
            RRect.fromRectAndCorners(
              rect,
              topLeft: const Radius.circular(22),
              topRight: const Radius.circular(4),
              bottomLeft: const Radius.circular(4),
              bottomRight: const Radius.circular(22),
            ),
          );
        } else {
          path.addRRect(
            RRect.fromRectAndCorners(
              rect,
              topLeft: const Radius.circular(4),
              topRight: const Radius.circular(22),
              bottomLeft: const Radius.circular(22),
              bottomRight: const Radius.circular(4),
            ),
          );
        }
        break;

      // 4. 3D V2: Chiseled layered bezel
      case BubbleStyleId.threeDV2:
      case BubbleStyleId.threeD:
        path.addRRect(RRect.fromRectAndRadius(rect, const Radius.circular(12)));
        break;

      // 5. Kitty: Whimsical crown arch
      case BubbleStyleId.kitty:
        path.addRRect(
          RRect.fromRectAndCorners(
            rect,
            topLeft: const Radius.circular(18),
            topRight: const Radius.circular(18),
            bottomLeft: isMe ? const Radius.circular(18) : const Radius.circular(4),
            bottomRight: isMe ? const Radius.circular(4) : const Radius.circular(18),
          ),
        );
        break;

      // 6. Amor: Heart crest accent
      case BubbleStyleId.amor:
        path.addRRect(
          RRect.fromRectAndCorners(
            rect,
            topLeft: const Radius.circular(16),
            topRight: const Radius.circular(16),
            bottomLeft: isMe ? const Radius.circular(16) : const Radius.circular(2),
            bottomRight: isMe ? const Radius.circular(2) : const Radius.circular(16),
          ),
        );
        break;

      // 7. RC SAMS BORD: Capsule outline
      case BubbleStyleId.rcSamsBord:
      case BubbleStyleId.gabiOutline:
        path.addRRect(RRect.fromRectAndRadius(rect, const Radius.circular(16)));
        break;

      // 8. RC IOS 11: Sleek rounded iOS tail
      case BubbleStyleId.rcIos11:
      case BubbleStyleId.ios:
        final r = Radius.circular(math.min(18.0, height / 2));
        if (isMe) {
          path.addRRect(
            RRect.fromRectAndCorners(
              rect,
              topLeft: r,
              topRight: r,
              bottomLeft: r,
              bottomRight: const Radius.circular(3),
            ),
          );
        } else {
          path.addRRect(
            RRect.fromRectAndCorners(
              rect,
              topLeft: r,
              topRight: r,
              bottomLeft: const Radius.circular(3),
              bottomRight: r,
            ),
          );
        }
        break;

      // 9. RC GOOGLE ASSISTAN: Pill with 4-corner balanced curve
      case BubbleStyleId.rcGoogleAssistan:
        path.addRRect(RRect.fromRectAndRadius(rect, const Radius.circular(20)));
        break;

      // 10. Gabi Squa: Geometric rounded square
      case BubbleStyleId.gabiSqua:
        path.addRRect(RRect.fromRectAndRadius(rect, const Radius.circular(8)));
        break;

      // 11. Gabi Dot & Gabi Dot 2
      case BubbleStyleId.gabiDot:
      case BubbleStyleId.gabiDot2:
      case BubbleStyleId.ilkhang:
        path.addRRect(
          RRect.fromRectAndCorners(
            rect,
            topLeft: const Radius.circular(14),
            topRight: const Radius.circular(14),
            bottomLeft: isMe ? const Radius.circular(14) : const Radius.circular(2),
            bottomRight: isMe ? const Radius.circular(2) : const Radius.circular(14),
          ),
        );
        break;

      // 12. Rc line: Hairline frame
      case BubbleStyleId.rcLine:
      case BubbleStyleId.aranbor:
        path.addRRect(RRect.fromRectAndRadius(rect, const Radius.circular(4)));
        break;

      // 13. Roundle: Organic circle pill
      case BubbleStyleId.roundle:
      case BubbleStyleId.gabyRon:
        path.addRRect(RRect.fromRectAndRadius(rect, Radius.circular(math.min(24.0, height / 2))));
        break;

      // 14. Stock / Whatsapp LB / Telegram: Classic messaging tail
      case BubbleStyleId.stock:
      case BubbleStyleId.whatsappLb:
      case BubbleStyleId.telegram:
      case BubbleStyleId.waChatOn:
        final r = const Radius.circular(14);
        if (isMe) {
          path.moveTo(left + 14, top);
          path.lineTo(right - 14, top);
          path.arcToPoint(Offset(right, top + 14), radius: r);
          path.lineTo(right, bottom - 10);
          path.lineTo(right + 4, bottom);
          path.lineTo(right - 10, bottom);
          path.lineTo(left + 14, bottom);
          path.arcToPoint(Offset(left, bottom - 14), radius: r);
          path.lineTo(left, top + 14);
          path.arcToPoint(Offset(left + 14, top), radius: r);
        } else {
          path.moveTo(left + 14, top);
          path.lineTo(right - 14, top);
          path.arcToPoint(Offset(right, top + 14), radius: r);
          path.lineTo(right, bottom - 14);
          path.arcToPoint(Offset(right - 14, bottom), radius: r);
          path.lineTo(left + 10, bottom);
          path.lineTo(left - 4, bottom);
          path.lineTo(left, bottom - 10);
          path.lineTo(left, top + 14);
          path.arcToPoint(Offset(left + 14, top), radius: r);
        }
        break;

      // 15. Facebook Messenger / Twitter / Plus Messenger ED
      case BubbleStyleId.facebookMessenger:
      case BubbleStyleId.twitter:
      case BubbleStyleId.plusMessengerEd:
        path.addRRect(RRect.fromRectAndRadius(rect, const Radius.circular(18)));
        break;

      // 16. Old Hangouts / MD / Samyadeep Crayon Alt
      case BubbleStyleId.oldHangouts:
      case BubbleStyleId.newHangouts4:
      case BubbleStyleId.doodleHang:
      case BubbleStyleId.samyadeepCrayonAlt:
        if (isMe) {
          path.addRRect(
            RRect.fromRectAndCorners(
              rect,
              topLeft: const Radius.circular(12),
              topRight: Radius.zero,
              bottomLeft: const Radius.circular(12),
              bottomRight: const Radius.circular(12),
            ),
          );
        } else {
          path.addRRect(
            RRect.fromRectAndCorners(
              rect,
              topLeft: Radius.zero,
              topRight: const Radius.circular(12),
              bottomLeft: const Radius.circular(12),
              bottomRight: const Radius.circular(12),
            ),
          );
        }
        break;

      // 17. Materialized / Rounded / MD
      case BubbleStyleId.materialized:
      case BubbleStyleId.rounded:
      case BubbleStyleId.md:
        path.addRRect(
          RRect.fromRectAndCorners(
            rect,
            topLeft: const Radius.circular(16),
            topRight: const Radius.circular(16),
            bottomLeft: isMe ? const Radius.circular(16) : const Radius.circular(4),
            bottomRight: isMe ? const Radius.circular(4) : const Radius.circular(16),
          ),
        );
        break;

      // 18. WA+ Paper Redesigned / Fold / Fold v2 (folded corner origami)
      case BubbleStyleId.waPaperRedesigned:
      case BubbleStyleId.fold:
      case BubbleStyleId.foldV2:
        if (isMe) {
          path.moveTo(left + 12, top);
          path.lineTo(right - 12, top);
          path.lineTo(right, top + 12);
          path.lineTo(right, bottom - 12);
          path.arcToPoint(Offset(right - 12, bottom), radius: const Radius.circular(12));
          path.lineTo(left + 12, bottom);
          path.arcToPoint(Offset(left, bottom - 12), radius: const Radius.circular(12));
          path.lineTo(left, top + 12);
          path.arcToPoint(Offset(left + 12, top), radius: const Radius.circular(12));
        } else {
          path.moveTo(left + 12, top + 12);
          path.lineTo(left, top);
          path.lineTo(right - 12, top);
          path.arcToPoint(Offset(right, top + 12), radius: const Radius.circular(12));
          path.lineTo(right, bottom - 12);
          path.arcToPoint(Offset(right - 12, bottom), radius: const Radius.circular(12));
          path.lineTo(left + 12, bottom);
          path.arcToPoint(Offset(left, bottom - 12), radius: const Radius.circular(12));
          path.close();
        }
        break;

      // 19. Transparent: clean rounded rect with stroke
      case BubbleStyleId.transparent:
      case BubbleStyleId.inBubble:
      case BubbleStyleId.bryed:
        path.addRRect(RRect.fromRectAndRadius(rect, const Radius.circular(14)));
        break;

      // 20. BBM / Hike / Win: Retro chat bubble
      case BubbleStyleId.bbm:
      case BubbleStyleId.hike:
      case BubbleStyleId.win:
        path.addRRect(
          RRect.fromRectAndCorners(
            rect,
            topLeft: const Radius.circular(8),
            topRight: const Radius.circular(8),
            bottomLeft: isMe ? const Radius.circular(8) : Radius.zero,
            bottomRight: isMe ? Radius.zero : const Radius.circular(8),
          ),
        );
        break;

      // 21. Bubble Drop / Samyadeep Dual / Popzup / Mood / Line
      case BubbleStyleId.bubbleDrop:
      case BubbleStyleId.samyadeepDual:
      case BubbleStyleId.popzup:
      case BubbleStyleId.line:
      case BubbleStyleId.mood:
        path.addRRect(
          RRect.fromRectAndCorners(
            rect,
            topLeft: const Radius.circular(20),
            topRight: const Radius.circular(20),
            bottomLeft: isMe ? const Radius.circular(20) : const Radius.circular(6),
            bottomRight: isMe ? const Radius.circular(6) : const Radius.circular(20),
          ),
        );
        break;
    }

    return path;
  }
}
