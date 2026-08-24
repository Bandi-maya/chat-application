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
import '../../ui/core/settings/settings_registry.dart';
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
          () => PrivacyCenterScreen(preferencesController: preferencesController),
        ),
      '/settings/privacy' => _reactive(
          () => PrivacyCenterScreen(preferencesController: preferencesController),
        ),
      '/settings/security' => _reactive(
          () => SecurityCenterScreen(preferencesController: preferencesController),
        ),
      '/settings/conversation' => _reactive(
          () => ConversationSettingsPage(preferencesController: preferencesController),
        ),
      '/settings/message_management' => _reactive(
          () => MessageManagementPage(
            preferencesController: preferencesController,
            dataStore: dataStore,
          ),
        ),
      '/settings/themes' => ThemeEditorScreen(themeController: themeController),
      '/settings/app_icon' => AppIconSettingsScreen(
          appIconController: _appIconController,
        ),
      '/settings/universal_appearance' => _reactive(
          () => UniversalAppearanceScreen(preferencesController: preferencesController),
        ),
      '/settings/home' => _reactive(
          () => HomeScreenSettingsPage(preferencesController: preferencesController),
        ),
      '/settings/notifications' => _reactive(
          () => NotificationSettingsPage(
            preferencesController: preferencesController,
            notificationService: notificationService,
          ),
        ),
      '/settings/permissions' => _reactive(
          () => SystemPermissionsScreen(
            preferencesController: preferencesController,
            notificationService: notificationService,
          ),
        ),
      '/settings/advanced' => _reactive(
          () => GbFeatureCenterScreen(preferencesController: preferencesController),
        ),
      _ => _reactive(
          () => UniversalAppearanceScreen(preferencesController: preferencesController),
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
          // ACCOUNT & SECURITY
          // -------------------------------------------------------------------
          ChatySettingsSection(
            title: 'Account & Security',
            children: [
              if (!preferencesController.privacy.hidePrivacyOption)
                ChatySettingsTile(
                  icon: Icons.visibility_off_rounded,
                  title: 'Privacy',
                  subtitle: 'Last seen, read receipts, status & chat privacy',
                  onTap: () => _push(
                    context,
                    () => PrivacyCenterScreen(
                      preferencesController: preferencesController,
                    ),
                  ),
                ),
              ChatySettingsTile(
                icon: Icons.lock_outline_rounded,
                title: 'Security & App Lock',
                subtitle: 'Biometric, PIN, pattern lock and screenshot guard',
                onTap: () => _push(
                  context,
                  () => SecurityCenterScreen(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.admin_panel_settings_outlined,
                title: 'System Permissions',
                subtitle: 'Camera, microphone, media, contacts and notifications',
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

          // -------------------------------------------------------------------
          // EXPERIENCE & DESIGN
          // -------------------------------------------------------------------
          ChatySettingsSection(
            title: 'Experience & Design',
            children: [
              ChatySettingsTile(
                icon: Icons.palette_rounded,
                title: 'Theme & Design Studio',
                subtitle: 'Dark, light & custom presets, palette generator',
                onTap: () => _push(
                  context,
                  () => ThemeEditorScreen(themeController: themeController),
                  listenToPreferences: false,
                ),
              ),
              ChatySettingsTile(
                leading: ChatyBrandIcon(
                  controller: appIconController,
                  size: 36,
                  borderRadius: 10,
                ),
                title: 'App Icon',
                subtitle: 'Launcher: ',
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
                subtitle: 'Layout mode, bottom bar design, header & stories strip',
                onTap: () => _push(
                  context,
                  () => HomeScreenSettingsPage(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.auto_awesome_rounded,
                title: 'Typography & Motion',
                subtitle: 'Text density, font scale & screen transitions',
                onTap: () => _push(
                  context,
                  () => UniversalAppearanceScreen(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
            ],
          ),

          // -------------------------------------------------------------------
          // CHATS & NOTIFICATIONS
          // -------------------------------------------------------------------
          ChatySettingsSection(
            title: 'Chats & Notifications',
            children: [
              ChatySettingsTile(
                icon: Icons.chat_bubble_outline_rounded,
                title: 'Conversation & Bubbles',
                subtitle: '48 bubble styles, 16 ticks, wallpaper & quick sidebar',
                onTap: () => _push(
                  context,
                  () => ConversationSettingsPage(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.schedule_send_rounded,
                title: 'Message Management',
                subtitle: 'Auto-reply rules, quick responses & scheduling',
                onTap: () => _push(
                  context,
                  () => MessageManagementPage(
                    preferencesController: preferencesController,
                    dataStore: dataStore,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.notifications_outlined,
                title: 'Notifications & Alerts',
                subtitle: 'Toast alerts, previews, sounds & online activity',
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

          // -------------------------------------------------------------------
          // ADVANCED & ABOUT
          // -------------------------------------------------------------------
          ChatySettingsSection(
            title: 'Advanced & System',
            children: [
              ChatySettingsTile(
                icon: Icons.tune_rounded,
                title: 'Advanced Features',
                subtitle: 'Power-user controls, media limits & behavior options',
                onTap: () => _push(
                  context,
                  () => GbFeatureCenterScreen(
                    preferencesController: preferencesController,
                  ),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.animation_rounded,
                title: 'Interactive Effects',
                subtitle: 'Touch particle animations & falling effects',
                onTap: () => _push(
                  context,
                  () => NavigationEffectsPage(
                    preferencesController: preferencesController,
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
              color: scheme.outlineVariant.withValues(alpha: 0.5),
            ),
          ),
          child: Row(
            children: [
              AppAvatar(
                initials: user.avatarInitials.isNotEmpty
                    ? user.avatarInitials
                    : (user.displayName.isNotEmpty
                          ? user.displayName.substring(0, 1).toUpperCase()
                          : 'C'),
                colorHex: user.avatarColorHex,
                size: 52,
                showOnlineBadge: true,
                presence: user.presence,
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      user.displayName.isNotEmpty
                          ? user.displayName
                          : 'Alex Rivera',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: scheme.onSurface,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '@',
                      style: TextStyle(
                        fontSize: 12.5,
                        color: scheme.onSurfaceVariant,
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
                          color: scheme.onSurface.withValues(alpha: 0.7),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              Icon(
                Icons.edit_outlined,
                size: 20,
                color: scheme.primary,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
