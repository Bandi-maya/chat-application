package com.example.chat.core.templates

import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.core.theme.ThemeController
import com.example.chat.core.theme.bubbleStyleIdFromString
import com.example.chat.core.theme.deliveryIconStyleFromString

/**
 * Binds TemplateController's sinks to the real controllers (uncompiled; CI is first compile).
 * OWNER GAP: AppNavigationMode (core/theme/ThemeConfig.kt) lacks Dart's `compactRail`. Until the owner adds it,
 * utilityBar cannot map faithfully; it falls back to bottomNav. Add `compactRail` and switch the fallback line.
 */
class TemplateRuntimeAdapter(
    private val themeController: ThemeController? = null,
    private val preferences: ChatyPreferencesController? = null,
    private val appearance: AppearanceVariantController? = null,
) : TemplateRuntimeSink {

    override fun applyNavigationLayout(layout: NavigationLayoutType) {
        val tc = themeController ?: return
        val target = when (layout) {
            NavigationLayoutType.flatTabs, NavigationLayoutType.floatingPill, NavigationLayoutType.centerActionDock -> AppNavigationMode.bottomNav
            NavigationLayoutType.utilityBar -> AppNavigationMode.bottomNav // TODO compactRail (owner gap)
            NavigationLayoutType.identityBar -> AppNavigationMode.topWhatsAppBar
        }
        if (tc.navigationMode != target) tc.setNavigationMode(target)
    }

    override fun applyConversationTheme(conversation: ConversationTemplate) {
        val tc = themeController ?: return
        val current = tc.globalTheme
        val next = current.copyWith(
            bubbleStyle = bubbleStyleIdFromString(conversation.bubbleStyle),
            deliveryTickStyle = deliveryIconStyleFromString(conversation.tickStyle),
            tickStyle = conversation.tickStyle,
            cornerRadius = conversation.bubbleCornerRadius,
        )
        if (next.bubbleStyle != current.bubbleStyle || next.deliveryTickStyle != current.deliveryTickStyle ||
            next.tickStyle != current.tickStyle || next.cornerRadius != current.cornerRadius
        ) tc.updateThemeConfig(next)
    }

    override suspend fun applyBottomBarStyle(name: String) {
        val a = appearance ?: return
        if (a.bottomBarStyle != name) a.setBottomBarStyle(name)
    }

    override fun applyHomePreferences(home: HomeTemplate) {
        val p = preferences ?: return
        val cur = p.home
        if (cur.homeStyle != home.homeStylePreset || cur.enableStoriesStrip != home.showStoriesStrip ||
            cur.storiesStyle != home.storiesStyle || cur.separateChatsAndGroups != home.separateChatsAndGroups
        ) {
            p.updateHome(
                cur.copy(
                    homeStyle = home.homeStylePreset,
                    enableStoriesStrip = home.showStoriesStrip,
                    storiesStyle = home.storiesStyle,
                    separateChatsAndGroups = home.separateChatsAndGroups,
                ),
                logTitle = "Template Home Sync",
            )
        }
    }

    override fun applyConversationPreferences(conversation: ConversationTemplate) {
        val p = preferences ?: return
        val cur = p.conversation
        if (cur.bubbleStyle != conversation.bubbleStyle || cur.tickStyle != conversation.tickStyle ||
            cur.enableQuickContactSidebar != conversation.enableQuickSidebar || cur.wallpaperType != conversation.wallpaperType
        ) {
            p.updateConversation(
                cur.copy(
                    bubbleStyle = conversation.bubbleStyle,
                    tickStyle = conversation.tickStyle,
                    enableQuickContactSidebar = conversation.enableQuickSidebar,
                    wallpaperType = conversation.wallpaperType,
                ),
                logTitle = "Template Conversation Sync",
            )
        }
    }

    fun runtime(): TemplateRuntime = TemplateRuntime(theme = this, appearance = this, preferences = this)
}
