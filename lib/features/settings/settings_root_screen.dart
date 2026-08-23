import 'package:flutter/material.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/notification_service.dart';
import '../../domain/models/user_profile.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/app_icon_controller.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/design_system/settings_primitives.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/widgets/app_brand_icon.dart';
import '../profile/profile_actions.dart';
import 'appearance/app_icon_settings_screen.dart';
import 'appearance/universal_appearance_screen.dart';
import 'conversation/conversation_settings_page.dart';
import 'effects/navigation_effects_page.dart';
import 'gb_features/gb_feature_center_screen.dart';
import 'home/home_screen_settings_page.dart';
import 'message_management/message_management_page.dart';
import 'notifications/notification_settings_page.dart';
import 'permissions/system_permissions_screen.dart';
import 'privacy/privacy_center_screen.dart';
import 'security/security_center_screen.dart';
import 'settings_search_delegate.dart';
import 'theme_editor_screen.dart';

class SettingsRootScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;
  final ChatyDataStore dataStore;
  final ChatyNotificationService notificationService;

  const SettingsRootScreen({
    super.key,
    required this.preferencesController,
    required this.themeController,
    required this.dataStore,
    required this.notificationService,
  });

  AppIconController get _appIconController => locator<AppIconController>();

  Widget _reactive(Widget Function() builder) {
    return _PreferencesReactiveRoute(
      preferencesController: preferencesController,
      builder: builder,
    );
  }

  void _push(
    BuildContext context,
    Widget Function() builder, {
    bool listenToPreferences = true,
  }) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => listenToPreferences ? _reactive(builder) : builder(),
      ),
    );
  }

  List<SettingsSearchResult> _searchIndex() {
    return <SettingsSearchResult>[
      SettingsSearchResult(
        title: 'App Icon',
        category: 'Appearance & Personalization',
        description:
            'Change the Android launcher icon or use a custom in-app Chaty brand image',
        icon: Icons.apps_rounded,
        destination: AppIconSettingsScreen(
          appIconController: _appIconController,
        ),
        keywords: const ['launcher', 'icon', 'logo', 'badge', 'brand', 'alias'],
      ),
      SettingsSearchResult(
        title: 'Advanced Features',
        category: 'Advanced',
        description:
            'Privacy, media, status, messaging, appearance and behavior controls',
        icon: Icons.tune_rounded,
        destination: _reactive(
          () => GbFeatureCenterScreen(
            preferencesController: preferencesController,
          ),
        ),
        keywords: const [
          'gb',
          'mod',
          'tweaks',
          'advanced',
          'customization',
          'power',
        ],
      ),
      SettingsSearchResult(
        title: 'Privacy',
        category: 'Privacy & Security',
        description: 'Last seen, receipts, deleted messages and status privacy',
        icon: Icons.visibility_off_rounded,
        destination: _reactive(
          () =>
              PrivacyCenterScreen(preferencesController: preferencesController),
        ),
        keywords: const [
          'blue tick',
          'read receipt',
          'freeze last seen',
          'ghost',
          'anti delete',
          'view once',
          'incognito',
          'online status',
        ],
      ),
      SettingsSearchResult(
        title: 'Security',
        category: 'Privacy & Security',
        description: 'App lock and account security controls',
        icon: Icons.security_rounded,
        destination: _reactive(
          () => SecurityCenterScreen(
            preferencesController: preferencesController,
          ),
        ),
        keywords: const [
          'pin',
          'biometric',
          'fingerprint',
          'face unlock',
          'pattern',
          'app lock',
          'password',
          'lock chat',
        ],
      ),
      SettingsSearchResult(
        title: 'Themes',
        category: 'Appearance & Personalization',
        description: 'Dark, light and custom theme presets',
        icon: Icons.palette_rounded,
        destination: ThemeEditorScreen(themeController: themeController),
        keywords: const [
          'amoled',
          'dark mode',
          'light mode',
          'true black',
          'midnight',
          'emerald',
          'crimson',
          'color',
          'palette',
          'custom theme',
        ],
      ),
      SettingsSearchResult(
        title: 'Universal Appearance',
        category: 'Appearance & Personalization',
        description: 'Navigation, icons, typography and transitions',
        icon: Icons.auto_awesome_rounded,
        destination: _reactive(
          () => UniversalAppearanceScreen(
            preferencesController: preferencesController,
          ),
        ),
        keywords: const [
          'font scale',
          'density',
          'reduced motion',
          'effects',
          'particles',
          'typography',
          'transitions',
        ],
      ),
      SettingsSearchResult(
        title: 'Home Screen',
        category: 'Appearance & Personalization',
        description: 'Search, stories and home layout options',
        icon: Icons.home_outlined,
        destination: _reactive(
          () => HomeScreenSettingsPage(
            preferencesController: preferencesController,
          ),
        ),
        keywords: const [
          'stories',
          'separate chats',
          'groups',
          'layout',
          'density',
          'avatar shape',
          'ghost mode',
        ],
      ),
      SettingsSearchResult(
        title: 'Conversation',
        category: 'Chats',
        description: 'Bubbles, sidebar and conversation presentation',
        icon: Icons.chat_bubble_outline_rounded,
        destination: _reactive(
          () => ConversationSettingsPage(
            preferencesController: preferencesController,
          ),
        ),
        keywords: const [
          'bubble shape',
          'bubble radius',
          'tick style',
          'double tap reaction',
          'sidebar',
          'enter is send',
          'voice speed',
          'wallpaper',
        ],
      ),
      SettingsSearchResult(
        title: 'Notifications',
        category: 'Notifications',
        description: 'Alerts, previews, sounds and notification behavior',
        icon: Icons.notifications_outlined,
        destination: _reactive(
          () => NotificationSettingsPage(
            preferencesController: preferencesController,
            notificationService: notificationService,
          ),
        ),
        keywords: const [
          'preview',
          'alert',
          'sound',
          'badge',
          'vibrate',
          'channel',
          'toast',
          'online alert',
        ],
      ),
      SettingsSearchResult(
        title: 'Message Automation',
        category: 'Messaging',
        description: 'Auto-reply rules, quick replies and scheduled messages',
        icon: Icons.schedule_send_rounded,
        destination: _reactive(
          () => MessageManagementPage(
            preferencesController: preferencesController,
            dataStore: dataStore,
          ),
        ),
        keywords: const [
          'auto reply',
          'schedule',
          'quick reply',
          'template',
          'bot',
          'auto response',
        ],
      ),
      SettingsSearchResult(
        title: 'Navigation Effects & Particles',
        category: 'Appearance & Personalization',
        description:
            'Touch particle effects, screen transitions and falling effects',
        icon: Icons.animation_rounded,
        destination: _reactive(
          () => NavigationEffectsPage(
            preferencesController: preferencesController,
          ),
        ),
        keywords: const [
          'particles',
          'sparkles',
          'falling stars',
          'hearts',
          'touch effect',
          'page transition',
        ],
      ),
      SettingsSearchResult(
        title: 'System Permissions',
        category: 'System',
        description:
            'Camera, microphone, storage, contacts and notification permissions',
        icon: Icons.admin_panel_settings_rounded,
        destination: _reactive(
          () => SystemPermissionsScreen(
            preferencesController: preferencesController,
            notificationService: notificationService,
          ),
        ),
        keywords: const [
          'permission',
          'camera',
          'microphone',
          'storage',
          'contacts',
          'access',
        ],
      ),
    ];
  }

  // Shared flows live in profile_actions.dart so the Profile root screen
  // and Settings use exactly one editor and one logout confirmation.
  Future<void> _showEditProfile(BuildContext context) =>
      showChatyProfileEditor(context, dataStore);

  Future<void> _logout(BuildContext context) => confirmChatyLogout(context);

  @override
  Widget build(BuildContext context) {
    final user = dataStore.currentUser;
    final appIconController = _appIconController;

    return ListenableBuilder(
      listenable: Listenable.merge([appIconController, preferencesController]),
      builder: (context, _) => ChatySettingsPage(
        title: 'Settings',
        subtitle: 'Account, privacy, appearance and app behavior',
        trailingHeaderWidget: IconButton(
          tooltip: 'Search settings',
          onPressed: () => showSearch(
            context: context,
            delegate: SettingsSearchDelegate(allSettings: _searchIndex()),
          ),
          icon: const Icon(Icons.search_rounded),
        ),
        children: [
          _ProfileCard(user: user, onEdit: () => _showEditProfile(context)),
          const SizedBox(height: 8),
          ChatySettingsSection(
            title: 'Appearance & Personalization',
            children: [
              ChatySettingsTile(
                leading: ChatyBrandIcon(
                  controller: appIconController,
                  size: 36,
                  borderRadius: 10,
                ),
                title: 'App Icon',
                subtitle: 'Launcher: ${appIconController.launcherIcon.title}',
                onTap: () => _push(
                  context,
                  () => AppIconSettingsScreen(
                    appIconController: appIconController,
                  ),
                  listenToPreferences: false,
                ),
              ),
              ChatySettingsTile(
                icon: Icons.palette_rounded,
                title: 'Themes',
                subtitle: 'Dark, light and custom theme presets',
                onTap: () => _push(
                  context,
                  () =>
                      ThemeEditorScreen(themeController: themeController),
                  listenToPreferences: false,
                ),
              ),
              ChatySettingsTile(
                icon: Icons.auto_awesome_rounded,
                title: 'Universal appearance',
                subtitle: 'Navigation, icons, fonts and motion',
                onTap: () => _push(
                  context,
                  () => UniversalAppearanceScreen(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.home_outlined,
                title: 'Home screen',
                subtitle: 'Search, stories and home layout',
                onTap: () => _push(
                  context,
                  () => HomeScreenSettingsPage(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
            ],
          ),
          ChatySettingsSection(
            title: 'Chats',
            children: [
              ChatySettingsTile(
                icon: Icons.chat_bubble_outline_rounded,
                title: 'Conversation',
                subtitle: 'Bubbles, wallpaper and conversation layout',
                onTap: () => _push(
                  context,
                  () => ConversationSettingsPage(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.schedule_send_rounded,
                title: 'Message management',
                subtitle: 'Quick replies, automation and scheduled messages',
                onTap: () => _push(
                  context,
                  () => MessageManagementPage(
                    preferencesController: preferencesController,
                    dataStore: dataStore,
                  ),
                ),
              ),
            ],
          ),
          ChatySettingsSection(
            title: 'Notifications',
            children: [
              ChatySettingsTile(
                icon: Icons.notifications_outlined,
                title: 'Notification settings',
                subtitle: 'Alerts, previews, sounds and notification behavior',
                onTap: () => _push(
                  context,
                  () => NotificationSettingsPage(
                    preferencesController: preferencesController,
                    notificationService: notificationService,
                  ),
                ),
              ),
            ],
          ),
          ChatySettingsSection(
            title: 'Privacy & Security',
            children: [
              // "Hide Privacy Option" removes this entry from the menu. It stays
              // recoverable from Advanced Features (same Saleh_HidePrivacy key).
              if (!preferencesController.privacy.hidePrivacyOption)
                ChatySettingsTile(
                  icon: Icons.visibility_off_rounded,
                  title: 'Privacy',
                  subtitle:
                      'Last seen, receipts, status and anti-delete options',
                  onTap: () => _push(
                    context,
                    () => PrivacyCenterScreen(
                      preferencesController: preferencesController,
                    ),
                  ),
                ),
              ChatySettingsTile(
                icon: Icons.security_rounded,
                title: 'Security',
                subtitle: 'App lock and account protection',
                onTap: () => _push(
                  context,
                  () => SecurityCenterScreen(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.admin_panel_settings_outlined,
                title: 'Permissions',
                subtitle:
                    'Camera, microphone, media, contacts and notifications',
                onTap: () => _push(
                  context,
                  () => SystemPermissionsScreen(
                    preferencesController: preferencesController,
                    notificationService: notificationService,
                  ),
                ),
              ),
            ],
          ),
          ChatySettingsSection(
            title: 'Advanced',
            children: [
              ChatySettingsTile(
                icon: Icons.tune_rounded,
                title: 'Advanced Features',
                subtitle:
                    'Detailed privacy, media, status, messaging and behavior controls',
                onTap: () => _push(
                  context,
                  () => GbFeatureCenterScreen(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.animation_rounded,
                title: 'Navigation effects',
                subtitle: 'Page transitions and interaction effects',
                onTap: () => _push(
                  context,
                  () => NavigationEffectsPage(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
            ],
          ),
          ChatySettingsSection(
            title: 'About & Account',
            children: [
              const ChatySettingsTile(
                icon: Icons.info_outline_rounded,
                title: 'Chaty',
                subtitle: 'Private customizable messaging • version 1.0.0',
              ),
              ChatySettingsTile(
                icon: Icons.logout_rounded,
                iconColor: Theme.of(context).colorScheme.error,
                title: 'Log out',
                subtitle: 'Sign out of this Chaty account on this device',
                onTap: () => _logout(context),
              ),
            ],
          ),
          const SizedBox(height: 18),
        ],
      ),
    );
  }
}

class _PreferencesReactiveRoute extends StatelessWidget {
  final ChatyPreferencesController preferencesController;
  final Widget Function() builder;

  const _PreferencesReactiveRoute({
    required this.preferencesController,
    required this.builder,
  });

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: preferencesController,
      builder: (_, _) => builder(),
    );
  }
}

class _ProfileCard extends StatelessWidget {
  final UserProfile user;
  final VoidCallback onEdit;

  const _ProfileCard({required this.user, required this.onEdit});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: scheme.surfaceContainerLow,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        onTap: onEdit,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.all(15),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: scheme.outlineVariant.withValues(alpha: 0.35),
            ),
          ),
          child: Row(
            children: [
              AppAvatar(
                initials: user.avatarInitials,
                colorHex: user.avatarColorHex,
                size: 54,
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      user.displayName,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '@${user.username}',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    if (user.about.isNotEmpty) ...[
                      const SizedBox(height: 3),
                      Text(
                        user.about,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ],
                ),
              ),
              IconButton(
                tooltip: 'Edit profile',
                onPressed: onEdit,
                icon: const Icon(Icons.edit_outlined),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
