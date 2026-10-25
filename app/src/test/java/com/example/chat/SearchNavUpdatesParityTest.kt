package com.example.chat

import com.example.chat.data.services.StatusRecord
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.core.templates.AppearanceVariantController
import com.example.chat.core.templates.ChatyNavigationDestinationIds
import com.example.chat.core.templates.ChatyTemplateId
import com.example.chat.core.templates.ChatyTemplateRegistry
import com.example.chat.core.templates.NavigationLayoutType
import com.example.chat.core.templates.NavigationTemplate
import com.example.chat.core.templates.TemplateController
import com.example.chat.core.templates.TemplateStateStore
import com.example.chat.ui.navigation.NavigationCatalog
import com.example.chat.ui.search.LocalConversationSearch
import com.example.chat.ui.search.SearchRequestGuard
import com.example.chat.ui.updates.StatusViewSession
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRequestGuardParityTest {

    @Test fun monotonicIdsAndCurrency() {
        val guard = SearchRequestGuard()
        val first = guard.begin()
        assertTrue(guard.isCurrent(first))
        val second = guard.begin()
        assertFalse(guard.isCurrent(first))
        assertTrue(guard.isCurrent(second))
    }

    @Test fun deduplicatePreservesBackendOrdering() {
        val guard = SearchRequestGuard()
        val result = guard.deduplicate(listOf("a", "b", "a", "c", "b")) { it }
        assertEquals(listOf("a", "b", "c"), result)
    }

    private fun conversation(id: String, title: String, lastMessage: String) = Conversation(
        id = id,
        type = ConversationType.direct,
        title = title,
        participantIds = listOf("me", "other"),
        lastMessageText = lastMessage,
        lastMessageTime = Instant.now(),
        lastMessageSenderId = "other",
    )

    @Test fun localFilterMatchesTitleAndLastMessageAndExcludesHidden() {
        val conversations = listOf(
            conversation("c1", "Bandi", "hello"),
            conversation("c2", "Work", "invoice attached"),
            conversation("c3", "Locked", "invoice secret"),
        )
        val hidden = { id: String -> id == "c3" }
        val byMessage = LocalConversationSearch.filter(conversations, "INVOICE", hidden)
        assertEquals(listOf("c2"), byMessage.map { it.id })
        val byTitle = LocalConversationSearch.filter(conversations, "ban", hidden)
        assertEquals(listOf("c1"), byTitle.map { it.id })
        assertTrue(LocalConversationSearch.filter(conversations, "", hidden).isEmpty())
    }
}

class NavigationDestinationsParityTest {

    private val template = ChatyTemplateRegistry.messageFirst.navigation

    @Test fun defaultTemplateIsMessageFirstRegistryValues() {
        assertEquals(NavigationLayoutType.floatingPill, template.layout)
        assertEquals(listOf("chats", "updates", "calls"), template.primaryDestinationIds)
        assertEquals(listOf("tasks", "settings", "desktop"), template.overflowDestinationIds)
        assertEquals(64.0, template.height, 0.0)
        assertEquals("Floating Pill", template.bottomBarStyleName)
        assertFalse(template.hasCenterAction)
    }

    @Test fun catalogInsertsGroupsOnlyWhenSeparatedOrCustom() {
        assertEquals(
            listOf("chats", "updates", "tasks", "calls", "settings"),
            NavigationCatalog.catalogIds(false, false, hideTasks = false, hideUpdates = false),
        )
        assertEquals(
            listOf("chats", "groups", "updates", "tasks", "calls", "settings"),
            NavigationCatalog.catalogIds(true, false, hideTasks = false, hideUpdates = false),
        )
        assertEquals(
            listOf("chats", "groups", "updates", "tasks", "calls", "settings"),
            NavigationCatalog.catalogIds(false, true, hideTasks = false, hideUpdates = false),
        )
    }

    @Test fun gbBoolRemovalsDropTasksAndUpdates() {
        assertEquals(
            listOf("chats", "updates", "calls", "settings"),
            NavigationCatalog.catalogIds(false, false, hideTasks = true, hideUpdates = false),
        )
        assertEquals(
            listOf("chats", "tasks", "calls", "settings"),
            NavigationCatalog.catalogIds(false, false, hideTasks = false, hideUpdates = true),
        )
    }

    @Test fun templateOrderingThenCatalogRemainder() {
        val catalog = listOf("chats", "updates", "tasks", "calls", "settings")
        // Template lists 'desktop' which has no catalog entry: skipped.
        assertEquals(
            listOf("chats", "updates", "calls", "tasks", "settings"),
            NavigationCatalog.resolveOrdered(catalog, template),
        )
        // A template missing 'calls' leaves it at the end in catalog order.
        val weird = template.copy(
            primaryDestinationIds = listOf("settings", "chats"),
            overflowDestinationIds = listOf("tasks"),
        )
        assertEquals(
            listOf("settings", "chats", "tasks", "updates", "calls"),
            NavigationCatalog.resolveOrdered(catalog, weird),
        )
    }

    @Test fun primaryOverflowSplitAndFallback() {
        val all = listOf("chats", "updates", "calls", "tasks", "settings")
        val (primary, overflow) = NavigationCatalog.splitPrimaryOverflow(all, template)
        assertEquals(listOf("chats", "updates", "calls"), primary)
        assertEquals(listOf("tasks", "settings"), overflow)

        // Malformed template cannot leave navigation unusable.
        val malformed = NavigationTemplate(
            layout = NavigationLayoutType.floatingPill,
            primaryDestinationIds = emptyList(),
            bottomBarStyleName = "Floating Pill",
        )
        val (fallbackPrimary, fallbackOverflow) = NavigationCatalog.splitPrimaryOverflow(all, malformed)
        assertEquals(listOf("chats", "updates", "calls"), fallbackPrimary)
        assertEquals(listOf("tasks", "settings"), fallbackOverflow)
    }

    @Test fun navItemsAppendMoreWhenOverflow() {
        assertEquals(
            listOf("chats", "updates", "calls"),
            NavigationCatalog.navItemIds(listOf("chats", "updates", "calls"), emptyList()),
        )
        assertEquals(
            listOf("chats", "updates", "calls", "more"),
            NavigationCatalog.navItemIds(listOf("chats", "updates", "calls"), listOf("tasks")),
        )
    }

    @Test fun effectiveIndexSnapsUnknownToZero() {
        val all = listOf("chats", "updates", "calls")
        assertEquals(1 to false, NavigationCatalog.effectiveIndex("updates", all))
        assertEquals(0 to true, NavigationCatalog.effectiveIndex("gone", all))
    }

    @Test fun destinationIdsKnownSet() {
        assertTrue(ChatyNavigationDestinationIds.known.contains("desktop"))
        assertEquals("Tasks", NavigationCatalog.labelFor("tasks"))
    }
}

class StatusViewSessionParityTest {

    private fun record(
        id: String = "s1",
        userId: String = "owner",
        expiresAt: Instant = Instant.now().plusSeconds(3600),
        deletedAt: Instant? = null,
    ) = StatusRecord(
        id = id,
        userId = userId,
        text = "",
        mediaType = "text",
        mediaPath = null,
        mediaName = null,
        mediaSize = 0,
        durationSeconds = 0,
        createdAt = Instant.now(),
        expiresAt = expiresAt,
        deletedAt = deletedAt,
    )

    @Test fun tryBeginRulesMatchDart() {
        val session = StatusViewSession()
        val now = Instant.now()
        // Happy path once, then duplicate rejected.
        assertTrue(session.tryBegin(record(), "viewer", now))
        assertFalse(session.tryBegin(record(), "viewer", now))
        // Empty ids rejected.
        assertFalse(session.tryBegin(record(id = ""), "viewer", now))
        assertFalse(session.tryBegin(record(), "", now))
        // Own status never records a view.
        assertFalse(session.tryBegin(record(), "owner", now))
        // Deleted and expired rejected.
        assertFalse(session.tryBegin(record(deletedAt = now), "viewer", now))
        assertFalse(session.tryBegin(record(expiresAt = now.minusSeconds(1)), "viewer", now))
    }

    @Test fun rollbackReleasesThePair() {
        val session = StatusViewSession()
        val status = record()
        assertTrue(session.tryBegin(status, "viewer"))
        session.rollback(status, "viewer")
        assertTrue(session.tryBegin(status, "viewer"))
    }
}

class NavigationTemplateRegistryParityTest {

    @Test fun allSixCanonicalRegistryTemplatesMatchDartRegistry() {
        for (id in ChatyTemplateId.values()) {
            val t = ChatyTemplateRegistry.get(id).navigation
            assertEquals(listOf("chats", "updates", "calls"), t.primaryDestinationIds)
            assertEquals(listOf("tasks", "settings", "desktop"), t.overflowDestinationIds)
        }
        val vs = ChatyTemplateRegistry.get(ChatyTemplateId.visualSocial).navigation
        assertEquals(NavigationLayoutType.floatingPill, vs.layout)
        assertEquals(68.0, vs.height, 0.0)
        assertEquals("Floating Pill", vs.bottomBarStyleName)

        val stream = ChatyTemplateRegistry.get(ChatyTemplateId.stream).navigation
        assertEquals(NavigationLayoutType.flatTabs, stream.layout)
        assertEquals(56.0, stream.height, 0.0)
        assertEquals("Classic Label Bar", stream.bottomBarStyleName)

        val camera = ChatyTemplateRegistry.get(ChatyTemplateId.cameraFirst).navigation
        assertEquals(NavigationLayoutType.centerActionDock, camera.layout)
        assertEquals(70.0, camera.height, 0.0)
        assertTrue(camera.hasCenterAction)
        assertEquals("camera", camera.centerActionId)
        assertEquals("camera_alt_rounded", camera.centerActionIcon)
        assertEquals("Active Pill Chip", camera.bottomBarStyleName)

        val messageFirst = ChatyTemplateRegistry.get(ChatyTemplateId.messageFirst).navigation
        assertEquals(NavigationLayoutType.floatingPill, messageFirst.layout)
        assertEquals(64.0, messageFirst.height, 0.0)
        assertNull(messageFirst.centerActionIcon)

        val powerChat = ChatyTemplateRegistry.get(ChatyTemplateId.powerChat).navigation
        assertEquals(NavigationLayoutType.utilityBar, powerChat.layout)
        assertEquals(60.0, powerChat.height, 0.0)
        assertEquals("Segmented Glass Dock", powerChat.bottomBarStyleName)

        val community = ChatyTemplateRegistry.get(ChatyTemplateId.community).navigation
        assertEquals(NavigationLayoutType.identityBar, community.layout)
        assertEquals(66.0, community.height, 0.0)
        assertEquals("Active Pill Chip", community.bottomBarStyleName)
    }

    private val noopStore = object : TemplateStateStore {
        override suspend fun load(): Map<String, Any?> = emptyMap()
        override suspend fun save(data: Map<String, Any?>): Boolean = true
    }

    @Test fun controllerNavigationKeepsBaseWhenNoOverridesAndAppliesPerListOverrides() {
        val controller = TemplateController(noopStore)
        val base = ChatyTemplateRegistry.messageFirst.navigation
        assertEquals(base, controller.navigation)
        val custom = TemplateController(noopStore)
        // Simulate a persisted destination override (config is immutable; apply via copy path).
        val field = TemplateController::class.java.getDeclaredField("config")
        field.isAccessible = true
        field.set(custom, com.example.chat.core.templates.UserTemplateConfiguration(
            navigationPrimaryDestinationIds = listOf("chats", "tasks"),
        ))
        assertEquals(listOf("chats", "tasks"), custom.navigation.primaryDestinationIds)
        assertEquals(base.overflowDestinationIds, custom.navigation.overflowDestinationIds)
    }

    @Test fun layoutToModeMappingMatchesTemplateController() {
        assertEquals(com.example.chat.core.theme.AppNavigationMode.bottomNav,
            com.example.chat.ui.navigation.navigationModeForLayout(com.example.chat.core.templates.NavigationLayoutType.flatTabs))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.bottomNav,
            com.example.chat.ui.navigation.navigationModeForLayout(com.example.chat.core.templates.NavigationLayoutType.floatingPill))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.bottomNav,
            com.example.chat.ui.navigation.navigationModeForLayout(com.example.chat.core.templates.NavigationLayoutType.centerActionDock))
        // Dart: compactRail; Kotlin enum lacks compactRail, bottomNav fallback.
        assertEquals(com.example.chat.core.theme.AppNavigationMode.bottomNav,
            com.example.chat.ui.navigation.navigationModeForLayout(com.example.chat.core.templates.NavigationLayoutType.utilityBar))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.topWhatsAppBar,
            com.example.chat.ui.navigation.navigationModeForLayout(com.example.chat.core.templates.NavigationLayoutType.identityBar))
    }
}

class HomePresetNormalizerParityTest {

    @Test fun navigationModeResolvesEveryDartCase() {
        val n = com.example.chat.ui.navigation.HomePresetNormalizer::navigationMode
        assertEquals(com.example.chat.core.theme.AppNavigationMode.topWhatsAppBar, n("Classic"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.topWhatsAppBar, n("whatsapp-style top bar"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.oneUi, n("Cards"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.oneUi, n("one ui"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.bottomNav, n("Chaty Default"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.bottomNav, n("WhatsApp UI Stock"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.iosStyle, n("Stories First"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.instagramStyle, n("Instagram Style"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.telegramStyle, n("Telegram Style"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.floatingIslandRail, n("Floating Rail"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.modernSideMenu, n("Modern Side Menu"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.gestureTabs, n("Compact"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.basicTabStyle, n("Productivity"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.perspective3DDrawer, n("3D Perspective Drawer"))
        assertEquals(com.example.chat.core.theme.AppNavigationMode.curvedRadialDrawer, n("Curved Radial Menu"))
        assertNull(n("Some Custom Preset"))
    }

    @Test fun homeStyleCanonicalizesAliases() {
        val h = com.example.chat.ui.navigation.HomePresetNormalizer::homeStyle
        assertEquals("Chaty Default", h("chaty"))
        assertEquals("Classic", h("whatsapp old ui"))
        assertEquals("Compact", h("bubbles tab style"))
        assertEquals("Stories First", h("ios style"))
        assertEquals("Productivity", h("basic tab style"))
        assertEquals("Classic", h("whatsapp-style top bar"))
        assertEquals("Stories First", h("floating rail"))
        assertEquals("Chaty Default", h("totally unknown"))
    }
}

class AppearanceVariantControllerParityTest {

    @Test fun canonicalTablesMatchDartController() {
        assertEquals(20, AppearanceVariantController.navigationStyles.size)
        assertEquals(12, AppearanceVariantController.bottomBarStyles.size)
        assertEquals("Adaptive Rail", AppearanceVariantController.navigationStyles.first())
        assertEquals("Focus Tabs", AppearanceVariantController.navigationStyles.last())
        assertEquals("Floating Pill", AppearanceVariantController.bottomBarStyles.first())
        assertEquals("Soft Square Tile", AppearanceVariantController.bottomBarStyles.last())
    }

    private class FakeStore(private val values: Map<String, String?>) : com.example.chat.core.templates.AppearanceStore {
        override suspend fun getString(key: String): String? = values[key]
        override suspend fun setString(key: String, value: String): Boolean = true
    }

    @Test fun validatedFallsBackToFirstAndIndexesClamp() = kotlinx.coroutines.runBlocking {
        val junk = AppearanceVariantController(FakeStore(mapOf(
            "appearance.navigation" to "junk",
            "appearance.bottomBar" to null,
        )))
        junk.load()
        assertEquals("Adaptive Rail", junk.navigationStyle)
        assertEquals(0, junk.navigationIndex)
        assertEquals("Floating Pill", junk.bottomBarStyle)
        assertEquals(0, junk.bottomBarIndex)

        val set = AppearanceVariantController(FakeStore(mapOf(
            "appearance.navigation" to "Focus Tabs",
            "appearance.bottomBar" to "Soft Square Tile",
        )))
        set.load()
        assertEquals(19, set.navigationIndex)
        assertEquals(11, set.bottomBarIndex)
        Unit
    }
}

class ChatyIconNavMappingParityTest {

    /** drop9 chatyIconIdForNavDestination maps each known destination id to
     *  the ChatyIconId enum entry of the same name; the mapping function
     *  itself lives in the owner's Compose file, so this guards the enum
     *  contract it depends on (drift here breaks the shell's icon lookup). */
    @Test fun everyKnownDestinationHasSameNamedRegistryIcon() {
        for (id in com.example.chat.core.templates.ChatyNavigationDestinationIds.all) {
            try {
                com.example.chat.ui.icons.ChatyIconId.valueOf(id)
            } catch (e: IllegalArgumentException) {
                throw AssertionError("ChatyIconId missing entry for destination '$id'", e)
            }
        }
        // 'more' and unknown ids intentionally have no registry mapping
        // (null keeps the template glyph in the shell).
        assertFalse(com.example.chat.core.templates.ChatyNavigationDestinationIds.known.contains("more"))
    }
}
