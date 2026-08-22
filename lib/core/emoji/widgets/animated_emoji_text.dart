import 'package:flutter/material.dart';
import '../emoji_parser.dart';
import '../models/parsed_emoji_span.dart';
import 'animated_emoji_view.dart';

/// Renders mixed text and animated emojis, or expressive jumbo/medium animated
/// emojis for emoji-only messages.
class AnimatedEmojiText extends StatelessWidget {
  final String text;
  final TextStyle? style;
  final TextAlign textAlign;
  final bool animate;
  final bool enableExpressiveSizing;
  final int? maxLines;
  final TextOverflow overflow;
  final VoidCallback? onEmojiTap;

  const AnimatedEmojiText({
    super.key,
    required this.text,
    this.style,
    this.textAlign = TextAlign.start,
    this.animate = true,
    this.enableExpressiveSizing = true,
    this.maxLines,
    this.overflow = TextOverflow.clip,
    this.onEmojiTap,
  });

  @override
  Widget build(BuildContext context) {
    if (text.isEmpty) return const SizedBox.shrink();

    final defaultStyle = DefaultTextStyle.of(context).style;
    final effectiveStyle = defaultStyle.merge(style);
    final fontSize = effectiveStyle.fontSize ?? 14.0;

    // Check if this is an emoji-only message that qualifies for expressive sizing
    if (enableExpressiveSizing) {
      final mode = ChatyEmojiParser.resolveEmojiOnlyDisplayMode(text);
      if (mode != null && mode != EmojiDisplayMode.inline) {
        return _buildExpressiveEmojiRow(context, mode, effectiveStyle);
      }
    }

    // Standard inline parsing with proportional emoji height
    final spans = ChatyEmojiParser.parse(text);
    final inlineSize = (fontSize * 1.35).clamp(16.0, 36.0);

    return RichText(
      textAlign: textAlign,
      maxLines: maxLines,
      overflow: overflow,
      text: TextSpan(
        style: effectiveStyle,
        children: spans
            .map<InlineSpan>((span) {
              if (!span.isEmoji) {
                return TextSpan(text: span.rawText);
              }
              return WidgetSpan(
                alignment: PlaceholderAlignment.middle,
                baseline: TextBaseline.alphabetic,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 1.5),
                  child: AnimatedEmojiView(
                    unicode: span.normalizedUnicode,
                    size: inlineSize,
                    animate: animate,
                    mode: EmojiDisplayMode.inline,
                    interactive: false,
                    onTap: onEmojiTap,
                  ),
                ),
              );
            })
            .toList(growable: false),
      ),
    );
  }

  Widget _buildExpressiveEmojiRow(
    BuildContext context,
    EmojiDisplayMode mode,
    TextStyle baseStyle,
  ) {
    final spans = ChatyEmojiParser.parse(
      text.trim(),
    ).where((s) => s.isEmoji).toList(growable: false);

    final double emojiSize = switch (mode) {
      EmojiDisplayMode.jumboSingle => 64.0,
      EmojiDisplayMode.mediumFew => 44.0,
      EmojiDisplayMode.mediumMany => 32.0,
      _ => 24.0,
    };

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Wrap(
        alignment: WrapAlignment.start,
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: 6,
        runSpacing: 6,
        children: spans
            .map((span) {
              return AnimatedEmojiView(
                unicode: span.normalizedUnicode,
                size: emojiSize,
                animate: animate,
                mode: mode,
                interactive: true,
                onTap: onEmojiTap,
              );
            })
            .toList(growable: false),
      ),
    );
  }
}
