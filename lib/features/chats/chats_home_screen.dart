import 'package:flutter/material.dart';

import '../../../ui/core/theme/theme_config.dart';
import '../../../ui/core/theme/theme_controller.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/user_profile.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../data/services/chaty_notification_service.dart';
import '../../ui/core/design_system/chaty_settings_primitives.dart';
import 'chat_detail_screen.dart';
import 'new_chat_screen.dart';
import '../search/global_search_screen.dart';

class ChatsHomeScreen extends StatefulWidget {
  final ThemeConfig theme;
  final MockDataStore dataStore;
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;
  final ChatyNotificationService notificationService;

  const ChatsHomeScreen({
    super.key,
    required this.theme,
    required this.dataStore,
    required this.preferencesController,
    required this.themeController,
    required this.notificationService,
  });

  @override
  State<ChatsHomeScreen> createState() => _ChatsHomeScreenState();
}

class _ChatsHomeScreenState extends State<ChatsHomeScreen> {
  final TextEditingController _searchCtrl = TextEditingController();
  final FocusNode _searchFocus = FocusNode();
  String _selectedFilter = 'All';
  bool _isSearchOpen = false;

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

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 10, 6),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      'Chaty',
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
            ),
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
                          hintText: 'Search chats and messages…',
                          hintStyle: TextStyle(color: theme.secondaryTextColor),
                          prefixIcon: Icon(Icons.search_rounded, color: theme.secondaryTextColor),
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
                            borderRadius: BorderRadius.circular(theme.cornerRadius),
                            borderSide: BorderSide.none,
                          ),
                        ),
                      ),
                    )
                  : const SizedBox.shrink(),
            ),
            if (homePrefs.enableStoriesStrip)
              SizedBox(
                height: 82,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 14),
                  itemCount: dataStore.contacts.length,
                  separatorBuilder: (_, __) => const SizedBox(width: 10),
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
                  children: <String>['All', 'Unread', 'Groups', 'Direct'].map((filter) {
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
            Expanded(
              child: conversations.isEmpty
                  ? _EmptyChats(theme: theme, onSearch: () => _openGlobalSearch(theme))
                  : ListView(
                      padding: const EdgeInsets.fromLTRB(8, 0, 8, 96),
                      children: [
                        if (pinned.isNotEmpty) ...[
                          _SectionLabel(label: 'PINNED', theme: theme),
                          ...pinned.map((conversation) => _conversationTile(conversation, theme)),
                          const SizedBox(height: 6),
                        ],
                        if (recent.isNotEmpty) ...[
                          if (pinned.isNotEmpty) _SectionLabel(label: 'MESSAGES', theme: theme),
                          ...recent.map((conversation) => _conversationTile(conversation, theme)),
                        ],
                      ],
                    ),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
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
        child: const Icon(Icons.edit_rounded),
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

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
      child: Material(
        color: conversation.isPinned ? theme.cardColor : Colors.transparent,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: () {
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => ChatDetailScreen(
                  conversationId: conversation.id,
                  theme: theme,
                  dataStore: dataStore,
                  preferencesController: widget.preferencesController,
                  themeController: widget.themeController,
                ),
              ),
            );
          },
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
                    if (isOnline)
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
    );
  }
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
        style: TextStyle(color: theme.secondaryTextColor, fontSize: 10.5, fontWeight: FontWeight.w800, letterSpacing: 0.8),
      ),
    );
  }
}

class _EmptyChats extends StatelessWidget {
  final ThemeConfig theme;
  final VoidCallback onSearch;
  const _EmptyChats({required this.theme, required this.onSearch});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.chat_bubble_outline_rounded, size: 56, color: theme.accentColor),
            const SizedBox(height: 18),
            Text('No conversations yet', style: TextStyle(color: theme.primaryTextColor, fontSize: 18, fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            Text(
              'Find a user by @username or create a group to start a conversation.',
              textAlign: TextAlign.center,
              style: TextStyle(color: theme.secondaryTextColor, height: 1.45),
            ),
            const SizedBox(height: 18),
            FilledButton.icon(onPressed: onSearch, icon: const Icon(Icons.search_rounded), label: const Text('Find people')),
          ],
        ),
      ),
    );
  }
}
