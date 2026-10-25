package com.example.chat

import com.example.chat.core.templates.ChatyNavigationDestinationIds
import com.example.chat.ui.settings.navigation.NavigationDestinationsLogic
import com.example.chat.ui.settings.navigation.SettingsSearchLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior tests derived from navigation_destinations_settings_screen.dart
 *  and settings_search_delegate.dart (api branch anchor 3bee780). */
class NavigationSettingsParityTest {

    private data class Item(
        val title: String,
        val category: String,
        val description: String,
        val keywords: List<String> = emptyList(),
    )

    private val allIds = ChatyNavigationDestinationIds.all

    // ---- reconcile (_loadCurrentConfiguration) ----

    @Test
    fun reconcileFullyAssignedPassesThrough() {
        val (p, o) = NavigationDestinationsLogic.reconcile(
            listOf("chats", "groups", "calls"),
            listOf("updates", "tasks", "settings", "desktop"),
        )
        assertEquals(listOf("chats", "groups", "calls"), p)
        assertEquals(listOf("updates", "tasks", "settings", "desktop"), o)
    }

    @Test
    fun reconcileAppendsUnassignedKnownIdsToOverflow() {
        val (p, o) = NavigationDestinationsLogic.reconcile(listOf("chats"), emptyList())
        assertEquals(listOf("chats"), p)
        assertEquals(allIds.filter { it != "chats" }, o)
    }

    @Test
    fun reconcileDropsUnknownIdsAndKeepsOrder() {
        val (p, o) = NavigationDestinationsLogic.reconcile(
            listOf("chats", "mystery"),
            listOf("calls", "ghost", "groups"),
        )
        assertEquals(listOf("chats"), p)
        // unknown labels removed; missing known ids appended in `all` order
        assertEquals(listOf("calls", "groups", "updates", "tasks", "settings", "desktop"), o)
    }

    // ---- move guards ----

    @Test
    fun moveToOverflowBlockedAtOnePrimary() {
        assertFalse(NavigationDestinationsLogic.canMoveToOverflow(listOf("chats"), "chats"))
        val p = mutableListOf("chats")
        val o = mutableListOf<String>()
        NavigationDestinationsLogic.moveToOverflow(p, o, "chats")
        assertEquals(listOf("chats"), p)
        assertTrue(o.isEmpty())
    }

    @Test
    fun moveToOverflowBlockedWhenNotPrimary() {
        assertFalse(NavigationDestinationsLogic.canMoveToOverflow(listOf("chats", "calls"), "groups"))
    }

    @Test
    fun moveToOverflowMovesId() {
        val p = mutableListOf("chats", "calls")
        val o = mutableListOf("updates")
        NavigationDestinationsLogic.moveToOverflow(p, o, "calls")
        assertEquals(listOf("chats"), p)
        assertEquals(listOf("updates", "calls"), o)
    }

    @Test
    fun moveToPrimaryBlockedAtFourPrimaries() {
        val p = listOf("chats", "groups", "updates", "tasks")
        assertFalse(NavigationDestinationsLogic.canMoveToPrimary(p, listOf("calls"), "calls"))
    }

    @Test
    fun moveToPrimaryBlockedWhenNotInOverflow() {
        assertFalse(NavigationDestinationsLogic.canMoveToPrimary(listOf("chats"), listOf("calls"), "chats"))
    }

    @Test
    fun moveToPrimaryMovesId() {
        val p = mutableListOf("chats")
        val o = mutableListOf("updates", "calls")
        NavigationDestinationsLogic.moveToPrimary(p, o, "updates")
        assertEquals(listOf("chats", "updates"), p)
        assertEquals(listOf("calls"), o)
    }

    // ---- reorder ----

    @Test
    fun reorderSwapsWithinBounds() {
        val list = mutableListOf("a", "b", "c")
        NavigationDestinationsLogic.reorder(list, 0, 1)
        assertEquals(listOf("b", "a", "c"), list)
        NavigationDestinationsLogic.reorder(list, 2, -1)
        assertEquals(listOf("b", "c", "a"), list)
    }

    @Test
    fun reorderNoOpAtBounds() {
        val list = mutableListOf("a", "b")
        NavigationDestinationsLogic.reorder(list, 0, -1)
        assertEquals(listOf("a", "b"), list)
        NavigationDestinationsLogic.reorder(list, 1, 1)
        assertEquals(listOf("a", "b"), list)
    }

    // ---- visibility & labels ----

    @Test
    fun desktopRowHiddenOthersVisible() {
        for (id in allIds) {
            assertEquals(id != "desktop", NavigationDestinationsLogic.isRowVisible(id))
        }
    }

    @Test
    fun labelsCoverEveryKnownDestination() {
        assertEquals(allIds.toSet(), NavigationDestinationsLogic.labels.keys)
        assertEquals("Linked devices", NavigationDestinationsLogic.labels["desktop"])
        assertEquals("Chats", NavigationDestinationsLogic.labels["chats"])
    }

    // ---- search filter (settings_search_delegate.dart) ----

    private fun search(query: String, items: List<Item>): List<Item> =
        SettingsSearchLogic.filter(query, items, { it.title }, { it.category }, { it.description }, { it.keywords })

    private val items = listOf(
        Item("Navigation destinations", "Templates", "Choose primary destinations", listOf("nav", "bar")),
        Item("Appearance", "Interface", "Theme and colors", listOf("dark", "light")),
        Item("Privacy", "Account", "Blocked contacts", emptyList()),
    )

    @Test
    fun emptyQueryReturnsAll() {
        assertEquals(items, search("", items))
        assertEquals(items, search("   ", items))
    }

    @Test
    fun titleMatchCaseInsensitive() {
        assertEquals(listOf(items[0]), search("NAVIGATION", items))
    }

    @Test
    fun categoryMatch() {
        assertEquals(listOf(items[1]), search("interface", items))
    }

    @Test
    fun descriptionMatch() {
        assertEquals(listOf(items[2]), search("blocked", items))
    }

    @Test
    fun keywordMatch() {
        assertEquals(listOf(items[0]), search("bar", items))
        assertEquals(listOf(items[1]), search("Dark", items))
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertTrue(search("zzzz", items).isEmpty())
    }
}
