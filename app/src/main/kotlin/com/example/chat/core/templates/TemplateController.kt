package com.example.chat.core.templates

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persistence seam: LocalPreferencesStorage.loadTemplateState / saveTemplateState ('chaty_template_v1'). */
interface TemplateStateStore {
    suspend fun load(): Map<String, Any?>
    suspend fun save(data: Map<String, Any?>): Boolean
}

/**
 * Runtime sinks that TemplateController._syncToRuntimeControllers writes into. Implemented over the real
 * ThemeController / ChatyPreferencesController / AppearanceVariantController by TemplateRuntimeAdapter.
 * Each method must apply only when the target differs from the current value (Dart's guard).
 * Null sink = Dart's null controller (that sync step is skipped).
 */
interface TemplateRuntimeSink {
    /** Dart: themeController.setNavigationMode(switch(layout)). */
    fun applyNavigationLayout(layout: NavigationLayoutType)
    /** Dart: themeWithTemplateConversation + updateThemeConfig when bubble/tick/cornerRadius differ. */
    fun applyConversationTheme(conversation: ConversationTemplate)
    /** Dart: appearanceController.setBottomBarStyle when different. */
    suspend fun applyBottomBarStyle(name: String)
    /** Dart: updateHome(... logTitle 'Template Home Sync') when any of 4 fields differ. */
    fun applyHomePreferences(home: HomeTemplate)
    /** Dart: updateConversation(... logTitle 'Template Conversation Sync') when any of 4 fields differ. */
    fun applyConversationPreferences(conversation: ConversationTemplate)
}

class TemplateRuntime(
    val theme: TemplateRuntimeSink? = null,
    val appearance: TemplateRuntimeSink? = null,
    val preferences: TemplateRuntimeSink? = null,
)

/** Port of lib/ui/core/templates/template_controller.dart. */
class TemplateController(private val store: TemplateStateStore) {
    var config: UserTemplateConfiguration = UserTemplateConfiguration(); private set
    private var initialized = false
    private val commitLock = Mutex()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyListeners() { listeners.forEach { it() } }

    val baseTemplate: ChatyTemplateId get() = config.baseTemplate
    val componentOverrides: Map<TemplateComponentType, ChatyTemplateId> get() = config.componentOverrides

    fun resolveTemplateFor(component: TemplateComponentType): ChatyTemplateId = config.resolveFor(component)

    val navigation: NavigationTemplate
        get() {
            val base = ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.navigation)).navigation
            val primary = config.navigationPrimaryDestinationIds
            val overflow = config.navigationOverflowDestinationIds
            if (primary == null && overflow == null) return base
            return base.copy(
                primaryDestinationIds = primary ?: base.primaryDestinationIds,
                overflowDestinationIds = overflow ?: base.overflowDestinationIds,
            )
        }

    val hasCustomNavigationDestinations: Boolean get() = config.hasNavigationDestinationOverride

    val home: HomeTemplate get() = ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.home)).home
    val chatList: ChatListTemplate get() = ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.chatList)).chatList
    val conversation: ConversationTemplate get() = ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.conversation)).conversation
    val composer: ComposerTemplate get() = ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.composer)).composer
    val updates: UpdatesTemplate get() = ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.updates)).updates
    val profile: ProfileTemplate get() = ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.profile)).profile
    val calls: CallTemplate get() = ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.calls)).calls

    /** Throws IllegalArgumentException with the Dart ArgumentError messages. */
    suspend fun setNavigationDestinations(
        primaryDestinationIds: List<String>,
        overflowDestinationIds: List<String>,
        runtime: TemplateRuntime = TemplateRuntime(),
    ) {
        val primary = primaryDestinationIds.toList()
        val overflow = overflowDestinationIds.toList()
        val primarySet = primary.toSet()
        val overflowSet = overflow.toSet()
        val known = ChatyNavigationDestinationIds.known
        if (primary.isEmpty() || primary.size > 4 ||
            primarySet.size != primary.size ||
            overflowSet.size != overflow.size ||
            primarySet.intersect(overflowSet).isNotEmpty() ||
            primary.any { it !in known } || overflow.any { it !in known }
        ) {
            throw IllegalArgumentException("Choose 1\u20134 unique primary destinations and move the rest into More.")
        }
        val assigned = primarySet + overflowSet
        if (assigned.size != ChatyNavigationDestinationIds.all.size || !assigned.containsAll(ChatyNavigationDestinationIds.all)) {
            throw IllegalArgumentException("Every Chaty destination must remain available in navigation or More.")
        }
        commit(
            { it.copyWithNavigationDestinations(primary, overflow) },
            runtime, applyNavigation = true,
        )
    }

    suspend fun resetNavigationDestinations(runtime: TemplateRuntime = TemplateRuntime()) =
        commit({ it.withoutNavigationDestinationOverride() }, runtime, applyNavigation = true)

    suspend fun init(runtime: TemplateRuntime = TemplateRuntime()) {
        if (initialized) return
        initialized = true
        try {
            val state = store.load()
            if (state.isNotEmpty()) config = UserTemplateConfiguration.fromMap(state)
        } catch (e: Exception) {
            // Dart: debugPrint, keep defaults.
        }
        sync(runtime, applyTheme = true, applyNavigation = true, applyHome = true, applyConversation = true)
    }

    suspend fun synchronizeThemeAndNavigation(runtime: TemplateRuntime) =
        sync(runtime, applyTheme = true, applyNavigation = true)

    suspend fun applyFullTemplate(templateId: ChatyTemplateId, runtime: TemplateRuntime = TemplateRuntime()) =
        commit({ UserTemplateConfiguration(baseTemplate = templateId) }, runtime,
            applyTheme = true, applyNavigation = true, applyHome = true, applyConversation = true)

    suspend fun applyComponent(component: TemplateComponentType, templateId: ChatyTemplateId, runtime: TemplateRuntime = TemplateRuntime()) =
        commit({ it.copyWithOverride(component, templateId) }, runtime,
            applyTheme = component == TemplateComponentType.conversation,
            applyNavigation = component == TemplateComponentType.navigation,
            applyHome = component == TemplateComponentType.home,
            applyConversation = component == TemplateComponentType.conversation)

    suspend fun applyConfiguration(configuration: UserTemplateConfiguration, runtime: TemplateRuntime = TemplateRuntime()) {
        val normalized = LinkedHashMap<TemplateComponentType, ChatyTemplateId>()
        for ((k, v) in configuration.componentOverrides) if (v != configuration.baseTemplate) normalized[k] = v
        commit({
            UserTemplateConfiguration(
                baseTemplate = configuration.baseTemplate,
                componentOverrides = normalized,
                navigationPrimaryDestinationIds = configuration.navigationPrimaryDestinationIds,
                navigationOverflowDestinationIds = configuration.navigationOverflowDestinationIds,
            )
        }, runtime, applyTheme = true, applyNavigation = true, applyHome = true, applyConversation = true)
    }

    suspend fun removeComponentOverride(component: TemplateComponentType, runtime: TemplateRuntime = TemplateRuntime()) =
        commit({ it.removeOverride(component) }, runtime,
            applyTheme = component == TemplateComponentType.conversation,
            applyNavigation = component == TemplateComponentType.navigation,
            applyHome = component == TemplateComponentType.home,
            applyConversation = component == TemplateComponentType.conversation)

    suspend fun resetToDefaults(runtime: TemplateRuntime = TemplateRuntime()) =
        commit({ UserTemplateConfiguration(baseTemplate = ChatyTemplateId.messageFirst) }, runtime,
            applyTheme = true, applyNavigation = true, applyHome = true, applyConversation = true)

    private suspend fun sync(
        runtime: TemplateRuntime,
        applyTheme: Boolean = false,
        applyNavigation: Boolean = false,
        applyHome: Boolean = false,
        applyConversation: Boolean = false,
    ) {
        if (applyNavigation) runtime.theme?.applyNavigationLayout(navigation.layout)
        if (applyTheme) runtime.theme?.applyConversationTheme(conversation)
        if (applyNavigation && runtime.appearance != null) {
            // Dart does not await this write; a failed persist must not fail the commit.
            try { runtime.appearance.applyBottomBarStyle(navigation.bottomBarStyleName) } catch (_: Exception) {}
        }
        if (runtime.preferences != null && applyHome) runtime.preferences.applyHomePreferences(home)
        if (runtime.preferences != null && applyConversation) runtime.preferences.applyConversationPreferences(conversation)
    }

    /** Serialized; candidate built from latest committed state inside the lock; persist first, then publish. */
    private suspend fun commit(
        buildNext: (UserTemplateConfiguration) -> UserTemplateConfiguration,
        runtime: TemplateRuntime,
        applyTheme: Boolean = false,
        applyNavigation: Boolean = false,
        applyHome: Boolean = false,
        applyConversation: Boolean = false,
    ) {
        commitLock.withLock {
            val next = buildNext(config)
            val saved = store.save(next.toMap())
            if (!saved) throw IllegalStateException("Unable to persist the Chaty template configuration.")
            config = next
            notifyListeners()
            sync(runtime, applyTheme, applyNavigation, applyHome, applyConversation)
        }
    }
}
