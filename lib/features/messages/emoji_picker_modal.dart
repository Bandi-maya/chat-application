import 'package:animated_emoji/animated_emoji.dart';
import 'package:flutter/material.dart';
import '../../core/emoji/emoji_registry.dart';
import '../../core/emoji/widgets/chaty_emoji_picker.dart' as central;
export '../../core/emoji/widgets/chaty_emoji_picker.dart' show ChatyEmojiComposerAction, ChatyEmojiComposerResult;

/// Public facade for the Chaty Emoji Picker.
class ChatyEmojiPicker {
  static Future<String?> show(
    BuildContext context, {
    bool reactionMode = false,
    Color? headerColor,
    Color? headerIconsColor,
    Color? backgroundColor,
  }) {
    return central.ChatyEmojiPicker.show(
      context,
      reactionMode: reactionMode,
      headerColor: headerColor,
      headerIconsColor: headerIconsColor,
      backgroundColor: backgroundColor,
    );
  }
  static Future<central.ChatyEmojiComposerResult?> showComposer(
    BuildContext context, {
    String initialText = '',
    Color? headerColor,
    Color? headerIconsColor,
    Color? backgroundColor,
  }) {
    return central.ChatyEmojiPicker.showComposer(
      context,
      initialText: initialText,
      headerColor: headerColor,
      headerIconsColor: headerIconsColor,
      backgroundColor: backgroundColor,
    );
  }

}

/// Backwards compatibility helper mapping a unicode sequence to AnimatedEmojiData.
AnimatedEmojiData? chatyAnimatedEmojiForUnicode(String value) {
  return ChatyEmojiRegistry.find(value);
}
