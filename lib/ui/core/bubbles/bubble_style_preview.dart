import 'package:flutter/material.dart';
import 'bubble_painter.dart';
import 'bubble_style_id.dart';
import 'bubble_style_registry.dart';

/// Container that renders content inside any of the 48 predefined BubbleStyleId geometries.
class ChatyBubbleContainer extends StatelessWidget {
  final BubbleStyleId styleId;
  final bool isMe;
  final Color fillColor;
  final Color? strokeColor;
  final Color accentColor;
  final Widget child;

  const ChatyBubbleContainer({
    super.key,
    required this.styleId,
    required this.isMe,
    required this.fillColor,
    this.strokeColor,
    required this.accentColor,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    final geometry = BubbleStyleRegistry.getGeometry(styleId);
    final effectiveStroke = strokeColor ?? accentColor.withValues(alpha: 0.4);

    return CustomPaint(
      painter: BubblePainter(
        styleId: styleId,
        isMe: isMe,
        fillColor: fillColor,
        strokeColor: effectiveStroke,
        accentColor: accentColor,
      ),
      child: Padding(padding: geometry.contentPadding, child: child),
    );
  }
}

/// Interactive preview tile displaying incoming and outgoing sample bubbles
/// rendered with the specific BubbleStyleId geometry and accent strokes.
class BubbleStylePreviewTile extends StatelessWidget {
  final BubbleStyleId styleId;
  final String label;
  final bool isSelected;
  final Color accentColor;
  final VoidCallback onTap;

  const BubbleStylePreviewTile({
    super.key,
    required this.styleId,
    required this.label,
    required this.isSelected,
    required this.accentColor,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    final incomingFill = isDark
        ? const Color(0xFF1E293B)
        : const Color(0xFFE2E8F0);
    final outgoingFill = accentColor.withValues(alpha: 0.85);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: isSelected
              ? accentColor.withValues(alpha: 0.12)
              : (isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC)),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isSelected
                ? accentColor
                : (isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0)),
            width: isSelected ? 2 : 1,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    label,
                    style: TextStyle(
                      fontWeight: isSelected
                          ? FontWeight.bold
                          : FontWeight.w600,
                      fontSize: 14,
                      color: isSelected
                          ? accentColor
                          : theme.textTheme.bodyMedium?.color,
                    ),
                  ),
                ),
                if (isSelected)
                  Icon(
                    Icons.check_circle_rounded,
                    size: 18,
                    color: accentColor,
                  ),
              ],
            ),
            const SizedBox(height: 10),
            // Outgoing preview bubble
            Align(
              alignment: Alignment.centerRight,
              child: ChatyBubbleContainer(
                styleId: styleId,
                isMe: true,
                fillColor: outgoingFill,
                accentColor: accentColor,
                child: const SizedBox(
                  width: 90,
                  child: Text(
                    'Hey there!',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 6),
            // Incoming preview bubble
            Align(
              alignment: Alignment.centerLeft,
              child: ChatyBubbleContainer(
                styleId: styleId,
                isMe: false,
                fillColor: incomingFill,
                accentColor: accentColor,
                child: SizedBox(
                  width: 100,
                  child: Text(
                    'Looks amazing! 🔥',
                    style: TextStyle(
                      color: isDark ? Colors.white : Colors.black87,
                      fontSize: 11,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
