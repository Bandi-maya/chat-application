package com.example.chat.core.templates

/** Port of lib/ui/core/templates/template_models.dart (api 3bee780d). iconName = Dart Icons.<name>. */
enum class ChatyTemplateId(val key: String, val displayName: String, val description: String) {
    visualSocial("visual_social", "Visual Social", "Media-first layout with prominent updates and strong identity"),
    stream("stream", "Stream", "Information-dense, text-forward timeline layout for fast scanning"),
    cameraFirst("camera_first", "Camera First", "Capture-centric layout with prominent center camera action"),
    messageFirst("message_first", "Message First", "Clean, conversation-first communication layout with minimal visual noise"),
    powerChat("power_chat", "Power Chat", "Utility-dense layout with folders, rich composer and power menus"),
    community("community", "Community", "Spaces and identity-focused layout with persistent navigation surface");

    companion object {
        fun fromKey(key: String?): ChatyTemplateId = values().firstOrNull { it.key == key } ?: messageFirst
    }
}

enum class TemplateComponentType(val title: String, val description: String, val iconName: String) {
    navigation("Navigation", "Bottom bar / dock layout and destination arrangement", "dock_rounded"),
    home("Home Structure", "Home feed composition, header, and stories rail", "home_outlined"),
    chatList("Chat List", "Row density, avatar treatment, and unread badges", "format_list_bulleted_rounded"),
    conversation("Conversation", "Bubble contours, timestamps, and message spacing", "chat_bubble_outline_rounded"),
    composer("Composer", "Action density, attachment position, and send morph", "input_rounded"),
    updates("Updates", "Status rails, story rings, and media presentation", "auto_stories_outlined"),
    profile("Profile", "Identity hierarchy, banner, and statistics layout", "person_outline_rounded"),
    calls("Calls", "Call controls, participant grid, and island styling", "call_outlined");
}

object ChatyNavigationDestinationIds {
    const val chats = "chats"
    const val groups = "groups"
    const val updates = "updates"
    const val tasks = "tasks"
    const val calls = "calls"
    const val settings = "settings"
    const val desktop = "desktop"
    val all: List<String> = listOf(chats, groups, updates, tasks, calls, settings, desktop)
    val known: Set<String> = all.toSet()
}

enum class NavigationLayoutType { flatTabs, floatingPill, centerActionDock, utilityBar, identityBar }

data class NavigationTemplate(
    val layout: NavigationLayoutType,
    val primaryDestinationIds: List<String>,
    val overflowDestinationIds: List<String> = emptyList(),
    val height: Double = 64.0,
    val hasCenterAction: Boolean = false,
    val centerActionId: String = "",
    val centerActionIcon: String? = null,
    val bottomBarStyleName: String,
)

enum class HomeHeaderStyle { compact, prominentIdentity, searchForward, storiesFirst }

data class HomeTemplate(
    val headerStyle: HomeHeaderStyle,
    val showStoriesStrip: Boolean,
    val storiesStyle: String,
    val homeStylePreset: String,
    val separateChatsAndGroups: Boolean = false,
)

enum class ChatListDensity { compact, regular, comfortable }

data class ChatListTemplate(
    val density: ChatListDensity,
    val avatarShape: String,
    val showPresenceBadge: Boolean = true,
    val showDivider: Boolean = true,
    val itemHeight: Double = 72.0,
)

data class ConversationTemplate(
    val bubbleStyle: String,
    val tickStyle: String,
    val bubbleCornerRadius: Double = 16.0,
    val enableQuickSidebar: Boolean = false,
    val wallpaperType: String = "Pattern",
    val showAvatarInGroup: Boolean = true,
)

enum class ComposerActionPlacement { integrated, split, powerRow }

data class ComposerTemplate(
    val actionPlacement: ComposerActionPlacement,
    val cornerRadius: Double = 24.0,
    val showVoiceLock: Boolean = true,
    val showCameraShortcut: Boolean = true,
    val enableSendMorph: Boolean = true,
)

enum class UpdatesLayoutMode { circularRail, squircleCards, minimalList, gridTiles }

data class UpdatesTemplate(
    val layoutMode: UpdatesLayoutMode,
    val enableStatusAudio: Boolean = true,
    val cardElevation: Double = 0.0,
)

enum class ProfileHeaderStyle { bannerWithAvatar, centeredIdentity, compactHeader }

data class ProfileTemplate(
    val headerStyle: ProfileHeaderStyle,
    val avatarShape: String,
    val showStatsGrid: Boolean = true,
)

data class CallTemplate(
    val enableFloatingIsland: Boolean = true,
    val controlBarCornerRadius: Double = 28.0,
)

data class ChatyTemplateDefinition(
    val id: ChatyTemplateId,
    val name: String,
    val subtitle: String,
    val description: String,
    val navigation: NavigationTemplate,
    val home: HomeTemplate,
    val chatList: ChatListTemplate,
    val conversation: ConversationTemplate,
    val composer: ComposerTemplate,
    val updates: UpdatesTemplate,
    val profile: ProfileTemplate,
    val calls: CallTemplate,
)

data class UserTemplateConfiguration(
    val baseTemplate: ChatyTemplateId = ChatyTemplateId.messageFirst,
    val componentOverrides: Map<TemplateComponentType, ChatyTemplateId> = emptyMap(),
    val navigationPrimaryDestinationIds: List<String>? = null,
    val navigationOverflowDestinationIds: List<String>? = null,
) {
    fun resolveFor(component: TemplateComponentType): ChatyTemplateId = componentOverrides[component] ?: baseTemplate

    fun isOverridden(component: TemplateComponentType): Boolean = componentOverrides.containsKey(component)

    val hasNavigationDestinationOverride: Boolean
        get() = navigationPrimaryDestinationIds != null || navigationOverflowDestinationIds != null

    fun copyWithOverride(component: TemplateComponentType, template: ChatyTemplateId): UserTemplateConfiguration {
        val next = LinkedHashMap(componentOverrides)
        if (template == baseTemplate) next.remove(component) else next[component] = template
        return copy(componentOverrides = next)
    }

    fun removeOverride(component: TemplateComponentType): UserTemplateConfiguration {
        val next = LinkedHashMap(componentOverrides)
        next.remove(component)
        return copy(componentOverrides = next)
    }

    fun copyWithNavigationDestinations(primaryDestinationIds: List<String>, overflowDestinationIds: List<String>) =
        copy(
            navigationPrimaryDestinationIds = primaryDestinationIds.toList(),
            navigationOverflowDestinationIds = overflowDestinationIds.toList(),
        )

    fun withoutNavigationDestinationOverride() =
        UserTemplateConfiguration(baseTemplate = baseTemplate, componentOverrides = componentOverrides)

    fun toMap(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["version"] = 1
        m["base"] = baseTemplate.key
        m["overrides"] = componentOverrides.entries.associate { it.key.name to it.value.key }
        navigationPrimaryDestinationIds?.let { m["navPrimary"] = it }
        navigationOverflowDestinationIds?.let { m["navOverflow"] = it }
        return m
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>?): UserTemplateConfiguration {
            if (map == null) return UserTemplateConfiguration()
            val base = ChatyTemplateId.fromKey(map["base"] as? String)
            val rawOverrides = (map["overrides"] as? Map<String, Any?>) ?: emptyMap()
            val overrides = LinkedHashMap<TemplateComponentType, ChatyTemplateId>()
            for ((k, v) in rawOverrides) {
                val comp = TemplateComponentType.values().firstOrNull { it.name == k }
                if (comp != null) overrides[comp] = ChatyTemplateId.fromKey(v as? String)
            }
            fun decode(raw: Any?): List<String>? {
                if (raw !is List<*>) return null
                val seen = HashSet<String>()
                val ids = ArrayList<String>()
                for (item in raw) {
                    if (item !is String || item !in ChatyNavigationDestinationIds.known || !seen.add(item)) continue
                    ids.add(item)
                }
                return if (ids.isEmpty()) null else ids
            }
            return UserTemplateConfiguration(base, overrides, decode(map["navPrimary"]), decode(map["navOverflow"]))
        }
    }
}
