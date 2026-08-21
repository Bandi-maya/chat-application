import 'dart:async';

import 'package:animated_emoji/animated_emoji.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:just_audio/just_audio.dart';
import 'package:latlong2/latlong.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:video_player/video_player.dart';

import '../../data/services/chat_media_service.dart';
import '../../domain/models/chat_message.dart';
import '../../ui/core/theme/theme_config.dart';
import '../../ui/core/widgets/app_avatar.dart';
import 'emoji_picker_modal.dart';

class MessageBubble extends StatelessWidget {
  final ChatMessage message;
  final bool isMe;
  final ThemeConfig theme;
  final String? senderName;
  final VoidCallback onLongPress;
  final VoidCallback? onTaskTap;
  final Function(String emoji)? onReactionTap;
  final VoidCallback? onMediaTap;
  final VoidCallback? onDoubleTap;
  final double voicePlaybackSpeed;
  final bool showDeletedContent;
  /// View-once support: whether the local user already opened this media and
  /// whether the sender's Anti-View-Once preference retains it after opening.
  final bool retainViewOnce;
  final bool viewOnceOpened;
  final VoidCallback? onViewOnceOpen;

  const MessageBubble({
    super.key,
    required this.message,
    required this.isMe,
    required this.theme,
    this.senderName,
    required this.onLongPress,
    this.onTaskTap,
    this.onReactionTap,
    this.onMediaTap,
    this.onDoubleTap,
    this.voicePlaybackSpeed = 1.0,
    this.showDeletedContent = false,
    this.retainViewOnce = false,
    this.viewOnceOpened = false,
    this.onViewOnceOpen,
  });

  BorderRadius _bubbleRadius() {
    final r = Radius.circular(theme.bubbleRadius);
    switch (theme.bubbleStyle) {
      case AppBubbleStyle.rounded:
        return BorderRadius.only(
          topLeft: r,
          topRight: r,
          bottomLeft: isMe ? r : const Radius.circular(3),
          bottomRight: isMe ? const Radius.circular(3) : r,
        );
      case AppBubbleStyle.softSquare:
        return BorderRadius.circular(7);
      case AppBubbleStyle.pill:
        return BorderRadius.circular(24);
      case AppBubbleStyle.sharpTail:
        return BorderRadius.only(
          topLeft: r,
          topRight: r,
          bottomLeft: isMe ? r : Radius.zero,
          bottomRight: isMe ? Radius.zero : r,
        );
    }
  }

  // Read-state tint for the delivery ticks, driven by the Conversation
  // "Delivery Tick Style" setting (surfaced via theme.tickStyle).
  Color _tickReadColor() {
    switch (theme.tickStyle) {
      case 'Neon':
        return const Color(0xFF39FF14);
      case 'iOS Style':
        return const Color(0xFF34C759);
      case 'Minimal':
        return Colors.white;
      case 'Double Check':
      case 'Default':
      default:
        return const Color(0xFF38BDF8);
    }
  }

  Widget _deliveryIcon() {
    final style = theme.tickStyle;
    // Minimal uses a single check for every positive state and no colored read
    // accent; Double Check promotes even the "sent" state to a double tick.
    final minimal = style == 'Minimal';
    final alwaysDouble = style == 'Double Check';
    switch (message.deliveryState) {
      case DeliveryState.queued:
      case DeliveryState.sending:
        return const Icon(
          Icons.access_time_rounded,
          size: 12,
          color: Colors.white70,
        );
      case DeliveryState.sent:
        return Icon(
          minimal || !alwaysDouble
              ? Icons.done_rounded
              : Icons.done_all_rounded,
          size: 13,
          color: Colors.white70,
        );
      case DeliveryState.delivered:
        return Icon(
          minimal ? Icons.done_rounded : Icons.done_all_rounded,
          size: 14,
          color: Colors.white70,
        );
      case DeliveryState.read:
        return Icon(
          minimal ? Icons.done_rounded : Icons.done_all_rounded,
          size: 14,
          color: minimal ? Colors.white : _tickReadColor(),
        );
      case DeliveryState.failed:
        return const Icon(
          Icons.error_outline_rounded,
          size: 12,
          color: Colors.redAccent,
        );
    }
  }

  String _formatTime(DateTime dt) {
    final local = dt.toLocal();
    final hour = local.hour.toString().padLeft(2, '0');
    final min = local.minute.toString().padLeft(2, '0');
    return '$hour:$min';
  }

  @override
  Widget build(BuildContext context) {
    if (message.type == MessageType.system) {
      return Center(
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: 8, horizontal: 24),
          padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 12),
          decoration: BoxDecoration(
            color: theme.surfaceColor.withValues(alpha: 0.8),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: theme.cardColor),
          ),
          child: Text(
            message.text,
            textAlign: TextAlign.center,
            style: TextStyle(
              color: theme.secondaryTextColor,
              fontSize: 11.5 * theme.fontScale,
            ),
          ),
        ),
      );
    }

    if (message.isDeletedForEveryone && !showDeletedContent) {
      return Align(
        alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
        child: GestureDetector(
          onLongPress: onLongPress,
          child: Container(
            margin: const EdgeInsets.symmetric(vertical: 3, horizontal: 12),
            padding: const EdgeInsets.symmetric(vertical: 9, horizontal: 13),
            decoration: BoxDecoration(
              color: theme.surfaceColor.withValues(alpha: 0.55),
              borderRadius: BorderRadius.circular(theme.cornerRadius),
              border: Border.all(color: theme.cardColor),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.cancel_outlined,
                  size: 17,
                  color: theme.secondaryTextColor,
                ),
                const SizedBox(width: 7),
                Text(
                  'This message was deleted',
                  style: TextStyle(
                    color: theme.secondaryTextColor,
                    fontSize: 13 * theme.fontScale,
                    fontStyle: FontStyle.italic,
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final bubbleBg = isMe
        ? theme.outgoingBubbleColor
        : theme.incomingBubbleColor;
    final textColor = isMe ? theme.outgoingTextColor : theme.incomingTextColor;

    return Padding(
      padding: EdgeInsets.symmetric(
        vertical: 3 * theme.density,
        horizontal: 12,
      ),
      child: Row(
        mainAxisAlignment: isMe
            ? MainAxisAlignment.end
            : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          if (!isMe && senderName != null)
            Padding(
              padding: const EdgeInsets.only(right: 8, bottom: 2),
              child: AppAvatar(
                initials: senderName!
                    .split(' ')
                    .map((e) => e.isEmpty ? '' : e[0])
                    .take(2)
                    .join(),
                colorHex: '0xFF6366F1',
                size: 28,
              ),
            ),
          Flexible(
            child: GestureDetector(
              onLongPress: onLongPress,
              onDoubleTap: onDoubleTap,
              child: Column(
                crossAxisAlignment: isMe
                    ? CrossAxisAlignment.end
                    : CrossAxisAlignment.start,
                children: [
                  Container(
                    constraints: BoxConstraints(
                      maxWidth:
                          MediaQuery.sizeOf(context).width *
                          theme.bubbleMaxWidthFactor,
                    ),
                    padding: EdgeInsets.symmetric(
                      horizontal: 9,
                      vertical: 8 * theme.density,
                    ),
                    decoration: BoxDecoration(
                      color: bubbleBg,
                      borderRadius: _bubbleRadius(),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.06),
                          blurRadius: 3,
                          offset: const Offset(0, 1),
                        ),
                      ],
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (!isMe && senderName != null) ...[
                          Text(
                            senderName!,
                            style: TextStyle(
                              color: theme.accentColor,
                              fontSize: 11.5 * theme.fontScale,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(height: 3),
                        ],
                        if (message.isDeletedForEveryone && showDeletedContent)
                          Container(
                            margin: const EdgeInsets.only(bottom: 7),
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 4,
                            ),
                            decoration: BoxDecoration(
                              color: Colors.black.withValues(alpha: 0.12),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.cancel_outlined,
                                  size: 14,
                                  color: textColor.withValues(alpha: 0.8),
                                ),
                                const SizedBox(width: 5),
                                Text(
                                  'Deleted message',
                                  style: TextStyle(
                                    color: textColor.withValues(alpha: 0.8),
                                    fontSize: 10.5,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        if (message.replyToMessageId != null)
                          Container(
                            margin: const EdgeInsets.only(bottom: 6),
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 5,
                            ),
                            decoration: BoxDecoration(
                              color: Colors.black.withValues(alpha: 0.13),
                              borderRadius: BorderRadius.circular(8),
                              border: Border(
                                left: BorderSide(
                                  color: isMe
                                      ? Colors.white70
                                      : theme.accentColor,
                                  width: 3,
                                ),
                              ),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  message.replyToSenderName ?? 'Reply',
                                  style: TextStyle(
                                    color: isMe
                                        ? Colors.white
                                        : theme.accentColor,
                                    fontSize: 11,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                                Text(
                                  message.replyToPreviewText ?? '',
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    color: textColor.withValues(alpha: 0.8),
                                    fontSize: 11,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        if (message.type == MessageType.taskCard)
                          InkWell(
                            onTap: onTaskTap,
                            borderRadius: BorderRadius.circular(10),
                            child: Container(
                              margin: const EdgeInsets.symmetric(vertical: 4),
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: Colors.black.withValues(alpha: 0.16),
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(
                                  color: theme.accentColor.withValues(
                                    alpha: 0.35,
                                  ),
                                ),
                              ),
                              child: Row(
                                children: [
                                  Icon(
                                    Icons.task_alt_rounded,
                                    color: theme.accentColor,
                                    size: 22,
                                  ),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          message.text,
                                          style: TextStyle(
                                            color: textColor,
                                            fontWeight: FontWeight.bold,
                                            fontSize: 13 * theme.fontScale,
                                          ),
                                        ),
                                        Text(
                                          'Task • tap to open',
                                          style: TextStyle(
                                            color: textColor.withValues(
                                              alpha: 0.7,
                                            ),
                                            fontSize: 10.5,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        // Real consumer of the Forwarded-tag setting: the
                        // flag is baked into message metadata at send time
                        // (yoDisableFwd suppresses it there).
                        if (message.metadata['forwarded'] == true)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 4),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.shortcut_rounded,
                                  size: 11,
                                  color: textColor.withValues(alpha: 0.6),
                                ),
                                const SizedBox(width: 3),
                                Text(
                                  'Forwarded',
                                  style: TextStyle(
                                    color: textColor.withValues(alpha: 0.6),
                                    fontSize: 10,
                                    fontStyle: FontStyle.italic,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        // Real consumer of Anti View-Once: recipients see a
                        // locked card until they open it once; afterwards the
                        // media is retained (pref on) or expired (pref off).
                        if (message.metadata['view_once'] == true &&
                            !isMe &&
                            !viewOnceOpened &&
                            (message.attachment?.type == 'image' ||
                                message.attachment?.type == 'video'))
                          _ViewOnceLockedCard(
                            isVideo: message.attachment?.type == 'video',
                            textColor: textColor,
                            accentColor: theme.accentColor,
                            surfaceColor: theme.surfaceColor,
                            onTap: onViewOnceOpen,
                          )
                        else if (message.metadata['view_once'] == true &&
                            !isMe &&
                            viewOnceOpened &&
                            !retainViewOnce &&
                            (message.attachment?.type == 'image' ||
                                message.attachment?.type == 'video'))
                          _ViewOnceExpiredCard(textColor: textColor)
                        else ...[
                          if (message.attachment?.type == 'image')
                            _SignedImagePreview(
                              storagePath: message.attachment!.url,
                              semanticLabel: message.attachment!.name,
                              onTap: onMediaTap,
                            ),
                          if (message.attachment?.type == 'video')
                            _SignedVideoPreview(
                              attachment: message.attachment!,
                              onOpen: onMediaTap,
                            ),
                        ],
                        if (message.attachment?.type == 'audio')
                          _VoiceNotePlayer(
                            attachment: message.attachment!,
                            textColor: textColor,
                            accentColor: theme.accentColor,
                            playbackSpeed: voicePlaybackSpeed,
                          ),
                        if (message.attachment?.type == 'document')
                          _DocumentPreview(
                            attachment: message.attachment!,
                            textColor: textColor,
                            accentColor: theme.accentColor,
                            onTap: onMediaTap,
                          ),
                        if (message.type == MessageType.location)
                          _LocationMapCard(
                            message: message,
                            textColor: textColor,
                            accentColor: theme.accentColor,
                          ),
                        if (message.type == MessageType.contact)
                          _ContactCard(
                            message: message,
                            textColor: textColor,
                            accentColor: theme.accentColor,
                          ),
                        if (message.type != MessageType.taskCard &&
                            message.type != MessageType.location &&
                            message.type != MessageType.contact &&
                            message.text.isNotEmpty)
                          Text(
                            message.text,
                            style: TextStyle(
                              color: textColor,
                              fontSize: 14 * theme.fontScale,
                              height: 1.35,
                            ),
                          ),
                        const SizedBox(height: 4),
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          mainAxisAlignment: MainAxisAlignment.end,
                          children: [
                            if (message.editedAt != null) ...[
                              Text(
                                'edited',
                                style: TextStyle(
                                  color: textColor.withValues(alpha: 0.58),
                                  fontSize: 9.5,
                                ),
                              ),
                              const SizedBox(width: 4),
                            ],
                            if (message.isPinned) ...[
                              Icon(
                                Icons.push_pin_rounded,
                                size: 11,
                                color: textColor.withValues(alpha: 0.7),
                              ),
                              const SizedBox(width: 3),
                            ],
                            if (message.isStarred) ...[
                              const Icon(
                                Icons.star_rounded,
                                size: 11,
                                color: Colors.amber,
                              ),
                              const SizedBox(width: 3),
                            ],
                            Text(
                              _formatTime(message.createdAt),
                              style: TextStyle(
                                color: textColor.withValues(alpha: 0.65),
                                fontSize: 10.5 * theme.fontScale,
                              ),
                            ),
                            if (isMe) ...[
                              const SizedBox(width: 4),
                              _deliveryIcon(),
                            ],
                          ],
                        ),
                      ],
                    ),
                  ),
                  if (message.reactions.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 2, left: 4, right: 4),
                      child: Wrap(
                        spacing: 4,
                        runSpacing: 4,
                        children: message.reactions
                            .map((reaction) {
                              final animated = chatyAnimatedEmojiForUnicode(
                                reaction.emoji,
                              );
                              return InkWell(
                                onTap: () =>
                                    onReactionTap?.call(reaction.emoji),
                                borderRadius: BorderRadius.circular(14),
                                child: Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 7,
                                    vertical: 3,
                                  ),
                                  decoration: BoxDecoration(
                                    color: theme.cardColor,
                                    borderRadius: BorderRadius.circular(14),
                                    border: Border.all(
                                      color: theme.accentColor.withValues(
                                        alpha: 0.25,
                                      ),
                                    ),
                                  ),
                                  child: Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      if (animated != null)
                                        AnimatedEmoji(animated, size: 20)
                                      else
                                        Text(
                                          reaction.emoji,
                                          style: const TextStyle(fontSize: 15),
                                        ),
                                      const SizedBox(width: 3),
                                      Text(
                                        '${reaction.userIds.length}',
                                        style: TextStyle(
                                          color: theme.primaryTextColor,
                                          fontSize: 10.5,
                                          fontWeight: FontWeight.w700,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              );
                            })
                            .toList(growable: false),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SignedImagePreview extends StatefulWidget {
  final String? storagePath;
  final String semanticLabel;
  final VoidCallback? onTap;
  const _SignedImagePreview({
    required this.storagePath,
    required this.semanticLabel,
    this.onTap,
  });

  @override
  State<_SignedImagePreview> createState() => _SignedImagePreviewState();
}

class _SignedImagePreviewState extends State<_SignedImagePreview> {
  late final Future<String>? _url;

  @override
  void initState() {
    super.initState();
    final path = widget.storagePath;
    _url = path == null || path.isEmpty
        ? null
        : ChatMediaService().createSignedUrl(path, expiresInSeconds: 3600);
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      image: true,
      label: widget.semanticLabel,
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(12),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: SizedBox(
            width: 270,
            height: 200,
            child: _url == null
                ? const ColoredBox(
                    color: Colors.black26,
                    child: Center(child: Icon(Icons.broken_image_outlined)),
                  )
                : FutureBuilder<String>(
                    future: _url,
                    builder: (context, snapshot) {
                      if (snapshot.connectionState != ConnectionState.done)
                        return const ColoredBox(
                          color: Colors.black26,
                          child: Center(
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        );
                      final url = snapshot.data;
                      if (url == null || url.isEmpty)
                        return const ColoredBox(
                          color: Colors.black26,
                          child: Center(
                            child: Icon(Icons.broken_image_outlined),
                          ),
                        );
                      return Image.network(
                        url,
                        fit: BoxFit.cover,
                        gaplessPlayback: true,
                        errorBuilder: (_, __, ___) => const ColoredBox(
                          color: Colors.black26,
                          child: Center(
                            child: Icon(Icons.broken_image_outlined),
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ),
      ),
    );
  }
}

class _SignedVideoPreview extends StatefulWidget {
  final MessageAttachment attachment;
  final VoidCallback? onOpen;
  const _SignedVideoPreview({required this.attachment, this.onOpen});

  @override
  State<_SignedVideoPreview> createState() => _SignedVideoPreviewState();
}

class _SignedVideoPreviewState extends State<_SignedVideoPreview> {
  VideoPlayerController? _controller;
  bool _loading = true;
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  Future<void> _initialize() async {
    try {
      final path = widget.attachment.url;
      if (path == null || path.isEmpty) throw Exception('Missing video path');
      final signed = await ChatMediaService().createSignedUrl(
        path,
        expiresInSeconds: 3600,
      );
      final controller = VideoPlayerController.networkUrl(Uri.parse(signed));
      await controller.initialize();
      controller.setLooping(true);
      controller.setVolume(0);
      if (!mounted) {
        await controller.dispose();
        return;
      }
      setState(() {
        _controller = controller;
        _loading = false;
      });
    } catch (_) {
      if (mounted)
        setState(() {
          _loading = false;
          _failed = true;
        });
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  Future<void> _toggle() async {
    final controller = _controller;
    if (controller == null) return;
    if (controller.value.isPlaying) {
      await controller.pause();
    } else {
      await controller.play();
    }
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    return Container(
      width: 270,
      height: 190,
      margin: const EdgeInsets.only(bottom: 5),
      decoration: BoxDecoration(
        color: Colors.black87,
        borderRadius: BorderRadius.circular(12),
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (_loading)
            const Center(
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: Colors.white,
              ),
            )
          else if (_failed || controller == null)
            const Center(
              child: Icon(
                Icons.videocam_off_outlined,
                color: Colors.white70,
                size: 42,
              ),
            )
          else
            FittedBox(
              fit: BoxFit.cover,
              clipBehavior: Clip.hardEdge,
              child: SizedBox(
                width: controller.value.size.width,
                height: controller.value.size.height,
                child: VideoPlayer(controller),
              ),
            ),
          Center(
            child: IconButton.filled(
              style: IconButton.styleFrom(
                backgroundColor: Colors.black54,
                foregroundColor: Colors.white,
              ),
              onPressed: controller == null ? null : _toggle,
              icon: Icon(
                controller?.value.isPlaying == true
                    ? Icons.pause_rounded
                    : Icons.play_arrow_rounded,
                size: 30,
              ),
            ),
          ),
          Positioned(
            left: 8,
            right: 48,
            bottom: 7,
            child: Text(
              widget.attachment.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 10.5,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          Positioned(
            right: 5,
            bottom: 2,
            child: IconButton(
              tooltip: 'Open video',
              onPressed: widget.onOpen,
              icon: const Icon(
                Icons.open_in_full_rounded,
                color: Colors.white,
                size: 18,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _DocumentPreview extends StatelessWidget {
  final MessageAttachment attachment;
  final Color textColor;
  final Color accentColor;
  final VoidCallback? onTap;
  const _DocumentPreview({
    required this.attachment,
    required this.textColor,
    required this.accentColor,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final extension = attachment.name.contains('.')
        ? attachment.name.split('.').last.toUpperCase()
        : 'FILE';
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: 270,
        margin: const EdgeInsets.symmetric(vertical: 3),
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: Colors.black.withValues(alpha: 0.13),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Container(
              width: 44,
              height: 48,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: accentColor.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.description_rounded, color: accentColor, size: 22),
                  Text(
                    extension,
                    maxLines: 1,
                    style: TextStyle(
                      color: accentColor,
                      fontSize: 8,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    attachment.name,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: textColor,
                      fontSize: 12.5,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    '${attachment.size} • $extension',
                    style: TextStyle(
                      color: textColor.withValues(alpha: 0.7),
                      fontSize: 10.5,
                    ),
                  ),
                ],
              ),
            ),
            Icon(
              Icons.chevron_right_rounded,
              color: textColor.withValues(alpha: 0.7),
            ),
          ],
        ),
      ),
    );
  }
}

class _VoiceNotePlayer extends StatefulWidget {
  final MessageAttachment attachment;
  final Color textColor;
  final Color accentColor;
  final double playbackSpeed;
  const _VoiceNotePlayer({
    required this.attachment,
    required this.textColor,
    required this.accentColor,
    this.playbackSpeed = 1.0,
  });

  @override
  State<_VoiceNotePlayer> createState() => _VoiceNotePlayerState();
}

class _VoiceNotePlayerState extends State<_VoiceNotePlayer> {
  final AudioPlayer _player = AudioPlayer();
  StreamSubscription<PlayerState>? _stateSubscription;
  bool _loading = false;
  bool _playing = false;
  Duration? _duration;

  @override
  void initState() {
    super.initState();
    _stateSubscription = _player.playerStateStream.listen((state) {
      if (mounted) setState(() => _playing = state.playing);
    });
  }

  @override
  void didUpdateWidget(covariant _VoiceNotePlayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Apply a live speed change (e.g. the user adjusts Voice Note Speed in
    // settings) without interrupting an in-progress playback.
    if (oldWidget.playbackSpeed != widget.playbackSpeed && _player.playing) {
      unawaited(_player.setSpeed(widget.playbackSpeed));
    }
  }

  @override
  void dispose() {
    unawaited(_stateSubscription?.cancel());
    unawaited(_player.dispose());
    super.dispose();
  }

  Future<void> _toggle() async {
    if (_loading) return;
    if (_player.playing) {
      await _player.pause();
      return;
    }
    try {
      if (_duration == null) {
        setState(() => _loading = true);
        final path = widget.attachment.url;
        if (path == null || path.isEmpty)
          throw Exception('Voice note path is missing.');
        final url = await ChatMediaService().createSignedUrl(
          path,
          expiresInSeconds: 3600,
        );
        _duration = await _player.setUrl(url);
      }
      if (_player.processingState == ProcessingState.completed)
        await _player.seek(Duration.zero);
      await _player.setSpeed(widget.playbackSpeed);
      await _player.play();
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _format(Duration value) {
    final minutes = value.inMinutes;
    final seconds = (value.inSeconds % 60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  @override
  Widget build(BuildContext context) {
    final fallback = Duration(seconds: widget.attachment.durationSeconds);
    return Container(
      width: 250,
      margin: const EdgeInsets.symmetric(vertical: 4),
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 7),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          IconButton.filled(
            visualDensity: VisualDensity.compact,
            style: IconButton.styleFrom(
              backgroundColor: widget.accentColor,
              foregroundColor: Colors.white,
            ),
            onPressed: _loading ? null : _toggle,
            icon: _loading
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : Icon(
                    _playing ? Icons.pause_rounded : Icons.play_arrow_rounded,
                  ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: StreamBuilder<Duration>(
              stream: _player.positionStream,
              initialData: Duration.zero,
              builder: (context, snapshot) {
                final position = snapshot.data ?? Duration.zero;
                final total = _duration ?? fallback;
                final maxMs = total.inMilliseconds <= 0
                    ? 1
                    : total.inMilliseconds;
                final progress = (position.inMilliseconds / maxMs).clamp(
                  0.0,
                  1.0,
                );
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    LinearProgressIndicator(
                      value: progress,
                      minHeight: 3,
                      borderRadius: BorderRadius.circular(3),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      '${_format(position)} / ${_format(total)}',
                      style: TextStyle(
                        color: widget.textColor.withValues(alpha: 0.75),
                        fontSize: 10.5,
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _LocationMapCard extends StatelessWidget {
  final ChatMessage message;
  final Color textColor;
  final Color accentColor;
  const _LocationMapCard({
    required this.message,
    required this.textColor,
    required this.accentColor,
  });

  double? _number(dynamic value) => value is num
      ? value.toDouble()
      : double.tryParse(value?.toString() ?? '');

  @override
  Widget build(BuildContext context) {
    final latitude = _number(message.metadata['latitude']);
    final longitude = _number(message.metadata['longitude']);
    final url =
        message.metadata['maps_url']?.toString() ??
        RegExp(r'https?://\S+').firstMatch(message.text)?.group(0);
    final point = latitude != null && longitude != null
        ? LatLng(latitude, longitude)
        : null;
    return InkWell(
      onTap: url == null
          ? null
          : () async {
              final uri = Uri.tryParse(url);
              if (uri != null)
                await launchUrl(uri, mode: LaunchMode.externalApplication);
            },
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: 270,
        decoration: BoxDecoration(
          color: Colors.black.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(12),
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              height: 142,
              child: point == null
                  ? Center(
                      child: Icon(
                        Icons.location_on_rounded,
                        color: accentColor,
                        size: 46,
                      ),
                    )
                  : FlutterMap(
                      options: MapOptions(
                        initialCenter: point,
                        initialZoom: 15,
                        interactionOptions: const InteractionOptions(
                          flags: InteractiveFlag.none,
                        ),
                      ),
                      children: [
                        TileLayer(
                          urlTemplate:
                              'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                          userAgentPackageName: 'com.chaty.app',
                        ),
                        MarkerLayer(
                          markers: [
                            Marker(
                              point: point,
                              width: 42,
                              height: 42,
                              child: Icon(
                                Icons.location_pin,
                                color: accentColor,
                                size: 42,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
            ),
            Padding(
              padding: const EdgeInsets.all(10),
              child: Row(
                children: [
                  Icon(
                    Icons.location_on_outlined,
                    color: accentColor,
                    size: 20,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Shared location',
                          style: TextStyle(
                            color: textColor,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        Text(
                          point == null
                              ? 'Tap to open location'
                              : '${point.latitude.toStringAsFixed(5)}, ${point.longitude.toStringAsFixed(5)}',
                          style: TextStyle(
                            color: textColor.withValues(alpha: 0.72),
                            fontSize: 10.5,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Icon(
                    Icons.open_in_new_rounded,
                    color: textColor.withValues(alpha: 0.7),
                    size: 17,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ContactCard extends StatelessWidget {
  final ChatMessage message;
  final Color textColor;
  final Color accentColor;
  const _ContactCard({
    required this.message,
    required this.textColor,
    required this.accentColor,
  });

  @override
  Widget build(BuildContext context) {
    final name = message.metadata['contact_name']?.toString().trim();
    final phonesRaw = message.metadata['phones'];
    final emailsRaw = message.metadata['emails'];
    final phones = phonesRaw is List
        ? phonesRaw
              .map((e) => e.toString())
              .where((e) => e.isNotEmpty)
              .toList(growable: false)
        : const <String>[];
    final emails = emailsRaw is List
        ? emailsRaw
              .map((e) => e.toString())
              .where((e) => e.isNotEmpty)
              .toList(growable: false)
        : const <String>[];
    final fallbackName = message.text
        .split('\n')
        .first
        .replaceFirst('👤', '')
        .trim();
    final displayName = name?.isNotEmpty == true
        ? name!
        : (fallbackName.isEmpty ? 'Shared contact' : fallbackName);
    final initials = displayName
        .split(RegExp(r'\s+'))
        .where((e) => e.isNotEmpty)
        .map((e) => e[0])
        .take(2)
        .join()
        .toUpperCase();
    return Container(
      width: 270,
      padding: const EdgeInsets.all(11),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              CircleAvatar(
                backgroundColor: accentColor.withValues(alpha: 0.2),
                foregroundColor: accentColor,
                child: Text(
                  initials.isEmpty ? 'C' : initials,
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  displayName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: textColor,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              Icon(
                Icons.contact_page_outlined,
                color: textColor.withValues(alpha: 0.7),
                size: 20,
              ),
            ],
          ),
          if (phones.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              phones.first,
              style: TextStyle(
                color: textColor.withValues(alpha: 0.78),
                fontSize: 11.5,
              ),
            ),
          ],
          if (emails.isNotEmpty) ...[
            const SizedBox(height: 3),
            Text(
              emails.first,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: textColor.withValues(alpha: 0.72),
                fontSize: 10.5,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// Locked state for view-once media before the recipient opens it.
class _ViewOnceLockedCard extends StatelessWidget {
  final bool isVideo;
  final Color textColor;
  final Color accentColor;
  final Color surfaceColor;
  final VoidCallback? onTap;

  const _ViewOnceLockedCard({
    required this.isVideo,
    required this.textColor,
    required this.accentColor,
    required this.surfaceColor,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(14),
      onTap: onTap,
      child: Container(
        width: 210,
        height: 130,
        decoration: BoxDecoration(
          color: surfaceColor.withValues(alpha: 0.55),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: accentColor.withValues(alpha: 0.35)),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Stack(
              alignment: Alignment.center,
              children: [
                Icon(
                  isVideo ? Icons.videocam_off_rounded : Icons.image_not_supported_outlined,
                  size: 30,
                  color: accentColor,
                ),
                Positioned(
                  right: -4,
                  bottom: -2,
                  child: Icon(Icons.one_k_rounded, size: 13, color: textColor.withValues(alpha: 0.7)),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              'View once',
              style: TextStyle(
                color: textColor.withValues(alpha: 0.85),
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              'Tap to open • opens only once',
              style: TextStyle(color: textColor.withValues(alpha: 0.5), fontSize: 9.5),
            ),
          ],
        ),
      ),
    );
  }
}

/// Expired state shown after opening when Anti View-Once retention is off.
class _ViewOnceExpiredCard extends StatelessWidget {
  final Color textColor;

  const _ViewOnceExpiredCard({required this.textColor});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 210,
      height: 64,
      decoration: BoxDecoration(
        color: textColor.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.visibility_off_rounded, size: 16, color: textColor.withValues(alpha: 0.55)),
          const SizedBox(width: 7),
          Text(
            'Opened • view-once media expired',
            style: TextStyle(color: textColor.withValues(alpha: 0.6), fontSize: 11),
          ),
        ],
      ),
    );
  }
}
