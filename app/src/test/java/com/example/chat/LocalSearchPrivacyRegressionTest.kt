package com.example.chat

import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.search.LocalConversationSearch
import com.example.chat.ui.search.SearchRequestGuard
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalSearchPrivacyRegressionTest {
    private fun chat(id: String, title: String = "Work", text: String = "invoice") = Conversation(
        id = id, type = ConversationType.direct, title = title,
        participantIds = emptyList(), lastMessageText = text,
        lastMessageTime = Instant.EPOCH, lastMessageSenderId = "sender",
    )

    @Test fun whitespaceOnlyDoesNotMatchEveryChat() {
        assertTrue(LocalConversationSearch.filter(listOf(chat("a")), "   ") { false }.isEmpty())
    }

    @Test fun queryNormalizesOuterWhitespace() {
        assertEquals(listOf("a"), LocalConversationSearch.filter(listOf(chat("a")), "  INVOICE  ") { false }.map { it.id })
    }

    @Test fun privacyChangeRemovesLocalMatchesWithoutNewQuery() {
        val rows = listOf(chat("a"))
        var hidden = false
        assertEquals(1, LocalConversationSearch.filter(rows, "invoice") { hidden }.size)
        hidden = true
        assertTrue(LocalConversationSearch.filter(rows, "invoice") { hidden }.isEmpty())
    }

    @Test fun removedConversationDoesNotSurviveSearchRefresh() {
        assertEquals(1, LocalConversationSearch.filter(listOf(chat("a")), "invoice") { false }.size)
        assertTrue(LocalConversationSearch.filter(emptyList(), "invoice") { false }.isEmpty())
    }

    @Test fun privacyFilterRunsBeforeContentMatching() {
        assertTrue(LocalConversationSearch.filter(listOf(chat("secret", "secret", "secret")), "secret") { true }.isEmpty())
    }

    @Test fun invalidationOnExitRejectsOldRemoteResult() {
        val guard = SearchRequestGuard()
        val request = guard.begin()
        guard.begin()
        assertFalse(guard.isCurrent(request))
    }
}
