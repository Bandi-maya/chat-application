import 'package:flutter/material.dart';
import 'theme_config.dart';
import '../bubbles/bubble_painter.dart';
import '../ticks/delivery_status_icon.dart';
import '../../../domain/models/chat_message.dart';

/// Interactive token-driven mini theme preview card.
/// Accurately renders runtime theme tokens without instantiating heavy state or streams.
class ThemePreviewCard extends StatelessWidget {
  final ThemeConfig themeConfig;
  final bool isSelected;
  final VoidCallback onTap;

  const ThemePreviewCard({
    super.key,
    required this.themeConfig,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final bubbleStyle = themeConfig.bubbleStyle;
    final tickStyle = themeConfig.deliveryTickStyle;

    return Container(
      decoration: BoxDecoration(
        color: themeConfig.backgroundColor,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: isSelected
              ? themeConfig.accentColor
              : themeConfig.secondaryTextColor.withValues(alpha: 0.25),
          width: isSelected ? 2.2 : 1.1,
        ),
        boxShadow: [
          if (isSelected)
            BoxShadow(
              color: themeConfig.accentColor.withValues(alpha: 0.28),
              blurRadius: 14,
              offset: const Offset(0, 4),
            )
          else
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.08),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(20),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(20),
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Header: Name & Radio selection
                Row(
                  children: [
                    // Radio indicator
                    Container(
                      width: 20,
                      height: 20,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: isSelected
                            ? themeConfig.accentColor
                            : Colors.transparent,
                        border: Border.all(
                          color: isSelected
                              ? themeConfig.accentColor
                              : themeConfig.secondaryTextColor.withValues(alpha: 0.6),
                          width: 2,
                        ),
                      ),
                      child: isSelected
                          ? Center(
                              child: Container(
                                width: 8,
                                height: 8,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: themeConfig.brightness == Brightness.dark
                                      ? Colors.black
                                      : Colors.white,
                                ),
                              ),
                            )
                          : null,
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        themeConfig.name,
                        style: TextStyle(
                          color: themeConfig.primaryTextColor,
                          fontSize: 15.5,
                          fontWeight: FontWeight.w700,
                          letterSpacing: -0.2,
                        ),
                      ),
                    ),
                    // Brightness pill
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                      decoration: BoxDecoration(
                        color: themeConfig.surfaceColor,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(
                          color: themeConfig.secondaryTextColor.withValues(alpha: 0.2),
                        ),
                      ),
                      child: Text(
                        themeConfig.brightness == Brightness.dark ? 'DARK' : 'LIGHT',
                        style: TextStyle(
                          color: themeConfig.secondaryTextColor,
                          fontSize: 10,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),

                // Mini App Canvas Preview
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
                  decoration: BoxDecoration(
                    color: themeConfig.surfaceColor,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(
                      color: themeConfig.secondaryTextColor.withValues(alpha: 0.15),
                    ),
                  ),
                  child: Column(
                    children: [
                      // Header simulated bar
                      Row(
                        children: [
                          Container(
                            width: 20,
                            height: 20,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: themeConfig.cardColor,
                            ),
                            child: Center(
                              child: Text(
                                'M',
                                style: TextStyle(
                                  color: themeConfig.primaryTextColor,
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(width: 6),
                          Text(
                            'Maya',
                            style: TextStyle(
                              color: themeConfig.primaryTextColor,
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const Spacer(),
                          Container(
                            width: 6,
                            height: 6,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: themeConfig.successColor,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),

                      // Incoming message preview
                      Align(
                        alignment: Alignment.centerLeft,
                        child: CustomPaint(
                          painter: BubblePainter(
                            styleId: bubbleStyle,
                            isMe: false,
                            fillColor: themeConfig.incomingBubbleColor,
                            strokeColor: themeConfig.secondaryTextColor.withValues(alpha: 0.1),
                            accentColor: themeConfig.accentColor,
                          ),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                            child: Text(
                              'Sounds good! 9:41',
                              style: TextStyle(
                                color: themeConfig.incomingTextColor,
                                fontSize: 11.5,
                              ),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 6),

                      // Outgoing message preview with real tick
                      Align(
                        alignment: Alignment.centerRight,
                        child: CustomPaint(
                          painter: BubblePainter(
                            styleId: bubbleStyle,
                            isMe: true,
                            fillColor: themeConfig.outgoingBubbleColor,
                            strokeColor: themeConfig.accentColor.withValues(alpha: 0.2),
                            accentColor: themeConfig.accentColor,
                          ),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(
                                  'Sure 👍',
                                  style: TextStyle(
                                    color: themeConfig.outgoingTextColor,
                                    fontSize: 11.5,
                                  ),
                                ),
                                const SizedBox(width: 5),
                                DeliveryStatusIcon(
                                  style: tickStyle,
                                  state: DeliveryState.read,
                                  unreadColor: themeConfig.outgoingTextColor.withValues(alpha: 0.6),
                                  readColor: themeConfig.brightness == Brightness.dark
                                      ? themeConfig.accentColor
                                      : Colors.white,
                                  size: 13,
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 10),

                // Palette Swatches Footer
                Row(
                  children: [
                    _colorDot(themeConfig.backgroundColor, 'Bg'),
                    const SizedBox(width: 5),
                    _colorDot(themeConfig.surfaceColor, 'Surface'),
                    const SizedBox(width: 5),
                    _colorDot(themeConfig.accentColor, 'Accent'),
                    const SizedBox(width: 5),
                    _colorDot(themeConfig.outgoingBubbleColor, 'Bubble'),
                    const Spacer(),
                    Text(
                      isSelected ? '● Active' : 'Tap to apply',
                      style: TextStyle(
                        color: isSelected
                            ? themeConfig.accentColor
                            : themeConfig.secondaryTextColor,
                        fontSize: 11.5,
                        fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _colorDot(Color color, String tooltip) {
    return Tooltip(
      message: tooltip,
      child: Container(
        width: 14,
        height: 14,
        decoration: BoxDecoration(
          color: color,
          shape: BoxShape.circle,
          border: Border.all(
            color: Colors.black.withValues(alpha: 0.15),
            width: 1,
          ),
        ),
      ),
    );
  }
}
