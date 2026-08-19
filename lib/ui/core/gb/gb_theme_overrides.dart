import 'package:flutter/material.dart';

import '../controllers/chaty_preferences_controller.dart';
import '../theme/theme_config.dart';

class GbThemeOverrides {
  const GbThemeOverrides._();

  static ThemeConfig resolve(ThemeConfig base, ChatyPreferencesController prefs) {
    Color? firstColor(List<String> keys) {
      for (final key in keys) {
        final value = prefs.gbColor(key);
        if (value != null) return value;
      }
      return null;
    }

    final dark = base.brightness == Brightness.dark;
    final accent = firstColor(<String>[
      if (dark) 'ModDarkConPickColor',
      'ModConPickColor',
      'ModConColor',
      'tabindicator',
    ]);
    final background = firstColor(<String>[
      if (dark) 'ModDarkConPickColorNav',
      'ModConBackColor',
      'list_bg_color',
      'ConvoBack',
    ]);
    final surface = firstColor(<String>['ModChatColor', 'BGColor']);
    final primaryText = firstColor(<String>['ModConTextColor', 'HomeBarText', 'ModContactNameColor']);
    final outgoingBubble = firstColor(<String>['ModChatRightBubble']);
    final incomingBubble = firstColor(<String>['ModChatLeftBubble']);
    final outgoingText = firstColor(<String>['ModChatBubbleText', 'date_right_color']);
    final incomingText = firstColor(<String>['ModChatBubbleTextLeft', 'date_left_color']);
    final link = firstColor(<String>['ModChatBubbleHyperlinks']);

    final textSize = prefs.gbDouble('text_size_pick', fallback: 15);
    final fontScale = (base.fontScale * (textSize / 15)).clamp(0.78, 1.6).toDouble();
    final bubbleStyle = _bubbleStyle(prefs.gbString('bubble_style', fallback: '')) ?? base.bubbleStyle;

    final candidate = base.copyWith(
      accentColor: accent,
      backgroundColor: background,
      surfaceColor: surface,
      cardColor: surface == null ? null : Color.alphaBlend(
        base.brightness == Brightness.dark
            ? Colors.white.withValues(alpha: 0.045)
            : Colors.black.withValues(alpha: 0.025),
        surface,
      ),
      primaryTextColor: primaryText,
      outgoingBubbleColor: outgoingBubble,
      incomingBubbleColor: incomingBubble,
      outgoingTextColor: outgoingText,
      incomingTextColor: incomingText,
      linkColor: link,
      fontScale: fontScale,
      bubbleStyle: bubbleStyle,
    );

    // Never accept a custom override that makes core text unreadable. If a
    // user picks an unsafe color combination, keep the selected theme's text
    // token for that surface while preserving the other valid overrides.
    return candidate.hasContrastIssue
        ? candidate.copyWith(
            primaryTextColor: _ensureContrast(candidate.primaryTextColor, candidate.backgroundColor, base.primaryTextColor),
            outgoingTextColor: _ensureContrast(candidate.outgoingTextColor, candidate.outgoingBubbleColor, base.outgoingTextColor),
            incomingTextColor: _ensureContrast(candidate.incomingTextColor, candidate.incomingBubbleColor, base.incomingTextColor),
          )
        : candidate;
  }

  static AppBubbleStyle? _bubbleStyle(String raw) {
    final value = raw.toLowerCase();
    if (value.contains('pill')) return AppBubbleStyle.pill;
    if (value.contains('compact') || value.contains('square') || value.contains('card')) return AppBubbleStyle.softSquare;
    if (value.contains('tail-less') || value.contains('tailless')) return AppBubbleStyle.rounded;
    if (value.contains('tail')) return AppBubbleStyle.sharpTail;
    if (value.contains('round') || value.contains('squircle')) return AppBubbleStyle.rounded;
    return null;
  }

  static Color _ensureContrast(Color foreground, Color background, Color fallback) {
    if (ThemeConfig.calculateContrastRatio(foreground, background) >= 3.5) return foreground;
    if (ThemeConfig.calculateContrastRatio(fallback, background) >= 3.5) return fallback;
    return background.computeLuminance() > 0.45 ? Colors.black : Colors.white;
  }
}
