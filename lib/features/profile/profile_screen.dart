import 'package:flutter/material.dart';

import '../../data/repositories/mock_data_store.dart';
import '../../data/services/notification_service.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/design_system/settings_primitives.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../settings/notifications/notification_settings_page.dart';
import '../settings/privacy/privacy_center_screen.dart';
import '../settings/settings_root_screen.dart';
import 'profile_actions.dart';

/// Root "Profile" destination (bottom navigation). Settings now lives ONE
/// level deeper: Profile → Settings → existing Settings system. Everything
/// shown here is real account data from the backend-backed data store —
/// no mock profile information.
class ProfileScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;
  final MockDataStore dataStore;
  final ChatyNotificationService notificationService;

  const ProfileScreen({
    super.key,
    required this.preferencesController,
    required this.themeController,
    required this.dataStore,
    required this.notificationService,
  });

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([dataStore, preferencesController]),
      builder: (context, _) {
        final user = dataStore.currentUser;
        final nameOverride = preferencesController.home.myNameOverride;
        final displayName = nameOverride.isNotEmpty
            ? nameOverride
            : user.displayName;
        return ChatySettingsPage(
          title: 'Profile',
          subtitle: 'Account, status and app entry points',
          children: [
            _ProfileHeader(
              initials: user.avatarInitials,
              colorHex: user.avatarColorHex,
              displayName: displayName,
              username: user.username,
              about: user.about,
              onEdit: () => showChatyProfileEditor(context, dataStore),
            ),
            const SizedBox(height: 8),
            ChatySettingsSection(
              title: 'Account',
              children: [
                if (user.phone.isNotEmpty)
                  ChatySettingsTile(
                    icon: Icons.call_outlined,
                    title: 'Phone',
                    subtitle: user.phone,
                  ),
                ChatySettingsTile(
                  icon: Icons.alternate_email_rounded,
                  title: 'Username',
                  subtitle: '@${user.username}',
                ),
                if (user.about.isNotEmpty)
                  ChatySettingsTile(
                    icon: Icons.info_outline_rounded,
                    title: 'About',
                    subtitle: user.about,
                  ),
              ],
            ),
            ChatySettingsSection(
              title: 'App',
              children: [
                ChatySettingsTile(
                  icon: Icons.settings_outlined,
                  title: 'Settings',
                  subtitle: 'Account, privacy, appearance and app behavior',
                  trailing: const _Chevron(),
                  onTap: () => _pushSettings(context),
                ),
                ChatySettingsTile(
                  icon: Icons.visibility_off_rounded,
                  title: 'Privacy Center',
                  subtitle: 'Presence, receipts and anti-delete options',
                  trailing: const _Chevron(),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => PrivacyCenterScreen(
                        preferencesController: preferencesController,
                      ),
                    ),
                  ),
                ),
                ChatySettingsTile(
                  icon: Icons.notifications_none_rounded,
                  title: 'Notifications',
                  subtitle: 'Toasts, alerts and previews',
                  trailing: const _Chevron(),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => NotificationSettingsPage(
                        preferencesController: preferencesController,
                        notificationService: notificationService,
                      ),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            ChatySettingsSection(
              children: [
                ChatySettingsTile(
                  icon: Icons.logout_rounded,
                  iconColor: context.colors.error,
                  title: 'Log out',
                  subtitle: 'Sign out of Chaty on this device',
                  onTap: () => confirmChatyLogout(context),
                ),
              ],
            ),
          ],
        );
      },
    );
  }

  void _pushSettings(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => SettingsRootScreen(
          preferencesController: preferencesController,
          themeController: themeController,
          dataStore: dataStore,
          notificationService: notificationService,
        ),
      ),
    );
  }
}

/// Large-avatar profile header: avatar, name, @username, about and one
/// restrained Edit action. Colors come exclusively from the design system.
class _ProfileHeader extends StatelessWidget {
  final String initials;
  final String colorHex;
  final String displayName;
  final String username;
  final String about;
  final VoidCallback onEdit;

  const _ProfileHeader({
    required this.initials,
    required this.colorHex,
    required this.displayName,
    required this.username,
    required this.about,
    required this.onEdit,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return Center(
      child: Column(
        children: [
          const SizedBox(height: 16),
          AppAvatar(initials: initials, colorHex: colorHex, size: 96),
          const SizedBox(height: 14),
          Text(
            displayName,
            textAlign: TextAlign.center,
            style: TextStyle(
              color: colors.foreground,
              fontSize: 24,
              fontWeight: FontWeight.w800,
              letterSpacing: -0.4,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            '@$username',
            style: TextStyle(
              color: colors.foregroundSecondary,
              fontSize: 13.5,
              fontWeight: FontWeight.w500,
            ),
          ),
          if (about.isNotEmpty) ...[
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: Text(
                about,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: colors.foregroundSecondary,
                  fontSize: 13,
                  height: 1.35,
                ),
              ),
            ),
          ],
          const SizedBox(height: 16),
          _EditPill(onTap: onEdit),
          const SizedBox(height: 20),
        ],
      ),
    );
  }
}

/// Small bordered "Edit profile" pill — press-scales subtly (120ms) so the
/// header feels responsive without shouting.
class _EditPill extends StatefulWidget {
  final VoidCallback onTap;
  const _EditPill({required this.onTap});

  @override
  State<_EditPill> createState() => _EditPillState();
}

class _EditPillState extends State<_EditPill> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapCancel: () => setState(() => _pressed = false),
      onTapUp: (_) => setState(() => _pressed = false),
      onTap: widget.onTap,
      child: AnimatedScale(
        scale: _pressed ? 0.96 : 1.0,
        duration: const Duration(milliseconds: 120),
        curve: Curves.easeOutCubic,
        child: AnimatedOpacity(
          duration: const Duration(milliseconds: 120),
          opacity: _pressed ? 0.82 : 1.0,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
            decoration: BoxDecoration(
              color: colors.surfaceElevated,
              borderRadius: BorderRadius.circular(999),
              border: Border.all(color: colors.borderSubtle),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.edit_outlined, size: 15, color: colors.primary),
                const SizedBox(width: 7),
                Text(
                  'Edit profile',
                  style: TextStyle(
                    color: colors.foreground,
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
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

/// Forward chevron for navigable rows (never used as a back button).
class _Chevron extends StatelessWidget {
  const _Chevron();

  @override
  Widget build(BuildContext context) {
    return Icon(
      Icons.chevron_right_rounded,
      size: 20,
      color: context.colors.foregroundTertiary,
    );
  }
}
