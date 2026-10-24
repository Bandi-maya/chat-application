import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../ui/core/theme/theme_extensions.dart';
import 'spring_interactive_scale.dart';

/// Segment tab item model for MorphingSegmentedControl.
class MorphingSegmentItem<T> {
  final T value;
  final String label;
  final IconData? icon;
  final int? badgeCount;

  const MorphingSegmentItem({
    required this.value,
    required this.label,
    this.icon,
    this.badgeCount,
  });
}

/// Fluid sliding-pill segmented filter control with spring animations
/// for inbox views (All, Unread, Groups, Favorites, Vault).
class MorphingSegmentedControl<T> extends StatelessWidget {
  final List<MorphingSegmentItem<T>> items;
  final T selectedValue;
  final ValueChanged<T> onSelected;
  final Color? activePillColor;
  final Color? activeTextColor;
  final Color? inactiveTextColor;
  final double height;

  const MorphingSegmentedControl({
    super.key,
    required this.items,
    required this.selectedValue,
    required this.onSelected,
    this.activePillColor,
    this.activeTextColor,
    this.inactiveTextColor,
    this.height = 42.0,
  });

  @override
  Widget build(BuildContext context) {
    final tokens = context.colors;
    final pillColor = activePillColor ?? tokens.primary;
    final textActive = activeTextColor ?? tokens.onPrimary;
    final textInactive = inactiveTextColor ?? tokens.foregroundSecondary;

    return Container(
      height: height,
      padding: const EdgeInsets.all(4.0),
      decoration: BoxDecoration(
        color: tokens.surfaceSecondary,
        borderRadius: BorderRadius.circular(height / 2),
        border: Border.all(
          color: tokens.borderSubtle,
          width: 1.0,
        ),
      ),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        physics: const BouncingScrollPhysics(),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: items.map((item) {
            final isSelected = item.value == selectedValue;
            return SpringInteractiveScale(
              onTap: () {
                if (!isSelected) {
                  HapticFeedback.selectionClick();
                  onSelected(item.value);
                }
              },
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 220),
                curve: Curves.easeOutCubic,
                padding: const EdgeInsets.symmetric(horizontal: 14.0, vertical: 6.0),
                decoration: BoxDecoration(
                  color: isSelected ? pillColor : Colors.transparent,
                  borderRadius: BorderRadius.circular(height / 2),
                  boxShadow: isSelected
                      ? [
                          BoxShadow(
                            color: pillColor.withOpacity(0.35),
                            blurRadius: 10,
                            offset: const Offset(0, 3),
                          ),
                        ]
                      : null,
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (item.icon != null) ...[
                      Icon(
                        item.icon,
                        size: 15,
                        color: isSelected ? textActive : textInactive,
                      ),
                      const SizedBox(width: 5),
                    ],
                    Text(
                      item.label,
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                        color: isSelected ? textActive : textInactive,
                      ),
                    ),
                    if (item.badgeCount != null && item.badgeCount! > 0) ...[
                      const SizedBox(width: 6),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: isSelected ? textActive.withValues(alpha: 0.25) : pillColor,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          '${item.badgeCount}',
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            color: isSelected ? textActive : tokens.onPrimary,
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }
}
