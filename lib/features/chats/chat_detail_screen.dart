import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../ui/core/formatting/chat_formatters.dart';
import '../../ui/core/theme/app_theme.dart';
import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/backend_service.dart';
import '../../data/services/chat_media_service.dart';
import '../../data/services/contact_relationship_service.dart';
import '../../data/services/rich_chat_realtime_service.dart';
import '../../data/services/voice_note_service.dart';
import '../../domain/models/chat_message.dart';
import '../../domain/models/chat_task.dart';
import '../../domain/models/contact_relationship.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/user_profile.dart';
import '../../features/camera/camera_capture_screen.dart';
import '../../features/tasks/task_detail_screen.dart';
import '../../injection/locator.dart';
import '../../ui/core/commands/chat_command_parser.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/design_system/settings_primitives.dart';
import '../../ui/core/design_system/chaty_haptics.dart';
import '../../ui/core/design_system/components/chaty_kit.dart';
import '../../ui/core/design_system/components/app_components.dart';
import '../../ui/core/design_system/components/composer_components.dart';
import '../../ui/core/gb/gb_theme_overrides.dart';
import '../../core/emoji/widgets/animated_emoji_text.dart';
import '../../ui/core/menu/app_context_menu.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/widgets/chat_wallpaper.dart';
import '../../data/services/call_signaling_service.dart';
import '../calls/ongoing_call_screen.dart';
import '../messages/attachment_sheet.dart';
import '../messages/chat_attachment_actions.dart';
import '../messages/emoji_picker_modal.dart';
import '../messages/media_viewer_screen.dart';
import '../messages/message_action_registry.dart';
import '../messages/message_bubble.dart';
import '../tasks/task_create_edit_modal.dart';
import 'contact_info_screen.dart';
import 'group_info_screen.dart';

class ChatDetailScreen extends StatefulWidget {
  final ThemeConfig theme;
  final ChatyDataStore dataStore;
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
  // View-once media the local user has already opened this session.
  // Deliberately local-only: the server never learns open state, and a fresh
  // session locks the media again (matching WhatsApp semantics).
  final Set<String> _openedViewOnceIds = <String>{};
  final TextEditingController _textCtrl = TextEditingController();
  final ScrollController _scrollCtrl = ScrollController();
  // --- WhatsApp/Telegram/Instagram scroll logic ---
  // True while the list is within [_nearBottomThreshold] of the newest
  // message. Drives the floating down-arrow button, the unseen-count badge,
  // and whether incoming messages auto-pin the list to the bottom.
  static const double _nearBottomThreshold = 250;
  bool _isNearBottom = true;
  int _pendingBelowCount = 0;
  int _lastKnownMessageCount = 0;
  late final ChatAttachmentActions _attachments;
  late final VoiceNoteService _voice;
  late final RichChatRealtimeService _realtime;
  late final ContactRelationshipService _relationships;
  Timer? _typingIdleTimer;
  Timer? _voiceTimer;
  bool _typingPublished = false;
  bool _loadingMessages = true;

  /// False until the list has laid out once and been positioned at its
  /// initial offset. The list renders fully laid-out but transparent until
  /// then, so the user never sees an off-screen frame or a visible jump.
  bool _initialPositionApplied = false;
  String? _loadError;
  ChatMessage? _replyTarget;
  bool _showQuickReplyOverlay = false;
  bool _recording = false;
  bool _recordLocked = false;
  bool _voiceBusy = false;
  int _voiceSeconds = 0;
  final TextEditingController _inChatSearchCtrl = TextEditingController();
  final FocusNode _inChatSearchFocus = FocusNode();
  bool _isInChatSearchOpen = false;
  int _currentSearchMatchIndex = 0;
  List<String> _searchMatchedMessageIds = <String>[];
  String? _highlightedSearchMessageId;
  Timer? _searchHighlightTimer;
  ContactConnectionStatus _connectionStatus = const ContactConnectionStatus();
  // Per-chat wallpaper override ('none' | theme pattern ids). Null until the
  // persisted value loads; consumed by the ChatWallpaper layer.
  String? _chatWallpaperOverride;

  String? _contextualMessageId;
  final Set<String> _selectedMessageIds = <String>{};
  String? _editingMessageId;

  bool get _isSelectionMode => _selectedMessageIds.isNotEmpty;

  ThemeConfig get _theme => GbThemeOverrides.resolve(
    widget.themeController?.globalTheme ?? widget.theme,
    widget.preferencesController,
  );

  @override
  void initState() {
    super.initState();
    _realtime = locator<RichChatRealtimeService>();
    _relationships = locator<ContactRelationshipService>();
    _attachments = ChatAttachmentActions(
      conversationId: widget.conversationId,
      dataStore: widget.dataStore,
      preferencesController: widget.preferencesController,
    );
    _voice = VoiceNoteService(
      conversationId: widget.conversationId,
      dataStore: widget.dataStore,
    );
    final conversation = widget.dataStore.conversations
        .where((item) => item.id == widget.conversationId)
        .firstOrNull;
    if (conversation != null && conversation.draftText.isNotEmpty)
      _textCtrl.text = conversation.draftText;
    unawaited(_realtime.trackConversation(widget.conversationId));
    unawaited(_loadChatWallpaperOverride());
    _scrollCtrl.addListener(_handleScrollChanged);
    widget.dataStore.addListener(_onDataStoreChanged);
    _scrollToBottom(animate: false);
    _loadConversation();
  }

  Future<void> _loadChatWallpaperOverride() async {
    final value = await widget.dataStore.loadChatWallpaper(
      widget.conversationId,
    );
    if (!mounted || value == null) return;
    setState(() => _chatWallpaperOverride = value);
  }

  Future<void> _loadConversation() async {
    // Keep _loadingMessages=true until cached/remote messages are actually
    // available — clearing it here used to show a false “No messages yet”
    // empty state while the first query was still in flight.
    if (mounted) {
      setState(() => _loadError = null);
    }
    // Offline-first: whatever is already cached renders instantly; only a
    // truly empty timeline keeps the loading state alive until the network
    // refresh lands.
    final cachedMessages = widget.dataStore.getMessages(widget.conversationId);
    if (!mounted) return;
    setState(() => _loadingMessages = cachedMessages.isEmpty);
    if (!_loadingMessages) _scrollToBottom(animate: false);
    try {
      await widget.dataStore.ensureConversationLoaded(widget.conversationId);
      await _realtime.trackConversation(widget.conversationId);
      await _realtime.markConversationDelivered(widget.conversationId);
      await _refreshConnectionStatus();
      if (!mounted) return;
      setState(() => _loadingMessages = false);
      _scrollToBottom(animate: false);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loadingMessages = false;
        _loadError = error.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  /// Whether a failure means MLS encryption is still being provisioned for
  /// this conversation — a temporary, recoverable state rather than an error.
  bool _isSecureSetupPendingError(String rawError) =>
      rawError.contains(ChatyBackendService.secureSetupPendingCode) ||
      rawError.contains('MLS group is not initialized') ||
      rawError.contains('MlsMembershipPendingException');

  Future<void> _refreshConnectionStatus() async {
    final conversation = widget.dataStore.conversations
        .where((item) => item.id == widget.conversationId)
        .firstOrNull;
    if (conversation == null || conversation.type != ConversationType.direct)
      return;
    final otherId = conversation.participantIds.firstWhere(
      (id) => id != widget.dataStore.currentUser.id,
      orElse: () => '',
    );
    if (otherId.isEmpty) return;
    try {
      final status = await _relationships.connectionStatus(otherId);
      if (mounted) setState(() => _connectionStatus = status);
    } catch (_) {}
  }

  @override
  void dispose() {
    _typingIdleTimer?.cancel();
    _voiceTimer?.cancel();
    if (_typingPublished)
      widget.dataStore.setTyping(widget.conversationId, false);
    if (_recording)
      unawaited(_realtime.setRecording(widget.conversationId, false));
    widget.dataStore.setDraft(widget.conversationId, _textCtrl.text);
    _scrollCtrl.removeListener(_handleScrollChanged);
    widget.dataStore.removeListener(_onDataStoreChanged);
    unawaited(_voice.dispose());
    _searchHighlightTimer?.cancel();
    _inChatSearchCtrl.dispose();
    _inChatSearchFocus.dispose();
    _textCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  String _senderName(String senderId) {
    if (senderId == widget.dataStore.currentUser.id) return 'You';
    return widget.dataStore.getUser(senderId)?.displayName ?? 'Chaty user';
  }

  String _lastSeen(String userId) {
    if (_realtime.isOnline(userId)) return 'online';
    return formatLastSeen(_realtime.lastSeenFor(userId)).isEmpty
        ? 'last seen hidden'
        : formatLastSeen(_realtime.lastSeenFor(userId));
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

  Future<void> _pickComposerEmoji() async {
    final emoji = await ChatyEmojiPicker.show(context);
    if (emoji == null || emoji.isEmpty || !mounted) return;
    final value = _textCtrl.value;
    final selection = value.selection;
    final start = selection.isValid ? selection.start : value.text.length;
    final end = selection.isValid ? selection.end : value.text.length;
    final nextText = value.text.replaceRange(start, end, emoji);
    _textCtrl.value = TextEditingValue(
      text: nextText,
      selection: TextSelection.collapsed(offset: start + emoji.length),
    );
    _handleComposerChanged(nextText);
  }

  Future<void> _sendMessage() async {
    final text = _textCtrl.text.trim();
    if (text.isEmpty) return;

    // --- Editing branch ---
    if (_editingMessageId != null) {
      final editId = _editingMessageId!;
      _textCtrl.clear();
      _typingIdleTimer?.cancel();
      if (_typingPublished) {
        _typingPublished = false;
        widget.dataStore.setTyping(widget.conversationId, false);
      }
      setState(() {
        _editingMessageId = null;
        _showQuickReplyOverlay = false;
      });
      try {
        await widget.dataStore.editMessage(
          conversationId: widget.conversationId,
          messageId: editId,
          newText: text,
        );
      } catch (error) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              error.toString().replaceFirst('Exception: ', ''),
            ),
          ),
        );
        // Restore so user can retry
        setState(() {
          _editingMessageId = editId;
          _textCtrl.text = text;
        });
      }
      return;
    }

    final command = ChatCommandParser.parse(text);
    if (command.type == ChatCommandType.task) {
      _textCtrl.clear();
      _typingPublished = false;
      widget.dataStore.setTyping(widget.conversationId, false);
      setState(() {});
      _openCreateTaskModal(
        initialTitle: command.argument.isEmpty ? null : command.argument,
      );
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
      await _realtime.trackConversation(widget.conversationId);
      _scrollToBottom();
    } catch (error) {
      if (!mounted) return;
      final raw = error.toString();
      if (_isSecureSetupPendingError(raw)) {
        await _realtime.trackConversation(widget.conversationId);
        _scrollToBottom();
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Message queued — sending automatically once secure setup finishes.',
            ),
          ),
        );
      } else {
        _textCtrl.text = text;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(raw.replaceFirst('Exception: ', '')),
          ),
        );
      }
      setState(() {});
    }
  }

  /// WhatsApp-style in-chat camera: full-screen capture with live effects,
  /// then upload + encrypted send with the chosen look as metadata so
  /// receivers render the same image treatment.
  Future<void> _captureAndSendPhoto() async {
    final conversation = widget.dataStore.conversations
        .where((item) => item.id == widget.conversationId)
        .firstOrNull;
    final result = await ChatyCameraCaptureScreen.open(
      context,
      mode: ChatyCaptureMode.chat,
      contactName: conversation?.title,
    );
    if (result == null || !mounted) return;
    try {
      final attachment = await ChatMediaService().uploadFile(
        conversationId: widget.conversationId,
        type: 'image',
        sourcePath: result.path,
      );
      await widget.dataStore.sendMessage(
        conversationId: widget.conversationId,
        text: result.caption,
        type: MessageType.image,
        attachment: attachment,
        extraMetadata: <String, dynamic>{
          'effect': result.effectId,
          'effect_intensity': result.effectIntensity.toString(),
        },
      );
      await _realtime.trackConversation(widget.conversationId);
      _scrollToBottom();
      ChatyHaptics.success();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Could not send photo. ${error.toString().replaceFirst('Exception: ', '')}',
          ),
        ),
      );
    }
  }

  Future<void> _beginVoice({required bool locked}) async {
    if (_voiceBusy || _recording) return;
    if (_typingPublished) {
      _typingPublished = false;
      widget.dataStore.setTyping(widget.conversationId, false);
    }
    setState(() => _voiceBusy = true);
    try {
      await _voice.start();
      await _realtime.setRecording(widget.conversationId, true);
      if (!mounted) return;
      setState(() {
        _recording = true;
        _recordLocked = locked;
        _voiceSeconds = 0;
      });
      _voiceTimer?.cancel();
      _voiceTimer = Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted) setState(() => _voiceSeconds++);
      });
    } catch (error) {
      unawaited(_realtime.setRecording(widget.conversationId, false));
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
    } finally {
      if (mounted) setState(() => _voiceBusy = false);
    }
  }

  Future<void> _finishVoice() async {
    if (!_recording || _voiceBusy) return;
    setState(() => _voiceBusy = true);
    _voiceTimer?.cancel();
    try {
      final sent = await _voice.stopAndSend();
      if (sent) {
        await _realtime.trackConversation(widget.conversationId);
        _scrollToBottom();
      }
    } catch (error) {
      if (mounted) {
        final raw = error.toString();
        final message = _isSecureSetupPendingError(raw)
            ? 'Voice note queued — it sends automatically once secure setup '
                  'finishes for this chat.'
            : 'Could not send voice note. ${raw.replaceFirst('Exception: ', '')}';
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(message)));
      }
    } finally {
      await _realtime.setRecording(widget.conversationId, false);
      if (mounted) {
        setState(() {
          _recording = false;
          _recordLocked = false;
          _voiceBusy = false;
          _voiceSeconds = 0;
        });
      }
    }
  }

  Future<void> _cancelVoice() async {
    if (!_recording || _voiceBusy) return;
    setState(() => _voiceBusy = true);
    _voiceTimer?.cancel();
    await _voice.cancel();
    await _realtime.setRecording(widget.conversationId, false);
    if (mounted) {
      setState(() {
        _recording = false;
        _recordLocked = false;
        _voiceBusy = false;
        _voiceSeconds = 0;
      });
    }
  }

  void _handleVoiceDrag(LongPressMoveUpdateDetails details) {
    if (!_recording) return;
    if (details.offsetFromOrigin.dx < -70) {
      unawaited(_cancelVoice());
      return;
    }
    if (details.offsetFromOrigin.dy < -55 && !_recordLocked)
      setState(() => _recordLocked = true);
  }

  void _handleVoiceRelease() {
    if (_recording && !_recordLocked) unawaited(_finishVoice());
  }

  void _scrollToBottom({bool animate = true}) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scrollCtrl.hasClients) return;
      if (animate) {
        _scrollCtrl.animateTo(
          _scrollCtrl.position.maxScrollExtent,
          duration: const Duration(milliseconds: 220),
          curve: Curves.easeOutCubic,
        );
        return;
      }
      _scrollCtrl.jumpTo(_scrollCtrl.position.maxScrollExtent);
      if (!_initialPositionApplied) {
        // First meaningful frame is now correct; reveal the list.
        setState(() => _initialPositionApplied = true);
      }
    });
  }

  int get _currentMessageCount =>
      widget.dataStore.getMessages(widget.conversationId).length;

  /// Scroll listener: tracks whether the newest message is on screen. This is
  /// what makes the down-arrow button appear/disappear and decides whether
  /// incoming messages auto-pin the list.
  void _handleScrollChanged() {
    if (!_scrollCtrl.hasClients) return;
    final position = _scrollCtrl.position;
    final nearBottom =
        position.maxScrollExtent - position.pixels < _nearBottomThreshold ||
        position.maxScrollExtent <= 0;
    if (nearBottom != _isNearBottom) {
      setState(() {
        _isNearBottom = nearBottom;
        // Reaching the bottom marks everything below as seen.
        if (nearBottom) {
          _pendingBelowCount = 0;
          _lastKnownMessageCount = _currentMessageCount;
        }
      });
    } else if (nearBottom && _pendingBelowCount != 0 && mounted) {
      setState(() => _pendingBelowCount = 0);
    }
  }

  /// Store listener implementing the shared WhatsApp/Telegram/Instagram
  /// arrival rule:
  /// - message arrives while pinned to bottom -> keep the list pinned;
  /// - message arrives while scrolled away -> DON'T move the list, count it
  ///   in the badge on the floating down-arrow instead;
  /// - own outgoing messages always scroll down (handled by _sendMessage).
  void _onDataStoreChanged() {
    final count = _currentMessageCount;
    final previous = _lastKnownMessageCount;
    if (count == previous) return;
    _lastKnownMessageCount = count;
    final grew = count > previous;
    if (!mounted) return;
    if (!grew) {
      if (_pendingBelowCount != 0) setState(() => _pendingBelowCount = 0);
      return;
    }
    if (_isNearBottom) {
      _scrollToBottom();
      if (_pendingBelowCount != 0) setState(() => _pendingBelowCount = 0);
    } else {
      setState(() => _pendingBelowCount += count - previous);
    }
  }


  static const List<String> _quickReactionEmojis = ['❤️', '👍', '😂', '😮', '😢', '🙏'];

  void _onMessageLongPressWithRect(ChatMessage message, Rect bubbleRect) {
    HapticFeedback.selectionClick();
    setState(() => _contextualMessageId = message.id);
    final theme = _theme;
    final isMine = message.senderId == widget.dataStore.currentUser.id;

    final actions = MessageActionRegistry.getAvailableActions(
      message: message,
      isMe: isMine,
    );

    final sections = <ContextMenuSection>[
      ContextMenuSection(
        items: actions.map((act) {
          return ContextMenuItem(
            icon: act.icon,
            label: act.label,
            isDestructive: act.isDestructive,
            onTap: () => _executeMessageAction(message, act.type, isMine),
          );
        }).toList(growable: false),
      ),
    ];

    AppContextMenu.showWithReactionRail(
      context: context,
      anchorRect: bubbleRect,
      backgroundColor: theme.surfaceColor,
      primaryTextColor: theme.primaryTextColor,
      secondaryTextColor: theme.secondaryTextColor,
      destructiveColor: theme.dangerColor,
      sections: sections,
      quickReactions: _quickReactionEmojis,
      onQuickReaction: (emoji) {
        widget.dataStore.toggleReaction(
          widget.conversationId,
          message.id,
          emoji,
        );
      },
      onAddReaction: () async {
        final emoji = await ChatyEmojiPicker.show(context, reactionMode: true);
        if (emoji != null && emoji.isNotEmpty && mounted) {
          widget.dataStore.toggleReaction(
            widget.conversationId,
            message.id,
            emoji,
          );
        }
      },
    ).then((_) {
      if (mounted) {
        setState(() => _contextualMessageId = null);
      }
    });
  }

  void _showReactionDetailsSheet(ChatMessage message, MessageReaction reaction) {
    final theme = _theme;
    final currentUserId = widget.dataStore.currentUser.id;
    final hasMyReaction = reaction.userIds.contains(currentUserId);

    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) => SafeArea(
        child: Container(
          margin: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          decoration: BoxDecoration(
            color: theme.surfaceColor,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: theme.secondaryTextColor.withValues(alpha: 0.15),
            ),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 36,
                height: 4,
                margin: const EdgeInsets.only(top: 10, bottom: 6),
                decoration: BoxDecoration(
                  color: theme.secondaryTextColor.withValues(alpha: 0.3),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
                child: Row(
                  children: [
                    Text(
                      reaction.emoji,
                      style: const TextStyle(fontSize: 28),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Reactions',
                            style: TextStyle(
                              color: theme.primaryTextColor,
                              fontWeight: FontWeight.w700,
                              fontSize: 16,
                            ),
                          ),
                          Text(
                            '${reaction.userIds.length} ${reaction.userIds.length == 1 ? 'person' : 'people'} reacted',
                            style: TextStyle(
                              color: theme.secondaryTextColor,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close_rounded),
                      iconSize: 20,
                      color: theme.secondaryTextColor,
                      onPressed: () => Navigator.of(sheetContext).pop(),
                    ),
                  ],
                ),
              ),
              Divider(
                height: 1,
                color: theme.secondaryTextColor.withValues(alpha: 0.12),
              ),
              Flexible(
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: reaction.userIds.length,
                  itemBuilder: (context, i) {
                    final uid = reaction.userIds[i];
                    final isMe = uid == currentUserId;
                    final user = widget.dataStore.getUser(uid);
                    final displayName = isMe
                        ? '${widget.dataStore.currentUser.displayName} (You)'
                        : (user?.displayName ?? 'User');

                    return ListTile(
                      leading: ChatyNetworkAvatar(
                        initials: isMe
                            ? widget.dataStore.currentUser.avatarInitials
                            : (user?.avatarInitials ?? 'U'),
                        colorHex: isMe
                            ? widget.dataStore.currentUser.avatarColorHex
                            : (user?.avatarColorHex ?? '#6366F1'),
                        url: isMe
                            ? widget.dataStore.currentUser.avatarUrl
                            : user?.avatarUrl,
                        size: 38,
                      ),
                      title: Text(
                        displayName,
                        style: TextStyle(
                          color: theme.primaryTextColor,
                          fontWeight: FontWeight.w600,
                          fontSize: 14.5,
                        ),
                      ),
                      subtitle: isMe
                          ? Text(
                              'Tap to remove reaction',
                              style: TextStyle(
                                color: theme.dangerColor,
                                fontSize: 11.5,
                                fontWeight: FontWeight.w500,
                              ),
                            )
                          : null,
                      trailing: Text(
                        reaction.emoji,
                        style: const TextStyle(fontSize: 20),
                      ),
                      onTap: isMe
                          ? () {
                              Navigator.of(sheetContext).pop();
                              widget.dataStore.toggleReaction(
                                widget.conversationId,
                                message.id,
                                reaction.emoji,
                              );
                            }
                          : null,
                    );
                  },
                ),
              ),
              if (hasMyReaction) ...[
                Divider(
                  height: 1,
                  color: theme.secondaryTextColor.withValues(alpha: 0.12),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: SizedBox(
                    width: double.infinity,
                    child: TextButton.icon(
                      onPressed: () {
                        Navigator.of(sheetContext).pop();
                        widget.dataStore.toggleReaction(
                          widget.conversationId,
                          message.id,
                          reaction.emoji,
                        );
                      },
                      icon: Icon(Icons.remove_circle_outline_rounded,
                          color: theme.dangerColor, size: 18),
                      label: Text(
                        'Tap to remove your reaction',
                        style: TextStyle(
                          color: theme.dangerColor,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 6),
            ],
          ),
        ),
      ),
    );
  }

  void _executeMessageAction(ChatMessage message, MessageActionType type, bool isMine) async {
    switch (type) {
      case MessageActionType.reply:
        setState(() => _replyTarget = message);
        break;
      case MessageActionType.copy:
        await Clipboard.setData(ClipboardData(text: message.text));
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Message copied')),
          );
        }
        break;
      case MessageActionType.forward:
        _openForwardSheet(message);
        break;
      case MessageActionType.star:
        widget.dataStore.toggleStarMessage(widget.conversationId, message.id);
        break;
      case MessageActionType.pin:
        widget.dataStore.togglePinMessage(widget.conversationId, message.id);
        break;
      case MessageActionType.edit:
        setState(() => _editingMessageId = message.id);
        _textCtrl.text = message.text;
        _textCtrl.selection = TextSelection.collapsed(offset: message.text.length);
        _handleComposerChanged(message.text);
        break;
      case MessageActionType.task:
        _openCreateTaskModal(
          initialTitle: message.text,
          sourceMessageId: message.id,
        );
        break;
      case MessageActionType.translate:
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Translation: ${message.text}'),
            ),
          );
        }
        break;
      case MessageActionType.select:
        // Enter multi-select mode
        setState(() => _selectedMessageIds.add(message.id));
        break;
      case MessageActionType.deleteForMe:
        widget.dataStore.deleteMessage(
          widget.conversationId,
          message.id,
          forEveryone: false,
        );
        break;
      case MessageActionType.deleteForEveryone:
        if (isMine) {
          widget.dataStore.deleteMessage(
            widget.conversationId,
            message.id,
            forEveryone: true,
          );
        }
        break;
      case MessageActionType.report:
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Use Contact info → Block to stop unwanted messages.'),
            ),
          );
        }
        break;
      default:
        break;
    }
  }

  void _onMessageLongPress(ChatMessage message) {
    _onMessageLongPressWithRect(
      message,
      Rect.fromCenter(
        center: Offset(
          MediaQuery.of(context).size.width / 2,
          MediaQuery.of(context).size.height / 2,
        ),
        width: 100,
        height: 40,
      ),
    );
  }

  void _openCreateTaskModal({
    String? initialTitle,
    String? sourceMessageId,
    ChatTask? existingTask,
  }) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => TaskCreateEditModal(
        theme: _theme,
        dataStore: widget.dataStore,
        sourceConversationId: widget.conversationId,
        initialTitle: initialTitle,
        sourceMessageId: sourceMessageId,
        existingTask: existingTask,
      ),
    );
  }

  void _openTaskDetails(ChatTask task) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => TaskDetailScreen(
          task: task,
          theme: _theme,
          dataStore: widget.dataStore,
        ),
      ),
    );
  }

  void _toggleTaskStatus(ChatTask task) {
    final nextStatus = task.status == TaskStatus.completed
        ? TaskStatus.inbox
        : TaskStatus.completed;
    HapticFeedback.lightImpact();
    widget.dataStore.updateTaskStatus(task.id, nextStatus);
  }

  void _showTaskContextMenu(ChatTask task, Rect anchorRect) {
    final theme = _theme;
    final isCompleted = task.status == TaskStatus.completed;

    final sections = <ContextMenuSection>[
      ContextMenuSection(
        title: task.title,
        items: [
          ContextMenuItem(
            icon: isCompleted ? Icons.replay_rounded : Icons.check_circle_outline_rounded,
            label: isCompleted ? 'Reopen task' : 'Mark completed',
            onTap: () => _toggleTaskStatus(task),
          ),
          ContextMenuItem(
            icon: Icons.edit_outlined,
            label: 'Edit task',
            onTap: () => _openCreateTaskModal(existingTask: task),
          ),
          ContextMenuItem(
            icon: Icons.open_in_new_rounded,
            label: 'Task details',
            onTap: () => _openTaskDetails(task),
          ),
          if (task.sourceMessageId != null)
            ContextMenuItem(
              icon: Icons.chat_bubble_outline_rounded,
              label: 'Go to source message',
              onTap: () => _highlightMessage(task.sourceMessageId!),
            ),
          ContextMenuItem(
            icon: Icons.delete_outline_rounded,
            label: 'Delete task',
            isDestructive: true,
            onTap: () => widget.dataStore.deleteTask(task.id),
          ),
        ],
      ),
    ];

    AppContextMenu.show(
      context: context,
      anchorRect: anchorRect,
      backgroundColor: theme.surfaceColor,
      primaryTextColor: theme.primaryTextColor,
      secondaryTextColor: theme.secondaryTextColor,
      destructiveColor: theme.dangerColor,
      sections: sections,
    );
  }

  void _openAttachmentSheet() {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => AttachmentSheet(
        theme: _theme,
        onMediaRequested: (type) async {
          await _attachments.shareMedia(context, type);
          await _realtime.trackConversation(widget.conversationId);
          _scrollToBottom();
        },
        onLocationRequested: () async {
          await _attachments.shareLocation(context);
          await _realtime.trackConversation(widget.conversationId);
          _scrollToBottom();
        },
        onContactRequested: () async {
          await _attachments.shareContact(context);
          await _realtime.trackConversation(widget.conversationId);
          _scrollToBottom();
        },
        onPollRequested: () async {
          await _attachments.createPoll(context);
          await _realtime.trackConversation(widget.conversationId);
          _scrollToBottom();
        },
        onTaskOption: _openCreateTaskModal,
      ),
    );
  }

  Future<void> _openContactInfo(
    Conversation conversation,
    UserProfile contact,
  ) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ContactInfoScreen(
          theme: _theme,
          dataStore: widget.dataStore,
          conversation: conversation,
          contact: contact,
          relationshipService: _relationships,
          realtimeService: _realtime,
        ),
      ),
    );
    await _refreshConnectionStatus();
  }

  Future<void> _showConnectionGate(UserProfile contact) async {
    final status = await _relationships.connectionStatus(contact.id);
    if (!mounted) return;
    setState(() => _connectionStatus = status);
    final result = await showModalBottomSheet<bool>(
      context: context,
      useSafeArea: true,
      showDragHandle: true,
      builder: (context) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 2, 20, 22),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              status.callsAllowed
                  ? Icons.verified_user_rounded
                  : Icons.person_add_alt_1_rounded,
              size: 46,
              color: status.callsAllowed
                  ? _theme.successColor
                  : _theme.accentColor,
            ),
            const SizedBox(height: 10),
            Text(
              status.callsAllowed
                  ? 'Calls are unlocked'
                  : 'Contact acceptance required',
              style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 7),
            Text(
              status.callsAllowed
                  ? 'Both people accepted this contact connection.'
                  : status.isWaitingForOther
                  ? 'You accepted ${contact.displayName}. Calls will unlock after they accept you too.'
                  : 'Chatting is available now. Voice and video calls unlock only after both people accept the contact request.',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 18),
            if (!status.myAccepted)
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: () => Navigator.pop(context, true),
                  icon: const Icon(Icons.person_add_alt_1_rounded),
                  label: const Text('Accept contact'),
                ),
              )
            else
              SizedBox(
                width: double.infinity,
                child: FilledButton.tonal(
                  onPressed: () => Navigator.pop(context, false),
                  child: const Text('Done'),
                ),
              ),
          ],
        ),
      ),
    );
    if (result == true) {
      try {
        await _relationships.acceptConnection(contact.id);
        await _refreshConnectionStatus();
      } catch (error) {
        if (mounted)
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  Future<void> _startCall(bool isVideo) async {
    final conversation = widget.dataStore.conversations
        .where((item) => item.id == widget.conversationId)
        .firstOrNull;
    if (conversation == null || conversation.type != ConversationType.direct) {
      if (mounted)
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Direct contact calls are available for one-to-one chats.',
            ),
          ),
        );
      return;
    }
    final otherId = conversation.participantIds.firstWhere(
      (id) => id != widget.dataStore.currentUser.id,
      orElse: () => '',
    );
    final contact = otherId.isEmpty ? null : widget.dataStore.getUser(otherId);
    if (contact == null) return;
    final status = await _relationships.connectionStatus(contact.id);
    if (!mounted) return;
    setState(() => _connectionStatus = status);
    if (!status.callsAllowed) {
      await _showConnectionGate(contact);
      return;
    }

    final callService = locator<CallSignalingService>();
    try {
      await callService.initiateCall(
        remoteUserId: contact.id,
        remoteDisplayName: conversation.title,
        remoteAvatarInitials: contact.avatarInitials,
        remoteAvatarColorHex: contact.avatarColorHex,
        isVideo: isVideo,
      );
      if (!mounted) {
        await callService.endCall(reason: 'caller_screen_disposed');
        return;
      }
      await Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => OngoingCallScreen(theme: _theme)),
      );
    } catch (error) {
      if (!mounted) return;
      final reason = error
          .toString()
          .replaceFirst('StateError: ', '')
          .replaceFirst('Exception: ', '');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Unable to start ${isVideo ? 'video' : 'voice'} call: $reason',
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[
        widget.dataStore,
        _realtime,
        widget.preferencesController,
      ]),
      builder: (context, _) => _buildChat(context),
    );
  }

  Widget _buildChat(BuildContext context) {
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
          leading: const Padding(
            padding: EdgeInsets.all(6.0),
            child: ChatyBackButton(),
          ),
          title: const Text('Chat'),
        ),
        body: _loadingMessages
            ? const Center(child: CircularProgressIndicator())
            : Center(
                child: Text(
                  'Conversation unavailable',
                  style: TextStyle(color: theme.secondaryTextColor),
                ),
              ),
      );
    }

    final otherId = conversation.participantIds.firstWhere(
      (id) => id != dataStore.currentUser.id,
      orElse: () => '',
    );
    final otherUser = otherId.isEmpty ? null : dataStore.getUser(otherId);
    final activity = otherId.isEmpty
        ? ContactActivityState(
            updatedAt: DateTime.fromMillisecondsSinceEpoch(0),
          )
        : _realtime.activityFor(widget.conversationId, otherId);
    final remoteTyping =
        activity.isTyping ||
        (conversation.type == ConversationType.group &&
            dataStore.isTypingInChat(widget.conversationId));
    final remoteRecording = activity.isRecording;
    final isOnline = otherId.isNotEmpty && _realtime.isOnline(otherId);
    final presence = conversation.type == ConversationType.direct
        ? (remoteRecording
              ? 'recording voice message…'
              : remoteTyping
              ? 'typing…'
              : otherId.isEmpty
              ? ''
              : _lastSeen(otherId))
        : (remoteTyping
              ? 'someone is typing…'
              : '${conversation.participantIds.length} participants');
    final messages = dataStore
        .getMessages(widget.conversationId)
        .map(_realtime.hydrateMessage)
        .toList(growable: false);
    final autoPrefs = widget.preferencesController.automation;
    // Real consumers for the 'Conversation header' GB controls. All default
    // to the current (visible) behavior and hide when explicitly disabled.
    final showHeaderAvatar = widget.preferencesController.gbBool(
      'PicProf',
      fallback: true,
    );
    final showHeaderName = widget.preferencesController.gbBool(
      'NameProf',
      fallback: true,
    );
    final showHeaderCalls = widget.preferencesController.gbBool(
      'Conv_call_btn',
      fallback: true,
    );
    final showPresenceLine = widget.preferencesController.gbBool(
      'statuschat',
      fallback: true,
    );
    final presencePillColor = widget.preferencesController.gbColor(
      'ModChatGStatusB',
    );
    final presenceTextColor = widget.preferencesController.gbColor(
      'ModChatGStatusT',
    );
    final showDeleted =
        widget.preferencesController.privacy.antiDeleteMessages ||
        widget.preferencesController.gbBool('yoAntiRevoke');

    Widget chat = Scaffold(
      backgroundColor: theme.backgroundColor,
      appBar: _isSelectionMode
          ? AppBar(
              automaticallyImplyLeading: false,
              backgroundColor: theme.surfaceColor,
              foregroundColor: theme.primaryTextColor,
              surfaceTintColor: Colors.transparent,
              elevation: 0.5,
              leading: IconButton(
                icon: const Icon(Icons.close_rounded),
                tooltip: 'Cancel selection',
                onPressed: () => setState(() => _selectedMessageIds.clear()),
              ),
              title: Text(
                '${_selectedMessageIds.length} selected',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontWeight: FontWeight.w700,
                  fontSize: 16,
                ),
              ),
              actions: [
                IconButton(
                  icon: const Icon(Icons.copy_outlined),
                  tooltip: 'Copy',
                  onPressed: () async {
                    final msgs = widget.dataStore
                        .getMessages(widget.conversationId)
                        .where((m) => _selectedMessageIds.contains(m.id))
                        .map((m) => m.text)
                        .join('\n');
                    await Clipboard.setData(ClipboardData(text: msgs));
                    if (!mounted) return;
                    setState(() => _selectedMessageIds.clear());
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Messages copied')),
                    );
                  },
                ),
                IconButton(
                  icon: const Icon(Icons.forward_outlined),
                  tooltip: 'Forward',
                  onPressed: () {
                    final first = widget.dataStore
                        .getMessages(widget.conversationId)
                        .where((m) => _selectedMessageIds.contains(m.id))
                        .firstOrNull;
                    if (first != null) _openForwardSheet(first);
                    setState(() => _selectedMessageIds.clear());
                  },
                ),
                IconButton(
                  icon: const Icon(Icons.star_border_rounded),
                  tooltip: 'Star',
                  onPressed: () {
                    for (final id in _selectedMessageIds) {
                      widget.dataStore.toggleStarMessage(widget.conversationId, id);
                    }
                    setState(() => _selectedMessageIds.clear());
                  },
                ),
                IconButton(
                  icon: Icon(Icons.delete_outline_rounded, color: theme.dangerColor),
                  tooltip: 'Delete',
                  onPressed: () {
                    for (final id in _selectedMessageIds) {
                      widget.dataStore.deleteMessage(
                        widget.conversationId,
                        id,
                        forEveryone: false,
                      );
                    }
                    setState(() => _selectedMessageIds.clear());
                  },
                ),
              ],
            )
          : _isInChatSearchOpen
          ? AppBar(
              automaticallyImplyLeading: false,
              backgroundColor: theme.surfaceColor,
              foregroundColor: theme.primaryTextColor,
              surfaceTintColor: Colors.transparent,
              elevation: 0.5,
              leading: IconButton(
                icon: const Icon(Icons.arrow_back_rounded),
                onPressed: _toggleInChatSearch,
              ),
              title: TextField(
                controller: _inChatSearchCtrl,
                focusNode: _inChatSearchFocus,
                onChanged: _performInChatSearch,
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 15.5 * theme.fontScale,
                ),
                decoration: InputDecoration(
                  hintText: 'Search conversation…',
                  hintStyle: TextStyle(color: theme.secondaryTextColor),
                  border: InputBorder.none,
                ),
              ),
              actions: [
                if (_searchMatchedMessageIds.isNotEmpty)
                  Center(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 6),
                      child: Text(
                        '$_currentSearchMatchIndex of ${_searchMatchedMessageIds.length}',
                        style: TextStyle(
                          color: theme.secondaryTextColor,
                          fontSize: 12.5,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
                if (_searchMatchedMessageIds.isNotEmpty)
                  IconButton(
                    icon: const Icon(Icons.keyboard_arrow_up_rounded, size: 22),
                    tooltip: 'Previous match',
                    onPressed: () => _jumpToSearchMatch(_currentSearchMatchIndex - 1),
                  ),
                if (_searchMatchedMessageIds.isNotEmpty)
                  IconButton(
                    icon: const Icon(Icons.keyboard_arrow_down_rounded, size: 22),
                    tooltip: 'Next match',
                    onPressed: () => _jumpToSearchMatch(_currentSearchMatchIndex + 1),
                  ),
                IconButton(
                  icon: const Icon(Icons.close_rounded),
                  tooltip: 'Clear search',
                  onPressed: () {
                    if (_inChatSearchCtrl.text.isNotEmpty) {
                      _inChatSearchCtrl.clear();
                      _performInChatSearch('');
                    } else {
                      _toggleInChatSearch();
                    }
                  },
                ),
              ],
            )
          : AppBar(
              automaticallyImplyLeading: false,
              backgroundColor: theme.surfaceColor,
              foregroundColor: theme.primaryTextColor,
              surfaceTintColor: Colors.transparent,
              elevation: 0.5,
              leading: const Padding(
                padding: EdgeInsets.all(6.0),
                child: ChatyBackButton(),
              ),
              titleSpacing: 4,
              title: InkWell(
                borderRadius: BorderRadius.circular(12),
                onTap: conversation.type == ConversationType.group
                    ? () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => GroupInfoScreen(
                            theme: theme,
                            dataStore: dataStore,
                            conversationId: widget.conversationId,
                          ),
                        ),
                      )
                    : otherUser == null
                    ? null
                    : () => _openContactInfo(conversation, otherUser),
                child: Row(
                  children: [
                    if (showHeaderAvatar)
                      Stack(
                        clipBehavior: Clip.none,
                        children: [
                          AppAvatar(
                            initials:
                                conversation.avatarInitials ??
                                conversation.title.characters
                                    .take(2)
                                    .toString()
                                    .toUpperCase(),
                            colorHex: conversation.avatarColorHex ?? '0xFF6366F1',
                            size: 38,
                          ),
                          if (conversation.type == ConversationType.direct &&
                              isOnline)
                            Positioned(
                              right: -1,
                              bottom: -1,
                              child: ChatyOnlineDot(
                                active: true,
                                avatarSize: 38,
                                color: theme.successColor,
                                ringColor: theme.surfaceColor,
                              ),
                            ),
                        ],
                      ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (showHeaderName)
                            Text(
                              conversation.title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                color: theme.primaryTextColor,
                                fontSize: 15.5 * theme.fontScale,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          if (showPresenceLine)
                            Container(
                              padding: presencePillColor == null
                                  ? EdgeInsets.zero
                                  : const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 2,
                                    ),
                              margin: presencePillColor == null
                                  ? EdgeInsets.zero
                                  : const EdgeInsets.only(top: 2),
                              decoration: BoxDecoration(
                                color: presencePillColor,
                                borderRadius: BorderRadius.circular(999),
                              ),
                              child: Text(
                                presence,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  color:
                                      presenceTextColor ??
                                      (remoteTyping || remoteRecording || isOnline
                                          ? theme.successColor
                                          : theme.secondaryTextColor),
                                  fontSize: 10.5 * theme.fontScale,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              actions: [
                if (showHeaderCalls)
                  IconButton(
                    icon: Icon(
                      Icons.call_rounded,
                      color: _connectionStatus.callsAllowed
                          ? null
                          : theme.secondaryTextColor,
                    ),
                    tooltip: _connectionStatus.callsAllowed
                        ? 'Voice call'
                        : 'Voice call • contact acceptance required',
                    onPressed: () => _startCall(false),
                  ),
                if (showHeaderCalls)
                  IconButton(
                    icon: Icon(
                      Icons.video_camera_front_rounded,
                      color: _connectionStatus.callsAllowed
                          ? null
                          : theme.secondaryTextColor,
                    ),
                    tooltip: _connectionStatus.callsAllowed
                        ? 'Video call'
                        : 'Video call • contact acceptance required',
                    onPressed: () => _startCall(true),
                  ),
                // Chat overflow menu: wallpaper, mute, clear, block and more.
                Builder(
                  builder: (btnCtx) => IconButton(
                    tooltip: 'Chat options',
                    icon: Icon(Icons.more_vert_rounded, color: theme.primaryTextColor),
                    onPressed: () => _openChatMenu(btnCtx, conversation, otherUser),
                  ),
                ),
              ],
            ),
      body: SafeArea(
        top: false,
        child: Column(
          children: [
            if (!_realtime.isConnected)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(
                  vertical: 4,
                  horizontal: 12,
                ),
                color: context.colors.warning.withValues(alpha: 0.15),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.wifi_off_rounded,
                      size: 14,
                      color: context.colors.warning,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      'No internet connection • Working offline with cached media',
                      style: TextStyle(
                        fontSize: 11.5 * theme.fontScale,
                        fontWeight: FontWeight.w600,
                        color: context.colors.foreground,
                      ),
                    ),
                  ],
                ),
              ),
            Expanded(
              // Wallpaper layer sits behind the transparent message list.
              child: ClipRect(
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    ChatWallpaper(
                      theme: theme,
                      wallpaperType: widget
                          .preferencesController
                          .conversation
                          .wallpaperType,
                      // Per-chat override wins over the theme pattern.
                      patternId: _chatWallpaperOverride ?? theme.wallpaperId,
                      profileColorHex:
                          conversation.avatarColorHex ??
                          otherUser?.avatarColorHex,
                      imagePath: widget
                          .preferencesController
                          .conversation
                          .wallpaperPath,
                    ),
                    _messagesBody(theme, conversation, messages, showDeleted),
                    // Floating scroll-to-bottom arrow with unseen count —
                    // hidden while pinned to bottom or when there is nothing
                    // to scroll to.
                    Positioned(
                      right: 14,
                      bottom: 12,
                      child: IgnorePointer(
                        ignoring: _isNearBottom || messages.isEmpty,
                        child: AnimatedScale(
                          scale: _isNearBottom || messages.isEmpty ? 0.4 : 1.0,
                          duration: const Duration(milliseconds: 160),
                          curve: Curves.easeOutBack,
                          child: AnimatedOpacity(
                            opacity: _isNearBottom || messages.isEmpty
                                ? 0.0
                                : 1.0,
                            duration: const Duration(milliseconds: 140),
                            child: Material(
                              color: Colors.transparent,
                              child: InkWell(
                                borderRadius: BorderRadius.circular(23),
                                onTap: () {
                                  _scrollToBottom();
                                  setState(() => _pendingBelowCount = 0);
                                },
                                child: Container(
                                  width: 44,
                                  height: 44,
                                  decoration: BoxDecoration(
                                    color: theme.cardColor,
                                    shape: BoxShape.circle,
                                    border: Border.all(
                                      color: theme.surfaceColor,
                                      width: 1,
                                    ),
                                    boxShadow: <BoxShadow>[
                                      BoxShadow(
                                        color: Colors.black.withValues(
                                          alpha: 0.18,
                                        ),
                                        blurRadius: 10,
                                        offset: const Offset(0, 3),
                                      ),
                                    ],
                                  ),
                                  child: Stack(
                                    clipBehavior: Clip.none,
                                    children: [
                                      Center(
                                        child: Icon(
                                          Icons.keyboard_arrow_down_rounded,
                                          size: 27,
                                          color: theme.primaryTextColor,
                                        ),
                                      ),
                                      if (_pendingBelowCount > 0)
                                        Positioned(
                                          top: -4,
                                          right: -5,
                                          child: ChatyCountBadge(
                                            count: _pendingBelowCount,
                                            color: theme.accentColor,
                                            textColor: theme.onAccentColor,
                                          ),
                                        ),
                                    ],
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            if (_showQuickReplyOverlay &&
                autoPrefs.quickReplies.isNotEmpty &&
                !_recording)
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
                  children: autoPrefs.quickReplies
                      .map(
                        (reply) => ListTile(
                          dense: true,
                          title: Text(
                            '${reply.shortcut} — ${reply.title}',
                            style: TextStyle(
                              color: theme.primaryTextColor,
                              fontWeight: FontWeight.w700,
                              fontSize: 12.5,
                            ),
                          ),
                          subtitle: Text(
                            reply.content,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          onTap: () {
                            _textCtrl.text = reply.content;
                            _textCtrl.selection = TextSelection.collapsed(
                              offset: _textCtrl.text.length,
                            );
                            _handleComposerChanged(_textCtrl.text);
                          },
                        ),
                      )
                      .toList(growable: false),
                ),
              ),
            if (_editingMessageId != null && !_recording)
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 7,
                ),
                color: _theme.cardColor,
                child: Row(
                  children: [
                    Icon(
                      Icons.edit_rounded,
                      color: _theme.accentColor,
                      size: 18,
                    ),
                    const SizedBox(width: 9),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Editing message',
                            style: TextStyle(
                              color: _theme.accentColor,
                              fontSize: 11.5,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          Text(
                            widget.dataStore
                                    .getMessages(widget.conversationId)
                                    .where((m) => m.id == _editingMessageId)
                                    .map((m) => m.text)
                                    .firstOrNull ??
                                '',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: _theme.secondaryTextColor,
                              fontSize: 11.5,
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      onPressed: () => setState(() {
                        _editingMessageId = null;
                        _textCtrl.clear();
                        _handleComposerChanged('');
                      }),
                      icon: const Icon(Icons.close_rounded, size: 18),
                    ),
                  ],
                ),
              ),
            if (_replyTarget != null && !_recording)
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 7,
                ),
                color: theme.cardColor,
                child: Row(
                  children: [
                    Icon(
                      Icons.reply_rounded,
                      color: theme.accentColor,
                      size: 18,
                    ),
                    const SizedBox(width: 9),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Replying to ${_senderName(_replyTarget!.senderId)}',
                            style: TextStyle(
                              color: theme.accentColor,
                              fontSize: 11.5,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          AnimatedEmojiText(
                            text: _replyTarget!.text,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: theme.secondaryTextColor,
                              fontSize: 11.5,
                            ),
                            enableExpressiveSizing: false,
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
              onCameraTap: _captureAndSendPhoto,
              onEmoji: _pickComposerEmoji,
              onSend: _sendMessage,
              onChanged: _handleComposerChanged,
              recording: _recording,
              recordLocked: _recordLocked,
              voiceBusy: _voiceBusy,
              voiceSeconds: _voiceSeconds,
              onVoiceTap: () => _beginVoice(locked: true),
              onVoiceHoldStart: () => _beginVoice(locked: false),
              onVoiceMove: _handleVoiceDrag,
              onVoiceHoldEnd: _handleVoiceRelease,
              onVoiceCancel: _cancelVoice,
              onVoiceSend: _finishVoice,
              amplitudeProvider: _voice.currentLevel,
            ),
          ],
        ),
      ),
    );

    final conversationPrefs = widget.preferencesController.conversation;
    if (!conversationPrefs.enableQuickContactSidebar ||
        MediaQuery.sizeOf(context).width < 720)
      return chat;
    final contacts = dataStore.contacts;
    Widget sidebar() => Container(
      width: 62,
      color: theme.surfaceColor.withValues(
        alpha: conversationPrefs.sidebarOpacity,
      ),
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
                  final next = await dataStore.getOrCreateDirectConversation(
                    contact,
                  );
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
                  shape: widget.preferencesController.home.avatarShape,
                ),
              ),
            ),
          );
        },
      ),
    );
    return Row(
      children: [
        if (conversationPrefs.sidebarPosition == 'Left') sidebar(),
        Expanded(child: chat),
        if (conversationPrefs.sidebarPosition == 'Right') sidebar(),
      ],
    );
  }

  /// Opens a locked view-once message: marks it opened locally (so the
  /// bubble flips to retained/expired per Anti View-Once) and shows media.
  void _openViewOnceMedia(ChatMessage message, ThemeConfig theme) {
    setState(() => _openedViewOnceIds.add(message.id));
    final attachment = message.attachment;
    if (attachment == null) return;
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => MediaViewerScreen(
          title: attachment.name,
          type: attachment.type,
          size: attachment.size,
          storagePath: attachment.url,
          theme: theme,
        ),
      ),
    );
  }

  /// Real consumer for the Disable Forwarded Label setting: the flag is
  /// resolved at SEND time (`privacy.disableForwardedLabel` OR GB toggle) and
  /// baked into the forwarded copy's metadata.
  Future<void> _openForwardSheet(ChatMessage message) async {
    final theme = _theme;
    final targets = widget.dataStore.conversations
        .where((c) => c.id != widget.conversationId)
        .toList(growable: false);
    if (!mounted) return;
    if (targets.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No other chats to forward to.')),
      );
      return;
    }
    final target = await showModalBottomSheet<Conversation>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: Container(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.of(sheetContext).size.height * 0.7,
          ),
          decoration: BoxDecoration(
            color: theme.surfaceColor,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(18)),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const SizedBox(height: 14),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  children: [
                    Icon(Icons.shortcut_rounded, color: theme.accentColor),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Forward to',
                        style: TextStyle(
                          color: theme.primaryTextColor,
                          fontWeight: FontWeight.w800,
                          fontSize: 15,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 6),
              Flexible(
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: targets.length,
                  itemBuilder: (context, index) {
                    final conversation = targets[index];
                    return ListTile(
                      leading: AppAvatar(
                        initials:
                            conversation.avatarInitials ??
                            conversation.title.characters
                                .take(2)
                                .toString()
                                .toUpperCase(),
                        colorHex: conversation.avatarColorHex ?? '0xFF6366F1',
                        size: 36,
                      ),
                      title: Text(
                        conversation.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(color: theme.primaryTextColor),
                      ),
                      onTap: () => Navigator.pop(sheetContext, conversation),
                    );
                  },
                ),
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
    if (target == null || !mounted) return;
    final labelAllowed =
        !(widget.preferencesController.privacy.disableForwardedLabel ||
            widget.preferencesController.gbBool('yoDisableFwd'));
    try {
      await widget.dataStore.sendMessage(
        conversationId: target.id,
        text: message.type == MessageType.system ? '' : message.text,
        type: message.type == MessageType.system
            ? MessageType.text
            : message.type,
        attachment: message.attachment,
        extraMetadata: <String, dynamic>{'forwarded': labelAllowed},
      );
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Forwarded to ${target.title}.')));
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Could not forward: $error')));
    }
  }

  // ---------------------------------------------------------------------------
  // Chat overflow menu (3-dots in the header): everything below is REAL.
  // ---------------------------------------------------------------------------
  void _openChatMenu(BuildContext anchorContext, Conversation conversation, UserProfile? otherUser) {
    final isDirect =
        conversation.type == ConversationType.direct && otherUser != null;
    ChatyMenuSheet.show(
      anchorContext,
      title: conversation.title,
      items: [
        ChatyMenuItem(
          icon: isDirect
              ? Icons.person_outline_rounded
              : Icons.groups_2_rounded,
          label: isDirect ? 'View contact info' : 'View group info',
          onTap: () {
            if (isDirect) {
              _openContactInfo(conversation, otherUser);
            } else {
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => GroupInfoScreen(
                    theme: _theme,
                    dataStore: widget.dataStore,
                    conversationId: widget.conversationId,
                  ),
                ),
              );
            }
          },
        ),
        ChatyMenuItem(
          icon: Icons.search_rounded,
          label: 'Search conversation',
          onTap: _toggleInChatSearch,
        ),
        ChatyMenuItem(
          icon: Icons.wallpaper_rounded,
          label: 'Chat wallpaper',
          onTap: _openWallpaperPicker,
        ),
        ChatyMenuItem(
          icon: conversation.isMuted
              ? Icons.notifications_active_rounded
              : Icons.notifications_off_rounded,
          label: conversation.isMuted
              ? 'Unmute notifications'
              : 'Mute notifications',
          onTap: () => widget.dataStore.toggleMuteConversation(conversation.id),
        ),
        ChatyMenuItem(
          icon: Icons.delete_sweep_rounded,
          label: 'Clear conversation',
          destructive: true,
          onTap: _clearConversation,
        ),
        if (isDirect)
          ChatyMenuItem(
            icon: Icons.block_rounded,
            label: 'Block ${otherUser.displayName.split(' ').first}',
            destructive: true,
            onTap: () => _blockContact(otherUser),
          ),
      ],
    );
  }

  void _toggleInChatSearch() {
    setState(() {
      _isInChatSearchOpen = !_isInChatSearchOpen;
      if (!_isInChatSearchOpen) {
        _inChatSearchCtrl.clear();
        _searchMatchedMessageIds.clear();
        _currentSearchMatchIndex = 0;
        _highlightedSearchMessageId = null;
      }
    });
    if (_isInChatSearchOpen) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _inChatSearchFocus.requestFocus();
      });
    }
  }

  void _performInChatSearch(String query) {
    final cleanQuery = query.trim().toLowerCase();
    if (cleanQuery.isEmpty) {
      setState(() {
        _searchMatchedMessageIds.clear();
        _currentSearchMatchIndex = 0;
        _highlightedSearchMessageId = null;
      });
      return;
    }

    final messages = widget.dataStore.getMessages(widget.conversationId);
    final matched = <String>[];
    for (final message in messages) {
      if (message.text.toLowerCase().contains(cleanQuery)) {
        matched.add(message.id);
      }
    }

    setState(() {
      _searchMatchedMessageIds = matched;
      _currentSearchMatchIndex = matched.isNotEmpty ? 1 : 0;
    });

    if (matched.isNotEmpty) {
      _highlightMessage(matched.first);
    }
  }

  void _jumpToSearchMatch(int index) {
    if (_searchMatchedMessageIds.isEmpty) return;
    final clamped = index.clamp(1, _searchMatchedMessageIds.length);
    setState(() => _currentSearchMatchIndex = clamped);
    final targetId = _searchMatchedMessageIds[clamped - 1];
    _highlightMessage(targetId);
  }

  void _highlightMessage(String messageId) {
    _searchHighlightTimer?.cancel();
    setState(() => _highlightedSearchMessageId = messageId);
    _searchHighlightTimer = Timer(const Duration(milliseconds: 1800), () {
      if (mounted) {
        setState(() => _highlightedSearchMessageId = null);
      }
    });
  }

  /// Per-chat wallpaper: writes a real override consumed by ChatWallpaper.
  void _openWallpaperPicker() {
    final theme = _theme;
    const options = <(String, String)>[
      ('none', 'Plain'),
      ('subtle_dots', 'Subtle Dots'),
      ('geometric', 'Geometric'),
      ('gradient_mesh', 'Gradient Mesh'),
      ('constellation', 'Constellation'),
    ];
    final current = _chatWallpaperOverride ?? theme.wallpaperId;
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) => SafeArea(
        child: Container(
          decoration: BoxDecoration(
            color: theme.surfaceColor,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(18)),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const SizedBox(height: 14),
              Text(
                'Chat wallpaper',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontWeight: FontWeight.w800,
                  fontSize: 15,
                ),
              ),
              const SizedBox(height: 4),
              for (final (id, label) in options)
                ListTile(
                  dense: true,
                  title: Text(
                    label,
                    style: TextStyle(color: theme.primaryTextColor),
                  ),
                  trailing: current == id
                      ? Icon(Icons.check_rounded, color: theme.accentColor)
                      : null,
                  onTap: () {
                    widget.dataStore.setChatWallpaper(
                      widget.conversationId,
                      id,
                    );
                    Navigator.of(sheetContext).pop();
                  },
                ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }



  Future<void> _clearConversation() async {
    final confirmed = await ChatyConfirmDialog.show(
      context,
      title: 'Clear conversation?',
      message: 'All messages will be removed from this chat for you.',
      confirmLabel: 'Clear',
      destructive: true,
    );
    if (confirmed != true || !mounted) return;
    final messages = widget.dataStore
        .getMessages(widget.conversationId)
        .toList(growable: false);
    for (final message in messages) {
      widget.dataStore.deleteMessage(
        widget.conversationId,
        message.id,
        forEveryone: false,
      );
    }
    setState(() {
      _openedViewOnceIds.clear();
      _pendingBelowCount = 0;
      _lastKnownMessageCount = 0;
    });
  }

  Future<void> _blockContact(UserProfile contact) async {
    final confirmed = await ChatyConfirmDialog.show(
      context,
      title: 'Block contact?',
      message:
          '${contact.displayName} will no longer be able to send you '
          'messages or call you.',
      confirmLabel: 'Block',
      destructive: true,
    );
    if (confirmed != true) return;
    try {
      await _relationships.setBlocked(contact.id, true);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${contact.displayName} was blocked.')),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not block this contact.')),
      );
    }
  }

  Widget _messagesBody(
    ThemeConfig theme,
    Conversation conversation,
    List<ChatMessage> messages,
    bool showDeleted,
  ) {
    if (_loadError != null && messages.isEmpty) {
      if (_isSecureSetupPendingError(_loadError!)) {
        return Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.verified_user_rounded,
                  size: 42,
                  color: theme.secondaryTextColor,
                ),
                const SizedBox(height: 10),
                Text(
                  'Waiting for secure chat setup',
                  style: TextStyle(
                    color: theme.primaryTextColor,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  'This chat is finishing its end-to-end encryption setup. '
                  'Messages will appear here as soon as it completes.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: theme.secondaryTextColor),
                ),
              ],
            ),
          ),
        );
      }
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.cloud_off_rounded,
                size: 42,
                color: theme.secondaryTextColor,
              ),
              const SizedBox(height: 10),
              Text(
                'Could not load messages',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                _loadError!,
                textAlign: TextAlign.center,
                style: TextStyle(color: theme.secondaryTextColor),
              ),
              const SizedBox(height: 12),
              FilledButton.tonalIcon(
                onPressed: _loadConversation,
                icon: const Icon(Icons.refresh_rounded),
                label: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }
    if (_loadingMessages) {
      // Deterministic loading state: skeleton until the first query lands —
      // never a fake “No messages yet”.
      return const Center(child: CircularProgressIndicator(strokeWidth: 2));
    }
    if (messages.isEmpty)
      return ChatyEmptyState(
        icon: Icons.chat_bubble_outline_rounded,
        title: 'No messages yet',
        message: 'Say hi to start the conversation.',
        iconColor: theme.secondaryTextColor,
        titleColor: theme.primaryTextColor,
        messageColor: theme.secondaryTextColor,
      );
    // Anti View-Once resolve chain: explicit privacy pref OR GB toggle.
    final retainViewOnce =
        widget.preferencesController.privacy.antiViewOnce ||
        widget.preferencesController.gbBool('anti_vw_once');
    return Opacity(
      opacity: _initialPositionApplied ? 1 : 0,
      child: ListView.builder(
        controller: _scrollCtrl,
        padding: const EdgeInsets.symmetric(vertical: 8),
        itemCount: messages.length,
        itemBuilder: (context, index) {
          final message = messages[index];
          final isMine = message.senderId == widget.dataStore.currentUser.id;
          final bubble = MessageBubble(
            message: message,
            isMe: isMine,
            theme: theme,
            showDeletedContent: showDeleted,
            retainViewOnce: retainViewOnce,
            viewOnceOpened: _openedViewOnceIds.contains(message.id),
            isSelected: _contextualMessageId == message.id ||
                _selectedMessageIds.contains(message.id) ||
                _highlightedSearchMessageId == message.id,
            onViewOnceOpen: () => _openViewOnceMedia(message, theme),
            senderName: conversation.type == ConversationType.group && !isMine
                ? _senderName(message.senderId)
                : null,
            onLongPress: () => _onMessageLongPress(message),
            onLongPressWithRect: (rect) => _onMessageLongPressWithRect(message, rect),
            onSwipeReply: () {
              HapticFeedback.lightImpact();
              setState(() => _replyTarget = message);
            },
            onReactionTap: (emoji) => widget.dataStore.toggleReaction(
              conversation.id,
              message.id,
              emoji,
            ),
            onReactionBadgeTap: (reaction) =>
                _showReactionDetailsSheet(message, reaction),
            onDoubleTap: () => widget.dataStore.toggleReaction(
              conversation.id,
              message.id,
              widget.preferencesController.conversation.doubleTapReactionEmoji,
            ),
            voicePlaybackSpeed:
                widget.preferencesController.conversation.voicePlaybackSpeed,
            task: message.linkedTaskId != null
                ? widget.dataStore.tasks.firstWhere(
                    (t) => t.id == message.linkedTaskId,
                    orElse: () => ChatTask(
                      id: message.linkedTaskId!,
                      sourceConversationId: conversation.id,
                      sourceMessageId: message.id,
                      title: message.text,
                      description: '',
                      creatorId: message.senderId,
                      assigneeIds: const [],
                      dueAt: message.createdAt.add(const Duration(days: 3)),
                      createdAt: message.createdAt,
                      updatedAt: message.createdAt,
                    ),
                  )
                : null,
            onTaskTap: () {
              final currentTask = message.linkedTaskId != null
                  ? widget.dataStore.tasks.firstWhere(
                      (t) => t.id == message.linkedTaskId,
                      orElse: () => ChatTask(
                        id: message.linkedTaskId!,
                        sourceConversationId: conversation.id,
                        sourceMessageId: message.id,
                        title: message.text,
                        description: '',
                        creatorId: message.senderId,
                        assigneeIds: const [],
                        dueAt: message.createdAt.add(const Duration(days: 3)),
                        createdAt: message.createdAt,
                        updatedAt: message.createdAt,
                      ),
                    )
                  : null;
              if (currentTask != null) {
                _openTaskDetails(currentTask);
              } else {
                _openCreateTaskModal(
                  initialTitle: message.text,
                  sourceMessageId: message.id,
                );
              }
            },
            onTaskToggle: () {
              final currentTask = message.linkedTaskId != null
                  ? widget.dataStore.tasks.firstWhere(
                      (t) => t.id == message.linkedTaskId,
                      orElse: () => ChatTask(
                        id: message.linkedTaskId!,
                        sourceConversationId: conversation.id,
                        sourceMessageId: message.id,
                        title: message.text,
                        description: '',
                        creatorId: message.senderId,
                        assigneeIds: const [],
                        dueAt: message.createdAt.add(const Duration(days: 3)),
                        createdAt: message.createdAt,
                        updatedAt: message.createdAt,
                      ),
                    )
                  : null;
              if (currentTask != null) {
                _toggleTaskStatus(currentTask);
              }
            },
            onTaskMenu: (anchorRect) {
              final currentTask = message.linkedTaskId != null
                  ? widget.dataStore.tasks.firstWhere(
                      (t) => t.id == message.linkedTaskId,
                      orElse: () => ChatTask(
                        id: message.linkedTaskId!,
                        sourceConversationId: conversation.id,
                        sourceMessageId: message.id,
                        title: message.text,
                        description: '',
                        creatorId: message.senderId,
                        assigneeIds: const [],
                        dueAt: message.createdAt.add(const Duration(days: 3)),
                        createdAt: message.createdAt,
                        updatedAt: message.createdAt,
                      ),
                    )
                  : null;
              if (currentTask != null) {
                _showTaskContextMenu(currentTask, anchorRect);
              }
            },
            onMediaTap: message.attachment == null
                ? null
                : () => Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => MediaViewerScreen(
                        title: message.attachment!.name,
                        type: message.attachment!.type,
                        size: message.attachment!.size,
                        storagePath: message.attachment!.url,
                        theme: theme,
                      ),
                    ),
                  ),
          );
          if (!ChatAttachmentActions.isPollMessage(message)) return bubble;
          return GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: () => _attachments.openPoll(context, message.id),
            child: bubble,
          );
        },
      ),
    );
  }
}

/// Premium message composer + voice recorder.
///
/// Presentation only: every callback is owned by the screen and the capture
/// pipeline is untouched. While recording, the level meter renders REAL
/// microphone amplitude samples (dBFS from the `record` plugin, polled at
/// ~90ms) — never synthetic bars. With no amplitude provider the meter is
/// simply omitted rather than faked.
/// Premium message composer + voice recorder.
///
/// Presentation only: every callback is owned by the screen and the capture
/// pipeline is untouched. While recording, the level meter renders REAL
/// microphone amplitude samples (dBFS from the `record` plugin, polled at
/// ~90ms) — never synthetic bars. With no amplitude provider the meter is
/// simply omitted rather than faked.
class _Composer extends StatefulWidget {
  final ThemeConfig theme;
  final TextEditingController controller;
  final VoidCallback onAttach;
  final VoidCallback onCameraTap;
  final VoidCallback onEmoji;
  final VoidCallback onSend;
  final ValueChanged<String> onChanged;
  final bool recording;
  final bool recordLocked;
  final bool voiceBusy;
  final int voiceSeconds;
  final VoidCallback onVoiceTap;
  final VoidCallback onVoiceHoldStart;
  final ValueChanged<LongPressMoveUpdateDetails> onVoiceMove;
  final VoidCallback onVoiceHoldEnd;
  final VoidCallback onVoiceCancel;
  final VoidCallback onVoiceSend;
  final Future<double> Function()? amplitudeProvider;

  const _Composer({
    required this.theme,
    required this.controller,
    required this.onAttach,
    required this.onCameraTap,
    required this.onEmoji,
    required this.onSend,
    required this.onChanged,
    required this.recording,
    required this.recordLocked,
    required this.voiceBusy,
    required this.voiceSeconds,
    required this.onVoiceTap,
    required this.onVoiceHoldStart,
    required this.onVoiceMove,
    required this.onVoiceHoldEnd,
    required this.onVoiceCancel,
    required this.onVoiceSend,
    this.amplitudeProvider,
  });

  @override
  State<_Composer> createState() => _ComposerState();
}

class _ComposerState extends State<_Composer>
    with SingleTickerProviderStateMixin {
  /// Level-meter resolution. Bars scroll left as new samples arrive.
  static const int _barCount = 30;
  static const double _dbFloor = -60;
  final List<double> _levels = List<double>.filled(_barCount, 0.0);
  Timer? _levelTimer;
  late final AnimationController _pulse = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 900),
  );

  @override
  void initState() {
    super.initState();
    if (widget.recording) {
      _pulse.repeat(reverse: true);
      _startLevelPolling();
    }
  }

  @override
  void didUpdateWidget(covariant _Composer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.recording == oldWidget.recording) return;
    if (widget.recording) {
      _pulse.repeat(reverse: true);
      _startLevelPolling();
    } else {
      _pulse.stop();
      _stopLevelPolling();
    }
  }

  @override
  void dispose() {
    _stopLevelPolling();
    _pulse.dispose();
    super.dispose();
  }

  void _startLevelPolling() {
    _levels.fillRange(0, _barCount, 0.0);
    _levelTimer?.cancel();
    _levelTimer = Timer.periodic(const Duration(milliseconds: 90), (_) async {
      final provider = widget.amplitudeProvider;
      if (provider == null || !mounted) return;
      final db = await provider();
      // dBFS → 0..1 with a small floor so silence still shows a stub.
      final normalized = ((db - _dbFloor) / -_dbFloor).clamp(0.0, 1.0);
      if (!mounted) return;
      setState(() {
        _levels.removeAt(0);
        _levels.add(normalized < 0.06 ? 0.06 : normalized);
      });
    });
  }

  void _stopLevelPolling() {
    _levelTimer?.cancel();
    _levelTimer = null;
  }

  String get _time {
    final min = (widget.voiceSeconds ~/ 60).toString().padLeft(2, '0');
    final sec = (widget.voiceSeconds % 60).toString().padLeft(2, '0');
    return '$min:$sec';
  }

  @override
  Widget build(BuildContext context) {
    final theme = widget.theme;
    final reduceMotion = MediaQuery.of(context).disableAnimations;
    return Container(
      padding: const EdgeInsets.fromLTRB(6, 8, 8, 8),
      decoration: BoxDecoration(
        color: theme.surfaceColor,
        border: Border(top: BorderSide(color: theme.cardColor)),
      ),
      child: SafeArea(
        top: false,
        child: widget.recording
            ? _buildRecordingBar(theme, reduceMotion)
            : _buildInputRow(theme),
      ),
    );
  }

  // --- Recording ------------------------------------------------------------

  Widget _buildRecordingBar(ThemeConfig theme, bool reduceMotion) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          children: [
            ChatyComposerActionButton(
              theme: theme,
              semanticsLabel: 'Cancel recording',
              tooltip: 'Cancel recording',
              icon: Icons.delete_outline_rounded,
              iconColor: theme.dangerColor,
              onTap: widget.voiceBusy ? null : widget.onVoiceCancel,
            ),
            const SizedBox(width: 10),
            FadeTransition(
              opacity: reduceMotion
                  ? const AlwaysStoppedAnimation<double>(1)
                  : Tween<double>(begin: 1, end: 0.35).animate(_pulse),
              child: Container(
                width: 9,
                height: 9,
                decoration: BoxDecoration(
                  color: theme.dangerColor,
                  shape: BoxShape.circle,
                ),
              ),
            ),
            const SizedBox(width: 8),
            Text(
              _time,
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 14.5,
                fontWeight: FontWeight.w700,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
            if (widget.recordLocked) ...[
              const SizedBox(width: 6),
              Icon(Icons.lock_rounded, size: 13, color: theme.successColor),
            ],
            const SizedBox(width: 12),
            if (widget.amplitudeProvider != null)
              Expanded(
                child: ChatyVoiceLevelMeter(levels: _levels, theme: theme),
              )
            else
              const Spacer(),
            const SizedBox(width: 10),
            ChatyComposerActionButton(
              theme: theme,
              semanticsLabel: 'Send voice note',
              tooltip: 'Send voice note',
              icon: Icons.send_rounded,
              fillColor: theme.accentColor,
              iconColor: theme.onAccentColor,
              busy: widget.voiceBusy,
              onTap: widget.voiceBusy ? null : widget.onVoiceSend,
            ),
          ],
        ),
        const SizedBox(height: 5),
        Text(
          widget.recordLocked
              ? 'Recording locked • tap send or delete'
              : 'Tap send to finish • slide left to cancel',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(color: theme.secondaryTextColor, fontSize: 11),
        ),
      ],
    );
  }

  // --- Text input -----------------------------------------------------------


  Widget _buildInputRow(ThemeConfig theme) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        ChatyComposerActionButton(
          theme: theme,
          semanticsLabel: 'Attach',
          tooltip: 'Attach',
          icon: Icons.add_circle_outline_rounded,
          iconColor: theme.accentColor,
          onTap: widget.onAttach,
        ),
        ChatyComposerActionButton(
          theme: theme,
          semanticsLabel: 'Camera',
          tooltip: 'Camera',
          icon: Icons.photo_camera_rounded,
          iconColor: theme.accentColor,
          onTap: widget.onCameraTap,
        ),
        Expanded(
          child: TextField(
            controller: widget.controller,
            minLines: 1,
            maxLines: 5,
            onChanged: widget.onChanged,
            style: TextStyle(
              color: theme.primaryTextColor,
              fontSize: 14 * theme.fontScale,
            ),
            decoration: InputDecoration(
              hintText: 'Message…  /task or #reply',
              hintStyle: TextStyle(color: theme.secondaryTextColor),
              filled: true,
              fillColor: theme.cardColor,
              prefixIcon: IconButton(
                tooltip: 'Emoji',
                onPressed: widget.onEmoji,
                icon: Icon(
                  Icons.emoji_emotions_outlined,
                  color: theme.secondaryTextColor,
                ),
              ),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(22),
                borderSide: BorderSide.none,
              ),
              isDense: true,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 12,
                vertical: 11,
              ),
            ),
          ),
        ),
        const SizedBox(width: 6),
        ValueListenableBuilder<TextEditingValue>(
          valueListenable: widget.controller,
          builder: (context, value, _) {
            final hasText = value.text.trim().isNotEmpty;
            if (hasText) {
              return ChatyComposerActionButton(
                theme: theme,
                semanticsLabel: 'Send message',
                tooltip: 'Send',
                icon: Icons.send_rounded,
                fillColor: theme.accentColor,
                iconColor: theme.onAccentColor,
                emphasized: true,
                onTap: widget.onSend,
              );
            }
            return ChatyComposerActionButton(
              theme: theme,
              icon: Icons.mic_rounded,
              fillColor: theme.accentColor,
              iconColor: theme.onAccentColor,
              semanticsLabel:
                  'Voice note. Tap to start locked recording, or hold to record and slide.',
              onTap: widget.onVoiceTap,
              onLongPressStart: (_) => widget.onVoiceHoldStart(),
              onLongPressMoveUpdate: widget.onVoiceMove,
              onLongPressEnd: (_) => widget.onVoiceHoldEnd(),
            );
          },
        ),
      ],
    );
  }
}
