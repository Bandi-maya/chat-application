package com.example.chat.core.preferences

/** Exact port of lib/ui/core/gb/gb_feature_catalog.dart (api branch),
 *  including its generated-definition pipeline (category key lists + kind /
 *  default / max / humanize inference), so `defaults` resolves identically. */

enum class GbFeatureKind { toggle, choice, slider, color, action }

data class GbFeatureDefinition(
    val key: String,
    val title: String,
    val category: String,
    val kind: GbFeatureKind,
    val defaultValue: Any?,
    val description: String,
    val min: Double = 0.0,
    val max: Double = 100.0,
    val options: List<String> = emptyList(),
)

object GbFeatureCatalog {

    private val categoryKeys: Map<String, String> = linkedMapOf(
        "Navigation & touch effects" to
            "tap_effect_enabled,tap_emoji,fall_effect_enabled,fall_emoji",
        "Chat list rows" to
            "ModConTextColor,ModContactNameColor,HomeCounterBK,HomeCounterText,ModOnlineColor,ModlastseenColor,key_mas_hide_archive_home,onlinechat,onlineDotchat,onlineDotchatColor",
        "Presence & activity alerts" to
            "abu_saleh_toast_online,abu_saleh_toast_online_bc,abu_saleh_toast_online_tc,abu_saleh_toast_status,abu_saleh_toast_status_bc,abu_saleh_toast_status_tc,abu_saleh_toast_profile,abu_saleh_toast_profile_bc,abu_saleh_toast_profile_tc,abu_saleh_toast_typing,abu_saleh_toast_typing_bc,abu_saleh_toast_typing_tc",
        "Home header" to
            "ui_home_styleV3,home_stories_key,home_stories_style,enable_grp_separationV2,my_name,yo_want_ghostmode,yo_want_toolbar_cam,yo_want_airplanemode,ModConColor,tabindicator",
        "Privacy" to
            "yoHideSeen,anti_vw_once,yoDisableFwd,yoCallsPrivacy,Saleh_HidePrivacy,yoHideStatViewV2,yoAntiRevokeStatus,AntiRevokeStatusNotif,yoAntiRevoke,AntiRevokeMsgNotif,yoBlueOnReply",
        "Status & stories" to
            "home_stories_key,home_stories_style,abu9aleh_status_audio",
        "Chat bubbles & ticks" to
            "tick_style,bubble_style,text_size_pick,ConvoBack,ModChatRightBubble,ModChatBubbleText,date_right_color,ModChatLeftBubble,ModChatBubbleTextLeft,date_left_color",
        "Universal behavior" to
            "Pop_Heds,always_online,Img_highres_seek",
        "Calls appearance" to
            "ModCallsBackground,ModCallsTextColor,ModCallsIconColors",
        "Conversation header" to
            "ModChatColor,PicProf,NameProf,Conv_call_btn,statuschat,ModChatGStatusB,ModChatGStatusT",
        "Quick contact" to
            "abu_saleh_quickcontact",
        "Universal colors" to
            "ModConPickColor,HomeBarText,ModConBackColor,list_bg_color,ModDarkConPickColor,ModDarkConPickColorNav",
        "Media limits & quality" to
            "Up_size_limit,abo_saleh_audio_limit_check,Img_share_limit,key_more_docs_send",
        "Composer appearance" to
            "BGColor",
    )

    private val choiceOptions: Map<String, List<String>> = mapOf(
        "ui_home_styleV3" to listOf("Chaty", "Classic", "Compact", "Cards", "Minimal", "Stories first"),
        "home_stories_style" to listOf("Circular", "Squircle", "Cards", "Compact", "Minimal"),
        "tick_style" to listOf("Default", "Double check", "iOS", "Minimal", "Neon"),
        "bubble_style" to listOf("Rounded", "Tail", "Tail-less", "Compact", "Squircle", "Card", "Pill"),
        "Language" to listOf("System", "English", "Hindi", "Telugu", "Tamil", "Spanish", "French", "German", "Arabic"),
        "abu_saleh_quickcontact" to listOf("Off", "Left", "Right", "Floating"),
        "MasOption" to listOf("Default", "Compact", "Expanded", "Bottom sheet"),
        "yoCallsPrivacy" to listOf("Everyone", "My contacts", "My contacts except", "Nobody"),
        "tap_emoji" to listOf("✨", "❤️", "🔥", "⚡", "⭐", "🌸", "💫", "🎉"),
        "fall_emoji" to listOf("Stars", "Hearts", "Snowflakes", "Leaves", "Bubbles", "Sparks"),
    )

    private val descriptions: Map<String, String> = mapOf(
        "yoHideSeen" to "Control read receipts / blue ticks.",
        "anti_vw_once" to "Allow view-once media to remain viewable on this device.",
        "yoDisableFwd" to "Hide forwarded labels on messages you forward.",
        "yoCallsPrivacy" to "Choose who may call you.",
        "yoAntiRevoke" to "Keep a locally visible copy when a sender deletes a message.",
        "yoAntiRevokeStatus" to "Keep a locally visible copy of a status after its owner deletes it until expiry.",
        "yoHideStatViewV2" to "Do not publish status-view receipts.",
        "yoBlueOnReply" to "Send read receipts only after you reply.",
        "always_online" to "Keep presence online while Chaty maintains an active connection.",
        "abu9aleh_status_audio" to "Allow audio status uploads.",
        "Audio_ears" to "Use proximity/earpiece behavior for voice-note playback.",
        "Audio_sensor" to "Enable proximity sensor handling during voice-note playback.",
        "Img_highres_seek" to "Set preferred image quality for uploads.",
        "Up_size_limit" to "Maximum video/file upload size. Capped at the current 50 MB private-storage limit.",
        "abo_saleh_audio_limit_check" to "Maximum audio upload size. Capped at the current 50 MB private-storage limit.",
        "Img_share_limit" to "Configure maximum images selectable in one send.",
        "key_more_docs_send" to "Allow selecting more documents per send.",
        "Pop_Heds" to "Enable heads-up / floating message alerts.",
        "home_stories_key" to "Show stories/status strip on home.",
        "onlinechat" to "Show online state in chat list rows.",
        "onlineDotchat" to "Show an online dot on chat avatars.",
        "abu_saleh_toast_online" to "Alert when a contact comes online (toast).",
        "abu_saleh_toast_typing" to "Alert when a contact starts typing (toast).",
        "abu_saleh_toast_status" to "Alert when a contact publishes a new status.",
        "abu_saleh_toast_profile" to "Alert when a contact updates their profile details.",
        "onlineDotchatColor" to "Custom color for the chat-list online dot.",
        "HomeCounterBK" to "Unread-count badge background color in chat rows.",
        "HomeCounterText" to "Unread-count badge text color in chat rows.",
        "ModOnlineColor" to "Online presence text color in chat rows.",
        "ModlastseenColor" to "Last-seen text color in chat rows.",
        "PicProf" to "Show the contact avatar in the conversation header.",
        "NameProf" to "Show the contact name in the conversation header.",
        "Conv_call_btn" to "Show voice/video call buttons in the conversation header.",
        "statuschat" to "Show the presence/status line under the header name.",
        "ModChatGStatusB" to "Presence pill background color in the header.",
        "ModChatGStatusT" to "Presence pill text color in the header.",
        "key_mas_hide_archive_home" to "Hide archived-chat shortcut on home.",
        "yo_want_ghostmode" to "Enable the stealth privacy bundle.",
        "yo_want_airplanemode" to "Pause Chaty network/realtime actions without disabling the phone network.",
        "yo_want_toolbar_cam" to "Show the camera shortcut.",
        "enable_grp_separationV2" to "Separate direct chats and groups.",
    )

    val all: List<GbFeatureDefinition> by lazy {
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<GbFeatureDefinition>()
        for ((category, csv) in categoryKeys) {
            for (raw in csv.split(",")) {
                val key = raw.trim()
                if (key.isEmpty() || !seen.add(key)) continue
                result.add(definition(category, key))
            }
        }
        result.toList()
    }

    val defaults: Map<String, Any?> by lazy { all.associate { it.key to it.defaultValue } }

    val categories: List<String> get() = categoryKeys.keys.toList()

    fun inCategory(category: String): List<GbFeatureDefinition> =
        all.filter { it.category == category }

    fun byKey(key: String): GbFeatureDefinition? = all.firstOrNull { it.key == key }

    private fun definition(category: String, key: String): GbFeatureDefinition {
        val kind = kindFor(key)
        val options = choiceOptions[key] ?: listOf("Default", "Style 1", "Style 2", "Style 3")
        return GbFeatureDefinition(
            key = key,
            title = humanize(key),
            category = category,
            kind = kind,
            defaultValue = defaultFor(key, kind, options),
            description = descriptions[key]
                ?: "Independent Chaty implementation of the extracted compatibility control.",
            max = maxFor(key),
            options = if (kind == GbFeatureKind.choice) options else emptyList(),
        )
    }

    private val explicitChoiceKeys = setOf(
        "yoCallsPrivacy", "animation_drawable", "animation_drawableshatssss",
        "yo_nicons", "key_admicon_list", "key_quick_position",
        "key_custom_iconup", "key_custom_icondown",
        "hazar_bozkurt_sesler_gelenmesaj", "hazar_bozkurt_sesler_gidenmesaj",
    )

    private val actionKeys = setOf(
        "clear_logs", "mas_key_cleanlog_blocklist", "load_customfont", "key_reply_mention",
    )

    private val sliderKeys = setOf(
        "main_text", "img_highres_seek", "up_size_limit",
        "abo_saleh_audio_limit_check", "img_share_limit", "text_size_pick",
        "pic_chat_size_pickerv2",
    )

    private fun kindFor(key: String): GbFeatureKind {
        if (choiceOptions.containsKey(key) || explicitChoiceKeys.contains(key)) {
            return GbFeatureKind.choice
        }
        if (actionKeys.contains(key)) return GbFeatureKind.action
        val k = key.lowercase()
        if (sliderKeys.contains(k) || k.contains("density") || k.contains("intens") ||
            k.contains("speed") || k.contains("duration") || k.endsWith("_rd")
        ) {
            return GbFeatureKind.slider
        }
        if (k.contains("color") || k.contains("picker") || k.contains("background") ||
            k.endsWith("_bc") || k.endsWith("_tc") || k.contains("counterbk") ||
            k.contains("countertext") || k.contains("quickbk") || k.contains("quicktext") ||
            k.startsWith("modchat") || k.startsWith("modcon") || k.startsWith("modcalls") ||
            k.startsWith("modwdg") || k.startsWith("modfab") || k.startsWith("emojipopup") ||
            k == "bgcolor" || k == "convoback" || k == "homebartext"
        ) {
            return GbFeatureKind.color
        }
        return GbFeatureKind.toggle
    }

    private val defaultTrueKeys = setOf(
        "anti_vw_once", "yoAntiRevoke", "yoAntiRevokeStatus", "AntiRevokeMsgNotif",
        "AntiRevokeStatusNotif", "key_chat_editview", "abusaleh_media", "yo_hideinfo",
        // Header/list elements that are visible until explicitly disabled.
        "onlinechat", "onlineDotchat", "PicProf", "NameProf", "Conv_call_btn", "statuschat",
    )

    internal fun defaultFor(key: String, kind: GbFeatureKind, options: List<String>): Any? {
        if (defaultTrueKeys.contains(key)) return true
        return when (kind) {
            GbFeatureKind.toggle -> false
            GbFeatureKind.choice -> options.first()
            GbFeatureKind.slider -> when (key) {
                "text_size_pick" -> 15.0
                "pic_chat_size_pickerV2" -> 36.0
                "Img_highres_seek" -> 85.0
                "Up_size_limit" -> 50.0
                "abo_saleh_audio_limit_check" -> 50.0
                "Img_share_limit" -> 30.0
                else -> 1.0
            }
            GbFeatureKind.color -> 0
            GbFeatureKind.action -> ""
        }
    }

    internal fun maxFor(key: String): Double {
        when (key) {
            "text_size_pick" -> return 30.0
            "pic_chat_size_pickerV2" -> return 64.0
            "Img_highres_seek" -> return 100.0
            "Up_size_limit" -> return 50.0
            "abo_saleh_audio_limit_check" -> return 50.0
            "Img_share_limit" -> return 100.0
        }
        val k = key.lowercase()
        if (k.contains("density") || k.contains("intens") || k.contains("speed")) return 20.0
        if (k.contains("duration")) return 5000.0
        return 100.0
    }

    internal fun humanize(key: String): String {
        var value = Regex("[_-]+").replace(key, " ")
        value = Regex("([a-z0-9])([A-Z])").replace(value, "$1 $2")
        value = Regex("\\s+").replace(value, " ").trim()
        val replacements = linkedMapOf(
            "yo " to "",
            "key " to "",
            "abu " to "",
            "saleh " to "",
            "BK" to "Background",
            "BG" to "Background",
            "TX" to "Text",
            "Pic" to "Picture",
            "Prof" to "Profile",
            "Convo" to "Conversation",
            "Fwd" to "Forward",
        )
        for ((from, to) in replacements) value = value.replace(from, to)
        if (value.isEmpty()) return key
        return value.replaceFirstChar { it.uppercase() }
    }
}
