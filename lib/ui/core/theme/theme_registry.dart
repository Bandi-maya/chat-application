import 'package:flutter/material.dart';
import 'theme_config.dart';
import 'theme_presets.dart';

/// Central Authority and Registry for all application themes.
/// Guarantees that every theme satisfies the full semantic contract.
class ThemeRegistry {
  const ThemeRegistry._();

  /// All registered themes in the application.
  static List<ThemeConfig> get allThemes => ThemePresets.all;

  /// Retrieve a theme by ID with graceful fallback.
  static ThemeConfig getById(String id) => ThemePresets.getById(id);

  /// Determine system default theme (e.g. WhatsApp iOS Light / Dark).
  static ThemeConfig getSystemDefault([Brightness? platformBrightness]) =>
      ThemePresets.getSystemDefaultTheme(platformBrightness);

  /// Register/Lookup theme by brightness.
  static List<ThemeConfig> getDarkThemes() => allThemes
      .where((t) => t.brightness == Brightness.dark)
      .toList(growable: false);

  static List<ThemeConfig> getLightThemes() => allThemes
      .where((t) => t.brightness == Brightness.light)
      .toList(growable: false);
}
