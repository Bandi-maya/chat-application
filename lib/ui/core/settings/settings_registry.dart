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
          subcategory: 'Message Protection',
          controlType: SettingControlType.toggle,
          icon: Icons.delete_forever_rounded,
          searchKeywords: ['anti delete', 'deleted messages', 'anti revoke', 'keep deleted'],
          canonicalRoute: '/settings/privacy',
          defaultValue: true,
        ),
        SettingDefinition<bool>(
          id: 'privacy_anti_delete_status',
          title: 'Anti-Delete Updates',
          description: 'Keep deleted status updates accessible until normal expiry',
          category: SettingsCategory.privacy,
          subcategory: 'Status Privacy',
          controlType: SettingControlType.toggle,
          icon: Icons.history_toggle_off_rounded,
          searchKeywords: ['anti delete status', 'deleted story', 'keep status'],
          canonicalRoute: '/settings/privacy',
          defaultValue: true,
        ),
        SettingDefinition<bool>(
          id: 'privacy_read_receipts',
          title: 'Read Receipts',
          description: 'Publish blue ticks when reading received messages',
          category: SettingsCategory.privacy,
          subcategory: 'Receipts',
          controlType: SettingControlType.toggle,
          icon: Icons.done_all_rounded,
          searchKeywords: ['read receipts', 'blue ticks', 'seen', 'delivered'],
          canonicalRoute: '/settings/privacy',
          defaultValue: true,
        ),
        SettingDefinition<bool>(
          id: 'privacy_blue_ticks_after_reply',
          title: 'Blue Ticks After Reply',
          description: 'Send read receipts only after you reply to a message',
          category: SettingsCategory.privacy,
          subcategory: 'Receipts',
          controlType: SettingControlType.toggle,
          icon: Icons.mark_chat_read_rounded,
          searchKeywords: ['blue ticks after reply', 'delayed ticks', 'reply receipt'],
          canonicalRoute: '/settings/privacy',
          defaultValue: false,
        ),
        SettingDefinition<bool>(
          id: 'privacy_typing_indicators',
          title: 'Typing Indicators',
          description: 'Show when you are typing or recording audio',
          category: SettingsCategory.privacy,
          subcategory: 'Presence & Online',
          controlType: SettingControlType.toggle,
          icon: Icons.edit_note_rounded,
          searchKeywords: ['typing indicator', 'hide typing', 'recording audio'],
          canonicalRoute: '/settings/privacy',
          defaultValue: true,
        ),
      ],
    ),

    // -------------------------------------------------------------------------
    // 3. Security & App Lock
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
      id: 'cluster_chat_bubbles',
      title: 'Bubbles, Ticks & Wallpaper',
      description: 'Chat visual contours, tick styles and per-chat backgrounds',
      category: SettingsCategory.chats,
      settings: [
        SettingDefinition<String>(
          id: 'chats_bubble_style',
          title: 'Bubble Style',
          description: 'Shape and contour treatment for incoming and outgoing messages',
          category: SettingsCategory.chats,
          subcategory: 'Visuals',
          controlType: SettingControlType.previewSelector,
          icon: Icons.chat_bubble_outline_rounded,
          searchKeywords: ['bubble style', 'chat bubbles', 'rounded bubble', '3d bubble'],
          canonicalRoute: '/settings/conversation',
          defaultValue: 'Stock',
        ),
        SettingDefinition<String>(
          id: 'chats_tick_style',
          title: 'Message Tick Style',
          description: 'Icons used to render sent, delivered, and read status',
          category: SettingsCategory.chats,
          subcategory: 'Visuals',
          controlType: SettingControlType.previewSelector,
          icon: Icons.done_all_rounded,
          searchKeywords: ['ticks', 'checkmarks', 'delivery ticks', 'blue tick icon'],
          canonicalRoute: '/settings/conversation',
          defaultValue: 'RC iOS 11',
        ),
        SettingDefinition<String>(
          id: 'chats_wallpaper_type',
          title: 'Chat Wallpaper',
          description: 'Background styling behind active conversation threads',
          category: SettingsCategory.chats,
          subcategory: 'Visuals',
          controlType: SettingControlType.singleChoice,
          icon: Icons.wallpaper_rounded,
          searchKeywords: ['wallpaper', 'chat background', 'gradient background', 'solid color'],
          canonicalRoute: '/settings/conversation',
          defaultValue: 'Pattern',
        ),
      ],
    ),
    SettingsCluster(
      id: 'cluster_chat_interactions',
      title: 'Chat Interactions & Gestures',
      description: 'Swipe actions, double-tap emoji, animated stickers and sidebar',
      category: SettingsCategory.chats,
      settings: [
        SettingDefinition<bool>(
          id: 'chats_swipe_to_reply',
          title: 'Swipe to Reply',
          description: 'Swipe a message left or right to quote reply instantly',
          category: SettingsCategory.chats,
          subcategory: 'Interactions',
          controlType: SettingControlType.toggle,
          icon: Icons.reply_rounded,
          searchKeywords: ['swipe to reply', 'quote message', 'quick reply'],
          canonicalRoute: '/settings/conversation',
          defaultValue: true,
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
    // 5. Appearance & Templates
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_appearance_templates',
      title: 'UI Templates & Layout Systems',
      description: 'Original layout systems and component-level overrides (Navigation, Composer, Conversation, Profile)',
      category: SettingsCategory.appearance,
      settings: [
        SettingDefinition<String>(
          id: 'appearance_ui_template',
          title: 'Templates',
          description: 'Apply complete UI archetypes or customize by individual component',
          category: SettingsCategory.appearance,
          subcategory: 'Layout System',
          controlType: SettingControlType.previewSelector,
          icon: Icons.dashboard_customize_rounded,
          searchKeywords: ['template', 'layout', 'bottom bar style', 'composer style', 'visual social', 'camera first', 'stream', 'power chat', 'community'],
          canonicalRoute: '/settings/templates',
        ),
      ],
    ),
    SettingsCluster(
      id: 'cluster_appearance_theme',
      title: 'Theme & Design Studio',
      description: 'Presets, custom color generator, JSON import/export',
      category: SettingsCategory.appearance,
      settings: [
        SettingDefinition<String>(
          id: 'appearance_theme_preset',
          title: 'Theme Presets',
          description: 'Curated color themes including Warm Neutral, True Dark AMOLED and Cobalt',
          category: SettingsCategory.appearance,
          subcategory: 'Color & Theme',
          controlType: SettingControlType.previewSelector,
          icon: Icons.palette_outlined,
          searchKeywords: ['theme', 'dark mode', 'light mode', 'amoled', 'emerald', 'midnight', 'warm neutral'],
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
          searchKeywords: ['home style', 'chat layout', 'cards layout', 'compact home'],
          canonicalRoute: '/settings/home',
          defaultValue: 'Chaty Default',
        ),
        SettingDefinition<bool>(
          id: 'home_enable_stories_strip',
          title: 'Updates / Stories Strip',
          description: 'Display top stories rail on the main chats screen',
          category: SettingsCategory.homeAndNavigation,
          subcategory: 'Stories',
          controlType: SettingControlType.toggle,
          icon: Icons.auto_stories_outlined,
          searchKeywords: ['stories strip', 'status strip', 'stories on home', 'top stories'],
          canonicalRoute: '/settings/home',
          defaultValue: false,
        ),
        SettingDefinition<bool>(
          id: 'home_separate_chats_groups',
          title: 'Separate Chats and Groups',
          description: 'Provide dedicated tabs for direct messages and group conversations',
          category: SettingsCategory.homeAndNavigation,
          subcategory: 'Tabs',
          controlType: SettingControlType.toggle,
          icon: Icons.groups_outlined,
          searchKeywords: ['separate groups', 'group tabs', 'direct chats', 'chat tabs'],
          canonicalRoute: '/settings/home',
          defaultValue: false,
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
    // 8. Calls
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_calls',
      title: 'Call Privacy & Presentation',
      description: 'Incoming call permissions, dynamic island, and audio optimization',
      category: SettingsCategory.calls,
      settings: [
        SettingDefinition<String>(
          id: 'calls_who_can_call_me',
          title: 'Who Can Call Me',
          description: 'Audience permitted to initiate voice and video calls with you',
          category: SettingsCategory.calls,
          subcategory: 'Privacy',
          controlType: SettingControlType.singleChoice,
          icon: Icons.ring_volume_rounded,
          searchKeywords: ['who can call me', 'call permissions', 'block calls', 'call privacy'],
          canonicalRoute: '/settings/calls',
          defaultValue: 'Everyone',
        ),
      ],
    ),

    // -------------------------------------------------------------------------
    // 9. Storage & Data
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_storage_media',
      title: 'Storage & Media Quality',
      description: 'High-res image sending, upload limits & disk cache cleaner',
      category: SettingsCategory.storageAndMedia,
      settings: [
        SettingDefinition<bool>(
          id: 'storage_high_res_images',
          title: 'High Resolution Media',
          description: 'Send images without aggressive downscaling',
          category: SettingsCategory.storageAndMedia,
          subcategory: 'Quality',
          controlType: SettingControlType.toggle,
          icon: Icons.hd_rounded,
          searchKeywords: ['high res images', 'hd photos', 'upload quality', 'image resolution'],
          canonicalRoute: '/settings/storage',
          defaultValue: true,
        ),
      ],
    ),

    // -------------------------------------------------------------------------
    // 10. Linked Devices
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
    // 11. Interactive Effects
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_interactive_effects',
      title: 'Interactive Effects',
      description: 'Touch particle animations & falling emoji effects',
      category: SettingsCategory.effects,
      settings: [
        SettingDefinition<bool>(
          id: 'effects_touch_particles',
          title: 'Touch Particle Animation',
          description: 'Sparks and particle bursts when tapping the screen',
          category: SettingsCategory.effects,
          subcategory: 'Touch Effects',
          controlType: SettingControlType.toggle,
          icon: Icons.auto_fix_high_rounded,
          searchKeywords: ['touch particles', 'click effects', 'falling emoji', 'interactive effects'],
          canonicalRoute: '/settings/effects',
          defaultValue: false,
        ),
      ],
    ),

    // -------------------------------------------------------------------------
    // 12. System Permissions
    // -------------------------------------------------------------------------
    SettingsCluster(
      id: 'cluster_system_permissions',
      title: 'System Permissions',
      description: 'Camera, microphone, media, contacts and push rights',
      category: SettingsCategory.permissions,
      settings: [
        SettingDefinition<void>(
          id: 'permissions_manage',
          title: 'Permission Center',
          description: 'Review and request OS hardware and storage permissions',
          category: SettingsCategory.permissions,
          subcategory: 'Permissions',
          controlType: SettingControlType.navigationLink,
          icon: Icons.admin_panel_settings_outlined,
          searchKeywords: ['permissions', 'camera access', 'microphone access', 'media permission'],
          canonicalRoute: '/settings/permissions',
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
