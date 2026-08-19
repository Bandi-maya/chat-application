import 'package:flutter/material.dart';
import '../../../ui/core/theme/theme_config.dart';
import '../../../ui/core/theme/theme_controller.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/user_profile.dart';
import '../../domain/models/chat_message.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../data/services/chat_media_service.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../ui/core/design_system/chaty_settings_primitives.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/widgets/security_chip.dart';
import '../../ui/core/commands/chat_command_parser.dart';
import '../messages/message_bubble.dart';

import '../messages/message_action_sheet.dart';
import '../messages/attachment_sheet.dart';
import '../messages/media_viewer_screen.dart';
import '../tasks/task_create_edit_modal.dart';
import 'group_info_screen.dart';
import '../calls/mock_call_screen.dart';

class ChatDetailScreen extends StatefulWidget {
  final ThemeConfig theme;
  final MockDataStore dataStore;
  final String conversationId;
  final ChatyPreferencesController preferencesController;
  final ThemeController? themeController;

  const ChatDetailScreen({
    super.key,
    required this.theme,
    required this.dataStore,
    required this.conversationId,
    required this.preferencesController,
    this.themeController,
  });

  @override
  State<ChatDetailScreen> createState() => _ChatDetailScreenState();
}

class _ChatDetailScreenState extends State<ChatDetailScreen> {
  final TextEditingController _textCtrl = TextEditingController();
  final ScrollController _scrollCtrl = ScrollController();
  final ChatMediaService _mediaService = ChatMediaService();
  ChatMessage? _replyTarget;
  bool _showQuickReplyOverlay = false;

  @override
  void initState() {
    super.initState();
    final conv = widget.dataStore.conversations.firstWhere(
      (c) => c.id == widget.conversationId,
      orElse: () => widget.dataStore.conversations.first,
    );
    if (conv.draftText.isNotEmpty) {
      _textCtrl.text = conv.draftText;
    }
  }

  @override
  void dispose() {
    _textCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  void _sendMessage() {
    final text = _textCtrl.text.trim();
    if (text.isEmpty) return;

    final parsedCommand = ChatCommandParser.parse(text);
    if (parsedCommand.type == ChatCommandType.task) {
      _textCtrl.clear();
      showModalBottomSheet(
        context: context,
        isScrollControlled: true,
        backgroundColor: Colors.transparent,
        builder: (context) => TaskCreateEditModal(
          theme: widget.theme,
          dataStore: widget.dataStore,
          sourceConversationId: widget.conversationId,
          initialTitle: parsedCommand.argument.isNotEmpty ? parsedCommand.argument : null,
        ),
      );
      return;
    }

    widget.dataStore.sendMessage(
      conversationId: widget.conversationId,
      text: text,
      replyToMessageId: _replyTarget?.id,
      replyToSenderName: _replyTarget != null ? _getSenderName(_replyTarget!.senderId) : null,
      replyToPreviewText: _replyTarget?.text,
    );

    _textCtrl.clear();
    setState(() {
      _replyTarget = null;
      _showQuickReplyOverlay = false;
    });
    _scrollToBottom();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollCtrl.hasClients) {
        _scrollCtrl.animateTo(
          _scrollCtrl.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  String _getSenderName(String senderId) {
    if (senderId == widget.dataStore.currentUser.id) return 'You';
    final contacts = widget.dataStore.contacts;
    final contact = contacts.where((c) => c.id == senderId).firstOrNull;
    return contact?.displayName ?? 'Chaty user';
  }

  void _onMessageLongPress(ChatMessage message) {
    final isMe = message.senderId == widget.dataStore.currentUser.id;
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (context) => MessageActionSheet(
        message: message,
        isMe: isMe,
        theme: widget.theme,
        onReact: (emoji) {
          widget.dataStore.toggleReaction(widget.conversationId, message.id, emoji);
          Navigator.of(context).pop();
        },
        onReply: () {
          setState(() => _replyTarget = message);
          Navigator.of(context).pop();
        },
        onPin: () {
          widget.dataStore.togglePinMessage(widget.conversationId, message.id);
          Navigator.of(context).pop();
        },
        onStar: () {
          widget.dataStore.toggleStarMessage(widget.conversationId, message.id);
          Navigator.of(context).pop();
        },
        onCopy: () {
          Navigator.of(context).pop();
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Message copied to clipboard')));
        },
        onDeleteForMe: () {
          widget.dataStore.deleteMessage(widget.conversationId, message.id, forEveryone: false);
          Navigator.of(context).pop();
        },
        onDeleteForEveryone: isMe
            ? () {
                widget.dataStore.deleteMessage(widget.conversationId, message.id, forEveryone: true);
                Navigator.of(context).pop();
              }
            : null,
        onReport: () {
          Navigator.of(context).pop();
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Message reported')));
        },
        onCreateTask: () {
          Navigator.of(context).pop();
          _openCreateTaskModal(initialTitle: message.text, sourceMessageId: message.id);
        },
      ),
    );
  }

  void _openCreateTaskModal({String? initialTitle, String? sourceMessageId}) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) => TaskCreateEditModal(
        theme: widget.theme,
        dataStore: widget.dataStore,
        sourceConversationId: widget.conversationId,
        initialTitle: initialTitle,
        sourceMessageId: sourceMessageId,
      ),
    );
  }

  Future<void> _shareMedia(String type) async {
    try {
      final attachment = await _mediaService.pickAndUpload(
        conversationId: widget.conversationId,
        type: type,
      );
      if (attachment == null) return;

      await widget.dataStore.sendMessage(
        conversationId: widget.conversationId,
        text: attachment.name,
        type: switch (type) {
          'image' => MessageType.image,
          'video' => MessageType.video,
          'audio' => MessageType.audio,
          _ => MessageType.document,
        },
        attachment: attachment,
      );
      _scrollToBottom();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    }
  }

  void _showAttachmentUnavailable(String feature) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('$feature is not configured for this release yet.')),
    );
  }

  void _openAttachmentSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (context) => AttachmentSheet(
        theme: widget.theme,
        onMediaRequested: _shareMedia,
        onLocationRequested: () => _showAttachmentUnavailable('Location sharing'),
        onContactRequested: () => _showAttachmentUnavailable('Contact sharing'),
        onPollRequested: () => _showAttachmentUnavailable('Poll creation'),
        onTaskOption: () => _openCreateTaskModal(),
      ),
    );
  }

  void _startMockCall(bool isVideo) {
    final conv = widget.dataStore.conversations.firstWhere(
      (c) => c.id == widget.conversationId,
      orElse: () => widget.dataStore.conversations.first,
    );
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => MockCallScreen(
          theme: widget.theme,
          title: conv.title,
          isVideo: isVideo,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = widget.theme;
    final dataStore = widget.dataStore;
    final convPrefs = widget.preferencesController.conversation;
    final autoPrefs = widget.preferencesController.automation;

    final conv = dataStore.conversations.firstWhere(
      (c) => c.id == widget.conversationId,
      orElse: () => dataStore.conversations.first,
    );

    final otherParticipant = conv.participantIds.firstWhere(
      (id) => id != dataStore.currentUser.id,
      orElse: () => '',
    );
    final otherUser = otherParticipant.isNotEmpty ? dataStore.getUser(otherParticipant) : null;

    final messages = dataStore.getMessages(widget.conversationId);
    final isTyping = dataStore.isUserTyping(widget.conversationId);

    Widget chatContent = Scaffold(
      backgroundColor: theme.backgroundColor,
      appBar: AppBar(
        backgroundColor: theme.surfaceColor,
        elevation: 1,
        titleSpacing: 0,
        title: InkWell(
          onTap: () {
            if (conv.type == ConversationType.group) {
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (context) => GroupInfoScreen(
                    theme: theme,
                    dataStore: dataStore,
                    conversationId: widget.conversationId,
                  ),
                ),
              );
            }
          },
          child: Row(
            children: [
              AppAvatar(
                initials: conv.avatarInitials ?? conv.title.characters.take(2).toString().toUpperCase(),
                colorHex: conv.avatarColorHex ?? '0xFF6366F1',
                size: 38,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      conv.title,
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 16 * theme.fontScale,
                        fontWeight: FontWeight.bold,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                    Row(
                      children: [
                        if (isTyping)
                          Text(
                            'typing...',
                            style: TextStyle(color: theme.accentColor, fontSize: 11.5, fontStyle: FontStyle.italic),
                          )
                        else if (otherUser?.presence == PresenceState.online) ...[
                          Container(width: 7, height: 7, decoration: const BoxDecoration(color: Colors.greenAccent, shape: BoxShape.circle)),
                          const SizedBox(width: 4),
                          Text('Online', style: TextStyle(color: theme.secondaryTextColor, fontSize: 11.5)),
                        ] else
                          Text('Offline', style: TextStyle(color: theme.secondaryTextColor, fontSize: 11.5)),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        actions: [
          IconButton(icon: const Icon(Icons.call_rounded), onPressed: () => _startMockCall(false)),
          IconButton(icon: const Icon(Icons.videocam_rounded), onPressed: () => _startMockCall(true)),
          Padding(
            padding: const EdgeInsets.only(right: 8.0),
            child: SecurityChip(status: conv.encryptionStatus),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: ListView.builder(
                controller: _scrollCtrl,
                padding: const EdgeInsets.symmetric(vertical: 8),
                itemCount: messages.length,
                itemBuilder: (context, idx) {
                  final msg = messages[idx];
                  final isMe = msg.senderId == dataStore.currentUser.id;
                  final senderName = conv.type == ConversationType.group && !isMe ? _getSenderName(msg.senderId) : null;
                  return MessageBubble(
                    message: msg,
                    isMe: isMe,
                    theme: theme,
                    senderName: senderName,
                    onLongPress: () => _onMessageLongPress(msg),
                    onReactionTap: (emoji) => dataStore.toggleReaction(conv.id, msg.id, emoji),
                    onTaskTap: msg.linkedTaskId != null ? () => _openCreateTaskModal(sourceMessageId: msg.id) : null,
                    onMediaTap: msg.attachment != null
                        ? () {
                            Navigator.of(context).push(
                              MaterialPageRoute(
                                builder: (_) => MediaViewerScreen(
                                  title: msg.attachment!.name,
                                  type: msg.attachment!.type,
                                  size: msg.attachment!.size,
                                  storagePath: msg.attachment!.url,
                                  theme: theme,
                                ),
                              ),
                            );
                          }
                        : null,
                  );
                },
              ),
            ),
            if (_showQuickReplyOverlay && autoPrefs.quickReplies.isNotEmpty)
              Container(
                margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: theme.cardColor,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: theme.accentColor.withValues(alpha: 0.3)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Padding(
                      padding: const EdgeInsets.only(left: 6, bottom: 4),
                      child: Text('QUICK REPLIES (#)', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: theme.accentColor)),
                    ),
                    ...autoPrefs.quickReplies.map((q) {
                      return ListTile(
                        dense: true,
                        title: Text('${q.shortcut} — ${q.title}', style: TextStyle(color: theme.primaryTextColor, fontSize: 13, fontWeight: FontWeight.bold)),
                        subtitle: Text(q.content, maxLines: 1, overflow: TextOverflow.ellipsis),
                        onTap: () {
                          _textCtrl.text = q.content;
                          setState(() => _showQuickReplyOverlay = false);
                        },
                      );
                    }),
                  ],
                ),
              ),
            if (_replyTarget != null)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                color: theme.cardColor,
                child: Row(
                  children: [
                    Icon(Icons.reply_rounded, color: theme.accentColor, size: 20),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Replying to ${_getSenderName(_replyTarget!.senderId)}', style: TextStyle(color: theme.accentColor, fontWeight: FontWeight.bold, fontSize: 12)),
                          Text(_replyTarget!.text, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: theme.secondaryTextColor, fontSize: 12)),
                        ],
                      ),
                    ),
                    IconButton(icon: const Icon(Icons.close_rounded, size: 18), onPressed: () => setState(() => _replyTarget = null)),
                  ],
                ),
              ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              decoration: BoxDecoration(
                color: theme.surfaceColor,
                border: Border(
                  top: BorderSide(
                    color: theme.brightness == Brightness.dark
                        ? Colors.white.withValues(alpha: 0.06)
                        : Colors.black.withValues(alpha: 0.05),
                    width: 1,
                  ),
                ),
              ),
              child: SafeArea(
                top: false,
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    IconButton(
                      icon: const Icon(Icons.add_circle_outline_rounded),
                      color: theme.accentColor,
                      tooltip: 'Attach Media or File',
                      onPressed: _openAttachmentSheet,
                    ),
                    Expanded(
                      child: Container(
                        decoration: BoxDecoration(
                          color: theme.cardColor,
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(
                            color: theme.brightness == Brightness.dark
                                ? Colors.white.withValues(alpha: 0.08)
                                : Colors.black.withValues(alpha: 0.06),
                            width: 1,
                          ),
                        ),
                        child: TextField(
                          controller: _textCtrl,
                          maxLines: 5,
                          minLines: 1,
                          style: TextStyle(
                            color: theme.primaryTextColor,
                            fontSize: 14.5 * theme.fontScale,
                            height: 1.3,
                          ),
                          onChanged: (val) {
                            setState(() {
                              _showQuickReplyOverlay = val.contains('#');
                            });
                          },
                          decoration: InputDecoration(
                            hintText: 'Type a message... (try /task or #)',
                            hintStyle: TextStyle(
                              color: theme.secondaryTextColor.withValues(alpha: 0.65),
                              fontSize: 13.5 * theme.fontScale,
                            ),
                            isDense: true,
                            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                            border: InputBorder.none,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    AnimatedSwitcher(
                      duration: const Duration(milliseconds: 160),
                      transitionBuilder: (child, animation) => ScaleTransition(
                        scale: Tween<double>(begin: 0.85, end: 1.0).animate(animation),
                        child: FadeTransition(opacity: animation, child: child),
                      ),
                      child: _textCtrl.text.trim().isNotEmpty
                          ? Container(
                              key: const ValueKey('send_btn'),
                              decoration: BoxDecoration(
                                color: theme.accentColor,
                                shape: BoxShape.circle,
                              ),
                              child: IconButton(
                                style: IconButton.styleFrom(
                                  backgroundColor: theme.accentColor,
                                  foregroundColor: Colors.white,
                                ),
                                icon: const Icon(Icons.send_rounded, size: 18),
                                onPressed: _sendMessage,
                              ),
                            )
                          : Container(
                              key: const ValueKey('mic_btn'),
                              decoration: BoxDecoration(
                                color: theme.accentColor.withValues(alpha: 0.15),
                                shape: BoxShape.circle,
                              ),
                              child: IconButton(
                                style: IconButton.styleFrom(
                                  foregroundColor: theme.accentColor,
                                ),
                                icon: const Icon(Icons.mic_rounded, size: 20),
                                onPressed: () {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    const SnackBar(
                                      content: Text('Voice-note recording is not configured for this release.'),
                                      duration: Duration(seconds: 2),
                                    ),
                                  );
                                },
                              ),
                            ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );

    if (convPrefs.enableQuickContactSidebar) {
      final contacts = dataStore.contacts;
      return Row(
        children: [
          if (convPrefs.sidebarPosition == 'Left')
            Container(
              width: 54,
              color: theme.surfaceColor.withValues(alpha: convPrefs.sidebarOpacity),
              child: ListView.builder(
                itemCount: contacts.length,
                itemBuilder: (ctx, i) {
                  final c = contacts[i];
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 6),
                    child: GestureDetector(
                      onTap: () {
                        final nextConv = dataStore.conversations.firstWhere(
                          (cv) => cv.title.contains(c.displayName.split(' ').first),
                          orElse: () => dataStore.conversations.first,
                        );
                        Navigator.of(context).pushReplacement(
                          MaterialPageRoute(
                            builder: (_) => ChatDetailScreen(
                              conversationId: nextConv.id,
                              theme: theme,
                              dataStore: dataStore,
                              preferencesController: widget.preferencesController,
                            ),
                          ),
                        );
                      },
                      child: ChatyAvatar(initials: c.avatarInitials, color: Color(int.parse(c.avatarColorHex)), size: 36),
                    ),
                  );
                },
              ),
            ),
          Expanded(child: chatContent),
          if (convPrefs.sidebarPosition == 'Right')
            Container(
              width: 54,
              color: theme.surfaceColor.withValues(alpha: convPrefs.sidebarOpacity),
              child: ListView.builder(
                itemCount: contacts.length,
                itemBuilder: (ctx, i) {
                  final c = contacts[i];
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 6),
                    child: GestureDetector(
                      onTap: () {
                        final nextConv = dataStore.conversations.firstWhere(
                          (cv) => cv.title.contains(c.displayName.split(' ').first),
                          orElse: () => dataStore.conversations.first,
                        );
                        Navigator.of(context).pushReplacement(
                          MaterialPageRoute(
                            builder: (_) => ChatDetailScreen(
                              conversationId: nextConv.id,
                              theme: theme,
                              dataStore: dataStore,
                              preferencesController: widget.preferencesController,
                            ),
                          ),
                        );
                      },
                      child: ChatyAvatar(initials: c.avatarInitials, color: Color(int.parse(c.avatarColorHex)), size: 36),
                    ),
                  );
                },
              ),
            ),
        ],
      );
    }

    return chatContent;
  }
}
