import 'dart:async';

import 'package:flutter/material.dart';

import '../../data/repositories/mock_data_store.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/user_profile.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../ui/core/design_system/chaty_settings_primitives.dart';
import '../../ui/core/theme/theme_config.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../chats/chat_detail_screen.dart';

class GlobalSearchScreen extends StatefulWidget {
  final ThemeConfig theme;
  final MockDataStore dataStore;
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;

  const GlobalSearchScreen({
    super.key,
    required this.theme,
    required this.dataStore,
    required this.preferencesController,
    required this.themeController,
  });

  @override
  State<GlobalSearchScreen> createState() => _GlobalSearchScreenState();
}

class _GlobalSearchScreenState extends State<GlobalSearchScreen> {
  final TextEditingController _searchController = TextEditingController();
  List<UserProfile> _matchedUsers = <UserProfile>[];
  List<Conversation> _matchedConversations = <Conversation>[];
  bool _isSearching = false;
  bool _isOpeningChat = false;
  Timer? _debounce;
  int _searchEpoch = 0;

  @override
  void initState() {
    super.initState();
    _searchController.addListener(_queueSearch);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.removeListener(_queueSearch);
    _searchController.dispose();
    super.dispose();
  }

  void _queueSearch() {
    _debounce?.cancel();
    final query = _searchController.text.trim();
    final lower = query.toLowerCase();
    final conversations = query.isEmpty
        ? <Conversation>[]
        : widget.dataStore.conversations.where((conversation) {
            return conversation.title.toLowerCase().contains(lower) ||
                conversation.lastMessageText.toLowerCase().contains(lower);
          }).toList();

    if (query.length < 2) {
      setState(() {
        _matchedUsers = <UserProfile>[];
        _matchedConversations = conversations;
        _isSearching = false;
      });
      return;
    }

    setState(() {
      _matchedConversations = conversations;
      _isSearching = true;
    });
    final epoch = ++_searchEpoch;
    _debounce = Timer(const Duration(milliseconds: 280), () {
      unawaited(_performRemoteSearch(query, epoch));
    });
  }

  Future<void> _performRemoteSearch(String query, int epoch) async {
    try {
      final users = await widget.dataStore.searchUsersRemote(query);
      if (!mounted || epoch != _searchEpoch) return;
      setState(() {
        _matchedUsers = users;
        _isSearching = false;
      });
    } catch (error) {
      if (!mounted || epoch != _searchEpoch) return;
      setState(() => _isSearching = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Search failed: ${_cleanError(error)}')),
      );
    }
  }

  Future<void> _startConversationWithUser(UserProfile user) async {
    if (_isOpeningChat) return;
    setState(() => _isOpeningChat = true);
    try {
      final conversation = await widget.dataStore.getOrCreateDirectConversation(user);
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => ChatDetailScreen(
            conversationId: conversation.id,
            theme: widget.theme,
            dataStore: widget.dataStore,
            preferencesController: widget.preferencesController,
            themeController: widget.themeController,
          ),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      setState(() => _isOpeningChat = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Unable to open chat: ${_cleanError(error)}')),
      );
    }
  }

  String _cleanError(Object error) =>
      error.toString().replaceFirst('Exception: ', '');

  @override
  Widget build(BuildContext context) {
    final theme = widget.theme;
    return Scaffold(
      backgroundColor: theme.backgroundColor,
      appBar: AppBar(
        backgroundColor: theme.cardColor,
        elevation: 0,
        leading: IconButton(
          icon: Icon(Icons.arrow_back_ios_new_rounded, color: theme.primaryTextColor),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: TextField(
          controller: _searchController,
          autofocus: true,
          style: TextStyle(color: theme.primaryTextColor, fontSize: 16),
          decoration: InputDecoration(
            hintText: 'Search @username, people, groups...',
            hintStyle: TextStyle(
              color: theme.secondaryTextColor.withValues(alpha: 0.6),
            ),
            border: InputBorder.none,
          ),
        ),
        actions: [
          if (_isSearching || _isOpeningChat)
            Padding(
              padding: const EdgeInsets.all(16),
              child: SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  valueColor: AlwaysStoppedAnimation<Color>(theme.accentColor),
                ),
              ),
            )
          else if (_searchController.text.isNotEmpty)
            IconButton(
              icon: Icon(Icons.clear_rounded, color: theme.secondaryTextColor),
              onPressed: _searchController.clear,
            ),
        ],
      ),
      body: _searchController.text.trim().isEmpty
          ? _buildEmptyPrompt(theme)
          : ListView(
              physics: const BouncingScrollPhysics(),
              padding: const EdgeInsets.symmetric(vertical: 12),
              children: [
                if (_matchedUsers.isNotEmpty) ...[
                  _sectionLabel('PEOPLE & USERNAMES', theme),
                  ..._matchedUsers.map((user) => _buildUserTile(user, theme)),
                  const SizedBox(height: 12),
                ],
                if (_matchedConversations.isNotEmpty) ...[
                  _sectionLabel('CHATS & GROUPS', theme),
                  ..._matchedConversations.map(
                    (conversation) => _buildConversationTile(conversation, theme),
                  ),
                ],
                if (!_isSearching &&
                    _matchedUsers.isEmpty &&
                    _matchedConversations.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(32),
                    child: Center(
                      child: Column(
                        children: [
                          Icon(
                            Icons.search_off_rounded,
                            size: 48,
                            color: theme.secondaryTextColor.withValues(alpha: 0.5),
                          ),
                          const SizedBox(height: 12),
                          Text(
                            'No matches found for "${_searchController.text}"',
                            style: TextStyle(
                              color: theme.secondaryTextColor,
                              fontSize: 14,
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

  Widget _sectionLabel(String value, ThemeConfig theme) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Text(
        value,
        style: TextStyle(
          color: theme.accentColor,
          fontSize: 12,
          fontWeight: FontWeight.bold,
          letterSpacing: 0.8,
        ),
      ),
    );
  }

  Widget _buildEmptyPrompt(ThemeConfig theme) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                color: theme.accentColor.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(
                Icons.alternate_email_rounded,
                size: 40,
                color: theme.accentColor,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'Username-First Discovery',
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              'Type an @username to find and message another Chaty user without exposing phone numbers.',
              textAlign: TextAlign.center,
              style: TextStyle(color: theme.secondaryTextColor, fontSize: 13),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildUserTile(UserProfile user, ThemeConfig theme) {
    return ListTile(
      leading: ChatyAvatar(
        initials: user.avatarInitials,
        color: Color(int.parse(user.avatarColorHex)),
        size: 46,
      ),
      title: Row(
        children: [
          Flexible(
            child: Text(
              user.displayName,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: theme.primaryTextColor,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          if (user.isVerified) ...[
            const SizedBox(width: 4),
            Icon(Icons.verified_rounded, size: 16, color: theme.accentColor),
          ],
        ],
      ),
      subtitle: Text(
        '@${user.username} • ${user.about}',
        style: TextStyle(color: theme.secondaryTextColor, fontSize: 13),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: ElevatedButton.icon(
        style: ElevatedButton.styleFrom(
          backgroundColor: theme.accentColor,
          foregroundColor: Colors.white,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          elevation: 0,
        ),
        onPressed: _isOpeningChat ? null : () => _startConversationWithUser(user),
        icon: const Icon(Icons.chat_bubble_outline_rounded, size: 16),
        label: const Text(
          'Message',
          style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
        ),
      ),
    );
  }

  Widget _buildConversationTile(Conversation conversation, ThemeConfig theme) {
    return ListTile(
      leading: ChatyAvatar(
        initials: conversation.avatarInitials ?? 'CH',
        color: conversation.avatarColorHex != null
            ? Color(int.parse(conversation.avatarColorHex!))
            : theme.accentColor,
        size: 46,
      ),
      title: Text(
        conversation.title,
        style: TextStyle(
          color: theme.primaryTextColor,
          fontWeight: FontWeight.bold,
        ),
      ),
      subtitle: Text(
        conversation.lastMessageText,
        style: TextStyle(color: theme.secondaryTextColor, fontSize: 13),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      onTap: () {
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => ChatDetailScreen(
              conversationId: conversation.id,
              theme: theme,
              dataStore: widget.dataStore,
              preferencesController: widget.preferencesController,
              themeController: widget.themeController,
            ),
          ),
        );
      },
    );
  }
}
