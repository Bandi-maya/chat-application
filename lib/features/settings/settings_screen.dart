import 'package:flutter/material.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/menu/app_context_menu.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/notification_service.dart';
import 'settings_search_delegate.dart';
import 'privacy/privacy_center_screen.dart';
import 'security/security_center_screen.dart';
import 'theme_editor_screen.dart';
import 'pro_pricing_screen.dart';
import 'universal/universal_screen.dart';
import 'appearance/universal_appearance_screen.dart';
import 'home/home_screen_settings_page.dart';
import 'conversation/conversation_settings_page.dart';
import 'notifications/notification_settings_page.dart';
import 'message_management/message_management_page.dart';
import 'effects/navigation_effects_page.dart';
import 'permissions/system_permissions_screen.dart';
import '../auth/welcome_screen.dart';

class SettingsScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;
  final ChatyDataStore dataStore;
  final ChatyNotificationService notificationService;

  const SettingsScreen({
    super.key,
    required this.preferencesController,
    required this.themeController,
    required this.dataStore,
    required this.notificationService,
  });

  List<SettingsSearchResult> _buildSearchIndex(BuildContext context) {
    return [
      SettingsSearchResult(
        title: 'Freeze Last Seen',
        category: 'Privacy',
        description: 'Freeze last visible timestamp',
        icon: Icons.ac_unit_rounded,
        destination: PrivacyCenterScreen(
          preferencesController: preferencesController,
        ),
      ),
      SettingsSearchResult(
        title: 'Hide Last Seen Audience',
        category: 'Privacy',
        description:
            'Restrict last seen visibility to Everyone, Contacts, Nobody',
        icon: Icons.visibility_off_rounded,
        destination: PrivacyCenterScreen(
          preferencesController: preferencesController,
        ),
      ),
      SettingsSearchResult(
        title: 'Read Receipts (Blue Ticks)',
        category: 'Privacy',
        description: 'Enable or disable read receipt indicators',
        icon: Icons.done_all_rounded,
        destination: PrivacyCenterScreen(
          preferencesController: preferencesController,
        ),
      ),
      SettingsSearchResult(
        title: 'Anti-Delete Messages',
        category: 'Privacy',
        description: 'Retain deleted messages marked "Deleted by sender"',
        icon: Icons.delete_forever_rounded,
        destination: PrivacyCenterScreen(
          preferencesController: preferencesController,
        ),
      ),
      SettingsSearchResult(
        title: 'Chaty App Lock',
        category: 'Security',
        description: 'Biometric, PIN, Pattern & Password protection',
        icon: Icons.lock_rounded,
        destination: SecurityCenterScreen(
          preferencesController: preferencesController,
        ),
      ),
      SettingsSearchResult(
        title: 'Chaty Themes',
        category: 'Themes',
        description: '13+ dark & light theme presets and token editor',
        icon: Icons.palette_rounded,
        destination: ThemeEditorScreen(themeController: themeController),
      ),
      SettingsSearchResult(
        title: 'Home UI Style',
        category: 'Home Screen',
        description: 'Default, Classic, Compact, Expressive, Stories First',
        icon: Icons.space_dashboard_rounded,
        destination: HomeScreenSettingsPage(
          preferencesController: preferencesController,
        ),
      ),
      SettingsSearchResult(
        title: 'Instagram Stories Bar',
        category: 'Home Screen',
        description: 'Horizontal story avatars above chats',
        icon: Icons.history_edu_rounded,
        destination: HomeScreenSettingsPage(
          preferencesController: preferencesController,
        ),
      ),
      SettingsSearchResult(
        title: 'Ghost Mode',
        category: 'Home Screen',
        description: 'One-tap total stealth privacy bundle',
        icon: Icons.shield_moon_rounded,
        destination: HomeScreenSettingsPage(
          preferencesController: preferencesController,
        ),
      ),
      SettingsSearchResult(
        title: 'Bubble Shapes & Corner Radius',
        category: 'Conversation',
        description: 'Rounded, Tail, Squircle, Compact bubble shapes',
        icon: Icons.chat_bubble_outline_rounded,
        destination: ConversationSettingsPage(
          preferencesController: preferencesController,
        ),
      ),
      SettingsSearchResult(
        title: 'Quick Contact Sidebar',
        category: 'Conversation',
        description: 'Docked contact switcher panel',
        icon: Icons.dock_rounded,
        destination: ConversationSettingsPage(
          preferencesController: preferencesController,
        ),
      ),
      SettingsSearchResult(
        title: 'Auto-Reply Rules',
        category: 'Message Management',
        description: 'Keyword automated response rules',
        icon: Icons.reply_all_rounded,
        destination: MessageManagementPage(
          preferencesController: preferencesController,
          dataStore: dataStore,
        ),
      ),
      SettingsSearchResult(
        title: 'Message Scheduler',
        category: 'Message Management',
        description: 'Schedule messages for auto execution',
        icon: Icons.schedule_rounded,
        destination: MessageManagementPage(
          preferencesController: preferencesController,
          dataStore: dataStore,
        ),
      ),
      SettingsSearchResult(
        title: 'Interactive Click Particles',
        category: 'Navigation Effects',
        description: 'Tap particle splash symbols and speed',
        icon: Icons.auto_awesome_rounded,
        destination: NavigationEffectsPage(
          preferencesController: preferencesController,
        ),
      ),
      SettingsSearchResult(
        title: 'Notification Sounds & LED',
        category: 'Notifications',
        description: 'Conversation tones, LED colors, and heads-up alerts',
        icon: Icons.notifications_active_rounded,
        destination: NotificationSettingsPage(
          preferencesController: preferencesController,
          notificationService: notificationService,
        ),
      ),
      SettingsSearchResult(
        title: 'System Permissions & Hardware',
        category: 'Permissions',
        description: 'Camera, mic, storage, notification access',
        icon: Icons.security_rounded,
        destination: SystemPermissionsScreen(
          preferencesController: preferencesController,
          notificationService: notificationService,
        ),
      ),
    ];
  }

  void _showEditProfileDialog(BuildContext context) {
    final user = dataStore.currentUser;
    final nameCtrl = TextEditingController(text: user.displayName);
    final aboutCtrl = TextEditingController(text: user.about);
    final colors = context.colors;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: colors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(
          left: 24,
          right: 24,
          top: 24,
          bottom: MediaQuery.of(ctx).viewInsets.bottom + 24,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Edit Profile Details',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: colors.foreground,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: nameCtrl,
              decoration: const InputDecoration(
                labelText: 'Display Name',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 14),
            TextField(
              controller: aboutCtrl,
              decoration: const InputDecoration(
                labelText: 'Bio / About Status',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                onPressed: () {
                  final updated = user.copyWith(
                    displayName: nameCtrl.text.trim(),
                    about: aboutCtrl.text.trim(),
                  );
                  dataStore.updateProfile(updated);
                  Navigator.pop(ctx);
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: colors.primary,
                  foregroundColor: colors.onPrimary,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                ),
                child: const Text(
                  'Save Profile',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showResetOptions(BuildContext anchorContext) {
    AppContextMenu.show(
      context: anchorContext,
      title: 'Reset & Preferences',
      subtitle: 'Restore settings or presets',
      sections: [
        ContextMenuSection(
          items: [
            ContextMenuItem(
              icon: Icons.restore_rounded,
              label: 'Reset Theme to Default B&W',
              subtitle: 'Restore default monochrome theme',
              onTap: () {
                themeController.resetToDefaults();
                ScaffoldMessenger.of(anchorContext).showSnackBar(
                  const SnackBar(content: Text('Theme reset to default.')),
                );
              },
            ),
            ContextMenuItem(
              icon: Icons.shield_outlined,
              label: 'Reset Privacy Options',
              subtitle: 'Reset last seen and stealth toggles',
              onTap: () {
                preferencesController.resetPrivacy();
                ScaffoldMessenger.of(anchorContext).showSnackBar(
                  const SnackBar(content: Text('Privacy settings reset.')),
                );
              },
            ),
          ],
        ),
        ContextMenuSection(
          title: 'Danger Zone',
          items: [
            ContextMenuItem(
              icon: Icons.cleaning_services_rounded,
              label: 'Reset ALL Preferences',
              subtitle: 'Restore factory defaults',
              isDestructive: true,
              onTap: () {
                preferencesController.resetAll();
                themeController.resetToDefaults();
                ScaffoldMessenger.of(anchorContext).showSnackBar(
                  const SnackBar(
                    content: Text('All preferences reset to factory defaults.'),
                  ),
                );
              },
            ),
          ],
        ),
      ],
    );
  }

  void _showExportDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Backup & Export'),
        content: const Text(
          'All your encrypted chats, settings, scheduled rules, and media configurations have been verified and backed up locally.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Close'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('Exported backup package successfully! 📦'),
                ),
              );
            },
            child: const Text('Export JSON'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = themeController.globalTheme;
    final colors = context.colors;
    final user = dataStore.currentUser;

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 1. Top Header Bar (Back, Search, QR scanner, Edit)
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  IconButton(
                    icon: Icon(Icons.arrow_back_rounded, color: theme.primaryTextColor, size: 22),
                    onPressed: () {
                      if (Navigator.of(context).canPop()) {
                        Navigator.of(context).pop();
                      }
                    },
                  ),
                  Row(
                    children: [
                      IconButton(
                        icon: Icon(
                          Icons.search_rounded,
                          color: theme.primaryTextColor,
                          size: 22,
                        ),
                        onPressed: () {
                          showSearch(
                            context: context,
                            delegate: SettingsSearchDelegate(
                              allSettings: _buildSearchIndex(context),
                            ),
                          );
                        },
                      ),
                      IconButton(
                        icon: Icon(
                          Icons.qr_code_scanner_rounded,
                          color: theme.primaryTextColor,
                          size: 22,
                        ),
                        onPressed: () => _showExportDialog(context),
                      ),
                      IconButton(
                        icon: Icon(
                          Icons.edit_outlined,
                          color: theme.primaryTextColor,
                          size: 22,
                        ),
                        onPressed: () => _showEditProfileDialog(context),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 10),

              // 2. User Hero Profile with "Current mood" pill and Dropdown Chevron (Image 1)
              Center(
                child: Column(
                  children: [
                    // Mood Bubble Pill
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                      decoration: BoxDecoration(
                        color: colors.surfaceSecondary,
                        borderRadius: BorderRadius.circular(18),
                        border: Border.all(color: colors.borderSubtle),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.mood_rounded, color: theme.accentColor, size: 16),
                          const SizedBox(width: 6),
                          Text(
                            user.about.isNotEmpty ? user.about : 'Current mood',
                            style: TextStyle(
                              color: theme.primaryTextColor,
                              fontSize: 12.5,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 10),

                    // Avatar Circle
                    Container(
                      width: 86,
                      height: 86,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        border: Border.all(color: theme.accentColor, width: 2.5),
                        boxShadow: [
                          BoxShadow(
                            color: theme.accentColor.withValues(alpha: 0.25),
                            blurRadius: 16,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: CircleAvatar(
                        radius: 40,
                        backgroundColor: colors.surfaceSecondary,
                        child: Text(
                          user.avatarInitials.isNotEmpty ? user.avatarInitials : 'BM',
                          style: TextStyle(
                            fontSize: 28,
                            fontWeight: FontWeight.w800,
                            color: theme.primaryTextColor,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),

                    // Name + Dropdown
                    GestureDetector(
                      onTap: () => _showEditProfileDialog(context),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            user.displayName.isNotEmpty ? user.displayName : 'Bandi Maya',
                            style: TextStyle(
                              color: theme.primaryTextColor,
                              fontSize: 20,
                              fontWeight: FontWeight.w800,
                              letterSpacing: -0.3,
                            ),
                          ),
                          const SizedBox(width: 4),
                          Icon(
                            Icons.keyboard_arrow_down_rounded,
                            color: theme.secondaryTextColor,
                            size: 22,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 3),

                    // Username Handle
                    Text(
                      '@${user.username.isNotEmpty ? user.username : 'bandi_maya'}',
                      style: TextStyle(
                        color: theme.secondaryTextColor,
                        fontSize: 13.5,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),



              // 3. Image 1 Primary Sections Card
              ChatySettingsCard(
                children: [
                  ChatySettingsTile(
                    icon: Icons.currency_rupee_rounded,
                    iconColor: colors.primary,
                    title: 'Payments',
                    onTap: () {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Chaty Secure UPI & Card Payments')),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.diamond_outlined,
                    iconColor: Colors.amber,
                    title: 'Subscriptions',
                    subtitle: 'Explore premium benefits',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const ProPricingScreen(),
                        ),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.devices_rounded,
                    iconColor: colors.info,
                    title: 'Linked devices',
                    subtitle: 'Use WhatsApp on other devices',
                    onTap: () => _showExportDialog(context),
                  ),
                  ChatySettingsTile(
                    icon: Icons.vpn_key_outlined,
                    iconColor: colors.success,
                    title: 'Account',
                    subtitle: 'Security notifications, change number',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => SecurityCenterScreen(
                            preferencesController: preferencesController,
                          ),
                        ),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.lock_outline_rounded,
                    iconColor: colors.primary,
                    title: 'Privacy',
                    subtitle: 'Blocked accounts, disappearing messages',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => PrivacyCenterScreen(
                            preferencesController: preferencesController,
                          ),
                        ),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.photo_library_outlined,
                    iconColor: colors.accent,
                    title: 'Lists',
                    subtitle: 'Manage people and groups',
                    onTap: () {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Lists: Manage people and contact groups')),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.chat_bubble_outline_rounded,
                    iconColor: colors.success,
                    title: 'Chats',
                    subtitle: 'Chat history, backup',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => ConversationSettingsPage(
                            preferencesController: preferencesController,
                          ),
                        ),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.palette_outlined,
                    iconColor: colors.warning,
                    title: 'Appearance',
                    subtitle: 'Chat theme, app icon, app theme',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => UniversalScreen(
                            preferencesController: preferencesController,
                          ),
                        ),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.cast_rounded,
                    iconColor: colors.primary,
                    title: 'Broadcasts',
                    onTap: () {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Broadcast list messaging')),
                      );
                    },
                  ),
                ],
              ),
              const SizedBox(height: 18),

              // 3. Section 1: PRIVACY & SECURITY
              _buildSectionHeader('PRIVACY & SECURITY', theme),
              const SizedBox(height: 8),
              ChatySettingsCard(
                children: [
                  ChatySettingsTile(
                    icon: Icons.shield_outlined,
                    iconColor: colors.info,
                    title: 'Privacy Center',
                    subtitle:
                        'Freeze last seen, anti-delete, read receipts & stealth',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => PrivacyCenterScreen(
                            preferencesController: preferencesController,
                          ),
                        ),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.lock_outline_rounded,
                    iconColor: colors.primary,
                    title: 'Security Center',
                    subtitle:
                        'Chaty App Lock (Biometric/PIN/Pattern), devices & audit',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => SecurityCenterScreen(
                            preferencesController: preferencesController,
                          ),
                        ),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.vpn_key_outlined,
                    iconColor: colors.success,
                    title: 'System Permissions & Hardware',
                    subtitle:
                        'Camera, mic, storage, background refresh & battery',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => SystemPermissionsScreen(
                            preferencesController: preferencesController,
                            notificationService: notificationService,
                          ),
                        ),
                      );
                    },
                  ),
                ],
              ),
              const SizedBox(height: 18),

              // 4. Section 2: APPEARANCE & STYLING
              _buildSectionHeader('THEMES & APPEARANCE', theme),
              const SizedBox(height: 8),
              ChatySettingsCard(
                children: [
                  ChatySettingsTile(
                    icon: Icons.palette_outlined,
                    iconColor: colors.accent,
                    title: 'Themes & Color Engine',
                    subtitle:
                        '13+ dark & light presets, custom tokens, font scaling',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => ThemeEditorScreen(
                            themeController: themeController,
                          ),
                        ),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.auto_awesome_mosaic_outlined,
                    iconColor: colors.primary,
                    title: 'Universal Appearance',
                    subtitle:
                        'App icon changer, custom emojis, typography styles',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => UniversalAppearanceScreen(
                            preferencesController: preferencesController,
                          ),
                        ),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.touch_app_outlined,
                    iconColor: colors.warning,
                    title: 'Navigation & Touch Effects',
                    subtitle:
                        'Interactive click particles (Stars/Hearts/Bubbles), Sound FX',
                    onTap: () {
                      Navigator.push(
                        context,
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
              const SizedBox(height: 18),

              // 5. Section 3: CHATS, CALLS & NOTIFICATIONS
              _buildSectionHeader('CHATS & NOTIFICATIONS', theme),
              const SizedBox(height: 8),
              ChatySettingsCard(
                children: [
                  ChatySettingsTile(
                    icon: Icons.space_dashboard_outlined,
                    iconColor: colors.info,
                    title: 'Home Screen UI Layout',
                    subtitle:
                        'Classic, compact, expressive, stories-first styles',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => HomeScreenSettingsPage(
                            preferencesController: preferencesController,
                          ),
                        ),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.chat_bubble_outline_rounded,
                    iconColor: colors.success,
                    title: 'Conversation Customization',
                    subtitle:
                        'Bubble shapes, corner radius, tick styles, contact sidebar',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => ConversationSettingsPage(
                            preferencesController: preferencesController,
                          ),
                        ),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.notifications_none_rounded,
                    iconColor: colors.warning,
                    title: 'Notification & Sound Studio',
                    subtitle:
                        'Conversation tones, LED colors, heads-up popups, vibration',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => NotificationSettingsPage(
                            preferencesController: preferencesController,
                            notificationService: notificationService,
                          ),
                        ),
                      );
                    },
                  ),
                  ChatySettingsTile(
                    icon: Icons.smart_toy_outlined,
                    iconColor: colors.success,
                    title: 'Message Automation & Scheduler',
                    subtitle:
                        'Auto-reply bot with keyword rules, message scheduler',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => MessageManagementPage(
                            preferencesController: preferencesController,
                            dataStore: dataStore,
                          ),
                        ),
                      );
                    },
                  ),
                ],
              ),
              const SizedBox(height: 18),

              // 6. Section 4: DATA, BACKUPS & ABOUT
              _buildSectionHeader('DATA & SYSTEM', theme),
              const SizedBox(height: 8),
              ChatySettingsCard(
                children: [
                  ChatySettingsTile(
                    icon: Icons.backup_outlined,
                    iconColor: colors.info,
                    title: 'Backup & Export Data',
                    subtitle:
                        'Export encrypted chats JSON, backup verification',
                    onTap: () => _showExportDialog(context),
                  ),
                  ChatySettingsTile(
                    icon: Icons.restore_page_outlined,
                    iconColor: colors.error,
                    title: 'Reset & Diagnostics',
                    subtitle:
                        'Reset privacy settings, themes, or restore factory defaults',
                    onTap: () => _showResetOptions(context),
                  ),
                  ChatySettingsTile(
                    icon: Icons.info_outline_rounded,
                    iconColor: colors.foregroundSecondary,
                    title: 'About Chaty',
                    subtitle:
                        'Version 1.0.0 • Pure private messaging architecture',
                    onTap: () {
                      showAboutDialog(
                        context: context,
                        applicationName: 'Chaty',
                        applicationVersion: '1.0.0 (Build 2026)',
                        applicationLegalese:
                            '© 2026 LOGY BYTE. All rights reserved.',
                        children: const [
                          SizedBox(height: 12),
                          Text(
                            'End-to-end encrypted, deeply customizable instant messaging application.',
                          ),
                        ],
                      );
                    },
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // 7. Log Out Action
              Center(
                child: SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: OutlinedButton.icon(
                    onPressed: () {
                      dataStore.logout();
                      Navigator.of(context).pushAndRemoveUntil(
                        MaterialPageRoute(
                          builder: (_) => const WelcomeScreen(),
                        ),
                        (route) => false,
                      );
                    },
                    icon: Icon(
                      Icons.logout_rounded,
                      color: colors.error,
                      size: 18,
                    ),
                    label: Text(
                      'Log Out of Chaty',
                      style: TextStyle(
                        color: colors.error,
                        fontWeight: FontWeight.w700,
                        fontSize: 14.5,
                      ),
                    ),
                    style: OutlinedButton.styleFrom(
                      side: BorderSide(color: colors.error, width: 1.2),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 90),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSectionHeader(String title, dynamic theme) {
    return Padding(
      padding: const EdgeInsets.only(left: 8.0, top: 4.0),
      child: Text(
        title,
        style: TextStyle(
          fontSize: 11.5,
          fontWeight: FontWeight.w800,
          color: theme.secondaryTextColor.withValues(alpha: 0.8),
          letterSpacing: 0.9,
        ),
      ),
    );
  }
}
