package com.example.chat

import com.example.chat.data.api.*
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApiScheduledMessageContractTest {
    private fun owner() = ApiAccountScope("u", "synthetic-token", "t", "https://example.invalid", "", 1)
    @Test fun stableIdSurvivesUnknownResponseAndRetry() = runBlocking {
        val ids = mutableMapOf<String, String>()
        val requests = mutableListOf<String>()
        var loseResponse = true
        val transport = ApiHttpTransport { method, _, _, body ->
            if (method == "GET") 200 to """{"data":{"scheduled_messages_v1":true}}"""
            else {
                requests += JSONObject(body!!).getString("client_id")
                if (loseResponse) { loseResponse = false; throw java.io.IOException("synthetic response loss") }
                200 to """{"data":{"id":"schedule-1"}}"""
            }
        }
        val client = ApiScheduledMessagesClient(owner(), transport, { _, f -> f(); true }, { hash -> ids.getOrPut(hash) { "stable-id" } })
        try { client.create("room", "hello", Instant.parse("2099-01-01T00:00:00Z")); fail("Expected network loss") } catch (_: java.io.IOException) {}
        assertEquals("schedule-1", client.create("room", "hello", Instant.parse("2099-01-01T00:00:00Z")))
        assertEquals(listOf("stable-id", "stable-id"), requests)
        assertEquals(1, ids.size)
    }
    @Test fun absentCapabilityHasNoMutation() = runBlocking {
        var mutations = 0
        val client = ApiScheduledMessagesClient(owner(), ApiHttpTransport { method, _, _, _ ->
            if (method != "GET") mutations++
            200 to """{"data":{}}"""
        }, { _, f -> f(); true }, { "stable-id" })
        try { client.create("room", "hello", Instant.parse("2099-01-01T00:00:00Z")); fail("Expected refusal") } catch (_: IllegalStateException) {}
        assertEquals(0, mutations)
    }
    @Test fun cancellationRequiresMatchingCancelledReceipt() = runBlocking {
        val client = ApiScheduledMessagesClient(owner(), ApiHttpTransport { method, _, _, _ ->
            if (method == "GET") 200 to """{"data":{"scheduled_messages_v1":true}}"""
            else 200 to """{"data":{"id":"different","state":"cancelled"}}"""
        }, { _, f -> f(); true }, { "stable-id" })
        try { client.cancel("wanted"); fail("Expected receipt refusal") } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("not confirmed"))
        }
    }
    @Test fun overlongBodyDoesNotReachTransport() = runBlocking {
        var calls = 0
        val client = ApiScheduledMessagesClient(owner(), ApiHttpTransport { _, _, _, _ -> calls++; 500 to "" },
            { _, f -> f(); true }, { "stable-id" })
        try { client.create("room", "x".repeat(10_001), Instant.parse("2099-01-01T00:00:00Z")); fail("Expected refusal") }
        catch (_: IllegalArgumentException) {}
        assertEquals(0, calls)
    }

}
