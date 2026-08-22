import 'package:flutter/material.dart';
import '../../../domain/models/chat_message.dart';
import 'delivery_icon_style.dart';
import 'delivery_status_icon_painter.dart';

/// Semantic, accessible renderer for message delivery indicators.
class DeliveryStatusIcon extends StatelessWidget {
  final DeliveryIconStyle style;
  final DeliveryState state;
  final Color unreadColor;
  final Color readColor;
  final double size;

  const DeliveryStatusIcon({
    super.key,
    required this.style,
    required this.state,
    required this.unreadColor,
    required this.readColor,
    this.size = 15.0,
  });

  String get _accessibilityLabel {
    switch (state) {
      case DeliveryState.queued:
        return 'Message queued';
      case DeliveryState.sending:
        return 'Message sending';
      case DeliveryState.sent:
        return 'Message sent';
      case DeliveryState.delivered:
        return 'Message delivered';
      case DeliveryState.read:
        return 'Message read';
      case DeliveryState.failed:
        return 'Message failed to send';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: _accessibilityLabel,
      child: SizedBox(
        width: size,
        height: size,
        child: CustomPaint(
          painter: DeliveryStatusIconPainter(
            style: style,
            state: state,
            unreadColor: unreadColor,
            readColor: readColor,
          ),
        ),
      ),
    );
  }
}

/// A preview tile for selecting among the 16 DeliveryIconStyle options.
class DeliveryStatusPreviewTile extends StatelessWidget {
  final DeliveryIconStyle style;
  final bool isSelected;
  final VoidCallback onTap;
  final Color primaryTextColor;
  final Color accentColor;
  final Color unreadColor;
  final Color readColor;

  const DeliveryStatusPreviewTile({
    super.key,
    required this.style,
    required this.isSelected,
    required this.onTap,
    required this.primaryTextColor,
    required this.accentColor,
    required this.unreadColor,
    required this.readColor,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        decoration: BoxDecoration(
          color: isSelected ? accentColor.withValues(alpha: 0.1) : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
          border: isSelected
              ? Border.all(color: accentColor.withValues(alpha: 0.4), width: 1.5)
              : null,
        ),
        child: Row(
          children: [
            // Preview: Delivered (unread) + Read (tinted)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
              decoration: BoxDecoration(
                color: primaryTextColor.withValues(alpha: 0.06),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  DeliveryStatusIcon(
                    style: style,
                    state: DeliveryState.read,
                    unreadColor: unreadColor,
                    readColor: readColor,
                    size: 18,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Text(
                style.displayName,
                style: TextStyle(
                  color: primaryTextColor,
                  fontSize: 15,
                  fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                ),
              ),
            ),
            Container(
              width: 22,
              height: 22,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: isSelected ? accentColor : primaryTextColor.withValues(alpha: 0.3),
                  width: 2,
                ),
              ),
              child: isSelected
                  ? Center(
                      child: Container(
                        width: 12,
                        height: 12,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: accentColor,
                        ),
                      ),
                    )
                  : null,
            ),
          ],
        ),
      ),
    );
  }
}
