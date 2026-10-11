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
    'expressive' => 'Expressive',
    'productivity' || 'basic tab style' => 'Productivity',
    'tablet split view' => 'Tablet Split View',
    'whatsapp ui stock' => 'Chaty Default',
    // Navigation-shell presets also pick a matching home-list treatment.
    'whatsapp-style top bar' || 'whatsapp style top bar' => 'Classic',
    'floating rail' => 'Stories First',
    '3d perspective drawer' => 'Expressive',
    'modern side menu' => 'Cards',
    'curved radial menu' => 'Expressive',
    _ => 'Chaty Default',
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
