package com.example.chat

import com.example.chat.data.api.ApiChatClient
import com.example.chat.data.api.ApiHttpTransport
import org.junit.Assert.*
import org.junit.Test

class ScopedDeliveryClientTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val other = "00000000-0000-0000-0000-000000000002"
    @Test fun exactIdsOnlyAndNoLegacyFallback() {
        val paths = mutableListOf<String>()
        val transport = ApiHttpTransport { _, url, _, _ ->
            paths += url
            404 to "{}"
        }
        val client = ApiChatClient({ "https://chat.test" }, { "tenant" }, { "token" }, transport)
        assertNull(client.markFetchedMessagesDelivered("room", listOf(id)))
        assertEquals(2, paths.size)
        assertTrue(paths.all { it.endsWith("/delivered-scoped") })
    }
    @Test fun neverAcceptsUnrequestedIdsFromResponse() {
        val transport = ApiHttpTransport { method, url, headers, body ->
            assertEquals("POST", method)
            assertTrue(url.endsWith("/delivered-scoped"))
            assertEquals("Bearer token", headers["Authorization"])
            assertTrue(body!!.contains(id))
            200 to """{"data":{"acknowledged_ids":["$id","$other"]}}"""
        }
        val client = ApiChatClient({ "https://chat.test" }, { "tenant" }, { "token" }, transport)
        assertEquals(setOf(id), client.markFetchedMessagesDelivered("room", listOf(id)))
    }
    @Test fun invalidIdsAndEmptyListsMakeNoRequest() {
        var calls = 0
        val transport = ApiHttpTransport { _, _, _, _ -> calls++; 200 to "{}" }
        val client = ApiChatClient({ "https://chat.test" }, { "tenant" }, { "token" }, transport)
        assertNull(client.markFetchedMessagesDelivered("room", emptyList()))
        assertNull(client.markFetchedMessagesDelivered("room", listOf("../bad")))
        assertNull(client.markFetchedMessagesDelivered("../room", listOf(id)))
        assertEquals(0, calls)
    }
}
