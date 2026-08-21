import 'package:flutter/material.dart';
import '../../../ui/core/theme/theme_config.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/design_system/design_system.dart';

class GroupInfoScreen extends StatelessWidget {
  final ThemeConfig theme;
  final MockDataStore dataStore;
  final String conversationId;

  const GroupInfoScreen({
    super.key,
    required this.theme,
    required this.dataStore,
    required this.conversationId,
  });

  @override
  Widget build(BuildContext context) {
    final conv = dataStore.conversations.firstWhere(
      (c) => c.id == conversationId,
      orElse: () => dataStore.conversations.first,
    );

    final participants = conv.participantIds
        .map((id) => dataStore.getUserById(id))
        .whereType<dynamic>()
        .toList();

    final themeData = Theme.of(context);

    return ChatyScaffold(
      appBar: ChatyAppBar(
        title: 'Group Info',
        leading: const ChatyBackButton(),
        actions: [
          ChatyIconButton(
            icon: Icons.edit_outlined,
            tooltip: 'Edit group info',
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Edit group name / avatar modal')),
              );
            },
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(
          horizontal: ChatySpacing.base,
          vertical: ChatySpacing.md,
        ),
        child: Column(
          children: [
            // Group Header Card
            ChatyCard(
              padding: const EdgeInsets.all(ChatySpacing.lg),
              child: Center(
                child: Column(
                  children: [
                    AppAvatar(
                      initials: conv.avatarInitials ?? 'GP',
                      colorHex: conv.avatarColorHex,
                      size: 68,
                    ),
                    const SizedBox(height: ChatySpacing.md),
                    Text(
                      conv.title,
                      textAlign: TextAlign.center,
                      style: ChatyTypography.headline(
                        themeData.colorScheme.onSurface,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${conv.participantIds.length} Members • End-to-End Encrypted',
                      style: ChatyTypography.caption(
                        themeData.colorScheme.onSurface.withValues(alpha: 0.6),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: ChatySpacing.base),

            // Controls
            ChatyGroupedSection(
              title: 'Group Security & Settings',
              children: [
                ChatyListTile(
                  leading: const Icon(
                    Icons.timer_outlined,
                    color: Color(0xFFF59E0B),
                  ),
                  title: Text(
                    'Disappearing Messages',
                    style: TextStyle(
                      color: themeData.colorScheme.onSurface,
                      fontWeight: FontWeight.w600,
                      fontSize: 15,
                    ),
                  ),
                  subtitle: Text(
                    '7 days active',
                    style: ChatyTypography.caption(
                      themeData.colorScheme.onSurface.withValues(alpha: 0.6),
                    ),
                  ),
                  trailing: Icon(
                    Icons.chevron_right_rounded,
                    color: themeData.colorScheme.onSurface.withValues(
                      alpha: 0.35,
                    ),
                  ),
                  onTap: () {},
                ),
                ChatyListTile(
                  leading: const Icon(
                    Icons.verified_user_outlined,
                    color: Color(0xFF10B981),
                  ),
                  title: Text(
                    'Encryption Verification',
                    style: TextStyle(
                      color: themeData.colorScheme.onSurface,
                      fontWeight: FontWeight.w600,
                      fontSize: 15,
                    ),
                  ),
                  subtitle: Text(
                    'Group prekeys authenticated',
                    style: ChatyTypography.caption(
                      themeData.colorScheme.onSurface.withValues(alpha: 0.6),
                    ),
                  ),
                  trailing: Icon(
                    Icons.chevron_right_rounded,
                    color: themeData.colorScheme.onSurface.withValues(
                      alpha: 0.35,
                    ),
                  ),
                  onTap: () {},
                ),
              ],
            ),

            // Participants List
            ChatyGroupedSection(
              title: '${participants.length} Members',
              children: [
                ChatyListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(ChatySpacing.sm),
                    decoration: BoxDecoration(
                      color: themeData.colorScheme.primary.withValues(
                        alpha: 0.12,
                      ),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.person_add_rounded,
                      color: themeData.colorScheme.primary,
                      size: 18,
                    ),
                  ),
                  title: Text(
                    'Add Member',
                    style: TextStyle(
                      color: themeData.colorScheme.primary,
                      fontWeight: FontWeight.w600,
                      fontSize: 15,
                    ),
                  ),
                  subtitle: Text(
                    'Invite via username or direct prekey share',
                    style: ChatyTypography.caption(
                      themeData.colorScheme.onSurface.withValues(alpha: 0.6),
                    ),
                  ),
                  onTap: () {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Mock invite link copied to clipboard.'),
                      ),
                    );
                  },
                ),
                for (final p in participants) ...[
                  Builder(
                    builder: (context) {
                      final isAdmin = conv.adminIds.contains(p.id);
                      final isMe = p.id == dataStore.currentUser.id;

                      return ChatyListTile(
                        leading: AppAvatar(
                          initials: p.avatarInitials,
                          colorHex: p.avatarColorHex,
                          size: 38,
                        ),
                        title: Row(
                          children: [
                            Text(
                              isMe ? '${p.displayName} (You)' : p.displayName,
                              style: TextStyle(
                                color: themeData.colorScheme.onSurface,
                                fontSize: 14.5,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            if (isAdmin) ...[
                              const SizedBox(width: ChatySpacing.xs),
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 6,
                                  vertical: 2,
                                ),
                                decoration: BoxDecoration(
                                  color: themeData.colorScheme.primary
                                      .withValues(alpha: 0.12),
                                  borderRadius: BorderRadius.circular(
                                    ChatyRadius.full,
                                  ),
                                ),
                                child: Text(
                                  'Admin',
                                  style: TextStyle(
                                    color: themeData.colorScheme.primary,
                                    fontSize: 10.5,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ),
                            ],
                          ],
                        ),
                        subtitle: Text(
                          '@${p.username}',
                          style: ChatyTypography.caption(
                            themeData.colorScheme.onSurface.withValues(
                              alpha: 0.6,
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ],
              ],
            ),

            // Leave / Delete Actions
            ChatyGroupedSection(
              children: [
                ChatyListTile(
                  leading: const Icon(
                    Icons.exit_to_app_rounded,
                    color: Color(0xFFEF4444),
                  ),
                  title: const Text(
                    'Leave Group',
                    style: TextStyle(
                      color: Color(0xFFEF4444),
                      fontWeight: FontWeight.w600,
                      fontSize: 15,
                    ),
                  ),
                  onTap: () {
                    Navigator.pop(context);
                    Navigator.pop(context);
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Left group.')),
                    );
                  },
                ),
              ],
            ),
            const SizedBox(height: ChatySpacing.xl),
          ],
        ),
      ),
    );
  }
}
