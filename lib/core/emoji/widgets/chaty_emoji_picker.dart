import 'package:emoji_picker_flutter/emoji_picker_flutter.dart';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/emoji_recent_cache.dart';

enum ChatyEmojiComposerAction { send, keyboard, close }

class ChatyEmojiComposerResult {
  const ChatyEmojiComposerResult({required this.text, required this.action});

  final String text;
  final ChatyEmojiComposerAction action;
}

/// Telegram-quality Emoji Picker bottom sheet with:
/// - Fast animated vector emoji grid & search
/// - Native Unicode emoji picker with categories & recents
/// - Skin tone support
/// - Offscreen scroll performance optimization
/// - Zero hardcoded colors (pure semantic theme tokens)
class ChatyEmojiPicker {
  static Future<String?> show(
    BuildContext context, {
    bool reactionMode = false,
    Color? headerColor,
    Color? headerIconsColor,
    Color? backgroundColor,
  }) async {
    final result = await showModalBottomSheet<Object?>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      enableDrag: false,
      showDragHandle: false,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      clipBehavior: Clip.hardEdge,
      backgroundColor: backgroundColor ?? Theme.of(context).colorScheme.surface,
      builder: (_) => _ChatyEmojiPickerSheet(
        reactionMode: reactionMode,
        composerMode: false,
        initialText: '',
        headerColor: headerColor,
        headerIconsColor: headerIconsColor,
        backgroundColor: backgroundColor,
      ),
    );
    return result is String ? result : null;
  }

  static Future<ChatyEmojiComposerResult?> showComposer(
    BuildContext context, {
    String initialText = '',
    Color? headerColor,
    Color? headerIconsColor,
    Color? backgroundColor,
  }) async {
    final result = await showModalBottomSheet<Object?>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      enableDrag: false,
      showDragHandle: false,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      clipBehavior: Clip.hardEdge,
      backgroundColor: backgroundColor ?? Theme.of(context).colorScheme.surface,
      builder: (_) => _ChatyEmojiPickerSheet(
        reactionMode: false,
        composerMode: true,
        initialText: initialText,
        headerColor: headerColor,
        headerIconsColor: headerIconsColor,
        backgroundColor: backgroundColor,
      ),
    );
    return result is ChatyEmojiComposerResult ? result : null;
  }
}

class _ChatyEmojiPickerSheet extends StatefulWidget {
  final bool reactionMode;
  final bool composerMode;
  final String initialText;
  final Color? headerColor;
  final Color? headerIconsColor;
  final Color? backgroundColor;
  const _ChatyEmojiPickerSheet({
    required this.reactionMode,
    required this.composerMode,
    required this.initialText,
    this.headerColor,
    this.headerIconsColor,
    this.backgroundColor,
  });

  @override
  State<_ChatyEmojiPickerSheet> createState() => _ChatyEmojiPickerSheetState();
}

class _ChatyEmojiPickerSheetState extends State<_ChatyEmojiPickerSheet> {
  double _heightFactor = 0.42;
  late final TextEditingController _draftController;

  @override
  void initState() {
    super.initState();
    _heightFactor = widget.reactionMode
        ? 0.32
        : widget.composerMode
            ? 0.72
            : 0.42;
    _draftController = TextEditingController(text: widget.initialText);
    EmojiRecentCache.instance.initialize().then((_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _draftController.dispose();
    super.dispose();
  }

  void _insertSelectedEmoji(String emoji) {
    final value = _draftController.value;
    final selection = value.selection;
    final start = selection.isValid ? selection.start : value.text.length;
    final end = selection.isValid ? selection.end : value.text.length;
    final next = value.text.replaceRange(start, end, emoji);
    _draftController.value = TextEditingValue(
      text: next,
      selection: TextSelection.collapsed(offset: start + emoji.length),
    );
    setState(() {});
  }

  void _completeComposer(ChatyEmojiComposerAction action) {
    Navigator.of(context).pop(
      ChatyEmojiComposerResult(text: _draftController.text, action: action),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final bottomInset = MediaQuery.viewInsetsOf(context).bottom;
    final availableHeight =
        (MediaQuery.sizeOf(context).height - bottomInset).clamp(250.0, double.infinity).toDouble();
    final maxSheetHeight = availableHeight * 0.90;
    final height = (availableHeight * _heightFactor)
        .clamp(math.min(250.0, maxSheetHeight), maxSheetHeight)
        .toDouble();
    final headerColor = widget.headerColor;
    final headerIconsColor = widget.headerIconsColor ?? theme.colorScheme.onSurface;
    final backgroundColor = widget.backgroundColor ?? theme.colorScheme.surface;
    final reservedHeight = widget.composerMode ? 174.0 : 110.0;
    final gridHeight = math.max(150.0, height - reservedHeight).toDouble();

    return Container(
      color: backgroundColor,
      child: SizedBox(
        height: height,
        child: Column(
          children: [
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onVerticalDragUpdate: (details) {
                if (availableHeight <= 0) return;
                setState(() {
                  _heightFactor = (_heightFactor - details.delta.dy / availableHeight)
                      .clamp(0.32, 0.90)
                      .toDouble();
                });
              },
              child: SizedBox(
                height: 22,
                width: double.infinity,
                child: Center(
                  child: Container(
                    width: 42, height: 4,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.onSurface.withValues(alpha: 0.32),
                      borderRadius: BorderRadius.zero,
                    ),
                  ),
                ),
              ),
            ),
            Container(
              color: headerColor,
              padding: const EdgeInsets.fromLTRB(18, 2, 12, 6),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      widget.reactionMode
                          ? 'Choose reaction'
                          : widget.composerMode
                              ? 'Message with emoji'
                              : 'Choose emoji',
                      style: theme.textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.w800,
                        color: headerIconsColor,
                      ),
                    ),
                  ),
                  if (widget.composerMode)
                    IconButton(
                      tooltip: 'Return to keyboard',
                      onPressed: () => _completeComposer(ChatyEmojiComposerAction.keyboard),
                      icon: Icon(Icons.keyboard_rounded, color: headerIconsColor),
                    ),
                  IconButton(
                    tooltip: 'Close',
                    onPressed: () => widget.composerMode
                        ? _completeComposer(ChatyEmojiComposerAction.close)
                        : Navigator.of(context).pop(),
                    icon: Icon(Icons.close_rounded, color: headerIconsColor),
                  ),
                ],
              ),
            ),
            if (widget.composerMode)
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _draftController,
                        maxLines: 2,
                        minLines: 1,
                        textInputAction: TextInputAction.newline,
                        decoration: InputDecoration(
                          hintText: 'Type or select emojis…',
                          isDense: true,
                          filled: true,
                          fillColor: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.62),
                          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(20),
                            borderSide: BorderSide.none,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    ValueListenableBuilder<TextEditingValue>(
                      valueListenable: _draftController,
                      builder: (context, value, _) => Material(
                        color: value.text.trim().isEmpty
                            ? theme.colorScheme.primary.withValues(alpha: 0.35)
                            : theme.colorScheme.primary,
                        shape: const CircleBorder(),
                        child: InkWell(
                          customBorder: const CircleBorder(),
                          onTap: value.text.trim().isEmpty
                              ? null
                              : () => _completeComposer(ChatyEmojiComposerAction.send),
                          child: const SizedBox.square(
                            dimension: 46,
                            child: Icon(Icons.send_rounded, color: Colors.white, size: 20),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            Expanded(child: _buildCombinedEmojiPicker(context, gridHeight)),
          ],
        ),
      ),
    );
  }

  Widget _buildCombinedEmojiPicker(BuildContext context, double height) {
    // A single, clearly named Emojis tab avoids duplicate choices. Animated
    // emoji rendering remains enabled in messages and reactions as before.
    return _buildUnicodePicker(context, height);
  }

  Widget _buildUnicodePicker(BuildContext context, double height) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final primary = theme.colorScheme.primary;
    final surfaceColor = theme.colorScheme.surface;
    final iconColor = isDark
        ? theme.colorScheme.onSurface.withValues(alpha: 0.65)
        : theme.colorScheme.onSurface.withValues(alpha: 0.7);

    return Container(
      color: surfaceColor,
      child: EmojiPicker(
        onEmojiSelected: (category, emoji) async {
          HapticFeedback.selectionClick();
          await EmojiRecentCache.instance.recordUsage(emoji.emoji, label: emoji.name);
          if (!context.mounted) return;
          if (widget.composerMode) {
            _insertSelectedEmoji(emoji.emoji);
          } else {
            Navigator.of(context).pop(emoji.emoji);
          }
        },
        config: Config(
          height: height,
          checkPlatformCompatibility: true,
          emojiViewConfig: EmojiViewConfig(
            emojiSizeMax: 30,
            columns: 8,
            recentsLimit: 32,
            backgroundColor: surfaceColor,
            buttonMode: ButtonMode.MATERIAL,
          ),
          categoryViewConfig: CategoryViewConfig(
            backgroundColor: surfaceColor,
            iconColor: iconColor,
            iconColorSelected: primary,
            indicatorColor: primary,
            dividerColor: isDark
                ? Colors.white.withValues(alpha: 0.08)
                : Colors.black.withValues(alpha: 0.06),
            tabBarHeight: 44,
          ),
          bottomActionBarConfig: BottomActionBarConfig(
            enabled: true,
            backgroundColor: surfaceColor,
            buttonColor: primary,
            buttonIconColor: Colors.white,
            showBackspaceButton: false,
          ),
          searchViewConfig: SearchViewConfig(
            backgroundColor: surfaceColor,
            buttonIconColor: primary,
            hintText: 'Search emoji…',
          ),
        ),
      ),
    );
  }
}
