/// The 10 distinct, visually differentiated Emoji Styles for Chaty.
/// Comprises 4 independently drawn Chaty original styles and 6 verified
/// open-source/source-informed styles, each rendering its own distinct visual artwork.
enum ChatyEmojiStyleId {
  chatyMotion(
    'Chaty Motion',
    'Original expressive animated vector emoji with procedural physics and morphing expressions.',
    '✨ 😂 ❤️',
    true,
  ),
  chatyInk(
    'Chaty Ink',
    'Original hand-drawn manga & calligraphic ink outlines with expressive comic character.',
    '✒️ 😜 💥',
    true,
  ),
  chatySoft3D(
    'Chaty Soft 3D',
    'Original dimensional clay-morphism with multi-stop radial highlights and soft bevels.',
    '🧸 🤩 💖',
    true,
  ),
  chatyPixel(
    'Chaty Pixel',
    'Original 8-bit stepped pixel grid with retro arcade block aesthetics.',
    '👾 🕹️ 🌟',
    false,
  ),
  openMoji(
    'OpenMoji',
    'Verified CC BY-SA 4.0: 2px solid black contours with flat unshaded color fills.',
    '🙌 🌟 🎈',
    false,
  ),
  twemoji(
    'Twemoji',
    'Verified CC BY 4.0: Modern flat vector silhouettes with signature capsule eyes.',
    '🎉 💯 🚀',
    false,
  ),
  notoColor(
    'Noto Color',
    'Verified SIL OFL 1.1: Google Material soft linear gradients and circular geometry.',
    '😃 💖 👍',
    true,
  ),
  fluentColor(
    'Fluent Color',
    'Verified MIT: Microsoft Fluent soft volumetric illumination and curved arches.',
    '🥳 😻 🔥',
    true,
  ),
  blobmoji(
    'Blobmoji',
    'Verified Apache 2.0: Classic Android asymmetric teardrop jelly-blob silhouette.',
    '🍮 🫠 💫',
    true,
  ),
  emojiTwo(
    'EmojiTwo',
    'Verified CC BY 4.0: Illustrated comic-book emoji with glossy rim highlights.',
    '😎 🍕 🏆',
    false,
  );

  final String title;
  final String description;
  final String samplePreview;
  final bool isAnimated;

  const ChatyEmojiStyleId(
    this.title,
    this.description,
    this.samplePreview,
    this.isAnimated,
  );

  // Backward compatibility static aliases for previously stored identifiers & code
  static const ChatyEmojiStyleId systemDefault = ChatyEmojiStyleId.chatyMotion;
  static const ChatyEmojiStyleId twemojiColor = ChatyEmojiStyleId.twemoji;
  static const ChatyEmojiStyleId iosInspired = ChatyEmojiStyleId.chatySoft3D;
  static const ChatyEmojiStyleId oneUiInspired = ChatyEmojiStyleId.notoColor;
  static const ChatyEmojiStyleId soft3D = ChatyEmojiStyleId.chatySoft3D;
  static const ChatyEmojiStyleId minimalOutline = ChatyEmojiStyleId.chatyInk;

  /// Resolves an emoji style ID from stored preference strings, handling
  /// both new canonical titles and legacy saved settings.
  static ChatyEmojiStyleId fromString(String? value) {
    if (value == null) return ChatyEmojiStyleId.chatyMotion;
    final normalized = value.trim().toLowerCase().replaceAll(RegExp(r'[\s_\-]+'), '');

    // Check legacy aliases
    switch (normalized) {
      case 'systemdefault':
      case 'system':
        return ChatyEmojiStyleId.chatyMotion;
      case 'twemojicolor':
      case 'twemoji':
        return ChatyEmojiStyleId.twemoji;
      case 'iosinspired':
      case 'ios':
        return ChatyEmojiStyleId.chatySoft3D;
      case 'oneuiinspired':
      case 'oneui':
        return ChatyEmojiStyleId.notoColor;
      case 'soft3d':
        return ChatyEmojiStyleId.chatySoft3D;
      case 'minimaloutline':
        return ChatyEmojiStyleId.chatyInk;
      case 'chatymotion':
      case 'motion':
        return ChatyEmojiStyleId.chatyMotion;
      case 'chatyink':
      case 'ink':
        return ChatyEmojiStyleId.chatyInk;
      case 'chatypixel':
      case 'pixel':
        return ChatyEmojiStyleId.chatyPixel;
      case 'openmoji':
        return ChatyEmojiStyleId.openMoji;
      case 'notocolor':
      case 'noto':
        return ChatyEmojiStyleId.notoColor;
      case 'fluentcolor':
      case 'fluent':
        return ChatyEmojiStyleId.fluentColor;
      case 'blobmoji':
      case 'blob':
        return ChatyEmojiStyleId.blobmoji;
      case 'emojitwo':
        return ChatyEmojiStyleId.emojiTwo;
    }

    for (final style in ChatyEmojiStyleId.values) {
      final styleNorm = style.name.toLowerCase().replaceAll(RegExp(r'[\s_\-]+'), '');
      final titleNorm = style.title.toLowerCase().replaceAll(RegExp(r'[\s_\-]+'), '');
      if (normalized == styleNorm || normalized == titleNorm) {
        return style;
      }
    }
    return ChatyEmojiStyleId.chatyMotion;
  }
}
