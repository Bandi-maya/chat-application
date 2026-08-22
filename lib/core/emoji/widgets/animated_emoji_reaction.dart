import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/parsed_emoji_span.dart';
import 'animated_emoji_view.dart';

/// Lightweight reaction pill widget that renders animated emojis with entrance bounce,
/// reaction counts, user highlight state, and semantic tokens.
class AnimatedEmojiReaction extends StatelessWidget {
  final String emoji;
  final int count;
  final bool isSelected;
  final bool animate;
  final VoidCallback? onTap;
  final Color? activeBorderColor;
  final Color? backgroundColor;
  final Color? textColor;

  const AnimatedEmojiReaction({
    super.key,
    required this.emoji,
    required this.count,
    this.isSelected = false,
    this.animate = true,
    this.onTap,
    this.activeBorderColor,
    this.backgroundColor,
    this.textColor,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final bg = backgroundColor ?? theme.cardColor;
    final textCol =
        textColor ?? theme.textTheme.bodyMedium?.color ?? Colors.white;
    final borderCol = isSelected
        ? (activeBorderColor ?? theme.colorScheme.primary)
        : (activeBorderColor?.withValues(alpha: 0.25) ??
              theme.dividerColor.withValues(alpha: 0.25));

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          HapticFeedback.selectionClick();
          onTap?.call();
        },
        borderRadius: BorderRadius.circular(14),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3.5),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: borderCol, width: isSelected ? 1.5 : 1.0),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              AnimatedEmojiView(
                unicode: emoji,
                size: 19.0,
                animate: animate,
                mode: EmojiDisplayMode.reaction,
                interactive: false,
              ),
              if (count > 0) ...[
                const SizedBox(width: 4),
                Text(
                  '$count',
                  style: TextStyle(
                    color: textCol,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
