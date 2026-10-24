import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../theme/theme_extensions.dart';

/// One selectable choice with a small live preview. Tapping it selects it
/// immediately (no separate Apply step), so the screen's pinned preview can
/// update while the user is still looking at the options.
///
/// Every colour comes from the resolved theme tokens, so the card follows
/// whatever light or dark theme is active. The whole card is one tap target
/// with a selected state exposed to screen readers.
class ChatyOptionCard extends StatelessWidget {
  const ChatyOptionCard({
    super.key,
    required this.label,
    required this.preview,
    required this.selected,
    required this.onTap,
    this.width = 104,
    this.previewHeight = 56,
  });

  final String label;
  final Widget preview;
  final bool selected;
  final VoidCallback onTap;
  final double width;
  final double previewHeight;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final radius = BorderRadius.circular(14);
    return Semantics(
      button: true,
      selected: selected,
      label: label,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: radius,
          onTap: () {
            HapticFeedback.selectionClick();
            onTap();
          },
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 160),
            width: width,
            padding: const EdgeInsets.fromLTRB(8, 8, 8, 8),
            decoration: BoxDecoration(
              color: selected
                  ? colors.primary.withValues(alpha: 0.12)
                  : colors.surfaceSecondary,
              borderRadius: radius,
              border: Border.all(
                color: selected ? colors.primary : colors.borderSubtle,
                width: selected ? 2 : 1,
              ),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(
                  height: previewHeight,
                  child: Stack(
                    children: [
                      Positioned.fill(child: Center(child: preview)),
                      if (selected)
                        Positioned(
                          top: 0,
                          right: 0,
                          child: Icon(
                            Icons.check_circle_rounded,
                            size: 18,
                            color: colors.primary,
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                    color: selected ? colors.primary : colors.foreground,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// A titled, horizontally scrolling row of [ChatyOptionCard]s. Used for
/// style pickers where the user compares small previews and picks one.
class ChatyOptionGallery<T> extends StatelessWidget {
  const ChatyOptionGallery({
    super.key,
    required this.title,
    this.subtitle,
    required this.values,
    required this.selected,
    required this.labelOf,
    required this.previewOf,
    required this.onSelected,
  });

  final String title;
  final String? subtitle;
  final List<T> values;
  final T selected;
  final String Function(T) labelOf;
  final Widget Function(T) previewOf;
  final ValueChanged<T> onSelected;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 2),
          child: Text(
            title,
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w700,
              color: colors.foreground,
            ),
          ),
        ),
        if (subtitle != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 4),
            child: Text(
              subtitle!,
              style: TextStyle(
                fontSize: 12,
                color: colors.foregroundSecondary,
              ),
            ),
          ),
        const SizedBox(height: 6),
        SizedBox(
          // Scales with the user's text size so labels are never clipped.
          height: MediaQuery.textScalerOf(context).scale(118),
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            itemCount: values.length,
            separatorBuilder: (_, __) => const SizedBox(width: 10),
            itemBuilder: (context, i) {
              final v = values[i];
              return ChatyOptionCard(
                label: labelOf(v),
                preview: previewOf(v),
                selected: v == selected,
                onTap: () => onSelected(v),
              );
            },
          ),
        ),
      ],
    );
  }
}
