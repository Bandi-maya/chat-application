package com.example.chat

import com.example.chat.data.api.ApiContactsClient
import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.services.ContactRelationshipService
import com.example.chat.data.services.SecureValueStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real relationship service with scoped fake transport. No account, network or Android runtime. */
class ContactRelationshipScopeTest {
    private fun service() = ContactRelationshipService(
        contacts = { error("Unscoped contacts plane must not be read") }, currentUserId = { "owner" },
        secureStore = object : SecureValueStore {
            override suspend fun read(key: String): String? = null
            override suspend fun write(key: String, value: String) {}
        })
    private fun plane(handler: (String, String, String?) -> Pair<Int, String>) = ApiContactsClient(
        { "https://scoped.invalid" }, { "tenant" }, ApiHttpTransport { m, u, _, b -> handler(m, u, b) }, { "token" })

    @Test fun acceptUsesSameScopedPlaneForInboxAndResponse() = runBlocking {
        val seen = mutableListOf<String>()
        val scoped = plane { method, url, body ->
            seen += "$method $url"
            if (method == "GET") 200 to """{"data":[{"request_id":"req-1","from":{"id":"peer","username":"peer"}}]}"""
            else { assertTrue(body!!.contains("req-1")); 200 to """{"data":{"status":"accepted"}}""" }
        }
        assertEquals("req-1", service().acceptConnection("peer", scoped))
        assertEquals(listOf("GET https://scoped.invalid/contacts/requests", "POST https://scoped.invalid/contacts/respond"), seen)
    }
    @Test fun missingIncomingRequestCannotSendAccept() = runBlocking {
        var writes = 0
        val scoped = plane { m, _, _ -> if (m != "GET") writes++; 200 to """{"data":[]}""" }
        assertTrue(runCatching { service().acceptConnection("peer", scoped) }.isFailure)
        assertEquals(0, writes)
    }
    @Test fun statusChecksOnlyScopedPlaneAndIncomingRemainsCallLocked() = runBlocking {
        val scoped = plane { _, url, _ ->
            if (url.endsWith("/contacts")) 200 to """{"data":[]}"""
            else 200 to """{"data":[{"request_id":"req","from":{"id":"peer"}}]}"""
        }
        val status = service().connectionStatus("peer", scoped)
        assertTrue(status.isPendingIncoming); assertFalse(status.callsAllowed)
    }
    @Test fun blockedLookupUsesScopedPlane() = runBlocking {
        val scoped = plane { _, url, _ -> assertEquals("https://scoped.invalid/blocks", url); 200 to """{"data":[{"id":"peer"}]}""" }
        assertTrue(service().isBlocked("peer", scoped))
    }
    @Test fun lookupFailureDoesNotBecomeAcceptance() = runBlocking {
        val scoped = plane { _, _, _ -> 500 to """{"detail":"offline"}""" }
        assertTrue(runCatching { service().connectionStatus("peer", scoped) }.isFailure)
    }
}
