import 'dart:async';

import 'package:flutter/material.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../domain/models/chat_message.dart';
import '../../domain/models/conversation.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/theme/app_theme.dart';
import '../chats/chat_detail_screen.dart';

/// A live, account-scoped list of messages the signed-in user has starred.
/// Star state is read from the backend-backed message snapshot; unstar uses the
/// same authoritative per-user message-state RPC as the message context menu.
class StarredMessagesScreen extends StatefulWidget {
  const StarredMessagesScreen({
    super.key,
    required this.dataStore,
    required this.theme,
    required this.preferencesController,
    this.themeController,
  });

  final ChatyDataStore dataStore;
  final ThemeConfig theme;
  final ChatyPreferencesController preferencesController;
  final ThemeController? themeController;

  @override
  State<StarredMessagesScreen> createState() => _StarredMessagesScreenState();
}

class _StarredMessagesScreenState extends State<StarredMessagesScreen> {
  final TextEditingController _searchController = TextEditingController();
  String _query = '';
  bool _loading = true;
  String? _loadError;

  @override
  void initState() {
    super.initState();
    widget.dataStore.addListener(_onStoreChanged);
    unawaited(_loadConversations());
  }

  @override
  void dispose() {
    widget.dataStore.removeListener(_onStoreChanged);
    _searchController.dispose();
    super.dispose();
  }

  void _onStoreChanged() {
    if (mounted) setState(() {});
  }

  Future<void> _loadConversations() async {
    setState(() {
      _loading = true;
      _loadError = null;
    });
    try {
      await Future.wait(
        widget.dataStore.conversations.map(
          (conversation) =>
              widget.dataStore.ensureConversationLoaded(conversation.id),
        ),
      );
    } catch (_) {
      if (mounted) {
        setState(() {
          _loadError = 'Some conversations could not be refreshed. Pull to retry.';
        });
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  List<({Conversation conversation, ChatMessage message})> _starredMessages() {
    final results = <({Conversation conversation, ChatMessage message})>[];
    for (final conversation in widget.dataStore.conversations) {
      for (final message in widget.dataStore.getMessages(conversation.id)) {
        if (!message.isStarred || message.isDeletedForMe) continue;
        final haystack = <String>[
          conversation.title,
          message.text,
          message.type.name,
        ].join(' ').toLowerCase();
        if (_query.isNotEmpty && !haystack.contains(_query)) continue;
        results.add((conversation: conversation, message: message));
      }
    }
    results.sort(
      (a, b) => b.message.createdAt.compareTo(a.message.createdAt),
    );
    return results;
  }

  void _openConversation(Conversation conversation) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ChatDetailScreen(
          conversationId: conversation.id,
          theme: widget.theme,
          dataStore: widget.dataStore,
          preferencesController: widget.preferencesController,
          themeController: widget.themeController,
        ),
      ),
    );
  }

  String _previewText(ChatMessage message) {
    if (message.isDeletedForEveryone) return 'This message was deleted';
    final text = message.text.trim();
    if (text.isNotEmpty) return text;
    return switch (message.type) {
      MessageType.image => 'Photo',
      MessageType.video => 'Video',
      MessageType.audio => 'Voice message',
      MessageType.document => 'Document',
      MessageType.location => 'Location',
      MessageType.contact => 'Contact',
      MessageType.taskCard => 'Task',
      MessageType.system => 'System message',
      MessageType.text => 'Message attachment',
    };
  }

  String _formatDate(DateTime date) {
    final local = date.toLocal();
    final now = DateTime.now();
    if (local.year == now.year &&
        local.month == now.month &&
        local.day == now.day) {
      final hour = local.hour % 12 == 0 ? 12 : local.hour % 12;
      final minute = local.minute.toString().padLeft(2, '0');
      return '$hour:$minute ${local.hour >= 12 ? 'PM' : 'AM'}';
    }
    return '${local.day.toString().padLeft(2, '0')}/${local.month.toString().padLeft(2, '0')}/${local.year}';
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final starred = _starredMessages();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Starred messages'),
        actions: [
          IconButton(
            tooltip: 'Refresh starred messages',
            onPressed: _loading ? null : _loadConversations,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 840),
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                  child: TextField(
                    controller: _searchController,
                    onChanged: (value) => setState(
                      () => _query = value.trim().toLowerCase(),
                    ),
                    textInputAction: TextInputAction.search,
                    decoration: InputDecoration(
                      hintText: 'Search starred messages',
                      prefixIcon: const Icon(Icons.search_rounded),
                      suffixIcon: _query.isEmpty
                          ? null
                          : IconButton(
                              tooltip: 'Clear search',
                              onPressed: () {
                                _searchController.clear();
                                setState(() => _query = '');
                              },
                              icon: const Icon(Icons.close_rounded),
                            ),
                      filled: true,
                      fillColor: colors.surfaceContainerHighest.withValues(
                        alpha: 0.45,
                      ),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(16),
                        borderSide: BorderSide.none,
                      ),
                    ),
                  ),
                ),
                if (_loading) const LinearProgressIndicator(minHeight: 2),
                if (_loadError != null)
                  MaterialBanner(
                    content: Text(_loadError!),
                    leading: const Icon(Icons.sync_problem_rounded),
                    actions: [
                      TextButton(
                        onPressed: _loadConversations,
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                Expanded(
                  child: RefreshIndicator(
                    onRefresh: _loadConversations,
                    child: starred.isEmpty
                        ? ListView(
                            physics: const AlwaysScrollableScrollPhysics(),
                            padding: const EdgeInsets.symmetric(
                              horizontal: 28,
                              vertical: 72,
                            ),
                            children: [
                              Icon(
                                _query.isEmpty
                                    ? Icons.star_border_rounded
                                    : Icons.search_off_rounded,
                                size: 48,
                                color: colors.onSurfaceVariant,
                              ),
                              const SizedBox(height: 14),
                              Text(
                                _query.isEmpty
                                    ? 'No starred messages yet'
                                    : 'No matching messages',
                                textAlign: TextAlign.center,
                                style: Theme.of(context)
                                    .textTheme
                                    .titleMedium
                                    ?.copyWith(fontWeight: FontWeight.w700),
                              ),
                              const SizedBox(height: 8),
                              Text(
                                _query.isEmpty
                                    ? 'Open a message menu and choose Star. Your saved messages will appear here.'
                                    : 'Try another word or conversation name.',
                                textAlign: TextAlign.center,
                                style: Theme.of(context).textTheme.bodyMedium
                                    ?.copyWith(color: colors.onSurfaceVariant),
                              ),
                            ],
                          )
                        : ListView.separated(
                            physics: const AlwaysScrollableScrollPhysics(),
                            padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
                            itemCount: starred.length,
                            separatorBuilder: (_, _) =>
                                const SizedBox(height: 6),
                            itemBuilder: (context, index) {
                              final item = starred[index];
                              final conversation = item.conversation;
                              final message = item.message;
                              return Card(
                                elevation: 0,
                                clipBehavior: Clip.antiAlias,
                                color: colors.surfaceContainerLow,
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(16),
                                  side: BorderSide(
                                    color: colors.outlineVariant.withValues(
                                      alpha: 0.55,
                                    ),
                                  ),
                                ),
                                child: ListTile(
                                  contentPadding: const EdgeInsets.fromLTRB(
                                    14,
                                    7,
                                    6,
                                    7,
                                  ),
                                  leading: CircleAvatar(
                                    backgroundColor: colors.primaryContainer,
                                    foregroundColor: colors.onPrimaryContainer,
                                    child: Icon(
                                      conversation.type == ConversationType.group
                                          ? Icons.groups_rounded
                                          : Icons.chat_bubble_outline_rounded,
                                    ),
                                  ),
                                  title: Text(
                                    conversation.title,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  subtitle: Padding(
                                    padding: const EdgeInsets.only(top: 5),
                                    child: Text(
                                      _previewText(message),
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                  trailing: PopupMenuButton<String>(
                                    tooltip: 'Message options',
                                    onSelected: (value) {
                                      if (value == 'open') {
                                        _openConversation(conversation);
                                      } else if (value == 'unstar') {
                                        widget.dataStore.toggleStarMessage(
                                          conversation.id,
                                          message.id,
                                        );
                                      }
                                    },
                                    itemBuilder: (_) => const [
                                      PopupMenuItem(
                                        value: 'open',
                                        child: ListTile(
                                          dense: true,
                                          leading: Icon(Icons.open_in_chat_rounded),
                                          title: Text('Open conversation'),
                                        ),
                                      ),
                                      PopupMenuItem(
                                        value: 'unstar',
                                        child: ListTile(
                                          dense: true,
                                          leading: Icon(Icons.star_border_rounded),
                                          title: Text('Remove star'),
                                        ),
                                      ),
                                    ],
                                    child: Padding(
                                      padding: const EdgeInsets.all(12),
                                      child: Column(
                                        mainAxisAlignment: MainAxisAlignment.center,
                                        children: [
                                          Icon(
                                            Icons.star_rounded,
                                            color: colors.primary,
                                            size: 19,
                                          ),
                                          const SizedBox(height: 3),
                                          Text(
                                            _formatDate(message.createdAt),
                                            style: Theme.of(context)
                                                .textTheme
                                                .labelSmall,
                                          ),
                                        ],
                                      ),
                                    ),
                                  ),
                                  onTap: () => _openConversation(conversation),
                                ),
                              );
                            },
                          ),
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
