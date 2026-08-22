import 'package:flutter/material.dart';

import '../../core/emoji/models/parsed_emoji_span.dart';
import '../../core/emoji/widgets/animated_emoji_view.dart';
import '../../domain/models/chat_message.dart';
import '../../ui/core/design_system/design_system.dart';
import 'emoji_picker_modal.dart';

class MessageActionSheet extends StatelessWidget {
  final ChatMessage message;
  final bool isMe;
  final ThemeConfig theme;
  final Function(String emoji) onReact;
  final VoidCallback onReply;
  final VoidCallback onForward;
  final VoidCallback onCreateTask;
  final VoidCallback onPin;
  final VoidCallback onStar;
  final VoidCallback onCopy;
  final VoidCallback onDeleteForMe;
  final VoidCallback? onDeleteForEveryone;
  final VoidCallback onReport;

  /// Real consumer of conversation.iosStylePopupMenu: switches the SAME
  /// actions to a floating iOS-style dark context menu instead of the
  /// bottom sheet.
  final bool useIosStyle;

  const MessageActionSheet({
    super.key,
    required this.message,
    required this.isMe,
    required this.theme,
    required this.onReact,
    required this.onReply,
    required this.onForward,
    required this.onCreateTask,
    required this.onPin,
    required this.onStar,
    required this.onCopy,
    required this.onDeleteForMe,
    this.onDeleteForEveryone,
    required this.onReport,
    this.useIosStyle = false,
  });

  Future<void> _openAllReactions(BuildContext context) async {
    final emoji = await ChatyEmojiPicker.show(context, reactionMode: true);
    if (emoji != null && emoji.isNotEmpty) onReact(emoji);
  }

  @override
  Widget build(BuildContext context) {
    if (useIosStyle) return _IosStyleMenu(this);
    final quickEmojis = <String>['👍', '❤️', '🔥', '🎉', '👀', '🚀'];
    final colors = context.colors;

    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: ChatySpacing.base,
        vertical: ChatySpacing.md,
      ),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: const BorderRadius.vertical(
          top: Radius.circular(ChatyRadius.xl),
        ),
      ),
      child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(
                child: Container(
                  width: 38,
                  height: 4.5,
                  decoration: BoxDecoration(
                    color: colors.foregroundSecondary.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(ChatyRadius.full),
                  ),
                ),
              ),
              const SizedBox(height: ChatySpacing.base),
              Container(
                padding: const EdgeInsets.symmetric(
                  vertical: ChatySpacing.xs,
                  horizontal: ChatySpacing.sm,
                ),
                decoration: BoxDecoration(
                  color: colors.surfaceSecondary,
                  borderRadius: BorderRadius.circular(ChatyRadius.full),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    ...quickEmojis.map((emoji) {
                      return InkWell(
                        onTap: () => onReact(emoji),
                        borderRadius: BorderRadius.circular(ChatyRadius.full),
                        child: SizedBox(
                          width: 42,
                          height: 42,
                          child: Center(
                            child: AnimatedEmojiView(
                              unicode: emoji,
                              size: 28,
                              interactive: true,
                              mode: EmojiDisplayMode.reaction,
                            ),
                          ),
                        ),
                      );
                    }),
                    InkWell(
                      onTap: () => _openAllReactions(context),
                      borderRadius: BorderRadius.circular(ChatyRadius.full),
                      child: Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          color: colors.surfaceElevated,
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          Icons.add_rounded,
                          color: colors.foreground,
                          size: 22,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: ChatySpacing.base),
              ChatyGroupedSection(
                children: [
                  ChatyListTile(
                    leading: Icon(
                      Icons.reply_rounded,
                      color: colors.primary,
                      size: 22,
                    ),
                    title: Text(
                      'Reply',
                      style: TextStyle(
                        color: colors.foreground,
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                    onTap: onReply,
                  ),
                  ChatyListTile(
                    leading: Icon(
                      Icons.shortcut_rounded,
                      color: colors.primary,
                      size: 22,
                    ),
                    title: Text(
                      'Forward',
                      style: TextStyle(
                        color: colors.foreground,
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                    onTap: onForward,
                  ),
                  ChatyListTile(
                    leading: Icon(
                      Icons.task_alt_rounded,
                      color: colors.primary,
                      size: 22,
                    ),
                    title: Text(
                      'Create Task from Message',
                      style: TextStyle(
                        color: colors.primary,
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                    onTap: onCreateTask,
                  ),
                  ChatyListTile(
                    leading: Icon(
                      message.isPinned
                          ? Icons.push_pin_outlined
                          : Icons.push_pin_rounded,
                      color: colors.foregroundSecondary,
                      size: 22,
                    ),
                    title: Text(
                      message.isPinned ? 'Unpin Message' : 'Pin Message',
                      style: TextStyle(
                        color: colors.foreground,
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                    onTap: onPin,
                  ),
                  ChatyListTile(
                    leading: Icon(
                      message.isStarred
                          ? Icons.star_border_rounded
                          : Icons.star_rounded,
                      color: colors.warning,
                      size: 22,
                    ),
                    title: Text(
                      message.isStarred ? 'Unstar Message' : 'Star Message',
                      style: TextStyle(
                        color: colors.foreground,
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                    onTap: onStar,
                  ),
                  ChatyListTile(
                    leading: Icon(
                      Icons.content_copy_rounded,
                      color: colors.foregroundSecondary,
                      size: 20,
                    ),
                    title: Text(
                      'Copy Text',
                      style: TextStyle(
                        color: colors.foreground,
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                    onTap: onCopy,
                  ),
                ],
              ),
              ChatyGroupedSection(
                children: [
                  ChatyListTile(
                    leading: Icon(
                      Icons.delete_outline_rounded,
                      color: colors.error,
                      size: 22,
                    ),
                    title: Text(
                      'Delete for me',
                      style: TextStyle(
                        color: colors.error,
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                    onTap: onDeleteForMe,
                  ),
                  if (isMe && onDeleteForEveryone != null)
                    ChatyListTile(
                      leading: Icon(
                        Icons.delete_forever_rounded,
                        color: colors.error,
                        size: 22,
                      ),
                      title: Text(
                        'Delete for everyone',
                        style: TextStyle(
                          color: colors.error,
                          fontWeight: FontWeight.w600,
                          fontSize: 15,
                        ),
                      ),
                      onTap: onDeleteForEveryone!,
                    ),
                  ChatyListTile(
                    leading: Icon(
                      Icons.flag_outlined,
                      color: colors.foregroundSecondary,
                      size: 22,
                    ),
                    title: Text(
                      'Report Message',
                      style: TextStyle(
                        color: colors.foregroundSecondary,
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                    onTap: onReport,
                  ),
                ],
              ),
              const SizedBox(height: ChatySpacing.sm),
            ],
          ),
        ),
      ),
    );
  }
}

/// Floating iOS-style presentation for [MessageActionSheet]: a compact dark
/// glass card with an emoji strip, an icon action row and grouped list rows.
class _IosStyleMenu extends StatelessWidget {
  final MessageActionSheet sheet;

  const _IosStyleMenu(this.sheet);

  @override
  Widget build(BuildContext context) {
    final s = sheet;
    final colors = context.colors;
    const quickEmojis = <String>['👍', '❤️', '🔥', '🎉', '👀', '🚀'];

    Widget row({
      required IconData icon,
      required String label,
      required VoidCallback onTap,
      bool destructive = false,
      bool chevron = false,
    }) {
      final color = destructive ? colors.error : colors.foreground;
      return InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
          child: Row(
            children: [
              Icon(icon, size: 19, color: color),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  label,
                  style: TextStyle(color: color, fontSize: 14.5),
                ),
              ),
              if (chevron)
                Icon(
                  Icons.chevron_right_rounded,
                  size: 17,
                  color: colors.foregroundTertiary,
                ),
            ],
          ),
        ),
      );
    }

    Widget hairline() => Container(height: 0.6, color: colors.border);

    return Dialog(
      backgroundColor: Colors.transparent,
      child: Container(
        width: 290,
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          color: colors.surface,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: colors.border),
        ),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 10),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  for (final emoji in quickEmojis)
                    InkWell(
                      onTap: () => s.onReact(emoji),
                      borderRadius: BorderRadius.circular(20),
                      child: SizedBox(
                        width: 38,
                        height: 38,
                        child: Center(
                          child: AnimatedEmojiView(
                            unicode: emoji,
                            size: 26,
                            interactive: true,
                            mode: EmojiDisplayMode.reaction,
                          ),
                        ),
                      ),
                    ),
                  InkWell(
                    onTap: () => s._openAllReactions(context),
                    borderRadius: BorderRadius.circular(18),
                    child: Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(
                        color: colors.surfaceSecondary,
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        Icons.add_rounded,
                        color: colors.foreground,
                        size: 18,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              hairline(),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _iconAction(
                    Icons.reply_rounded,
                    'Reply',
                    sheet.onReply,
                    colors,
                  ),
                  _iconAction(
                    Icons.shortcut_rounded,
                    'Forward',
                    sheet.onForward,
                    colors,
                  ),
                  _iconAction(
                    Icons.content_copy_rounded,
                    'Copy',
                    sheet.onCopy,
                    colors,
                  ),
                  _iconAction(
                    sheet.message.isStarred
                        ? Icons.star_rounded
                        : Icons.star_border_rounded,
                    'Star',
                    sheet.onStar,
                    colors,
                  ),
                  _iconAction(
                    sheet.message.isPinned
                        ? Icons.push_pin_rounded
                        : Icons.push_pin_outlined,
                    'Pin',
                    sheet.onPin,
                    colors,
                  ),
                ],
              ),
              hairline(),
              row(
                icon: Icons.task_alt_rounded,
                label: 'Create Task from Message',
                chevron: true,
                onTap: sheet.onCreateTask,
              ),
              hairline(),
              row(
                icon: Icons.delete_outline_rounded,
                label: 'Delete for me',
                destructive: true,
                onTap: sheet.onDeleteForMe,
              ),
              if (sheet.onDeleteForEveryone != null)
                row(
                  icon: Icons.delete_forever_outlined,
                  label: 'Delete for everyone',
                  destructive: true,
                  onTap: sheet.onDeleteForEveryone!,
                ),
              hairline(),
              row(
                icon: Icons.flag_outlined,
                label: 'Report',
                destructive: true,
                onTap: sheet.onReport,
              ),
            ],
          ),
        ),
      ),
    );
  }

  static Widget _iconAction(
    IconData icon,
    String label,
    VoidCallback onTap,
    AppColors colors,
  ) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 9),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 21, color: colors.foreground),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                color: colors.foregroundSecondary,
                fontSize: 9.5,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
