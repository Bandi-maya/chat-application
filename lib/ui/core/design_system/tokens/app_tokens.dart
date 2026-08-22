import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../theme/theme_config.dart';

/// ---------------------------------------------------------------------------
/// CHATY SPACING SCALE (8-pt / 4-pt rhythm)
/// ---------------------------------------------------------------------------
class ChatySpacing {
  static const double xxs = 2.0;
  static const double xs = 4.0;
  static const double sm = 8.0;
  static const double md = 12.0;
  static const double base = 16.0;
  static const double lg = 20.0;
  static const double xl = 24.0;
  static const double xxl = 32.0;
  static const double xxxl = 40.0;
  static const double massive = 48.0;
  static const double jumbo = 64.0;
}

/// ---------------------------------------------------------------------------
/// CHATY RADIUS TOKENS
/// ---------------------------------------------------------------------------
class ChatyRadius {
  static const double xs = 4.0;
  static const double sm = 8.0;
  static const double md = 12.0;
  static const double lg = 16.0;
  static const double xl = 20.0;
  static const double xxl = 24.0;
  static const double card = 18.0;
  static const double sheet = 26.0;
  static const double full = 999.0;

  static const BorderRadius roundedXs = BorderRadius.all(Radius.circular(xs));
  static const BorderRadius roundedSm = BorderRadius.all(Radius.circular(sm));
  static const BorderRadius roundedMd = BorderRadius.all(Radius.circular(md));
  static const BorderRadius roundedLg = BorderRadius.all(Radius.circular(lg));
  static const BorderRadius roundedXl = BorderRadius.all(Radius.circular(xl));
  static const BorderRadius roundedXxl = BorderRadius.all(Radius.circular(xxl));
  static const BorderRadius roundedCard = BorderRadius.all(
    Radius.circular(card),
  );
  static const BorderRadius roundedFull = BorderRadius.all(
    Radius.circular(full),
  );
}

/// ---------------------------------------------------------------------------
/// CHATY ACCESSIBLE TOUCH TARGETS & ICON SIZES
/// ---------------------------------------------------------------------------
class ChatyTouchTargets {
  static const double minTouchTarget = 44.0;
  static const double preferredTouchTarget = 48.0;
  static const double inputHeight = 52.0;
  static const double buttonHeight = 48.0;
  static const double compactButtonHeight = 38.0;
}

class ChatyIconSize {
  static const double xs = 14.0;
  static const double sm = 16.0;
  static const double md = 20.0;
  static const double lg = 24.0;
  static const double xl = 28.0;
  static const double hero = 36.0;
}

/// ---------------------------------------------------------------------------
/// CHATY MOTION TOKENS (Emil Kowalski restrained motion curves & durations)
/// ---------------------------------------------------------------------------
class ChatyMotion {
  // Durations
  static const Duration instant = Duration(milliseconds: 100);
  static const Duration fast = Duration(milliseconds: 160);
  static const Duration standard = Duration(milliseconds: 220);
  static const Duration emphasized = Duration(milliseconds: 300);

  // Easing Curves: Custom smooth cubic curves
  static const Curve enter = Cubic(0.23, 1.0, 0.32, 1.0); // --ease-out
  static const Curve exit = Cubic(0.77, 0.0, 0.175, 1.0);
  static const Curve standardEasing = Cubic(0.2, 0.0, 0.0, 1.0);
  static const Curve spring = Cubic(0.34, 1.56, 0.64, 1.0);
  static const Curve sheetCurve = Cubic(0.32, 0.72, 0.0, 1.0);

  // Active Press Scale factors
  static const double activeScale = 0.97;
  static const double activeIconScale = 0.92;

  // Haptic feedback methods
  static void light() => HapticFeedback.lightImpact();
  static void selection() => HapticFeedback.selectionClick();
  static void medium() => HapticFeedback.mediumImpact();
}

/// ---------------------------------------------------------------------------
/// CHATY TYPOGRAPHY TOKENS (Crisp, clean, readable hierarchy)
/// ---------------------------------------------------------------------------
class ChatyTypography {
  static TextStyle display(Color color, {double scale = 1.0}) => TextStyle(
    fontSize: 28 * scale,
    fontWeight: FontWeight.w800,
    letterSpacing: -0.6,
    height: 1.2,
    color: color,
  );

  static TextStyle largeTitle(Color color, {double scale = 1.0}) => TextStyle(
    fontSize: 22 * scale,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.4,
    height: 1.25,
    color: color,
  );

  static TextStyle title(Color color, {double scale = 1.0}) => TextStyle(
    fontSize: 18 * scale,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.3,
    height: 1.3,
    color: color,
  );

  static TextStyle headline(Color color, {double scale = 1.0}) => TextStyle(
    fontSize: 16 * scale,
    fontWeight: FontWeight.w600,
    letterSpacing: -0.2,
    height: 1.35,
    color: color,
  );

  static TextStyle body(Color color, {double scale = 1.0}) => TextStyle(
    fontSize: 15 * scale,
    fontWeight: FontWeight.w400,
    letterSpacing: -0.1,
    height: 1.45,
    color: color,
  );

  static TextStyle bodyMedium(Color color, {double scale = 1.0}) => TextStyle(
    fontSize: 15 * scale,
    fontWeight: FontWeight.w500,
    letterSpacing: -0.1,
    height: 1.45,
    color: color,
  );

  static TextStyle callout(Color color, {double scale = 1.0}) => TextStyle(
    fontSize: 14 * scale,
    fontWeight: FontWeight.w500,
    letterSpacing: 0.0,
    height: 1.4,
    color: color,
  );

  static TextStyle subheadline(Color color, {double scale = 1.0}) => TextStyle(
    fontSize: 13 * scale,
    fontWeight: FontWeight.w500,
    letterSpacing: 0.0,
    height: 1.35,
    color: color,
  );

  static TextStyle caption(Color color, {double scale = 1.0}) => TextStyle(
    fontSize: 12 * scale,
    fontWeight: FontWeight.w500,
    letterSpacing: 0.1,
    height: 1.3,
    color: color,
  );

  static TextStyle metadata(Color color, {double scale = 1.0}) => TextStyle(
    fontSize: 11 * scale,
    fontWeight: FontWeight.w500,
    letterSpacing: 0.2,
    height: 1.2,
    color: color,
  );
}

/// ---------------------------------------------------------------------------
/// CHATY SEMANTIC COLORS RESOLVER
/// ---------------------------------------------------------------------------
class ChatyColors {
  final ThemeConfig theme;

  const ChatyColors(this.theme);

  bool get isDark => theme.brightness == Brightness.dark;

  Color get background => theme.backgroundColor;
  Color get surface => theme.surfaceColor;
  Color get surfaceElevated => theme.cardColor;
  Color get primary => theme.accentColor;
  Color get onPrimary => theme.onAccentColor;
  Color get textPrimary => theme.primaryTextColor;
  Color get textSecondary => theme.secondaryTextColor;
  Color get textTertiary => theme.secondaryTextColor.withValues(alpha: 0.65);
  Color get border =>
      isDark ? const Color(0xFF27272A) : const Color(0xFFE4E4E7);
  Color get borderSubtle =>
      isDark ? const Color(0xFF18181B) : const Color(0xFFF4F4F5);
  Color get divider =>
      isDark ? const Color(0xFF27272A) : const Color(0xFFE5E7EB);
  Color get success => theme.successColor;
  Color get warning => const Color(0xFFF59E0B);
  Color get error => theme.dangerColor;
  Color get cardBackground => theme.cardColor;
  Color get inputFill =>
      isDark ? const Color(0xFF18181B) : const Color(0xFFF4F4F5);
  Color get highlight => theme.accentColor.withValues(alpha: 0.12);
}
