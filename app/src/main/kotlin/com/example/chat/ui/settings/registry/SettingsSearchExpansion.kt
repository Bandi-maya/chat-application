package com.example.chat.ui.settings.registry

/**
 * Search-index expansion: the original registry indexes 57 entries while the
 * app ships far more working controls, so most settings were unreachable from
 * settings search. Every entry below points at an EXISTING, verified control
 * and reuses an already-mapped canonicalRoute (unknown routes only show a
 * notice, so this data-only file cannot break navigation). Icon resolution
 * falls back to the category icon when iconName is null.
 *
 * Ids use the "x_" prefix so parallel helper patches adding their own
 * registry entries cannot collide with these.
 */
internal val settingsSearchExpansionClusters: List<SettingsCluster> = listOf(
    SettingsCluster(
        id = "x_cluster_chats_reading", title = "Reading & Bubbles", description = "Text size, quick reactions and conversation reading controls",
        category = SettingsCategory.chats,
        settings = listOf(
        SettingDefinition(
            id = "x_chats_message_text_size", title = "Message Text Size", description = "Font size used for message text inside conversations",
            category = SettingsCategory.chats, subcategory = "Conversation",
            controlType = SettingControlType.slider, iconName = null,
            searchKeywords = listOf("font size", "text size", "message size", "bigger text", "smaller text"),
            canonicalRoute = "/settings/conversation", defaultValue = 16.0,
        ),
        SettingDefinition(
            id = "x_chats_make_text_selectable", title = "Selectable Message Text", description = "Long-press selection of message text inside bubbles",
            category = SettingsCategory.chats, subcategory = "Conversation",
            controlType = SettingControlType.toggle, iconName = null,
            searchKeywords = listOf("select text", "copy message", "selectable", "highlight text"),
            canonicalRoute = "/settings/conversation", defaultValue = true,
        ),
        SettingDefinition(
            id = "x_chats_wallpaper_per_contact", title = "Custom Wallpaper per Contact", description = "Set a different wallpaper for each conversation",
            category = SettingsCategory.chats, subcategory = "Wallpaper",
            controlType = SettingControlType.toggle, iconName = null,
            searchKeywords = listOf("wallpaper per chat", "per contact wallpaper", "chat background"),
            canonicalRoute = "/settings/conversation", defaultValue = false,
        ),
        SettingDefinition(
            id = "x_chats_profile_pic_wallpaper", title = "Profile Photo Wallpaper", description = "Use the contact's profile photo as the chat wallpaper",
            category = SettingsCategory.chats, subcategory = "Wallpaper",
            controlType = SettingControlType.toggle, iconName = null,
            searchKeywords = listOf("profile pic wallpaper", "avatar background", "photo wallpaper"),
            canonicalRoute = "/settings/conversation", defaultValue = false,
        ),
        SettingDefinition(
            id = "x_chats_voice_playback_speed", title = "Voice Note Playback Speed", description = "Default speed for playing voice messages",
            category = SettingsCategory.chats, subcategory = "Voice & Audio",
            controlType = SettingControlType.singleChoice, iconName = null,
            searchKeywords = listOf("playback speed", "voice speed", "1.5x", "2x", "audio speed"),
            canonicalRoute = "/settings/conversation", defaultValue = "1x",
        ),
        SettingDefinition(
            id = "x_chats_proximity_sensor", title = "Earpiece Proximity Playback", description = "Switch voice notes to the earpiece when you raise the phone",
            category = SettingsCategory.chats, subcategory = "Voice & Audio",
            controlType = SettingControlType.toggle, iconName = null,
            searchKeywords = listOf("proximity", "earpiece", "raise to listen", "speaker switch"),
            canonicalRoute = "/settings/conversation", defaultValue = true,
        ),
        SettingDefinition(
            id = "x_chats_confirm_sticker", title = "Confirm Before Sending Stickers", description = "Ask before a tapped sticker is sent",
            category = SettingsCategory.chats, subcategory = "Sending",
            controlType = SettingControlType.toggle, iconName = null,
            searchKeywords = listOf("sticker confirm", "accidental sticker", "confirm send"),
            canonicalRoute = "/settings/conversation", defaultValue = true,
        ),
        SettingDefinition(
            id = "x_chats_entry_style", title = "Conversation Entry Style", description = "Layout of the message box, mic and send buttons",
            category = SettingsCategory.chats, subcategory = "Composer",
            controlType = SettingControlType.singleChoice, iconName = null,
            searchKeywords = listOf("entry style", "message box", "composer layout", "input bar style"),
            canonicalRoute = "/settings/conversation", defaultValue = "Stock",
        ),
        ),
    ),
    SettingsCluster(
        id = "x_cluster_home_lists", title = "Home List & Header", description = "Chat list layout, rows and home header controls",
        category = SettingsCategory.homeAndNavigation,
        settings = listOf(
        SettingDefinition(
            id = "x_home_style", title = "Home Layout Style", description = "Overall home screen layout (Chaty Default, One UI, iOS and more)",
            category = SettingsCategory.homeAndNavigation, subcategory = "Layout",
            controlType = SettingControlType.singleChoice, iconName = null,
            searchKeywords = listOf("home style", "layout", "one ui", "ios layout", "home design"),
            canonicalRoute = "/settings/home", defaultValue = "Chaty Default",
        ),
        SettingDefinition(
            id = "x_home_separate_chats_groups", title = "Separate Chats and Groups", description = "Split direct chats and groups into separate home sections",
            category = SettingsCategory.homeAndNavigation, subcategory = "Layout",
            controlType = SettingControlType.toggle, iconName = null,
            searchKeywords = listOf("separate groups", "chats groups split", "sections"),
            canonicalRoute = "/settings/home", defaultValue = false,
        ),
        SettingDefinition(
            id = "x_home_screen_text_size", title = "Home Text Size", description = "Text size for chat rows on the home list",
            category = SettingsCategory.homeAndNavigation, subcategory = "Rows",
            controlType = SettingControlType.slider, iconName = null,
            searchKeywords = listOf("row text size", "home font", "list text size"),
            canonicalRoute = "/settings/home", defaultValue = 17.0,
        ),
        SettingDefinition(
            id = "x_home_avatar_shape", title = "Avatar Shape", description = "Circle or rounded-square contact photos in lists",
            category = SettingsCategory.homeAndNavigation, subcategory = "Rows",
            controlType = SettingControlType.singleChoice, iconName = null,
            searchKeywords = listOf("avatar shape", "round photo", "square avatar", "profile photo shape"),
            canonicalRoute = "/settings/home", defaultValue = "circle",
        ),
        SettingDefinition(
            id = "x_home_ghost_mode", title = "Ghost Mode", description = "Hide your activity indicators from the home header",
            category = SettingsCategory.homeAndNavigation, subcategory = "Header",
            controlType = SettingControlType.toggle, iconName = null,
            searchKeywords = listOf("ghost mode", "ghost pill", "hide activity"),
            canonicalRoute = "/settings/home", defaultValue = false,
        ),
        SettingDefinition(
            id = "x_home_list_animation", title = "List Animation", description = "Entrance animation style for home and conversation lists",
            category = SettingsCategory.homeAndNavigation, subcategory = "Motion",
            controlType = SettingControlType.singleChoice, iconName = null,
            searchKeywords = listOf("list animation", "entrance animation", "list motion"),
            canonicalRoute = "/settings/home", defaultValue = "Standard",
        ),
        ),
    ),
    SettingsCluster(
        id = "x_cluster_appearance_brand", title = "Branding & Icons", description = "App icon, icon packs, emoji style and font",
        category = SettingsCategory.appearance,
        settings = listOf(
        SettingDefinition(
            id = "x_appearance_icon_pack", title = "Icon Pack", description = "Icon set used across the whole app",
            category = SettingsCategory.appearance, subcategory = "Icons",
            controlType = SettingControlType.singleChoice, iconName = null,
            searchKeywords = listOf("icon pack", "icon theme", "hugeicons", "icons"),
            canonicalRoute = "/settings/universal_appearance", defaultValue = "Hugeicons Free",
        ),
        SettingDefinition(
            id = "x_appearance_emoji_style", title = "Emoji Style", description = "Emoji artwork used in the picker and messages",
            category = SettingsCategory.appearance, subcategory = "Emoji",
            controlType = SettingControlType.singleChoice, iconName = null,
            searchKeywords = listOf("emoji style", "emoji pack", "emoji design"),
            canonicalRoute = "/settings/universal_appearance", defaultValue = "Chaty Motion",
        ),
        SettingDefinition(
            id = "x_appearance_font_style", title = "Font Style", description = "Typeface used across the app",
            category = SettingsCategory.appearance, subcategory = "Typography",
            controlType = SettingControlType.singleChoice, iconName = null,
            searchKeywords = listOf("font style", "typeface", "font family", "load font"),
            canonicalRoute = "/settings/universal_appearance", defaultValue = "Default",
        ),
        SettingDefinition(
            id = "x_appearance_launcher_icon", title = "Launcher Icon", description = "App icon shown on the phone home screen",
            category = SettingsCategory.appearance, subcategory = "Branding",
            controlType = SettingControlType.previewSelector, iconName = null,
            searchKeywords = listOf("launcher icon", "app icon", "icon designs"),
            canonicalRoute = "/settings/app_icon", defaultValue = "Classic",
        ),
        SettingDefinition(
            id = "x_appearance_component_styles", title = "Component Styles", description = "Bubble, tick, composer and popup shape variants",
            category = SettingsCategory.appearance, subcategory = "Components",
            controlType = SettingControlType.navigationLink, iconName = null,
            searchKeywords = listOf("component styles", "bubble shape", "tick style", "composer style"),
            canonicalRoute = "/settings/customization", defaultValue = null,
        ),
        ),
    ),
    SettingsCluster(
        id = "x_cluster_storage_controls", title = "Storage Controls", description = "Upload quality, cache limit and gallery hiding",
        category = SettingsCategory.storageAndMedia,
        settings = listOf(
        SettingDefinition(
            id = "x_storage_full_resolution", title = "Full Resolution Image Upload", description = "Send images at up to 6 MB instead of 1 MB",
            category = SettingsCategory.storageAndMedia, subcategory = "Uploads",
            controlType = SettingControlType.toggle, iconName = null,
            searchKeywords = listOf("full resolution", "image quality", "upload quality", "hd photos"),
            canonicalRoute = "/settings/storage", defaultValue = false,
        ),
        SettingDefinition(
            id = "x_storage_cache_limit", title = "Media Cache Limit", description = "Maximum local media cache size before older entries are removed",
            category = SettingsCategory.storageAndMedia, subcategory = "Cache",
            controlType = SettingControlType.singleChoice, iconName = null,
            searchKeywords = listOf("cache limit", "storage limit", "media cache", "free up space"),
            canonicalRoute = "/settings/storage", defaultValue = "1 GB",
        ),
        SettingDefinition(
            id = "x_storage_clear_cache", title = "Clear Local Media Cache", description = "Remove cached media from this device; server copies stay",
            category = SettingsCategory.storageAndMedia, subcategory = "Cache",
            controlType = SettingControlType.action, iconName = null,
            searchKeywords = listOf("clear cache", "delete cache", "free space", "storage cleanup"),
            canonicalRoute = "/settings/storage", defaultValue = null,
        ),
        SettingDefinition(
            id = "x_storage_hide_media_gallery", title = "Hide Media from Gallery", description = "Keep Chaty photos, videos and GIFs out of the phone gallery",
            category = SettingsCategory.storageAndMedia, subcategory = "Gallery",
            controlType = SettingControlType.toggle, iconName = null,
            searchKeywords = listOf("hide from gallery", "nomedia", "gallery visibility", "hide photos"),
            canonicalRoute = "/settings/storage", defaultValue = false,
        ),
        ),
    ),
    SettingsCluster(
        id = "x_cluster_chats_automation", title = "Automation", description = "Auto-reply rules and scheduled messages",
        category = SettingsCategory.chats,
        settings = listOf(
        SettingDefinition(
            id = "x_chats_auto_reply", title = "Auto Reply Rules", description = "Automatic responses matched on keywords",
            category = SettingsCategory.chats, subcategory = "Automation",
            controlType = SettingControlType.navigationLink, iconName = null,
            searchKeywords = listOf("auto reply", "automatic reply", "keyword reply", "away message"),
            canonicalRoute = "/settings/message_management", defaultValue = null,
        ),
        SettingDefinition(
            id = "x_chats_scheduled_messages", title = "Scheduled Messages", description = "Queue messages to send at a future time",
            category = SettingsCategory.chats, subcategory = "Automation",
            controlType = SettingControlType.navigationLink, iconName = null,
            searchKeywords = listOf("scheduled message", "send later", "schedule", "timed message"),
            canonicalRoute = "/settings/message_management", defaultValue = null,
        ),
        ),
    ),
    SettingsCluster(
        id = "x_cluster_effects_particles", title = "Particles & Motion", description = "Touch particles and falling-item effects",
        category = SettingsCategory.effects,
        settings = listOf(
        SettingDefinition(
            id = "x_effects_click_particles", title = "Click Particles", description = "Small particle burst where you tap",
            category = SettingsCategory.effects, subcategory = "Particles",
            controlType = SettingControlType.toggle, iconName = null,
            searchKeywords = listOf("click particles", "tap effect", "touch effect"),
            canonicalRoute = "/settings/effects", defaultValue = false,
        ),
        SettingDefinition(
            id = "x_effects_falling_particles", title = "Falling Items", description = "Stars or symbols falling over the screen",
            category = SettingsCategory.effects, subcategory = "Particles",
            controlType = SettingControlType.toggle, iconName = null,
            searchKeywords = listOf("falling particles", "falling stars", "screen effect"),
            canonicalRoute = "/settings/effects", defaultValue = false,
        ),
        ),
    ),
)
