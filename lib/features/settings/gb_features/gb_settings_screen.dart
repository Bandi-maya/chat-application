import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../data/repositories/chaty_data_store.dart';
import '../../../data/services/notification_service.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../universal/universal_screen.dart';
import '../conversation/conversation_settings_page.dart';
import '../effects/navigation_effects_page.dart';
import '../home/home_screen_settings_page.dart';
import '../media/storage_and_media_settings_screen.dart';
import '../message_management/message_management_page.dart';
import '../notifications/notification_settings_page.dart';
import '../privacy/privacy_center_screen.dart';
import '../theme_editor_screen.dart';

/// GBWhatsApp Settings screen matching Image 2.
/// Grouped sleek cards, dynamic light/dark theming using global theme colors,
/// without hardcoded green borders.
class GbSettingsScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;
  final ChatyDataStore dataStore;
  final ChatyNotificationService notificationService;

  const GbSettingsScreen({
    super.key,
    required this.preferencesController,
    required this.themeController,
    required this.dataStore,
    required this.notificationService,
  });

  @override
  Widget build(BuildContext context) {
    final theme = themeController.globalTheme;
    final colors = context.colors;
    final isDark = theme.brightness == Brightness.dark;
    final user = dataStore.currentUser;

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      appBar: AppBar(
        backgroundColor: theme.backgroundColor,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: Padding(
          padding: const EdgeInsets.all(8.0),
          child: ChatyBackButton(
            onPressed: () => Navigator.of(context).pop(),
          ),
        ),
        title: Text(
          'Settings',
          style: TextStyle(
            color: theme.primaryTextColor,
            fontSize: 20 * theme.fontScale,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.3,
          ),
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 16.0),
            child: Center(
              child: Container(
                padding: const EdgeInsets.all(2.0),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: theme.accentColor,
                    width: 2.0,
                  ),
                ),
                child: ChatyNetworkAvatar(
                  initials: user.avatarInitials,
                  colorHex: user.avatarColorHex,
                  url: user.avatarUrl,
                  size: 32,
                ),
              ),
            ),
          ),
        ],
      ),
      body: SafeArea(
        top: false,
        child: ListView(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          children: [
            // Group 1: Download Apps
            _buildGroupCard(
              theme: theme,
              colors: colors,
              isDark: isDark,
              children: [
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.apps_rounded,
                  iconAssetPlaceholder: true,
                  iconBgColor: colors.primary.withValues(alpha: 0.12),
                  iconColor: theme.accentColor,
                  title: 'Download Apps New',
                  subtitle: 'Instagram • TikTok • Twitter • Telegram',
                  onTap: () => _showDownloadAppsSheet(context, theme, colors),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Group 2: Privacy, Themes, Universal
            _buildGroupCard(
              theme: theme,
              colors: colors,
              isDark: isDark,
              children: [
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.security_rounded,
                  iconBgColor: theme.accentColor.withValues(alpha: 0.14),
                  iconColor: theme.accentColor,
                  title: 'Privacy and Security',
                  subtitle: 'Privacy settings • Lock WhatsApp',
                  onTap: () {
                    HapticFeedback.selectionClick();
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => PrivacyCenterScreen(
                          preferencesController: preferencesController,
                        ),
                      ),
                    );
                  },
                ),
                _buildDivider(colors),
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.format_paint_rounded,
                  iconBgColor: colors.warning.withValues(alpha: 0.14),
                  iconColor: colors.warning,
                  title: 'GBThemes',
                  subtitle: 'Download Themes • Reset',
                  onTap: () {
                    HapticFeedback.selectionClick();
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => ThemeEditorScreen(
                          themeController: themeController,
                        ),
                      ),
                    );
                  },
                ),
                _buildDivider(colors),
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.settings_suggest_rounded,
                  iconBgColor: colors.info.withValues(alpha: 0.14),
                  iconColor: colors.info,
                  title: 'Universal',
                  subtitle: 'Colors • Icons and Fonts • Language • Other',
                  onTap: () {
                    HapticFeedback.selectionClick();
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => UniversalScreen(
                          preferencesController: preferencesController,
                        ),
                      ),
                    );
                  },
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Group 3: Home, Conversation, Notifications, Messages, Media, Effects
            _buildGroupCard(
              theme: theme,
              colors: colors,
              isDark: isDark,
              children: [
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.home_rounded,
                  iconBgColor: colors.primary.withValues(alpha: 0.14),
                  iconColor: theme.accentColor,
                  title: 'Home Screen',
                  subtitle: 'top bar • lines • floating button • status',
                  onTap: () {
                    HapticFeedback.selectionClick();
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => HomeScreenSettingsPage(
                          preferencesController: preferencesController,
                        ),
                      ),
                    );
                  },
                ),
                _buildDivider(colors),
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.chat_bubble_outline_rounded,
                  iconBgColor: colors.primary.withValues(alpha: 0.14),
                  iconColor: theme.accentColor,
                  title: 'Conversation Screen',
                  subtitle: 'Bubbles and Health • Images • More',
                  onTap: () {
                    HapticFeedback.selectionClick();
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => ConversationSettingsPage(
                          preferencesController: preferencesController,
                        ),
                      ),
                    );
                  },
                ),
                _buildDivider(colors),
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.notifications_active_rounded,
                  iconBgColor: colors.warning.withValues(alpha: 0.14),
                  iconColor: colors.warning,
                  title: 'Notifications',
                  subtitle: 'Notify from a caller • Notify who viewed your status',
                  onTap: () {
                    HapticFeedback.selectionClick();
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => NotificationSettingsPage(
                          preferencesController: preferencesController,
                          notificationService: notificationService,
                        ),
                      ),
                    );
                  },
                ),
                _buildDivider(colors),
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.mark_chat_unread_rounded,
                  iconBgColor: colors.info.withValues(alpha: 0.14),
                  iconColor: colors.info,
                  title: 'Message management',
                  subtitle: 'Auto Reply • Scheduling • Encrypt',
                  onTap: () {
                    HapticFeedback.selectionClick();
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => MessageManagementPage(
                          preferencesController: preferencesController,
                          dataStore: dataStore,
                        ),
                      ),
                    );
                  },
                ),
                _buildDivider(colors),
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.perm_media_rounded,
                  iconBgColor: Colors.pinkAccent.withValues(alpha: 0.14),
                  iconColor: Colors.pinkAccent,
                  title: 'Setting Media',
                  subtitle: 'media management • photos • video',
                  onTap: () {
                    HapticFeedback.selectionClick();
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => StorageAndMediaSettingsScreen(
                          preferencesController: preferencesController,
                        ),
                      ),
                    );
                  },
                ),
                _buildDivider(colors),
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.auto_awesome_rounded,
                  iconBgColor: Colors.purpleAccent.withValues(alpha: 0.14),
                  iconColor: Colors.purpleAccent,
                  title: 'Navigation Effects',
                  subtitle: 'Scroll up and down effects',
                  onTap: () {
                    HapticFeedback.selectionClick();
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => NavigationEffectsPage(
                          preferencesController: preferencesController,
                        ),
                      ),
                    );
                  },
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Group 4: Backup, Waste Cleaning, Widget
            _buildGroupCard(
              theme: theme,
              colors: colors,
              isDark: isDark,
              children: [
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.cloud_sync_rounded,
                  iconBgColor: colors.primary.withValues(alpha: 0.14),
                  iconColor: theme.accentColor,
                  title: 'Backup and restore',
                  subtitle: 'GBWA Data Backup • GBWA Data Recovery',
                  onTap: () => _showBackupRestoreSheet(context, theme, colors),
                ),
                _buildDivider(colors),
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.cleaning_services_rounded,
                  iconBgColor: colors.warning.withValues(alpha: 0.14),
                  iconColor: colors.warning,
                  title: 'Waste cleaning',
                  subtitle: 'Clear Cache • Increase Memory Space',
                  onTap: () => _showWasteCleaningSheet(context, theme, colors),
                ),
                _buildDivider(colors),
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.widgets_rounded,
                  iconBgColor: colors.info.withValues(alpha: 0.14),
                  iconColor: colors.info,
                  title: 'GBWA Widget',
                  subtitle: 'Design Shortcut • Widget',
                  onTap: () => _showWidgetSettingsSheet(context, theme, colors),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Group 5: Updates, About, Share
            _buildGroupCard(
              theme: theme,
              colors: colors,
              isDark: isDark,
              children: [
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.system_update_rounded,
                  iconBgColor: colors.primary.withValues(alpha: 0.14),
                  iconColor: theme.accentColor,
                  title: 'Updates',
                  subtitle: 'Check for a new update • Changelog',
                  onTap: () => _showUpdatesDialog(context, theme, colors),
                ),
                _buildDivider(colors),
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.info_outline_rounded,
                  iconBgColor: colors.info.withValues(alpha: 0.14),
                  iconColor: colors.info,
                  title: 'About',
                  subtitle: 'Follow the developer for the latest news',
                  onTap: () => _showAboutDialog(context, theme, colors),
                ),
                _buildDivider(colors),
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.share_rounded,
                  iconBgColor: Colors.deepPurpleAccent.withValues(alpha: 0.14),
                  iconColor: Colors.deepPurpleAccent,
                  title: 'Share GBWhatsApp with friends!',
                  subtitle: 'Share GBWhatsApp with your friends! 😍❤️',
                  onTap: () => _shareAppWithFriends(context),
                ),
              ],
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  Widget _buildGroupCard({
    required dynamic theme,
    required dynamic colors,
    required bool isDark,
    required List<Widget> children,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E2124) : colors.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: colors.borderSubtle,
          width: 0.9,
        ),
        boxShadow: [
          BoxShadow(
            color: colors.shadow.withValues(alpha: isDark ? 0.25 : 0.04),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: children,
        ),
      ),
    );
  }

  Widget _buildDivider(dynamic colors) {
    return Divider(
      height: 1,
      thickness: 0.8,
      indent: 64,
      endIndent: 16,
      color: colors.borderSubtle.withValues(alpha: 0.4),
    );
  }

  Widget _buildSettingsRow({
    required dynamic theme,
    required dynamic colors,
    required IconData icon,
    required Color iconBgColor,
    required Color iconColor,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
    bool iconAssetPlaceholder = false,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          HapticFeedback.selectionClick();
          onTap();
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: iconBgColor,
                  borderRadius: BorderRadius.circular(13),
                ),
                child: Center(
                  child: Icon(icon, color: iconColor, size: 23),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 15.5 * theme.fontScale,
                        fontWeight: FontWeight.w600,
                        letterSpacing: -0.2,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      subtitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: theme.secondaryTextColor,
                        fontSize: 12.5 * theme.fontScale,
                        fontWeight: FontWeight.w400,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Icon(
                Icons.chevron_right_rounded,
                color: theme.accentColor,
                size: 22,
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showDownloadAppsSheet(
    BuildContext context,
    ThemeConfig theme,
    AppColors colors,
  ) {
    HapticFeedback.lightImpact();
    showModalBottomSheet(
      context: context,
      backgroundColor: theme.surfaceColor,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: colors.borderSubtle,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Text(
                'Download Apps New',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 18 * theme.fontScale,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Direct social suite integrations with verified builds.',
                style: TextStyle(
                  color: theme.secondaryTextColor,
                  fontSize: 13 * theme.fontScale,
                ),
              ),
              const SizedBox(height: 18),
              _appDownloadTile('Instagram Pro', 'Photos, Reels, Direct DMs', Icons.camera_alt_rounded, colors.primary, ctx),
              _appDownloadTile('TikTok Plus', 'Short videos without watermark', Icons.music_note_rounded, colors.info, ctx),
              _appDownloadTile('Twitter / X Mod', 'Download media, ad-free feed', Icons.alternate_email_rounded, colors.secondary, ctx),
              _appDownloadTile('Telegram Elite', 'Unlimited channels and cloud', Icons.send_rounded, Colors.lightBlue, ctx),
            ],
          ),
        ),
      ),
    );
  }

  Widget _appDownloadTile(String name, String desc, IconData icon, Color color, BuildContext ctx) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.15),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Icon(icon, color: color, size: 20),
      ),
      title: Text(name, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
      subtitle: Text(desc, style: const TextStyle(fontSize: 12)),
      trailing: FilledButton.tonal(
        onPressed: () {
          Navigator.pop(ctx);
          ScaffoldMessenger.of(ctx).showSnackBar(
            SnackBar(content: Text('Downloading $name package...')),
          );
        },
        child: const Text('Get'),
      ),
    );
  }

  void _showBackupRestoreSheet(
    BuildContext context,
    ThemeConfig theme,
    AppColors colors,
  ) {
    HapticFeedback.lightImpact();
    showModalBottomSheet(
      context: context,
      backgroundColor: theme.surfaceColor,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'GBWA Data Backup & Recovery',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 18 * theme.fontScale,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                'Create a full local snapshot of encrypted chats, media indexes, themes, and configuration.',
                style: TextStyle(color: theme.secondaryTextColor, fontSize: 13),
              ),
              const SizedBox(height: 20),
              ListTile(
                leading: Icon(Icons.backup_rounded, color: theme.accentColor),
                title: const Text('Back up GBWhatsApp Data'),
                subtitle: const Text('Save encrypted archive to local storage'),
                onTap: () {
                  Navigator.pop(ctx);
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('GBWA Backup created successfully! 📦')),
                  );
                },
              ),
              ListTile(
                leading: Icon(Icons.restore_rounded, color: colors.warning),
                title: const Text('Restore Data'),
                subtitle: const Text('Recover chats and media from previous backup'),
                onTap: () {
                  Navigator.pop(ctx);
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('GBWA Backup restored successfully! ✅')),
                  );
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showWasteCleaningSheet(
    BuildContext context,
    ThemeConfig theme,
    AppColors colors,
  ) {
    HapticFeedback.lightImpact();
    showModalBottomSheet(
      context: context,
      backgroundColor: theme.surfaceColor,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Waste Cleaning & Memory Space',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 18 * theme.fontScale,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: colors.primary.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Row(
                  children: [
                    Icon(Icons.cleaning_services_rounded, color: theme.accentColor, size: 28),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: const [
                          Text('Recoverable Cache', style: TextStyle(fontWeight: FontWeight.bold)),
                          Text('Cached thumbnails & logs: 248.6 MB', style: TextStyle(fontSize: 12)),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  style: FilledButton.styleFrom(
                    backgroundColor: theme.accentColor,
                    foregroundColor: colors.onPrimary,
                  ),
                  icon: const Icon(Icons.delete_sweep_rounded),
                  label: const Text('Clean All Waste Cache (248.6 MB)'),
                  onPressed: () {
                    Navigator.pop(ctx);
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Cleared 248.6 MB of cache successfully! 🚀')),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showWidgetSettingsSheet(
    BuildContext context,
    ThemeConfig theme,
    AppColors colors,
  ) {
    showModalBottomSheet(
      context: context,
      backgroundColor: theme.surfaceColor,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'GBWA Widget Settings',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 18 * theme.fontScale,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 10),
              const Text('Configure home screen widget appearance and shortcuts:'),
              const SizedBox(height: 16),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                activeColor: theme.accentColor,
                title: const Text('Show Unread Chats Count'),
                value: true,
                onChanged: (val) {},
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                activeColor: theme.accentColor,
                title: const Text('Show Status Stories Shortcut'),
                value: true,
                onChanged: (val) {},
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showUpdatesDialog(BuildContext context, ThemeConfig theme, AppColors colors) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: theme.surfaceColor,
        title: const Text('GBWhatsApp Updates'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: const [
            Text('Current Version: v19.50 (Latest)', style: TextStyle(fontWeight: FontWeight.bold)),
            SizedBox(height: 10),
            Text('• Anti-ban protection v4.2 enhanced'),
            Text('• 144Hz high refresh rate animations'),
            Text('• Freeze last seen and anti-delete safeguards'),
            Text('• Custom icons and themes support'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Close'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: theme.accentColor),
            onPressed: () {
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('You are on the latest version v19.50! ✨')),
              );
            },
            child: const Text('Check for Updates'),
          ),
        ],
      ),
    );
  }

  void _showAboutDialog(BuildContext context, ThemeConfig theme, AppColors colors) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: theme.surfaceColor,
        title: const Text('About GBWhatsApp'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: const [
            Text('GBWhatsApp Pro Edition', style: TextStyle(fontWeight: FontWeight.bold)),
            SizedBox(height: 6),
            Text('Version: 19.50.0 (Global Edition)'),
            SizedBox(height: 12),
            Text('Follow the official channels for the latest mods, themes, and security releases.'),
          ],
        ),
        actions: [
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: theme.accentColor),
            onPressed: () => Navigator.pop(ctx),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  void _shareAppWithFriends(BuildContext context) {
    HapticFeedback.lightImpact();
    Clipboard.setData(
      const ClipboardData(
        text: 'Download GBWhatsApp Pro: Enjoy privacy, themes, anti-delete and more! https://chaty.app/download',
      ),
    );
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('GBWhatsApp invite link copied to clipboard! Share it with your friends! 😍❤️'),
      ),
    );
  }
}
