package com.example.chat

import com.example.chat.data.search.GlobalSearchService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchServiceTest {

    private val hits = listOf(
        GlobalSearchService.MessageHit("1", "room", "alice", "text", "meet at noon", 1),
        GlobalSearchService.MessageHit("2", "room", "bob", "image", "noon pic", 2),
        GlobalSearchService.MessageHit("3", "room", "alice", "video", "noon clip", 3),
    )
    private val contacts = listOf(
        GlobalSearchService.ContactHit("u1", "noonfan", "Noon Fan"),
    )

    private fun service() = GlobalSearchService(
        messageSearch = { q, _ -> hits.filter { it.snippet.contains(q, true) } },
        contactSearch = { q, _ -> contacts.filter { it.displayName.contains(q, true) } },
    )

    @Test fun `messages and contacts both surface`() = runTest {
        val result = service().search("noon")
        assertEquals(3, result.messages.size)
        assertEquals(1, result.contacts.size)
    }

    @Test fun `media type chip filters`() = runTest {
        val result = service().search("noon", GlobalSearchService.Filters(mediaTypes = setOf("image", "video")))
        assertEquals(listOf("2", "3"), result.messages.map { it.id })
    }

    @Test fun `sender filter applies`() = runTest {
        val result = service().search("noon", GlobalSearchService.Filters(senderId = "alice"))
        assertEquals(listOf("1", "3"), result.messages.map { it.id })
    }

    @Test fun `blank query returns empty without hitting seams`() = runTest {
        val result = service().search("   ")
        assertTrue(result.messages.isEmpty() && result.contacts.isEmpty())
    }
}
