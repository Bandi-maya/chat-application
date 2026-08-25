import 'package:flutter/material.dart';
import '../../../data/repositories/chaty_data_store.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart'
    hide ChatySettingsSection;
import '../../../ui/core/design_system/settings_primitives.dart';
import '../../profile/profile_actions.dart';

/// Dedicated canonical settings screen for Account & Profile details.
class AccountSettingsScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;
  final ChatyDataStore dataStore;

  const AccountSettingsScreen({
    super.key,
    required this.preferencesController,
    required this.dataStore,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return ListenableBuilder(
      listenable: dataStore,
      builder: (context, _) {
        final user = dataStore.currentUser;

        return ChatySettingsPage(
          title: 'Account & Profile',
          subtitle: 'Profile photo, display name, username & bio',
          children: [
            // 1. PROFILE DETAILS
            ChatySettingsSection(
              title: 'Profile Information',
              children: [
                ChatySettingsTile(
                  leading: ChatyAvatar(
                    initials: user.avatarInitials,
                    color: colors.primary,
                    size: 40,
                  ),
                  title: user.displayName.isNotEmpty
                      ? user.displayName
                      : 'Display Name',
                  subtitle: '@',
                ),
                ChatySettingsTile(
                  icon: Icons.info_outline_rounded,
                  iconColor: colors.primary,
                  title: 'About / Bio',
                  subtitle: user.about.isNotEmpty ? user.about : 'No bio set',
                ),
              ],
            ),

            // 2. ACCOUNT SECURITY
            ChatySettingsSection(
              title: 'Account Actions',
              children: [
                ChatySettingsTile(
                  icon: Icons.qr_code_rounded,
                  iconColor: colors.primary,
                  title: 'My QR Code Identity',
                  subtitle: 'Share your public contact card',
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () {},
                ),
                ChatySettingsTile(
                  icon: Icons.logout_rounded,
                  iconColor: colors.error,
                  title: 'Log Out',
                  subtitle: 'Sign out of your current session on this device',
                  onTap: () => confirmChatyLogout(context),
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}
