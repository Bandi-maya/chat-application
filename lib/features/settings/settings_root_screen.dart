import 'package:flutter/material.dart';

import '../../data/repositories/mock_data_store.dart';
import '../../data/services/chaty_notification_service.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../../ui/core/widgets/app_avatar.dart';
import 'appearance/universal_appearance_screen.dart';
import 'conversation/conversation_settings_page.dart';
import 'effects/navigation_effects_page.dart';
import 'home/home_screen_settings_page.dart';
import 'message_management/message_management_page.dart';
import 'notifications/notification_settings_page.dart';
import 'permissions/system_permissions_screen.dart';
import 'privacy/privacy_center_screen.dart';
import 'security/security_center_screen.dart';
import 'settings_search_delegate.dart';
import 'themes/chaty_theme_editor_screen.dart';

class SettingsRootScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;
  final MockDataStore dataStore;
  final ChatyNotificationService notificationService;

  const SettingsRootScreen({
    super.key,
    required this.preferencesController,
    required this.themeController,
    required this.dataStore,
    required this.notificationService,
  });

  void _push(BuildContext context, Widget child) {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => child));
  }

  List<SettingsSearchResult> _searchIndex() {
    return <SettingsSearchResult>[
      SettingsSearchResult(
        title: 'Privacy',
        category: 'Settings',
        description: 'Last seen, receipts, deleted messages and status privacy',
        icon: Icons.visibility_off_rounded,
        destination: PrivacyCenterScreen(preferencesController: preferencesController),
      ),
      SettingsSearchResult(
        title: 'Security',
        category: 'Settings',
        description: 'App lock and account security controls',
        icon: Icons.security_rounded,
        destination: SecurityCenterScreen(preferencesController: preferencesController),
      ),
      SettingsSearchResult(
        title: 'Themes',
        category: 'Appearance',
        description: '20 production theme presets',
        icon: Icons.palette_rounded,
        destination: ChatyThemeEditorScreen(themeController: themeController),
      ),
      SettingsSearchResult(
        title: 'Universal Appearance',
        category: 'Appearance',
        description: 'Navigation, icons, typography and transitions',
        icon: Icons.auto_awesome_rounded,
        destination: UniversalAppearanceScreen(preferencesController: preferencesController),
      ),
      SettingsSearchResult(
        title: 'Home Screen',
        category: 'Appearance',
        description: 'Search, stories and home layout options',
        icon: Icons.home_outlined,
        destination: HomeScreenSettingsPage(preferencesController: preferencesController),
      ),
      SettingsSearchResult(
        title: 'Conversation',
        category: 'Appearance',
        description: 'Bubbles, sidebar and conversation presentation',
        icon: Icons.chat_bubble_outline_rounded,
        destination: ConversationSettingsPage(preferencesController: preferencesController),
      ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final theme = themeController.globalTheme;
    final user = dataStore.currentUser;

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 96),
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Settings',
                    style: TextStyle(
                      color: theme.primaryTextColor,
                      fontSize: 24 * theme.fontScale,
                      fontWeight: FontWeight.w800,
                      letterSpacing: -0.5,
                    ),
                  ),
                ),
                IconButton(
                  tooltip: 'Search settings',
                  color: theme.primaryTextColor,
                  onPressed: () {
                    showSearch(
                      context: context,
                      delegate: SettingsSearchDelegate(allSettings: _searchIndex()),
                    );
                  },
                  icon: const Icon(Icons.search_rounded),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: theme.cardColor,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: theme.surfaceColor),
              ),
              child: Row(
                children: [
                  AppAvatar(
                    initials: user.avatarInitials,
                    colorHex: user.avatarColorHex,
                    size: 52,
                  ),
                  const SizedBox(width: 13),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          user.displayName,
                          style: TextStyle(color: theme.primaryTextColor, fontSize: 16, fontWeight: FontWeight.w800),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          '@${user.username}',
                          style: TextStyle(color: theme.secondaryTextColor, fontSize: 12.5),
                        ),
                        if (user.about.isNotEmpty)
                          Text(
                            user.about,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(color: theme.secondaryTextColor, fontSize: 11.5),
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 18),
            _SectionTitle(theme: theme, label: 'Appearance'),
            _SettingsTile(
              theme: theme,
              icon: Icons.palette_rounded,
              title: 'Themes',
              subtitle: '20 dark and light presets',
              onTap: () => _push(context, ChatyThemeEditorScreen(themeController: themeController)),
            ),
            _SettingsTile(
              theme: theme,
              icon: Icons.auto_awesome_rounded,
              title: 'Universal appearance',
              subtitle: 'Preview navigation, bars, icons, fonts and motion',
              onTap: () => _push(context, UniversalAppearanceScreen(preferencesController: preferencesController)),
            ),
            _SettingsTile(
              theme: theme,
              icon: Icons.home_outlined,
              title: 'Home screen',
              subtitle: 'Search, stories and home layout',
              onTap: () => _push(context, HomeScreenSettingsPage(preferencesController: preferencesController)),
            ),
            _SettingsTile(
              theme: theme,
              icon: Icons.chat_bubble_outline_rounded,
              title: 'Conversation',
              subtitle: 'Bubbles, wallpaper and chat layout',
              onTap: () => _push(context, ConversationSettingsPage(preferencesController: preferencesController)),
            ),
            const SizedBox(height: 14),
            _SectionTitle(theme: theme, label: 'Privacy & security'),
            _SettingsTile(
              theme: theme,
              icon: Icons.visibility_off_rounded,
              title: 'Privacy',
              subtitle: 'Last seen, receipts and anti-delete options',
              onTap: () => _push(context, PrivacyCenterScreen(preferencesController: preferencesController)),
            ),
            _SettingsTile(
              theme: theme,
              icon: Icons.security_rounded,
              title: 'Security',
              subtitle: 'App lock and account protection',
              onTap: () => _push(context, SecurityCenterScreen(preferencesController: preferencesController)),
            ),
            const SizedBox(height: 14),
            _SectionTitle(theme: theme, label: 'Messaging & system'),
            _SettingsTile(
              theme: theme,
              icon: Icons.notifications_outlined,
              title: 'Notifications',
              subtitle: 'Alerts, preview behavior and sounds',
              onTap: () => _push(
                context,
                NotificationSettingsPage(
                  preferencesController: preferencesController,
                  notificationService: notificationService,
                ),
              ),
            ),
            _SettingsTile(
              theme: theme,
              icon: Icons.schedule_send_rounded,
              title: 'Message management',
              subtitle: 'Quick replies, automation and scheduled messages',
              onTap: () => _push(
                context,
                MessageManagementPage(
                  preferencesController: preferencesController,
                  dataStore: dataStore,
                ),
              ),
            ),
            _SettingsTile(
              theme: theme,
              icon: Icons.animation_rounded,
              title: 'Navigation effects',
              subtitle: 'Page transitions and interaction effects',
              onTap: () => _push(context, NavigationEffectsPage(preferencesController: preferencesController)),
            ),
            _SettingsTile(
              theme: theme,
              icon: Icons.admin_panel_settings_outlined,
              title: 'Permissions',
              subtitle: 'Camera, microphone, media, contacts and notifications',
              onTap: () => _push(
                context,
                SystemPermissionsScreen(
                  preferencesController: preferencesController,
                  notificationService: notificationService,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final dynamic theme;
  final String label;
  const _SectionTitle({required this.theme, required this.label});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 0, 4, 6),
      child: Text(
        label.toUpperCase(),
        style: TextStyle(color: theme.secondaryTextColor, fontSize: 10.5, fontWeight: FontWeight.w800, letterSpacing: 0.8),
      ),
    );
  }
}

class _SettingsTile extends StatelessWidget {
  final dynamic theme;
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  const _SettingsTile({
    required this.theme,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 7),
      child: Material(
        color: theme.cardColor,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 12),
            child: Row(
              children: [
                Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(
                    color: theme.accentColor.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(icon, color: theme.accentColor, size: 20),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title, style: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.w700, fontSize: 14)),
                      const SizedBox(height: 2),
                      Text(subtitle, style: TextStyle(color: theme.secondaryTextColor, fontSize: 11.5)),
                    ],
                  ),
                ),
                Icon(Icons.chevron_right_rounded, color: theme.secondaryTextColor),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
