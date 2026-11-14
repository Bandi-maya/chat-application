package com.example.chat

import com.example.chat.data.api.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApiMessageReceiptsTest {
    private val message = "11111111-1111-4111-8111-111111111111"
    private val user = "22222222-2222-4222-8222-222222222222"
    private fun scope() = ApiAccountScope("me", "fake", "org-a", "https://example.invalid", "", 1)
    @Test fun partialGroupReadRetainsServerTimeButDoesNotAssertAllRead() {
        val event = MessageReceiptEvent.parse(JSONObject("""{"message_id":"$message","room_id":"room","user_id":"$user","read_at":"2026-11-14T10:00:00Z","delivered_at":"2026-11-14T09:59:00Z","played_at":null,"all_delivered":true,"all_read":false}"""))!!
        assertFalse(event.allRead); assertEquals("2026-11-14T10:00:00Z", event.times.readAt.toString())
    }
    @Test fun hiddenTimesSuppressedAndEmptyPageKeepsContinuation() {
        val owner = scope()
        val body = """{"data":{"message_id":"$message","snapshot_known":true,"sent_at":null,"recipients":[],"next_after_user_id":"$user"}}"""
        val client = ApiMessageReceiptsClient(owner, ApiHttpTransport { _, _, headers, _ ->
            assertEquals("org-a", headers["x-tenant-id"]); 200 to body
        }) { true }
        val page = client.info(message)
        assertTrue(page.recipients.isEmpty()); assertEquals(user, page.nextAfterUserId)
    }
    @Test fun staleOwnerCannotFallbackOrPublish() {
        var valid = true; var requests = 0
        val client = ApiMessageReceiptsClient(scope(), ApiHttpTransport { _, _, _, _ -> requests++; valid = false; 404 to "" }) { valid }
        try { client.info(message); fail("Expected stale rejection") } catch (_: IllegalStateException) { }
        assertEquals(1, requests)
    }
}
