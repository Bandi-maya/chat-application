import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../domain/models/preferences.dart';
import '../../../ui/core/theme/theme_config.dart';
import '../../../ui/core/theme/theme_controller.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../data/services/notification_service.dart';
import '../../data/services/contact_relationship_service.dart';
import '../../data/services/rich_chat_realtime_service.dart';
import '../../domain/models/conversation.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/design_system/settings_primitives.dart';
import '../../ui/core/design_system/components/chaty_kit.dart';
import '../../ui/core/design_system/components/app_components.dart';
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
  // P4 large-title collapse: 0 = fully expanded, 1 = fully collapsed.
  double _largeTitleCollapse = 0;

  double get _effectiveTitleCollapse =>
      (_isSelectionMode || _isSearchOpen) ? 1.0 : _largeTitleCollapse;

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
      if (_trackedConversationIds.add(conversation.id))
        unawaited(_realtime.trackConversation(conversation.id));
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
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _searchFocus.requestFocus(),
      );
    } else {
      _searchFocus.unfocus();
    }
  }

  void _clearSelection() => setState(() => _selectedConversationIds.clear());

  void _toggleSelection(String id) {
    HapticFeedback.selectionClick();
    setState(() {
      if (!_selectedConversationIds.add(id))
        _selectedConversationIds.remove(id);
    });
  }

  void _selectAll(List<Conversation> visible) =>
      setState(() => _selectedConversationIds.addAll(visible.map((c) => c.id)));

  void _openChat(Conversation conversation) {
    unawaited(_realtime.trackConversation(conversation.id));
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
    if (widget.preferencesController.isConversationLocked(conversation.id)) {
      AppLockOverlayModal.show(
        context,
        preferencesController: widget.preferencesController,
      ).then((unlocked) {
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
    final selected = widget.dataStore.conversations
        .where((c) => _selectedConversationIds.contains(c.id))
        .toList(growable: false);
    final pin = selected.any((c) => !c.isPinned);
    for (final conversation in selected) {
      if (conversation.isPinned != pin)
        widget.dataStore.togglePinConversation(conversation.id);
    }
    _clearSelection();
  }

  void _toggleArchiveSelected() {
    for (final id in List<String>.from(_selectedConversationIds))
      widget.dataStore.toggleArchiveConversation(id);
    _clearSelection();
  }

  void _toggleMuteSelected() {
    final selected = widget.dataStore.conversations
        .where((c) => _selectedConversationIds.contains(c.id))
        .toList(growable: false);
    final mute = selected.any((c) => !c.isMuted);
    for (final conversation in selected) {
      if (conversation.isMuted != mute)
        widget.dataStore.toggleMuteConversation(conversation.id);
    }
    _clearSelection();
  }

  Future<void> _deleteSelected() async {
    final count = _selectedConversationIds.length;
    final confirmed = await ChatyConfirmDialog.show(
      context,
      title: 'Delete $count chat${count == 1 ? '' : 's'}?',
      message:
          'This removes the selected conversations for this account. Shared media objects are not silently deleted.',
      confirmLabel: 'Delete',
      destructive: true,
    );
    if (confirmed != true) return;
    for (final id in List<String>.from(_selectedConversationIds))
      widget.dataStore.deleteConversation(id);
    _clearSelection();
  }

  void _toggleLockSelected() {
    final ids = List<String>.from(_selectedConversationIds);
    final lock = ids.any(
      (id) => !widget.preferencesController.isConversationLocked(id),
    );
    for (final id in ids)
      widget.preferencesController.toggleLockConversation(id, lock: lock);
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
    if (now.year == local.year &&
        now.month == local.month &&
        now.day == local.day) {
      return '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
    }
    final yesterday = now.subtract(const Duration(days: 1));
    if (yesterday.year == local.year &&
        yesterday.month == local.month &&
        yesterday.day == local.day)
      return 'Yesterday';
    return '${local.day}/${local.month}';
  }

  String _formatLastSeen(String userId) {
    if (_realtime.isOnline(userId)) return 'online';
    final value = _realtime.lastSeenFor(userId);
    if (value == null) return '';
    final local = value.toLocal();
    final now = DateTime.now();
    if (now.year == local.year &&
        now.month == local.month &&
        now.day == local.day) {
      return 'last seen ${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
    }
    return 'last seen ${local.day}/${local.month}';
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

  void _openQrScreen({int initialIndex = 1}) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => LinkedDevicesQrScreen(
          dataStore: widget.dataStore,
          relationshipService: _relationships,
          preferencesController: widget.preferencesController,
          themeController: widget.themeController,
          initialIndex: initialIndex,
          qrOnly: true,
        ),
      ),
    );
  }

  void _openLinkedDevices() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => LinkedDevicesQrScreen(
          dataStore: widget.dataStore,
          relationshipService: _relationships,
          preferencesController: widget.preferencesController,
          themeController: widget.themeController,
          devicesOnly: true,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    _trackConversations();
    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[
        widget.dataStore,
        _realtime,
        widget.preferencesController,
      ]),
      builder: (context, _) {
        final theme = widget.themeController.globalTheme;
        final homePrefs = widget.preferencesController.home;
        // Home Style consumers: each variant maps to REAL structural
        // differences below (filters, sections, stories strip geometry,
        // tile density, unread grouping, split view).
        final homeStyle = homePrefs.homeStyle;
        final styleShowFilters =
            homeStyle != 'Classic' && homeStyle != 'Minimal';
        final styleShowSections = homeStyle != 'Minimal';
        final styleAlwaysLabelSections = homeStyle == 'Classic';
        final styleStoriesFirst = homeStyle == 'Stories First';
        final styleStoriesHidden =
            homeStyle == 'Compact' || homeStyle == 'Minimal';
        final styleStoriesHeight = styleStoriesFirst
            ? 112.0
            : homeStyle == 'Expressive'
            ? 96.0
            : 82.0;
        final styleStoriesAvatar = styleStoriesFirst
            ? 64.0
            : homeStyle == 'Expressive'
            ? 56.0
            : 48.0;
        final styleTileDensity = homeStyle == 'Compact'
            ? 0.88
            : homeStyle == 'Expressive'
            ? 1.12
            : 1.0;
        final styleSplitView = homeStyle == 'Tablet Split View';
        // Stories Style consumers: each value renders the strip differently.
        final storiesStyle = homePrefs.storiesStyle;
        final storyShape = switch (storiesStyle) {
          'Squircle' => 'squircle',
          'Card' => 'roundedSquare',
          _ => 'circle',
        };
        final storyShowOnlineRing = storiesStyle != 'Minimal';
        final storyCardWrap = storiesStyle == 'Card';
        final storyCompact = storiesStyle == 'Compact';
        final storyTileWidth = storyCardWrap
            ? 74.0
            : storyCompact
            ? 48.0
            : 58.0;
        final storyNameFontSize = storyCompact ? 9.0 : 10.5;
        final query = _searchCtrl.text.trim().toLowerCase();
        final conversations = widget.dataStore.conversations
            .where((conversation) {
              if (widget.forcedType != null &&
                  conversation.type != widget.forcedType)
                return false;
              if (query.isNotEmpty &&
                  !conversation.title.toLowerCase().contains(query) &&
                  !conversation.lastMessageText.toLowerCase().contains(query))
                return false;
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
            })
            .toList(growable: false);
        final archived = conversations
            .where((c) => c.isArchived)
            .toList(growable: false);
        final pinned = conversations
            .where((c) => c.isPinned && !c.isArchived)
            .toList(growable: false);
        final unreadFirst = homeStyle == 'Productivity'
            ? conversations
                  .where(
                    (c) => c.unreadCount > 0 && !c.isPinned && !c.isArchived,
                  )
                  .toList(growable: false)
            : const <Conversation>[];
        final recent = conversations
            .where(
              (c) =>
                  !c.isPinned &&
                  !c.isArchived &&
                  !(homeStyle == 'Productivity' && c.unreadCount > 0),
            )
            .toList(growable: false);
        final entries = <Object>[];
        if (archived.isNotEmpty &&
            !_isSearchOpen &&
            // Real consumer: 'Hide archived-chat shortcut on home'.
            !widget.preferencesController.gbBool('key_mas_hide_archive_home'))
          entries.add(_ArchivedEntry(archivedCount: archived.length));
        if (pinned.isNotEmpty) {
          if (styleShowSections)
            entries.add(const _ConversationSection('PINNED'));
          entries.addAll(pinned);
        }
        if (unreadFirst.isNotEmpty) {
          if (styleShowSections)
            entries.add(const _ConversationSection('UNREAD'));
          entries.addAll(unreadFirst);
        }
        if (recent.isNotEmpty) {
          final labelMessages =
              styleAlwaysLabelSections ||
              pinned.isNotEmpty ||
              unreadFirst.isNotEmpty;
          if (styleShowSections && labelMessages)
            entries.add(const _ConversationSection('MESSAGES'));
          entries.addAll(recent);
        }
        final filters = widget.forcedType == null
            ? const <String>['All', 'Unread', 'Groups', 'Direct']
            : const <String>['All', 'Unread'];

        return PopScope(
          canPop: !_isSelectionMode,
          onPopInvokedWithResult: (didPop, result) {
            if (!didPop && _isSelectionMode) _clearSelection();
          },
          child: Scaffold(
            backgroundColor: theme.backgroundColor,
            body: SafeArea(
              child: Column(
                children: [
                  _isSelectionMode
                      ? _selectionAppBar(theme, conversations)
                      : _standardAppBar(theme, homePrefs),
                  // P4: iOS collapsing LARGE title. Shrinks away as the list
                  // scrolls; the compact bar title fades in to replace it.
                  if (!_isSelectionMode)
                    Align(
                      alignment: Alignment.centerLeft,
                      child: SizedBox(
                        height: (1 - _effectiveTitleCollapse) * 44,
                        child: Opacity(
                          opacity: 1 - _effectiveTitleCollapse,
                          child: Padding(
                            padding: const EdgeInsets.fromLTRB(16, 2, 16, 6),
                            child: Align(
                              alignment: Alignment.centerLeft,
                              child: Text(
                                widget.pageTitle ?? 'Chaty',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  color: theme.primaryTextColor,
                                  fontSize: 32 * theme.fontScale,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: -0.5,
                                  height: 1.0,
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  if (!_isSelectionMode) ...[
                    AnimatedSize(
                      duration: const Duration(milliseconds: 180),
                      child: _isSearchOpen && homePrefs.showSearchBar
                          ? Padding(
                              padding: const EdgeInsets.fromLTRB(16, 2, 16, 8),
                              child: TextField(
                                controller: _searchCtrl,
                                focusNode: _searchFocus,
                                onChanged: (_) => setState(() {}),
                                style: TextStyle(color: theme.primaryTextColor),
                                decoration: InputDecoration(
                                  hintText:
                                      widget.forcedType ==
                                          ConversationType.group
                                      ? 'Search groups…'
                                      : 'Search chats and messages…',
                                  hintStyle: TextStyle(
                                    color: theme.secondaryTextColor,
                                  ),
                                  prefixIcon: Icon(
                                    Icons.search_rounded,
                                    color: theme.secondaryTextColor,
                                  ),
                                  suffixIcon: _searchCtrl.text.isEmpty
                                      ? null
                                      : IconButton(
                                          onPressed: () {
                                            _searchCtrl.clear();
                                            setState(() {});
                                          },
                                          icon: const Icon(Icons.close_rounded),
                                        ),
                                  filled: true,
                                  fillColor: theme.cardColor,
                                  border: OutlineInputBorder(
                                    borderRadius: BorderRadius.circular(
                                      theme.cornerRadius,
                                    ),
                                    borderSide: BorderSide.none,
                                  ),
                                ),
                              ),
                            )
                          : const SizedBox.shrink(),
                    ),
                    if ((homePrefs.enableStoriesStrip || styleStoriesFirst) &&
                        !styleStoriesHidden &&
                        widget.forcedType != ConversationType.group)
                      SizedBox(
                        height: styleStoriesHeight,
                        child: ListView.separated(
                          scrollDirection: Axis.horizontal,
                          padding: const EdgeInsets.symmetric(horizontal: 14),
                          itemCount: widget.dataStore.contacts.length,
                          separatorBuilder: (_, __) =>
                              const SizedBox(width: 10),
                          itemBuilder: (context, index) {
                            final contact = widget.dataStore.contacts[index];
                            final online =
                                _realtime.isOnline(contact.id) &&
                                storyShowOnlineRing;
                            Widget entry = SizedBox(
                              width: storyCardWrap
                                  ? storyTileWidth - 8
                                  : storyTileWidth,
                              child: Column(
                                children: [
                                  Stack(
                                    children: [
                                      ChatyAvatar(
                                        initials: contact.avatarInitials,
                                        color: Color(
                                          int.parse(contact.avatarColorHex),
                                        ),
                                        size: storyCompact
                                            ? styleStoriesAvatar * 0.82
                                            : styleStoriesAvatar,
                                        shape: storyShape,
                                      ),
                                      if (online)
                                        Positioned(
                                          right: -1,
                                          bottom: -1,
                                          child: ChatyOnlineDot(
                                            active: true,
                                            avatarSize: storyCompact
                                                ? styleStoriesAvatar * 0.82
                                                : styleStoriesAvatar,
                                            color: theme.successColor,
                                            ringColor: theme.backgroundColor,
                                          ),
                                        ),
                                    ],
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    contact.displayName.split(' ').first,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(
                                      color: storiesStyle == 'Minimal'
                                          ? theme.secondaryTextColor
                                                .withValues(alpha: 0.55)
                                          : theme.secondaryTextColor,
                                      fontSize: storyNameFontSize,
                                    ),
                                  ),
                                ],
                              ),
                            );
                            // 'Card' style wraps every story in a small
                            // surface card; other styles stay bare.
                            if (!storyCardWrap) return entry;
                            return Container(
                              width: storyTileWidth,
                              margin: const EdgeInsets.symmetric(vertical: 2),
                              padding: const EdgeInsets.fromLTRB(4, 6, 4, 6),
                              decoration: BoxDecoration(
                                color: theme.cardColor,
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(
                                  color: theme.surfaceColor,
                                ),
                              ),
                              child: entry,
                            );
                          },
                        ),
                      ),
                    if (styleShowFilters) Align(
                      alignment: Alignment.centerLeft,
                      child: SingleChildScrollView(
                        scrollDirection: Axis.horizontal,
                        padding: const EdgeInsets.fromLTRB(16, 3, 16, 8),
                        child: Row(
                          children: filters
                              .map((filter) {
                                final selected = _selectedFilter == filter;
                                return Padding(
                                  padding: const EdgeInsets.only(right: 8),
                                  child: ChoiceChip(
                                    label: Text(filter),
                                    selected: selected,
                                    selectedColor: theme.accentColor.withValues(
                                      alpha: 0.18,
                                    ),
                                    backgroundColor: theme.cardColor,
                                    side: BorderSide(
                                      color: selected
                                          ? theme.accentColor.withValues(
                                              alpha: 0.35,
                                            )
                                          : theme.surfaceColor,
                                    ),
                                    labelStyle: TextStyle(
                                      color: selected
                                          ? theme.accentColor
                                          : theme.secondaryTextColor,
                                      fontWeight: selected
                                          ? FontWeight.w700
                                          : FontWeight.w500,
                                    ),
                                    onSelected: (value) {
                                      if (value)
                                        setState(
                                          () => _selectedFilter = filter,
                                        );
                                    },
                                  ),
                                );
                              })
                              .toList(growable: false),
                        ),
                      ),
                    ),
                  ],
                  Expanded(
                    // P4: drives the large-title collapse from the primary
                    // list scroll (depth 0 + vertical axis only, so the
                    // horizontal stories strip never triggers it).
                    child: NotificationListener<ScrollNotification>(
                      onNotification: (notification) {
                        if (notification.depth == 0 &&
                            notification.metrics.axis == Axis.vertical) {
                          final value = (notification.metrics.pixels / 44)
                              .clamp(0.0, 1.0);
                          if ((value - _largeTitleCollapse).abs() > 0.02) {
                            setState(() => _largeTitleCollapse = value);
                          }
                        }
                        return false;
                      },
                      child: conversations.isEmpty
                          ? _EmptyChats(
                              theme: theme,
                              onSearch: () => _openGlobalSearch(theme),
                              forcedType: widget.forcedType,
                            )
                          // Home Style consumer: 'Tablet Split View' switches
                          // to list + quick-access pane on wide screens; every
                          // other style renders the single-column list.
                          : styleSplitView
                          ? _splitBody(theme, entries, styleTileDensity)
                          : _conversationsList(
                              theme,
                              entries,
                              styleTileDensity,
                            ),
                    ),
                  ),
                ],
              ),
            ),
            floatingActionButton: _isSelectionMode
                ? null
                : FloatingActionButton(
                    tooltip: widget.forcedType == ConversationType.group
                        ? 'Create group'
                        : 'New chat',
                    backgroundColor: theme.accentColor,
                    foregroundColor: theme.onAccentColor,
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => NewChatScreen(
                          theme: theme,
                          dataStore: widget.dataStore,
                          preferencesController: widget.preferencesController,
                        ),
                      ),
                    ),
                    child: Icon(
                      widget.forcedType == ConversationType.group
                          ? Icons.group_add_rounded
                          : Icons.edit_rounded,
                    ),
                  ),
          ),
        );
      },
    );
  }

  Widget _conversationsList(
    ThemeConfig theme,
    List<Object> entries,
    double density,
  ) {
    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(8, 0, 8, 96),
      cacheExtent: 420,
      itemCount: entries.length,
      itemBuilder: (context, index) {
        final entry = entries[index];
        if (entry is _ArchivedEntry) {
          return _archivedTile(entry.archivedCount, theme);
        }
        if (entry is _ConversationSection) {
          return _SectionLabel(label: entry.label, theme: theme);
        }
        final conversation = entry as Conversation;
        // P4: WhatsApp-iOS swipe actions — Pin, Mute, Archive.
        return ChatySwipeActions(
          backgroundColor: theme.backgroundColor,
          actions: [
            ChatySwipeAction(
              icon: conversation.isPinned
                  ? Icons.push_pin_rounded
                  : Icons.push_pin_outlined,
              label: conversation.isPinned ? 'Unpin' : 'Pin',
              color: context.colors.primary,
              onTriggered: () =>
                  widget.dataStore.togglePinConversation(conversation.id),
            ),
            ChatySwipeAction(
              icon: conversation.isMuted
                  ? Icons.notifications_active_rounded
                  : Icons.notifications_off_rounded,
              label: conversation.isMuted ? 'Unmute' : 'Mute',
              color: context.colors.warning,
              onTriggered: () =>
                  widget.dataStore.toggleMuteConversation(conversation.id),
            ),
            ChatySwipeAction(
              icon: Icons.archive_outlined,
              label: 'Archive',
              color: context.colors.foregroundSecondary,
              onTriggered: () =>
                  widget.dataStore.toggleArchiveConversation(conversation.id),
            ),
          ],
          child: _conversationTile(conversation, theme, density: density),
        );
      },
    );
  }

  /// 'Tablet Split View' Home Style: on sufficiently wide layouts the
  /// conversation list sits beside a real quick-access pane listing pinned
  /// chats and groups that open directly. Narrow layouts fall back to the
  /// standard single column.
  Widget _splitBody(ThemeConfig theme, List<Object> entries, double density) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final list = _conversationsList(theme, entries, density);
        if (constraints.maxWidth < 640) return list;
        return Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SizedBox(width: 360, child: list),
            VerticalDivider(width: 1, color: theme.surfaceColor),
            Expanded(
              child: _SplitQuickPane(
                theme: theme,
                pinned: widget.dataStore.conversations
                    .where((c) => c.isPinned && !c.isArchived)
                    .toList(growable: false),
                groups: widget.dataStore.conversations
                    .where(
                      (c) =>
                          c.type == ConversationType.group && !c.isArchived,
                    )
                    .toList(growable: false),
                onOpen: _handleConversationTap,
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _archivedTile(int count, ThemeConfig theme) {
    return InkWell(
      onTap: () {
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => _ArchivedChatsScreen(
              dataStore: widget.dataStore,
              preferencesController: widget.preferencesController,
              themeController: widget.themeController,
              notificationService: widget.notificationService,
            ),
          ),
        );
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Icon(
              Icons.archive_outlined,
              color: theme.secondaryTextColor,
              size: 22,
            ),
            const SizedBox(width: 24),
            Expanded(
              child: Text(
                'Archived',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 16 * theme.fontScale,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            Text(
              '$count',
              style: TextStyle(
                color: theme.accentColor,
                fontSize: 13,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _standardAppBar(ThemeConfig theme, HomePreferences homePrefs) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 4, 6),
      child: Row(
        children: [
          Expanded(
            child: Text(
              widget.pageTitle ?? 'Chaty',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 22 * theme.fontScale,
                fontWeight: FontWeight.w800,
                letterSpacing: -0.4,
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
            tooltip: 'QR / Scan',
            color: theme.primaryTextColor,
            onPressed: () => _openQrScreen(initialIndex: 1),
            icon: const Icon(Icons.qr_code_scanner_rounded),
          ),
          if (homePrefs.showSearchBar)
            IconButton(
              tooltip: _isSearchOpen ? 'Close search' : 'Search chats',
              color: theme.primaryTextColor,
              onPressed: _toggleSearch,
              icon: Icon(
                _isSearchOpen ? Icons.close_rounded : Icons.search_rounded,
              ),
            ),
          IconButton(
            tooltip: 'Toggle light / dark',
            color: theme.primaryTextColor,
            onPressed: widget.themeController.toggleBrightness,
            icon: Icon(
              theme.brightness == Brightness.dark
                  ? Icons.light_mode_rounded
                  : Icons.dark_mode_rounded,
            ),
          ),
          PopupMenuButton<String>(
            tooltip: 'More',
            icon: Icon(Icons.more_vert_rounded, color: theme.primaryTextColor),
            onSelected: (value) {
              switch (value) {
                case 'linked':
                  _openLinkedDevices();
                  break;
              }
            },
            itemBuilder: (_) => const [
              PopupMenuItem(
                value: 'linked',
                child: ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(Icons.devices_rounded),
                  title: Text('Linked devices'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _selectionAppBar(ThemeConfig theme, List<Conversation> visible) {
    final selected = widget.dataStore.conversations
        .where((c) => _selectedConversationIds.contains(c.id))
        .toList(growable: false);
    final anyUnpinned = selected.any((c) => !c.isPinned);
    final anyUnmuted = selected.any((c) => !c.isMuted);
    final anyUnlocked = _selectedConversationIds.any(
      (id) => !widget.preferencesController.isConversationLocked(id),
    );
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
      color: theme.cardColor,
      child: Row(
        children: [
          IconButton(
            tooltip: 'Clear selection',
            icon: const Icon(Icons.close_rounded),
            color: theme.primaryTextColor,
            onPressed: _clearSelection,
          ),
          Text(
            '${_selectedConversationIds.length}',
            style: TextStyle(
              color: theme.primaryTextColor,
              fontSize: 19,
              fontWeight: FontWeight.w800,
            ),
          ),
          const Spacer(),
          IconButton(
            visualDensity: VisualDensity.compact,
            tooltip: anyUnpinned ? 'Pin' : 'Unpin',
            onPressed: _togglePinSelected,
            icon: Icon(
              anyUnpinned ? Icons.push_pin_outlined : Icons.push_pin_rounded,
              color: theme.primaryTextColor,
            ),
          ),
          IconButton(
            visualDensity: VisualDensity.compact,
            tooltip: 'Delete',
            onPressed: _deleteSelected,
            icon: Icon(
              Icons.delete_outline_rounded,
              color: theme.primaryTextColor,
            ),
          ),
          IconButton(
            visualDensity: VisualDensity.compact,
            tooltip: anyUnmuted ? 'Mute' : 'Unmute',
            onPressed: _toggleMuteSelected,
            icon: Icon(
              anyUnmuted ? Icons.volume_off_outlined : Icons.volume_up_outlined,
              color: theme.primaryTextColor,
            ),
          ),
          IconButton(
            visualDensity: VisualDensity.compact,
            tooltip: 'Archive',
            onPressed: _toggleArchiveSelected,
            icon: Icon(Icons.archive_outlined, color: theme.primaryTextColor),
          ),
          PopupMenuButton<String>(
            tooltip: 'More actions',
            icon: Icon(Icons.more_vert_rounded, color: theme.primaryTextColor),
            onSelected: (value) {
              if (value == 'lock') _toggleLockSelected();
              if (value == 'unread')
                _markSelectedReadUnread(markAsUnread: true);
              if (value == 'read') _markSelectedReadUnread(markAsUnread: false);
              if (value == 'all') _selectAll(visible);
            },
            itemBuilder: (_) => [
              PopupMenuItem(
                value: 'lock',
                child: ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(
                    anyUnlocked
                        ? Icons.lock_outline_rounded
                        : Icons.lock_open_rounded,
                  ),
                  title: Text(anyUnlocked ? 'Lock chat' : 'Unlock chat'),
                ),
              ),
              const PopupMenuItem(
                value: 'unread',
                child: ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(Icons.mark_chat_unread_outlined),
                  title: Text('Mark as unread'),
                ),
              ),
              const PopupMenuItem(
                value: 'read',
                child: ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(Icons.mark_chat_read_outlined),
                  title: Text('Mark as read'),
                ),
              ),
              const PopupMenuItem(
                value: 'all',
                child: ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(Icons.select_all_rounded),
                  title: Text('Select all'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _conversationTile(
    Conversation conversation,
    ThemeConfig theme, {
    double density = 1.0,
  }) {
    final otherId = conversation.participantIds.firstWhere(
      (id) => id != widget.dataStore.currentUser.id,
      orElse: () => '',
    );
    final online =
        conversation.type == ConversationType.direct &&
        otherId.isNotEmpty &&
        _realtime.isOnline(otherId) &&
        // Real consumer: 'Show online state in chat list rows'.
        widget.preferencesController.gbBool('onlinechat', fallback: true);
    final activity = otherId.isEmpty
        ? null
        : _realtime.activityFor(conversation.id, otherId);
    final presence =
        conversation.type == ConversationType.direct && otherId.isNotEmpty
        ? activity?.isRecording == true
              ? 'recording…'
              : activity?.isTyping == true
              ? 'typing…'
              : _formatLastSeen(otherId)
        : '';
    final selected = _selectedConversationIds.contains(conversation.id);
    final locked = widget.preferencesController.isConversationLocked(
      conversation.id,
    );
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
      child: Material(
        color: selected
            ? theme.accentColor.withValues(alpha: 0.18)
            : conversation.isPinned
            ? theme.cardColor
            : Colors.transparent,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: () => _handleConversationTap(conversation),
          onLongPress: () => _handleConversationLongPress(conversation),
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: 72 * density),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
              child: Row(
                children: [
                  Stack(
                    children: [
                      ChatyAvatar(
                        initials:
                            conversation.avatarInitials ??
                            conversation.title.characters
                                .take(2)
                                .toString()
                                .toUpperCase(),
                        color: conversation.avatarColorHex == null
                            ? theme.accentColor
                            : Color(int.parse(conversation.avatarColorHex!)),
                        size: 50 * density,
                        shape: widget.preferencesController.home.avatarShape,
                      ),
                      if (selected)
                        Positioned.fill(
                          child: Container(
                            decoration: BoxDecoration(
                              color: theme.accentColor.withValues(alpha: 0.86),
                              shape: BoxShape.circle,
                            ),
                            child: Icon(
                              Icons.check_rounded,
                              color: theme.onAccentColor,
                              size: 26,
                            ),
                          ),
                        )
                      else if (online &&
                          widget.preferencesController.gbBool(
                            'onlineDotchat',
                            fallback: true,
                          ))
                        // WA-iOS presence dot sits bottom-right.
                        Positioned(
                          right: -1,
                          bottom: -1,
                          child: ChatyOnlineDot(
                            active: true,
                            avatarSize: 50 * density,
                            color:
                                widget.preferencesController.gbColor(
                                  'onlineDotchatColor',
                                ) ??
                                theme.successColor,
                            ringColor: theme.backgroundColor,
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
                                  if (locked) ...[
                                    Icon(
                                      Icons.lock_rounded,
                                      size: 14,
                                      color: theme.accentColor,
                                    ),
                                    const SizedBox(width: 4),
                                  ],
                                  Flexible(
                                    child: Text(
                                      conversation.title,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        color: theme.primaryTextColor,
                                        // WA-iOS row metrics: 16pt name.
                                        fontSize: 16 * density * theme.fontScale,
                                        fontWeight: conversation.unreadCount > 0
                                            ? FontWeight.w800
                                            : FontWeight.w600,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 8),
                            ChatyTimeLabel(
                              text: _formatMessageTime(
                                conversation.lastMessageTime,
                              ),
                              highlight: conversation.unreadCount > 0,
                              color: theme.secondaryTextColor,
                              highlightColor: theme.accentColor,
                            ),
                          ],
                        ),
                        const SizedBox(height: 4),
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                activity?.isTyping == true ||
                                        activity?.isRecording == true
                                    ? presence
                                    : conversation.lastMessageText.isEmpty
                                    ? presence
                                    : conversation.lastMessageText,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  color:
                                      activity?.isTyping == true ||
                                          activity?.isRecording == true
                                      ? theme.successColor
                                      : conversation.unreadCount > 0
                                      ? theme.primaryTextColor
                                      : theme.secondaryTextColor,
                                  // WA-iOS: 13.5pt gray preview.
                                  fontSize: 13.5 * density * theme.fontScale,
                                  fontWeight:
                                      activity?.isTyping == true ||
                                          activity?.isRecording == true
                                      ? FontWeight.w700
                                      : FontWeight.w400,
                                ),
                              ),
                            ),
                            if (presence.isNotEmpty &&
                                conversation.lastMessageText.isNotEmpty &&
                                activity?.isTyping != true &&
                                activity?.isRecording != true) ...[
                              const SizedBox(width: 8),
                              ConstrainedBox(
                                constraints: const BoxConstraints(
                                  maxWidth: 105,
                                ),
                                child: Text(
                                  presence,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    // Real consumers: online / last-seen
                                    // text colors for chat rows.
                                    color: online
                                        ? widget.preferencesController.gbColor(
                                              'ModOnlineColor',
                                            ) ??
                                            theme.successColor
                                        : widget.preferencesController.gbColor(
                                              'ModlastseenColor',
                                            ) ??
                                            theme.secondaryTextColor,
                                    fontSize: 9.5,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                            ],
                            if (conversation.isMuted) ...[
                              const SizedBox(width: 6),
                              Icon(
                                Icons.volume_off_rounded,
                                size: 14,
                                color: theme.secondaryTextColor,
                              ),
                            ],
                            if (conversation.isPinned) ...[
                              const SizedBox(width: 6),
                              Icon(
                                Icons.push_pin_rounded,
                                size: 14,
                                color: theme.secondaryTextColor,
                              ),
                            ],
                            if (conversation.unreadCount > 0) ...[
                              const SizedBox(width: 7),
                              // Real consumer: unread badge color keys.
                              ChatyCountBadge(
                                count: conversation.unreadCount,
                                color:
                                    widget.preferencesController.gbColor(
                                      'HomeCounterBK',
                                    ) ??
                                    theme.accentColor,
                                textColor:
                                    widget.preferencesController.gbColor(
                                      'HomeCounterText',
                                    ) ??
                                    theme.onAccentColor,
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
    );
  }
}

class _ConversationSection {
  final String label;
  const _ConversationSection(this.label);
}

class _ArchivedEntry {
  final int archivedCount;
  const _ArchivedEntry({required this.archivedCount});
}

class _SectionLabel extends StatelessWidget {
  final String label;
  final ThemeConfig theme;
  const _SectionLabel({required this.label, required this.theme});

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 7, 12, 4),
    child: ChatySectionHeader(
      text: label,
      color: theme.secondaryTextColor,
      fontSize: 10.5,
    ),
  );
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
    final groups = forcedType == ConversationType.group;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              groups
                  ? Icons.groups_outlined
                  : Icons.chat_bubble_outline_rounded,
              size: 56,
              color: theme.accentColor,
            ),
            const SizedBox(height: 18),
            Text(
              groups ? 'No groups yet' : 'No conversations yet',
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 18,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              groups
                  ? 'Create a group and add people to start a shared conversation.'
                  : 'Find a user by @username or scan their Chaty QR code to start a conversation.',
              textAlign: TextAlign.center,
              style: TextStyle(color: theme.secondaryTextColor, height: 1.45),
            ),
            const SizedBox(height: 18),
            FilledButton.icon(
              onPressed: onSearch,
              icon: const Icon(Icons.search_rounded),
              label: const Text('Find people'),
            ),
          ],
        ),
      ),
    );
  }
}

class _ArchivedChatsScreen extends StatefulWidget {
  final MockDataStore dataStore;
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;
  final ChatyNotificationService notificationService;

  const _ArchivedChatsScreen({
    required this.dataStore,
    required this.preferencesController,
    required this.themeController,
    required this.notificationService,
  });

  @override
  State<_ArchivedChatsScreen> createState() => _ArchivedChatsScreenState();
}

class _ArchivedChatsScreenState extends State<_ArchivedChatsScreen> {
  final Set<String> _selectedConversationIds = <String>{};

  void _clearSelection() => setState(() => _selectedConversationIds.clear());

  void _toggleSelection(String id) {
    setState(() {
      if (_selectedConversationIds.contains(id)) {
        _selectedConversationIds.remove(id);
      } else {
        _selectedConversationIds.add(id);
      }
    });
  }

  void _unarchiveSelected() {
    for (final id in List<String>.from(_selectedConversationIds)) {
      widget.dataStore.toggleArchiveConversation(id);
    }
    _clearSelection();
  }

  Future<void> _deleteSelected() async {
    final count = _selectedConversationIds.length;
    final confirmed = await ChatyConfirmDialog.show(
      context,
      title: 'Delete $count chat${count == 1 ? '' : 's'}?',
      message: 'This removes the selected conversations for this account.',
      confirmLabel: 'Delete',
      destructive: true,
    );
    if (confirmed != true) return;
    for (final id in List<String>.from(_selectedConversationIds)) {
      widget.dataStore.deleteConversation(id);
    }
    _clearSelection();
  }

  String _formatMessageTime(DateTime value) {
    final now = DateTime.now();
    final local = value.toLocal();
    if (now.year == local.year &&
        now.month == local.month &&
        now.day == local.day) {
      return '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
    }
    final yesterday = now.subtract(const Duration(days: 1));
    if (yesterday.year == local.year &&
        yesterday.month == local.month &&
        yesterday.day == local.day)
      return 'Yesterday';
    return '${local.day}/${local.month}';
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.dataStore,
      builder: (context, _) {
        final theme = widget.themeController.globalTheme;
        final archived = widget.dataStore.conversations
            .where((c) => c.isArchived)
            .toList(growable: false);
        final isSelectionMode = _selectedConversationIds.isNotEmpty;

        return Scaffold(
          backgroundColor: theme.backgroundColor,
          appBar: isSelectionMode
              ? AppBar(
                  backgroundColor: theme.cardColor,
                  foregroundColor: theme.primaryTextColor,
                  leading: Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: ChatyBackButton(onPressed: _clearSelection),
                  ),
                  title: Text(
                    '${_selectedConversationIds.length}',
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  actions: [
                    IconButton(
                      tooltip: 'Unarchive',
                      icon: const Icon(Icons.unarchive_outlined),
                      onPressed: _unarchiveSelected,
                    ),
                    IconButton(
                      tooltip: 'Delete',
                      icon: const Icon(Icons.delete_outline_rounded),
                      onPressed: _deleteSelected,
                    ),
                  ],
                )
              : AppBar(
                  backgroundColor: theme.surfaceColor,
                  foregroundColor: theme.primaryTextColor,
                  leading: const Padding(
                    padding: EdgeInsets.all(8.0),
                    child: ChatyBackButton(),
                  ),
                  title: const Text('Archived'),
                ),
          body: archived.isEmpty
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.archive_outlined,
                        size: 56,
                        color: theme.secondaryTextColor,
                      ),
                      const SizedBox(height: 16),
                      Text(
                        'No archived chats',
                        style: TextStyle(
                          color: theme.primaryTextColor,
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Chats you archive will appear here',
                        style: TextStyle(color: theme.secondaryTextColor),
                      ),
                    ],
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  itemCount: archived.length,
                  itemBuilder: (context, index) {
                    final conversation = archived[index];
                    final isSelected = _selectedConversationIds.contains(
                      conversation.id,
                    );

                    return InkWell(
                      onTap: () {
                        if (isSelectionMode) {
                          _toggleSelection(conversation.id);
                        } else {
                          Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => ChatDetailScreen(
                                conversationId: conversation.id,
                                theme: theme,
                                dataStore: widget.dataStore,
                                preferencesController:
                                    widget.preferencesController,
                                themeController: widget.themeController,
                              ),
                            ),
                          );
                        }
                      },
                      onLongPress: () {
                        HapticFeedback.mediumImpact();
                        _toggleSelection(conversation.id);
                      },
                      child: Container(
                        color: isSelected
                            ? theme.accentColor.withValues(alpha: 0.12)
                            : Colors.transparent,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 10,
                        ),
                        child: Row(
                          children: [
                            ChatyAvatar(
                              initials:
                                  conversation.avatarInitials ??
                                  conversation.title.characters
                                      .take(2)
                                      .toString()
                                      .toUpperCase(),
                              color: conversation.avatarColorHex == null
                                  ? theme.accentColor
                                  : Color(
                                      int.parse(conversation.avatarColorHex!),
                                    ),
                              size: 48,
                              shape:
                                  widget.preferencesController.home.avatarShape,
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Expanded(
                                        child: Text(
                                          conversation.title,
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: TextStyle(
                                            color: theme.primaryTextColor,
                                            fontSize: 16 * theme.fontScale,
                                            fontWeight:
                                                conversation.unreadCount > 0
                                                ? FontWeight.w800
                                                : FontWeight.w600,
                                          ),
                                        ),
                                      ),
                                      ChatyTimeLabel(
                                        text: _formatMessageTime(
                                          conversation.lastMessageTime,
                                        ),
                                        highlight: conversation.unreadCount > 0,
                                        color: theme.secondaryTextColor,
                                        highlightColor: theme.accentColor,
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 4),
                                  Row(
                                    children: [
                                      Expanded(
                                        child: Text(
                                          conversation.lastMessageText.isEmpty
                                              ? 'No messages'
                                              : conversation.lastMessageText,
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: TextStyle(
                                            color: conversation.unreadCount > 0
                                                ? theme.primaryTextColor
                                                : theme.secondaryTextColor,
                                            fontSize: 12.5 * theme.fontScale,
                                          ),
                                        ),
                                      ),
                                      if (conversation.unreadCount > 0)
                                        Container(
                                          margin: const EdgeInsets.only(
                                            left: 6,
                                          ),
                                          padding: const EdgeInsets.symmetric(
                                            horizontal: 6,
                                            vertical: 2,
                                          ),
                                          decoration: BoxDecoration(
                                            color: theme.accentColor,
                                            borderRadius: BorderRadius.circular(
                                              10,
                                            ),
                                          ),
                                          child: Text(
                                            '${conversation.unreadCount}',
                                            style: TextStyle(
                                              color: theme.onAccentColor,
                                              fontSize: 10.5,
                                              fontWeight: FontWeight.w800,
                                            ),
                                          ),
                                        ),
                                    ],
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
        );
      },
    );
  }
}

/// Quick-access pane for the 'Tablet Split View' Home Style. Lists pinned
/// chats and groups; every entry opens its real conversation screen.
class _SplitQuickPane extends StatelessWidget {
  final ThemeConfig theme;
  final List<Conversation> pinned;
  final List<Conversation> groups;
  final ValueChanged<Conversation> onOpen;

  const _SplitQuickPane({
    required this.theme,
    required this.pinned,
    required this.groups,
    required this.onOpen,
  });

  @override
  Widget build(BuildContext context) {
    final hasContent = pinned.isNotEmpty || groups.isNotEmpty;
    if (!hasContent) {
      return Container(
        color: theme.surfaceColor.withValues(alpha: 0.35),
        alignment: Alignment.center,
        padding: const EdgeInsets.all(24),
        child: Text(
          'Pin chats or create groups to see them here.',
          textAlign: TextAlign.center,
          style: TextStyle(color: theme.secondaryTextColor, fontSize: 12.5),
        ),
      );
    }
    return Container(
      color: theme.surfaceColor.withValues(alpha: 0.35),
      child: ListView(
        padding: const EdgeInsets.all(14),
        children: [
          if (pinned.isNotEmpty) ...[
            Text(
              'PINNED CHATS',
              style: TextStyle(
                color: theme.secondaryTextColor,
                fontSize: 10.5,
                fontWeight: FontWeight.w800,
                letterSpacing: 0.8,
              ),
            ),
            const SizedBox(height: 6),
            for (final conversation in pinned) _paneTile(conversation),
            const SizedBox(height: 14),
          ],
          if (groups.isNotEmpty) ...[
            Text(
              'GROUPS',
              style: TextStyle(
                color: theme.secondaryTextColor,
                fontSize: 10.5,
                fontWeight: FontWeight.w800,
                letterSpacing: 0.8,
              ),
            ),
            const SizedBox(height: 6),
            for (final conversation in groups) _paneTile(conversation),
          ],
        ],
      ),
    );
  }

  Widget _paneTile(Conversation conversation) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Material(
        color: theme.cardColor,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: () => onOpen(conversation),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 9),
            child: Row(
              children: [
                ChatyAvatar(
                  initials:
                      conversation.avatarInitials ??
                      conversation.title.characters
                          .take(2)
                          .toString()
                          .toUpperCase(),
                  color: conversation.avatarColorHex == null
                      ? theme.accentColor
                      : Color(int.parse(conversation.avatarColorHex!)),
                  size: 34,
                  shape: 'circle',
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    conversation.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: theme.primaryTextColor,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                if (conversation.unreadCount > 0)
                  Text(
                    '${conversation.unreadCount}',
                    style: TextStyle(
                      color: theme.accentColor,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
