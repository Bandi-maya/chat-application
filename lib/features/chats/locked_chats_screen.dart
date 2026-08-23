import 'package:flutter/material.dart';

import 'package:chat/domain/models/conversation.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/design_system/design_system.dart';
import 'package:chat/ui/core/widgets/app_avatar.dart';
import 'package:chat/data/repositories/chaty_data_store.dart';
import 'package:chat/data/services/local_lock_service.dart';
import 'package:chat/data/services/protected_resource_gate.dart';
import 'package:chat/injection/locator.dart';
import 'chat_detail_screen.dart';

class LockedChatsScreen extends StatefulWidget {
  final ChatyDataStore dataStore;
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;

  const LockedChatsScreen({
    super.key,
    required this.dataStore,
    required this.preferencesController,
    required this.themeController,
  });

  static Future<void> open(
    BuildContext context, {
    required ChatyDataStore dataStore,
    required ChatyPreferencesController preferencesController,
    required ThemeController themeController,
  }) async {
    final authorized = await ProtectedResourceGate.authorizeGeneralAction(
      context,
      preferencesController: preferencesController,
      title: 'Locked & Hidden Chats',
      reason: 'Authenticate to access your locked conversations',
    );

    if (authorized && context.mounted) {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => LockedChatsScreen(
            dataStore: dataStore,
            preferencesController: preferencesController,
            themeController: themeController,
          ),
        ),
      );
    }
  }

  @override
  State<LockedChatsScreen> createState() => _LockedChatsScreenState();
}

class _LockedChatsScreenState extends State<LockedChatsScreen> {
  final TextEditingController _secretWordCtrl = TextEditingController();
  late final LocalLockService _lockService;
  bool _hasSecretPhrase = false;

  @override
  void initState() {
    super.initState();
    _lockService = locator<LocalLockService>();
    _checkSecretPhrase();
  }

  @override
  void dispose() {
    _secretWordCtrl.dispose();
    super.dispose();
  }

  Future<void> _checkSecretPhrase() async {
    final has = await _lockService.hasSecretPhrase();
    if (mounted) setState(() => _hasSecretPhrase = has);
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

  Future<void> _showSecretCodeDialog() async {
    _secretWordCtrl.clear();
    final theme = widget.themeController.globalTheme;

    final updated = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: theme.cardColor,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
        ),
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: theme.accentColor.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(Icons.key_rounded, color: theme.accentColor, size: 22),
            ),
            const SizedBox(width: 12),
            Text(
              _hasSecretPhrase ? 'Change Secret Code' : 'Set Secret Code',
              style: TextStyle(color: theme.primaryTextColor, fontSize: 18),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Enter a secret word, phrase, or emoji. Typing this secret into the chat search bar will instantly reveal your locked chats.',
              style: TextStyle(
                color: theme.secondaryTextColor,
                fontSize: 13,
                height: 1.35,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _secretWordCtrl,
              autofocus: true,
              style: TextStyle(color: theme.primaryTextColor),
              decoration: InputDecoration(
                hintText: 'e.g. 🔒 secret or my-vault',
                hintStyle: TextStyle(color: theme.secondaryTextColor),
                filled: true,
                fillColor: theme.surfaceColor,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: Text('Cancel', style: TextStyle(color: theme.secondaryTextColor)),
          ),
          if (_hasSecretPhrase)
            TextButton(
              onPressed: () async {
                await _lockService.clearSecretPhrase();
                widget.preferencesController.updateSecurity(
                  widget.preferencesController.security.copyWith(
                    entryBySecretPhrase: false,
                  ),
                );
                if (ctx.mounted) Navigator.of(ctx).pop(true);
              },
              child: Text(
                'Remove Code',
                style: TextStyle(color: theme.dangerColor),
              ),
            ),
          FilledButton(
            onPressed: () async {
              final secret = _secretWordCtrl.text.trim();
              if (secret.isEmpty) return;
              await _lockService.setSecretPhrase(secret);
              widget.preferencesController.updateSecurity(
                widget.preferencesController.security.copyWith(
                  entryBySecretPhrase: true,
                ),
              );
              if (ctx.mounted) Navigator.of(ctx).pop(true);
            },
            style: FilledButton.styleFrom(backgroundColor: theme.accentColor),
            child: const Text('Save Code'),
          ),
        ],
      ),
    );

    if (updated == true) {
      await _checkSecretPhrase();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              _hasSecretPhrase
                  ? 'Secret code active. Type it into search to reveal locked chats.'
                  : 'Secret code removed.',
            ),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    }
  }

  void _showChatLockSettings(Conversation conversation) {
    final isHidden = widget.preferencesController.isConversationHidden(conversation.id);
    final theme = widget.themeController.globalTheme;

    showModalBottomSheet<void>(
      context: context,
      backgroundColor: theme.cardColor,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: theme.secondaryTextColor.withValues(alpha: 0.3),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 16),
              ListTile(
                leading: CircleAvatar(
                  backgroundColor: isHidden
                      ? theme.accentColor.withValues(alpha: 0.15)
                      : theme.secondaryTextColor.withValues(alpha: 0.15),
                  child: Icon(
                    isHidden ? Icons.visibility_off_rounded : Icons.visibility_rounded,
                    color: isHidden ? theme.accentColor : theme.primaryTextColor,
                  ),
                ),
                title: Text(
                  isHidden ? 'Unhide Chat' : 'Hide Chat from Chat List',
                  style: TextStyle(
                    color: theme.primaryTextColor,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                subtitle: Text(
                  isHidden
                      ? 'Show in standard chat list with locked badge'
                      : 'Keep hidden and only discoverable via secret code or title tap',
                  style: TextStyle(color: theme.secondaryTextColor, fontSize: 12),
                ),
                onTap: () {
                  Navigator.of(ctx).pop();
                  widget.preferencesController.toggleHideConversation(
                    conversation.id,
                    hide: !isHidden,
                  );
                },
              ),
              ListTile(
                leading: CircleAvatar(
                  backgroundColor: theme.dangerColor.withValues(alpha: 0.15),
                  child: Icon(Icons.lock_open_rounded, color: theme.dangerColor),
                ),
                title: Text(
                  'Unlock & Remove Protection',
                  style: TextStyle(
                    color: theme.dangerColor,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                subtitle: Text(
                  'Removes PIN / biometric requirement from this conversation',
                  style: TextStyle(color: theme.secondaryTextColor, fontSize: 12),
                ),
                onTap: () {
                  Navigator.of(ctx).pop();
                  widget.preferencesController.unlockConversationCompletely(
                    conversation.id,
                  );
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = widget.themeController.globalTheme;
    final security = widget.preferencesController.security;

    // Retrieve all conversations that are locked or hidden
    final lockedConversations = widget.dataStore.conversations.where((c) {
      return widget.preferencesController.isConversationProtected(c.id);
    }).toList(growable: false);

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      appBar: AppBar(
        backgroundColor: theme.backgroundColor,
        foregroundColor: theme.primaryTextColor,
        elevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Locked & Hidden Chats',
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 19 * theme.fontScale,
                fontWeight: FontWeight.w800,
              ),
            ),
            Text(
              '${lockedConversations.length} protected conversations',
              style: TextStyle(
                color: theme.secondaryTextColor,
                fontSize: 12,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Secret Search Code Settings',
            icon: Icon(
              _hasSecretPhrase ? Icons.key_rounded : Icons.key_off_outlined,
              color: _hasSecretPhrase ? theme.accentColor : theme.secondaryTextColor,
            ),
            onPressed: _showSecretCodeDialog,
          ),
          IconButton(
            tooltip: 'Lock Settings',
            icon: const Icon(Icons.shield_outlined),
            onPressed: () {
              Navigator.of(context).pushNamed('/settings/security');
            },
          ),
        ],
      ),
      body: lockedConversations.isEmpty
          ? ChatyEmptyState(
              icon: Icons.lock_outline_rounded,
              title: 'No Locked Chats',
              message:
                  'To lock a chat, open chat options or Contact Info and toggle Chat Lock with PIN or biometric protection.',
              iconColor: theme.secondaryTextColor,
              titleColor: theme.primaryTextColor,
              messageColor: theme.secondaryTextColor,
            )
          : ListView(
              padding: const EdgeInsets.symmetric(vertical: 8),
              children: [
                // Info Banner for Hidden chats & Secret Entry
                Container(
                  margin: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: theme.cardColor,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: theme.accentColor.withValues(alpha: 0.2),
                    ),
                  ),
                  child: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: theme.accentColor.withValues(alpha: 0.12),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          Icons.visibility_off_rounded,
                          color: theme.accentColor,
                          size: 22,
                        ),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Hidden Chat Discovery',
                              style: TextStyle(
                                color: theme.primaryTextColor,
                                fontWeight: FontWeight.w700,
                                fontSize: 14,
                              ),
                            ),
                            const SizedBox(height: 3),
                            Text(
                              _hasSecretPhrase
                                  ? 'Type your secret code in the Chats search bar, or tap the Chaty header title to open this vault.'
                                  : 'Tap the key icon above to set a secret phrase or emoji for hidden chat search discovery.',
                              style: TextStyle(
                                color: theme.secondaryTextColor,
                                fontSize: 12,
                                height: 1.3,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                ...lockedConversations.map((conversation) {
                  final isHidden = widget.preferencesController.isConversationHidden(
                    conversation.id,
                  );
                  return ListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    leading: Stack(
                      children: [
                        AppAvatar(
                          initials: conversation.avatarInitials ?? 'CH',
                          colorHex: conversation.avatarColorHex,
                          size: 50,
                        ),
                        Positioned(
                          right: -2,
                          bottom: -2,
                          child: Container(
                            padding: const EdgeInsets.all(3),
                            decoration: BoxDecoration(
                              color: isHidden
                                  ? theme.accentColor
                                  : theme.cardColor,
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: theme.backgroundColor,
                                width: 2,
                              ),
                            ),
                            child: Icon(
                              isHidden
                                  ? Icons.visibility_off_rounded
                                  : Icons.lock_rounded,
                              size: 12,
                              color: isHidden ? Colors.white : theme.accentColor,
                            ),
                          ),
                        ),
                      ],
                    ),
                    title: Text(
                      conversation.title,
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontWeight: FontWeight.w700,
                        fontSize: 16 * theme.fontScale,
                      ),
                    ),
                    subtitle: Text(
                      isHidden ? 'Hidden & Locked' : 'Locked with ${security.lockMethod}',
                      style: TextStyle(
                        color: isHidden ? theme.accentColor : theme.secondaryTextColor,
                        fontSize: 13,
                        fontWeight: isHidden ? FontWeight.w600 : FontWeight.normal,
                      ),
                    ),
                    trailing: IconButton(
                      icon: const Icon(Icons.more_vert_rounded),
                      color: theme.secondaryTextColor,
                      onPressed: () => _showChatLockSettings(conversation),
                    ),
                    onTap: () => _openChat(conversation),
                  );
                }),
              ],
            ),
    );
  }
}
