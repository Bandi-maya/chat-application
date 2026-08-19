import 'dart:async';

import 'package:flutter/material.dart';

import '../../../ui/core/theme/theme_config.dart';
import '../../../ui/core/theme/theme_controller.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/user_profile.dart';
import '../../domain/models/chat_message.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../ui/core/design_system/chaty_settings_primitives.dart';
import '../../ui/core/gb/gb_theme_overrides.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/widgets/security_chip.dart';
import '../../ui/core/commands/chat_command_parser.dart';
import '../messages/message_bubble.dart';
import '../messages/message_action_sheet.dart';
import '../messages/attachment_sheet.dart';
import '../messages/chat_attachment_actions.dart';
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
  late final ChatAttachmentActions _attachments;
  Timer? _typingIdleTimer;
  bool _typingPublished = false;
  ChatMessage? _replyTarget;
  bool _showQuickReplyOverlay = false;

  ThemeConfig get _theme => GbThemeOverrides.resolve(
        widget.themeController?.globalTheme ?? widget.theme,
        widget.preferencesController,
      );

  @override
  void initState() {
    super.initState();
    _attachments = ChatAttachmentActions(
      conversationId: widget.conversationId,
      dataStore: widget.dataStore,
      preferencesController: widget.preferencesController,
    );
    final conversation = widget.dataStore.conversations
        .where((item) => item.id == widget.conversationId)
        .firstOrNull;
    if (conversation != null && conversation.draftText.isNotEmpty) {
      _textCtrl.text = conversation.draftText;
    }
    widget.dataStore.ensureConversationLoaded(widget.conversationId).then((_) {
      if (!mounted) return;
      setState(() {});
      _scrollToBottom();
    }).catchError((_) {});
  }

  @override
  void dispose() {
    _typingIdleTimer?.cancel();
    if (_typingPublished) widget.dataStore.setTyping(widget.conversationId, false);
    widget.dataStore.setDraft(widget.conversationId, _textCtrl.text);
    _textCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  String _senderName(String senderId) {
    if (senderId == widget.dataStore.currentUser.id) return 'You';
    return widget.dataStore.getUser(senderId)?.displayName ?? 'Chaty user';
  }

  String _lastSeen(UserProfile? user) {
    if (user == null) return '';
    if (user.presence == PresenceState.typing) return 'typing…';
    if (user.presence == PresenceState.online) return 'online';
    final lastSeen = user.lastSeenAt.toLocal();
    final now = DateTime.now();
    final difference = now.difference(lastSeen);
    if (difference.inMinutes < 1) return 'last seen just now';
    if (difference.inMinutes < 60) return 'last seen ${difference.inMinutes}m ago';
    if (difference.inHours < 24 && now.day == lastSeen.day) {
      final hour = lastSeen.hour.toString().padLeft(2, '0');
      final minute = lastSeen.minute.toString().padLeft(2, '0');
      return 'last seen today at $hour:$minute';
    }
    return 'last seen ${lastSeen.day}/${lastSeen.month}/${lastSeen.year}';
  }

  void _handleComposerChanged(String value) {
    widget.dataStore.setDraft(widget.conversationId, value);
    final shouldType = value.trim().isNotEmpty;
    if (shouldType && !_typingPublished) {
      _typingPublished = true;
      widget.dataStore.setTyping(widget.conversationId, true);
    }
    if (!shouldType && _typingPublished) {
      _typingPublished = false;
      widget.dataStore.setTyping(widget.conversationId, false);
    }
    _typingIdleTimer?.cancel();
    if (shouldType) {
      _typingIdleTimer = Timer(const Duration(seconds: 5), () {
        if (!mounted || !_typingPublished) return;
        _typingPublished = false;
        widget.dataStore.setTyping(widget.conversationId, false);
      });
    }
    setState(() => _showQuickReplyOverlay = value.contains('#'));
  }

  Future<void> _sendMessage() async {
    final text = _textCtrl.text.trim();
    if (text.isEmpty) return;

    final command = ChatCommandParser.parse(text);
    if (command.type == ChatCommandType.task) {
      _textCtrl.clear();
      _typingPublished = false;
      widget.dataStore.setTyping(widget.conversationId, false);
      setState(() {});
      _openCreateTaskModal(initialTitle: command.argument.isEmpty ? null : command.argument);
      return;
    }

    final reply = _replyTarget;
    _textCtrl.clear();
    _typingIdleTimer?.cancel();
    if (_typingPublished) {
      _typingPublished = false;
      widget.dataStore.setTyping(widget.conversationId, false);
    }
    setState(() {
      _replyTarget = null;
      _showQuickReplyOverlay = false;
    });

    try {
      await widget.dataStore.sendMessage(
        conversationId: widget.conversationId,
        text: text,
        replyToMessageId: reply?.id,
        replyToSenderName: reply == null ? null : _senderName(reply.senderId),
        replyToPreviewText: reply?.text,
      );
      _scrollToBottom();
    } catch (error) {
      if (!mounted) return;
      _textCtrl.text = text;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.toString().replaceFirst('Exception: ', ''))),
      );
      setState(() {});
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollCtrl.hasClients) {
        _scrollCtrl.animateTo(
          _scrollCtrl.position.maxScrollExtent,
          duration: const Duration(milliseconds: 220),
          curve: Curves.easeOutCubic,
        );
      }
    });
  }

  void _onMessageLongPress(ChatMessage message) {
    final theme = _theme;
    final isMine = message.senderId == widget.dataStore.currentUser.id;
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) => MessageActionSheet(
        message: message,
        isMe: isMine,
        theme: theme,
        onReact: (emoji) {
          widget.dataStore.toggleReaction(widget.conversationId, message.id, emoji);
          Navigator.of(sheetContext).pop();
        },
        onReply: () {
          Navigator.of(sheetContext).pop();
          setState(() => _replyTarget = message);
        },
        onPin: () {
          widget.dataStore.togglePinMessage(widget.conversationId, message.id);
          Navigator.of(sheetContext).pop();
        },
        onStar: () {
          widget.dataStore.toggleStarMessage(widget.conversationId, message.id);
          Navigator.of(sheetContext).pop();
        },
        onCopy: () {
          Navigator.of(sheetContext).pop();
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Message copied')));
        },
        onDeleteForMe: () {
          widget.dataStore.deleteMessage(widget.conversationId, message.id, forEveryone: false);
          Navigator.of(sheetContext).pop();
        },
        onDeleteForEveryone: isMine
            ? () {
                widget.dataStore.deleteMessage(widget.conversationId, message.id, forEveryone: true);
                Navigator.of(sheetContext).pop();
              }
            : null,
        onReport: () {
          Navigator.of(sheetContext).pop();
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Report submitted')));
        },
        onCreateTask: () {
          Navigator.of(sheetContext).pop();
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
      builder: (_) => TaskCreateEditModal(
        theme: _theme,
        dataStore: widget.dataStore,
        sourceConversationId: widget.conversationId,
        initialTitle: initialTitle,
        sourceMessageId: sourceMessageId,
      ),
    );
  }

  void _openAttachmentSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => AttachmentSheet(
        theme: _theme,
        onMediaRequested: (type) async {
          await _attachments.shareMedia(context, type);
          _scrollToBottom();
        },
        onLocationRequested: () async {
          await _attachments.shareLocation(context);
          _scrollToBottom();
        },
        onContactRequested: () async {
          await _attachments.shareContact(context);
          _scrollToBottom();
        },
        onPollRequested: () async {
          await _attachments.createPoll(context);
          _scrollToBottom();
        },
        onTaskOption: _openCreateTaskModal,
      ),
    );
  }

  void _startCall(bool isVideo) {
    final conversation = widget.dataStore.conversations
        .where((item) => item.id == widget.conversationId)
        .firstOrNull;
    if (conversation == null) return;
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => MockCallScreen(
          theme: _theme,
          title: conversation.title,
          isVideo: isVideo,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = _theme;
    final dataStore = widget.dataStore;
    final conversation = dataStore.conversations
        .where((item) => item.id == widget.conversationId)
        .firstOrNull;

    if (conversation == null) {
      return Scaffold(
        backgroundColor: theme.backgroundColor,
        appBar: AppBar(
          automaticallyImplyLeading: false,
          leading: IconButton(
            onPressed: () => Navigator.of(context).pop(),
            icon: const Icon(Icons.chevron_left_rounded),
          ),
          title: const Text('Chat'),
        ),
        body: Center(child: Text('Conversation unavailable', style: TextStyle(color: theme.secondaryTextColor))),
      );
    }

    final otherId = conversation.participantIds.firstWhere(
      (id) => id != dataStore.currentUser.id,
      orElse: () => '',
    );
    final otherUser = otherId.isEmpty ? null : dataStore.getUser(otherId);
    final remoteTyping = dataStore.isTypingInChat(widget.conversationId);
    final presence = conversation.type == ConversationType.direct
        ? (remoteTyping ? 'typing…' : _lastSeen(otherUser))
        : (remoteTyping ? 'someone is typing…' : '${conversation.participantIds.length} participants');
    final messages = dataStore.getMessages(widget.conversationId);
    final autoPrefs = widget.preferencesController.automation;

    Widget chat = Scaffold(
      backgroundColor: theme.backgroundColor,
      appBar: AppBar(
        automaticallyImplyLeading: false,
        backgroundColor: theme.surfaceColor,
        foregroundColor: theme.primaryTextColor,
        surfaceTintColor: Colors.transparent,
        elevation: 0.5,
        leading: IconButton(
          tooltip: 'Back',
          onPressed: () => Navigator.of(context).pop(),
          icon: const Icon(Icons.chevron_left_rounded),
        ),
        titleSpacing: 0,
        title: InkWell(
          onTap: conversation.type == ConversationType.group
              ? () {
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => GroupInfoScreen(
                        theme: theme,
                        dataStore: dataStore,
                        conversationId: widget.conversationId,
                      ),
                    ),
                  );
                }
              : null,
          child: Row(
            children: [
              AppAvatar(
                initials: conversation.avatarInitials ?? conversation.title.characters.take(2).toString().toUpperCase(),
                colorHex: conversation.avatarColorHex ?? '0xFF6366F1',
                size: 38,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      conversation.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: theme.primaryTextColor, fontSize: 15.5 * theme.fontScale, fontWeight: FontWeight.w800),
                    ),
                    Text(
                      presence,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: remoteTyping || otherUser?.presence == PresenceState.online ? theme.successColor : theme.secondaryTextColor,
                        fontSize: 10.5 * theme.fontScale,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        actions: [
          IconButton(icon: const Icon(Icons.call_rounded), tooltip: 'Voice call', onPressed: () => _startCall(false)),
          IconButton(icon: const Icon(Icons.videocam_rounded), tooltip: 'Video call', onPressed: () => _startCall(true)),
          Padding(
            padding: const EdgeInsets.only(right: 6),
            child: SecurityChip(status: conversation.encryptionStatus),
          ),
        ],
      ),
      body: SafeArea(
        top: false,
        child: Column(
          children: [
            Expanded(
              child: messages.isEmpty
                  ? Center(
                      child: Text('No messages yet', style: TextStyle(color: theme.secondaryTextColor)),
                    )
                  : ListView.builder(
                      controller: _scrollCtrl,
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      itemCount: messages.length,
                      itemBuilder: (context, index) {
                        final message = messages[index];
                        final isMine = message.senderId == dataStore.currentUser.id;
                        final bubble = MessageBubble(
                          message: message,
                          isMe: isMine,
                          theme: theme,
                          senderName: conversation.type == ConversationType.group && !isMine
                              ? _senderName(message.senderId)
                              : null,
                          onLongPress: () => _onMessageLongPress(message),
                          onReactionTap: (emoji) => dataStore.toggleReaction(conversation.id, message.id, emoji),
                          onTaskTap: message.linkedTaskId == null
                              ? null
                              : () => _openCreateTaskModal(sourceMessageId: message.id),
                          onMediaTap: message.attachment == null
                              ? null
                              : () {
                                  Navigator.of(context).push(
                                    MaterialPageRoute(
                                      builder: (_) => MediaViewerScreen(
                                        title: message.attachment!.name,
                                        type: message.attachment!.type,
                                        size: message.attachment!.size,
                                        storagePath: message.attachment!.url,
                                        theme: theme,
                                      ),
                                    ),
                                  );
                                },
                        );
                        if (!ChatAttachmentActions.isPollMessage(message)) return bubble;
                        return GestureDetector(
                          behavior: HitTestBehavior.translucent,
                          onTap: () => _attachments.openPoll(context, message.id),
                          child: bubble,
                        );
                      },
                    ),
            ),
            if (_showQuickReplyOverlay && autoPrefs.quickReplies.isNotEmpty)
              Container(
                margin: const EdgeInsets.fromLTRB(12, 4, 12, 4),
                constraints: const BoxConstraints(maxHeight: 180),
                decoration: BoxDecoration(
                  color: theme.cardColor,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: theme.surfaceColor),
                ),
                child: ListView(
                  shrinkWrap: true,
                  children: autoPrefs.quickReplies.map((reply) {
                    return ListTile(
                      dense: true,
                      title: Text(
                        '${reply.shortcut} — ${reply.title}',
                        style: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.w700, fontSize: 12.5),
                      ),
                      subtitle: Text(reply.content, maxLines: 1, overflow: TextOverflow.ellipsis),
                      onTap: () {
                        _textCtrl.text = reply.content;
                        _textCtrl.selection = TextSelection.collapsed(offset: _textCtrl.text.length);
                        _handleComposerChanged(_textCtrl.text);
                      },
                    );
                  }).toList(growable: false),
                ),
              ),
            if (_replyTarget != null)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
                color: theme.cardColor,
                child: Row(
                  children: [
                    Icon(Icons.reply_rounded, color: theme.accentColor, size: 18),
                    const SizedBox(width: 9),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Replying to ${_senderName(_replyTarget!.senderId)}',
                            style: TextStyle(color: theme.accentColor, fontSize: 11.5, fontWeight: FontWeight.w700),
                          ),
                          Text(
                            _replyTarget!.text,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(color: theme.secondaryTextColor, fontSize: 11.5),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      onPressed: () => setState(() => _replyTarget = null),
                      icon: const Icon(Icons.close_rounded, size: 18),
                    ),
                  ],
                ),
              ),
            _Composer(
              theme: theme,
              controller: _textCtrl,
              onAttach: _openAttachmentSheet,
              onSend: _sendMessage,
              onChanged: _handleComposerChanged,
              onVoice: () async {
                if (_typingPublished) {
                  _typingPublished = false;
                  widget.dataStore.setTyping(widget.conversationId, false);
                }
                await _attachments.recordVoiceNote(context);
                _scrollToBottom();
              },
            ),
          ],
        ),
      ),
    );

    final conversationPrefs = widget.preferencesController.conversation;
    if (!conversationPrefs.enableQuickContactSidebar || MediaQuery.sizeOf(context).width < 720) {
      return chat;
    }

    final contacts = dataStore.contacts;
    Widget sidebar() {
      return Container(
        width: 62,
        color: theme.surfaceColor.withValues(alpha: conversationPrefs.sidebarOpacity),
        child: ListView.builder(
          padding: const EdgeInsets.symmetric(vertical: 8),
          itemCount: contacts.length,
          itemBuilder: (context, index) {
            final contact = contacts[index];
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 5),
              child: InkWell(
                borderRadius: BorderRadius.circular(30),
                onTap: () async {
                  try {
                    final next = await dataStore.getOrCreateDirectConversation(contact);
                    if (!mounted) return;
                    Navigator.of(context).pushReplacement(
                      MaterialPageRoute(
                        builder: (_) => ChatDetailScreen(
                          conversationId: next.id,
                          theme: theme,
                          dataStore: dataStore,
                          preferencesController: widget.preferencesController,
                          themeController: widget.themeController,
                        ),
                      ),
                    );
                  } catch (_) {}
                },
                child: Center(
                  child: ChatyAvatar(
                    initials: contact.avatarInitials,
                    color: Color(int.parse(contact.avatarColorHex)),
                    size: 38,
                  ),
                ),
              ),
            );
          },
        ),
      );
    }

    return Row(
      children: [
        if (conversationPrefs.sidebarPosition == 'Left') sidebar(),
        Expanded(child: chat),
        if (conversationPrefs.sidebarPosition == 'Right') sidebar(),
      ],
    );
  }
}

class _Composer extends StatelessWidget {
  final ThemeConfig theme;
  final TextEditingController controller;
  final VoidCallback onAttach;
  final VoidCallback onSend;
  final VoidCallback onVoice;
  final ValueChanged<String> onChanged;

  const _Composer({
    required this.theme,
    required this.controller,
    required this.onAttach,
    required this.onSend,
    required this.onVoice,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(6, 7, 7, 7),
      decoration: BoxDecoration(
        color: theme.surfaceColor,
        border: Border(top: BorderSide(color: theme.cardColor)),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            IconButton(
              tooltip: 'Attach',
              color: theme.accentColor,
              onPressed: onAttach,
              icon: const Icon(Icons.add_circle_outline_rounded),
            ),
            Expanded(
              child: TextField(
                controller: controller,
                minLines: 1,
                maxLines: 5,
                onChanged: onChanged,
                style: TextStyle(color: theme.primaryTextColor, fontSize: 14 * theme.fontScale),
                decoration: InputDecoration(
                  hintText: 'Message…  /task or #reply',
                  hintStyle: TextStyle(color: theme.secondaryTextColor),
                  filled: true,
                  fillColor: theme.cardColor,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(22),
                    borderSide: BorderSide.none,
                  ),
                  isDense: true,
                  contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
                ),
              ),
            ),
            const SizedBox(width: 6),
            ValueListenableBuilder<TextEditingValue>(
              valueListenable: controller,
              builder: (context, value, _) {
                final hasText = value.text.trim().isNotEmpty;
                return IconButton.filled(
                  tooltip: hasText ? 'Send' : 'Voice note',
                  style: IconButton.styleFrom(
                    backgroundColor: theme.accentColor,
                    foregroundColor: theme.onAccentColor,
                  ),
                  onPressed: hasText ? onSend : onVoice,
                  icon: Icon(hasText ? Icons.send_rounded : Icons.mic_rounded, size: 19),
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}
