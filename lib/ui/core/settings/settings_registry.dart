import 'package:flutter/material.dart';
import 'settings_models.dart';

/// Central Metadata Registry for all Chaty Settings.
///
/// Ensures ONE SETTING = ONE CANONICAL LOCATION invariant across the entire app.
class SettingsRegistry {
  const SettingsRegistry._();

  static const List<SettingsCluster> clusters = <SettingsCluster>[
    // -------------------------------------------------------------------------
    // 1. Account & Profile
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_account',
      title: 'Profile & Account',
      description: 'Display name, username, bio, and account credentials',
      category: SettingsCategory.account,
      settings: [
        SettingDefinition<String>(
          id: 'account_display_name',
          title: 'Display Name',
          description: 'Your visible full name shown across conversations',
          category: SettingsCategory.account,
          subcategory: 'Profile Details',
          controlType: SettingControlType.action,
          icon: Icons.badge_outlined,
          searchKeywords: ['name', 'display name', 'nickname', 'profile name'],
          canonicalRoute: '/settings/account',
        ),
        SettingDefinition<String>(
          id: 'account_username',
          title: 'Username',
          description: 'Unique @handle used to find and message you',
          category: SettingsCategory.account,
          subcategory: 'Profile Details',
          controlType: SettingControlType.action,
          icon: Icons.alternate_email_rounded,
          searchKeywords: ['username', 'handle', 'tag', 'id'],
          canonicalRoute: '/settings/account',
        ),
        SettingDefinition<String>(
          id: 'account_bio',
          title: 'About / Status Bio',
          description: 'Brief text bio displayed on your profile card',
          category: SettingsCategory.account,
          subcategory: 'Profile Details',
          controlType: SettingControlType.action,
          icon: Icons.info_outline_rounded,
          searchKeywords: ['bio', 'status message', 'about', 'tagline'],
          canonicalRoute: '/settings/account',
        ),
      ],
    ),

    // -------------------------------------------------------------------------
    // 2. Privacy
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_privacy_presence',
      title: 'Last Seen & Online Presence',
      description: 'Freeze last seen timestamp and audience visibility',
      category: SettingsCategory.privacy,
      settings: [
        SettingDefinition<bool>(
          id: 'privacy_freeze_last_seen',
          title: 'Freeze Last Seen',
          description: 'Prevents the server from updating your last seen timestamp',
          category: SettingsCategory.privacy,
          subcategory: 'Presence & Online',
          controlType: SettingControlType.toggle,
          icon: Icons.ac_unit_rounded,
          searchKeywords: ['freeze last seen', 'last seen', 'online timestamp', 'hide online'],
          canonicalRoute: '/settings/privacy',
          defaultValue: false,
        ),
        SettingDefinition<String>(
          id: 'privacy_who_can_see_last_seen',
          title: 'Who Can See Last Seen',
          description: 'Audience allowed to view your online presence',
          category: SettingsCategory.privacy,
          subcategory: 'Presence & Online',
          controlType: SettingControlType.singleChoice,
          icon: Icons.visibility_outlined,
          searchKeywords: ['who can see last seen', 'last seen audience', 'presence visibility'],
          canonicalRoute: '/settings/privacy',
          defaultValue: 'Everyone',
        ),
      ],
    ),
    SettingsCluster(
      id: 'cluster_privacy_chat_protection',
      title: 'Chat Privacy & Anti-Revoke',
      description: 'Message protection, read receipts and status privacy',
      category: SettingsCategory.privacy,
      settings: [
        SettingDefinition<bool>(
          id: 'privacy_anti_delete_messages',
          title: 'Anti-Delete Messages',
          description: 'Retain deleted messages sent by others in conversations',
          category: SettingsCategory.privacy,
          subcategory: 'Chat Privacy',
          controlType: SettingControlType.toggle,
          icon: Icons.delete_forever_outlined,
          searchKeywords: ['anti delete', 'prevent delete', 'revoke', 'deleted messages'],
          canonicalRoute: '/settings/privacy',
          defaultValue: false,
        ),
        SettingDefinition<bool>(
          id: 'privacy_anti_view_once',
          title: 'Anti-View Once Media',
          description: 'View disappearing and view-once media unlimited times',
          category: SettingsCategory.privacy,
          subcategory: 'Chat Privacy',
          controlType: SettingControlType.toggle,
          icon: Icons.remove_red_eye_outlined,
          searchKeywords: ['view once', 'anti view once', 'ephemeral media', 'disappearing'],
          canonicalRoute: '/settings/privacy',
          defaultValue: false,
        ),
        SettingDefinition<bool>(
          id: 'privacy_disable_forward_tag',
          title: 'Disable Forwarded Tag',
          description: 'Remove the forwarded label when sharing messages',
          category: SettingsCategory.privacy,
          subcategory: 'Chat Privacy',
          controlType: SettingControlType.toggle,
          icon: Icons.forward_rounded,
          searchKeywords: ['forward', 'forwarded tag', 'remove forwarded label'],
          canonicalRoute: '/settings/privacy',
          defaultValue: false,
        ),
        SettingDefinition<bool>(
          id: 'privacy_show_blue_ticks_after_reply',
          title: 'Show Blue Ticks After Reply',
          description: 'Only mark incoming messages as read once you reply',
          category: SettingsCategory.privacy,
          subcategory: 'Receipts',
          controlType: SettingControlType.toggle,
          icon: Icons.done_all_rounded,
          searchKeywords: ['blue ticks', 'read receipts', 'blue on reply', 'ticks after reply'],
          canonicalRoute: '/settings/privacy',
          defaultValue: false,
        ),
      ],
    ),
    SettingsCluster(
      id: 'cluster_privacy_calls',
      title: 'Calling Privacy',
      description: 'Control who is permitted to call you',
      category: SettingsCategory.privacy,
      settings: [
        SettingDefinition<String>(
          id: 'privacy_who_can_call_me',
          title: 'Who Can Call Me',
          description: 'Restrict incoming voice and video calls to specific contacts',
          category: SettingsCategory.privacy,
          subcategory: 'Calls',
          controlType: SettingControlType.singleChoice,
          icon: Icons.phone_callback_rounded,
          searchKeywords: ['who can call me', 'call privacy', 'block calls', 'call permissions'],
          canonicalRoute: '/settings/privacy',
          defaultValue: 'Everyone',
        ),
      ],
    ),

    // -------------------------------------------------------------------------
    // 3. Security & Lock
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_security_lock',
      title: 'App Lock & Biometrics',
      description: 'Hardware biometric, PIN, Pattern & Password protection',
      category: SettingsCategory.security,
      settings: [
        SettingDefinition<bool>(
          id: 'security_app_lock_enabled',
          title: 'App Lock',
          description: 'Require authentication when opening Chaty',
          category: SettingsCategory.security,
          subcategory: 'Authentication',
          controlType: SettingControlType.toggle,
          icon: Icons.lock_outline_rounded,
          searchKeywords: ['app lock', 'passcode', 'pin lock', 'lock chaty', 'security lock'],
          canonicalRoute: '/settings/security',
          defaultValue: false,
        ),
        SettingDefinition<String>(
          id: 'security_lock_method',
          title: 'Lock Method',
          description: 'Choose Biometric, PIN, Pattern, Password, or Device Credential',
          category: SettingsCategory.security,
          subcategory: 'Authentication',
          controlType: SettingControlType.singleChoice,
          icon: Icons.fingerprint_rounded,
          searchKeywords: ['fingerprint', 'biometric', 'face unlock', 'pin', 'pattern', 'password'],
          canonicalRoute: '/settings/security',
          defaultValue: 'PIN',
        ),
        SettingDefinition<String>(
          id: 'security_autolock_timeout',
          title: 'Auto-Lock Timeout',
          description: 'Time elapsed before Chaty requires unlock again',
          category: SettingsCategory.security,
          subcategory: 'Authentication',
          controlType: SettingControlType.singleChoice,
          icon: Icons.timer_outlined,
          searchKeywords: ['timeout', 'auto lock', 'lock delay', 'lock immediately'],
          canonicalRoute: '/settings/security',
          defaultValue: '1m',
        ),
      ],
    ),
    SettingsCluster(
      id: 'cluster_security_chats',
      title: 'Hidden & Locked Chats',
      description: 'Manage secluded chat conversations with stealth lock',
      category: SettingsCategory.security,
      settings: [
        SettingDefinition<bool>(
          id: 'security_hide_locked_chats',
          title: 'Hide Locked Chats from List',
          description: 'Remove locked conversations completely from the main chat list',
          category: SettingsCategory.security,
          subcategory: 'Chat Protection',
          controlType: SettingControlType.toggle,
          icon: Icons.visibility_off_rounded,
          searchKeywords: ['hide locked chats', 'hidden chats', 'stealth mode', 'locked chats'],
          canonicalRoute: '/settings/security',
          defaultValue: false,
        ),
        SettingDefinition<bool>(
          id: 'security_protect_screenshots',
          title: 'Protect From Screenshots',
          description: 'Block screen capture and recents preview across the app',
          category: SettingsCategory.security,
          subcategory: 'Protection',
          controlType: SettingControlType.toggle,
          icon: Icons.screenshot_outlined,
          searchKeywords: ['screenshot', 'screen capture', 'screen protection', 'block screenshot'],
          canonicalRoute: '/settings/security',
          defaultValue: false,
        ),
      ],
    ),

    // -------------------------------------------------------------------------
    // 4. Chats
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_chat_bubbles_ticks',
      title: 'Bubble & Tick Styles',
      description: 'Geometry contours and vector delivery glyphs',
      category: SettingsCategory.chats,
      settings: [
        SettingDefinition<String>(
          id: 'chats_bubble_style',
          title: 'Bubble Style Geometry',
          description: 'Choose from 48 discrete bubble contours and radii',
          category: SettingsCategory.chats,
          subcategory: 'Presentation',
          controlType: SettingControlType.previewSelector,
          icon: Icons.chat_bubble_outline_rounded,
          searchKeywords: ['bubble style', 'bubble shape', 'chat bubble', 'bubble geometry'],
          canonicalRoute: '/settings/conversation',
          defaultValue: 'Stock',
        ),
        SettingDefinition<String>(
          id: 'chats_tick_style',
          title: 'Delivery Tick Style',
          description: 'Choose from 16 custom vector delivery ticks',
          category: SettingsCategory.chats,
          subcategory: 'Presentation',
          controlType: SettingControlType.previewSelector,
          icon: Icons.done_all_rounded,
          searchKeywords: ['tick style', 'check mark', 'delivery ticks', 'read ticks'],
          canonicalRoute: '/settings/conversation',
          defaultValue: 'RC iOS 11',
        ),
      ],
    ),
    SettingsCluster(
      id: 'cluster_chat_conversation_tools',
      title: 'Conversation Tools & Media',
      description: 'Wallpaper, quick sidebar, voice speed and double-tap reactions',
      category: SettingsCategory.chats,
      settings: [
        SettingDefinition<String>(
          id: 'chats_wallpaper_type',
          title: 'Chat Wallpaper',
          description: 'Pattern, solid, gradient or custom imported background',
          category: SettingsCategory.chats,
          subcategory: 'Wallpaper',
          controlType: SettingControlType.previewSelector,
          icon: Icons.wallpaper_rounded,
          searchKeywords: ['wallpaper', 'background', 'chat wallpaper', 'chat background'],
          canonicalRoute: '/settings/conversation',
          defaultValue: 'Pattern',
        ),
        SettingDefinition<String>(
          id: 'chats_double_tap_emoji',
          title: 'Double-Tap Reaction Emoji',
          description: 'Quick emoji reaction placed on double-tapping a message',
          category: SettingsCategory.chats,
          subcategory: 'Interactions',
          controlType: SettingControlType.singleChoice,
          icon: Icons.favorite_outline_rounded,
          searchKeywords: ['double tap', 'quick reaction', 'reaction emoji', 'heart reaction'],
          canonicalRoute: '/settings/conversation',
          defaultValue: '❤️',
        ),
        SettingDefinition<bool>(
          id: 'chats_enable_sidebar',
          title: 'Quick Contact Sidebar',
          description: 'Swipeable vertical avatar bar inside chat screens',
          category: SettingsCategory.chats,
          subcategory: 'Interactions',
          controlType: SettingControlType.toggle,
          icon: Icons.view_sidebar_outlined,
          searchKeywords: ['sidebar', 'quick contact', 'contact bar', 'chat sidebar'],
          canonicalRoute: '/settings/conversation',
          defaultValue: false,
        ),
        SettingDefinition<bool>(
          id: 'chats_enable_animated_emojis',
          title: 'Animated Emojis',
          description: 'Smooth SVG animations for standalone emoji messages',
          category: SettingsCategory.chats,
          subcategory: 'Interactions',
          controlType: SettingControlType.toggle,
          icon: Icons.emoji_emotions_outlined,
          searchKeywords: ['animated emojis', 'emoji animations', 'svg emojis', 'spring emoji'],
          canonicalRoute: '/settings/conversation',
          defaultValue: true,
        ),
      ],
    ),
    SettingsCluster(
      id: 'cluster_chat_automation',
      title: 'Message Automation',
      description: 'Auto-reply rules, quick responses and scheduled messages',
      category: SettingsCategory.chats,
      settings: [
        SettingDefinition<bool>(
          id: 'chats_auto_reply_enabled',
          title: 'Auto-Reply Rules',
          description: 'Automated responses based on keywords and schedules',
          category: SettingsCategory.chats,
          subcategory: 'Automation',
          controlType: SettingControlType.toggle,
          icon: Icons.schedule_send_rounded,
          searchKeywords: ['auto reply', 'auto responder', 'quick reply', 'scheduled message'],
          canonicalRoute: '/settings/message_management',
          defaultValue: false,
        ),
      ],
    ),

    // -------------------------------------------------------------------------
    // 5. Appearance
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_appearance_theme',
      title: 'Theme & Design Studio',
      description: 'Presets, custom color generator, JSON import/export',
      category: SettingsCategory.appearance,
      settings: [
        SettingDefinition<String>(
          id: 'appearance_theme_preset',
          title: 'Theme Presets',
          description: 'Curated color themes including True Dark AMOLED and Emerald',
          category: SettingsCategory.appearance,
          subcategory: 'Color & Theme',
          controlType: SettingControlType.previewSelector,
          icon: Icons.palette_outlined,
          searchKeywords: ['theme', 'dark mode', 'light mode', 'amoled', 'emerald', 'midnight'],
          canonicalRoute: '/settings/themes',
        ),
        SettingDefinition<String>(
          id: 'appearance_app_icon',
          title: 'App Launcher Icon',
          description: 'Choose from 6 bundled 3D brand icons or custom image',
          category: SettingsCategory.appearance,
          subcategory: 'Branding',
          controlType: SettingControlType.previewSelector,
          icon: Icons.apps_rounded,
          searchKeywords: ['app icon', 'launcher icon', 'icon style', 'custom icon'],
          canonicalRoute: '/settings/app_icon',
        ),
      ],
    ),
    SettingsCluster(
      id: 'cluster_appearance_typography_motion',
      title: 'Typography & Motion',
      description: 'Scale, density, and animated transition styles',
      category: SettingsCategory.appearance,
      settings: [
        SettingDefinition<String>(
          id: 'appearance_typography_style',
          title: 'Typography Style',
          description: 'Text scale and letter spacing density across the app',
          category: SettingsCategory.appearance,
          subcategory: 'Typography',
          controlType: SettingControlType.singleChoice,
          icon: Icons.text_fields_rounded,
          searchKeywords: ['font size', 'typography', 'text scale', 'font style'],
          canonicalRoute: '/settings/universal_appearance',
          defaultValue: 'Standard',
        ),
        SettingDefinition<String>(
          id: 'appearance_entry_motion',
          title: 'Screen Entry Animation',
          description: 'Transition curve applied when opening screens',
          category: SettingsCategory.appearance,
          subcategory: 'Motion',
          controlType: SettingControlType.singleChoice,
          icon: Icons.login_rounded,
          searchKeywords: ['entry animation', 'screen transition', 'open animation', 'motion'],
          canonicalRoute: '/settings/universal_appearance',
          defaultValue: 'Fluid Slide Up',
        ),
        SettingDefinition<String>(
          id: 'appearance_exit_motion',
          title: 'Screen Exit Animation',
          description: 'Transition curve applied when closing screens',
          category: SettingsCategory.appearance,
          subcategory: 'Motion',
          controlType: SettingControlType.singleChoice,
          icon: Icons.logout_rounded,
          searchKeywords: ['exit animation', 'close animation', 'pop animation'],
          canonicalRoute: '/settings/universal_appearance',
          defaultValue: 'Fluid Slide Down',
        ),
      ],
    ),

    // -------------------------------------------------------------------------
    // 6. Home & Navigation
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_home_layout',
      title: 'Home Screen Layout',
      description: 'Styles, stories strip, tab separation & ghost mode',
      category: SettingsCategory.homeAndNavigation,
      settings: [
        SettingDefinition<String>(
          id: 'home_style',
          title: 'Home Screen Style',
          description: 'Overall layout hierarchy for chats and stories',
          category: SettingsCategory.homeAndNavigation,
          subcategory: 'Layout',
          controlType: SettingControlType.singleChoice,
          icon: Icons.dashboard_outlined,
          searchKeywords: ['home style', 'home layout', 'compact layout', 'stories first'],
          canonicalRoute: '/settings/home',
          defaultValue: 'Chaty Default',
        ),
        SettingDefinition<bool>(
          id: 'home_stories_strip',
          title: 'Show Stories Strip',
          description: 'Display recent status updates at top of chat list',
          category: SettingsCategory.homeAndNavigation,
          subcategory: 'Layout',
          controlType: SettingControlType.toggle,
          icon: Icons.history_edu_rounded,
          searchKeywords: ['stories', 'status strip', 'stories bar', 'status updates'],
          canonicalRoute: '/settings/home',
          defaultValue: false,
        ),
        SettingDefinition<bool>(
          id: 'home_separate_chats_groups',
          title: 'Separate Chats & Groups',
          description: 'Split direct 1:1 messages and group chats into tabs',
          category: SettingsCategory.homeAndNavigation,
          subcategory: 'Tabs',
          controlType: SettingControlType.toggle,
          icon: Icons.groups_outlined,
          searchKeywords: ['separate groups', 'group tabs', 'split chats', 'groups'],
          canonicalRoute: '/settings/home',
          defaultValue: false,
        ),
        SettingDefinition<bool>(
          id: 'home_ghost_mode',
          title: 'Ghost Mode',
          description: 'Incognito presence banner with simulated offline behavior',
          category: SettingsCategory.homeAndNavigation,
          subcategory: 'Presence',
          controlType: SettingControlType.toggle,
          icon: Icons.airplanemode_active_rounded,
          searchKeywords: ['ghost mode', 'airplane mode', 'offline simulator', 'incognito'],
          canonicalRoute: '/settings/home',
          defaultValue: false,
        ),
      ],
    ),
    SettingsCluster(
      id: 'cluster_navigation_architecture',
      title: 'Navigation Layout & Bottom Bar',
      description: 'Choose navigation structure and 12 bottom bar designs',
      category: SettingsCategory.homeAndNavigation,
      settings: [
        SettingDefinition<String>(
          id: 'navigation_mode',
          title: 'Navigation Layout Architecture',
          description: 'Bottom Nav, Top WhatsApp Bar, Floating Rail, 3D Drawer or Side Menu',
          category: SettingsCategory.homeAndNavigation,
          subcategory: 'Architecture',
          controlType: SettingControlType.singleChoice,
          icon: Icons.view_sidebar_outlined,
          searchKeywords: ['navigation layout', 'nav mode', 'top whatsapp bar', 'side drawer', 'rail'],
          canonicalRoute: '/settings/home',
          defaultValue: 'bottomNav',
        ),
        SettingDefinition<String>(
          id: 'navigation_bottom_bar_style',
          title: 'Bottom Navigation Bar Design',
          description: '12 animated navigation dock layouts and indicators',
          category: SettingsCategory.homeAndNavigation,
          subcategory: 'Bar Design',
          controlType: SettingControlType.singleChoice,
          icon: Icons.dock_rounded,
          searchKeywords: ['bottom bar style', 'dock design', 'nav indicator', 'pill style'],
          canonicalRoute: '/settings/universal_appearance',
          defaultValue: 'Floating Pill',
        ),
      ],
    ),

    // -------------------------------------------------------------------------
    // 7. Notifications
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_notification_alerts',
      title: 'Toast & Event Alerts',
      description: 'Contact activity, presence toasts, duration and sounds',
      category: SettingsCategory.notifications,
      settings: [
        SettingDefinition<bool>(
          id: 'notifications_global_enabled',
          title: 'Enable Notifications',
          description: 'Receive push and in-app message alerts',
          category: SettingsCategory.notifications,
          subcategory: 'Global',
          controlType: SettingControlType.toggle,
          icon: Icons.notifications_active_outlined,
          searchKeywords: ['notifications', 'push alerts', 'enable alerts'],
          canonicalRoute: '/settings/notifications',
          defaultValue: true,
        ),
        SettingDefinition<bool>(
          id: 'notifications_show_preview',
          title: 'Message Preview',
          description: 'Show message body text in push notification banners',
          category: SettingsCategory.notifications,
          subcategory: 'Content',
          controlType: SettingControlType.toggle,
          icon: Icons.preview_outlined,
          searchKeywords: ['preview', 'message preview', 'hide preview', 'notification content'],
          canonicalRoute: '/settings/notifications',
          defaultValue: true,
        ),
        SettingDefinition<bool>(
          id: 'notifications_contact_online',
          title: 'Contact Online Alert',
          description: 'Toast alert when selected contacts become active',
          category: SettingsCategory.notifications,
          subcategory: 'Presence Alerts',
          controlType: SettingControlType.toggle,
          icon: Icons.online_prediction_rounded,
          searchKeywords: ['contact online alert', 'online toast', 'presence notification'],
          canonicalRoute: '/settings/notifications',
          defaultValue: false,
        ),
      ],
    ),

    // -------------------------------------------------------------------------
    // 8. Linked Devices
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_devices',
      title: 'Linked Devices & Sessions',
      description: 'Manage active sessions, scan QR code, and desktop link',
      category: SettingsCategory.devices,
      settings: [
        SettingDefinition<void>(
          id: 'devices_qr_scan',
          title: 'Link a New Device',
          description: 'Scan QR code from Chaty Web or Desktop client',
          category: SettingsCategory.devices,
          subcategory: 'Linking',
          controlType: SettingControlType.action,
          icon: Icons.qr_code_scanner_rounded,
          searchKeywords: ['link device', 'qr code', 'scan qr', 'desktop', 'web session'],
          canonicalRoute: '/settings/devices',
        ),
      ],
    ),

    // -------------------------------------------------------------------------
    // 9. Advanced & Permissions
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_system_permissions',
      title: 'System Permissions',
      description: 'Camera, microphone, media, contacts and push rights',
      category: SettingsCategory.security,
      settings: [
        SettingDefinition<void>(
          id: 'permissions_manage',
          title: 'Permission Center',
          description: 'Review and request OS hardware and storage permissions',
          category: SettingsCategory.security,
          subcategory: 'Permissions',
          controlType: SettingControlType.navigationLink,
          icon: Icons.admin_panel_settings_outlined,
          searchKeywords: ['permissions', 'camera access', 'microphone access', 'media permission'],
          canonicalRoute: '/settings/permissions',
        ),
      ],
    ),
    SettingsCluster(
      id: 'cluster_advanced_features',
      title: 'Advanced Features',
      description: 'Extended privacy toggles, media limit overrides and catalog',
      category: SettingsCategory.advanced,
      settings: [
        SettingDefinition<void>(
          id: 'advanced_feature_center',
          title: 'Advanced Feature Catalog',
          description: 'Deep behavioral options and granular UI overrides',
          category: SettingsCategory.advanced,
          subcategory: 'Catalog',
          controlType: SettingControlType.navigationLink,
          icon: Icons.tune_rounded,
          searchKeywords: ['advanced features', 'gb features', 'tweaks', 'catalog', 'developer'],
          canonicalRoute: '/settings/advanced',
        ),
      ],
    ),
  ];

  /// Returns all registered setting definitions flattened across all clusters.
  static List<SettingDefinition> get allSettings {
    final List<SettingDefinition> results = <SettingDefinition>[];
    for (final cluster in clusters) {
      results.addAll(cluster.settings);
    }
    return List<SettingDefinition>.unmodifiable(results);
  }

  /// Returns clusters belonging to a specific category.
  static List<SettingsCluster> clustersForCategory(SettingsCategory category) {
    return clusters.where((c) => c.category == category).toList(growable: false);
  }

  /// Validates the ONE SETTING = ONE CANONICAL LOCATION invariant.
  /// Throws an [AssertionError] if duplicate IDs are detected.
  static bool validateInvariants() {
    final Set<String> ids = <String>{};
    for (final setting in allSettings) {
      if (ids.contains(setting.id)) {
        throw AssertionError('Duplicate setting ID found: ');
      }
      ids.add(setting.id);
    }
    return true;
  }
}
