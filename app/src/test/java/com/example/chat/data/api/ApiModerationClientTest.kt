package com.example.chat.data.api

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ApiModerationClientTest {
    private fun scope() = ApiAccountScope("me", "test-token", "tenant", "https://test.invalid/api", "", 1)
    @Test fun reportRequiresExplicitReceiptAndCarriesOnlyIdsReasonDetails() {
        val api = ApiModerationClient(ApiHttpTransport { method, url, headers, body ->
            assertEquals("POST", method); assertEquals("https://test.invalid/api/moderation/reports", url)
            assertEquals("tenant", headers["x-tenant-id"])
            val json = JSONObject(body!!); assertEquals("m1", json.getString("message_id")); assertEquals("spam", json.getString("reason"))
            201 to "{\"data\":{\"report_id\":\"r1\",\"status\":\"received\"}}"
        })
        assertEquals("r1", api.report(scope(), { true }, "m1", "spam", ""))
    }
    @Test fun missingBackendNeverSucceeds() {
        val api = ApiModerationClient(ApiHttpTransport { _, _, _, _ -> 404 to "" })
        try { api.block(scope(), { true }, "u1", true); fail() } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("not available")) }
    }
    @Test fun accountSwitchDiscardsResponse() {
        var current = true
        val api = ApiModerationClient(ApiHttpTransport { _, _, _, _ -> current = false; 200 to "{\"data\":{\"user_id\":\"u1\",\"blocked\":true}}" })
        try { api.block(scope(), { current }, "u1", true); fail() } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Account changed")) }
    }
    @Test fun invalidPathDoesNotReachNetwork() {
        val api = ApiModerationClient(ApiHttpTransport { _, _, _, _ -> fail("must not send"); 0 to "" })
        try { api.block(scope(), { true }, "../u", true); fail() } catch (_: IllegalStateException) {}
    }
}
