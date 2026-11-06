package com.example.chat

import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.newchat.NewChatRules
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression contracts for the pure new-chat / group rules in [NewChatRules].
 * Reference: api branch new_chat_screen.dart and ApiBackendService.searchUsers
 * at 3bee780 (searchUsers body is identical at cac25f1e).
 *
 * Synthetic profiles only. No OS contacts, network, persistence or account
 * state is touched. The epoch checks cover an integer comparison only; they do
 * not prove account or session safety. There is no restoration API in
 * NewChatRules: nothing here tests or adds persistence or restoration.
 */
class PeerNewChatRulesContractTest {

    private fun user(id: String, name: String, username: String, email: String = "") = UserProfile(
        id = id,
        displayName = name,
        username = username,
        avatarInitials = name.take(1),
        avatarColorHex = "0xFF6366F1",
        about = "",
        lastSeenAt = Instant.EPOCH,
        email = email,
    )

    private val directory = listOf(
        user("u1", "Maya Rao", "maya", "maya@x.com"),
        user("u2", "Alex Doe", "alexd", "alex@y.com"),
        user("me", "Self Maya", "selfy", "self@z.com"),
        user("u4", "Zed Quinn", "zq", "zed@q.com"),
    )

    // 1. Effective normal-group title trims exactly as baseline.
    @Test
    fun normalTitleIsTrimmedAndOtherwiseUnchanged() {
        assertEquals("My Group", NewChatRules.effectiveGroupTitle("  My Group  ", false, 2))
        assertEquals("My Group", NewChatRules.effectiveGroupTitle("\t My Group \n", false, 2))
        assertEquals("a  b", NewChatRules.effectiveGroupTitle(" a  b ", false, 2))
        assertEquals("Mixed Case", NewChatRules.effectiveGroupTitle("Mixed Case", false, 2))
        // A normal group never gets the broadcast prefix, even if titled "Broadcast".
        assertEquals("Broadcast", NewChatRules.effectiveGroupTitle("Broadcast", false, 2))
        assertEquals("Broadcast: x", NewChatRules.effectiveGroupTitle(" Broadcast: x ", false, 2))
    }

    // 2. Blank normal-group title blocks creation.
    @Test
    fun blankNormalTitleBlocksCreation() {
        assertEquals("", NewChatRules.effectiveGroupTitle("", false, 3))
        assertEquals("", NewChatRules.effectiveGroupTitle("   \t ", false, 3))
        assertFalse(NewChatRules.canCreateGroup("", false, 3, false))
        assertFalse(NewChatRules.canCreateGroup("   ", false, 3, false))
        assertTrue(NewChatRules.canCreateGroup(" G ", false, 3, false))
    }

    // 3. Existing broadcast blank-title/count convention stays exact.
    @Test
    fun broadcastBlankTitleUsesMemberCount() {
        assertEquals("Broadcast (1)", NewChatRules.effectiveGroupTitle("", true, 1))
        assertEquals("Broadcast (3)", NewChatRules.effectiveGroupTitle("   ", true, 3))
        assertEquals("Broadcast (12)", NewChatRules.effectiveGroupTitle("\n", true, 12))
        // Count 0 still formats, but creation stays blocked by the member guard.
        assertEquals("Broadcast (0)", NewChatRules.effectiveGroupTitle("", true, 0))
        assertFalse(NewChatRules.canCreateGroup("", true, 0, false))
        assertTrue(NewChatRules.canCreateGroup("", true, 2, false))
    }

    // 4. Existing broadcast prefix/case behavior stays exact.
    @Test
    fun broadcastPrefixAndCaseRules() {
        assertEquals("Broadcast: Team", NewChatRules.effectiveGroupTitle("Team", true, 2))
        assertEquals("Broadcast: Team", NewChatRules.effectiveGroupTitle("  Team  ", true, 2))
        assertEquals("broadcast news", NewChatRules.effectiveGroupTitle("broadcast news", true, 2))
        assertEquals("BROADCAST news", NewChatRules.effectiveGroupTitle("BROADCAST news", true, 2))
        assertEquals("Broadcast", NewChatRules.effectiveGroupTitle("Broadcast", true, 2))
        // startsWith, not word match: "Broadcasting" keeps its title.
        assertEquals("Broadcasting", NewChatRules.effectiveGroupTitle("Broadcasting", true, 2))
        // Prefix test is on the start only, after trimming.
        assertEquals("Broadcast: Team broadcast", NewChatRules.effectiveGroupTitle("Team broadcast", true, 2))
        assertEquals("broadcast x", NewChatRules.effectiveGroupTitle("  broadcast x", true, 2))
    }

    // 5. Empty members or loading state blocks duplicate/invalid creation.
    @Test
    fun emptyMembersOrLoadingBlockCreation() {
        assertFalse(NewChatRules.canCreateGroup("G", false, 0, false))
        assertFalse(NewChatRules.canCreateGroup("G", false, 2, true))
        assertFalse(NewChatRules.canCreateGroup("G", true, 0, false))
        assertFalse(NewChatRules.canCreateGroup("G", true, 2, true))
        assertFalse(NewChatRules.canCreateGroup("", true, 2, true))
        assertFalse(NewChatRules.canCreateGroup("G", false, 0, true))
        assertTrue(NewChatRules.canCreateGroup("G", false, 1, false))
    }

    // 6. Directory self-exclusion uses exact user ID and preserves includeSelf.
    @Test
    fun selfExclusionIsExactIdAndHonorsIncludeSelf() {
        assertEquals(listOf("u1", "u2", "u4"), NewChatRules.filterUsers(directory, "", "me").map { it.id })
        assertEquals(
            listOf("u1", "u2", "me", "u4"),
            NewChatRules.filterUsers(directory, "", "me", includeSelf = true).map { it.id },
        )
        // Exact, case-sensitive id: "ME" or "m" do not exclude "me".
        assertEquals(
            listOf("u1", "u2", "me", "u4"),
            NewChatRules.filterUsers(directory, "", "ME").map { it.id },
        )
        assertEquals(
            listOf("u1", "u2", "me", "u4"),
            NewChatRules.filterUsers(directory, "", "m").map { it.id },
        )
        // Same display name text is not identity: only the id is compared.
        assertEquals(listOf("u1"), NewChatRules.filterUsers(directory, "maya", "me").map { it.id })
        // Unknown self id (null) excludes nobody.
        assertEquals(listOf("u1", "u2", "me", "u4"), NewChatRules.filterUsers(directory, "", null).map { it.id })
        // Self matching the query is still excluded unless includeSelf.
        assertEquals(listOf("me"), NewChatRules.filterUsers(directory, "selfy", null).map { it.id })
        assertEquals(emptyList<String>(), NewChatRules.filterUsers(directory, "selfy", "me").map { it.id })
        assertEquals(listOf("me"), NewChatRules.filterUsers(directory, "selfy", "me", includeSelf = true).map { it.id })
    }

    // 7. Search: display-name/username/email, whitespace/case, no-result, input order.
    @Test
    fun searchMatchesNameUsernameEmailCaseAndWhitespace() {
        assertEquals(listOf("u1"), NewChatRules.filterUsers(directory, "rao", null).map { it.id })
        assertEquals(listOf("u2"), NewChatRules.filterUsers(directory, "ALEXD", null).map { it.id })
        assertEquals(listOf("u4"), NewChatRules.filterUsers(directory, "zed@q.com", null).map { it.id })
        assertEquals(listOf("u4"), NewChatRules.filterUsers(directory, "@Q.CoM", null).map { it.id })
        // Query is trimmed and lower-cased.
        assertEquals(listOf("u1"), NewChatRules.filterUsers(directory, "  MAYA@X  ", null).map { it.id })
        // Inner whitespace is kept: matches the display name text.
        assertEquals(listOf("u1"), NewChatRules.filterUsers(directory, "maya rao", null).map { it.id })
        assertEquals(emptyList<String>(), NewChatRules.filterUsers(directory, "mayarao", null).map { it.id })
        // Whitespace-only query behaves like an empty query.
        assertEquals(directory.map { it.id }, NewChatRules.filterUsers(directory, "  \t ", null).map { it.id })
        // Substring in more than one field returns each user once.
        assertEquals(listOf("u1", "me"), NewChatRules.filterUsers(directory, "maya", null).map { it.id })
        // No result.
        assertEquals(emptyList<String>(), NewChatRules.filterUsers(directory, "nomatch", null).map { it.id })
        assertEquals(emptyList<String>(), NewChatRules.filterUsers(emptyList(), "", null).map { it.id })
    }

    @Test
    fun searchPreservesInputOrderAndDoesNotDeduplicate() {
        val reversed = directory.reversed()
        assertEquals(
            listOf("u4", "me", "u2", "u1"),
            NewChatRules.filterUsers(reversed, "", null).map { it.id },
        )
        assertEquals(listOf("me", "u1"), NewChatRules.filterUsers(reversed, "maya", null).map { it.id })
        // Input duplicates are not collapsed: this seam only filters.
        val withDup = listOf(directory[0], directory[0], directory[1])
        assertEquals(listOf("u1", "u1", "u2"), NewChatRules.filterUsers(withDup, "", null).map { it.id })
    }

    // 8. Toggle uses identity/set semantics.
    @Test
    fun toggleAddsRemovesAndNeverDuplicates() {
        val one = NewChatRules.toggleMember(emptySet(), "u1")
        assertEquals(setOf("u1"), one)
        val two = NewChatRules.toggleMember(one, "u2")
        assertEquals(setOf("u1", "u2"), two)
        assertEquals(2, two.size)
        assertEquals(setOf("u2"), NewChatRules.toggleMember(two, "u1"))
        assertEquals(emptySet<String>(), NewChatRules.toggleMember(NewChatRules.toggleMember(emptySet(), "u1"), "u1"))
        // Id is compared exactly (case-sensitive), so "U1" is a different member.
        assertEquals(setOf("u1", "U1"), NewChatRules.toggleMember(one, "U1"))
        // Toggling twice restores the original membership.
        assertEquals(two, NewChatRules.toggleMember(NewChatRules.toggleMember(two, "u3"), "u3"))
    }

    // 9. Epoch gate accepts only the matching generation.
    @Test
    fun epochGateAcceptsOnlyMatchingGeneration() {
        assertTrue(NewChatRules.isCurrentEpoch(5, 5))
        assertTrue(NewChatRules.isCurrentEpoch(0, 0))
        assertFalse(NewChatRules.isCurrentEpoch(4, 5))
        assertFalse(NewChatRules.isCurrentEpoch(6, 5))
        assertFalse(NewChatRules.isCurrentEpoch(0, 1))
        assertFalse(NewChatRules.isCurrentEpoch(-1, 0))
    }

    // 10. Inputs are not mutated; caller-facing contract stays compatible.
    @Test
    fun inputsAreNotMutatedAndCallerContractHolds() {
        val users = directory.toMutableList()
        val snapshot = users.toList()
        val result = NewChatRules.filterUsers(users, "maya", "me")
        assertEquals(snapshot, users)
        assertNotSame(users, result)
        // The result is a new list: changing it does not touch the input.
        val mutable = result.toMutableList()
        mutable.clear()
        assertEquals(snapshot, users)

        val selected = setOf("u1", "u2")
        val before = selected.toSet()
        val toggled = NewChatRules.toggleMember(selected, "u3")
        assertEquals(before, selected)
        assertEquals(setOf("u1", "u2", "u3"), toggled)
        assertNotSame(selected, toggled)

        // Signatures used by NewChatScreen (named/default args included).
        assertEquals(250L, NewChatRules.SEARCH_DEBOUNCE_MS)
        assertEquals("T", NewChatRules.effectiveGroupTitle(rawTitle = "T", isBroadcast = false, memberCount = 1))
        assertTrue(NewChatRules.canCreateGroup("T", false, 1, false))
        assertEquals(
            1,
            NewChatRules.filterUsers(users = users, query = "zq", selfId = "me", includeSelf = false).size,
        )
    }
}
