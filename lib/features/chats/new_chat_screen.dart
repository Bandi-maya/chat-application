import 'dart:async';

import 'package:flutter/material.dart';

import '../../data/repositories/mock_data_store.dart';
import '../../domain/models/user_profile.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../ui/core/theme/theme_config.dart';
import '../../ui/core/widgets/app_avatar.dart';
import 'chat_detail_screen.dart';

class NewChatScreen extends StatefulWidget {
  final ThemeConfig theme;
  final MockDataStore dataStore;
  final ChatyPreferencesController? preferencesController;

  const NewChatScreen({
    super.key,
    required this.theme,
    required this.dataStore,
    this.preferencesController,
  });

  @override
  State<NewChatScreen> createState() => _NewChatScreenState();
}

class _NewChatScreenState extends State<NewChatScreen> {
  final TextEditingController _searchCtrl = TextEditingController();
  final Set<String> _selectedGroupMembers = <String>{};
  final TextEditingController _groupNameCtrl = TextEditingController();
  List<UserProfile> _results = <UserProfile>[];
  bool _isCreatingGroup = false;
  bool _isLoading = false;
  Timer? _debounce;
  int _epoch = 0;

  @override
  void initState() {
    super.initState();
    _results = widget.dataStore.contacts;
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchCtrl.dispose();
    _groupNameCtrl.dispose();
    super.dispose();
  }

  void _onSearchChanged(String value) {
    _debounce?.cancel();
    final query = value.trim();
    if (query.length < 2) {
      setState(() {
        _results = widget.dataStore.contacts;
        _isLoading = false;
      });
      return;
    }
    final epoch = ++_epoch;
    setState(() => _isLoading = true);
    _debounce = Timer(const Duration(milliseconds: 280), () {
      unawaited(_searchUsers(query, epoch));
    });
  }

  Future<void> _searchUsers(String query, int epoch) async {
    try {
      final users = await widget.dataStore.searchUsersRemote(query);
      if (!mounted || epoch != _epoch) return;
      setState(() {
        _results = users;
        _isLoading = false;
      });
    } catch (error) {
      if (!mounted || epoch != _epoch) return;
      setState(() => _isLoading = false);
      _showError(error);
    }
  }

  Future<void> _finishGroupCreation() async {
    final title = _groupNameCtrl.text.trim();
    if (title.isEmpty || _selectedGroupMembers.isEmpty || _isLoading) return;
    setState(() => _isLoading = true);
    try {
      final conversation = await widget.dataStore.createGroupAsync(
        title: title,
        memberIds: _selectedGroupMembers.toList(),
      );
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => ChatDetailScreen(
            theme: widget.theme,
            dataStore: widget.dataStore,
            conversationId: conversation.id,
            preferencesController:
                widget.preferencesController ?? ChatyPreferencesController(),
          ),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      setState(() => _isLoading = false);
      _showError(error);
    }
  }

  Future<void> _openDirectChat(UserProfile user) async {
    if (_isLoading) return;
    setState(() => _isLoading = true);
    try {
      final conversation =
          await widget.dataStore.getOrCreateDirectConversation(user);
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => ChatDetailScreen(
            theme: widget.theme,
            dataStore: widget.dataStore,
            conversationId: conversation.id,
            preferencesController:
                widget.preferencesController ?? ChatyPreferencesController(),
          ),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      setState(() => _isLoading = false);
      _showError(error);
    }
  }

  void _showError(Object error) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(error.toString().replaceFirst('Exception: ', '')),
        backgroundColor: widget.theme.dangerColor,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = widget.theme;
    return Scaffold(
      backgroundColor: theme.backgroundColor,
      appBar: AppBar(
        title: Text(
          _isCreatingGroup
              ? 'New Group (${_selectedGroupMembers.length})'
              : 'New Chat',
        ),
        actions: [
          if (_isLoading)
            const Padding(
              padding: EdgeInsets.all(18),
              child: SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ),
          if (_isCreatingGroup && !_isLoading)
            TextButton(
              onPressed: _selectedGroupMembers.isNotEmpty
                  ? _finishGroupCreation
                  : null,
              child: Text(
                'Create',
                style: TextStyle(
                  color: _selectedGroupMembers.isNotEmpty
                      ? theme.accentColor
                      : theme.secondaryTextColor,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              children: [
                if (_isCreatingGroup) ...[
                  TextField(
                    controller: _groupNameCtrl,
                    style: TextStyle(color: theme.primaryTextColor),
                    maxLength: 100,
                    decoration: InputDecoration(
                      counterText: '',
                      hintText: 'Enter group subject...',
                      hintStyle: TextStyle(color: theme.secondaryTextColor),
                      filled: true,
                      fillColor: theme.cardColor,
                      prefixIcon: const Icon(Icons.group_work_rounded),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(theme.cornerRadius),
                        borderSide: BorderSide.none,
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                ],
                TextField(
                  controller: _searchCtrl,
                  onChanged: _onSearchChanged,
                  style: TextStyle(color: theme.primaryTextColor),
                  decoration: InputDecoration(
                    hintText: 'Search @username or display name...',
                    hintStyle: TextStyle(color: theme.secondaryTextColor),
                    filled: true,
                    fillColor: theme.cardColor,
                    prefixIcon: const Icon(Icons.search_rounded),
                    suffixIcon: _searchCtrl.text.isNotEmpty
                        ? IconButton(
                            icon: const Icon(Icons.close_rounded),
                            onPressed: () {
                              _searchCtrl.clear();
                              _onSearchChanged('');
                            },
                          )
                        : null,
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 10,
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(theme.cornerRadius),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (!_isCreatingGroup)
            ListTile(
              leading: Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: theme.accentColor.withValues(alpha: 0.2),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.group_add_rounded,
                  color: theme.accentColor,
                  size: 22,
                ),
              ),
              title: Text(
                'Create New Group',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontWeight: FontWeight.bold,
                  fontSize: 14.5 * theme.fontScale,
                ),
              ),
              subtitle: Text(
                'Add multiple Chaty users with server-enforced membership',
                style: TextStyle(
                  color: theme.secondaryTextColor,
                  fontSize: 12,
                ),
              ),
              onTap: () => setState(() => _isCreatingGroup = true),
            ),
          const Divider(height: 1),
          Expanded(
            child: _results.isEmpty && !_isLoading
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(32),
                      child: Text(
                        _searchCtrl.text.trim().length < 2
                            ? 'Search for another Chaty user by username or name.'
                            : 'No matching Chaty users found.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: theme.secondaryTextColor),
                      ),
                    ),
                  )
                : ListView.separated(
                    itemCount: _results.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final user = _results[index];
                      final isSelected = _selectedGroupMembers.contains(user.id);
                      return ListTile(
                        leading: AppAvatar(
                          initials: user.avatarInitials,
                          colorHex: user.avatarColorHex,
                          size: 42,
                          showOnlineBadge: true,
                          presence: user.presence,
                        ),
                        title: Text(
                          user.displayName,
                          style: TextStyle(
                            color: theme.primaryTextColor,
                            fontSize: 14 * theme.fontScale,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        subtitle: Text(
                          '@${user.username} • ${user.about}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: theme.secondaryTextColor,
                            fontSize: 12,
                          ),
                        ),
                        trailing: _isCreatingGroup
                            ? Checkbox(
                                value: isSelected,
                                activeColor: theme.accentColor,
                                onChanged: (_) => _toggleMember(user.id),
                              )
                            : const Icon(
                                Icons.chat_bubble_outline_rounded,
                                size: 18,
                              ),
                        onTap: _isCreatingGroup
                            ? () => _toggleMember(user.id)
                            : () => _openDirectChat(user),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  void _toggleMember(String userId) {
    setState(() {
      if (!_selectedGroupMembers.add(userId)) {
        _selectedGroupMembers.remove(userId);
      }
    });
  }
}
