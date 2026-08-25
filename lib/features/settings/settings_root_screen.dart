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
import '../../ui/core/settings/settings_registry.dart';
import 'account/account_settings_screen.dart';
import 'appearance/app_icon_settings_screen.dart';
import 'appearance/universal_appearance_screen.dart';
import 'calls/call_settings_screen.dart';
import 'templates/templates_settings_screen.dart';
import '../../ui/core/templates/template_controller.dart';
import '../profile/profile_actions.dart';
import 'conversation/conversation_settings_page.dart';
import 'effects/navigation_effects_page.dart';
import 'home/home_screen_settings_page.dart';
import 'media/storage_and_media_settings_screen.dart';
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

  /// Builds search results based on the centralized [SettingsRegistry].
  List<SettingsSearchResult> _searchIndex() {
    return SettingsRegistry.allSettings.map((def) {
      final destination = _destinationForRoute(def.canonicalRoute);
      return SettingsSearchResult(
        title: def.title,
        category: def.category.title,
        description: def.description,
        icon: def.icon ?? def.category.icon,
        destination: destination,
        keywords: def.searchKeywords,
      );
    }).toList();
  }

  Widget _destinationForRoute(String route) {
    return switch (route) {
      '/settings/account' => _reactive(
        () => AccountSettingsScreen(
          preferencesController: preferencesController,
          dataStore: dataStore,
        ),
      ),
      '/settings/privacy' => _reactive(
        () => PrivacyCenterScreen(preferencesController: preferencesController),
      ),
      '/settings/security' => _reactive(
        () =>
            SecurityCenterScreen(preferencesController: preferencesController),
      ),
      '/settings/conversation' => _reactive(
        () => ConversationSettingsPage(
          preferencesController: preferencesController,
        ),
      ),
      '/settings/message_management' => _reactive(
        () => MessageManagementPage(
          preferencesController: preferencesController,
          dataStore: dataStore,
        ),
      ),
      '/settings/themes' => ThemeEditorScreen(themeController: themeController),
      '/settings/templates' => const TemplatesSettingsScreen(),
      '/settings/app_icon' => AppIconSettingsScreen(
        appIconController: _appIconController,
      ),
      '/settings/universal_appearance' => _reactive(
        () => UniversalAppearanceScreen(
          preferencesController: preferencesController,
        ),
      ),
      '/settings/home' => _reactive(
        () => HomeScreenSettingsPage(
          preferencesController: preferencesController,
        ),
      ),
      '/settings/notifications' => _reactive(
        () => NotificationSettingsPage(
          preferencesController: preferencesController,
          notificationService: notificationService,
        ),
      ),
      '/settings/calls' => _reactive(
        () => CallSettingsScreen(preferencesController: preferencesController),
      ),
      '/settings/storage' => _reactive(
        () => StorageAndMediaSettingsScreen(
          preferencesController: preferencesController,
        ),
      ),
      '/settings/effects' => _reactive(
        () =>
            NavigationEffectsPage(preferencesController: preferencesController),
      ),
      '/settings/permissions' => _reactive(
        () => SystemPermissionsScreen(
          preferencesController: preferencesController,
          notificationService: notificationService,
        ),
      ),
      _ => _reactive(
        () => AccountSettingsScreen(
          preferencesController: preferencesController,
          dataStore: dataStore,
        ),
      ),
    };
  }

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
        subtitle: 'Account, privacy, appearance & app configuration',
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

          // -------------------------------------------------------------------
          // 1. ACCOUNT
          // -------------------------------------------------------------------
          ChatySettingsSection(
            title: 'Account',
            children: [
              ChatySettingsTile(
                icon: Icons.person_outline_rounded,
                title: 'Account & Profile',
                subtitle: 'Display name, username, bio & credentials',
                onTap: () => _push(
                  context,
                  () => AccountSettingsScreen(
                    preferencesController: preferencesController,
                    dataStore: dataStore,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.visibility_off_outlined,
                title: 'Privacy',
                subtitle: 'Last seen, online presence, receipts & anti-delete',
                onTap: () => _push(
                  context,
                  () => PrivacyCenterScreen(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.lock_outline_rounded,
                title: 'Security & Lock',
                subtitle: 'App lock, biometric, PIN & hidden chats',
                onTap: () => _push(
                  context,
                  () => SecurityCenterScreen(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
            ],
          ),

          // -------------------------------------------------------------------
          // 2. EXPERIENCE
          // -------------------------------------------------------------------
          ChatySettingsSection(
            title: 'Experience',
            children: [
              ChatySettingsTile(
                icon: Icons.chat_bubble_outline_rounded,
                title: 'Chats',
                subtitle: 'Bubbles, ticks, swipe actions & animated emoji',
                onTap: () => _push(
                  context,
                  () => ConversationSettingsPage(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.palette_outlined,
                title: 'Appearance & Themes',
                subtitle: 'Theme presets, custom palette & typography',
                onTap: () => _push(
                  context,
                  () => ThemeEditorScreen(themeController: themeController),
                  listenToPreferences: false,
                ),
              ),
              ListenableBuilder(
                listenable: locator<TemplateController>(),
                builder: (context, _) {
                  final templateCtrl = locator<TemplateController>();
                  final activeTemplate = templateCtrl.baseTemplate.displayName;
                  final overrideCount = templateCtrl.componentOverrides.length;
                  final subtitle = overrideCount > 0
                      ? ' •  component overrides'
                      : activeTemplate;

                  return ChatySettingsTile(
                    icon: Icons.dashboard_customize_rounded,
                    title: 'Templates',
                    subtitle: subtitle,
                    badgeText: 'NEW',
                    badgeColor: Theme.of(context).colorScheme.primary,
                    onTap: () => _push(
                      context,
                      () => const TemplatesSettingsScreen(),
                      listenToPreferences: false,
                    ),
                  );
                },
              ),
              ChatySettingsTile(
                icon: Icons.apps_rounded,
                title: 'App Icon',
                subtitle: 'Launcher icon & custom brand artwork',
                onTap: () => _push(
                  context,
                  () => AppIconSettingsScreen(
                    appIconController: appIconController,
                  ),
                  listenToPreferences: false,
                ),
              ),
              ChatySettingsTile(
                icon: Icons.home_outlined,
                title: 'Home & Navigation',
                subtitle: 'Layout mode, stories strip & group separation',
                onTap: () => _push(
                  context,
                  () => HomeScreenSettingsPage(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
            ],
          ),

          // -------------------------------------------------------------------
          // 3. COMMUNICATION
          // -------------------------------------------------------------------
          ChatySettingsSection(
            title: 'Communication',
            children: [
              ChatySettingsTile(
                icon: Icons.notifications_outlined,
                title: 'Notifications',
                subtitle: 'Toast alerts, preview, sounds & presence alerts',
                onTap: () => _push(
                  context,
                  () => NotificationSettingsPage(
                    preferencesController: preferencesController,
                    notificationService: notificationService,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.call_outlined,
                title: 'Calls',
                subtitle: 'Who can call you, audio quality & call island',
                onTap: () => _push(
                  context,
                  () => CallSettingsScreen(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
            ],
          ),

          // -------------------------------------------------------------------
          // 4. MEDIA & DATA
          // -------------------------------------------------------------------
          ChatySettingsSection(
            title: 'Media & Data',
            children: [
              ChatySettingsTile(
                icon: Icons.storage_rounded,
                title: 'Storage & Data',
                subtitle: 'HD media sending, upload thresholds & cache cleaner',
                onTap: () => _push(
                  context,
                  () => StorageAndMediaSettingsScreen(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
            ],
          ),

          // -------------------------------------------------------------------
          // 5. SYSTEM & AUTOMATION
          // -------------------------------------------------------------------
          ChatySettingsSection(
            title: 'System & Automation',
            children: [
              ChatySettingsTile(
                icon: Icons.schedule_send_rounded,
                title: 'Message Automation',
                subtitle: 'Auto-reply rules & scheduled messaging',
                onTap: () => _push(
                  context,
                  () => MessageManagementPage(
                    preferencesController: preferencesController,
                    dataStore: dataStore,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.animation_rounded,
                title: 'Interactive Effects',
                subtitle: 'Touch particle animations & falling emoji effects',
                onTap: () => _push(
                  context,
                  () => NavigationEffectsPage(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.admin_panel_settings_outlined,
                title: 'System Permissions',
                subtitle: 'Hardware, notifications, storage & OS rights',
                onTap: () => _push(
                  context,
                  () => SystemPermissionsScreen(
                    preferencesController: preferencesController,
                    notificationService: notificationService,
                  ),
                ),
              ),
              const ChatySettingsTile(
                icon: Icons.info_outline_rounded,
                title: 'About Chaty',
                subtitle: 'Private customizable messaging • Version 1.0.0',
              ),
              ChatySettingsTile(
                icon: Icons.logout_rounded,
                iconColor: Theme.of(context).colorScheme.error,
                title: 'Log Out',
                subtitle: 'Sign out of your session on this device',
                onTap: () => _logout(context),
              ),
            ],
          ),
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
      builder: (context, _) => builder(),
    );
  }
}

class _ProfileCard extends StatelessWidget {
  final UserProfile user;
  final VoidCallback onEdit;

  const _ProfileCard({required this.user, required this.onEdit});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final initials = user.avatarInitials;

    return InkWell(
      onTap: onEdit,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: theme.cardColor,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: theme.dividerColor.withValues(alpha: 0.12),
            width: 1,
          ),
        ),
        child: Row(
          children: [
            AppAvatar(
              initials: initials,
              colorHex: user.avatarColorHex,
              size: 56,
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    user.displayName.isNotEmpty
                        ? user.displayName
                        : 'Set Display Name',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '@',
                    style: TextStyle(
                      fontSize: 13,
                      color: theme.textTheme.bodyMedium?.color?.withValues(
                        alpha: 0.7,
                      ),
                    ),
                  ),
                  if (user.about.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(
                      user.about,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 12,
                        color: theme.textTheme.bodyMedium?.color?.withValues(
                          alpha: 0.55,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const Icon(Icons.edit_outlined, size: 20),
          ],
        ),
      ),
    );
  }
}
