import 'dart:async';

import 'package:flutter/material.dart';

import '../../data/repositories/mock_data_store.dart';
import '../../domain/models/user_profile.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/design_system/design_system.dart';
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
      final conversation = await widget.dataStore.getOrCreateDirectConversation(
        user,
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

  void _showError(Object error) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(error.toString().replaceFirst('Exception: ', '')),
        backgroundColor: context.colors.error,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return ChatyScaffold(
      appBar: ChatyAppBar(
        title: _isCreatingGroup
            ? 'New Group (${_selectedGroupMembers.length})'
            : 'New Conversation',
        leading: ChatyBackButton(
          onPressed: () {
            if (_isCreatingGroup) {
              setState(() {
                _isCreatingGroup = false;
                _selectedGroupMembers.clear();
              });
            } else {
              Navigator.of(context).maybePop();
            }
          },
        ),
        actions: [
          if (_isLoading)
            const Center(
              child: Padding(
                padding: EdgeInsets.symmetric(horizontal: ChatySpacing.base),
                child: SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2.2),
                ),
              ),
            ),
          if (_isCreatingGroup && !_isLoading)
            Padding(
              padding: const EdgeInsets.only(right: ChatySpacing.sm),
              child: TextButton(
                onPressed: _selectedGroupMembers.isNotEmpty
                    ? _finishGroupCreation
                    : null,
                child: Text(
                  'Create',
                  style: TextStyle(
                    color: _selectedGroupMembers.isNotEmpty
                        ? colors.primary
                        : colors.foregroundTertiary,
                    fontWeight: FontWeight.w700,
                    fontSize: 15,
                  ),
                ),
              ),
            ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: ChatySpacing.base,
              vertical: ChatySpacing.sm,
            ),
            child: Column(
              children: [
                if (_isCreatingGroup) ...[
                  ChatyInput(
                    controller: _groupNameCtrl,
                    hintText: 'Enter group subject...',
                    prefixIcon: Icon(
                      Icons.group_work_rounded,
                      color: colors.primary,
                      size: 20,
                    ),
                  ),
                  const SizedBox(height: ChatySpacing.sm),
                ],
                ChatyInput(
                  controller: _searchCtrl,
                  onChanged: _onSearchChanged,
                  hintText: 'Search @username or name...',
                  prefixIcon: Icon(
                    Icons.search_rounded,
                    color: colors.foregroundTertiary,
                    size: 20,
                  ),
                  suffixIcon: _searchCtrl.text.isNotEmpty
                      ? ChatyIconButton(
                          icon: Icons.close_rounded,
                          size: 32,
                          iconSize: 18,
                          onPressed: () {
                            _searchCtrl.clear();
                            _onSearchChanged('');
                          },
                        )
                      : null,
                ),
              ],
            ),
          ),
          if (!_isCreatingGroup) ...[
            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: ChatySpacing.base,
                vertical: ChatySpacing.xs,
              ),
              child: ChatyCard(
                padding: EdgeInsets.zero,
                onTap: () => setState(() => _isCreatingGroup = true),
                child: ChatyListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(ChatySpacing.sm),
                    decoration: BoxDecoration(
                      color: colors.primary.withValues(alpha: 0.12),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.group_add_rounded,
                      color: colors.primary,
                      size: 20,
                    ),
                  ),
                  title: Text(
                    'Create New Group',
                    style: TextStyle(
                      color: colors.foreground,
                      fontWeight: FontWeight.w700,
                      fontSize: 15,
                    ),
                  ),
                  subtitle: Text(
                    'Start an encrypted group with multiple members',
                    style: ChatyTypography.caption(colors.foregroundSecondary),
                  ),
                  trailing: Icon(
                    Icons.chevron_right_rounded,
                    color: colors.foregroundTertiary,
                  ),
                ),
              ),
            ),
            const SizedBox(height: ChatySpacing.xs),
          ],
          Expanded(
            child: _results.isEmpty && !_isLoading
                ? (_searchCtrl.text.trim().length >= 2
                    ? ChatyNoResultsState(
                        query: _searchCtrl.text.trim(),
                        message:
                            'No contacts or users matched your search keyword.',
                        onClear: () {
                          _searchCtrl.clear();
                          _onSearchChanged('');
                        },
                      )
                    : ChatyEmptyState(
                        icon: Icons.person_search_rounded,
                        title: 'Search contacts',
                        message:
                            'Enter a name or @username above to start an encrypted conversation.',
                        iconColor: colors.foregroundTertiary,
                        titleColor: colors.foreground,
                        messageColor: colors.foregroundSecondary,
                      ))
                : ListView.separated(
                    padding: const EdgeInsets.symmetric(
                      horizontal: ChatySpacing.base,
                      vertical: ChatySpacing.xs,
                    ),
                    itemCount: _results.length,
                    separatorBuilder: (_, __) => Divider(
                      height: 1,
                      thickness: 1,
                      indent: 64,
                      color: colors.divider,
                    ),
                    itemBuilder: (context, index) {
                      final user = _results[index];
                      final isSelected = _selectedGroupMembers.contains(
                        user.id,
                      );
                      return ChatyListTile(
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
                            color: colors.foreground,
                            fontSize: 15,
                            fontWeight: FontWeight.w600,
                            letterSpacing: -0.2,
                          ),
                        ),
                        subtitle: Text(
                          '@${user.username}${user.about.isNotEmpty ? ' • ${user.about}' : ''}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: ChatyTypography.caption(
                            colors.foregroundSecondary,
                          ),
                        ),
                        trailing: _isCreatingGroup
                            ? Checkbox(
                                value: isSelected,
                                activeColor: colors.primary,
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                onChanged: (_) => _toggleMember(user.id),
                              )
                            : Icon(
                                Icons.chevron_right_rounded,
                                size: 20,
                                color: colors.foregroundTertiary,
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
