import 'package:flutter/material.dart';

import '../../data/repositories/mock_data_store.dart';
import '../../data/services/chaty_backend_service.dart';
import '../../data/services/chaty_notification_service.dart';
import '../../domain/models/user_profile.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/app_icon_controller.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../ui/core/design_system/chaty_settings_primitives.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../../ui/core/validators/chaty_validators.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/widgets/chaty_brand_icon.dart';
import '../../ui/core/widgets/username_availability_field.dart';
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
        description: 'Change the Android launcher icon or use a custom in-app Chaty brand image',
        icon: Icons.apps_rounded,
        destination: AppIconSettingsScreen(appIconController: _appIconController),
      ),
      SettingsSearchResult(
        title: 'Advanced Features',
        category: 'Advanced',
        description: 'Privacy, media, status, messaging, appearance and behavior controls',
        icon: Icons.tune_rounded,
        destination: _reactive(() => GbFeatureCenterScreen(preferencesController: preferencesController)),
      ),
      SettingsSearchResult(
        title: 'Privacy',
        category: 'Privacy & Security',
        description: 'Last seen, receipts, deleted messages and status privacy',
        icon: Icons.visibility_off_rounded,
        destination: _reactive(() => PrivacyCenterScreen(preferencesController: preferencesController)),
      ),
      SettingsSearchResult(
        title: 'Security',
        category: 'Privacy & Security',
        description: 'App lock and account security controls',
        icon: Icons.security_rounded,
        destination: _reactive(() => SecurityCenterScreen(preferencesController: preferencesController)),
      ),
      SettingsSearchResult(
        title: 'Themes',
        category: 'Appearance & Personalization',
        description: 'Dark, light and custom theme presets',
        icon: Icons.palette_rounded,
        destination: ChatyThemeEditorScreen(themeController: themeController),
      ),
      SettingsSearchResult(
        title: 'Universal Appearance',
        category: 'Appearance & Personalization',
        description: 'Navigation, icons, typography and transitions',
        icon: Icons.auto_awesome_rounded,
        destination: _reactive(() => UniversalAppearanceScreen(preferencesController: preferencesController)),
      ),
      SettingsSearchResult(
        title: 'Home Screen',
        category: 'Appearance & Personalization',
        description: 'Search, stories and home layout options',
        icon: Icons.home_outlined,
        destination: _reactive(() => HomeScreenSettingsPage(preferencesController: preferencesController)),
      ),
      SettingsSearchResult(
        title: 'Conversation',
        category: 'Chats',
        description: 'Bubbles, sidebar and conversation presentation',
        icon: Icons.chat_bubble_outline_rounded,
        destination: _reactive(() => ConversationSettingsPage(preferencesController: preferencesController)),
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
      ),
    ];
  }

  Future<void> _showEditProfile(BuildContext context) async {
    final user = dataStore.currentUser;
    final backend = locator<ChatyBackendService>();
    final formKey = GlobalKey<FormState>();
    final displayNameController = TextEditingController(text: user.displayName);
    final usernameController = TextEditingController(text: user.username);
    final aboutController = TextEditingController(text: user.about);
    final phoneController = TextEditingController(text: user.phone);
    var saving = false;
    bool? usernameAvailable = true;

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheetContext) => StatefulBuilder(
        builder: (sheetContext, setSheetState) {
          Future<void> save() async {
            if (saving || formKey.currentState?.validate() != true) return;
            final normalized = ChatyValidators.normalizeUsername(usernameController.text);
            final unchanged = normalized == ChatyValidators.normalizeUsername(user.username);
            if (!unchanged && usernameAvailable != true) {
              ScaffoldMessenger.of(sheetContext).showSnackBar(
                const SnackBar(content: Text('Choose an available username before saving.')),
              );
              return;
            }
            setSheetState(() => saving = true);
            final displayName = displayNameController.text.trim();
            final about = aboutController.text.trim();
            final phone = phoneController.text.trim();
            final updated = user.copyWith(
              displayName: displayName,
              username: normalized,
              about: about,
              phone: phone,
              avatarInitials: _initialsFor(displayName),
            );
            try {
              if (!unchanged && !await backend.isUsernameAvailable(normalized)) {
                if (!sheetContext.mounted) return;
                setSheetState(() {
                  saving = false;
                  usernameAvailable = false;
                });
                formKey.currentState?.validate();
                return;
              }
              await dataStore.updateUser(updated);
              if (!sheetContext.mounted) return;
              Navigator.of(sheetContext).pop();
              if (context.mounted) {
                ScaffoldMessenger.of(context)
                  ..hideCurrentSnackBar()
                  ..showSnackBar(const SnackBar(content: Text('Profile updated.')));
              }
            } catch (error) {
              if (!sheetContext.mounted) return;
              setSheetState(() => saving = false);
              ScaffoldMessenger.of(sheetContext).showSnackBar(
                SnackBar(content: Text('Could not update profile: $error')),
              );
            }
          }

          return Padding(
            padding: EdgeInsets.fromLTRB(
              20,
              4,
              20,
              MediaQuery.viewInsetsOf(sheetContext).bottom + 20,
            ),
            child: SingleChildScrollView(
              child: Form(
                key: formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Edit profile', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
                    const SizedBox(height: 4),
                    Text(
                      'Update your profile. Usernames are checked live so you never submit a name that is already taken.',
                      style: Theme.of(sheetContext).textTheme.bodySmall,
                    ),
                    const SizedBox(height: 18),
                    TextFormField(
                      controller: displayNameController,
                      enabled: !saving,
                      textInputAction: TextInputAction.next,
                      validator: ChatyValidators.validateDisplayName,
                      decoration: const InputDecoration(labelText: 'Display name', border: OutlineInputBorder()),
                    ),
                    const SizedBox(height: 12),
                    UsernameAvailabilityField(
                      controller: usernameController,
                      backend: backend,
                      currentUsername: user.username,
                      enabled: !saving,
                      onAvailabilityChanged: (value) {
                        usernameAvailable = value;
                      },
                      decoration: const InputDecoration(
                        labelText: 'Username',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: phoneController,
                      enabled: !saving,
                      keyboardType: TextInputType.phone,
                      textInputAction: TextInputAction.next,
                      validator: (value) => value == null || value.trim().isEmpty
                          ? null
                          : ChatyValidators.validatePhone(value),
                      decoration: const InputDecoration(labelText: 'Phone', border: OutlineInputBorder()),
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: aboutController,
                      enabled: !saving,
                      minLines: 2,
                      maxLines: 4,
                      maxLength: 256,
                      validator: ChatyValidators.validateBio,
                      decoration: const InputDecoration(labelText: 'About', border: OutlineInputBorder()),
                    ),
                    const SizedBox(height: 14),
                    SizedBox(
                      width: double.infinity,
                      height: 50,
                      child: FilledButton.icon(
                        onPressed: saving ? null : save,
                        icon: saving
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Icon(Icons.check_rounded),
                        label: Text(saving ? 'Saving…' : 'Save profile'),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );

    displayNameController.dispose();
    usernameController.dispose();
    aboutController.dispose();
    phoneController.dispose();
  }

  Future<void> _logout(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Log out of Chaty?'),
        content: const Text(
          'Your account will be signed out on this device. Your chats and account data remain on the server.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(dialogContext).pop(false), child: const Text('Cancel')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Theme.of(dialogContext).colorScheme.error),
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Log out'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    try {
      await locator<ChatyBackendService>().logout();
    } catch (error) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Could not log out: $error')));
    }
  }

  static String _initialsFor(String displayName) {
    final parts = displayName.trim().split(RegExp(r'\s+')).where((part) => part.isNotEmpty).toList();
    if (parts.isEmpty) return 'CU';
    if (parts.length == 1) {
      return parts.first.substring(0, parts.first.length >= 2 ? 2 : 1).toUpperCase();
    }
    return '${parts.first[0]}${parts.last[0]}'.toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final user = dataStore.currentUser;
    final appIconController = _appIconController;

    return ListenableBuilder(
      listenable: appIconController,
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
          _ProfileCard(
            user: user,
            onEdit: () => _showEditProfile(context),
          ),
          const SizedBox(height: 8),
          ChatySettingsSection(
            title: 'Appearance & Personalization',
            children: [
              ChatySettingsTile(
                leading: ChatyBrandIcon(controller: appIconController, size: 36, borderRadius: 10),
                title: 'App Icon',
                subtitle: 'Launcher: ${appIconController.launcherIcon.title}',
                onTap: () => _push(
                  context,
                  () => AppIconSettingsScreen(appIconController: appIconController),
                  listenToPreferences: false,
                ),
              ),
              ChatySettingsTile(
                icon: Icons.palette_rounded,
                title: 'Themes',
                subtitle: 'Dark, light and custom theme presets',
                onTap: () => _push(
                  context,
                  () => ChatyThemeEditorScreen(themeController: themeController),
                  listenToPreferences: false,
                ),
              ),
              ChatySettingsTile(
                icon: Icons.auto_awesome_rounded,
                title: 'Universal appearance',
                subtitle: 'Navigation, icons, fonts and motion',
                onTap: () => _push(
                  context,
                  () => UniversalAppearanceScreen(preferencesController: preferencesController),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.home_outlined,
                title: 'Home screen',
                subtitle: 'Search, stories and home layout',
                onTap: () => _push(
                  context,
                  () => HomeScreenSettingsPage(preferencesController: preferencesController),
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
                  () => ConversationSettingsPage(preferencesController: preferencesController),
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
              ChatySettingsTile(
                icon: Icons.visibility_off_rounded,
                title: 'Privacy',
                subtitle: 'Last seen, receipts, status and anti-delete options',
                onTap: () => _push(
                  context,
                  () => PrivacyCenterScreen(preferencesController: preferencesController),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.security_rounded,
                title: 'Security',
                subtitle: 'App lock and account protection',
                onTap: () => _push(
                  context,
                  () => SecurityCenterScreen(preferencesController: preferencesController),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.admin_panel_settings_outlined,
                title: 'Permissions',
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
          ChatySettingsSection(
            title: 'Advanced',
            children: [
              ChatySettingsTile(
                icon: Icons.tune_rounded,
                title: 'Advanced Features',
                subtitle: 'Detailed privacy, media, status, messaging and behavior controls',
                onTap: () => _push(
                  context,
                  () => GbFeatureCenterScreen(preferencesController: preferencesController),
                ),
              ),
              ChatySettingsTile(
                icon: Icons.animation_rounded,
                title: 'Navigation effects',
                subtitle: 'Page transitions and interaction effects',
                onTap: () => _push(
                  context,
                  () => NavigationEffectsPage(preferencesController: preferencesController),
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
            border: Border.all(color: scheme.outlineVariant.withValues(alpha: 0.35)),
          ),
          child: Row(
            children: [
              AppAvatar(initials: user.avatarInitials, colorHex: user.avatarColorHex, size: 54),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      user.displayName,
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
                    ),
                    const SizedBox(height: 2),
                    Text('@${user.username}', style: Theme.of(context).textTheme.bodySmall),
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
