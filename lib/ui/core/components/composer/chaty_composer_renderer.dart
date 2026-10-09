import 'package:flutter/material.dart';
import '../../design_system/tokens/app_tokens.dart';
import '../../theme/app_theme.dart';

enum ComposerStateMode {
  idle,
  typing,
  reply,
  edit,
  recording,
  mediaDraft,
  offline,
}

class ChatyComposerData {
  final TextEditingController textController;
  final FocusNode focusNode;
  final ComposerStateMode mode;
  final bool hasText;
  final bool isRecording;
  final bool isOffline;
  final String? replyTitle;
  final String? replySnippet;
  final VoidCallback? onCancelReply;
  final String? editOriginalText;
  final VoidCallback? onCancelEdit;
  final VoidCallback onSend;
  final VoidCallback onMicPress;
  final VoidCallback? onMicRelease;
  final VoidCallback? onMicCancel;
  final VoidCallback onAttach;
  final VoidCallback onCamera;
  final VoidCallback onEmojiToggle;
  final Widget? mediaDraftTray;
  final List<double> recordingLevels;
  final String? recordingDurationLabel;

  const ChatyComposerData({
    required this.textController,
    required this.focusNode,
    this.mode = ComposerStateMode.idle,
    this.hasText = false,
    this.isRecording = false,
    this.isOffline = false,
    this.replyTitle,
    this.replySnippet,
    this.onCancelReply,
    this.editOriginalText,
    this.onCancelEdit,
    required this.onSend,
    required this.onMicPress,
    this.onMicRelease,
    this.onMicCancel,
    required this.onAttach,
    required this.onCamera,
    required this.onEmojiToggle,
    this.mediaDraftTray,
    this.recordingLevels = const [],
    this.recordingDurationLabel,
  });
}

class ChatyComposerRenderer extends StatelessWidget {
  final String variantId;
  final ChatyComposerData data;
  final Color? backgroundColor;

  const ChatyComposerRenderer({
    super.key,
    required this.variantId,
    required this.data,
    this.backgroundColor,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;

    return Container(
      decoration: BoxDecoration(
        color: bg,
        border: Border(top: BorderSide(color: colors.borderSubtle, width: 0.8)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Media Draft Tray if active
            if (data.mediaDraftTray != null) data.mediaDraftTray!,

            // Reply or Edit strip
            if (data.mode == ComposerStateMode.reply && data.replyTitle != null)
              _buildReplyStrip(context),
            if (data.mode == ComposerStateMode.edit) _buildEditStrip(context),

            // Main Input Row based on variant
            Padding(
              padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
              child: _buildInputRow(context),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildReplyStrip(BuildContext context) {
    final colors = context.colors;
    return Container(
      margin: const EdgeInsets.fromLTRB(10, 6, 10, 0),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: colors.surfaceSecondary,
        borderRadius: BorderRadius.circular(10),
        border: Border(left: BorderSide(color: colors.primary, width: 3.5)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  data.replyTitle!,
                  style: TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.w700,
                    color: colors.primary,
                  ),
                ),
                if (data.replySnippet != null)
                  Text(
                    data.replySnippet!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 11.5,
                      color: colors.foregroundSecondary,
                    ),
                  ),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.close_rounded, size: 18),
            onPressed: data.onCancelReply,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
          ),
        ],
      ),
    );
  }

  Widget _buildEditStrip(BuildContext context) {
    final colors = context.colors;
    return Container(
      margin: const EdgeInsets.fromLTRB(10, 6, 10, 0),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: colors.surfaceSecondary,
        borderRadius: BorderRadius.circular(10),
        border: Border(left: BorderSide(color: colors.warning, width: 3.5)),
      ),
      child: Row(
        children: [
          const Icon(Icons.edit_rounded, size: 16),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Editing message',
              style: TextStyle(
                fontSize: 12.5,
                fontWeight: FontWeight.w600,
                color: colors.foreground,
              ),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.close_rounded, size: 18),
            onPressed: data.onCancelEdit,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
          ),
        ],
      ),
    );
  }

  Widget _buildInputRow(BuildContext context) {
    if (data.isRecording) {
      return _buildRecordingRow(context);
    }

    switch (variantId) {
      case 'telegram_dense':
        return _buildTelegramDenseRow(context);
      case 'signal_quiet':
        return _buildSignalQuietRow(context);
      case 'imessage_expressive':
        return _buildIMessageRow(context);
      case 'messenger_action_row':
        return _buildMessengerRow(context);
      case 'instagram_media_first':
        return _buildInstagramRow(context);
      case 'command_composer':
        return _buildCommandRow(context);
      case 'lens_first':
      case 'chaty_capsule':
      default:
        return _buildCapsuleRow(context);
    }
  }

  // ---------------------------------------------------------------------------
  // RECORDING STATE ROW
  // ---------------------------------------------------------------------------
  Widget _buildRecordingRow(BuildContext context) {
    final colors = context.colors;
    return Row(
      children: [
        IconButton(
          icon: Icon(Icons.delete_outline_rounded, color: colors.error),
          onPressed: data.onMicCancel,
        ),
        const SizedBox(width: 8),
        Text(
          data.recordingDurationLabel ?? '0:00',
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w700,
            color: colors.foreground,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Row(
            children: data.recordingLevels.map((lvl) {
              return Expanded(
                child: Container(
                  height: 4 + 20 * lvl.clamp(0.0, 1.0),
                  margin: const EdgeInsets.symmetric(horizontal: 1),
                  decoration: BoxDecoration(
                    color: colors.primary,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              );
            }).toList(),
          ),
        ),
        const SizedBox(width: 8),
        FloatingActionButton.small(
          backgroundColor: colors.primary,
          foregroundColor: Colors.white,
          elevation: 0,
          onPressed: data.onSend,
          child: const Icon(Icons.send_rounded, size: 18),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // CMP-01: Chaty Capsule (Default)
  // ---------------------------------------------------------------------------
  Widget _buildCapsuleRow(BuildContext context) {
    final colors = context.colors;
    final showSend = data.hasText || data.mode == ComposerStateMode.reply || data.mode == ComposerStateMode.edit;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Expanded(
          child: Container(
            decoration: BoxDecoration(
              color: colors.inputFill,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: colors.border, width: 0.8),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                IconButton(
                  icon: const Icon(Icons.emoji_emotions_outlined, size: 22),
                  color: colors.foregroundSecondary,
                  onPressed: data.onEmojiToggle,
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: TextField(
                      controller: data.textController,
                      focusNode: data.focusNode,
                      maxLines: 5,
                      minLines: 1,
                      style: TextStyle(fontSize: 15, color: colors.foreground),
                      decoration: InputDecoration(
                        hintText: data.isOffline ? 'Message (Offline - will queue)' : 'Message...',
                        hintStyle: TextStyle(
                          color: colors.foregroundSecondary,
                          fontSize: 15,
                        ),
                        border: InputBorder.none,
                        isDense: true,
                        contentPadding: const EdgeInsets.symmetric(vertical: 8),
                      ),
                    ),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.attach_file_rounded, size: 22),
                  color: colors.foregroundSecondary,
                  onPressed: data.onAttach,
                ),
                if (!showSend)
                  IconButton(
                    icon: const Icon(Icons.camera_alt_outlined, size: 22),
                    color: colors.foregroundSecondary,
                    onPressed: data.onCamera,
                  ),
              ],
            ),
          ),
        ),
        const SizedBox(width: 6),
        _ActionBubble(
          isSend: showSend,
          onSend: data.onSend,
          onMicPress: data.onMicPress,
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // CMP-02: Telegram Dense
  // ---------------------------------------------------------------------------
  Widget _buildTelegramDenseRow(BuildContext context) {
    final colors = context.colors;
    final showSend = data.hasText;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        IconButton(
          icon: const Icon(Icons.attach_file_rounded, size: 22),
          color: colors.foregroundSecondary,
          onPressed: data.onAttach,
        ),
        Expanded(
          child: Container(
            decoration: BoxDecoration(
              color: colors.inputFill,
              borderRadius: BorderRadius.circular(18),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                    child: TextField(
                      controller: data.textController,
                      focusNode: data.focusNode,
                      maxLines: 5,
                      minLines: 1,
                      style: TextStyle(fontSize: 15, color: colors.foreground),
                      decoration: InputDecoration(
                        hintText: 'Message',
                        hintStyle: TextStyle(color: colors.foregroundSecondary),
                        border: InputBorder.none,
                        isDense: true,
                        contentPadding: const EdgeInsets.symmetric(vertical: 8),
                      ),
                    ),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.emoji_emotions_outlined, size: 20),
                  color: colors.foregroundSecondary,
                  onPressed: data.onEmojiToggle,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(width: 4),
        _ActionBubble(
          isSend: showSend,
          onSend: data.onSend,
          onMicPress: data.onMicPress,
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // CMP-05: Signal Quiet
  // ---------------------------------------------------------------------------
  Widget _buildSignalQuietRow(BuildContext context) {
    final colors = context.colors;
    final showSend = data.hasText;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        IconButton(
          icon: const Icon(Icons.add_circle_outline_rounded, size: 24),
          color: colors.foregroundSecondary,
          onPressed: data.onAttach,
        ),
        Expanded(
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
            decoration: BoxDecoration(
              color: colors.surfaceSecondary,
              borderRadius: BorderRadius.circular(22),
            ),
            child: TextField(
              controller: data.textController,
              focusNode: data.focusNode,
              maxLines: 4,
              minLines: 1,
              style: TextStyle(fontSize: 15, color: colors.foreground),
              decoration: InputDecoration(
                hintText: 'Signal message...',
                hintStyle: TextStyle(color: colors.foregroundSecondary),
                border: InputBorder.none,
                isDense: true,
                contentPadding: const EdgeInsets.symmetric(vertical: 8),
              ),
            ),
          ),
        ),
        const SizedBox(width: 6),
        _ActionBubble(
          isSend: showSend,
          onSend: data.onSend,
          onMicPress: data.onMicPress,
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // CMP-03: iMessage Expressive
  // ---------------------------------------------------------------------------
  Widget _buildIMessageRow(BuildContext context) {
    final colors = context.colors;
    final showSend = data.hasText;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        IconButton(
          icon: const Icon(Icons.add_rounded, size: 26),
          color: colors.primary,
          onPressed: data.onAttach,
        ),
        Expanded(
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            decoration: BoxDecoration(
              color: colors.inputFill,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: colors.border, width: 0.8),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Expanded(
                  child: TextField(
                    controller: data.textController,
                    focusNode: data.focusNode,
                    maxLines: 5,
                    minLines: 1,
                    style: TextStyle(fontSize: 15, color: colors.foreground),
                    decoration: InputDecoration(
                      hintText: 'iMessage',
                      hintStyle: TextStyle(color: colors.foregroundSecondary),
                      border: InputBorder.none,
                      isDense: true,
                      contentPadding: const EdgeInsets.symmetric(vertical: 8),
                    ),
                  ),
                ),
                if (!showSend)
                  IconButton(
                    icon: const Icon(Icons.mic_none_rounded, size: 22),
                    color: colors.primary,
                    onPressed: data.onMicPress,
                  ),
                if (showSend)
                  IconButton(
                    icon: Icon(Icons.arrow_upward_rounded, size: 22, color: colors.primary),
                    onPressed: data.onSend,
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // CMP-04: Messenger Action Row
  // ---------------------------------------------------------------------------
  Widget _buildMessengerRow(BuildContext context) {
    return _buildCapsuleRow(context);
  }

  // ---------------------------------------------------------------------------
  // CMP-06: Instagram Media First
  // ---------------------------------------------------------------------------
  Widget _buildInstagramRow(BuildContext context) {
    final colors = context.colors;
    final showSend = data.hasText;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        IconButton(
          icon: const Icon(Icons.camera_alt_rounded, size: 22),
          color: colors.primary,
          onPressed: data.onCamera,
        ),
        Expanded(
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            decoration: BoxDecoration(
              color: colors.surfaceSecondary,
              borderRadius: BorderRadius.circular(22),
            ),
            child: TextField(
              controller: data.textController,
              focusNode: data.focusNode,
              maxLines: 4,
              minLines: 1,
              style: TextStyle(fontSize: 15, color: colors.foreground),
              decoration: InputDecoration(
                hintText: 'Message...',
                hintStyle: TextStyle(color: colors.foregroundSecondary),
                border: InputBorder.none,
                isDense: true,
                contentPadding: const EdgeInsets.symmetric(vertical: 8),
              ),
            ),
          ),
        ),
        const SizedBox(width: 4),
        if (!showSend) ...[
          IconButton(
            icon: const Icon(Icons.mic_none_rounded, size: 22),
            color: colors.foreground,
            onPressed: data.onMicPress,
          ),
          IconButton(
            icon: const Icon(Icons.image_outlined, size: 22),
            color: colors.foreground,
            onPressed: data.onAttach,
          ),
        ] else ...[
          TextButton(
            onPressed: data.onSend,
            child: Text(
              'Send',
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: colors.primary,
              ),
            ),
          ),
        ],
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // CMP-08: Command Composer
  // ---------------------------------------------------------------------------
  Widget _buildCommandRow(BuildContext context) {
    return _buildCapsuleRow(context);
  }
}

class _ActionBubble extends StatelessWidget {
  final bool isSend;
  final VoidCallback onSend;
  final VoidCallback onMicPress;

  const _ActionBubble({
    required this.isSend,
    required this.onSend,
    required this.onMicPress,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return SizedBox(
      width: 44,
      height: 44,
      child: Material(
        color: colors.primary,
        shape: const CircleBorder(),
        elevation: 0,
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: () {
            ChatyMotion.selection();
            if (isSend) {
              onSend();
            } else {
              onMicPress();
            }
          },
          child: Center(
            child: AnimatedSwitcher(
              duration: ChatyMotion.fast,
              child: isSend
                  ? const Icon(
                      Icons.send_rounded,
                      key: ValueKey('send'),
                      color: Colors.white,
                      size: 20,
                    )
                  : const Icon(
                      Icons.mic_rounded,
                      key: ValueKey('mic'),
                      color: Colors.white,
                      size: 22,
                    ),
            ),
          ),
        ),
      ),
    );
  }
}
