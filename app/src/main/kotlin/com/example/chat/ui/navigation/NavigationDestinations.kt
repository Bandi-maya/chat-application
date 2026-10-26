package com.example.chat.ui.navigation

import com.example.chat.core.templates.ChatyNavigationDestinationIds
import com.example.chat.core.templates.NavigationLayoutType
import com.example.chat.core.templates.NavigationTemplate

/** Exact port of the main_navigation_shell.dart destination-resolution
 *  pipeline (catalog build -> template ordering -> primary/overflow split ->
 *  effective index) so it is JVM-testable without Compose. The Dart shell
 *  resolves this inside build(); here the composable calls the same pure
 *  functions. Canonical template models (NavigationLayoutType,
 *  NavigationTemplate, ChatyNavigationDestinationIds) come from
 *  com.example.chat.core.templates (Settings drop8) - no local duplicates. */

/** template_controller.dart's layout -> AppNavigationMode sync
 *  (applyNavigation branch). topWhatsAppBar is a
 *  com.example.chat.core.theme.AppNavigationMode value. */
fun navigationModeForLayout(layout: NavigationLayoutType): com.example.chat.core.theme.AppNavigationMode =
    when (layout) {
        NavigationLayoutType.flatTabs,
        NavigationLayoutType.floatingPill,
        NavigationLayoutType.centerActionDock,
        -> com.example.chat.core.theme.AppNavigationMode.bottomNav
        NavigationLayoutType.utilityBar -> com.example.chat.core.theme.AppNavigationMode.compactRail
        NavigationLayoutType.identityBar -> com.example.chat.core.theme.AppNavigationMode.topWhatsAppBar
    }

data class NavDestination(
    val id: String,
    val label: String,
)

object NavigationCatalog {

    fun labelFor(id: String): String = when (id) {
        ChatyNavigationDestinationIds.chats -> "Chats"
        ChatyNavigationDestinationIds.groups -> "Groups"
        ChatyNavigationDestinationIds.updates -> "Updates"
        ChatyNavigationDestinationIds.tasks -> "Tasks"
        ChatyNavigationDestinationIds.calls -> "Calls"
        ChatyNavigationDestinationIds.settings -> "Settings"
        ChatyNavigationDestinationIds.desktop -> "Desktop"
        else -> id
    }

    /** Dart destination catalog order, including the 'groups' insertion rule
     *  and the gbBool('nav_hide_tasks') / gbBool('nav_hide_updates') removals. */
    fun catalogIds(
        separateChatsAndGroups: Boolean,
        hasCustomNavigationDestinations: Boolean,
        hideTasks: Boolean,
        hideUpdates: Boolean,
    ): List<String> {
        val catalog = mutableListOf<String>()
        catalog.add(ChatyNavigationDestinationIds.chats)
        if (separateChatsAndGroups || hasCustomNavigationDestinations) {
            catalog.add(ChatyNavigationDestinationIds.groups)
        }
        catalog.add(ChatyNavigationDestinationIds.updates)
        catalog.add(ChatyNavigationDestinationIds.tasks)
        catalog.add(ChatyNavigationDestinationIds.calls)
        catalog.add(ChatyNavigationDestinationIds.settings)
        if (hideTasks) catalog.remove(ChatyNavigationDestinationIds.tasks)
        if (hideUpdates) catalog.remove(ChatyNavigationDestinationIds.updates)
        return catalog
    }

    /** Dart: apply the template's destination order without changing identity;
     *  catalog ids missing from the template keep their relative order at the
     *  end; template ids missing from the catalog are skipped. */
    fun resolveOrdered(catalog: List<String>, template: NavigationTemplate): List<String> {
        val ordered = mutableListOf<String>()
        val preferred = template.primaryDestinationIds + template.overflowDestinationIds
        for (id in preferred) {
            if (id in catalog && id !in ordered) ordered.add(id)
        }
        for (id in catalog) {
            if (id !in ordered) ordered.add(id)
        }
        return ordered
    }

    /** Dart primary/overflow split. A malformed or future template cannot
     *  leave navigation unusable: empty primary falls back to take(3). */
    fun splitPrimaryOverflow(
        allDestinations: List<String>,
        template: NavigationTemplate,
    ): Pair<List<String>, List<String>> {
        val catalog = allDestinations.toSet()
        val primary = mutableListOf<String>()
        for (id in template.primaryDestinationIds) {
            if (id in catalog && id !in primary) primary.add(id)
        }
        if (primary.isEmpty()) primary.addAll(allDestinations.take(3))
        val primaryIds = primary.toSet()
        val overflow = mutableListOf<String>()
        for (id in template.overflowDestinationIds) {
            if (id in catalog && id !in primaryIds && id !in overflow) overflow.add(id)
        }
        for (id in allDestinations) {
            if (id !in primaryIds && id !in overflow) overflow.add(id)
        }
        return primary to overflow
    }

    /** Dart navItems: primary destinations plus the synthetic 'more' item
     *  when overflow exists. */
    const val MORE_ID = "more"

    fun navItemIds(primary: List<String>, overflow: List<String>): List<String> =
        if (overflow.isEmpty()) primary else primary + MORE_ID

    /** Dart: resolve the effective page index from the current destination
     *  id; an unknown id snaps to 0. Returns index + whether the stored
     *  selection was invalid (Dart resets _currentDestinationId then). */
    fun effectiveIndex(currentDestinationId: String, allDestinations: List<String>): Pair<Int, Boolean> {
        val index = allDestinations.indexOf(currentDestinationId)
        return if (index < 0) 0 to true else index to false
    }
}
