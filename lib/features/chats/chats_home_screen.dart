import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../domain/models/chaty_preferences.dart';
import '../../../ui/core/theme/theme_config.dart';
import '../../../ui/core/theme/theme_controller.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../data/services/chaty_notification_service.dart';
import '../../data/services/contact_relationship_service.dart';
import '../../data/services/rich_chat_realtime_service.dart';
import '../../domain/models/conversation.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../ui/core/design_system/chaty_settings_primitives.dart';
import '../search/global_search_screen.dart';
import '../settings/security/app_lock_overlay.dart';
import 'chat_detail_screen.dart';
import 'linked_devices_qr_screen.dart';
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
  final Set<String> _trackedConversationIds = <String>{};
  late final RichChatRealtimeService _realtime;
  late final ContactRelationshipService _relationships;
  String _selectedFilter = 'All';
  bool _isSearchOpen = false;

  bool get _isSelectionMode => _selectedConversationIds.isNotEmpty;

  @override
  void initState() {
    super.initState();
    _realtime = locator<RichChatRealtimeService>();
    _relationships = locator<ContactRelationshipService>();
    WidgetsBinding.instance.addPostFrameCallback((_) => _trackConversations());
  }

  void _trackConversations() {
    for (final conversation in widget.dataStore.conversations) {
      if (_trackedConversationIds.add(conversation.id)) unawaited(_realtime.trackConversation(conversation.id));
    }
  }

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

  void _clearSelection() => setState(() => _selectedConversationIds.clear());

  void _toggleSelection(String id) {
    HapticFeedback.selectionClick();
    setState(() {
      if (!_selectedConversationIds.add(id)) _selectedConversationIds.remove(id);
    });
  }

  void _selectAll(List<Conversation> visible) => setState(() => _selectedConversationIds.addAll(visible.map((c) => c.id)));

  void _openChat(Conversation conversation) {
    unawaited(_realtime.trackConversation(conversation.id));
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => ChatDetailScreen(
        conversationId: conversation.id,
        theme: widget.themeController.globalTheme,
        dataStore: widget.dataStore,
        preferencesController: widget.preferencesController,
        themeController: widget.themeController,
      ),
    ));
  }

  void _handleConversationTap(Conversation conversation) {
    if (_isSelectionMode) {
      _toggleSelection(conversation.id);
      return;
    }
    if (widget.preferencesController.isConversationLocked(conversation.id)) {
      AppLockOverlayModal.show(context, preferencesController: widget.preferencesController).then((unlocked) {
        if (unlocked == true && mounted) _openChat(conversation);
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
    final selected = widget.dataStore.conversations.where((c) => _selectedConversationIds.contains(c.id)).toList(growable: false);
    final pin = selected.any((c) => !c.isPinned);
    for (final conversation in selected) {
      if (conversation.isPinned != pin) widget.dataStore.togglePinConversation(conversation.id);
    }
    _clearSelection();
  }

  void _toggleArchiveSelected() {
    for (final id in List<String>.from(_selectedConversationIds)) widget.dataStore.toggleArchiveConversation(id);
    _clearSelection();
  }

  void _toggleMuteSelected() {
    final selected = widget.dataStore.conversations.where((c) => _selectedConversationIds.contains(c.id)).toList(growable: false);
    final mute = selected.any((c) => !c.isMuted);
    for (final conversation in selected) {
      if (conversation.isMuted != mute) widget.dataStore.toggleMuteConversation(conversation.id);
    }
    _clearSelection();
  }

  Future<void> _deleteSelected() async {
    final count = _selectedConversationIds.length;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Delete $count chat${count == 1 ? '' : 's'}?'),
        content: const Text('This removes the selected conversations for this account. Shared media objects are not silently deleted.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Delete')),
        ],
      ),
    );
    if (confirmed != true) return;
    for (final id in List<String>.from(_selectedConversationIds)) widget.dataStore.deleteConversation(id);
    _clearSelection();
  }

  void _toggleLockSelected() {
    final ids = List<String>.from(_selectedConversationIds);
    final lock = ids.any((id) => !widget.preferencesController.isConversationLocked(id));
    for (final id in ids) widget.preferencesController.toggleLockConversation(id, lock: lock);
    _clearSelection();
  }

  void _markSelectedReadUnread({required bool markAsUnread}) {
    for (final id in _selectedConversationIds) {
      if (markAsUnread) {
        widget.dataStore.markAsUnread(id);
      } else {
        widget.dataStore.markAsRead(id);
      }
    }
    _clearSelection();
  }

  String _formatMessageTime(DateTime value) {
    final now = DateTime.now();
    final local = value.toLocal();
    if (now.year == local.year && now.month == local.month && now.day == local.day) {
      return '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
    }
    final yesterday = now.subtract(const Duration(days: 1));
    if (yesterday.year == local.year && yesterday.month == local.month && yesterday.day == local.day) return 'Yesterday';
    return '${local.day}/${local.month}';
  }

  String _formatLastSeen(String userId) {
    if (_realtime.isOnline(userId)) return 'online';
    final value = _realtime.lastSeenFor(userId);
    if (value == null) return '';
    final local = value.toLocal();
    final now = DateTime.now();
    if (now.year == local.year && now.month == local.month && now.day == local.day) {
      return 'last seen ${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
    }
    return 'last seen ${local.day}/${local.month}';
  }

  void _openGlobalSearch(ThemeConfig theme) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => GlobalSearchScreen(
        theme: theme,
        dataStore: widget.dataStore,
        preferencesController: widget.preferencesController,
        themeController: widget.themeController,
      ),
    ));
  }

  void _openLinkedDevices() {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => LinkedDevicesQrScreen(
        dataStore: widget.dataStore,
        relationshipService: _relationships,
        preferencesController: widget.preferencesController,
        themeController: widget.themeController,
      ),
    ));
  }

  @override
  Widget build(BuildContext context) {
    _trackConversations();
    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[widget.dataStore, _realtime]),
      builder: (context, _) {
        final theme = widget.themeController.globalTheme;
        final homePrefs = widget.preferencesController.home;
        final query = _searchCtrl.text.trim().toLowerCase();
        final conversations = widget.dataStore.conversations.where((conversation) {
          if (widget.forcedType != null && conversation.type != widget.forcedType) return false;
          if (query.isNotEmpty && !conversation.title.toLowerCase().contains(query) && !conversation.lastMessageText.toLowerCase().contains(query)) return false;
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
        final pinned = conversations.where((c) => c.isPinned && !c.isArchived).toList(growable: false);
        final recent = conversations.where((c) => !c.isPinned && !c.isArchived).toList(growable: false);
        final entries = <Object>[];
        if (pinned.isNotEmpty) entries..add(const _ConversationSection('PINNED'))..addAll(pinned);
        if (recent.isNotEmpty) {
          if (pinned.isNotEmpty) entries.add(const _ConversationSection('MESSAGES'));
          entries.addAll(recent);
        }
        final filters = widget.forcedType == null ? const <String>['All', 'Unread', 'Groups', 'Direct'] : const <String>['All', 'Unread'];

        return PopScope(
          canPop: !_isSelectionMode,
          onPopInvokedWithResult: (didPop, result) {
            if (!didPop && _isSelectionMode) _clearSelection();
          },
          child: Scaffold(
            backgroundColor: theme.backgroundColor,
            body: SafeArea(
              child: Column(children: [
                _isSelectionMode ? _selectionAppBar(theme, conversations) : _standardAppBar(theme, homePrefs),
                if (!_isSelectionMode) ...[
                  AnimatedSize(
                    duration: const Duration(milliseconds: 180),
                    child: _isSearchOpen
                        ? Padding(
                            padding: const EdgeInsets.fromLTRB(16, 2, 16, 8),
                            child: TextField(
                              controller: _searchCtrl,
                              focusNode: _searchFocus,
                              onChanged: (_) => setState(() {}),
                              style: TextStyle(color: theme.primaryTextColor),
                              decoration: InputDecoration(
                                hintText: widget.forcedType == ConversationType.group ? 'Search groups…' : 'Search chats and messages…',
                                hintStyle: TextStyle(color: theme.secondaryTextColor),
                                prefixIcon: Icon(Icons.search_rounded, color: theme.secondaryTextColor),
                                suffixIcon: _searchCtrl.text.isEmpty ? null : IconButton(onPressed: () { _searchCtrl.clear(); setState(() {}); }, icon: const Icon(Icons.close_rounded)),
                                filled: true,
                                fillColor: theme.cardColor,
                                border: OutlineInputBorder(borderRadius: BorderRadius.circular(theme.cornerRadius), borderSide: BorderSide.none),
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
                        itemCount: widget.dataStore.contacts.length,
                        separatorBuilder: (_, __) => const SizedBox(width: 10),
                        itemBuilder: (context, index) {
                          final contact = widget.dataStore.contacts[index];
                          final online = _realtime.isOnline(contact.id);
                          return SizedBox(
                            width: 58,
                            child: Column(children: [
                              Stack(children: [
                                ChatyAvatar(initials: contact.avatarInitials, color: Color(int.parse(contact.avatarColorHex)), size: 48, shape: 'circle'),
                                if (online)
                                  Positioned(right: 0, top: 0, child: Container(width: 12, height: 12, decoration: BoxDecoration(color: theme.successColor, shape: BoxShape.circle, border: Border.all(color: theme.backgroundColor, width: 2)))),
                              ]),
                              const SizedBox(height: 4),
                              Text(contact.displayName.split(' ').first, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: theme.secondaryTextColor, fontSize: 10.5)),
                            ]),
                          );
                        },
                      ),
                    ),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.fromLTRB(16, 3, 16, 8),
                      child: Row(children: filters.map((filter) {
                        final selected = _selectedFilter == filter;
                        return Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: ChoiceChip(
                            label: Text(filter),
                            selected: selected,
                            selectedColor: theme.accentColor.withValues(alpha: 0.18),
                            backgroundColor: theme.cardColor,
                            side: BorderSide(color: selected ? theme.accentColor.withValues(alpha: 0.35) : theme.surfaceColor),
                            labelStyle: TextStyle(color: selected ? theme.accentColor : theme.secondaryTextColor, fontWeight: selected ? FontWeight.w700 : FontWeight.w500),
                            onSelected: (value) { if (value) setState(() => _selectedFilter = filter); },
                          ),
                        );
                      }).toList(growable: false)),
                    ),
                  ),
                ],
                Expanded(
                  child: conversations.isEmpty
                      ? _EmptyChats(theme: theme, onSearch: () => _openGlobalSearch(theme), forcedType: widget.forcedType)
                      : ListView.builder(
                          padding: const EdgeInsets.fromLTRB(8, 0, 8, 96),
                          cacheExtent: 420,
                          itemCount: entries.length,
                          itemBuilder: (context, index) {
                            final entry = entries[index];
                            return entry is _ConversationSection ? _SectionLabel(label: entry.label, theme: theme) : _conversationTile(entry as Conversation, theme);
                          },
                        ),
                ),
              ]),
            ),
            floatingActionButton: _isSelectionMode
                ? null
                : FloatingActionButton(
                    tooltip: widget.forcedType == ConversationType.group ? 'Create group' : 'New chat',
                    backgroundColor: theme.accentColor,
                    foregroundColor: theme.onAccentColor,
                    onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => NewChatScreen(theme: theme, dataStore: widget.dataStore, preferencesController: widget.preferencesController))),
                    child: Icon(widget.forcedType == ConversationType.group ? Icons.group_add_rounded : Icons.edit_rounded),
                  ),
          ),
        );
      },
    );
  }

  Widget _standardAppBar(ThemeConfig theme, HomePreferences homePrefs) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 4, 6),
      child: Row(children: [
        Expanded(child: Text(widget.pageTitle ?? 'Chaty', style: TextStyle(color: theme.primaryTextColor, fontSize: 24 * theme.fontScale, fontWeight: FontWeight.w800, letterSpacing: -0.5))),
        if (homePrefs.showCameraIcon) IconButton(tooltip: 'Camera', color: theme.primaryTextColor, onPressed: () => _openGlobalSearch(theme), icon: const Icon(Icons.camera_alt_outlined)),
        IconButton(tooltip: _isSearchOpen ? 'Close search' : 'Search chats', color: theme.primaryTextColor, onPressed: _toggleSearch, icon: Icon(_isSearchOpen ? Icons.close_rounded : Icons.search_rounded)),
        IconButton(tooltip: 'Toggle light / dark', color: theme.primaryTextColor, onPressed: widget.themeController.toggleBrightness, icon: Icon(theme.brightness == Brightness.dark ? Icons.light_mode_rounded : Icons.dark_mode_rounded)),
        PopupMenuButton<String>(
          tooltip: 'More',
          icon: Icon(Icons.more_vert_rounded, color: theme.primaryTextColor),
          onSelected: (value) {
            switch (value) {
              case 'linked':
                _openLinkedDevices();
                break;
              case 'new_chat':
                Navigator.of(context).push(MaterialPageRoute(builder: (_) => NewChatScreen(theme: theme, dataStore: widget.dataStore, preferencesController: widget.preferencesController)));
                break;
              case 'search':
                _openGlobalSearch(theme);
                break;
            }
          },
          itemBuilder: (_) => const [
            PopupMenuItem(value: 'linked', child: ListTile(contentPadding: EdgeInsets.zero, leading: Icon(Icons.devices_rounded), title: Text('Linked devices & QR'))),
            PopupMenuItem(value: 'new_chat', child: ListTile(contentPadding: EdgeInsets.zero, leading: Icon(Icons.chat_bubble_outline_rounded), title: Text('New chat'))),
            PopupMenuItem(value: 'search', child: ListTile(contentPadding: EdgeInsets.zero, leading: Icon(Icons.search_rounded), title: Text('Search'))),
          ],
        ),
      ]),
    );
  }

  Widget _selectionAppBar(ThemeConfig theme, List<Conversation> visible) {
    final selected = widget.dataStore.conversations.where((c) => _selectedConversationIds.contains(c.id)).toList(growable: false);
    final anyUnpinned = selected.any((c) => !c.isPinned);
    final anyUnmuted = selected.any((c) => !c.isMuted);
    final anyUnlocked = _selectedConversationIds.any((id) => !widget.preferencesController.isConversationLocked(id));
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
      color: theme.cardColor,
      child: Row(children: [
        IconButton(tooltip: 'Clear selection', icon: const Icon(Icons.arrow_back_rounded), color: theme.primaryTextColor, onPressed: _clearSelection),
        Text('${_selectedConversationIds.length}', style: TextStyle(color: theme.primaryTextColor, fontSize: 19, fontWeight: FontWeight.w800)),
        const Spacer(),
        IconButton(tooltip: anyUnpinned ? 'Pin' : 'Unpin', onPressed: _togglePinSelected, icon: Icon(anyUnpinned ? Icons.push_pin_outlined : Icons.push_pin_rounded)),
        IconButton(tooltip: 'Delete', onPressed: _deleteSelected, icon: const Icon(Icons.delete_outline_rounded)),
        IconButton(tooltip: anyUnmuted ? 'Mute' : 'Unmute', onPressed: _toggleMuteSelected, icon: Icon(anyUnmuted ? Icons.volume_off_outlined : Icons.volume_up_outlined)),
        IconButton(tooltip: 'Archive', onPressed: _toggleArchiveSelected, icon: const Icon(Icons.archive_outlined)),
        IconButton(tooltip: anyUnlocked ? 'Lock chat' : 'Unlock chat', onPressed: _toggleLockSelected, icon: Icon(anyUnlocked ? Icons.lock_outline_rounded : Icons.lock_open_rounded)),
        PopupMenuButton<String>(
          onSelected: (value) {
            if (value == 'unread') _markSelectedReadUnread(markAsUnread: true);
            if (value == 'read') _markSelectedReadUnread(markAsUnread: false);
            if (value == 'all') _selectAll(visible);
          },
          itemBuilder: (_) => const [
            PopupMenuItem(value: 'unread', child: Text('Mark as unread')),
            PopupMenuItem(value: 'read', child: Text('Mark as read')),
            PopupMenuItem(value: 'all', child: Text('Select all')),
          ],
        ),
      ]),
    );
  }

  Widget _conversationTile(Conversation conversation, ThemeConfig theme) {
    final otherId = conversation.participantIds.firstWhere((id) => id != widget.dataStore.currentUser.id, orElse: () => '');
    final online = conversation.type == ConversationType.direct && otherId.isNotEmpty && _realtime.isOnline(otherId);
    final activity = otherId.isEmpty ? null : _realtime.activityFor(conversation.id, otherId);
    final presence = conversation.type == ConversationType.direct && otherId.isNotEmpty
        ? activity?.isRecording == true
            ? 'recording…'
            : activity?.isTyping == true
                ? 'typing…'
                : _formatLastSeen(otherId)
        : '';
    final selected = _selectedConversationIds.contains(conversation.id);
    final locked = widget.preferencesController.isConversationLocked(conversation.id);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
      child: Material(
        color: selected ? theme.accentColor.withValues(alpha: 0.18) : conversation.isPinned ? theme.cardColor : Colors.transparent,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: () => _handleConversationTap(conversation),
          onLongPress: () => _handleConversationLongPress(conversation),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 72),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
              child: Row(children: [
                Stack(children: [
                  ChatyAvatar(initials: conversation.avatarInitials ?? conversation.title.characters.take(2).toString().toUpperCase(), color: conversation.avatarColorHex == null ? theme.accentColor : Color(int.parse(conversation.avatarColorHex!)), size: 50, shape: 'circle'),
                  if (selected)
                    Positioned.fill(child: Container(decoration: BoxDecoration(color: theme.accentColor.withValues(alpha: 0.86), shape: BoxShape.circle), child: const Icon(Icons.check_rounded, color: Colors.white, size: 26)))
                  else if (online)
                    Positioned(right: 0, top: 0, child: Container(width: 13, height: 13, decoration: BoxDecoration(color: theme.successColor, shape: BoxShape.circle, border: Border.all(color: theme.backgroundColor, width: 2.2)))),
                ]),
                const SizedBox(width: 12),
                Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    Expanded(child: Row(children: [
                      if (locked) ...[Icon(Icons.lock_rounded, size: 14, color: theme.accentColor), const SizedBox(width: 4)],
                      Flexible(child: Text(conversation.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: theme.primaryTextColor, fontSize: 15 * theme.fontScale, fontWeight: conversation.unreadCount > 0 ? FontWeight.w800 : FontWeight.w600))),
                    ])),
                    const SizedBox(width: 8),
                    Text(_formatMessageTime(conversation.lastMessageTime), style: TextStyle(color: conversation.unreadCount > 0 ? theme.accentColor : theme.secondaryTextColor, fontSize: 11.5, fontWeight: conversation.unreadCount > 0 ? FontWeight.w700 : FontWeight.w500)),
                  ]),
                  const SizedBox(height: 4),
                  Row(children: [
                    Expanded(child: Text(activity?.isTyping == true || activity?.isRecording == true ? presence : conversation.lastMessageText.isEmpty ? presence : conversation.lastMessageText, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: activity?.isTyping == true || activity?.isRecording == true ? theme.successColor : conversation.unreadCount > 0 ? theme.primaryTextColor : theme.secondaryTextColor, fontSize: 12.5 * theme.fontScale, fontWeight: activity?.isTyping == true || activity?.isRecording == true ? FontWeight.w700 : FontWeight.w400))),
                    if (presence.isNotEmpty && conversation.lastMessageText.isNotEmpty && activity?.isTyping != true && activity?.isRecording != true) ...[
                      const SizedBox(width: 8),
                      ConstrainedBox(constraints: const BoxConstraints(maxWidth: 105), child: Text(presence, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: online ? theme.successColor : theme.secondaryTextColor, fontSize: 9.5, fontWeight: FontWeight.w600))),
                    ],
                    if (conversation.isMuted) ...[const SizedBox(width: 6), Icon(Icons.volume_off_rounded, size: 14, color: theme.secondaryTextColor)],
                    if (conversation.isPinned) ...[const SizedBox(width: 6), Icon(Icons.push_pin_rounded, size: 14, color: theme.secondaryTextColor)],
                    if (conversation.unreadCount > 0) ...[
                      const SizedBox(width: 7),
                      Container(constraints: const BoxConstraints(minWidth: 20, minHeight: 20), padding: const EdgeInsets.symmetric(horizontal: 6), alignment: Alignment.center, decoration: BoxDecoration(color: theme.accentColor, borderRadius: BorderRadius.circular(10)), child: Text('${conversation.unreadCount}', style: TextStyle(color: theme.onAccentColor, fontSize: 10.5, fontWeight: FontWeight.w800))),
                    ],
                  ]),
                ])),
              ]),
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
  Widget build(BuildContext context) => Padding(padding: const EdgeInsets.fromLTRB(12, 7, 12, 4), child: Text(label, style: TextStyle(color: theme.secondaryTextColor, fontSize: 10.5, fontWeight: FontWeight.w800, letterSpacing: 0.8)));
}

class _EmptyChats extends StatelessWidget {
  final ThemeConfig theme;
  final VoidCallback onSearch;
  final ConversationType? forcedType;
  const _EmptyChats({required this.theme, required this.onSearch, this.forcedType});

  @override
  Widget build(BuildContext context) {
    final groups = forcedType == ConversationType.group;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Icon(groups ? Icons.groups_outlined : Icons.chat_bubble_outline_rounded, size: 56, color: theme.accentColor),
          const SizedBox(height: 18),
          Text(groups ? 'No groups yet' : 'No conversations yet', style: TextStyle(color: theme.primaryTextColor, fontSize: 18, fontWeight: FontWeight.w800)),
          const SizedBox(height: 8),
          Text(groups ? 'Create a group and add people to start a shared conversation.' : 'Find a user by @username or scan their Chaty QR code to start a conversation.', textAlign: TextAlign.center, style: TextStyle(color: theme.secondaryTextColor, height: 1.45)),
          const SizedBox(height: 18),
          FilledButton.icon(onPressed: onSearch, icon: const Icon(Icons.search_rounded), label: const Text('Find people')),
        ]),
      ),
    );
  }
}
