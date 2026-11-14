package com.example.chat

import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test

class MessageKeyLookupClientTest {
    private val room = "11111111-1111-4111-8111-111111111111"
    private val message = "22222222-2222-4222-8222-222222222222"
    private val owner = ApiAccountScope("user", "synthetic", "tenant", "https://example.invalid", "", 1)
    @Test fun exactKeyIsEncodedWithoutTrimmingAndIdentityMetadataIsReturned() {
        val transport = ApiHttpTransport { method, url, headers, body ->
            assertEquals("GET", method); assertNull(body); assertEquals("tenant", headers["x-tenant-id"])
            if (url.endsWith("capabilities")) 200 to """{"data":{"message_client_id_lookup_v1":true}}"""
            else {
                assertTrue(url.endsWith("client_id=key%2B+exact"))
                200 to """{"success":true,"data":{"message_id":"$message","room_id":"$room","client_id":"key+ exact","state":"edited","seq":null,"created_at":null,"edited_at":"2026-11-14T11:00:00Z","deleted_at":null}}"""
            }
        }
        val found = ApiMessageKeyLookupClient(owner, transport) { true }.lookup(room, "key+ exact")
        assertEquals(message, found.messageId); assertEquals("edited", found.state); assertNull(found.sequence)
    }
    @Test fun absentCapabilityNeverGuessesEndpoint() {
        var requests = 0
        val client = ApiMessageKeyLookupClient(owner, ApiHttpTransport { _, _, _, _ -> requests++; 200 to """{"data":{}}""" }) { true }
        try { client.lookup(room, "key"); fail("Must stay unresolved") } catch (_: IllegalStateException) { }
        assertEquals(1, requests)
    }
    @Test fun notFoundDoesNotMeanNeverSentAndMakesNoWrite() {
        val client = ApiMessageKeyLookupClient(owner, ApiHttpTransport { method, url, _, _ ->
            assertEquals("GET", method)
            if (url.endsWith("capabilities")) 200 to """{"data":{"message_client_id_lookup_v1":true}}""" else 404 to "{}"
        }) { true }
        try { client.lookup(room, "key"); fail("Must stay unresolved") }
        catch (error: IllegalStateException) { assertTrue(error.message!!.contains("does not prove")) }
    }
}
