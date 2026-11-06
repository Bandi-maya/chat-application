package com.example.chat.ui.settings.registry

/** Port of lib/ui/core/settings/settings_models.dart + settings_registry.dart.
 *  iconName holds the Material icon identifier used by Dart (Icons.<name>); resolve via a icon-name map in UI. */
enum class SettingsCategory(val id: String, val title: String, val subtitle: String, val iconName: String) {
    account("account", "Account & Profile", "Profile, security credentials & account details", "person_outline_rounded"),
    privacy("privacy", "Privacy", "Last seen, online presence, read receipts & status privacy", "visibility_off_outlined"),
    security("security", "Security & Lock", "App lock, biometric, PIN, hidden chats & permissions", "lock_outline_rounded"),
    chats("chats", "Chats", "Bubbles, ticks, wallpaper, automation & conversation tools", "chat_bubble_outline_rounded"),
    appearance("appearance", "Appearance", "Theme presets, custom palette, typography & motion effects", "palette_outlined"),
    homeAndNavigation("home_navigation", "Home & Navigation", "Layout mode, bottom bar design, header shortcuts & tabs", "home_outlined"),
    notifications("notifications", "Notifications", "Toast alerts, sound, preview, vibration & presence alerts", "notifications_outlined"),
    calls("calls", "Calls", "Call permissions, who can call you & call appearance", "call_outlined"),
    storageAndMedia("storage_media", "Storage & Data", "Media quality, upload limits & device cache retention", "storage_rounded"),
    devices("devices", "Linked Devices", "Active web/desktop sessions, QR code scanner & sync", "devices_rounded"),
    effects("effects", "Interactive Effects", "Touch particle animations, navigation effects & falling items", "animation_rounded"),
    permissions("permissions", "System Permissions", "Hardware, notifications, storage & OS rights", "admin_panel_settings_outlined"),
    about("about", "About", "Version info, open source licenses & system status", "info_outline_rounded");
}

enum class SettingControlType { toggle, singleChoice, previewSelector, slider, action, navigationLink }

data class SettingDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: SettingsCategory,
    val subcategory: String,
    val controlType: SettingControlType,
    val iconName: String?,
    val searchKeywords: List<String> = emptyList(),
    val canonicalRoute: String,
    val defaultValue: Any? = null,
)

data class SettingsCluster(
    val id: String,
    val title: String,
    val description: String,
    val category: SettingsCategory,
    val settings: List<SettingDefinition>,
)

object SettingsRegistry {
    val clusters: List<SettingsCluster> = listOf(
        SettingsCluster(
            id = "cluster_account", title = "Profile & Account", description = "Display name, username, bio, and account credentials",
            category = SettingsCategory.account,
            settings = listOf(
            SettingDefinition(
                id = "account_display_name", title = "Display Name", description = "Your visible full name shown across conversations",
                category = SettingsCategory.account, subcategory = "Profile Details",
                controlType = SettingControlType.action,
                iconName = "badge_outlined",
                searchKeywords = listOf("name", "display name", "nickname", "profile name"),
                canonicalRoute = "/settings/account", defaultValue = null,
            ),
            SettingDefinition(
                id = "account_username", title = "Username", description = "Unique @handle used to find and message you",
                category = SettingsCategory.account, subcategory = "Profile Details",
                controlType = SettingControlType.action,
                iconName = "alternate_email_rounded",
                searchKeywords = listOf("username", "handle", "tag", "id"),
                canonicalRoute = "/settings/account", defaultValue = null,
            ),
            SettingDefinition(
                id = "account_bio", title = "About / Status Bio", description = "Brief text bio displayed on your profile card",
                category = SettingsCategory.account, subcategory = "Profile Details",
                controlType = SettingControlType.action,
                iconName = "info_outline_rounded",
                searchKeywords = listOf("bio", "status message", "about", "tagline"),
                canonicalRoute = "/settings/account", defaultValue = null,
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_privacy_presence", title = "Last Seen & Online Presence", description = "Freeze last seen timestamp and audience visibility",
            category = SettingsCategory.privacy,
            settings = listOf(
            SettingDefinition(
                id = "privacy_freeze_last_seen", title = "Freeze Last Seen", description = "Prevents the server from updating your last seen timestamp",
                category = SettingsCategory.privacy, subcategory = "Presence & Online",
                controlType = SettingControlType.toggle,
                iconName = "ac_unit_rounded",
                searchKeywords = listOf("freeze last seen", "last seen", "online timestamp", "hide online"),
                canonicalRoute = "/settings/privacy", defaultValue = false,
            ),
            SettingDefinition(
                id = "privacy_who_can_see_last_seen", title = "Who Can See Last Seen", description = "Audience allowed to view your online presence",
                category = SettingsCategory.privacy, subcategory = "Presence & Online",
                controlType = SettingControlType.singleChoice,
                iconName = "visibility_outlined",
                searchKeywords = listOf("who can see last seen", "last seen audience", "presence visibility"),
                canonicalRoute = "/settings/privacy", defaultValue = "Everyone",
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_privacy_chat_protection", title = "Chat Privacy & Anti-Revoke", description = "Message protection, read receipts and status privacy",
            category = SettingsCategory.privacy,
            settings = listOf(
            SettingDefinition(
                id = "privacy_anti_delete_messages", title = "Anti-Delete Messages", description = "Retain deleted messages sent by others in conversations",
                category = SettingsCategory.privacy, subcategory = "Message Protection",
                controlType = SettingControlType.toggle,
                iconName = "delete_forever_rounded",
                searchKeywords = listOf("anti delete", "deleted messages", "anti revoke", "keep deleted"),
                canonicalRoute = "/settings/privacy", defaultValue = true,
            ),
            SettingDefinition(
                id = "privacy_anti_delete_status", title = "Anti-Delete Updates", description = "Keep deleted status updates accessible until normal expiry",
                category = SettingsCategory.privacy, subcategory = "Status Privacy",
                controlType = SettingControlType.toggle,
                iconName = "history_toggle_off_rounded",
                searchKeywords = listOf("anti delete status", "deleted story", "keep status"),
                canonicalRoute = "/settings/privacy", defaultValue = true,
            ),
            SettingDefinition(
                id = "privacy_read_receipts", title = "Read Receipts", description = "Publish blue ticks when reading received messages",
                category = SettingsCategory.privacy, subcategory = "Receipts",
                controlType = SettingControlType.toggle,
                iconName = "done_all_rounded",
                searchKeywords = listOf("read receipts", "blue ticks", "seen", "delivered"),
                canonicalRoute = "/settings/privacy", defaultValue = true,
            ),
            SettingDefinition(
                id = "privacy_blue_ticks_after_reply", title = "Blue Ticks After Reply", description = "Send read receipts only after you reply to a message",
                category = SettingsCategory.privacy, subcategory = "Receipts",
                controlType = SettingControlType.toggle,
                iconName = "mark_chat_read_rounded",
                searchKeywords = listOf("blue ticks after reply", "delayed ticks", "reply receipt"),
                canonicalRoute = "/settings/privacy", defaultValue = false,
            ),
            SettingDefinition(
                id = "privacy_typing_indicators", title = "Typing Indicators", description = "Show when you are typing or recording audio",
                category = SettingsCategory.privacy, subcategory = "Presence & Online",
                controlType = SettingControlType.toggle,
                iconName = "edit_note_rounded",
                searchKeywords = listOf("typing indicator", "hide typing", "recording audio"),
                canonicalRoute = "/settings/privacy", defaultValue = true,
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_security_lock", title = "App Lock & Biometrics", description = "Hardware biometric, PIN, Pattern & Password protection",
            category = SettingsCategory.security,
            settings = listOf(
            SettingDefinition(
                id = "security_app_lock_enabled", title = "App Lock", description = "Require authentication when opening Chaty",
                category = SettingsCategory.security, subcategory = "Authentication",
                controlType = SettingControlType.toggle,
                iconName = "lock_outline_rounded",
                searchKeywords = listOf("app lock", "passcode", "pin lock", "lock chaty", "security lock"),
                canonicalRoute = "/settings/security", defaultValue = false,
            ),
            SettingDefinition(
                id = "security_lock_method", title = "Lock Method", description = "Choose Biometric, PIN, Pattern, Password, or Device Credential",
                category = SettingsCategory.security, subcategory = "Authentication",
                controlType = SettingControlType.singleChoice,
                iconName = "fingerprint_rounded",
                searchKeywords = listOf("fingerprint", "biometric", "face unlock", "pin", "pattern", "password"),
                canonicalRoute = "/settings/security", defaultValue = "PIN",
            ),
            SettingDefinition(
                id = "security_autolock_timeout", title = "Auto-Lock Timeout", description = "Time elapsed before Chaty requires unlock again",
                category = SettingsCategory.security, subcategory = "Authentication",
                controlType = SettingControlType.singleChoice,
                iconName = "timer_outlined",
                searchKeywords = listOf("timeout", "auto lock", "lock delay", "lock immediately"),
                canonicalRoute = "/settings/security", defaultValue = "1m",
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_security_chats", title = "Hidden & Locked Chats", description = "Manage secluded chat conversations with stealth lock",
            category = SettingsCategory.security,
            settings = listOf(
            SettingDefinition(
                id = "security_hide_locked_chats", title = "Hide Locked Chats from List", description = "Remove locked conversations completely from the main chat list",
                category = SettingsCategory.security, subcategory = "Chat Protection",
                controlType = SettingControlType.toggle,
                iconName = "visibility_off_rounded",
                searchKeywords = listOf("hide locked chats", "hidden chats", "stealth mode", "locked chats"),
                canonicalRoute = "/settings/security", defaultValue = false,
            ),
            SettingDefinition(
                id = "security_protect_screenshots", title = "Protect From Screenshots", description = "Block screen capture and recents preview across the app",
                category = SettingsCategory.security, subcategory = "Protection",
                controlType = SettingControlType.toggle,
                iconName = "screenshot_outlined",
                searchKeywords = listOf("screenshot", "screen capture", "screen protection", "block screenshot"),
                canonicalRoute = "/settings/security", defaultValue = false,
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_chat_bubbles", title = "Bubbles, Ticks & Wallpaper", description = "Chat visual contours, tick styles and per-chat backgrounds",
            category = SettingsCategory.chats,
            settings = listOf(
            SettingDefinition(
                id = "chats_bubble_style", title = "Bubble Style", description = "Shape and contour treatment for incoming and outgoing messages",
                category = SettingsCategory.chats, subcategory = "Visuals",
                controlType = SettingControlType.previewSelector,
                iconName = "chat_bubble_outline_rounded",
                searchKeywords = listOf("bubble style", "chat bubbles", "rounded bubble", "3d bubble"),
                canonicalRoute = "/settings/conversation", defaultValue = "Stock",
            ),
            SettingDefinition(
                id = "chats_tick_style", title = "Message Tick Style", description = "Icons used to render sent, delivered, and read status",
                category = SettingsCategory.chats, subcategory = "Visuals",
                controlType = SettingControlType.previewSelector,
                iconName = "done_all_rounded",
                searchKeywords = listOf("ticks", "checkmarks", "delivery ticks", "blue tick icon"),
                canonicalRoute = "/settings/conversation", defaultValue = "RC iOS 11",
            ),
            SettingDefinition(
                id = "chats_wallpaper_type", title = "Chat Wallpaper", description = "Background styling behind active conversation threads",
                category = SettingsCategory.chats, subcategory = "Visuals",
                controlType = SettingControlType.singleChoice,
                iconName = "wallpaper_rounded",
                searchKeywords = listOf("wallpaper", "chat background", "gradient background", "solid color"),
                canonicalRoute = "/settings/conversation", defaultValue = "Pattern",
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_chat_interactions", title = "Chat Interactions & Gestures", description = "Swipe actions, double-tap emoji, animated stickers and sidebar",
            category = SettingsCategory.chats,
            settings = listOf(
            SettingDefinition(
                id = "chats_swipe_to_reply", title = "Swipe to Reply", description = "Swipe a message left or right to quote reply instantly",
                category = SettingsCategory.chats, subcategory = "Interactions",
                controlType = SettingControlType.toggle,
                iconName = "reply_rounded",
                searchKeywords = listOf("swipe to reply", "quote message", "quick reply"),
                canonicalRoute = "/settings/conversation", defaultValue = true,
            ),
            SettingDefinition(
                id = "chats_double_tap_emoji", title = "Double-Tap Reaction Emoji", description = "Quick emoji reaction placed on double-tapping a message",
                category = SettingsCategory.chats, subcategory = "Interactions",
                controlType = SettingControlType.singleChoice,
                iconName = "favorite_outline_rounded",
                searchKeywords = listOf("double tap", "quick reaction", "reaction emoji", "heart reaction"),
                canonicalRoute = "/settings/conversation", defaultValue = "❤️",
            ),
            SettingDefinition(
                id = "chats_enable_sidebar", title = "Quick Contact Sidebar", description = "Swipeable vertical avatar bar inside chat screens",
                category = SettingsCategory.chats, subcategory = "Interactions",
                controlType = SettingControlType.toggle,
                iconName = "view_sidebar_outlined",
                searchKeywords = listOf("sidebar", "quick contact", "contact bar", "chat sidebar"),
                canonicalRoute = "/settings/conversation", defaultValue = false,
            ),
            SettingDefinition(
                id = "chats_enable_animated_emojis", title = "Animated Emojis", description = "Smooth SVG animations for standalone emoji messages",
                category = SettingsCategory.chats, subcategory = "Interactions",
                controlType = SettingControlType.toggle,
                iconName = "emoji_emotions_outlined",
                searchKeywords = listOf("animated emojis", "emoji animations", "svg emojis", "spring emoji"),
                canonicalRoute = "/settings/conversation", defaultValue = true,
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_chat_automation", title = "Message Automation", description = "Auto-reply rules, quick responses and scheduled messages",
            category = SettingsCategory.chats,
            settings = listOf(
            SettingDefinition(
                id = "chats_auto_reply_enabled", title = "Auto-Reply Rules", description = "Automated responses based on keywords and schedules",
                category = SettingsCategory.chats, subcategory = "Automation",
                controlType = SettingControlType.toggle,
                iconName = "schedule_send_rounded",
                searchKeywords = listOf("auto reply", "auto responder", "quick reply", "scheduled message"),
                canonicalRoute = "/settings/message_management", defaultValue = false,
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_appearance_templates", title = "UI Templates & Layout Systems", description = "Original layout systems and component-level overrides (Navigation, Composer, Conversation, Profile)",
            category = SettingsCategory.appearance,
            settings = listOf(
            SettingDefinition(
                id = "appearance_ui_template", title = "Templates", description = "Apply complete UI archetypes or customize by individual component",
                category = SettingsCategory.appearance, subcategory = "Layout System",
                controlType = SettingControlType.previewSelector,
                iconName = "dashboard_customize_rounded",
                searchKeywords = listOf("template", "layout", "bottom bar style", "composer style", "visual social", "camera first", "stream", "power chat", "community"),
                canonicalRoute = "/settings/templates", defaultValue = null,
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_appearance_theme", title = "Theme & Design Studio", description = "Presets, custom color generator, JSON import/export",
            category = SettingsCategory.appearance,
            settings = listOf(
            SettingDefinition(
                id = "appearance_theme_preset", title = "Theme Presets", description = "Curated color themes including Warm Neutral, True Dark AMOLED and Cobalt",
                category = SettingsCategory.appearance, subcategory = "Color & Theme",
                controlType = SettingControlType.previewSelector,
                iconName = "palette_outlined",
                searchKeywords = listOf("theme", "dark mode", "light mode", "amoled", "emerald", "midnight", "warm neutral"),
                canonicalRoute = "/settings/themes", defaultValue = null,
            ),
            SettingDefinition(
                id = "appearance_app_icon", title = "App Launcher Icon", description = "Choose from 6 bundled 3D brand icons or custom image",
                category = SettingsCategory.appearance, subcategory = "Branding",
                controlType = SettingControlType.previewSelector,
                iconName = "apps_rounded",
                searchKeywords = listOf("app icon", "launcher icon", "icon style", "custom icon"),
                canonicalRoute = "/settings/app_icon", defaultValue = null,
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_appearance_typography_motion", title = "Typography & Motion", description = "Scale, density, and animated transition styles",
            category = SettingsCategory.appearance,
            settings = listOf(
            SettingDefinition(
                id = "appearance_typography_style", title = "Typography Style", description = "Text scale and letter spacing density across the app",
                category = SettingsCategory.appearance, subcategory = "Typography",
                controlType = SettingControlType.singleChoice,
                iconName = "text_fields_rounded",
                searchKeywords = listOf("font size", "typography", "text scale", "font style"),
                canonicalRoute = "/settings/universal_appearance", defaultValue = "Standard",
            ),
            SettingDefinition(
                id = "appearance_entry_motion", title = "Screen Entry Animation", description = "Transition curve applied when opening screens",
                category = SettingsCategory.appearance, subcategory = "Motion",
                controlType = SettingControlType.singleChoice,
                iconName = "login_rounded",
                searchKeywords = listOf("entry animation", "screen transition", "open animation", "motion"),
                canonicalRoute = "/settings/universal_appearance", defaultValue = "Fluid Slide Up",
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_home_layout", title = "Home Screen Layout", description = "Styles, stories strip, tab separation & ghost mode",
            category = SettingsCategory.homeAndNavigation,
            settings = listOf(
            SettingDefinition(
                id = "home_style", title = "Home Screen Style", description = "Overall layout hierarchy for chats and stories",
                category = SettingsCategory.homeAndNavigation, subcategory = "Layout",
                controlType = SettingControlType.singleChoice,
                iconName = "dashboard_outlined",
                searchKeywords = listOf("home style", "chat layout", "cards layout", "compact home"),
                canonicalRoute = "/settings/home", defaultValue = "Chaty Default",
            ),
            SettingDefinition(
                id = "home_enable_stories_strip", title = "Updates / Stories Strip", description = "Display top stories rail on the main chats screen",
                category = SettingsCategory.homeAndNavigation, subcategory = "Stories",
                controlType = SettingControlType.toggle,
                iconName = "auto_stories_outlined",
                searchKeywords = listOf("stories strip", "status strip", "stories on home", "top stories"),
                canonicalRoute = "/settings/home", defaultValue = false,
            ),
            SettingDefinition(
                id = "home_separate_chats_groups", title = "Separate Chats and Groups", description = "Provide dedicated tabs for direct messages and group conversations",
                category = SettingsCategory.homeAndNavigation, subcategory = "Tabs",
                controlType = SettingControlType.toggle,
                iconName = "groups_outlined",
                searchKeywords = listOf("separate groups", "group tabs", "direct chats", "chat tabs"),
                canonicalRoute = "/settings/home", defaultValue = false,
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_notification_alerts", title = "Toast & Event Alerts", description = "Contact activity, presence toasts, duration and sounds",
            category = SettingsCategory.notifications,
            settings = listOf(
            SettingDefinition(
                id = "notifications_global_enabled", title = "Enable Notifications", description = "Receive push and in-app message alerts",
                category = SettingsCategory.notifications, subcategory = "Global",
                controlType = SettingControlType.toggle,
                iconName = "notifications_active_outlined",
                searchKeywords = listOf("notifications", "push alerts", "enable alerts"),
                canonicalRoute = "/settings/notifications", defaultValue = true,
            ),
            SettingDefinition(
                id = "notifications_show_preview", title = "Message Preview", description = "Show message body text in push notification banners",
                category = SettingsCategory.notifications, subcategory = "Content",
                controlType = SettingControlType.toggle,
                iconName = "preview_outlined",
                searchKeywords = listOf("preview", "message preview", "hide preview", "notification content"),
                canonicalRoute = "/settings/notifications", defaultValue = true,
            ),
            SettingDefinition(
                id = "notifications_contact_online", title = "Contact Online Alert", description = "Toast alert when selected contacts become active",
                category = SettingsCategory.notifications, subcategory = "Presence Alerts",
                controlType = SettingControlType.toggle,
                iconName = "online_prediction_rounded",
                searchKeywords = listOf("contact online alert", "online toast", "presence notification"),
                canonicalRoute = "/settings/notifications", defaultValue = false,
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_calls", title = "Call Privacy & Presentation", description = "Incoming call permissions, dynamic island, and audio optimization",
            category = SettingsCategory.calls,
            settings = listOf(
            SettingDefinition(
                id = "calls_who_can_call_me", title = "Who Can Call Me", description = "Audience permitted to initiate voice and video calls with you",
                category = SettingsCategory.calls, subcategory = "Privacy",
                controlType = SettingControlType.singleChoice,
                iconName = "ring_volume_rounded",
                searchKeywords = listOf("who can call me", "call permissions", "block calls", "call privacy"),
                canonicalRoute = "/settings/calls", defaultValue = "Everyone",
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_storage_media", title = "Storage & Media Quality", description = "High-res image sending, upload limits & disk cache cleaner",
            category = SettingsCategory.storageAndMedia,
            settings = listOf(
            SettingDefinition(
                id = "storage_high_res_images", title = "High Resolution Media", description = "Send images without aggressive downscaling",
                category = SettingsCategory.storageAndMedia, subcategory = "Quality",
                controlType = SettingControlType.toggle,
                iconName = "hd_rounded",
                searchKeywords = listOf("high res images", "hd photos", "upload quality", "image resolution"),
                canonicalRoute = "/settings/storage", defaultValue = true,
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_devices", title = "Linked Devices & Sessions", description = "Manage active sessions, scan QR code, and desktop link",
            category = SettingsCategory.devices,
            settings = listOf(
            SettingDefinition(
                id = "devices_qr_scan", title = "Link a New Device", description = "Scan QR code from Chaty Web or Desktop client",
                category = SettingsCategory.devices, subcategory = "Linking",
                controlType = SettingControlType.action,
                iconName = "qr_code_scanner_rounded",
                searchKeywords = listOf("link device", "qr code", "scan qr", "desktop", "web session"),
                canonicalRoute = "/settings/devices", defaultValue = null,
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_interactive_effects", title = "Interactive Effects", description = "Touch particle animations & falling emoji effects",
            category = SettingsCategory.effects,
            settings = listOf(
            SettingDefinition(
                id = "effects_touch_particles", title = "Touch Particle Animation", description = "Sparks and particle bursts when tapping the screen",
                category = SettingsCategory.effects, subcategory = "Touch Effects",
                controlType = SettingControlType.toggle,
                iconName = "auto_fix_high_rounded",
                searchKeywords = listOf("touch particles", "click effects", "falling emoji", "interactive effects"),
                canonicalRoute = "/settings/effects", defaultValue = false,
            ),
            ),
        ),
        SettingsCluster(
            id = "cluster_system_permissions", title = "System Permissions", description = "Camera, microphone, media, contacts and push rights",
            category = SettingsCategory.permissions,
            settings = listOf(
            SettingDefinition(
                id = "permissions_manage", title = "Permission Center", description = "Review and request OS hardware and storage permissions",
                category = SettingsCategory.permissions, subcategory = "Permissions",
                controlType = SettingControlType.navigationLink,
                iconName = "admin_panel_settings_outlined",
                searchKeywords = listOf("permissions", "camera access", "microphone access", "media permission"),
                canonicalRoute = "/settings/permissions", defaultValue = null,
            ),
            ),
        ),
    )

    val allSettings: List<SettingDefinition> get() = clusters.flatMap { it.settings }

    fun clustersForCategory(category: SettingsCategory): List<SettingsCluster> =
        clusters.filter { it.category == category }

    /** ONE SETTING = ONE CANONICAL LOCATION. Throws on duplicate ids (source message omitted the id interpolation). */
    fun validateInvariants(): Boolean {
        val ids = HashSet<String>()
        for (s in allSettings) {
            if (!ids.add(s.id)) throw AssertionError("Duplicate setting ID found: ")
        }
        return true
    }
}
