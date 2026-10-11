import 'dart:io';

import 'package:flutter/material.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/profile_media_service.dart';
import '../../data/services/contact_relationship_service.dart';
import '../../data/services/notification_service.dart';
import '../../domain/models/user_profile.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/app_icon_controller.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/design_system/gb_design_system.dart';
import '../../ui/core/design_system/design_system.dart';
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
    return ListenableBuilder(
      listenable: Listenable.merge([preferencesController, dataStore, themeController]),
      builder: (context, _) {
        // Read account fields inside the listener so changed avatars, names,
        // and banners refresh without leaving Settings and reopening it.
        final user = dataStore.currentUser;
        final homePrefs = preferencesController.home;
        final displayName = homePrefs.myNameOverride.isNotEmpty
            ? homePrefs.myNameOverride
            : (user.displayName.isNotEmpty ? user.displayName : 'Bandi Maya');
        final handle = user.username.isNotEmpty ? '@${user.username}' : '@bandi_maya';
        final colors = context.colors;
        final theme = themeController.globalTheme;
        return Scaffold(
        backgroundColor: colors.background,
        appBar: AppBar(
          backgroundColor: colors.background,
          elevation: 0,
          title: Text(
            'Settings',
            style: TextStyle(
              color: colors.foreground,
              fontWeight: FontWeight.bold,
              fontSize: 20,
            ),
          ),
          leading: Navigator.of(context).canPop()
              ? IconButton(
                  icon: Icon(Icons.arrow_back, color: colors.foreground, size: 24),
                  onPressed: () => Navigator.of(context).pop(),
                )
              : null,
          actions: [
            IconButton(
              icon: Icon(Icons.search_rounded, color: colors.foreground, size: 24),
              tooltip: 'Search settings',
              onPressed: () => showSearch(
                context: context,
                delegate: SettingsSearchDelegate(allSettings: _searchIndex()),
              ),
            ),
            IconButton(
              icon: Icon(Icons.qr_code_2_rounded, color: colors.foreground, size: 25),
              tooltip: 'QR code',
              onPressed: () => _openQrScreen(context),
            ),
          ],
        ),
        body: SingleChildScrollView(
          child: Column(
            children: [
              // Editable account cover and overlapping avatar; all media is
              // loaded from the user's own profile data rather than a demo asset.
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 10, 16, 16),
                child: Column(
                  children: [
                    Stack(
                      clipBehavior: Clip.none,
                      alignment: Alignment.bottomCenter,
                      children: [
                        Container(
                          height: 158,
                          width: double.infinity,
                          decoration: BoxDecoration(
                            color: colors.surface,
                            borderRadius: BorderRadius.circular(20),
                            border: Border.all(color: colors.borderSubtle),
                          ),
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(19),
                            child: (user.bannerUrl ?? '').trim().isEmpty
                                ? DecoratedBox(
                                    decoration: BoxDecoration(
                                      gradient: LinearGradient(
                                        begin: Alignment.topLeft,
                                        end: Alignment.bottomRight,
                                        colors: [
                                          theme.accentColor.withValues(alpha: 0.30),
                                          colors.surface,
                                          colors.background,
                                        ],
                                      ),
                                    ),
                                    child: Center(
                                      child: Icon(
                                        Icons.wallpaper_outlined,
                                        size: 34,
                                        color: theme.accentColor.withValues(alpha: 0.65),
                                      ),
                                    ),
                                  )
                                : _accountBannerImage(user.bannerUrl!, colors),
                          ),
                        ),
                        Positioned(
                          right: 10,
                          top: 10,
                          child: Material(
                            color: colors.surface.withValues(alpha: 0.92),
                            shape: const CircleBorder(),
                            child: IconButton(
                              tooltip: 'Change account banner',
                              visualDensity: VisualDensity.compact,
                              onPressed: () => _editAccountBanner(context),
                              icon: Icon(
                                Icons.wallpaper_outlined,
                                color: colors.foreground,
                                size: 19,
                              ),
                            ),
                          ),
                        ),
                        Positioned(
                          bottom: -40,
                          child: Container(
                            padding: const EdgeInsets.all(4),
                            decoration: BoxDecoration(
                              color: colors.background,
                              shape: BoxShape.circle,
                              border: Border.all(color: colors.borderSubtle, width: 1),
                            ),
                            child: ClipOval(
                              child: user.avatarUrl != null && user.avatarUrl!.isNotEmpty
                                  ? Image.network(
                                      user.avatarUrl!,
                                      width: 84,
                                      height: 84,
                                      fit: BoxFit.cover,
                                      errorBuilder: (_, __, ___) => _avatarFallback(user, size: 84, colors: colors),
                                    )
                                  : SizedBox(
                                      width: 84,
                                      height: 84,
                                      child: _avatarFallback(user, size: 84, colors: colors),
                                    ),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 50),
                    InkWell(
                      onTap: () => _showEditProfile(context),
                      borderRadius: BorderRadius.circular(12),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Flexible(
                              child: Text(
                                displayName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  color: colors.foreground,
                                  fontSize: 21,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: -0.35,
                                ),
                              ),
                            ),
                            const SizedBox(width: 4),
                            Icon(
                              Icons.keyboard_arrow_down_rounded,
                              color: colors.foregroundSecondary,
                              size: 21,
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      handle,
                      style: TextStyle(
                        color: colors.foregroundSecondary,
                        fontSize: 13,
                      ),
                    ),
                    if (user.about.isNotEmpty) ...[
                      const SizedBox(height: 6),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 20),
                        child: Text(
                          user.about,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: colors.foregroundSecondary,
                            fontSize: 12.5,
                          ),
                        ),
                      ),
                    ],
                    const SizedBox(height: 10),
                    OutlinedButton.icon(
                      onPressed: () => _showEditProfile(context),
                      icon: const Icon(Icons.edit_outlined, size: 16),
                      label: const Text('Edit profile'),
                    ),
                  ],
                ),
              ),

              Divider(height: 1, color: colors.divider),
              const SizedBox(height: 8),

              // Settings Container Card: Only displaying the 5 requested items
              Container(
                width: double.infinity,
                decoration: BoxDecoration(
                  color: colors.surfaceSecondary,
                  borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
                  border: Border(top: BorderSide(color: colors.borderSubtle)),
                ),
                padding: const EdgeInsets.only(top: 8, bottom: 32),
                child: Column(
                  children: [
                    // Customization Center
                    _SettingsItemTile(
                      icon: Icon(Icons.palette_outlined, color: colors.primary, size: 24),
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
                      icon: Icon(Icons.devices_rounded, color: colors.foregroundSecondary, size: 24),
                      title: 'Linked devices',
                      subtitle: 'Use Chaty on other devices',
                      onTap: () => _openLinkedDevices(context),
                    ),

                    // 2. Share Chaty
                    _SettingsItemTile(
                      icon: Icon(Icons.share_rounded, color: colors.foregroundSecondary, size: 24),
                      title: 'Share Chaty',
                      subtitle: 'Invite friends and family to join Chaty',
                      onTap: () => ChatyShareService.shareApp(context),
                    ),

                    // 3. Privacy policy
                    _SettingsItemTile(
                      icon: Icon(Icons.shield_outlined, color: colors.foregroundSecondary, size: 24),
                      title: 'Privacy policy',
                      subtitle: 'Read our security and data privacy policy',
                      onTap: () => _showPrivacyPolicy(context),
                    ),

                    // 4. About
                    _SettingsItemTile(
                      icon: Icon(Icons.info_outline_rounded, color: colors.foregroundSecondary, size: 24),
                      title: 'About',
                      subtitle: 'App version, build and information',
                      onTap: () => _showAboutDialog(context),
                    ),

                    const SizedBox(height: 12),
                    Divider(height: 1, color: colors.divider, indent: 20, endIndent: 20),
                    const SizedBox(height: 12),

                    // 5. Log out
                    _SettingsItemTile(
                      icon: Icon(Icons.logout_rounded, color: colors.error, size: 24),
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
        );
      },
    );
  }

  Widget _accountBannerImage(String source, AppColors colors) {
    final trimmed = source.trim();
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
      return Image.network(
        trimmed,
        fit: BoxFit.cover,
        errorBuilder: (_, __, ___) => _accountBannerFallback(colors),
      );
    }
    return Image.file(
      File(trimmed.replaceFirst('file://', '')),
      fit: BoxFit.cover,
      errorBuilder: (_, __, ___) => _accountBannerFallback(colors),
    );
  }

  Widget _accountBannerFallback(AppColors colors) => ColoredBox(
    color: colors.surfaceElevated,
    child: Center(
      child: Icon(
        Icons.wallpaper_outlined,
        color: colors.foregroundSecondary,
        size: 30,
      ),
    ),
  );

  Future<void> _editAccountBanner(BuildContext context) async {
    final source = await showModalBottomSheet<ProfileMediaSource>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_camera_rounded),
              title: const Text('Take photo'),
              onTap: () => Navigator.of(sheetContext).pop(ProfileMediaSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_rounded),
              title: const Text('Choose from gallery'),
              onTap: () => Navigator.of(sheetContext).pop(ProfileMediaSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source == null || !context.mounted) return;
    try {
      final url = await ProfileMediaService().uploadBanner(
        source: source,
        context: context,
      );
      await dataStore.updateUser(dataStore.currentUser.copyWith(bannerUrl: url));
      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Banner updated.')));
    } catch (error) {
      if (error.toString().contains('cancelled') || !context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not update banner: $error')),
      );
    }
  }

  Widget _avatarFallback(
    UserProfile user, {
    double size = 60,
    required AppColors colors,
  }) {
    return Container(
      color: colors.surfaceElevated,
      width: size,
      height: size,
      child: Center(
        child: Text(
          user.avatarInitials.isNotEmpty ? user.avatarInitials : 'BM',
          style: TextStyle(
            color: colors.foreground,
            fontSize: size * 0.46,
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
                    style: TextStyle(
                      color: context.colors.foreground,
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                      letterSpacing: -0.1,
                    ),
                  ),
                  if (subtitle != null && subtitle!.isNotEmpty) ...[
                    const SizedBox(height: 3),
                    Text(
                      subtitle!,
                      style: TextStyle(
                        color: context.colors.foregroundSecondary,
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

