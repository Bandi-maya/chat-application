import '../theme/theme_config.dart';

/// Canonicalizes legacy/GB-facing home and stories labels before they are
/// persisted into the typed preference model consumed by the UI.
abstract final class HomePresetNormalizer {
  static String homeStyle(String value) => switch (value.trim().toLowerCase()) {
    'chaty' || 'default' || 'chaty default' => 'Chaty Default',
    'classic' || 'whatsapp old ui' => 'Classic',
    'compact' || 'bubbles tab style' => 'Compact',
    'cards' || 'one ui' => 'Cards',
    'minimal' => 'Minimal',
    'stories first' || 'stories-first' || 'ios style' => 'Stories First',
    // Navigation-only presets must keep their own persisted identity. Mapping
    // these to unrelated home presets caused the shell to restore the wrong
    // navigation mode after a restart or settings refresh.
    'instagram style' => 'Instagram Style',
    'telegram style' => 'Telegram Style',
    'expressive' => 'Expressive',
    'productivity' || 'basic tab style' => 'Productivity',
    'tablet split view' => 'Tablet Split View',
    'whatsapp ui stock' || 'telegram style' => 'Chaty Default',
    // Navigation-shell presets also pick a matching home-list treatment.
    'whatsapp-style top bar' || 'whatsapp style top bar' => 'Classic',
    'floating rail' => 'Floating Rail',
    '3d perspective drawer' => '3D Perspective Drawer',
    'modern side menu' => 'Modern Side Menu',
    'curved radial menu' => 'Curved Radial Menu',
    _ => 'Chaty Default',
  };

  /// Resolves the selected Home UI label to the runtime shell mode.
  ///
  /// Resolves both explicit Home UI labels and canonical labels stored by
  /// older releases, so previously saved selections restore the same layout.
  /// Unknown/custom presets return null and retain the Template Studio mode.
  static AppNavigationMode? navigationMode(String value) =>
      switch (value.trim().toLowerCase()) {
        // Explicit labels and their legacy canonical equivalents share a mode.
        'classic' ||
        'whatsapp old ui' ||
        'whatsapp-style top bar' ||
        'whatsapp style top bar' => AppNavigationMode.topWhatsAppBar,
        'cards' || 'one ui' => AppNavigationMode.oneUi,
        'chaty default' || 'whatsapp ui stock' => AppNavigationMode.bottomNav,
        'stories first' || 'ios style' => AppNavigationMode.iosStyle,
        'instagram style' => AppNavigationMode.instagramStyle,
        'telegram style' => AppNavigationMode.telegramStyle,
        'floating rail' => AppNavigationMode.floatingIslandRail,
        'modern side menu' => AppNavigationMode.modernSideMenu,
        'compact' || 'bubbles tab style' => AppNavigationMode.gestureTabs,
        'productivity' || 'basic tab style' => AppNavigationMode.basicTabStyle,
        '3d perspective drawer' => AppNavigationMode.perspective3DDrawer,
        'curved radial menu' => AppNavigationMode.curvedRadialDrawer,
        _ => null,
      };

  static String storiesStyle(String value) =>
      switch (value.trim().toLowerCase()) {
        'cards' || 'card' => 'Card',
        'squircle' => 'Squircle',
        'compact' => 'Compact',
        'minimal' => 'Minimal',
        'instagram' || 'facebook' || 'stock' || 'circular' => 'Circular',
        _ => 'Circular',
      };
}
