import 'dart:async';

import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../ui/core/theme/theme_config.dart';
import '../../data/services/chat_media_service.dart';
import '../../domain/models/chat_message.dart';
import '../../ui/core/widgets/app_avatar.dart';

class MessageBubble extends StatelessWidget {
  final ChatMessage message;
  final bool isMe;
  final ThemeConfig theme;
  final String? senderName;
  final VoidCallback onLongPress;
  final VoidCallback? onTaskTap;
  final Function(String emoji)? onReactionTap;
  final VoidCallback? onMediaTap;
  final bool showDeletedContent;

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
    this.showDeletedContent = false,
  });

  BorderRadius _bubbleRadius() {
    final r = Radius.circular(theme.cornerRadius);
    switch (theme.bubbleStyle) {
      case AppBubbleStyle.rounded:
        return BorderRadius.only(
          topLeft: r,
          topRight: r,
          bottomLeft: isMe ? r : const Radius.circular(3),
          bottomRight: isMe ? const Radius.circular(3) : r,
        );
      case AppBubbleStyle.softSquare:
        return BorderRadius.circular(6);
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

  Widget _deliveryIcon() {
    switch (message.deliveryState) {
      case DeliveryState.queued:
      case DeliveryState.sending:
        return const Icon(Icons.access_time_rounded, size: 12, color: Colors.white70);
      case DeliveryState.sent:
        return const Icon(Icons.done_rounded, size: 13, color: Colors.white70);
      case DeliveryState.delivered:
        return const Icon(Icons.done_all_rounded, size: 14, color: Colors.white70);
      case DeliveryState.read:
        return const Icon(Icons.done_all_rounded, size: 14, color: Color(0xFF38BDF8));
      case DeliveryState.failed:
        return const Icon(Icons.error_outline_rounded, size: 12, color: Colors.redAccent);
    }
  }

  String _formatTime(DateTime dt) {
    final hour = dt.hour.toString().padLeft(2, '0');
    final min = dt.minute.toString().padLeft(2, '0');
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
          child: Text(message.text, textAlign: TextAlign.center, style: TextStyle(color: theme.secondaryTextColor, fontSize: 11.5 * theme.fontScale)),
        ),
      );
    }

    if (message.isDeletedForEveryone && !showDeletedContent) {
      return Align(
        alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
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
              Icon(Icons.cancel_outlined, size: 17, color: theme.secondaryTextColor),
              const SizedBox(width: 7),
              Text('This message was deleted', style: TextStyle(color: theme.secondaryTextColor, fontSize: 13 * theme.fontScale, fontStyle: FontStyle.italic)),
            ],
          ),
        ),
      );
    }

    final bubbleBg = isMe ? theme.outgoingBubbleColor : theme.incomingBubbleColor;
    final textColor = isMe ? theme.outgoingTextColor : theme.incomingTextColor;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3, horizontal: 12),
      child: Row(
        mainAxisAlignment: isMe ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          if (!isMe && senderName != null)
            Padding(
              padding: const EdgeInsets.only(right: 8, bottom: 2),
              child: AppAvatar(
                initials: senderName!.split(' ').map((e) => e.isEmpty ? '' : e[0]).take(2).join(),
                colorHex: '0xFF6366F1',
                size: 28,
              ),
            ),
          Flexible(
            child: GestureDetector(
              onLongPress: onLongPress,
              child: Column(
                crossAxisAlignment: isMe ? CrossAxisAlignment.end : CrossAxisAlignment.start,
                children: [
                  Container(
                    constraints: BoxConstraints(maxWidth: MediaQuery.sizeOf(context).width * 0.78),
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                    decoration: BoxDecoration(
                      color: bubbleBg,
                      borderRadius: _bubbleRadius(),
                      boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.06), blurRadius: 3, offset: const Offset(0, 1))],
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (!isMe && senderName != null) ...[
                          Text(senderName!, style: TextStyle(color: theme.accentColor, fontSize: 11.5 * theme.fontScale, fontWeight: FontWeight.w700)),
                          const SizedBox(height: 3),
                        ],
                        if (message.isDeletedForEveryone && showDeletedContent)
                          Container(
                            margin: const EdgeInsets.only(bottom: 7),
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                            decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(10)),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.cancel_outlined, size: 14, color: textColor.withValues(alpha: 0.8)),
                                const SizedBox(width: 5),
                                Text('Deleted message', style: TextStyle(color: textColor.withValues(alpha: 0.8), fontSize: 10.5, fontWeight: FontWeight.w700)),
                              ],
                            ),
                          ),
                        if (message.replyToMessageId != null)
                          Container(
                            margin: const EdgeInsets.only(bottom: 6),
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
                            decoration: BoxDecoration(
                              color: Colors.black.withValues(alpha: 0.13),
                              borderRadius: BorderRadius.circular(8),
                              border: Border(left: BorderSide(color: isMe ? Colors.white70 : theme.accentColor, width: 3)),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(message.replyToSenderName ?? 'Reply', style: TextStyle(color: isMe ? Colors.white : theme.accentColor, fontSize: 11, fontWeight: FontWeight.bold)),
                                Text(message.replyToPreviewText ?? '', maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: textColor.withValues(alpha: 0.8), fontSize: 11)),
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
                              decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.16), borderRadius: BorderRadius.circular(10), border: Border.all(color: theme.accentColor.withValues(alpha: 0.35))),
                              child: Row(
                                children: [
                                  Icon(Icons.task_alt_rounded, color: theme.accentColor, size: 22),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(message.text, style: TextStyle(color: textColor, fontWeight: FontWeight.bold, fontSize: 13 * theme.fontScale)),
                                        Text('Task • tap to open', style: TextStyle(color: textColor.withValues(alpha: 0.7), fontSize: 10.5)),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        if (message.attachment != null && message.attachment!.type == 'image')
                          _SignedImagePreview(storagePath: message.attachment!.url, semanticLabel: message.attachment!.name, onTap: onMediaTap),
                        if (message.attachment != null && message.attachment!.type == 'video')
                          InkWell(
                            onTap: onMediaTap,
                            borderRadius: BorderRadius.circular(12),
                            child: Container(
                              height: 170,
                              width: 260,
                              margin: const EdgeInsets.only(bottom: 6),
                              decoration: BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(12)),
                              child: Stack(
                                alignment: Alignment.center,
                                children: [
                                  const Icon(Icons.play_circle_fill_rounded, size: 52, color: Colors.white),
                                  Positioned(left: 10, right: 10, bottom: 8, child: Text(message.attachment!.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white, fontSize: 11))),
                                ],
                              ),
                            ),
                          ),
                        if (message.attachment != null && message.attachment!.type == 'audio')
                          _VoiceNotePlayer(attachment: message.attachment!, textColor: textColor, accentColor: theme.accentColor),
                        if (message.attachment != null && message.attachment!.type == 'document')
                          InkWell(
                            onTap: onMediaTap,
                            borderRadius: BorderRadius.circular(10),
                            child: Container(
                              margin: const EdgeInsets.symmetric(vertical: 4),
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.13), borderRadius: BorderRadius.circular(10)),
                              child: Row(
                                children: [
                                  const Icon(Icons.insert_drive_file_rounded, color: Colors.redAccent, size: 24),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(message.attachment!.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: textColor, fontSize: 12.5, fontWeight: FontWeight.bold)),
                                        Text(message.attachment!.size, style: TextStyle(color: textColor.withValues(alpha: 0.7), fontSize: 10.5)),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        if (message.type == MessageType.location)
                          _LocationCard(text: message.text, textColor: textColor, accentColor: theme.accentColor),
                        if (message.type != MessageType.taskCard && message.type != MessageType.location && message.text.isNotEmpty)
                          Text(message.text, style: TextStyle(color: textColor, fontSize: 14 * theme.fontScale, height: 1.35)),
                        const SizedBox(height: 4),
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          mainAxisAlignment: MainAxisAlignment.end,
                          children: [
                            if (message.isPinned) ...[
                              Icon(Icons.push_pin_rounded, size: 11, color: textColor.withValues(alpha: 0.7)),
                              const SizedBox(width: 3),
                            ],
                            if (message.isStarred) ...[
                              const Icon(Icons.star_rounded, size: 11, color: Colors.amber),
                              const SizedBox(width: 3),
                            ],
                            Text(_formatTime(message.createdAt), style: TextStyle(color: textColor.withValues(alpha: 0.65), fontSize: 10.5 * theme.fontScale)),
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
                        children: message.reactions.map((reaction) {
                          return InkWell(
                            onTap: () => onReactionTap?.call(reaction.emoji),
                            borderRadius: BorderRadius.circular(12),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(color: theme.cardColor, borderRadius: BorderRadius.circular(12), border: Border.all(color: theme.accentColor.withValues(alpha: 0.25))),
                              child: Text('${reaction.emoji} ${reaction.userIds.length}', style: TextStyle(color: theme.primaryTextColor, fontSize: 11)),
                            ),
                          );
                        }).toList(growable: false),
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
  const _SignedImagePreview({required this.storagePath, required this.semanticLabel, this.onTap});

  @override
  State<_SignedImagePreview> createState() => _SignedImagePreviewState();
}

class _SignedImagePreviewState extends State<_SignedImagePreview> {
  late Future<String>? _url;

  @override
  void initState() {
    super.initState();
    final path = widget.storagePath;
    _url = path == null || path.isEmpty ? null : ChatMediaService().createSignedUrl(path, expiresInSeconds: 3600);
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
            width: 260,
            height: 190,
            child: _url == null
                ? const ColoredBox(color: Colors.black26, child: Center(child: Icon(Icons.broken_image_outlined)))
                : FutureBuilder<String>(
                    future: _url,
                    builder: (context, snapshot) {
                      if (snapshot.connectionState != ConnectionState.done) {
                        return const ColoredBox(color: Colors.black26, child: Center(child: CircularProgressIndicator(strokeWidth: 2)));
                      }
                      final url = snapshot.data;
                      if (url == null || url.isEmpty) {
                        return const ColoredBox(color: Colors.black26, child: Center(child: Icon(Icons.broken_image_outlined)));
                      }
                      return Image.network(url, fit: BoxFit.cover, gaplessPlayback: true, errorBuilder: (_, _, _) => const ColoredBox(color: Colors.black26, child: Center(child: Icon(Icons.broken_image_outlined))));
                    },
                  ),
          ),
        ),
      ),
    );
  }
}

class _VoiceNotePlayer extends StatefulWidget {
  final MessageAttachment attachment;
  final Color textColor;
  final Color accentColor;
  const _VoiceNotePlayer({required this.attachment, required this.textColor, required this.accentColor});

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
        if (path == null || path.isEmpty) throw Exception('Voice note path is missing.');
        final url = await ChatMediaService().createSignedUrl(path, expiresInSeconds: 3600);
        _duration = await _player.setUrl(url);
      }
      if (_player.processingState == ProcessingState.completed) await _player.seek(Duration.zero);
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
      width: 240,
      margin: const EdgeInsets.symmetric(vertical: 4),
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 7),
      decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(14)),
      child: Row(
        children: [
          IconButton.filled(
            visualDensity: VisualDensity.compact,
            style: IconButton.styleFrom(backgroundColor: widget.accentColor, foregroundColor: Colors.white),
            onPressed: _loading ? null : _toggle,
            icon: _loading
                ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : Icon(_playing ? Icons.pause_rounded : Icons.play_arrow_rounded),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: StreamBuilder<Duration>(
              stream: _player.positionStream,
              initialData: Duration.zero,
              builder: (context, snapshot) {
                final position = snapshot.data ?? Duration.zero;
                final total = _duration ?? fallback;
                final maxMs = total.inMilliseconds <= 0 ? 1 : total.inMilliseconds;
                final progress = (position.inMilliseconds / maxMs).clamp(0.0, 1.0);
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    LinearProgressIndicator(value: progress, minHeight: 3, borderRadius: BorderRadius.circular(3)),
                    const SizedBox(height: 5),
                    Text('${_format(position)} / ${_format(total)}', style: TextStyle(color: widget.textColor.withValues(alpha: 0.75), fontSize: 10.5)),
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

class _LocationCard extends StatelessWidget {
  final String text;
  final Color textColor;
  final Color accentColor;
  const _LocationCard({required this.text, required this.textColor, required this.accentColor});

  @override
  Widget build(BuildContext context) {
    final rawUrl = RegExp(r'https?://\S+').firstMatch(text)?.group(0);
    return InkWell(
      onTap: rawUrl == null
          ? null
          : () async {
              final uri = Uri.tryParse(rawUrl);
              if (uri != null) await launchUrl(uri, mode: LaunchMode.externalApplication);
            },
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: 240,
        padding: const EdgeInsets.all(11),
        decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(12)),
        child: Row(
          children: [
            Container(width: 42, height: 42, decoration: BoxDecoration(color: accentColor.withValues(alpha: 0.2), borderRadius: BorderRadius.circular(12)), child: Icon(Icons.location_on_rounded, color: accentColor)),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Shared location', style: TextStyle(color: textColor, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 2),
                  Text('Tap to open in Maps', style: TextStyle(color: textColor.withValues(alpha: 0.7), fontSize: 11)),
                ],
              ),
            ),
            Icon(Icons.open_in_new_rounded, color: textColor.withValues(alpha: 0.7), size: 18),
          ],
        ),
      ),
    );
  }
}
