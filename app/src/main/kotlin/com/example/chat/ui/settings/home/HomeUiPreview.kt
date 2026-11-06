package com.example.chat.ui.settings.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.chat.ServiceLocator
import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.chats.ChatsHomeScreen
import com.example.chat.ui.chats.home.HomeIdentity
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.navigation.MainNavigationShell
import com.example.chat.ui.navigation.NavigationShellPrefs

/** Read-only viewport of the real Home screen and real navigation shell, not a sample drawing. */
@Composable
fun HomeUiPreview(style: String, theme: ThemeConfig, current: AppNavigationMode, modifier: Modifier = Modifier, large: Boolean = false) {
    val prefs = ServiceLocator.preferencesController
    val api = ServiceLocator.apiBackend
    val home = prefs.home.copy(homeStyle = style)
    val me = api.currentUser
    Box(modifier.fillMaxWidth().height(if (large) 360.dp else 300.dp).clipToBounds()
        .clearAndSetSemantics { contentDescription = "Read-only preview of $style" }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }) {
        androidx.compose.runtime.key(style) {
        MainNavigationShell(
            colors = ChatyColors(theme), modifier = Modifier.fillMaxSize(), navigationMode = current, previewOnly = true, previewMenusOpen = true,
            template = ServiceLocator.templateController.navigation,
            prefs = NavigationShellPrefs(homeStyle = style, separateChatsAndGroups = home.separateChatsAndGroups,
                pagerTransition3d = home.pagerTransition3d, animationsDisabled = true,
                hasCustomNavigationDestinations = ServiceLocator.templateController.hasCustomNavigationDestinations,
                hideTasks = prefs.gbBool("nav_hide_tasks"),
                hideUpdates = prefs.gbBool("nav_hide_updates") || !com.example.chat.ReleaseFeatures.STATUS_UPDATES,
                bottomBarStyle = ServiceLocator.appearanceVariantController.bottomBarStyle,
                oneUiRaisedCenterDock = com.example.chat.ui.navigation.HomePresetNormalizer.oneUiRaisedCenterDockEnabled(style),
                navigationStyleName = ServiceLocator.appearanceVariantController.navigationStyle),
            screenContent = {
                ChatsHomeScreen(
                    templates = com.example.chat.ui.chats.home.resolveHomeTemplates(ServiceLocator.templateController.config, com.example.chat.core.preferences.HomePresentationPolicy.isDefault(style), ServiceLocator.templateController.chatList),
                    conversations = api.conversations.filterNot { prefs.isConversationLocked(it.id) || prefs.isConversationHidden(it.id) },
                    requiresSubscription = false, subscriptionMessage = null, onOpenConversation = {},
                    currentUserId = me?.id.orEmpty(), homePreferences = home, securityPreferences = prefs.security,
                    identity = HomeIdentity(displayName = me?.displayName.orEmpty(), username = me?.username.orEmpty(),
                        about = me?.about.orEmpty(), avatarInitials = me?.avatarInitials ?: "C",
                        avatarColorHex = me?.avatarColorHex, avatarUrl = me?.avatarUrl),
                    contacts = api.users, isConversationHidden = { prefs.isConversationHidden(it) },
                    gbBool = { key, fallback -> prefs.gbBool(key, fallback) }, gbColor = { prefs.gbColor(it) },
                    headerBottom = com.example.chat.ui.navigation.LocalHomeHeaderTabs.current?.let { tabs ->
                        if (!tabs.active) null else ({ com.example.chat.ui.navigation.HomeHeaderTabsBar(
                            ChatyColors(theme), tabs.ids, tabs.selectedIndex, tabs.heightDp, onSelect = {}) })
                    },
                )
            },
        )
        }
    }
}
