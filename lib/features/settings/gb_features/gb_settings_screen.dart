import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../domain/models/preferences.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/appearance_variant_controller.dart';
import '../../../ui/core/templates/template_controller.dart';
import '../../../ui/core/templates/template_models.dart';

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
          'Chaty Settings',
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
                  subtitle: 'Open official Google Play listings',
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
                  subtitle: '23 Presets • Photo Palette • Import/Export • Reset',
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
                  subtitle: 'Export and restore Chaty settings safely',
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
                  subtitle: 'Remove expired generated image cache files',
                  onTap: () => _showWasteCleaningSheet(context, theme, colors),
                ),
                _buildDivider(colors),
                _buildSettingsRow(
                  theme: theme,
                  colors: colors,
                  icon: Icons.widgets_rounded,
                  iconBgColor: colors.info.withValues(alpha: 0.14),
                  iconColor: colors.info,
                  title: 'Home shortcuts',
                  subtitle: 'Configure in-app home layout and shortcuts',
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
                  subtitle: 'Open Chaty release notes and versions',
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
                'Get Official Apps',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 18 * theme.fontScale,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Open the official app listings in Google Play. Chaty does not distribute modified third-party apps.',
                style: TextStyle(
                  color: theme.secondaryTextColor,
                  fontSize: 13 * theme.fontScale,
                ),
              ),
              const SizedBox(height: 18),
              _appDownloadTile(
                'Instagram', 'Photos, Reels and Direct messages',
                Icons.camera_alt_rounded, colors.primary, ctx, context,
                'https://play.google.com/store/apps/details?id=com.instagram.android',
              ),
              _appDownloadTile(
                'TikTok', 'Short-form videos and creators',
                Icons.music_note_rounded, colors.info, ctx, context,
                'https://play.google.com/store/apps/details?id=com.zhiliaoapp.musically',
              ),
              _appDownloadTile(
                'X', 'Posts, conversations and media',
                Icons.alternate_email_rounded, colors.secondary, ctx, context,
                'https://play.google.com/store/apps/details?id=com.twitter.android',
              ),
              _appDownloadTile(
                'Telegram', 'Messaging, channels and cloud chats',
                Icons.send_rounded, Colors.lightBlue, ctx, context,
                'https://play.google.com/store/apps/details?id=org.telegram.messenger',
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _appDownloadTile(
    String name,
    String desc,
    IconData icon,
    Color color,
    BuildContext sheetContext,
    BuildContext pageContext,
    String url,
  ) {
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
        onPressed: () async {
          Navigator.of(sheetContext).pop();
          final opened = await launchUrl(
            Uri.parse(url),
            mode: LaunchMode.externalApplication,
          );
          if (!pageContext.mounted || opened) return;
          ScaffoldMessenger.of(pageContext).showSnackBar(
            SnackBar(content: Text('Could not open the official $name listing.')),
          );
        },
        child: const Text('Open'),
      ),
    );
  }

  Map<String, dynamic> _safeSettingsMap(Map<String, dynamic> input) {
    // Device paths and display-name overrides belong to this device/account
    // and should not leak into a portable settings profile.
    final result = <String, dynamic>{};
    for (final entry in input.entries) {
      final key = entry.key.toLowerCase();
      if (key == 'path' ||
          key.endsWith('path') ||
          key.endsWith('filepath') ||
          key.contains('file_path') ||
          key.contains('mynameoverride') ||
          key == 'my_name' ||
          key.contains('customwallpaperimage')) {
        continue;
      }
      final value = entry.value;
      if (value is Map) {
        result[entry.key] = _safeSettingsMap(Map<String, dynamic>.from(value));
      } else if (value is List) {
        result[entry.key] = List<dynamic>.from(value);
      } else {
        result[entry.key] = value;
      }
    }
    return result;
  }

  Future<void> _copySettingsBackup(
    BuildContext context,
    ThemeConfig theme,
  ) async {
    const portableGbKeys = <String>{
      'ModConTextColor', 'ModContactNameColor', 'HomeCounterBK',
      'HomeCounterText', 'ModOnlineColor', 'ModlastseenColor',
      'onlineDotchatColor', 'ModConColor', 'tabindicator',
      'bubble_style', 'tick_style', 'text_size_pick', 'ConvoBack',
      'ModChatRightBubble', 'ModChatBubbleText', 'date_right_color',
      'ModChatLeftBubble', 'ModChatBubbleTextLeft', 'date_left_color',
      'ModCallsBackground', 'ModCallsTextColor', 'ModCallsIconColors',
      'ModChatColor', 'ModChatGStatusB', 'ModChatGStatusT',
      'ModConPickColor', 'HomeBarText', 'ModConBackColor',
      'list_bg_color', 'ModDarkConPickColor', 'ModDarkConPickColorNav',
      'BGColor', 'home_stories_style', 'ui_home_styleV3',
    };
    final templateController = locator<TemplateController>();
    final portableGb = <String, Object?>{
      for (final key in portableGbKeys)
        if (preferencesController.gbFeatures.containsKey(key))
          key: preferencesController.gbFeatures[key],
    };
    final payload = jsonEncode(<String, dynamic>{
      'kind': 'chaty_settings_profile',
      'schemaVersion': 1,
      'home': _safeSettingsMap(preferencesController.home.toMap()),
      'conversation': _safeSettingsMap(preferencesController.conversation.toMap()),
      'universal': _safeSettingsMap(preferencesController.universal.toMap()),
      'effects': _safeSettingsMap(preferencesController.effects.toMap()),
      'gbAppearance': portableGb,
      'theme': themeController.globalTheme.toMap(),
      'template': templateController.config.toMap(),
    });
    await Clipboard.setData(ClipboardData(text: payload));
    if (!context.mounted) return;
    Navigator.of(context).pop();
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(
          content: Text('Settings backup copied. Chats, messages, account data and security secrets are not included.'),
        ),
      );
  }

  Future<void> _restoreSettingsBackup(
    BuildContext context,
  ) async {
    final clipboard = await Clipboard.getData('text/plain');
    final input = TextEditingController(text: clipboard?.text ?? '');
    final raw = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Restore settings'),
        content: SizedBox(
          width: 520,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Paste a Chaty settings profile JSON. It restores appearance and layout preferences only; chats, accounts, security secrets, and encryption keys are never imported.',
              ),
              const SizedBox(height: 12),
              TextField(
                controller: input,
                minLines: 4,
                maxLines: 8,
                decoration: const InputDecoration(
                  hintText: 'Paste settings backup JSON',
                  border: OutlineInputBorder(),
                ),
              ),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: () async {
                    final data = await Clipboard.getData('text/plain');
                    if (data?.text != null) input.text = data!.text!;
                  },
                  icon: const Icon(Icons.content_paste_rounded),
                  label: const Text('Paste clipboard'),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(input.text),
            child: const Text('Validate & Restore'),
          ),
        ],
      ),
    );
    input.dispose();
    if (!context.mounted || raw == null || raw.trim().isEmpty) return;

    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map ||
          decoded['kind'] != 'chaty_settings_profile' ||
          decoded['schemaVersion'] != 1) {
        throw const FormatException('Unsupported Chaty settings profile.');
      }

      Map<String, dynamic> optionalMap(String key) {
        final value = decoded[key];
        if (value == null) return <String, dynamic>{};
        if (value is! Map) throw FormatException('Invalid $key settings.');
        return Map<String, dynamic>.from(value);
      }

      final homeMap = optionalMap('home');
      final conversationMap = optionalMap('conversation');
      final universalMap = optionalMap('universal');
      final effectsMap = optionalMap('effects');
      final gbMap = optionalMap('gbAppearance');
      final themeMap = optionalMap('theme');
      final templateMap = optionalMap('template');

      const allowedGbAppearanceKeys = <String>{
        'ModConTextColor', 'ModContactNameColor', 'HomeCounterBK',
        'HomeCounterText', 'ModOnlineColor', 'ModlastseenColor',
        'onlineDotchatColor', 'ModConColor', 'tabindicator',
        'bubble_style', 'tick_style', 'text_size_pick', 'ConvoBack',
        'ModChatRightBubble', 'ModChatBubbleText', 'date_right_color',
        'ModChatLeftBubble', 'ModChatBubbleTextLeft', 'date_left_color',
        'ModCallsBackground', 'ModCallsTextColor', 'ModCallsIconColors',
        'ModChatColor', 'ModChatGStatusB', 'ModChatGStatusT',
        'ModConPickColor', 'HomeBarText', 'ModConBackColor',
        'list_bg_color', 'ModDarkConPickColor', 'ModDarkConPickColorNav',
        'BGColor', 'home_stories_style', 'ui_home_styleV3',
      };
      if (gbMap.keys.any((key) => !allowedGbAppearanceKeys.contains(key))) {
        throw const FormatException('Unknown appearance setting in profile.');
      }
      if (templateMap.isNotEmpty) {
        final rawBase = templateMap['base'];
        if (!ChatyTemplateId.values.any((template) => template.key == rawBase)) {
          throw const FormatException('Unknown template profile.');
        }
        final rawOverrides = templateMap['overrides'];
        if (rawOverrides != null && rawOverrides is! Map) {
          throw const FormatException('Invalid component overrides.');
        }
        if (rawOverrides is Map) {
          for (final entry in rawOverrides.entries) {
            final componentKnown = TemplateComponentType.values.any(
              (component) => component.name == entry.key,
            );
            final templateKnown = ChatyTemplateId.values.any(
              (template) => template.key == entry.value,
            );
            if (!componentKnown || !templateKnown) {
              throw const FormatException('Unknown component or template.');
            }
          }
        }
      }

      // Decode everything before mutating any live state, so malformed input
      // cannot leave the app half-restored.
      final nextHome = HomePreferences.fromMap(<String, dynamic>{
        ...preferencesController.home.toMap(),
        ...homeMap,
      });
      final nextConversation = ConversationPreferences.fromMap(<String, dynamic>{
        ...preferencesController.conversation.toMap(),
        ...conversationMap,
      });
      final nextUniversal = UniversalPreferences.fromMap(<String, dynamic>{
        ...preferencesController.universal.toMap(),
        ...universalMap,
      });
      final nextEffects = NavigationEffectPreferences.fromMap(<String, dynamic>{
        ...preferencesController.effects.toMap(),
        ...effectsMap,
      });
      final nextTheme = themeMap.isEmpty
          ? null
          : ThemeConfig.fromMap(themeMap);
      final nextTemplate = templateMap.isEmpty
          ? null
          : UserTemplateConfiguration.fromMap(templateMap);
      if (nextTemplate != null) {
        await locator<TemplateController>().applyConfiguration(
          nextTemplate,
          appearanceController: locator<AppearanceVariantController>(),
          preferencesController: preferencesController,
          themeController: themeController,
        );
      }
      if (homeMap.isNotEmpty) {
        preferencesController.updateHome(nextHome, logTitle: 'Settings restore');
      }
      if (conversationMap.isNotEmpty) {
        preferencesController.updateConversation(
          nextConversation,
          logTitle: 'Settings restore',
        );
      }
      if (universalMap.isNotEmpty) {
        preferencesController.updateUniversal(
          nextUniversal,
          logTitle: 'Settings restore',
        );
      }
      if (effectsMap.isNotEmpty) {
        preferencesController.updateEffects(
          nextEffects,
          logTitle: 'Settings restore',
        );
      }
      if (gbMap.isNotEmpty) {
        preferencesController.updateGbFeatures(
          Map<String, Object?>.from(gbMap),
          logTitle: 'Settings restore',
        );
      }
      if (nextTheme != null) {
        themeController.updateThemeConfig(nextTheme);
      }

      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(content: Text('Settings profile restored.')),
        );
    } catch (_) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(
            content: Text('Restore failed. The profile is invalid or from an unsupported version.'),
          ),
        );
    }
  }

  void _showBackupRestoreSheet(
    BuildContext context,
    ThemeConfig theme,
    AppColors colors,
  ) {
    HapticFeedback.lightImpact();
    showModalBottomSheet<void>(
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
                'Chaty Settings Backup & Restore',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 18 * theme.fontScale,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                'Export or restore themes, templates, home layout, conversation appearance and supported visual preferences. Chats, media, account information and security secrets are not included.',
                style: TextStyle(color: theme.secondaryTextColor, fontSize: 13),
              ),
              const SizedBox(height: 16),
              ListTile(
                leading: Icon(Icons.content_copy_rounded, color: theme.accentColor),
                title: const Text('Copy settings backup'),
                subtitle: const Text('Copy a portable JSON settings profile'),
                onTap: () => _copySettingsBackup(context, theme),
              ),
              ListTile(
                leading: Icon(Icons.restore_rounded, color: colors.warning),
                title: const Text('Restore settings'),
                subtitle: const Text('Paste a previously copied settings profile'),
                onTap: () {
                  Navigator.of(ctx).pop();
                  _restoreSettingsBackup(context);
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<({int count, int bytes})> _scanExpiredCompressedImages() async {
    final temp = await getTemporaryDirectory();
    if (!await temp.exists()) return (count: 0, bytes: 0);
    final cutoff = DateTime.now().subtract(const Duration(hours: 24));
    final generatedImage = RegExp(r'^chaty_[0-9a-fA-F-]{36}\.jpg$');
    var count = 0;
    var bytes = 0;
    await for (final entity in temp.list(followLinks: false)) {
      if (entity is! File ||
          !generatedImage.hasMatch(entity.uri.pathSegments.last)) {
        continue;
      }
      try {
        final stat = await entity.stat();
        if (!stat.modified.isBefore(cutoff)) continue;
        count++;
        bytes += await entity.length();
      } catch (_) {
        // Cache entries can disappear while another operation is finishing.
      }
    }
    return (count: count, bytes: bytes);
  }

  Future<({int count, int bytes})> _cleanExpiredCompressedImages() async {
    final temp = await getTemporaryDirectory();
    if (!await temp.exists()) return (count: 0, bytes: 0);
    final cutoff = DateTime.now().subtract(const Duration(hours: 24));
    final generatedImage = RegExp(r'^chaty_[0-9a-fA-F-]{36}\.jpg$');
    var count = 0;
    var bytes = 0;
    await for (final entity in temp.list(followLinks: false)) {
      if (entity is! File ||
          !generatedImage.hasMatch(entity.uri.pathSegments.last)) {
        continue;
      }
      try {
        final stat = await entity.stat();
        if (!stat.modified.isBefore(cutoff)) continue;
        final length = await entity.length();
        await entity.delete();
        count++;
        bytes += length;
      } catch (_) {
        // Leave any file that is locked or otherwise unavailable.
      }
    }
    return (count: count, bytes: bytes);
  }

  String _formatCacheSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(2)} MB';
  }

  void _showWasteCleaningSheet(
    BuildContext context,
    ThemeConfig theme,
    AppColors colors,
  ) {
    HapticFeedback.lightImpact();
    showModalBottomSheet<void>(
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
                'Expired Cache Cleanup',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 18 * theme.fontScale,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                'Only Chaty-generated compressed image copies older than 24 hours are eligible. Voice notes, recent uploads, unsent staged media, chat attachments and profile images are preserved.',
                style: TextStyle(color: theme.secondaryTextColor, fontSize: 13),
              ),
              const SizedBox(height: 16),
              FutureBuilder<({int count, int bytes})>(
                future: _scanExpiredCompressedImages(),
                builder: (context, snapshot) {
                  if (snapshot.connectionState != ConnectionState.done) {
                    return const Padding(
                      padding: EdgeInsets.all(18),
                      child: Center(child: CircularProgressIndicator()),
                    );
                  }
                  if (snapshot.hasError) {
                    return Text(
                      'Cache information is unavailable on this device.',
                      style: TextStyle(color: theme.secondaryTextColor),
                    );
                  }
                  final stats = snapshot.data ?? (count: 0, bytes: 0);
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: colors.primary.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: Row(
                          children: [
                            Icon(Icons.cleaning_services_rounded,
                                color: theme.accentColor, size: 28),
                            const SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Text('Expired image cache',
                                      style: TextStyle(fontWeight: FontWeight.bold)),
                                  Text(
                                    '${stats.count} file(s) • ${_formatCacheSize(stats.bytes)}',
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: theme.secondaryTextColor,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 16),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton.icon(
                          style: FilledButton.styleFrom(
                            backgroundColor: theme.accentColor,
                            foregroundColor: colors.onPrimary,
                          ),
                          icon: const Icon(Icons.delete_sweep_rounded),
                          label: Text(
                            stats.count == 0
                                ? 'No expired cache to clean'
                                : 'Clean ${stats.count} expired file(s)',
                          ),
                          onPressed: stats.count == 0
                              ? null
                              : () async {
                                  Navigator.of(ctx).pop();
                                  final removed = await _cleanExpiredCompressedImages();
                                  if (!context.mounted) return;
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(
                                      content: Text(
                                        'Removed ${removed.count} expired file(s), ${_formatCacheSize(removed.bytes)}.',
                                      ),
                                    ),
                                  );
                                },
                        ),
                      ),
                    ],
                  );
                },
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
    showModalBottomSheet<void>(
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
                'Home shortcuts',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 18 * theme.fontScale,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                'Chaty applies home layout and shortcut settings inside the app. Native Android/iOS launcher widgets are not registered in this build, so this screen does not show switches that cannot take effect.',
                style: TextStyle(color: theme.secondaryTextColor, fontSize: 13),
              ),
              const SizedBox(height: 18),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  icon: const Icon(Icons.dashboard_customize_rounded),
                  label: const Text('Customize in-app home'),
                  onPressed: () {
                    Navigator.of(ctx).pop();
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => HomeScreenSettingsPage(
                          preferencesController: preferencesController,
                        ),
                      ),
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

  void _showUpdatesDialog(
    BuildContext context,
    ThemeConfig theme,
    AppColors colors,
  ) {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: theme.surfaceColor,
        title: const Text('Chaty Updates'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: const [
            Text('Installed version: 1.0.0+1',
                style: TextStyle(fontWeight: FontWeight.bold)),
            SizedBox(height: 10),
            Text('• Global templates and component-specific appearance'),
            Text('• Settings backup and restore for visual preferences'),
            Text('• Runtime fixes and compatibility improvements'),
            SizedBox(height: 8),
            Text('Check the project release page for published release notes and newer builds.'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Close'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: theme.accentColor),
            onPressed: () async {
              Navigator.pop(ctx);
              final opened = await launchUrl(
                Uri.parse('https://github.com/Bandi-maya/chat-application/releases'),
                mode: LaunchMode.externalApplication,
              );
              if (!context.mounted || opened) return;
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Could not open Chaty release notes.')),
              );
            },
            child: const Text('View Release Notes'),
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
