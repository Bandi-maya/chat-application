package com.example.chat

import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiPresenceClient
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ApiPresenceScopeSafetyTest {
    @Test fun unsafeUserPathMakesNoRequest() {
        var calls = 0
        val client = ApiPresenceClient({ "https://example.invalid" }, { "tenant" },
            ApiHttpTransport { _, _, _, _ -> calls++; 200 to "{}" }, { "token" })
        listOf("../privacy", "u/other", "u?token=x", "u#fragment").forEach {
            assertEquals(ApiPresenceClient.PresenceView.Unavailable, client.presence(it))
        }
        assertEquals(0, calls)
    }
    @Test(expected = CancellationException::class) fun cancelledGetDoesNotTurnIntoNetworkMessage() {
        val client = ApiPresenceClient({ "https://example.invalid" }, { "tenant" },
            ApiHttpTransport { _, _, _, _ -> throw CancellationException("scope closed") }, { "token" })
        client.presence("user")
    }
    @Test(expected = CancellationException::class) fun cancelledHeartbeatIsNotSuccessOrRetryHint() {
        val client = ApiPresenceClient({ "https://example.invalid" }, { "tenant" },
            ApiHttpTransport { _, _, _, _ -> throw CancellationException("scope closed") }, { "token" })
        client.heartbeat()
    }
}
