import 'package:flutter/material.dart';
import '../../../ui/core/theme/theme_controller.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../domain/models/user_profile.dart';
import '../../ui/core/design_system/design_system.dart';
import 'profile_setup_screen.dart';
import '../../injection/locator.dart';

class DemoAccountChooserScreen extends StatelessWidget {
  const DemoAccountChooserScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    final dataStore = locator<MockDataStore>();
    final theme = themeController.globalTheme;
    final allUsers = <UserProfile>[
      UserProfile(
        id: 'user_bandi_maya',
        displayName: 'Bandi Maya',
        username: '@bandi_maya',
        avatarInitials: 'BM',
        avatarColorHex: '0xFF6366F1',
        about: 'Hey there! I am using Chaty.',
        presence: PresenceState.online,
        lastSeenAt: DateTime.now().subtract(const Duration(minutes: 2)),
        isVerified: false,
        email: 'bandi.maya@example.com',
        phone: '',
        safetyNumber: '58291 04928 11948 29384 10293 88472',
      ),
      UserProfile(
        id: 'user_lisa_kim',
        displayName: 'Lisa Kim',
        username: '@lisakim',
        avatarInitials: 'LK',
        avatarColorHex: '0xFF6366F1',
        about: 'Exploring new features!',
        presence: PresenceState.offline,
        lastSeenAt: DateTime.now().subtract(const Duration(hours: 3)),
        isVerified: false,
        email: 'lisa.kim@example.com',
        phone: '',
        safetyNumber: '58291 04928 11948 29384 10293 88472',
      ),
      UserProfile(
        id: 'user_raj_patel',
        displayName: 'Raj Patel',
        username: '@rajpatel',
        avatarInitials: 'RP',
        avatarColorHex: '0xFF6366F1',
        about: 'Busy in meetings',
        presence: PresenceState.online,
        lastSeenAt: DateTime.now().subtract(const Duration(minutes: 10)),
        isVerified: false,
        email: 'raj.patel@example.com',
        phone: '',
        safetyNumber: '58291 04928 11948 29384 10293 88472',
      ),
      UserProfile(
        id: 'user_sofia_garcia',
        displayName: 'Sofia Garcia',
        username: '@sofiagarcia',
        avatarInitials: 'SG',
        avatarColorHex: '0xFF6366F1',
        about: 'On my way!',
        presence: PresenceState.offline,
        lastSeenAt: DateTime.now().subtract(const Duration(days: 1)),
        isVerified: false,
        email: 'sofia.garcia@example.com',
        phone: '',
        safetyNumber: '58291 04928 11948 29384 10293 88472',
      ),
    ];

    return ChatyScaffold(
      appBar: const ChatyAppBar(
        title: 'Choose Demo Account',
        leading: ChatyBackButton(),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: ChatySpacing.base,
            vertical: ChatySpacing.md,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Select an identity to test multi-device synced chats, tasks, and calls in sandbox mode.',
                style: TextStyle(
                  fontSize: 14.0,
                  color: theme.secondaryTextColor,
                  height: 1.4,
                ),
              ),
              const SizedBox(height: ChatySpacing.lg),
              Expanded(
                child: ChatyGroupedSection(
                  title: 'Available Sandbox Profiles',
                  children: [
                    for (final user in allUsers)
                      _DemoAccountTile(
                        user: user,
                        onTap: () {
                          dataStore.switchDemoAccount(user);
                          Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => const ProfileSetupScreen(),
                            ),
                          );
                        },
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DemoAccountTile extends StatelessWidget {
  final UserProfile user;
  final VoidCallback onTap;

  const _DemoAccountTile({required this.user, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = locator<ThemeController>().globalTheme;
    return ChatyListTile(
      leading: AppAvatar(
        initials: user.avatarInitials,
        colorHex: user.avatarColorHex,
        presence: user.presence,
        showOnlineBadge: true,
      ),
      title: Text(
        user.displayName,
        style: TextStyle(
          color: theme.primaryTextColor,
          fontWeight: FontWeight.w600,
          fontSize: 15,
        ),
      ),
      subtitle: Text(
        '${user.username} • ${user.about}',
        style: TextStyle(color: theme.secondaryTextColor, fontSize: 13),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: Icon(
        Icons.chevron_right_rounded,
        size: 20.0,
        color: theme.secondaryTextColor.withValues(alpha: 0.5),
      ),
      onTap: onTap,
    );
  }
}

