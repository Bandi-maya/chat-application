package com.example.chat.home

import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.chats.home.ChatListDensity
import com.example.chat.ui.chats.home.HomeEntry
import com.example.chat.ui.chats.home.HomeFormatters
import com.example.chat.ui.chats.home.HomeHeaderStyle
import com.example.chat.ui.chats.home.HomeStyleSpec
import com.example.chat.ui.chats.home.buildHomeEntries
import com.example.chat.ui.chats.home.chatListDensityFactor
import com.example.chat.ui.chats.home.compactBarTitle
import com.example.chat.ui.chats.home.displayConversationTitle
import com.example.chat.ui.chats.home.effectiveHomeHeaderStyle
import com.example.chat.ui.chats.home.filterHomeConversations
import com.example.chat.ui.chats.home.homeFilters
import com.example.chat.ui.chats.home.resolveSearchPlacement
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior tests written from the Dart source's observable rules
 *  (chats_home_screen.dart build() + chat_formatters.dart). */
class HomeEntriesParityTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val now: Instant = Instant.parse("2026-10-25T12:00:00Z") // 17:30 IST

    private fun at(iso: String): Instant = Instant.parse(iso)

    private fun conv(
        id: String,
        title: String = "Chat $id",
        type: ConversationType = ConversationType.direct,
        pinned: Boolean = false,
        archived: Boolean = false,
        muted: Boolean = false,
        unread: Int = 0,
        lastText: String = "hello",
        roomType: String? = null,
        taskId: String? = null,
        participants: List<String> = listOf("me", "other-$id"),
    ) = Conversation(
        id = id,
        type = type,
        title = title,
        participantIds = participants,
        lastMessageText = lastText,
        lastMessageTime = now,
        lastMessageSenderId = "other-$id",
        unreadCount = unread,
        isPinned = pinned,
        isArchived = archived,
        isMuted = muted,
        roomType = roomType,
        taskId = taskId,
    )

    // ---- chat_formatters.dart ----

    @Test fun clockTimeFormats12HourWithAmPm() {
        assertEquals("5:30 PM", HomeFormatters.formatClockTime(now, zone))
        assertEquals("12:05 PM", HomeFormatters.formatClockTime(at("2026-10-25T06:35:00Z"), zone)) // 12:05 IST
        assertEquals("12:30 AM", HomeFormatters.formatClockTime(at("2026-10-24T19:00:00Z"), zone)) // 00:30 IST
    }

    @Test fun conversationTimestampRules() {
        assertEquals("5:30 PM", HomeFormatters.formatConversationTimestamp(now, now, zone))
        assertEquals("Yesterday", HomeFormatters.formatConversationTimestamp(at("2026-10-24T12:00:00Z"), now, zone))
        // 2026-10-21 was a Wednesday, within 7 days.
        assertEquals("Wednesday", HomeFormatters.formatConversationTimestamp(at("2026-10-21T12:00:00Z"), now, zone))
        assertEquals("10/10/2026", HomeFormatters.formatConversationTimestamp(at("2026-10-10T12:00:00Z"), now, zone))
    }

    @Test fun elapsedTimeBuckets() {
        assertEquals("just now", HomeFormatters.formatElapsedTime(now.minusSeconds(30), now))
        assertEquals("just now", HomeFormatters.formatElapsedTime(now.plusSeconds(90), now))
        assertEquals("5m ago", HomeFormatters.formatElapsedTime(now.minusSeconds(300), now))
        assertEquals("2h ago", HomeFormatters.formatElapsedTime(now.minusSeconds(7200), now))
        assertEquals("3d ago", HomeFormatters.formatElapsedTime(now.minusSeconds(3 * 86400), now))
        assertEquals("2w ago", HomeFormatters.formatElapsedTime(now.minusSeconds(14 * 86400), now))
        assertEquals("2mo ago", HomeFormatters.formatElapsedTime(now.minusSeconds(61 * 86400L), now))
        assertEquals("1y ago", HomeFormatters.formatElapsedTime(now.minusSeconds(400 * 86400L), now))
    }

    @Test fun lastSeenRules() {
        assertEquals("", HomeFormatters.formatLastSeen(null, now, zone))
        assertEquals("last seen today at 5:30 PM", HomeFormatters.formatLastSeen(now, now, zone))
        assertEquals(
            "last seen yesterday at 5:30 PM",
            HomeFormatters.formatLastSeen(at("2026-10-24T12:00:00Z"), now, zone),
        )
        assertEquals(
            "last seen 10/10/2026 at 5:30 PM",
            HomeFormatters.formatLastSeen(at("2026-10-10T12:00:00Z"), now, zone),
        )
    }

    @Test fun archivedTimeRules() {
        assertEquals("17:30", HomeFormatters.formatArchivedTime(now, now, zone))
        assertEquals("Yesterday", HomeFormatters.formatArchivedTime(at("2026-10-24T12:00:00Z"), now, zone))
        assertEquals("10/10", HomeFormatters.formatArchivedTime(at("2026-10-10T12:00:00Z"), now, zone))
    }

    // ---- filter chips ----

    @Test fun filterChipSets() {
        assertEquals(listOf("All", "Unread", "Groups", "Direct", "Vault"), homeFilters(null))
        assertEquals(listOf("All", "Unread", "Vault"), homeFilters(ConversationType.group))
    }

    // ---- build() where() chain ----

    @Test fun filteringMatchesDartChain() {
        val list = listOf(
            conv("a", title = "Alice"),
            conv("t", title = "Task room", roomType = "task"),
            conv("g", title = "Team", type = ConversationType.group),
            conv("u", title = "Bob", unread = 3),
            conv("h", title = "Hidden"),
        )
        val noHidden: (String) -> Boolean = { false }
        // Tasks always excluded.
        assertEquals(4, filterHomeConversations(list, "", "All", null, noHidden).size)
        // Hidden locked chats excluded.
        assertEquals(
            3,
            filterHomeConversations(list, "", "All", null) { it == "h" }.size,
        )
        // forcedType enforced.
        assertEquals(
            listOf("g"),
            filterHomeConversations(list, "", "All", ConversationType.group, noHidden).map { it.id },
        )
        // Query matches title or last message, case-insensitive.
        assertEquals(
            listOf("a"),
            filterHomeConversations(list, "ALIC", "All", null, noHidden).map { it.id },
        )
        assertEquals(
            listOf("g"),
            filterHomeConversations(listOf(conv("g", lastText = "standup notes")), "notes", "All", null, noHidden).map { it.id },
        )
        // Chips.
        assertEquals(listOf("u"), filterHomeConversations(list, "", "Unread", null, noHidden).map { it.id })
        assertEquals(listOf("g"), filterHomeConversations(list, "", "Groups", null, noHidden).map { it.id })
        assertEquals(3, filterHomeConversations(list, "", "Direct", null, noHidden).size)
    }

    // ---- section ordering ----

    private fun specFor(style: String, raw: String = "") = HomeStyleSpec.resolve(style, raw)

    @Test fun entriesPinnedSectionThenMessages() {
        val list = listOf(
            conv("p", pinned = true),
            conv("r1"),
            conv("r2"),
        )
        val entries = buildHomeEntries(
            list, "Chaty Default", specFor("Chaty Default"),
            isSearchOpen = false, hideArchivedChats = false,
            gbHideArchiveHome = false, archiveChatsOnTop = true,
        )
        assertEquals(
            listOf(
                HomeEntry.Section("PINNED"),
                HomeEntry.Item(list[0]),
                HomeEntry.Section("MESSAGES"),
                HomeEntry.Item(list[1]),
                HomeEntry.Item(list[2]),
            ),
            entries,
        )
    }

    @Test fun entriesNoSectionsWhenNothingPinnedAndNotClassic() {
        val list = listOf(conv("r1"), conv("r2"))
        val entries = buildHomeEntries(
            list, "Chaty Default", specFor("Chaty Default"),
            isSearchOpen = false, hideArchivedChats = false,
            gbHideArchiveHome = false, archiveChatsOnTop = true,
        )
        assertEquals(listOf(HomeEntry.Item(list[0]), HomeEntry.Item(list[1])), entries)
    }

    @Test fun entriesClassicAlwaysLabelsMessages() {
        val list = listOf(conv("r1"))
        val entries = buildHomeEntries(
            list, "Classic", specFor("Classic"),
            isSearchOpen = false, hideArchivedChats = false,
            gbHideArchiveHome = false, archiveChatsOnTop = true,
        )
        assertEquals(listOf(HomeEntry.Section("MESSAGES"), HomeEntry.Item(list[0])), entries)
    }

    @Test fun entriesMinimalHidesSections() {
        val list = listOf(conv("p", pinned = true), conv("r1"))
        val entries = buildHomeEntries(
            list, "Minimal", specFor("Minimal"),
            isSearchOpen = false, hideArchivedChats = false,
            gbHideArchiveHome = false, archiveChatsOnTop = true,
        )
        assertEquals(listOf(HomeEntry.Item(list[0]), HomeEntry.Item(list[1])), entries)
    }

    @Test fun entriesProductivityUnreadFirst() {
        val list = listOf(
            conv("p", pinned = true, unread = 2),
            conv("u", unread = 5),
            conv("r1"),
        )
        val entries = buildHomeEntries(
            list, "Productivity", specFor("Productivity"),
            isSearchOpen = false, hideArchivedChats = false,
            gbHideArchiveHome = false, archiveChatsOnTop = true,
        )
        assertEquals(
            listOf(
                HomeEntry.Section("PINNED"), HomeEntry.Item(list[0]),
                HomeEntry.Section("UNREAD"), HomeEntry.Item(list[1]),
                HomeEntry.Section("MESSAGES"), HomeEntry.Item(list[2]),
            ),
            entries,
        )
    }

    @Test fun archivedEntryPlacementAndSuppression() {
        val list = listOf(conv("a", archived = true), conv("r1"))
        // On top when archiveChatsOnTop.
        val top = buildHomeEntries(
            list, "Chaty Default", specFor("Chaty Default"),
            isSearchOpen = false, hideArchivedChats = false,
            gbHideArchiveHome = false, archiveChatsOnTop = true,
        )
        assertEquals(HomeEntry.Archived(1), top.first())
        // At the end otherwise.
        val bottom = buildHomeEntries(
            list, "Chaty Default", specFor("Chaty Default"),
            isSearchOpen = false, hideArchivedChats = false,
            gbHideArchiveHome = false, archiveChatsOnTop = false,
        )
        assertEquals(HomeEntry.Archived(1), bottom.last())
        // Hidden while searching, when pref hides archived, or GB key hides it.
        assertTrue(
            buildHomeEntries(list, "Chaty Default", specFor("Chaty Default"), true, false, false, true)
                .none { it is HomeEntry.Archived },
        )
        assertTrue(
            buildHomeEntries(list, "Chaty Default", specFor("Chaty Default"), false, true, false, true)
                .none { it is HomeEntry.Archived },
        )
        assertTrue(
            buildHomeEntries(list, "Chaty Default", specFor("Chaty Default"), false, false, true, true)
                .none { it is HomeEntry.Archived },
        )
    }

    // ---- style spec formulas ----

    @Test fun styleSpecMatchesDartFormulas() {
        val ig = specFor("Stories First", "instagram style")
        assertEquals(118.0, ig.storiesHeight, 0.0)
        assertEquals(68.0, ig.storiesAvatar, 0.0)
        assertEquals(1.04, ig.tileDensity, 0.0)
        assertTrue(ig.cardRows)
        assertTrue(ig.storiesFirst)

        val tg = specFor("Chaty Default", "telegram style")
        assertEquals(0.93, tg.tileDensity, 0.0)
        assertEquals(82.0, tg.storiesHeight, 0.0)

        val compact = specFor("Compact")
        assertEquals(0.88, compact.tileDensity, 0.0)
        assertTrue(compact.storiesHidden)

        val expressive = specFor("Expressive")
        assertEquals(1.12, expressive.tileDensity, 0.0)
        assertEquals(96.0, expressive.storiesHeight, 0.0)

        assertTrue(specFor("Tablet Split View").splitView)
        assertTrue(specFor("Classic").alwaysLabelSections)
        assertTrue(!specFor("Minimal").showSections)
    }

    // ---- tile title ----

    @Test fun unsavedNumberTitles() {
        val numbered = conv(
            "n",
            title = "+91 99516 72843",
            participants = listOf("me", "u1"),
        )
        assertEquals("+91 99516 72843", displayConversationTitle(numbered, false, "me", emptySet()))
        assertEquals("Unknown contact", displayConversationTitle(numbered, true, "me", emptySet()))
        assertEquals("+91 99516 72843", displayConversationTitle(numbered, true, "me", setOf("u1")))
        // Named titles and groups are untouched.
        assertEquals("Alice", displayConversationTitle(conv("a", title = "Alice"), true, "me", emptySet()))
        assertEquals(
            "+91 99516 72843",
            displayConversationTitle(
                conv("g", title = "+91 99516 72843", type = ConversationType.group), true, "me", emptySet(),
            ),
        )
    }

    // ---- compact bar title ----

    @Test fun compactBarTitleRules() {
        // Classic with no page title is 'WhatsApp'.
        assertEquals(
            "WhatsApp",
            compactBarTitle("Classic", HomeHeaderStyle.prominentIdentity, null, true, "", "Pavan", "pavan", true),
        )
        // Placeholder names fall back to username.
        assertEquals(
            "pavan",
            compactBarTitle("Chaty Default", HomeHeaderStyle.compact, null, true, "", "Bandi Maya", "pavan", true),
        )
        // Override wins when it is not a placeholder.
        assertEquals(
            "Boss",
            compactBarTitle("Chaty Default", HomeHeaderStyle.compact, null, true, "Boss", "Pavan", "pavan", true),
        )
        // setMyName off is always 'Chaty'.
        assertEquals(
            "Chaty",
            compactBarTitle("Chaty Default", HomeHeaderStyle.compact, null, false, "Boss", "Pavan", "pavan", true),
        )
        // searchForward falls back to 'Search chats'.
        assertEquals(
            "Search chats",
            compactBarTitle("Productivity", HomeHeaderStyle.searchForward, null, true, "", "Pavan", "pavan", true),
        )
        // pageTitle wins for compact; storiesFirst shows identity while the
        // large title is expanded.
        assertEquals(
            "Chats",
            compactBarTitle("Compact", HomeHeaderStyle.compact, "Chats", true, "", "Pavan", "pavan", true),
        )
        assertEquals(
            "Pavan",
            compactBarTitle("Stories First", HomeHeaderStyle.storiesFirst, "Chats", true, "", "Pavan", "pavan", true),
        )
        assertEquals(
            "Chats",
            compactBarTitle("Stories First", HomeHeaderStyle.storiesFirst, "Chats", true, "", "Pavan", "pavan", false),
        )
    }

    // ---- header style + search placement ----

    @Test fun headerStyleMapping() {
        assertEquals(
            HomeHeaderStyle.storiesFirst,
            effectiveHomeHeaderStyle("instagram style", "Chaty Default", HomeHeaderStyle.compact),
        )
        assertEquals(
            HomeHeaderStyle.compact,
            effectiveHomeHeaderStyle("telegram style", "Chaty Default", HomeHeaderStyle.prominentIdentity),
        )
        assertEquals(
            HomeHeaderStyle.prominentIdentity,
            effectiveHomeHeaderStyle("classic", "Classic", HomeHeaderStyle.compact),
        )
        assertEquals(
            HomeHeaderStyle.searchForward,
            effectiveHomeHeaderStyle("productivity", "Productivity", HomeHeaderStyle.compact),
        )
        // Unknown styles fall back to the component template's header.
        assertEquals(
            HomeHeaderStyle.searchForward,
            effectiveHomeHeaderStyle("something new", "Unknown", HomeHeaderStyle.searchForward),
        )
    }

    @Test fun searchPlacementLegacyFallback() {
        assertEquals("Header action", resolveSearchPlacement("Header action", false))
        assertEquals("Below header", resolveSearchPlacement("below header", false))
        assertEquals("Above chat filters", resolveSearchPlacement("Above chat filters", false))
        // Disabled header shortcut without a placement keeps search below header.
        assertEquals("Below header", resolveSearchPlacement("Header action", true))
        assertEquals("Below header", resolveSearchPlacement("Below header", true))
    }

    @Test fun densityFactors() {
        assertEquals(0.92, chatListDensityFactor(ChatListDensity.compact), 0.0)
        assertEquals(1.0, chatListDensityFactor(ChatListDensity.regular), 0.0)
        assertEquals(1.08, chatListDensityFactor(ChatListDensity.comfortable), 0.0)
    }
}
