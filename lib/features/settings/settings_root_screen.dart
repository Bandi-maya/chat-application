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
import '../chats/linked_devices_qr_screen.dart';
import '../profile/profile_actions.dart';
import 'account/account_settings_screen.dart';
import 'appearance/app_icon_settings_screen.dart';
import 'appearance/universal_appearance_screen.dart';
import 'calls/call_settings_screen.dart';
import 'conversation/conversation_settings_page.dart';
import 'effects/navigation_effects_page.dart';
import 'home/header_settings_screen.dart';
import 'home/home_screen_settings_page.dart';
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
import '../../data/services/api_backend_service.dart';
import '../../data/services/backend_config.dart';
import '../subscription/plan_subscription_screen.dart';

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
      builder: (ctx) {
        final colors = ctx.colors;
        return AlertDialog(
          backgroundColor: colors.surfaceElevated,
          title: Row(
            children: [
              Icon(Icons.shield_outlined, color: colors.accent),
              const SizedBox(width: 8),
              Text('Privacy Policy', style: TextStyle(color: colors.foreground, fontSize: 18)),
            ],
          ),
          content: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'Your Privacy is Our Priority',
                  style: TextStyle(color: colors.foreground, fontWeight: FontWeight.bold, fontSize: 14),
                ),
                const SizedBox(height: 8),
                Text(
                  '• Local End-to-End Encryption:\nYour messages and calls are encrypted and stored safely on your device.\n\n'
                  '• No Remote Telemetry:\nChaty never tracks your browsing, locations, or personal data.\n\n'
                  '• Security & App Lock:\nLocal PIN, Pattern, and Biometric lock protect your messages completely offline.\n\n'
                  '• Full Data Ownership:\nYou can clear chat histories, media caches, and revoke device links at any time.',
                  style: TextStyle(color: colors.foregroundSecondary, fontSize: 13, height: 1.4),
                ),
              ],
            ),
          ),
          actions: [
            FilledButton(
              style: FilledButton.styleFrom(backgroundColor: colors.accent),
              onPressed: () => Navigator.pop(ctx),
              child: const Text('I Understand', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
            ),
          ],
        );
      },
    );
  }

  void _showAboutDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) {
        final colors = ctx.colors;
        return AlertDialog(
          backgroundColor: colors.surfaceElevated,
          title: Row(
            children: [
              Icon(Icons.info_outline_rounded, color: colors.accent),
              const SizedBox(width: 8),
              Text('About Chaty', style: TextStyle(color: colors.foreground, fontSize: 18)),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Chaty Messenger Pro', style: TextStyle(color: colors.foreground, fontWeight: FontWeight.bold)),
              const SizedBox(height: 4),
              Text('Version 2.4.0 (Build 2026.10)', style: TextStyle(color: colors.foregroundSecondary, fontSize: 12)),
              const SizedBox(height: 12),
              Text(
                'A modern, privacy-first messaging platform with advanced personalization, rich customization, and offline security.',
                style: TextStyle(color: colors.foregroundSecondary, fontSize: 13, height: 1.3),
              ),
            ],
          ),
          actions: [
            FilledButton(
              style: FilledButton.styleFrom(backgroundColor: colors.accent),
              onPressed: () => Navigator.pop(ctx),
              child: const Text('OK', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
            ),
          ],
        );
      },
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
                padding: const EdgeInsets.only(top: 12, bottom: 36),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // SECTION 1: ACCOUNT & PROFILE
                    _GbGroup(children: [
                    _SettingsItemTile(
                      icon: Icon(Icons.person_outline_rounded, color: colors.primary, size: 24),
                      title: 'Account',
                      subtitle: 'Profile details, display name, handle & account info',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => AccountSettingsScreen(
                            preferencesController: preferencesController,
                            dataStore: dataStore,
                          ),
                        ),
                      ),
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.workspace_premium_rounded, color: context.colors.accent, size: 24),
                      title: 'Subscription & Plans',
                      subtitle: 'Manage workspace billing, upgrade plan & usage limits',
                      onTap: () {
                        final backend = locator<ApiBackendService>();
                        final org = backend.currentOrg;
                        final orgId = org?['uid']?.toString() ??
                            org?['tenantId']?.toString() ??
                            ApiBackendConfig.tenantId;
                        final orgName = org?['name']?.toString() ??
                            ApiBackendConfig.tenantId;
                        Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => PlanSubscriptionScreen(
                              organizationId: orgId,
                              organizationName: orgName,
                              isNewRegistration: false,
                            ),
                          ),
                        );
                      },
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.devices_rounded, color: colors.foregroundSecondary, size: 24),
                      title: 'Linked Devices',
                      subtitle: 'Use Chaty on web and desktop, view active sessions',
                      onTap: () => _openLinkedDevices(context),
                    ),
                    ]),
                    _GbGroup(children: [
                    _SettingsItemTile(
                      icon: Icon(Icons.security_rounded, color: colors.info, size: 24),
                      title: 'Privacy Center',
                      subtitle: 'Last seen, online presence, anti-delete, read receipts & stealth',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => PrivacyCenterScreen(
                            preferencesController: preferencesController,
                          ),
                        ),
                      ),
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.lock_outline_rounded, color: colors.success, size: 24),
                      title: 'Security & App Lock',
                      subtitle: 'Biometric, PIN, Pattern protection & session security',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => SecurityCenterScreen(
                            preferencesController: preferencesController,
                          ),
                        ),
                      ),
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.color_lens_outlined, color: colors.accent, size: 24),
                      title: 'Theme & Color Engine',
                      subtitle: '13+ dark & light theme presets, token editor & palette',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => ThemeEditorScreen(
                            themeController: themeController,
                          ),
                        ),
                      ),
                    ),
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
                    _SettingsItemTile(
                      icon: Icon(Icons.dashboard_customize_rounded, color: colors.info, size: 24),
                      title: 'Global Template Studio',
                      subtitle: 'App-wide templates and independent component styles',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => const TemplatesSettingsScreen(),
                        ),
                      ),
                    ),
                    ]),
                    _GbGroup(children: [
                    _SettingsItemTile(
                      icon: Icon(Icons.home_outlined, color: colors.accent, size: 24),
                      title: 'Home Screen',
                      subtitle: 'Header, chat rows, floating button & status',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => HomeScreenSettingsPage(
                            preferencesController: preferencesController,
                          ),
                        ),
                      ),
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.chat_bubble_outline_rounded, color: colors.success, size: 24),
                      title: 'Conversation Customization',
                      subtitle: 'Bubbles, ticks, contact sidebar & message appearance',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => ConversationSettingsPage(
                            preferencesController: preferencesController,
                          ),
                        ),
                      ),
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.notifications_none_rounded, color: colors.warning, size: 24),
                      title: 'Notifications & Alerts',
                      subtitle: 'Heads-up popups, notification sounds, LED & vibration',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => NotificationSettingsPage(
                            preferencesController: preferencesController,
                            notificationService: notificationService,
                          ),
                        ),
                      ),
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.smart_toy_outlined, color: colors.info, size: 24),
                      title: 'Message Automation & Scheduler',
                      subtitle: 'Auto-reply rules, auto-responder bot & message scheduler',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => MessageManagementPage(
                            preferencesController: preferencesController,
                            dataStore: dataStore,
                          ),
                        ),
                      ),
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.storage_rounded, color: colors.accent, size: 24),
                      title: 'Storage & Media',
                      subtitle: 'Auto-download quality, media limits & cache retention',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => StorageAndMediaSettingsScreen(
                            preferencesController: preferencesController,
                          ),
                        ),
                      ),
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.touch_app_outlined, color: colors.warning, size: 24),
                      title: 'Navigation & Touch Effects',
                      subtitle: 'Interactive click particles (Stars/Hearts/Bubbles), Sound FX',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => NavigationEffectsPage(
                            preferencesController: preferencesController,
                          ),
                        ),
                      ),
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.call_outlined, color: colors.warning, size: 24),
                      title: 'Call Settings',
                      subtitle: 'Call privacy, incoming call controls & call appearance',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => CallSettingsScreen(
                            preferencesController: preferencesController,
                          ),
                        ),
                      ),
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.admin_panel_settings_outlined, color: colors.primary, size: 24),
                      title: 'System Permissions & Hardware',
                      subtitle: 'Camera, microphone, storage, battery & OS access',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => SystemPermissionsScreen(
                            preferencesController: preferencesController,
                            notificationService: notificationService,
                          ),
                        ),
                      ),
                    ),
                    ]),
                    _GbGroup(children: [
                    _SettingsItemTile(
                      icon: Icon(Icons.info_outline_rounded, color: colors.foregroundSecondary, size: 24),
                      title: 'About Chaty',
                      subtitle: 'App version, build information and licenses',
                      onTap: () => _showAboutDialog(context),
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.shield_outlined, color: colors.foregroundSecondary, size: 24),
                      title: 'Privacy Policy',
                      subtitle: 'Read our zero-telemetry security & privacy policy',
                      onTap: () => _showPrivacyPolicy(context),
                    ),
                    _SettingsItemTile(
                      icon: Icon(Icons.share_rounded, color: colors.foregroundSecondary, size: 24),
                      title: 'Share Chaty',
                      subtitle: 'Invite friends and family to join Chaty',
                      onTap: () => ChatyShareService.shareApp(context),
                    ),
                    ]),

                    const SizedBox(height: 16),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 20),
                      child: SizedBox(
                        width: double.infinity,
                        height: 48,
                        child: OutlinedButton.icon(
                          onPressed: () => _logout(context),
                          icon: Icon(Icons.logout_rounded, color: colors.error, size: 20),
                          label: Text(
                            'Log Out of Chaty',
                            style: TextStyle(
                              color: colors.error,
                              fontSize: 15,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          style: OutlinedButton.styleFrom(
                            side: BorderSide(color: colors.error.withValues(alpha: 0.8), width: 1.2),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(14),
                            ),
                          ),
                        ),
                      ),
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

  Widget _buildSettingsSectionHeader(String title, AppColors colors) {
    return Padding(
      padding: const EdgeInsets.only(left: 20, right: 20, top: 8, bottom: 6),
      child: Text(
        title,
        style: TextStyle(
          fontSize: 11.5,
          fontWeight: FontWeight.w800,
          color: colors.primary,
          letterSpacing: 0.9,
        ),
      ),
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
    final colors = context.colors;
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(color: colors.primary, width: 1.5),
              ),
              child: Center(child: icon),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      color: colors.foreground,
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  if (subtitle != null && subtitle!.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      subtitle!,
                      style: TextStyle(
                        color: colors.foregroundSecondary,
                        fontSize: 13,
                        height: 1.25,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(width: 8),
            Icon(Icons.chevron_right_rounded,
                color: colors.foregroundSecondary, size: 20),
          ],
        ),
      ),
    );
  }
}

/// Rounded group card with inset dividers between rows (GB-style grouping).
class _GbGroup extends StatelessWidget {
  final List<Widget> children;
  const _GbGroup({required this.children});

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final rows = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      rows.add(children[i]);
      if (i < children.length - 1) {
        rows.add(Divider(
          height: 1,
          thickness: 1,
          color: colors.divider,
          indent: 68,
          endIndent: 16,
        ));
      }
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 6, 12, 6),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: colors.surface,
          borderRadius: BorderRadius.circular(24),
          border: Border.all(color: colors.borderSubtle),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(24),
          child: Column(children: rows),
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
