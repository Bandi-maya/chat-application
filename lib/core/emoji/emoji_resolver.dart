import 'package:animated_emoji/animated_emoji.dart';
import 'package:flutter/widgets.dart';
import 'emoji_registry.dart';

/// Resolves animated emoji assets and fallbacks taking user settings,
/// system animation preferences, and platform capabilities into account.
class ChatyEmojiResolver {
  const ChatyEmojiResolver._();

  /// Resolves an [AnimatedEmojiData] if animations are enabled and an asset exists.
  /// Otherwise returns null (instructing the renderer to use static Unicode).
  static AnimatedEmojiData? resolve(
    String unicode, {
    bool enabled = true,
    BuildContext? context,
  }) {
    if (!enabled) return null;

    if (context != null) {
      final media = MediaQuery.maybeOf(context);
      if (media != null && media.disableAnimations) {
        return null;
      }
    }

    return ChatyEmojiRegistry.find(unicode);
  }
}
