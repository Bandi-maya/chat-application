import 'dart:async';
import 'package:flutter/material.dart';
import '../persistence/preferences_storage.dart';
import 'theme_config.dart';
import 'theme_presets.dart';

/// Owns the app's global theme, per-chat theme overrides, and display-level
/// settings (layout, navigation, font scale, density, reduced motion).
///
/// PERSISTENCE: the full [ThemeConfig] (including any user customizations made
/// in the theme editor) is stored in the device-global theme bucket and
/// restored by [init] *before the first frame* so there is no theme flash on
/// launch and a user's chosen theme survives restarts. This state is
/// device-global (not account-sensitive) and is intentionally NOT namespaced
/// per user. All mutators persist their result.
class ThemeController extends ChangeNotifier {
  /// Bump when the persisted theme-state shape changes.
  static const int _stateVersion = 1;

  ThemeConfig _globalTheme = ThemePresets.getSystemDefaultTheme();
  final Map<String, ThemeConfig> _perChatThemes = {};
  UILayoutMode _layoutMode = UILayoutMode.classic;
  AppNavigationMode _navigationMode = AppNavigationMode.bottomNav;
  bool _useReducedMotion = false;
  double _fontScale = 1.0;
  double _density = 1.0;
  bool _initialized = false;

  ThemeConfig get globalTheme => _globalTheme;
  UILayoutMode get layoutMode => _layoutMode;
  AppNavigationMode get navigationMode => _navigationMode;
  bool get useReducedMotion => _useReducedMotion;
  double get fontScale => _fontScale;
  double get density => _density;

  /// Loads persisted theme state. Call once from `main()` and `await` it before
  /// `runApp` so the first frame already reflects the user's theme. Safe to call
  /// more than once (subsequent calls are no-ops).
  Future<void> init() async {
    if (_initialized) return;
    _initialized = true;
    try {
      final state = await LocalPreferencesStorage.loadThemeState();
      if (state.isNotEmpty) {
        _applyState(state);
      }
    } catch (_) {
      // Corrupt/absent theme state: keep the system-default theme already set.
      debugPrint(
        'ThemeController: failed to load theme state (using defaults)',
      );
    }
  }

  ThemeConfig getThemeForChat(String conversationId) {
    return _perChatThemes[conversationId] ?? _globalTheme;
  }

  void setGlobalTheme(ThemeConfig newTheme) {
    _globalTheme = newTheme.copyWith(
      layoutMode: _layoutMode,
      navigationMode: _navigationMode,
      fontScale: _fontScale,
      density: _density,
      animationLevel: _useReducedMotion ? 0.0 : 1.0,
    );
    notifyListeners();
    _persist();
  }

  void updateThemeConfig(ThemeConfig customConfig) {
    _globalTheme = customConfig;
    // Keep the standalone display fields consistent with the incoming config so
    // a later setGlobalTheme does not clobber the user's customization.
    _layoutMode = customConfig.layoutMode;
    _navigationMode = customConfig.navigationMode;
    _fontScale = customConfig.fontScale;
    _density = customConfig.density;
    _useReducedMotion = customConfig.animationLevel <= 0.0;
    notifyListeners();
    _persist();
  }

  void setChatTheme(String conversationId, ThemeConfig? customTheme) {
    if (customTheme == null) {
      _perChatThemes.remove(conversationId);
    } else {
      _perChatThemes[conversationId] = customTheme;
    }
    notifyListeners();
    _persist();
  }

  void setLayoutMode(UILayoutMode mode) {
    _layoutMode = mode;
    _globalTheme = _globalTheme.copyWith(layoutMode: mode);
    notifyListeners();
    _persist();
  }

  void setNavigationMode(AppNavigationMode mode) {
    _navigationMode = mode;
    _globalTheme = _globalTheme.copyWith(navigationMode: mode);
    notifyListeners();
    _persist();
  }

  void setFontScale(double scale) {
    _fontScale = scale;
    _globalTheme = _globalTheme.copyWith(fontScale: scale);
    notifyListeners();
    _persist();
  }

  void setDensity(double density) {
    _density = density;
    _globalTheme = _globalTheme.copyWith(density: density);
    notifyListeners();
    _persist();
  }

  void setReducedMotion(bool reduced) {
    _useReducedMotion = reduced;
    _globalTheme = _globalTheme.copyWith(animationLevel: reduced ? 0.0 : 1.0);
    notifyListeners();
    _persist();
  }

  void toggleBrightness() {
    final currentId = _globalTheme.id.toLowerCase();
    if (currentId == 'warm_neutral') {
      setGlobalTheme(ThemePresets.warmNeutralDark);
      return;
    } else if (currentId == 'warm_neutral_dark') {
      setGlobalTheme(ThemePresets.warmNeutral);
      return;
    }

    // Check if there is an exact registered preset counterpart
    if (currentId.contains('dark') || currentId.contains('light')) {
      final targetId = currentId.contains('dark')
          ? currentId.replaceAll('dark', 'light')
          : currentId.replaceAll('light', 'dark');

      final match = ThemePresets.all
          .where((p) => p.id.toLowerCase() == targetId)
          .firstOrNull;
      if (match != null) {
        setGlobalTheme(match); // persists
        return;
      }
    }

    // For any custom or arbitrary theme, dynamically invert & adapt colors
    setGlobalTheme(_globalTheme.toggleBrightness()); // persists
  }

  void resetToDefaults() {
    _globalTheme = ThemePresets.getSystemDefaultTheme();
    _perChatThemes.clear();
    _layoutMode = UILayoutMode.classic;
    _navigationMode = AppNavigationMode.bottomNav;
    _useReducedMotion = false;
    _fontScale = 1.0;
    _density = 1.0;
    notifyListeners();
    _persist();
  }

  // ---------------------------------------------------------------------------
  // Persistence internals
  // ---------------------------------------------------------------------------

  void _persist() {
    // Do not write during the initial load; only user-driven mutations persist.
    if (!_initialized) return;
    unawaited(LocalPreferencesStorage.saveThemeState(_toState()));
  }

  Map<String, dynamic> _toState() {
    final perChat = <String, dynamic>{};
    _perChatThemes.forEach((chatId, cfg) {
      perChat[chatId] = cfg.toMap();
    });
    return {
      'schemaVersion': _stateVersion,
      'globalTheme': _globalTheme.toMap(),
      'perChatThemes': perChat,
      'useReducedMotion': _useReducedMotion,
    };
  }

  void _applyState(Map<String, dynamic> state) {
    // Global theme is authoritative for layout/nav/font/density (setters always
    // sync those into it), so derive the standalone fields from it after decode.
    final rawGlobal = state['globalTheme'];
    if (rawGlobal is Map) {
      try {
        _globalTheme = ThemeConfig.fromMap(
          Map<String, dynamic>.from(rawGlobal),
        );
      } catch (_) {
        debugPrint(
          'ThemeController: failed to decode global theme (keeping default)',
        );
      }
    }

    _layoutMode = _globalTheme.layoutMode;
    _navigationMode = _globalTheme.navigationMode;
    _fontScale = _globalTheme.fontScale;
    _density = _globalTheme.density;

    final reduced = state['useReducedMotion'];
    _useReducedMotion = reduced is bool
        ? reduced
        : _globalTheme.animationLevel <= 0.0;
    // Keep animationLevel consistent with the restored reduced-motion flag.
    _globalTheme = _globalTheme.copyWith(
      animationLevel: _useReducedMotion ? 0.0 : 1.0,
    );

    final rawPerChat = state['perChatThemes'];
    if (rawPerChat is Map) {
      _perChatThemes.clear();
      rawPerChat.forEach((key, value) {
        if (value is Map) {
          try {
            _perChatThemes['$key'] = ThemeConfig.fromMap(
              Map<String, dynamic>.from(value),
            );
          } catch (_) {
            // Skip a single corrupt per-chat entry; others still load.
          }
        }
      });
    }
  }
}
