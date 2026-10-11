import 'package:flutter/material.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/contact_relationship_service.dart';
import '../../data/services/notification_service.dart';
import '../../domain/models/user_profile.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/app_icon_controller.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/design_system/gb_design_system.dart';
import '../../ui/core/settings/settings_registry.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../chats/linked_devices_qr_screen.dart';
import '../profile/profile_actions.dart';
import 'account/account_settings_screen.dart';
import 'appearance/app_icon_settings_screen.dart';
import 'appearance/universal_appearance_screen.dart';
import 'calls/call_settings_screen.dart';
import 'conversation/conversation_settings_page.dart';
import 'effects/navigation_effects_page.dart';
import 'home/header_settings_screen.dart';
import 'media/storage_and_media_settings_screen.dart';
import 'message_management/message_management_page.dart';
import 'notifications/notification_settings_page.dart';
import 'permissions/system_permissions_screen.dart';
import 'privacy/privacy_center_screen.dart';
import '../profile/profile_edit_screen.dart';
import '../../data/services/chaty_share_service.dart';
import 'security/security_center_screen.dart';
import 'settings_search_delegate.dart';
import 'templates/templates_settings_screen.dart';
import 'theme_editor_screen.dart';
import 'customization/customization_center_screen.dart';

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
      '/settings/customization' => const CustomizationCenterScreen(),
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
        () => HeaderSettingsScreen(
          preferencesController: preferencesController,
          dataStore: dataStore,
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

  void _showEditProfile(BuildContext context) =>
      ProfileEditScreen.open(context, dataStore);

  Future<void> _logout(BuildContext context) => confirmChatyLogout(context);

  void _openQrScreen(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => LinkedDevicesQrScreen(
          dataStore: dataStore,
          relationshipService: locator<ContactRelationshipService>(),
          preferencesController: preferencesController,
          themeController: themeController,
          qrOnly: true,
        ),
      ),
    );
  }

  void _openLinkedDevices(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => LinkedDevicesQrScreen(
          dataStore: dataStore,
          relationshipService: locator<ContactRelationshipService>(),
          preferencesController: preferencesController,
          themeController: themeController,
          devicesOnly: true,
        ),
      ),
    );
  }


  void _showPrivacyPolicy(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E282E),
        title: const Row(
          children: [
            Icon(Icons.shield_outlined, color: GbColors.activeGreen),
            SizedBox(width: 8),
            Text('Privacy Policy', style: TextStyle(color: Colors.white, fontSize: 18)),
          ],
        ),
        content: const SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'Your Privacy is Our Priority',
                style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14),
              ),
              SizedBox(height: 8),
              Text(
                '• Local End-to-End Encryption:\nYour messages and calls are encrypted and stored safely on your device.\n\n'
                '• No Remote Telemetry:\nChaty never tracks your browsing, locations, or personal data.\n\n'
                '• Security & App Lock:\nLocal PIN, Pattern, and Biometric lock protect your messages completely offline.\n\n'
                '• Full Data Ownership:\nYou can clear chat histories, media caches, and revoke device links at any time.',
                style: TextStyle(color: Color(0xFF8696A0), fontSize: 13, height: 1.4),
              ),
            ],
          ),
        ),
        actions: [
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: GbColors.activeGreen),
            onPressed: () => Navigator.pop(ctx),
            child: const Text('I Understand', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  void _showAboutDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E282E),
        title: const Row(
          children: [
            Icon(Icons.info_outline_rounded, color: GbColors.activeGreen),
            SizedBox(width: 8),
            Text('About Chaty', style: TextStyle(color: Colors.white, fontSize: 18)),
          ],
        ),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Chaty Messenger Pro', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
            SizedBox(height: 4),
            Text('Version 2.4.0 (Build 2026.10)', style: TextStyle(color: Color(0xFF8696A0), fontSize: 12)),
            SizedBox(height: 12),
            Text(
              'A modern, privacy-first messaging platform with advanced personalization, rich customization, and offline security.',
              style: TextStyle(color: Color(0xFFD1D7DB), fontSize: 13, height: 1.3),
            ),
          ],
        ),
        actions: [
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: GbColors.activeGreen),
            onPressed: () => Navigator.pop(ctx),
            child: const Text('OK', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final user = dataStore.currentUser;
    final homePrefs = preferencesController.home;
    final displayName = homePrefs.myNameOverride.isNotEmpty
        ? homePrefs.myNameOverride
        : (user.displayName.isNotEmpty ? user.displayName : 'Bandi Maya');
    final handle = user.username.isNotEmpty ? '@${user.username}' : '@bandi_maya';

    return ListenableBuilder(
      listenable: Listenable.merge([preferencesController, dataStore]),
      builder: (context, _) => Scaffold(
        backgroundColor: const Color(0xFF0C1014),
        appBar: AppBar(
          backgroundColor: const Color(0xFF0C1014),
          elevation: 0,
          title: const Text(
            'Settings',
            style: TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.bold,
              fontSize: 20,
            ),
          ),
          leading: Navigator.of(context).canPop()
              ? IconButton(
                  icon: const Icon(Icons.arrow_back, color: Colors.white, size: 24),
                  onPressed: () => Navigator.of(context).pop(),
                )
              : null,
          actions: [
            IconButton(
              icon: const Icon(Icons.search_rounded, color: Colors.white, size: 24),
              tooltip: 'Search settings',
              onPressed: () => showSearch(
                context: context,
                delegate: SettingsSearchDelegate(allSettings: _searchIndex()),
              ),
            ),
            IconButton(
              icon: const Icon(Icons.qr_code_2_rounded, color: Colors.white, size: 25),
              tooltip: 'QR code',
              onPressed: () => _openQrScreen(context),
            ),
          ],
        ),
        body: SingleChildScrollView(
          child: Column(
            children: [
              // Profile Header Card
              InkWell(
                onTap: () => _showEditProfile(context),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  child: Row(
                    children: [
                      Container(
                        width: 60,
                        height: 60,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: const Color(0xFF223038),
                          border: Border.all(color: const Color(0xFF1E282E), width: 2),
                        ),
                        child: ClipOval(
                          child: user.avatarUrl != null && user.avatarUrl!.isNotEmpty
                              ? Image.network(
                                  user.avatarUrl!,
                                  fit: BoxFit.cover,
                                  errorBuilder: (_, __, ___) => _avatarFallback(user),
                                )
                              : _avatarFallback(user),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              displayName,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 18,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              user.about.isNotEmpty ? user.about : handle,
                              style: const TextStyle(
                                color: Color(0xFF8696A0),
                                fontSize: 13.5,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      const Icon(
                        Icons.chevron_right_rounded,
                        color: Color(0xFF8696A0),
                        size: 24,
                      ),
                    ],
                  ),
                ),
              ),

              const Divider(height: 1, color: Color(0xFF1E282E)),
              const SizedBox(height: 8),

              // Settings Container Card: Only displaying the 5 requested items
              Container(
                width: double.infinity,
                decoration: const BoxDecoration(
                  color: Color(0xFF11161B),
                  borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
                ),
                padding: const EdgeInsets.only(top: 8, bottom: 32),
                child: Column(
                  children: [
                    // Customization Center
                    _SettingsItemTile(
                      icon: const Icon(Icons.palette_outlined, color: Color(0xFF22C55E), size: 24),
                      title: 'Customization Center',
                      subtitle: 'Visual dialects, headers, bubbles, ticks, composers & motion',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => const CustomizationCenterScreen(),
                        ),
                      ),
                    ),

                    // 1. Linked devices
                    _SettingsItemTile(
                      icon: const Icon(Icons.devices_rounded, color: Color(0xFF8696A0), size: 24),
                      title: 'Linked devices',
                      subtitle: 'Use Chaty on other devices',
                      onTap: () => _openLinkedDevices(context),
                    ),

                    // 2. Share Chaty
                    _SettingsItemTile(
                      icon: const Icon(Icons.share_rounded, color: Color(0xFF8696A0), size: 24),
                      title: 'Share Chaty',
                      subtitle: 'Invite friends and family to join Chaty',
                      onTap: () => ChatyShareService.shareApp(context),
                    ),

                    // 3. Privacy policy
                    _SettingsItemTile(
                      icon: const Icon(Icons.shield_outlined, color: Color(0xFF8696A0), size: 24),
                      title: 'Privacy policy',
                      subtitle: 'Read our security and data privacy policy',
                      onTap: () => _showPrivacyPolicy(context),
                    ),

                    // 4. About
                    _SettingsItemTile(
                      icon: const Icon(Icons.info_outline_rounded, color: Color(0xFF8696A0), size: 24),
                      title: 'About',
                      subtitle: 'App version, build and information',
                      onTap: () => _showAboutDialog(context),
                    ),

                    const SizedBox(height: 12),
                    const Divider(height: 1, color: Color(0xFF1E282E), indent: 20, endIndent: 20),
                    const SizedBox(height: 12),

                    // 5. Log out
                    _SettingsItemTile(
                      icon: const Icon(Icons.logout_rounded, color: Color(0xFFEF4444), size: 24),
                      title: 'Log out',
                      subtitle: 'Sign out of your session on this device',
                      onTap: () => _logout(context),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _avatarFallback(UserProfile user) {
    return Container(
      color: const Color(0xFF223038),
      child: Center(
        child: Text(
          user.avatarInitials.isNotEmpty ? user.avatarInitials : 'BM',
          style: const TextStyle(
            color: Colors.white,
            fontSize: 28,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    );
  }
}

class _SettingsItemTile extends StatelessWidget {
  final Widget icon;
  final String title;
  final String? subtitle;
  final VoidCallback? onTap;

  const _SettingsItemTile({
    required this.icon,
    required this.title,
    this.subtitle,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            SizedBox(
              width: 32,
              height: 32,
              child: Center(child: icon),
            ),
            const SizedBox(width: 20),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                      letterSpacing: -0.1,
                    ),
                  ),
                  if (subtitle != null && subtitle!.isNotEmpty) ...[
                    const SizedBox(height: 3),
                    Text(
                      subtitle!,
                      style: const TextStyle(
                        color: Color(0xFF8696A0),
                        fontSize: 13,
                        height: 1.25,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
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

