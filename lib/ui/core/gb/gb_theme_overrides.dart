import 'package:flutter/material.dart';

import '../controllers/preferences_controller.dart';
import '../theme/theme_config.dart';
import '../bubbles/bubble_style_id.dart';
import '../ticks/delivery_icon_style.dart';

class GbThemeOverrides {
  const GbThemeOverrides._();

  static ThemeConfig resolve(
    ThemeConfig base,
    ChatyPreferencesController prefs,
  ) {
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
    final primaryText = firstColor(<String>[
      'ModConTextColor',
      'HomeBarText',
      'ModContactNameColor',
    ]);
    final outgoingBubble = firstColor(<String>['ModChatRightBubble']);
    final incomingBubble = firstColor(<String>['ModChatLeftBubble']);
    final outgoingText = firstColor(<String>['ModChatBubbleRightColor']);
    final incomingText = firstColor(<String>['ModChatBubbleLeftColor']);
    final link = firstColor(<String>['ModChatLinkColor']);
    final fontScaleRaw = prefs.gbDouble('font_scale');
    final fontScale = (fontScaleRaw > 0.0 && fontScaleRaw.isFinite)
        ? fontScaleRaw.clamp(0.5, 2.0)
        : null;

    // Resolve the bubble geometry from the GB catalog key first (legacy/mod
    // themes), then fall back to the structured Conversation "bubble shape"
    // picker, and finally the base theme. Previously only the GB key was read,
    // so the structured picker was write-only.
    final bubbleStyle =
        _bubbleStyle(prefs.gbString('bubble_style', fallback: '')) ??
        _bubbleStyle(prefs.conversation.bubbleStyle) ??
        base.bubbleStyle;

    final deliveryTickStyle = DeliveryIconStyleExtension.fromString(
      prefs.conversation.tickStyle,
    );

    final candidate = base.copyWith(
      accentColor: accent,
      backgroundColor: background,
      surfaceColor: surface,
      cardColor: surface == null
          ? null
          : Color.alphaBlend(
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
      deliveryTickStyle: deliveryTickStyle,
      tickStyle: prefs.conversation.tickStyle,
    );

    // Never accept a custom override that makes core text unreadable. If a
    // user picks an unsafe color combination, keep the selected theme's text
    // token for that surface while preserving the other valid overrides.
    if (!candidate.hasContrastIssue) return candidate;

    var repaired = candidate.copyWith(
      // Primary text must read on BOTH the canvas and elevated surfaces
      // (app bars, cards), so repair against whichever is currently worse.
      primaryTextColor: _ensureContrastWorst(
        candidate.primaryTextColor,
        <Color>[candidate.backgroundColor, candidate.surfaceColor],
        base.primaryTextColor,
      ),
      secondaryTextColor: _ensureContrast(
        candidate.secondaryTextColor,
        candidate.backgroundColor,
        base.secondaryTextColor,
      ),
      outgoingTextColor: _ensureContrast(
        candidate.outgoingTextColor,
        candidate.outgoingBubbleColor,
        base.outgoingTextColor,
      ),
      incomingTextColor: _ensureContrast(
        candidate.incomingTextColor,
        candidate.incomingBubbleColor,
        base.incomingTextColor,
      ),
    );

    // Some custom themes pick a surface that is tonally opposite to the
    // canvas (e.g. near-black app bar under a near-white background). No
    // single text token can then read on both, so pull the SURFACE toward
    // the canvas until one does. This is the only case where we touch a
    // user-chosen surface color.
    final surfaces = <Color>[repaired.backgroundColor, repaired.surfaceColor];
    if (_ensureContrastWorst(
          repaired.primaryTextColor,
          surfaces,
          base.primaryTextColor,
        ) !=
        repaired.primaryTextColor) {
      var surface = repaired.surfaceColor;
      for (var i = 0; i < 4; i++) {
        surface = Color.alphaBlend(
          repaired.backgroundColor.withValues(alpha: 0.45),
          surface,
        );
        final attempt = repaired.copyWith(surfaceColor: surface);
        if (ThemeConfig.calculateContrastRatio(
              attempt.primaryTextColor,
              surface,
            ) >=
            3.5) {
          repaired = attempt;
          break;
        }
      }
      if (repaired.surfaceColor != surface) {
        repaired = repaired.copyWith(surfaceColor: surface);
      }
    }
    return repaired;
  }

  static BubbleStyleId? _bubbleStyle(String raw) {
    if (raw.isEmpty) return null;
    return BubbleStyleIdExtension.fromString(raw);
  }

  static Color _ensureContrast(
    Color foreground,
    Color background,
    Color fallback,
  ) {
    if (ThemeConfig.calculateContrastRatio(foreground, background) >= 3.5)
      return foreground;
    if (ThemeConfig.calculateContrastRatio(fallback, background) >= 3.5)
      return fallback;
    return background.computeLuminance() > 0.45 ? Colors.black : Colors.white;
  }

  static Color _ensureContrastWorst(
    Color foreground,
    List<Color> backgrounds,
    Color fallback,
  ) {
    var minCandidate = double.infinity;
    var minFallback = double.infinity;
    for (final bg in backgrounds) {
      final cRatio = ThemeConfig.calculateContrastRatio(foreground, bg);
      final fRatio = ThemeConfig.calculateContrastRatio(fallback, bg);
      if (cRatio < minCandidate) minCandidate = cRatio;
      if (fRatio < minFallback) minFallback = fRatio;
    }
    if (minCandidate >= 3.5) return foreground;
    if (minFallback >= 3.5) return fallback;
    return backgrounds.first.computeLuminance() > 0.45
        ? Colors.black
        : Colors.white;
  }
}
