import 'package:flutter/material.dart';
import 'theme_config.dart';
import 'theme_pair_preview.dart';

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
            padding: const EdgeInsets.all(10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Header: Name & Radio selection
                Row(
                  children: [
                    // Radio indicator
                    Container(
                      width: 18,
                      height: 18,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: isSelected
                            ? themeConfig.accentColor
                            : Colors.transparent,
                        border: Border.all(
                          color: isSelected
                              ? themeConfig.accentColor
                              : themeConfig.secondaryTextColor.withValues(
                                  alpha: 0.6,
                                ),
                          width: 2,
                        ),
                      ),
                      child: isSelected
                          ? Center(
                              child: Container(
                                width: 6,
                                height: 6,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color:
                                      themeConfig.brightness == Brightness.dark
                                      ? Colors.black
                                      : Colors.white,
                                ),
                              ),
                            )
                          : null,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        themeConfig.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: themeConfig.primaryTextColor,
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          letterSpacing: -0.2,
                        ),
                      ),
                    ),
                    // Brightness pill
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 1.5,
                      ),
                      decoration: BoxDecoration(
                        color: themeConfig.surfaceColor,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(
                          color: themeConfig.secondaryTextColor.withValues(
                            alpha: 0.2,
                          ),
                        ),
                      ),
                      child: Text(
                        themeConfig.brightness == Brightness.dark
                            ? 'DARK'
                            : 'LIGHT',
                        style: TextStyle(
                          color: themeConfig.secondaryTextColor,
                          fontSize: 9.5,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),

                // Home + Conversation as they look once this theme is applied
                Center(child: ThemePairPreview(themeConfig: themeConfig)),
                const SizedBox(height: 8),

                // Palette Swatches Footer
                Row(
                  children: [
                    _colorDot(themeConfig.backgroundColor, 'Bg'),
                    const SizedBox(width: 4),
                    _colorDot(themeConfig.surfaceColor, 'Surface'),
                    const SizedBox(width: 4),
                    _colorDot(themeConfig.accentColor, 'Accent'),
                    const SizedBox(width: 4),
                    _colorDot(themeConfig.outgoingBubbleColor, 'Bubble'),
                    const Spacer(),
                    Flexible(
                      child: Text(
                        isSelected ? '● Active' : 'Apply',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: isSelected
                              ? themeConfig.accentColor
                              : themeConfig.secondaryTextColor,
                          fontSize: 11,
                          fontWeight: isSelected
                              ? FontWeight.bold
                              : FontWeight.w500,
                        ),
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
