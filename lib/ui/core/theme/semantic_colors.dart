import 'package:flutter/material.dart';

/// Complete semantic color token contract.
/// Available everywhere via `context.colors` or `Theme.of(context).extension<AppColors>()`.
@immutable
class AppColors extends ThemeExtension<AppColors> {
  final Brightness brightness;

  // Brand & Action
  final Color primary;
  final Color onPrimary;
  final Color secondary;
  final Color onSecondary;
  final Color accent;
  final Color onAccent;

  // Background & Surfaces
  final Color background;
  final Color onBackground;
  final Color surface;
  final Color onSurface;
  final Color surfaceSecondary;
  final Color onSurfaceSecondary;
  final Color surfaceElevated;
  final Color onSurfaceElevated;

  // Foreground / Typography
  final Color foreground;
  final Color foregroundSecondary;
  final Color foregroundTertiary;

  // Borders & Dividers
  final Color border;
  final Color borderSubtle;
  final Color divider;

  // Inputs & Controls
  final Color input;
  final Color inputBorder;
  final Color inputFill;

  // States
  final Color disabled;
  final Color disabledForeground;
  final Color selected;
  final Color onSelected;
  final Color hover;
  final Color pressed;

  // Status & Feedback
  final Color success;
  final Color onSuccess;
  final Color warning;
  final Color onWarning;
  final Color error;
  final Color onError;
  final Color info;
  final Color onInfo;

  // Connection Strength & Message Delivery Health Tokens
  final Color connectionExcellent;
  final Color connectionWeak;
  final Color connectionPoor;
  final Color connectionOffline;

  // Utility
  final Color link;
  final Color icon;
  final Color iconSecondary;
  final Color shadow;

  const AppColors({
    required this.brightness,
    required this.primary,
    required this.onPrimary,
    required this.secondary,
    required this.onSecondary,
    required this.accent,
    required this.onAccent,
    required this.background,
    required this.onBackground,
    required this.surface,
    required this.onSurface,
    required this.surfaceSecondary,
    required this.onSurfaceSecondary,
    required this.surfaceElevated,
    required this.onSurfaceElevated,
    required this.foreground,
    required this.foregroundSecondary,
    required this.foregroundTertiary,
    required this.border,
    required this.borderSubtle,
    required this.divider,
    required this.input,
    required this.inputBorder,
    required this.inputFill,
    required this.disabled,
    required this.disabledForeground,
    required this.selected,
    required this.onSelected,
    required this.hover,
    required this.pressed,
    required this.success,
    required this.onSuccess,
    required this.warning,
    required this.onWarning,
    required this.error,
    required this.onError,
    required this.info,
    required this.onInfo,
    this.connectionExcellent = const Color(0xFF10B981), // Emerald 500
    this.connectionWeak = const Color(0xFFF59E0B),      // Amber 500
    this.connectionPoor = const Color(0xFFEF4444),      // Red 500
    this.connectionOffline = const Color(0xFF71717A),   // Zinc 500
    required this.link,
    required this.icon,
    required this.iconSecondary,
    required this.shadow,
  });

  bool get isDark => brightness == Brightness.dark;

  @override
  AppColors copyWith({
    Brightness? brightness,
    Color? primary,
    Color? onPrimary,
    Color? secondary,
    Color? onSecondary,
    Color? accent,
    Color? onAccent,
    Color? background,
    Color? onBackground,
    Color? surface,
    Color? onSurface,
    Color? surfaceSecondary,
    Color? onSurfaceSecondary,
    Color? surfaceElevated,
    Color? onSurfaceElevated,
    Color? foreground,
    Color? foregroundSecondary,
    Color? foregroundTertiary,
    Color? border,
    Color? borderSubtle,
    Color? divider,
    Color? input,
    Color? inputBorder,
    Color? inputFill,
    Color? disabled,
    Color? disabledForeground,
    Color? selected,
    Color? onSelected,
    Color? hover,
    Color? pressed,
    Color? success,
    Color? onSuccess,
    Color? warning,
    Color? onWarning,
    Color? error,
    Color? onError,
    Color? info,
    Color? onInfo,
    Color? connectionExcellent,
    Color? connectionWeak,
    Color? connectionPoor,
    Color? connectionOffline,
    Color? link,
    Color? icon,
    Color? iconSecondary,
    Color? shadow,
  }) {
    return AppColors(
      brightness: brightness ?? this.brightness,
      primary: primary ?? this.primary,
      onPrimary: onPrimary ?? this.onPrimary,
      secondary: secondary ?? this.secondary,
      onSecondary: onSecondary ?? this.onSecondary,
      accent: accent ?? this.accent,
      onAccent: onAccent ?? this.onAccent,
      background: background ?? this.background,
      onBackground: onBackground ?? this.onBackground,
      surface: surface ?? this.surface,
      onSurface: onSurface ?? this.onSurface,
      surfaceSecondary: surfaceSecondary ?? this.surfaceSecondary,
      onSurfaceSecondary: onSurfaceSecondary ?? this.onSurfaceSecondary,
      surfaceElevated: surfaceElevated ?? this.surfaceElevated,
      onSurfaceElevated: onSurfaceElevated ?? this.onSurfaceElevated,
      foreground: foreground ?? this.foreground,
      foregroundSecondary: foregroundSecondary ?? this.foregroundSecondary,
      foregroundTertiary: foregroundTertiary ?? this.foregroundTertiary,
      border: border ?? this.border,
      borderSubtle: borderSubtle ?? this.borderSubtle,
      divider: divider ?? this.divider,
      input: input ?? this.input,
      inputBorder: inputBorder ?? this.inputBorder,
      inputFill: inputFill ?? this.inputFill,
      disabled: disabled ?? this.disabled,
      disabledForeground: disabledForeground ?? this.disabledForeground,
      selected: selected ?? this.selected,
      onSelected: onSelected ?? this.onSelected,
      hover: hover ?? this.hover,
      pressed: pressed ?? this.pressed,
      success: success ?? this.success,
      onSuccess: onSuccess ?? this.onSuccess,
      warning: warning ?? this.warning,
      onWarning: onWarning ?? this.onWarning,
      error: error ?? this.error,
      onError: onError ?? this.onError,
      info: info ?? this.info,
      onInfo: onInfo ?? this.onInfo,
      connectionExcellent: connectionExcellent ?? this.connectionExcellent,
      connectionWeak: connectionWeak ?? this.connectionWeak,
      connectionPoor: connectionPoor ?? this.connectionPoor,
      connectionOffline: connectionOffline ?? this.connectionOffline,
      link: link ?? this.link,
      icon: icon ?? this.icon,
      iconSecondary: iconSecondary ?? this.iconSecondary,
      shadow: shadow ?? this.shadow,
    );
  }

  @override
  AppColors lerp(ThemeExtension<AppColors>? other, double t) {
    if (other is! AppColors) return this;
    return AppColors(
      brightness: t < 0.5 ? brightness : other.brightness,
      primary: Color.lerp(primary, other.primary, t) ?? primary,
      onPrimary: Color.lerp(onPrimary, other.onPrimary, t) ?? onPrimary,
      secondary: Color.lerp(secondary, other.secondary, t) ?? secondary,
      onSecondary: Color.lerp(onSecondary, other.onSecondary, t) ?? onSecondary,
      accent: Color.lerp(accent, other.accent, t) ?? accent,
      onAccent: Color.lerp(onAccent, other.onAccent, t) ?? onAccent,
      background: Color.lerp(background, other.background, t) ?? background,
      onBackground:
          Color.lerp(onBackground, other.onBackground, t) ?? onBackground,
      surface: Color.lerp(surface, other.surface, t) ?? surface,
      onSurface: Color.lerp(onSurface, other.onSurface, t) ?? onSurface,
      surfaceSecondary:
          Color.lerp(surfaceSecondary, other.surfaceSecondary, t) ??
          surfaceSecondary,
      onSurfaceSecondary:
          Color.lerp(onSurfaceSecondary, other.onSurfaceSecondary, t) ??
          onSurfaceSecondary,
      surfaceElevated:
          Color.lerp(surfaceElevated, other.surfaceElevated, t) ??
          surfaceElevated,
      onSurfaceElevated:
          Color.lerp(onSurfaceElevated, other.onSurfaceElevated, t) ??
          onSurfaceElevated,
      foreground: Color.lerp(foreground, other.foreground, t) ?? foreground,
      foregroundSecondary:
          Color.lerp(foregroundSecondary, other.foregroundSecondary, t) ??
          foregroundSecondary,
      foregroundTertiary:
          Color.lerp(foregroundTertiary, other.foregroundTertiary, t) ??
          foregroundTertiary,
      border: Color.lerp(border, other.border, t) ?? border,
      borderSubtle:
          Color.lerp(borderSubtle, other.borderSubtle, t) ?? borderSubtle,
      divider: Color.lerp(divider, other.divider, t) ?? divider,
      input: Color.lerp(input, other.input, t) ?? input,
      inputBorder: Color.lerp(inputBorder, other.inputBorder, t) ?? inputBorder,
      inputFill: Color.lerp(inputFill, other.inputFill, t) ?? inputFill,
      disabled: Color.lerp(disabled, other.disabled, t) ?? disabled,
      disabledForeground:
          Color.lerp(disabledForeground, other.disabledForeground, t) ??
          disabledForeground,
      selected: Color.lerp(selected, other.selected, t) ?? selected,
      onSelected: Color.lerp(onSelected, other.onSelected, t) ?? onSelected,
      hover: Color.lerp(hover, other.hover, t) ?? hover,
      pressed: Color.lerp(pressed, other.pressed, t) ?? pressed,
      success: Color.lerp(success, other.success, t) ?? success,
      onSuccess: Color.lerp(onSuccess, other.onSuccess, t) ?? onSuccess,
      warning: Color.lerp(warning, other.warning, t) ?? warning,
      onWarning: Color.lerp(onWarning, other.onWarning, t) ?? onWarning,
      error: Color.lerp(error, other.error, t) ?? error,
      onError: Color.lerp(onError, other.onError, t) ?? onError,
      info: Color.lerp(info, other.info, t) ?? info,
      onInfo: Color.lerp(onInfo, other.onInfo, t) ?? onInfo,
      connectionExcellent:
          Color.lerp(connectionExcellent, other.connectionExcellent, t) ??
          connectionExcellent,
      connectionWeak:
          Color.lerp(connectionWeak, other.connectionWeak, t) ?? connectionWeak,
      connectionPoor:
          Color.lerp(connectionPoor, other.connectionPoor, t) ?? connectionPoor,
      connectionOffline:
          Color.lerp(connectionOffline, other.connectionOffline, t) ??
          connectionOffline,
      link: Color.lerp(link, other.link, t) ?? link,
      icon: Color.lerp(icon, other.icon, t) ?? icon,
      iconSecondary:
          Color.lerp(iconSecondary, other.iconSecondary, t) ?? iconSecondary,
      shadow: Color.lerp(shadow, other.shadow, t) ?? shadow,
    );
  }
}
