import 'package:flutter/material.dart';

/// Supported top-level canonical settings categories.
enum SettingsCategory {
  account(
    id: 'account',
    title: 'Account & Profile',
    subtitle: 'Profile, security credentials & account details',
    icon: Icons.person_outline_rounded,
  ),
  privacy(
    id: 'privacy',
    title: 'Privacy',
    subtitle: 'Last seen, online presence, read receipts & status privacy',
    icon: Icons.visibility_off_outlined,
  ),
  security(
    id: 'security',
    title: 'Security & Lock',
    subtitle: 'App lock, biometric, PIN, hidden chats & permissions',
    icon: Icons.lock_outline_rounded,
  ),
  chats(
    id: 'chats',
    title: 'Chats',
    subtitle: 'Bubbles, ticks, wallpaper, automation & conversation tools',
    icon: Icons.chat_bubble_outline_rounded,
  ),
  appearance(
    id: 'appearance',
    title: 'Appearance',
    subtitle: 'Theme presets, custom palette, typography & motion effects',
    icon: Icons.palette_outlined,
  ),
  homeAndNavigation(
    id: 'home_navigation',
    title: 'Home & Navigation',
    subtitle: 'Layout mode, bottom bar design, header shortcuts & tabs',
    icon: Icons.home_outlined,
  ),
  notifications(
    id: 'notifications',
    title: 'Notifications',
    subtitle: 'Toast alerts, sound, preview, vibration & presence alerts',
    icon: Icons.notifications_outlined,
  ),
  calls(
    id: 'calls',
    title: 'Calls',
    subtitle: 'Call permissions, who can call you & call appearance',
    icon: Icons.call_outlined,
  ),
  storageAndMedia(
    id: 'storage_media',
    title: 'Storage & Data',
    subtitle: 'Media quality, upload limits & device cache retention',
    icon: Icons.storage_rounded,
  ),
  devices(
    id: 'devices',
    title: 'Linked Devices',
    subtitle: 'Active web/desktop sessions, QR code scanner & sync',
    icon: Icons.devices_rounded,
  ),
  effects(
    id: 'effects',
    title: 'Interactive Effects',
    subtitle: 'Touch particle animations, navigation effects & falling items',
    icon: Icons.animation_rounded,
  ),
  permissions(
    id: 'permissions',
    title: 'System Permissions',
    subtitle: 'Hardware, notifications, storage & OS rights',
    icon: Icons.admin_panel_settings_outlined,
  ),
  about(
    id: 'about',
    title: 'About',
    subtitle: 'Version info, open source licenses & system status',
    icon: Icons.info_outline_rounded,
  );

  final String id;
  final String title;
  final String subtitle;
  final IconData icon;

  const SettingsCategory({
    required this.id,
    required this.title,
    required this.subtitle,
    required this.icon,
  });
}

/// Control types for rendering or describing individual settings.
enum SettingControlType {
  toggle,
  singleChoice,
  previewSelector,
  slider,
  action,
  navigationLink,
}

/// Metadata definition for a single setting.
class SettingDefinition<T> {
  final String id;
  final String title;
  final String description;
  final SettingsCategory category;
  final String subcategory;
  final SettingControlType controlType;
  final IconData? icon;
  final List<String> searchKeywords;
  final String canonicalRoute;
  final T? defaultValue;

  const SettingDefinition({
    required this.id,
    required this.title,
    required this.description,
    required this.category,
    required this.subcategory,
    required this.controlType,
    this.icon,
    this.searchKeywords = const <String>[],
    required this.canonicalRoute,
    this.defaultValue,
  });
}

/// Logical cluster grouping 2-6 related settings together.
class SettingsCluster {
  final String id;
  final String title;
  final String description;
  final SettingsCategory category;
  final List<SettingDefinition> settings;

  const SettingsCluster({
    required this.id,
    required this.title,
    required this.description,
    required this.category,
    required this.settings,
  });
}
