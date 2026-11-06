package com.example.chat
import com.example.chat.core.groups.GroupMemberPolicy
import com.example.chat.domain.models.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GroupMemberPolicyTest {
    private fun user(id: String, name: String = id, username: String = id) = UserProfile(id, name, username, "X", "0xFF000000", "", lastSeenAt = Instant.EPOCH)
    private val profiles = listOf(user("c", "C"), user("b", "B"), user("a", "A"), user("outside"))
    @Test fun onlyMembershipIdsAppear() { assertEquals(listOf("a", "b"), GroupMemberPolicy.members(listOf("a","b"), profiles, emptyList(), "", "").map { it.id }) }
    @Test fun meThenAdminsThenAlphabetical() { assertEquals(listOf("c","b","a"), GroupMemberPolicy.members(listOf("a","b","c"), profiles, listOf("b"), "c", "").map { it.id }) }
    @Test fun adminDoesNotImplyMembership() { assertTrue(GroupMemberPolicy.members(emptyList(), profiles, listOf("outside"), "", "").isEmpty()) }
    @Test fun unknownProfileNotInvented() { assertTrue(GroupMemberPolicy.members(listOf("missing"), profiles, emptyList(), "", "").isEmpty()) }
    @Test fun duplicateProfilesDoNotRepeat() { assertEquals(1, GroupMemberPolicy.members(listOf("a","a"), profiles + profiles, emptyList(), "", "").size) }
    @Test fun usernameSearchAcceptsAtPrefix() { assertEquals(listOf("a"), GroupMemberPolicy.members(listOf("a","b"), profiles, emptyList(), "", "@A").map { it.id }) }
    @Test fun caseInsensitiveNameSearch() { assertEquals("a", GroupMemberPolicy.members(listOf("a"), listOf(user("a","John Doe")), emptyList(), "", "doe").single().id) }
    @Test fun knownCountExcludesDuplicatesBlank() { assertEquals(2, GroupMemberPolicy.knownCount(listOf("a","a","", "b"))) }
}
