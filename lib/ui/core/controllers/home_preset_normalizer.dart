/// Canonicalizes legacy/GB-facing home and stories labels before they are
/// persisted into the typed preference model consumed by the UI.
abstract final class HomePresetNormalizer {
  static String homeStyle(String value) => switch (value.trim().toLowerCase()) {
    'chaty' || 'default' || 'chaty default' => 'Chaty Default',
    'classic' => 'Classic',
    'compact' => 'Compact',
    'cards' => 'Cards',
    'minimal' => 'Minimal',
    'stories first' || 'stories-first' => 'Stories First',
    'expressive' => 'Expressive',
    'productivity' => 'Productivity',
    'tablet split view' => 'Tablet Split View',
    _ => 'Chaty Default',
  };

  static String storiesStyle(String value) =>
      switch (value.trim().toLowerCase()) {
        'cards' || 'card' => 'Card',
        'squircle' => 'Squircle',
        'compact' => 'Compact',
        'minimal' => 'Minimal',
        _ => 'Circular',
      };
}
