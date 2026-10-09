import 'dart:io';
import 'package:flutter/material.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/notification_service.dart';
import '../../data/services/profile_media_service.dart';
import '../../ui/core/templates/template_controller.dart';
import '../../ui/core/templates/template_models.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/preferences_controller.dart';
// ChatySettingsSection is duplicated between the barrel-exported
// components/settings_components.dart and settings_primitives.dart; this
// screen is written against the primitives variant.
import '../../ui/core/design_system/design_system.dart'
    hide ChatySettingsSection;
import '../../ui/core/design_system/settings_primitives.dart';
import '../settings/notifications/notification_settings_page.dart';
import '../settings/privacy/privacy_center_screen.dart';
import '../settings/settings_root_screen.dart';
import 'profile_actions.dart';
import 'profile_edit_screen.dart';

/// Root "Profile" destination (bottom navigation). Settings now lives ONE
/// level deeper: Profile → Settings → existing Settings system. Everything
/// shown here is real account data from the backend-backed data store —
/// no mock profile information.
class ProfileScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;
  final ChatyDataStore dataStore;
  final ChatyNotificationService notificationService;

  const ProfileScreen({
    super.key,
    required this.preferencesController,
    required this.themeController,
    required this.dataStore,
    required this.notificationService,
  });

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([
        dataStore,
        preferencesController,
        locator<TemplateController>(),
      ]),
      builder: (context, _) {
        final user = dataStore.currentUser;
        final profileTemplate = locator<TemplateController>().profile;
        final nameOverride = preferencesController.home.myNameOverride;
        final displayName = nameOverride.isNotEmpty
            ? nameOverride
            : user.displayName;
        return ChatySettingsPage(
          title: 'Profile',
          subtitle: 'Account, status and app entry points',
          children: [
            _ProfileHeader(
              initials: user.avatarInitials,
              colorHex: user.avatarColorHex,
              avatarUrl: user.avatarUrl,
              bannerUrl: user.bannerUrl,
              displayName: displayName,
              username: user.username,
              about: user.about,
              onEdit: () => ProfileEditScreen.open(context, dataStore),
              onEditBanner: () => _editBanner(context),
            ),
            if (profileTemplate.showStatsGrid) ...[
              const SizedBox(height: 8),
              _ProfileStatsGrid(
                chats: dataStore.conversations.length,
                groups: dataStore.conversations
                    .where((conversation) => conversation.type.name == 'group')
                    .length,
                contacts: dataStore.contacts.length,
              ),
            ],
            const SizedBox(height: 8),
            ChatySettingsSection(
              title: 'Account',
              children: [
                ChatySettingsTile(
                  icon: Icons.alternate_email_rounded,
                  title: 'Username',
                  subtitle: '@${user.username}',
                ),
                if (user.about.isNotEmpty)
                  ChatySettingsTile(
                    icon: Icons.info_outline_rounded,
                    title: 'About',
                    subtitle: user.about,
                  ),
              ],
            ),
            ChatySettingsSection(
              title: 'App',
              children: [
                ChatySettingsTile(
                  icon: Icons.settings_outlined,
                  title: 'Settings',
                  subtitle: 'Account, privacy, appearance and app behavior',
                  trailing: const _Chevron(),
                  onTap: () => _pushSettings(context),
                ),
                ChatySettingsTile(
                  icon: Icons.visibility_off_rounded,
                  title: 'Privacy Center',
                  subtitle: 'Presence, receipts and anti-delete options',
                  trailing: const _Chevron(),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => PrivacyCenterScreen(
                        preferencesController: preferencesController,
                      ),
                    ),
                  ),
                ),
                ChatySettingsTile(
                  icon: Icons.notifications_none_rounded,
                  title: 'Notifications',
                  subtitle: 'Toasts, alerts and previews',
                  trailing: const _Chevron(),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => NotificationSettingsPage(
                        preferencesController: preferencesController,
                        notificationService: notificationService,
                      ),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            ChatySettingsSection(
              children: [
                ChatySettingsTile(
                  icon: Icons.logout_rounded,
                  iconColor: context.colors.error,
                  title: 'Log out',
                  subtitle: 'Sign out of Chaty on this device',
                  onTap: () => confirmChatyLogout(context),
                ),
              ],
            ),
          ],
        );
      },
    );
  }

  /// Camera/Gallery → upload → persist. Real storage upload; failures show
  /// the real error and nothing is changed.
  Future<void> _editBanner(BuildContext context) async {
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
              onTap: () =>
                  Navigator.pop(sheetContext, ProfileMediaSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_rounded),
              title: const Text('Choose from gallery'),
              onTap: () =>
                  Navigator.pop(sheetContext, ProfileMediaSource.gallery),
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
      await dataStore.updateUser(
        dataStore.currentUser.copyWith(bannerUrl: url),
      );
      if (context.mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(const SnackBar(content: Text('Banner updated.')));
      }
    } catch (error) {
      final message = error.toString();
      if (message.contains('cancelled') || !context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not update banner: $message')),
      );
    }
  }

  void _pushSettings(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => SettingsRootScreen(
          preferencesController: preferencesController,
          themeController: themeController,
          dataStore: dataStore,
          notificationService: notificationService,
        ),
      ),
    );
  }
}

/// Large-avatar profile header: avatar, name, @username, about and one
/// restrained Edit action. Colors come exclusively from the design system.
class _ProfileHeader extends StatelessWidget {
  final String initials;
  final String colorHex;
  final String? avatarUrl;
  final String? bannerUrl;
  final String displayName;
  final String username;
  final String about;
  final VoidCallback onEdit;
  final VoidCallback onEditBanner;

  const _ProfileHeader({
    required this.initials,
    required this.colorHex,
    required this.displayName,
    required this.username,
    required this.about,
    required this.onEdit,
    required this.onEditBanner,
    this.avatarUrl,
    this.bannerUrl,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final profileTemplate = locator<TemplateController>().profile;
    final bannerHeight = switch (profileTemplate.headerStyle) {
      ProfileHeaderStyle.compactHeader => 128.0,
      ProfileHeaderStyle.centeredIdentity => 160.0,
      ProfileHeaderStyle.bannerWithAvatar => 180.0,
    };
    final avatarSize = switch (profileTemplate.headerStyle) {
      ProfileHeaderStyle.compactHeader => 64.0,
      ProfileHeaderStyle.centeredIdentity => 96.0,
      ProfileHeaderStyle.bannerWithAvatar => 92.0,
    };
    if (profileTemplate.headerStyle == ProfileHeaderStyle.compactHeader) {
      return _buildCompactHeader(context, colors, avatarSize);
    }
    if (profileTemplate.headerStyle == ProfileHeaderStyle.centeredIdentity) {
      return _buildCenteredIdentity(context, colors, avatarSize, bannerHeight);
    }
    return Center(
      child: Column(
        children: [
          // Banner with an overlapping avatar (original Chaty hierarchy).
          Stack(
            clipBehavior: Clip.none,
            alignment: Alignment.bottomCenter,
            children: [
              Container(
                margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                height: bannerHeight,
                width: double.infinity,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(20),
                  color: colors.surfaceElevated,
                  border: Border.all(color: colors.borderSubtle),
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(19),
                  child: (bannerUrl ?? '').isNotEmpty
                      ? (bannerUrl!.startsWith('http://') ||
                                bannerUrl!.startsWith('https://')
                            ? Image.network(
                                bannerUrl!,
                                fit: BoxFit.cover,
                                errorBuilder: (_, __, ___) =>
                                    _BannerFallback(colors: colors),
                              )
                            : Image.file(
                                File(bannerUrl!.replaceFirst('file://', '')),
                                fit: BoxFit.cover,
                                errorBuilder: (_, __, ___) =>
                                    _BannerFallback(colors: colors),
                              ))
                      : _BannerFallback(colors: colors),
                ),
              ),
              Positioned(
                right: 28,
                top: 24,
                child: Semantics(
                  button: true,
                  label: 'Change profile banner',
                  child: InkWell(
                    onTap: onEditBanner,
                    borderRadius: BorderRadius.circular(999),
                    child: Container(
                      padding: const EdgeInsets.all(7),
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: colors.surface.withValues(alpha: 0.85),
                        border: Border.all(color: colors.borderSubtle),
                      ),
                      child: Icon(
                        Icons.wallpaper_rounded,
                        size: 17,
                        color: colors.foreground,
                      ),
                    ),
                  ),
                ),
              ),
              Positioned(
                bottom: -44,
                child: Container(
                  padding: const EdgeInsets.all(4),
                  decoration: BoxDecoration(
                    shape: profileTemplate.avatarShape == 'circle'
                        ? BoxShape.circle
                        : BoxShape.rectangle,
                    borderRadius: profileTemplate.avatarShape == 'circle'
                        ? null
                        : BorderRadius.circular(avatarSize * 0.25),
                    color: colors.background,
                  ),
                  child: ChatyNetworkAvatar(
                    initials: initials,
                    colorHex: colorHex,
                    url: avatarUrl,
                    size: avatarSize,
                    shape: profileTemplate.avatarShape,
                  ),
                ),
              ),
            ],
          ),
          SizedBox(
            height: profileTemplate.headerStyle == ProfileHeaderStyle.compactHeader
                ? 34
                : 52,
          ),
          const SizedBox(height: 14),
          Text(
            displayName,
            textAlign: TextAlign.center,
            style: TextStyle(
              color: colors.foreground,
              fontSize: profileTemplate.headerStyle == ProfileHeaderStyle.compactHeader
                  ? 20
                  : 24,
              fontWeight: FontWeight.w800,
              letterSpacing: -0.4,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            '@$username',
            style: TextStyle(
              color: colors.foregroundSecondary,
              fontSize: 13.5,
              fontWeight: FontWeight.w500,
            ),
          ),
          if (about.isNotEmpty) ...[
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: Text(
                about,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: colors.foregroundSecondary,
                  fontSize: 13,
                  height: 1.35,
                ),
              ),
            ),
          ],
          const SizedBox(height: 16),
          _EditPill(onTap: onEdit),
          const SizedBox(height: 20),
        ],
      ),
    );
  }

  Widget _buildCompactHeader(
    BuildContext context,
    AppColors colors,
    double avatarSize,
  ) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 18),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(3),
            decoration: BoxDecoration(
              color: colors.surface,
              borderRadius: profileTemplateAvatarRadius(
                avatarSize,
                locator<TemplateController>().profile.avatarShape,
              ),
              border: Border.all(color: colors.borderSubtle),
            ),
            child: ChatyNetworkAvatar(
              initials: initials,
              colorHex: colorHex,
              url: avatarUrl,
              size: avatarSize,
              shape: locator<TemplateController>().profile.avatarShape,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Text(
                          displayName,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: colors.foreground,
                            fontSize: 20,
                            fontWeight: FontWeight.w800,
                            height: 1.08,
                          ),
                        ),
                      ),
                      IconButton(
                        tooltip: 'Change profile banner',
                        visualDensity: VisualDensity.compact,
                        onPressed: onEditBanner,
                        icon: Icon(
                          Icons.wallpaper_outlined,
                          size: 20,
                          color: colors.foregroundSecondary,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 3),
                  Text(
                    '@$username',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: colors.foregroundSecondary,
                      fontSize: 12.5,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  if (about.isNotEmpty) ...[
                    const SizedBox(height: 7),
                    Text(
                      about,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: colors.foregroundSecondary,
                        fontSize: 12.5,
                        height: 1.35,
                      ),
                    ),
                  ],
                  const SizedBox(height: 10),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: _EditPill(onTap: onEdit),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCenteredIdentity(
    BuildContext context,
    AppColors colors,
    double avatarSize,
    double bannerHeight,
  ) {
    final profileTemplate = locator<TemplateController>().profile;
    return Center(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 18),
        child: Column(
          children: [
            Stack(
              children: [
                _bannerSurface(colors, bannerHeight * 0.60),
                Positioned(
                  top: 8,
                  right: 8,
                  child: IconButton(
                    tooltip: 'Change profile banner',
                    style: IconButton.styleFrom(
                      backgroundColor: colors.surface.withValues(alpha: 0.88),
                      foregroundColor: colors.foreground,
                    ),
                    onPressed: onEditBanner,
                    icon: const Icon(Icons.wallpaper_outlined),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Container(
              padding: const EdgeInsets.all(4),
              decoration: BoxDecoration(
                color: colors.background,
                borderRadius: profileTemplateAvatarRadius(
                  avatarSize + 8,
                  profileTemplate.avatarShape,
                ),
                border: Border.all(color: colors.borderSubtle),
              ),
              child: ChatyNetworkAvatar(
                initials: initials,
                colorHex: colorHex,
                url: avatarUrl,
                size: avatarSize,
                shape: profileTemplate.avatarShape,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              displayName,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: TextStyle(
                color: colors.foreground,
                fontSize: 24,
                fontWeight: FontWeight.w800,
                letterSpacing: -0.4,
              ),
            ),
            const SizedBox(height: 3),
            Text(
              '@$username',
              style: TextStyle(
                color: colors.foregroundSecondary,
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
            if (about.isNotEmpty) ...[
              const SizedBox(height: 8),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Text(
                  about,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: colors.foregroundSecondary,
                    fontSize: 13,
                    height: 1.4,
                  ),
                ),
              ),
            ],
            const SizedBox(height: 14),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _EditPill(onTap: onEdit),
                const SizedBox(width: 8),
                OutlinedButton.icon(
                  onPressed: onEditBanner,
                  icon: const Icon(Icons.wallpaper_outlined, size: 17),
                  label: const Text('Banner'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _bannerSurface(AppColors colors, double height) {
    final source = (bannerUrl ?? '').trim();
    Widget image;
    if (source.isEmpty) {
      image = _BannerFallback(colors: colors);
    } else if (source.startsWith('http://') || source.startsWith('https://')) {
      image = Image.network(
        source,
        fit: BoxFit.cover,
        errorBuilder: (_, __, ___) => _BannerFallback(colors: colors),
      );
    } else {
      image = Image.file(
        File(source.replaceFirst('file://', '')),
        fit: BoxFit.cover,
        errorBuilder: (_, __, ___) => _BannerFallback(colors: colors),
      );
    }
    return Container(
      height: height,
      width: double.infinity,
      decoration: BoxDecoration(
        color: colors.surfaceElevated,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: colors.borderSubtle),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(17),
        child: image,
      ),
    );
  }

  BorderRadius profileTemplateAvatarRadius(double size, String shape) {
    return switch (shape) {
      'squircle' => BorderRadius.circular(size * 0.32),
      'roundedSquare' => BorderRadius.circular(size * 0.22),
      _ => BorderRadius.circular(size / 2),
    };
  }
}

class _ProfileStatsGrid extends StatelessWidget {
  final int chats;
  final int groups;
  final int contacts;

  const _ProfileStatsGrid({
    required this.chats,
    required this.groups,
    required this.contacts,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final stats = <(String, int, IconData)>[
      ('Chats', chats, Icons.chat_bubble_outline_rounded),
      ('Groups', groups, Icons.groups_outlined),
      ('Contacts', contacts, Icons.people_outline_rounded),
    ];
    return Row(
      children: [
        for (final stat in stats)
          Expanded(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 4),
              padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 6),
              decoration: BoxDecoration(
                color: colors.surface,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: colors.borderSubtle),
              ),
              child: Column(
                children: [
                  Icon(stat.$3, size: 17, color: colors.primary),
                  const SizedBox(height: 5),
                  Text(
                    stat.$2.toString(),
                    style: TextStyle(
                      color: colors.foreground,
                      fontWeight: FontWeight.w800,
                      fontSize: 16,
                    ),
                  ),
                  Text(
                    stat.$1,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: colors.foregroundSecondary,
                      fontSize: 10.5,
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

/// Small bordered "Edit profile" pill — press-scales subtly (120ms) so the
/// header feels responsive without shouting.
class _EditPill extends StatefulWidget {
  final VoidCallback onTap;
  const _EditPill({required this.onTap});

  @override
  State<_EditPill> createState() => _EditPillState();
}

class _EditPillState extends State<_EditPill> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapCancel: () => setState(() => _pressed = false),
      onTapUp: (_) => setState(() => _pressed = false),
      onTap: widget.onTap,
      child: AnimatedScale(
        scale: _pressed ? 0.96 : 1.0,
        duration: const Duration(milliseconds: 120),
        curve: Curves.easeOutCubic,
        child: AnimatedOpacity(
          duration: const Duration(milliseconds: 120),
          opacity: _pressed ? 0.82 : 1.0,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
            decoration: BoxDecoration(
              color: colors.surfaceElevated,
              borderRadius: BorderRadius.circular(999),
              border: Border.all(color: colors.borderSubtle),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.edit_outlined, size: 15, color: colors.primary),
                const SizedBox(width: 7),
                Text(
                  'Edit profile',
                  style: TextStyle(
                    color: colors.foreground,
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Forward chevron for navigable rows (never used as a back button).
class _Chevron extends StatelessWidget {
  const _Chevron();

  @override
  Widget build(BuildContext context) {
    return Icon(
      Icons.chevron_right_rounded,
      size: 20,
      color: context.colors.foregroundTertiary,
    );
  }
}

/// Token-driven placeholder shown when no banner has been uploaded yet.
class _BannerFallback extends StatelessWidget {
  final AppColors colors;

  const _BannerFallback({required this.colors});

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            colors.primary.withValues(alpha: 0.18),
            colors.surfaceSecondary,
          ],
        ),
      ),
      child: Center(
        child: Icon(
          Icons.wallpaper_rounded,
          size: 30,
          color: colors.foregroundTertiary,
        ),
      ),
    );
  }
}
