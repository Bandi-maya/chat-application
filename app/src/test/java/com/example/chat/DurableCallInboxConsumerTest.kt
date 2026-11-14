package com.example.chat

import com.example.chat.data.api.*
import com.example.chat.data.e2ee.SecureKeyValueStore
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DurableCallInboxConsumerTest {
    private class Store : SecureKeyValueStore {
        val values = mutableMapOf<String, String>(); var fail = false
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) { check(!fail); values[key] = value }
        override suspend fun delete(key: String) { values.remove(key) }
    }
    @Test fun failedEncryptedWriteNeverAcknowledgesServerEvent() = runTest {
        val store = Store().apply { fail = true }; var acknowledgements = 0; var recoveryPrepared = false
        val owner = ApiAccountScope("user", "fake", "tenant", "https://example.invalid", "", 1)
        val now = Instant.now()
        val event = JSONObject().put("id", "11111111-1111-4111-8111-111111111111").put("event_id", "11111111-1111-4111-8111-111111111111")
            .put("seq", 1).put("sender_id", "22222222-2222-4222-8222-222222222222")
            .put("data", JSONObject().put("signal_type", "offer").put("call_id", "call"))
            .put("created_at", now.toString()).put("expires_at", now.plusSeconds(60).toString())
        val transport = ApiHttpTransport { _, url, _, _ ->
            when {
                url.endsWith("capabilities") -> 200 to """{"data":{"call_signal_inbox_v1":true}}"""
                url.contains("/peek") -> 200 to JSONObject().put("data", JSONObject().put("events", org.json.JSONArray().put(event)).put("has_more", false)).toString()
                else -> { acknowledgements++; 200 to """{"data":{"accepted":true}}""" }
            }
        }
        val consumer = DurableCallInboxConsumer(EncryptedCallInboxStage(store))
        val outcome = consumer.consume("a".repeat(64), DurableCallInboxClient(owner, transport) { true }, { true }, prepareRecovery = { recoveryPrepared = true }) { fail("No handoff before persistence") }
        assertEquals(DurableCallInboxConsumer.Outcome.RETRY_REQUIRED, outcome); assertEquals(0, acknowledgements); assertTrue(recoveryPrepared)
    }
    @Test fun emptyCaughtUpInboxDoesNotRegisterRecoveryWork() = runTest {
        val owner = ApiAccountScope("user", "fake", "tenant", "https://example.invalid", "", 1)
        val transport = ApiHttpTransport { _, url, _, _ ->
            if (url.endsWith("capabilities")) 200 to """{"data":{"call_signal_inbox_v1":true}}"""
            else 200 to """{"data":{"events":[],"has_more":false}}"""
        }
        var preparations = 0
        val outcome = DurableCallInboxConsumer(EncryptedCallInboxStage(Store())).consume("a".repeat(64),
            DurableCallInboxClient(owner, transport) { true }, { true }, prepareRecovery = { preparations++ }) { }
        assertEquals(DurableCallInboxConsumer.Outcome.CAUGHT_UP, outcome)
        assertEquals(0, preparations)
    }

}
