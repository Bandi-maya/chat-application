import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../domain/models/chaty_preferences.dart';
import '../../../ui/core/theme/theme_config.dart';
import '../../../ui/core/theme/theme_controller.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../data/services/chaty_notification_service.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/user_profile.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../ui/core/design_system/chaty_settings_primitives.dart';
import '../search/global_search_screen.dart';
import '../settings/security/app_lock_overlay.dart';
import 'chat_detail_screen.dart';
import 'new_chat_screen.dart';

class ChatsHomeScreen extends StatefulWidget {
  final ThemeConfig theme;
  final MockDataStore dataStore;
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;
  final ChatyNotificationService notificationService;
  final ConversationType? forcedType;
  final String? pageTitle;

  const ChatsHomeScreen({
    super.key,
    required this.theme,
    required this.dataStore,
    required this.preferencesController,
    required this.themeController,
    required this.notificationService,
    this.forcedType,
    this.pageTitle,
  });

  @override
  State<ChatsHomeScreen> createState() => _ChatsHomeScreenState();
}

class _ChatsHomeScreenState extends State<ChatsHomeScreen> {
  final TextEditingController _searchCtrl = TextEditingController();
  final FocusNode _searchFocus = FocusNode();
  final Set<String> _selectedConversationIds = <String>{};
  String _selectedFilter = 'All';
  bool _isSearchOpen = false;

  bool get _isSelectionMode => _selectedConversationIds.isNotEmpty;

  @override
  void dispose() {
    _searchCtrl.dispose();
    _searchFocus.dispose();
    super.dispose();
  }

  void _toggleSearch() {
    setState(() {
      _isSearchOpen = !_isSearchOpen;
      if (!_isSearchOpen) _searchCtrl.clear();
    });
    if (_isSearchOpen) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _searchFocus.requestFocus());
    } else {
      _searchFocus.unfocus();
    }
  }

  void _clearSelection() {
    if (mounted) {
      setState(() => _selectedConversationIds.clear());
    }
  }

  void _toggleSelection(String id) {
    HapticFeedback.selectionClick();
    setState(() {
      if (_selectedConversationIds.contains(id)) {
        _selectedConversationIds.remove(id);
      } else {
        _selectedConversationIds.add(id);
      }
    });
  }

  void _selectAll(List<Conversation> visibleConversations) {
    setState(() {
      _selectedConversationIds.addAll(visibleConversations.map((c) => c.id));
    });
  }

  void _openChat(Conversation conversation) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ChatDetailScreen(
          conversationId: conversation.id,
          theme: widget.themeController.globalTheme,
          dataStore: widget.dataStore,
          preferencesController: widget.preferencesController,
          themeController: widget.themeController,
        ),
      ),
    );
  }

  void _handleConversationTap(Conversation conversation) {
    if (_isSelectionMode) {
      _toggleSelection(conversation.id);
      return;
    }

    final isLocked = widget.preferencesController.isConversationLocked(conversation.id);
    if (isLocked) {
      AppLockOverlayModal.show(context, preferencesController: widget.preferencesController).then((unlocked) {
        if (unlocked == true && mounted) {
          _openChat(conversation);
        }
      });
    } else {
      _openChat(conversation);
    }
  }

  void _handleConversationLongPress(Conversation conversation) {
    HapticFeedback.mediumImpact();
    _toggleSelection(conversation.id);
  }

  void _togglePinSelected() {
    final dataStore = widget.dataStore;
    final selectedConvs = dataStore.conversations.where((c) => _selectedConversationIds.contains(c.id)).toList();
    final anyUnpinned = selectedConvs.any((c) => !c.isPinned);
    for (final conv in selectedConvs) {
      if (anyUnpinned && !conv.isPinned) {
        dataStore.togglePinConversation(conv.id);
      } else if (!anyUnpinned && conv.isPinned) {
        dataStore.togglePinConversation(conv.id);
      }
    }
    final count = _selectedConversationIds.length;
    _clearSelection();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(anyUnpinned ? '$count chat${count > 1 ? 's' : ''} pinned' : '$count chat${count > 1 ? 's' : ''} unpinned'),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  void _toggleArchiveSelected() {
    final dataStore = widget.dataStore;
    final idsToArchive = List<String>.from(_selectedConversationIds);
    final count = idsToArchive.length;
    for (final id in idsToArchive) {
      dataStore.toggleArchiveConversation(id);
    }
    _clearSelection();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('$count chat${count > 1 ? 's' : ''} archived'),
        action: SnackBarAction(
          label: 'UNDO',
          onPressed: () {
            for (final id in idsToArchive) {
              dataStore.toggleArchiveConversation(id);
            }
          },
        ),
        duration: const Duration(seconds: 3),
      ),
    );
  }

  void _showMuteDialog() {
    final dataStore = widget.dataStore;
    final selectedConvs = dataStore.conversations.where((c) => _selectedConversationIds.contains(c.id)).toList();
    final anyUnmuted = selectedConvs.any((c) => !c.isMuted);

    if (!anyUnmuted) {
      for (final conv in selectedConvs) {
        dataStore.toggleMuteConversation(conv.id);
      }
      final count = _selectedConversationIds.length;
      _clearSelection();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('$count chat${count > 1 ? 's' : ''} unmuted'), duration: const Duration(seconds: 2)),
      );
      return;
    }

    String selectedDuration = '8 hours';
    bool showNotifications = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setModalState) {
          return AlertDialog(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            title: const Text('Mute notifications for…', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                ...['8 hours', '1 week', 'Always'].map((duration) {
                  return RadioListTile<String>(
                    contentPadding: EdgeInsets.zero,
                    title: Text(duration),
                    value: duration,
                    groupValue: selectedDuration,
                    onChanged: (val) => setModalState(() => selectedDuration = val ?? '8 hours'),
                  );
                }),
                const Divider(),
                CheckboxListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Show notifications', style: TextStyle(fontSize: 14)),
                  value: showNotifications,
                  onChanged: (val) => setModalState(() => showNotifications = val ?? false),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: () {
                  for (final conv in selectedConvs) {
                    if (!conv.isMuted) dataStore.toggleMuteConversation(conv.id);
                  }
                  final count = _selectedConversationIds.length;
                  Navigator.of(ctx).pop();
                  _clearSelection();
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('Muted $count chat${count > 1 ? 's' : ''} ($selectedDuration)'),
                      duration: const Duration(seconds: 2),
                    ),
                  );
                },
                child: const Text('OK'),
              ),
            ],
          );
        },
      ),
    );
  }

  void _showDeleteDialog() {
    final count = _selectedConversationIds.length;
    final idsToDelete = List<String>.from(_selectedConversationIds);
    bool deleteMedia = true;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setModalState) {
          return AlertDialog(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            title: Text('Delete $count chat${count > 1 ? 's' : ''}?'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Messages will be deleted from your conversations list.',
                  style: TextStyle(fontSize: 14, height: 1.4),
                ),
                const SizedBox(height: 12),
                CheckboxListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text(
                    'Also delete media received in these chats from device gallery',
                    style: TextStyle(fontSize: 13),
                  ),
                  value: deleteMedia,
                  onChanged: (val) => setModalState(() => deleteMedia = val ?? true),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(),
                child: const Text('Cancel'),
              ),
              FilledButton(
                style: FilledButton.styleFrom(backgroundColor: Colors.redAccent),
                onPressed: () {
                  for (final id in idsToDelete) {
                    widget.dataStore.deleteConversation(id);
                  }
                  Navigator.of(ctx).pop();
                  _clearSelection();
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('$count chat${count > 1 ? 's' : ''} deleted'),
                      duration: const Duration(seconds: 2),
                    ),
                  );
                },
                child: Text('Delete chat${count > 1 ? 's' : ''}'),
              ),
            ],
          );
        },
      ),
    );
  }

  void _toggleLockSelected() {
    final prefs = widget.preferencesController;
    final ids = List<String>.from(_selectedConversationIds);
    final anyUnlocked = ids.any((id) => !prefs.isConversationLocked(id));

    for (final id in ids) {
      prefs.toggleLockConversation(id, lock: anyUnlocked);
    }
    final count = ids.length;
    _clearSelection();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            Icon(anyUnlocked ? Icons.lock_rounded : Icons.lock_open_rounded, color: Colors.white, size: 18),
            const SizedBox(width: 10),
            Text(anyUnlocked ? '$count chat${count > 1 ? 's' : ''} locked' : '$count chat${count > 1 ? 's' : ''} unlocked'),
          ],
        ),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  void _markSelectedReadUnread({required bool markAsUnread}) {
    final dataStore = widget.dataStore;
    for (final id in _selectedConversationIds) {
      if (markAsUnread) {
        dataStore.markAsUnread(id);
      } else {
        dataStore.markAsRead(id);
      }
    }
    final count = _selectedConversationIds.length;
    _clearSelection();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(markAsUnread ? 'Marked $count chat${count > 1 ? 's' : ''} as unread' : 'Marked $count chat${count > 1 ? 's' : ''} as read'),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  String _formatMessageTime(DateTime value) {
    final now = DateTime.now();
    final local = value.toLocal();
    final difference = now.difference(local);
    if (difference.inDays == 0 && now.day == local.day) {
      final hour = local.hour.toString().padLeft(2, '0');
      final minute = local.minute.toString().padLeft(2, '0');
      return '$hour:$minute';
    }
    if (difference.inDays <= 1) return 'Yesterday';
    return '${local.day}/${local.month}';
  }

  String _formatLastSeen(UserProfile? user) {
    if (user == null) return '';
    if (user.presence == PresenceState.online || user.presence == PresenceState.typing) return 'online';
    final value = user.lastSeenAt.toLocal();
    final now = DateTime.now();
    final difference = now.difference(value);
    if (difference.inMinutes < 1) return 'last seen just now';
    if (difference.inMinutes < 60) return 'last seen ${difference.inMinutes}m ago';
    if (difference.inHours < 24 && now.day == value.day) {
      final hour = value.hour.toString().padLeft(2, '0');
      final minute = value.minute.toString().padLeft(2, '0');
      return 'last seen $hour:$minute';
    }
    return 'last seen ${value.day}/${value.month}';
  }

  void _openGlobalSearch(ThemeConfig theme) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => GlobalSearchScreen(
          theme: theme,
          dataStore: widget.dataStore,
          preferencesController: widget.preferencesController,
          themeController: widget.themeController,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = widget.themeController.globalTheme;
    final dataStore = widget.dataStore;
    final homePrefs = widget.preferencesController.home;
    final query = _searchCtrl.text.trim().toLowerCase();

    final conversations = dataStore.conversations.where((conversation) {
      if (widget.forcedType != null && conversation.type != widget.forcedType) return false;
      final matchesQuery = query.isEmpty ||
          conversation.title.toLowerCase().contains(query) ||
          conversation.lastMessageText.toLowerCase().contains(query);
      if (!matchesQuery) return false;
      switch (_selectedFilter) {
        case 'Unread':
          return conversation.unreadCount > 0;
        case 'Groups':
          return conversation.type == ConversationType.group;
        case 'Direct':
          return conversation.type == ConversationType.direct;
        default:
          return true;
      }
    }).toList(growable: false);

    final pinned = conversations.where((item) => item.isPinned && !item.isArchived).toList(growable: false);
    final recent = conversations.where((item) => !item.isPinned && !item.isArchived).toList(growable: false);
    final filters = widget.forcedType == null
        ? const <String>['All', 'Unread', 'Groups', 'Direct']
        : const <String>['All', 'Unread'];
    final listEntries = <Object>[];
    if (pinned.isNotEmpty) {
      listEntries
        ..add(const _ConversationSection('PINNED'))
        ..addAll(pinned);
    }
    if (recent.isNotEmpty) {
      if (pinned.isNotEmpty) listEntries.add(const _ConversationSection('MESSAGES'));
      listEntries.addAll(recent);
    }

    return PopScope(
      canPop: !_isSelectionMode,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop && _isSelectionMode) {
          _clearSelection();
        }
      },
      child: Scaffold(
        backgroundColor: theme.backgroundColor,
        body: SafeArea(
          child: Column(
            children: [
              _isSelectionMode
                  ? _buildWhatsAppSelectionAppBar(theme, conversations)
                  : _buildStandardAppBar(theme, homePrefs),
              if (!_isSelectionMode) ...[
                AnimatedSize(
                  duration: const Duration(milliseconds: 180),
                  curve: Curves.easeOutCubic,
                  child: _isSearchOpen
                      ? Padding(
                          padding: const EdgeInsets.fromLTRB(16, 2, 16, 8),
                          child: TextField(
                            controller: _searchCtrl,
                            focusNode: _searchFocus,
                            onChanged: (_) => setState(() {}),
                            style: TextStyle(color: theme.primaryTextColor),
                            decoration: InputDecoration(
                              hintText: widget.forcedType == ConversationType.group
                                  ? 'Search groups…'
                                  : widget.forcedType == ConversationType.direct
                                      ? 'Search direct chats…'
                                      : 'Search chats and messages…',
                              hintStyle: TextStyle(color: theme.secondaryTextColor),
                              prefixIcon: Icon(Icons.search_rounded, color: theme.secondaryTextColor),
                              suffixIcon: _searchCtrl.text.isEmpty
                                  ? null
                                  : IconButton(
                                      tooltip: 'Clear search',
                                      onPressed: () {
                                        _searchCtrl.clear();
                                        setState(() {});
                                      },
                                      icon: const Icon(Icons.close_rounded),
                                    ),
                              filled: true,
                              fillColor: theme.cardColor,
                              border: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(theme.cornerRadius),
                                borderSide: BorderSide.none,
                              ),
                            ),
                          ),
                        )
                      : const SizedBox.shrink(),
                ),
                if (homePrefs.enableStoriesStrip && widget.forcedType != ConversationType.group)
                  SizedBox(
                    height: 82,
                    child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      itemCount: dataStore.contacts.length,
                      separatorBuilder: (_, _) => const SizedBox(width: 10),
                      itemBuilder: (context, index) {
                        final contact = dataStore.contacts[index];
                        return SizedBox(
                          width: 58,
                          child: Column(
                            children: [
                              ChatyAvatar(
                                initials: contact.avatarInitials,
                                color: Color(int.parse(contact.avatarColorHex)),
                                size: 48,
                                shape: homePrefs.avatarShape,
                              ),
                              const SizedBox(height: 4),
                              Text(
                                contact.displayName.split(' ').first,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(color: theme.secondaryTextColor, fontSize: 10.5),
                              ),
                            ],
                          ),
                        );
                      },
                    ),
                  ),
                Align(
                  alignment: Alignment.centerLeft,
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.fromLTRB(16, 3, 16, 8),
                    child: Row(
                      children: filters.map((filter) {
                        final selected = _selectedFilter == filter;
                        return Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: ChoiceChip(
                            label: Text(filter),
                            selected: selected,
                            selectedColor: theme.accentColor.withValues(alpha: 0.18),
                            backgroundColor: theme.cardColor,
                            side: BorderSide(color: selected ? theme.accentColor.withValues(alpha: 0.35) : theme.surfaceColor),
                            labelStyle: TextStyle(
                              color: selected ? theme.accentColor : theme.secondaryTextColor,
                              fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                            ),
                            onSelected: (value) {
                              if (value) setState(() => _selectedFilter = filter);
                            },
                          ),
                        );
                      }).toList(growable: false),
                    ),
                  ),
                ),
              ],
              Expanded(
                child: conversations.isEmpty
                    ? _EmptyChats(
                        theme: theme,
                        onSearch: () => _openGlobalSearch(theme),
                        forcedType: widget.forcedType,
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.fromLTRB(8, 0, 8, 96),
                        cacheExtent: 420,
                        itemCount: listEntries.length,
                        itemBuilder: (context, index) {
                          final entry = listEntries[index];
                          if (entry is _ConversationSection) {
                            return _SectionLabel(label: entry.label, theme: theme);
                          }
                          return _conversationTile(entry as Conversation, theme);
                        },
                      ),
              ),
            ],
          ),
        ),
        floatingActionButton: _isSelectionMode
            ? null
            : FloatingActionButton(
                tooltip: widget.forcedType == ConversationType.group ? 'Create group' : 'New chat',
                backgroundColor: theme.accentColor,
                foregroundColor: theme.onAccentColor,
                onPressed: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => NewChatScreen(
                        theme: theme,
                        dataStore: dataStore,
                        preferencesController: widget.preferencesController,
                      ),
                    ),
                  );
                },
                child: Icon(widget.forcedType == ConversationType.group ? Icons.group_add_rounded : Icons.edit_rounded),
              ),
      ),
    );
  }

  Widget _buildStandardAppBar(ThemeConfig theme, HomePreferences homePrefs) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 10, 6),
      child: Row(
        children: [
          Expanded(
            child: Text(
              widget.pageTitle ?? 'Chaty',
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 24 * theme.fontScale,
                fontWeight: FontWeight.w800,
                letterSpacing: -0.5,
              ),
            ),
          ),
          if (homePrefs.showCameraIcon)
            IconButton(
              tooltip: 'Camera',
              color: theme.primaryTextColor,
              onPressed: () => _openGlobalSearch(theme),
              icon: const Icon(Icons.camera_alt_outlined),
            ),
          IconButton(
            tooltip: _isSearchOpen ? 'Close search' : 'Search chats',
            color: theme.primaryTextColor,
            onPressed: _toggleSearch,
            icon: Icon(_isSearchOpen ? Icons.close_rounded : Icons.search_rounded),
          ),
          IconButton(
            tooltip: 'Toggle light / dark',
            color: theme.primaryTextColor,
            onPressed: widget.themeController.toggleBrightness,
            icon: Icon(theme.brightness == Brightness.dark ? Icons.light_mode_rounded : Icons.dark_mode_rounded),
          ),
        ],
      ),
    );
  }

  Widget _buildWhatsAppSelectionAppBar(ThemeConfig theme, List<Conversation> visibleConversations) {
    final count = _selectedConversationIds.length;
    final selectedConvs = widget.dataStore.conversations.where((c) => _selectedConversationIds.contains(c.id)).toList();
    final anyUnpinned = selectedConvs.any((c) => !c.isPinned);
    final anyUnmuted = selectedConvs.any((c) => !c.isMuted);
    final anyUnlocked = _selectedConversationIds.any((id) => !widget.preferencesController.isConversationLocked(id));

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
      decoration: BoxDecoration(
        color: theme.cardColor,
        border: Border(bottom: BorderSide(color: theme.surfaceColor.withValues(alpha: 0.3))),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          IconButton(
            tooltip: 'Clear selection',
            icon: const Icon(Icons.arrow_back_rounded),
            color: theme.primaryTextColor,
            onPressed: _clearSelection,
          ),
          const SizedBox(width: 4),
          Text(
            '$count',
            style: TextStyle(
              color: theme.primaryTextColor,
              fontSize: 19 * theme.fontScale,
              fontWeight: FontWeight.w800,
            ),
          ),
          const Spacer(),
          IconButton(
            tooltip: anyUnpinned ? 'Pin' : 'Unpin',
            icon: Icon(anyUnpinned ? Icons.push_pin_outlined : Icons.push_pin_rounded),
            color: theme.primaryTextColor,
            onPressed: _togglePinSelected,
          ),
          IconButton(
            tooltip: 'Delete',
            icon: const Icon(Icons.delete_outline_rounded),
            color: theme.primaryTextColor,
            onPressed: _showDeleteDialog,
          ),
          IconButton(
            tooltip: anyUnmuted ? 'Mute' : 'Unmute',
            icon: Icon(anyUnmuted ? Icons.volume_off_outlined : Icons.volume_up_outlined),
            color: theme.primaryTextColor,
            onPressed: _showMuteDialog,
          ),
          IconButton(
            tooltip: 'Archive',
            icon: const Icon(Icons.archive_outlined),
            color: theme.primaryTextColor,
            onPressed: _toggleArchiveSelected,
          ),
          IconButton(
            tooltip: anyUnlocked ? 'Lock chat' : 'Unlock chat',
            icon: Icon(anyUnlocked ? Icons.lock_outline_rounded : Icons.lock_open_rounded),
            color: theme.primaryTextColor,
            onPressed: _toggleLockSelected,
          ),
          PopupMenuButton<String>(
            tooltip: 'More options',
            icon: Icon(Icons.more_vert_rounded, color: theme.primaryTextColor),
            onSelected: (value) {
              switch (value) {
                case 'mark_unread':
                  _markSelectedReadUnread(markAsUnread: true);
                  break;
                case 'mark_read':
                  _markSelectedReadUnread(markAsUnread: false);
                  break;
                case 'select_all':
                  _selectAll(visibleConversations);
                  break;
                case 'toggle_lock':
                  _toggleLockSelected();
                  break;
              }
            },
            itemBuilder: (context) => [
              const PopupMenuItem(
                value: 'mark_unread',
                child: Row(
                  children: [
                    Icon(Icons.mark_chat_unread_outlined, size: 20),
                    SizedBox(width: 12),
                    Text('Mark as unread'),
                  ],
                ),
              ),
              const PopupMenuItem(
                value: 'mark_read',
                child: Row(
                  children: [
                    Icon(Icons.mark_chat_read_outlined, size: 20),
                    SizedBox(width: 12),
                    Text('Mark as read'),
                  ],
                ),
              ),
              const PopupMenuItem(
                value: 'select_all',
                child: Row(
                  children: [
                    Icon(Icons.select_all_rounded, size: 20),
                    SizedBox(width: 12),
                    Text('Select all'),
                  ],
                ),
              ),
              PopupMenuItem(
                value: 'toggle_lock',
                child: Row(
                  children: [
                    Icon(anyUnlocked ? Icons.lock_outline_rounded : Icons.lock_open_rounded, size: 20),
                    SizedBox(width: 12),
                    Text(anyUnlocked ? 'Lock chats' : 'Unlock chats'),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _conversationTile(Conversation conversation, ThemeConfig theme) {
    final dataStore = widget.dataStore;
    final homePrefs = widget.preferencesController.home;
    final otherParticipantId = conversation.participantIds.firstWhere(
      (id) => id != dataStore.currentUser.id,
      orElse: () => '',
    );
    final otherUser = otherParticipantId.isEmpty ? null : dataStore.getUser(otherParticipantId);
    final presenceLabel = conversation.type == ConversationType.direct ? _formatLastSeen(otherUser) : '';
    final isOnline = otherUser?.presence == PresenceState.online || otherUser?.presence == PresenceState.typing;
    final isSelected = _selectedConversationIds.contains(conversation.id);
    final isLocked = widget.preferencesController.isConversationLocked(conversation.id);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 160),
        decoration: BoxDecoration(
          color: isSelected
              ? theme.accentColor.withValues(alpha: 0.18)
              : conversation.isPinned
                  ? theme.cardColor
                  : Colors.transparent,
          borderRadius: BorderRadius.circular(16),
          border: isSelected
              ? Border.all(color: theme.accentColor.withValues(alpha: 0.5), width: 1.5)
              : null,
        ),
        child: Material(
          color: Colors.transparent,
          borderRadius: BorderRadius.circular(16),
          child: InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: () => _handleConversationTap(conversation),
            onLongPress: () => _handleConversationLongPress(conversation),
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 72),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
                child: Row(
                  children: [
                    Stack(
                      children: [
                        ChatyAvatar(
                          initials: conversation.avatarInitials ?? conversation.title.characters.take(2).toString().toUpperCase(),
                          color: conversation.avatarColorHex == null ? theme.accentColor : Color(int.parse(conversation.avatarColorHex!)),
                          size: 50,
                          shape: homePrefs.avatarShape,
                        ),
                        if (isSelected)
                          Positioned.fill(
                            child: Container(
                              decoration: BoxDecoration(
                                color: theme.accentColor.withValues(alpha: 0.85),
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(
                                Icons.check_rounded,
                                color: Colors.white,
                                size: 26,
                              ),
                            ),
                          )
                        else if (isOnline)
                          Positioned(
                            right: 0,
                            bottom: 0,
                            child: Container(
                              width: 13,
                              height: 13,
                              decoration: BoxDecoration(
                                color: theme.successColor,
                                shape: BoxShape.circle,
                                border: Border.all(color: theme.backgroundColor, width: 2.2),
                              ),
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Expanded(
                                child: Row(
                                  children: [
                                    if (isLocked) ...[
                                      Icon(Icons.lock_rounded, size: 14, color: theme.accentColor),
                                      const SizedBox(width: 4),
                                    ],
                                    Flexible(
                                      child: Text(
                                        conversation.title,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: TextStyle(
                                          color: theme.primaryTextColor,
                                          fontSize: 15 * theme.fontScale,
                                          fontWeight: conversation.unreadCount > 0 ? FontWeight.w800 : FontWeight.w600,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(width: 8),
                              Text(
                                _formatMessageTime(conversation.lastMessageTime),
                                style: TextStyle(
                                  color: conversation.unreadCount > 0 ? theme.accentColor : theme.secondaryTextColor,
                                  fontSize: 11.5,
                                  fontWeight: conversation.unreadCount > 0 ? FontWeight.w700 : FontWeight.w500,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 3),
                          Row(
                            children: [
                              Expanded(
                                child: Text(
                                  conversation.lastMessageText.isEmpty ? presenceLabel : conversation.lastMessageText,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    color: conversation.unreadCount > 0 ? theme.primaryTextColor : theme.secondaryTextColor,
                                    fontSize: 12.5 * theme.fontScale,
                                  ),
                                ),
                              ),
                              if (presenceLabel.isNotEmpty && conversation.lastMessageText.isNotEmpty) ...[
                                const SizedBox(width: 8),
                                ConstrainedBox(
                                  constraints: const BoxConstraints(maxWidth: 105),
                                  child: Text(
                                    presenceLabel,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    textAlign: TextAlign.right,
                                    style: TextStyle(
                                      color: isOnline ? theme.successColor : theme.secondaryTextColor.withValues(alpha: 0.85),
                                      fontSize: 9.5,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ),
                              ],
                              if (conversation.isMuted) ...[
                                const SizedBox(width: 6),
                                Icon(Icons.volume_off_rounded, size: 14, color: theme.secondaryTextColor),
                              ],
                              if (conversation.isPinned) ...[
                                const SizedBox(width: 6),
                                Icon(Icons.push_pin_rounded, size: 14, color: theme.secondaryTextColor),
                              ],
                              if (conversation.unreadCount > 0) ...[
                                const SizedBox(width: 7),
                                Container(
                                  constraints: const BoxConstraints(minWidth: 20, minHeight: 20),
                                  padding: const EdgeInsets.symmetric(horizontal: 6),
                                  alignment: Alignment.center,
                                  decoration: BoxDecoration(color: theme.accentColor, borderRadius: BorderRadius.circular(10)),
                                  child: Text(
                                    '${conversation.unreadCount}',
                                    style: TextStyle(color: theme.onAccentColor, fontSize: 10.5, fontWeight: FontWeight.w800),
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ConversationSection {
  final String label;
  const _ConversationSection(this.label);
}

class _SectionLabel extends StatelessWidget {
  final String label;
  final ThemeConfig theme;

  const _SectionLabel({required this.label, required this.theme});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 7, 12, 4),
      child: Text(
        label,
        style: TextStyle(
          color: theme.secondaryTextColor,
          fontSize: 10.5,
          fontWeight: FontWeight.w800,
          letterSpacing: 0.8,
        ),
      ),
    );
  }
}

class _EmptyChats extends StatelessWidget {
  final ThemeConfig theme;
  final VoidCallback onSearch;
  final ConversationType? forcedType;

  const _EmptyChats({
    required this.theme,
    required this.onSearch,
    this.forcedType,
  });

  @override
  Widget build(BuildContext context) {
    final isGroups = forcedType == ConversationType.group;
    final isDirect = forcedType == ConversationType.direct;
    final title = isGroups ? 'No groups yet' : isDirect ? 'No direct chats yet' : 'No conversations yet';
    final description = isGroups
        ? 'Create a group and add people to start a shared conversation.'
        : 'Find a user by @username or create a group to start a conversation.';
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(isGroups ? Icons.groups_outlined : Icons.chat_bubble_outline_rounded, size: 56, color: theme.accentColor),
            const SizedBox(height: 18),
            Text(title, style: TextStyle(color: theme.primaryTextColor, fontSize: 18, fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            Text(
              description,
              textAlign: TextAlign.center,
              style: TextStyle(color: theme.secondaryTextColor, height: 1.45),
            ),
            const SizedBox(height: 18),
            FilledButton.icon(
              onPressed: onSearch,
              icon: const Icon(Icons.search_rounded),
              label: Text(isGroups ? 'Find people' : 'Find people'),
            ),
          ],
        ),
      ),
    );
  }
}
