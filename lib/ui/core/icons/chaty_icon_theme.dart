/// The 10 distinct, original theme directions for Chaty icons.
/// Each theme embodies a completely independent design grammar, silhouette topology,
/// and mass balance rather than mere stroke-width or color recoloring.
enum ChatyIconThemeId {
  chatySigil(
    'Chaty Sigil',
    'Signature Chaty visual identity with balanced geometry and orbital counter-spaces.',
  ),
  precisionContour(
    'Precision Contour',
    'Hairline architectural contour drafting with precision open terminals.',
  ),
  monolithicCutout(
    'Monolithic Cutout',
    'Bold solid silhouettes with stenciled negative-space internal cuts.',
  ),
  origamiGeometry(
    'Origami Geometry',
    'Faceted paper-fold geometry with 45°/60° planar angular creases.',
  ),
  organicMotion(
    'Organic Motion',
    'Fluid droplet contours and asymmetrical pebble-smooth organic curves.',
  ),
  pixelPrecision(
    'Pixel Precision',
    'Authentic 8-bit stepped pixel grid with modular block construction.',
  ),
  technicalBlueprint(
    'Technical Blueprint',
    'Engineered CAD schematic linework with corner ticks and datum crosshairs.',
  ),
  sculptedVolume(
    'Sculpted Volume',
    'Multi-planar dimensional facets implying depth via overlapping planes.',
  ),
  expressiveInk(
    'Expressive Ink',
    'Dynamic brush-pen calligraphy with high-contrast pressure stroke rhythm.',
  ),
  signalLanguage(
    'Signal Language',
    'Telecommunication radar motifs with concentric broadcast pulse waves.',
  );

  final String title;
  final String description;

  const ChatyIconThemeId(this.title, this.description);

  // Backward compatibility static aliases for previously stored identifiers & code
  static const ChatyIconThemeId chatyOrbit = ChatyIconThemeId.chatySigil;
  static const ChatyIconThemeId precisionLine = ChatyIconThemeId.precisionContour;
  static const ChatyIconThemeId softGeometry = ChatyIconThemeId.monolithicCutout;
  static const ChatyIconThemeId liquidMotion = ChatyIconThemeId.organicMotion;
  static const ChatyIconThemeId sculptedDuotone = ChatyIconThemeId.sculptedVolume;
  static const ChatyIconThemeId signalPulse = ChatyIconThemeId.signalLanguage;
  static const ChatyIconThemeId neoMinimal = ChatyIconThemeId.technicalBlueprint;
  static const ChatyIconThemeId inkFlow = ChatyIconThemeId.expressiveInk;
  static const ChatyIconThemeId prismEdge = ChatyIconThemeId.origamiGeometry;

  /// Resolves an icon theme ID from stored preference strings, handling
  /// both new canonical titles and legacy saved settings.
  static ChatyIconThemeId fromString(String? value) {
    if (value == null) return ChatyIconThemeId.chatySigil;
    final normalized = value.trim().toLowerCase().replaceAll(RegExp(r'[\s_\-]+'), '');

    // Check legacy aliases
    switch (normalized) {
      case 'chatyorbit':
      case 'orbit':
        return ChatyIconThemeId.chatySigil;
      case 'precisionline':
        return ChatyIconThemeId.precisionContour;
      case 'softgeometry':
        return ChatyIconThemeId.monolithicCutout;
      case 'liquidmotion':
        return ChatyIconThemeId.organicMotion;
      case 'sculptedduotone':
        return ChatyIconThemeId.sculptedVolume;
      case 'signalpulse':
        return ChatyIconThemeId.signalLanguage;
      case 'neominimal':
        return ChatyIconThemeId.technicalBlueprint;
      case 'inkflow':
        return ChatyIconThemeId.expressiveInk;
      case 'prismedge':
        return ChatyIconThemeId.origamiGeometry;
    }

    for (final theme in ChatyIconThemeId.values) {
      final themeNorm = theme.name.toLowerCase().replaceAll(RegExp(r'[\s_\-]+'), '');
      final titleNorm = theme.title.toLowerCase().replaceAll(RegExp(r'[\s_\-]+'), '');
      if (normalized == themeNorm || normalized == titleNorm) {
        return theme;
      }
    }
    return ChatyIconThemeId.chatySigil;
  }
}
