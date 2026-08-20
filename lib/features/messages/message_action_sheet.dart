import 'package:animated_emoji/animated_emoji.dart';
import 'package:flutter/material.dart';

import '../../domain/models/chat_message.dart';
import '../../ui/core/theme/theme_config.dart';
import 'chaty_emoji_picker.dart';

class MessageActionSheet extends StatelessWidget {
  final ChatMessage message;
  final bool isMe;
  final ThemeConfig theme;
  final Function(String emoji) onReact;
  final VoidCallback onReply;
  final VoidCallback onCreateTask;
  final VoidCallback onPin;
  final VoidCallback onStar;
  final VoidCallback onCopy;
  final VoidCallback onDeleteForMe;
  final VoidCallback? onDeleteForEveryone;
  final VoidCallback onReport;

  const MessageActionSheet({
    super.key,
    required this.message,
    required this.isMe,
    required this.theme,
    required this.onReact,
    required this.onReply,
    required this.onCreateTask,
    required this.onPin,
    required this.onStar,
    required this.onCopy,
    required this.onDeleteForMe,
    this.onDeleteForEveryone,
    required this.onReport,
  });

  Future<void> _openAllReactions(BuildContext context) async {
    final emoji = await ChatyEmojiPicker.show(context, reactionMode: true);
    if (emoji != null && emoji.isNotEmpty) onReact(emoji);
  }

  @override
  Widget build(BuildContext context) {
    final quickEmojis = <String>['👍', '❤️', '🔥', '🎉', '👀', '🚀'];

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      decoration: BoxDecoration(
        color: theme.surfaceColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
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
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(
                    color: theme.secondaryTextColor.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Container(
                padding: const EdgeInsets.symmetric(vertical: 7, horizontal: 8),
                decoration: BoxDecoration(
                  color: theme.cardColor,
                  borderRadius: BorderRadius.circular(30),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    ...quickEmojis.map((emoji) {
                      final animated = chatyAnimatedEmojiForUnicode(emoji);
                      return InkWell(
                        onTap: () => onReact(emoji),
                        borderRadius: BorderRadius.circular(24),
                        child: SizedBox(
                          width: 40,
                          height: 40,
                          child: Center(
                            child: animated == null
                                ? Text(emoji, style: const TextStyle(fontSize: 23))
                                : AnimatedEmoji(animated, size: 31),
                          ),
                        ),
                      );
                    }),
                    InkWell(
                      onTap: () => _openAllReactions(context),
                      borderRadius: BorderRadius.circular(24),
                      child: Container(
                        width: 40,
                        height: 40,
                        decoration: BoxDecoration(
                          color: theme.surfaceColor,
                          shape: BoxShape.circle,
                        ),
                        child: Icon(Icons.add_rounded, color: theme.primaryTextColor, size: 24),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),
              _buildActionTile(icon: Icons.reply_rounded, label: 'Reply', theme: theme, onTap: onReply),
              _buildActionTile(
                icon: Icons.task_alt_rounded,
                label: 'Create Task from Message',
                color: theme.accentColor,
                theme: theme,
                onTap: onCreateTask,
              ),
              _buildActionTile(
                icon: message.isPinned ? Icons.push_pin_outlined : Icons.push_pin_rounded,
                label: message.isPinned ? 'Unpin Message' : 'Pin Message',
                theme: theme,
                onTap: onPin,
              ),
              _buildActionTile(
                icon: message.isStarred ? Icons.star_border_rounded : Icons.star_rounded,
                label: message.isStarred ? 'Unstar' : 'Star Message',
                theme: theme,
                onTap: onStar,
              ),
              _buildActionTile(icon: Icons.content_copy_rounded, label: 'Copy Text', theme: theme, onTap: onCopy),
              _buildActionTile(
                icon: Icons.delete_outline_rounded,
                label: 'Delete for me',
                color: theme.dangerColor,
                theme: theme,
                onTap: onDeleteForMe,
              ),
              if (isMe && onDeleteForEveryone != null)
                _buildActionTile(
                  icon: Icons.delete_forever_rounded,
                  label: 'Delete for everyone',
                  color: theme.dangerColor,
                  theme: theme,
                  onTap: onDeleteForEveryone!,
                ),
              _buildActionTile(
                icon: Icons.report_problem_outlined,
                label: 'Report Message',
                color: theme.secondaryTextColor,
                theme: theme,
                onTap: onReport,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildActionTile({
    required IconData icon,
    required String label,
    Color? color,
    required ThemeConfig theme,
    required VoidCallback onTap,
  }) {
    return ListTile(
      leading: Icon(icon, color: color ?? theme.primaryTextColor, size: 20),
      title: Text(
        label,
        style: TextStyle(
          color: color ?? theme.primaryTextColor,
          fontSize: 14 * theme.fontScale,
          fontWeight: FontWeight.w500,
        ),
      ),
      dense: true,
      visualDensity: VisualDensity.compact,
      contentPadding: const EdgeInsets.symmetric(horizontal: 8),
      onTap: onTap,
    );
  }
}
