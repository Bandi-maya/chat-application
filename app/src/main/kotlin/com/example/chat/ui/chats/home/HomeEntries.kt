package com.example.chat.ui.chats.home

import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType

/** Pure, JVM-testable port of the list-shaping half of
 *  chats_home_screen.dart's build(): conversation filtering, home-style
 *  section ordering, archived-entry placement and filter-chip sets.
 *  Compose renders these; the rules stay identical to the Dart source. */

/** List entry union: Dart used `List<Object>` of _ConversationSection /
 *  _ArchivedEntry / Conversation. */
sealed interface HomeEntry {
    data class Section(val label: String) : HomeEntry
    data class Archived(val archivedCount: Int) : HomeEntry
    data class Item(val conversation: Conversation) : HomeEntry
}

/** Resolved per-build style switches (chats_home_screen.dart lines ~470-520).
 *  [normalizedHomeStyle] is HomePresetNormalizer.homeStyle(home.homeStyle);
 *  [rawHomeUiStyle] is home.homeStyle.trim().lowercase(). */
data class HomeStyleSpec(
    val showFilters: Boolean,
    val showSections: Boolean,
    val alwaysLabelSections: Boolean,
    val storiesFirst: Boolean,
    val storiesHidden: Boolean,
    val cardRows: Boolean,
    val storiesHeight: Double,
    val storiesAvatar: Double,
    val tileDensity: Double,
    val splitView: Boolean,
) {
    companion object {
        fun resolve(normalizedHomeStyle: String, rawHomeUiStyle: String): HomeStyleSpec {
            val isInstagramUi = rawHomeUiStyle == "instagram style"
            val isIosUi = rawHomeUiStyle == "ios style"
            val isTelegramUi = rawHomeUiStyle == "telegram style"
            val homeStyle = normalizedHomeStyle
            val styleStoriesFirst = homeStyle == "Stories First" || isInstagramUi || isIosUi
            val styleCardRows = homeStyle == "Cards" || isInstagramUi
            return HomeStyleSpec(
                showFilters = true,
                showSections = homeStyle != "Minimal",
                alwaysLabelSections = homeStyle == "Classic",
                storiesFirst = styleStoriesFirst,
                storiesHidden = homeStyle == "Compact" || homeStyle == "Minimal",
                cardRows = styleCardRows,
                storiesHeight = if (isInstagramUi) 118.0 else if (isIosUi) 104.0
                else if (styleStoriesFirst) 112.0
                else if (homeStyle == "Expressive" || styleCardRows) 96.0
                else 82.0,
                storiesAvatar = if (isInstagramUi) 68.0 else if (isIosUi) 58.0
                else if (styleStoriesFirst) 64.0
                else if (homeStyle == "Expressive" || styleCardRows) 56.0
                else 48.0,
                tileDensity = if (isTelegramUi) 0.93 else if (isInstagramUi) 1.04
                else if (homeStyle == "Compact") 0.88
                else if (homeStyle == "Expressive") 1.12
                else 1.0,
                splitView = homeStyle == "Tablet Split View",
            )
        }
    }
}

/** Filter chip sets (build(): forcedType == null ? full : reduced). */
fun homeFilters(forcedType: ConversationType?): List<String> =
    if (forcedType == null) listOf("All", "Unread", "Groups", "Direct", "Vault")
    else listOf("All", "Unread", "Vault")

/** The where() chain from build(): tasks excluded, forcedType enforced,
 *  hidden locked chats excluded, search query matches title or last message,
 *  then the selected filter chip. */
fun filterHomeConversations(
    conversations: List<Conversation>,
    query: String,
    selectedFilter: String,
    forcedType: ConversationType?,
    isConversationHidden: (String) -> Boolean,
): List<Conversation> {
    val q = query.trim().lowercase()
    return conversations.filter { conversation ->
        if (conversation.isTask) return@filter false
        if (forcedType != null && conversation.type != forcedType) return@filter false
        if (isConversationHidden(conversation.id)) return@filter false
        if (q.isNotEmpty() &&
            !conversation.title.lowercase().contains(q) &&
            !conversation.lastMessageText.lowercase().contains(q)
        ) return@filter false
        when (selectedFilter) {
            "Unread" -> conversation.unreadCount > 0
            "Groups" -> conversation.type == ConversationType.group
            "Direct" -> conversation.type == ConversationType.direct
            else -> true
        }
    }
}

/** Sectioned entry list (build(): archived-on-top setting, PINNED / UNREAD /
 *  MESSAGES sections, Productivity unread-first grouping). */
fun buildHomeEntries(
    conversations: List<Conversation>,
    normalizedHomeStyle: String,
    spec: HomeStyleSpec,
    isSearchOpen: Boolean,
    hideArchivedChats: Boolean,
    gbHideArchiveHome: Boolean,
    archiveChatsOnTop: Boolean,
): List<HomeEntry> {
    val archived = conversations.filter { it.isArchived }
    val pinned = conversations.filter { it.isPinned && !it.isArchived }
    val unreadFirst = if (normalizedHomeStyle == "Productivity") {
        conversations.filter { it.unreadCount > 0 && !it.isPinned && !it.isArchived }
    } else {
        emptyList()
    }
    val recent = conversations.filter {
        !it.isPinned && !it.isArchived &&
            !(normalizedHomeStyle == "Productivity" && it.unreadCount > 0)
    }
    val entries = mutableListOf<HomeEntry>()
    val shouldShowArchived = archived.isNotEmpty() && !isSearchOpen &&
        !hideArchivedChats && !gbHideArchiveHome
    if (shouldShowArchived && archiveChatsOnTop) {
        entries.add(HomeEntry.Archived(archived.size))
    }
    if (pinned.isNotEmpty()) {
        if (spec.showSections) entries.add(HomeEntry.Section("PINNED"))
        pinned.forEach { entries.add(HomeEntry.Item(it)) }
    }
    if (unreadFirst.isNotEmpty()) {
        if (spec.showSections) entries.add(HomeEntry.Section("UNREAD"))
        unreadFirst.forEach { entries.add(HomeEntry.Item(it)) }
    }
    if (recent.isNotEmpty()) {
        val labelMessages = spec.alwaysLabelSections || pinned.isNotEmpty() || unreadFirst.isNotEmpty()
        if (spec.showSections && labelMessages) entries.add(HomeEntry.Section("MESSAGES"))
        recent.forEach { entries.add(HomeEntry.Item(it)) }
    }
    if (shouldShowArchived && !archiveChatsOnTop) {
        entries.add(HomeEntry.Archived(archived.size))
    }
    return entries
}

/** _displayConversationTitle: with `hideUnsavedNumbers` on, a direct chat
 *  whose title still looks like a raw phone number shows 'Unknown contact'
 *  unless the other participant is a known contact. */
fun displayConversationTitle(
    conversation: Conversation,
    hideUnsavedNumbers: Boolean,
    currentUserId: String,
    knownContactIds: Set<String>,
): String {
    val title = conversation.title.trim()
    val looksLikeNumber = Regex("^\\+?[\\d\\s().-]{7,}$").matches(title)
    if (!hideUnsavedNumbers || conversation.type != ConversationType.direct || !looksLikeNumber) {
        return conversation.title
    }
    val otherId = conversation.participantIds.firstOrNull { it != currentUserId } ?: ""
    val isKnownContact = otherId.isNotEmpty() && knownContactIds.contains(otherId)
    return if (isKnownContact) conversation.title else "Unknown contact"
}

/** _compactBarTitle: the iOS-style compact-bar identity, including the
 *  placeholder-name exclusions and the Classic 'WhatsApp' special case. */
fun compactBarTitle(
    homeStyle: String,
    headerStyle: HomeHeaderStyle,
    pageTitle: String?,
    setMyName: Boolean,
    myNameOverride: String,
    userDisplayName: String,
    userUsername: String,
    largeTitleShown: Boolean,
): String {
    val override = myNameOverride.trim()
    val effectiveOverride =
        if (override.isNotEmpty() && override != "Alex Rivera" && override != "Bandi Maya") override else ""
    val rawName = userDisplayName.trim()
    val effectiveDisplayName =
        if (rawName.isNotEmpty() && rawName != "Chaty User" && rawName != "Bandi Maya") rawName
        else (if (userUsername.trim().isNotEmpty()) userUsername.trim() else "Chaty")
    val identity = if (setMyName) {
        if (effectiveOverride.isNotEmpty()) effectiveOverride else effectiveDisplayName
    } else {
        "Chaty"
    }
    val fallback = identity

    if (homeStyle == "Classic" && pageTitle == null) return "WhatsApp"

    return when (headerStyle) {
        HomeHeaderStyle.prominentIdentity -> fallback
        HomeHeaderStyle.compact -> pageTitle ?: fallback
        HomeHeaderStyle.searchForward -> pageTitle ?: "Search chats"
        HomeHeaderStyle.storiesFirst ->
            if (pageTitle != null && largeTitleShown) fallback else pageTitle ?: fallback
    }
}

/** _effectiveHomeHeaderStyle: the Home UI selector owns its header
 *  presentation; component templates remain the fallback for styles that do
 *  not declare a dedicated header. */
fun effectiveHomeHeaderStyle(
    rawHomeStyle: String,
    normalizedHomeStyle: String,
    templateHeaderStyle: HomeHeaderStyle,
): HomeHeaderStyle {
    val raw = rawHomeStyle.trim().lowercase()
    if (raw == "instagram style" || raw == "ios style") return HomeHeaderStyle.storiesFirst
    if (raw == "telegram style") return HomeHeaderStyle.compact
    return when (normalizedHomeStyle) {
        "Classic" -> HomeHeaderStyle.prominentIdentity
        "Cards" -> HomeHeaderStyle.prominentIdentity
        "Stories First" -> HomeHeaderStyle.storiesFirst
        "Compact" -> HomeHeaderStyle.compact
        "Productivity" -> HomeHeaderStyle.searchForward
        "Expressive" -> HomeHeaderStyle.prominentIdentity
        "Chaty Default" -> HomeHeaderStyle.compact
        "Tablet Split View" -> HomeHeaderStyle.compact
        else -> templateHeaderStyle
    }
}

/** Search placement resolution, including the legacy fallback: older saved
 *  settings may have disabled only the header shortcut without a placement
 *  value; search is preserved by moving it below the header. */
fun resolveSearchPlacement(searchPlacement: String, disableSearchBar: Boolean): String {
    val configured = when (searchPlacement.trim().lowercase()) {
        "below header" -> "Below header"
        "above chat filters" -> "Above chat filters"
        else -> "Header action"
    }
    return if (disableSearchBar && configured == "Header action") "Below header" else configured
}
