import 'dart:async';

import 'package:flutter/material.dart';

import '../../data/repositories/mock_data_store.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/user_profile.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../data/services/local_lock_service.dart';
import '../../injection/locator.dart';
import '../chats/chat_detail_screen.dart';
import '../chats/locked_chats_screen.dart';

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

    // Check secret search phrase for revealing locked vault
    if (query.isNotEmpty) {
      final lockService = locator<LocalLockService>();
      lockService.verifySecretPhrase(query).then((isMatch) {
        if (isMatch && mounted) {
          _searchController.clear();
          LockedChatsScreen.open(
            context,
            dataStore: widget.dataStore,
            preferencesController: widget.preferencesController,
            themeController: widget.themeController,
          );
        }
      });
    }

    final conversations = query.isEmpty
        ? <Conversation>[]
        : widget.dataStore.conversations.where((conversation) {
            // Privacy rule: Exclude hidden locked chats from global search
            if (widget.preferencesController.isConversationHidden(conversation.id)) {
              return false;
            }
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
      final conversation = await widget.dataStore.getOrCreateDirectConversation(
        user,
      );
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
    final themeData = Theme.of(context);

    return ChatyScaffold(
      appBar: ChatyAppBar(
        leading: const ChatyBackButton(),
        titleWidget: Container(
          height: 40,
          decoration: BoxDecoration(
            color: context.colors.inputFill,
            borderRadius: BorderRadius.circular(ChatyRadius.full),
          ),
          child: TextField(
            controller: _searchController,
            autofocus: true,
            style: TextStyle(color: context.colors.foreground, fontSize: 15),
            decoration: InputDecoration(
              contentPadding: const EdgeInsets.symmetric(
                horizontal: ChatySpacing.base,
                vertical: 10,
              ),
              hintText: 'Search @username, people, groups...',
              hintStyle: ChatyTypography.caption(
                themeData.colorScheme.onSurface.withValues(alpha: 0.5),
              ),
              border: InputBorder.none,
              prefixIcon: Icon(
                Icons.search_rounded,
                size: 20,
                color: themeData.colorScheme.onSurface.withValues(alpha: 0.5),
              ),
            ),
          ),
        ),
        actions: [
          if (_isSearching || _isOpeningChat)
            const Padding(
              padding: EdgeInsets.all(16),
              child: SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            )
          else if (_searchController.text.isNotEmpty)
            ChatyIconButton(
              icon: Icons.clear_rounded,
              tooltip: 'Clear',
              onPressed: _searchController.clear,
            ),
        ],
      ),
      body: _searchController.text.trim().isEmpty
          ? _buildEmptyPrompt(themeData)
          : ListView(
              physics: const BouncingScrollPhysics(),
              padding: const EdgeInsets.symmetric(
                horizontal: ChatySpacing.base,
                vertical: ChatySpacing.md,
              ),
              children: [
                if (_matchedUsers.isNotEmpty)
                  ChatyGroupedSection(
                    title: 'People & Usernames',
                    children: [
                      for (final user in _matchedUsers)
                        _buildUserTile(user, themeData),
                    ],
                  ),
                if (_matchedConversations.isNotEmpty)
                  ChatyGroupedSection(
                    title: 'Chats & Groups',
                    children: [
                      for (final conversation in _matchedConversations)
                        _buildConversationTile(conversation, themeData),
                    ],
                  ),
                if (!_isSearching &&
                    _matchedUsers.isEmpty &&
                    _matchedConversations.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(ChatySpacing.xl),
                    child: Center(
                      child: Column(
                        children: [
                          Icon(
                            Icons.search_off_rounded,
                            size: 48,
                            color: themeData.colorScheme.onSurface.withValues(
                              alpha: 0.35,
                            ),
                          ),
                          const SizedBox(height: ChatySpacing.md),
                          Text(
                            'No matches found for "${_searchController.text}"',
                            style: ChatyTypography.caption(
                              themeData.colorScheme.onSurface.withValues(
                                alpha: 0.6,
                              ),
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

  Widget _buildEmptyPrompt(ThemeData themeData) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: ChatySpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 76,
              height: 76,
              decoration: BoxDecoration(
                color: themeData.colorScheme.primary.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(
                Icons.alternate_email_rounded,
                size: 38,
                color: themeData.colorScheme.primary,
              ),
            ),
            const SizedBox(height: ChatySpacing.base),
            Text(
              'Username Discovery',
              style: ChatyTypography.headline(themeData.colorScheme.onSurface),
            ),
            const SizedBox(height: 6),
            Text(
              'Type an @username to find and message another Chaty user without exposing phone numbers.',
              textAlign: TextAlign.center,
              style: ChatyTypography.caption(
                themeData.colorScheme.onSurface.withValues(alpha: 0.65),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildUserTile(UserProfile user, ThemeData themeData) {
    return ChatyListTile(
      leading: AppAvatar(
        initials: user.avatarInitials,
        colorHex: user.avatarColorHex,
        size: 42,
      ),
      title: Row(
        children: [
          Flexible(
            child: Text(
              user.displayName,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: themeData.colorScheme.onSurface,
                fontWeight: FontWeight.w600,
                fontSize: 15,
              ),
            ),
          ),
          if (user.isVerified) ...[
            const SizedBox(width: 4),
            Icon(
              Icons.verified_rounded,
              size: 15,
              color: themeData.colorScheme.primary,
            ),
          ],
        ],
      ),
      subtitle: Text(
        '@${user.username}${user.about.isNotEmpty ? ' • ${user.about}' : ''}',
        style: ChatyTypography.caption(
          themeData.colorScheme.onSurface.withValues(alpha: 0.6),
        ),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: ChatyPrimaryButton(
        text: 'Message',
        height: 34,
        width: 90,
        isLoading: _isOpeningChat,
        onPressed: () => _startConversationWithUser(user),
      ),
    );
  }

  Widget _buildConversationTile(
    Conversation conversation,
    ThemeData themeData,
  ) {
    return ChatyListTile(
      leading: AppAvatar(
        initials: conversation.avatarInitials ?? 'CH',
        colorHex: conversation.avatarColorHex ?? '0xFF6366F1',
        size: 42,
      ),
      title: Text(
        conversation.title,
        style: TextStyle(
          color: themeData.colorScheme.onSurface,
          fontWeight: FontWeight.w600,
          fontSize: 15,
        ),
      ),
      subtitle: Text(
        conversation.lastMessageText,
        style: ChatyTypography.caption(
          themeData.colorScheme.onSurface.withValues(alpha: 0.6),
        ),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: Icon(
        Icons.chevron_right_rounded,
        color: themeData.colorScheme.onSurface.withValues(alpha: 0.35),
      ),
      onTap: () {
        Navigator.of(context).push(
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
      },
    );
  }
}
