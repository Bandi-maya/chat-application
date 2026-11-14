package com.example.chat

import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.newchat.NewChatRules
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ExactUsernameLookupTest {
    private fun user(id: String, username: String, name: String = "Name", email: String = "") =
        UserProfile(id, name, username, "N", "#000000", "", lastSeenAt = Instant.EPOCH, email = email)
    @Test fun optionalAtAndCaseMatchExactOnly() {
        val users = listOf(user("1", "maya"), user("2", "mayaxx"), user("3", "other", "Maya", "maya@example.invalid"))
        assertEquals(listOf("1"), NewChatRules.exactUsernameMatch(users, " @MaYa ", "self").map { it.id })
        assertTrue(NewChatRules.exactUsernameMatch(users, "may", "self").isEmpty())
    }
    @Test fun selfExcludedAndSameIdentityDeduplicated() {
        val maya = user("1", "maya")
        assertTrue(NewChatRules.exactUsernameMatch(listOf(maya), "maya", "1").isEmpty())
        assertEquals(1, NewChatRules.exactUsernameMatch(listOf(maya, maya), "maya", "self").size)
    }
    @Test(expected = IllegalStateException::class) fun conflictingExactOwnersAreNotChosen() {
        NewChatRules.exactUsernameMatch(listOf(user("1", "maya"), user("2", "maya")), "maya", "self")
    }
}
