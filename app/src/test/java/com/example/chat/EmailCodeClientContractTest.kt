package com.example.chat

import com.example.chat.data.api.ApiEmailCodeClient
import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiRegistrationUsernameClient
import org.junit.Assert.*
import org.junit.Test

class EmailCodeClientContractTest {
    private val uuid = "00000000-0000-4000-8000-000000000001"
    @Test fun missingUsernameEndpointNeverMeansAvailable() {
        var calls = 0
        val c = ApiRegistrationUsernameClient("https://chat.test", "tenant", ApiHttpTransport { _, _, h, _ ->
            calls++; assertFalse(h.containsKey("Authorization")); 404 to "{}"
        })
        try { c.check("maya"); fail("Missing endpoint must fail") } catch (_: IllegalStateException) { }
        assertEquals(2, calls)
    }
    @Test fun malformedUsernameAnswerRejected() {
        val c = ApiRegistrationUsernameClient("https://chat.test", "tenant", ApiHttpTransport { _, _, _, _ ->
            200 to """{"data":{"valid":true}}"""
        })
        try { c.check("maya"); fail("Missing available must fail") } catch (_: IllegalStateException) { }
    }
    @Test fun challengeIsBoundToCapturedTenantAndHasNoBearer() {
        val c = ApiEmailCodeClient("https://chat.test", "chosen-tenant", ApiHttpTransport { method, url, h, body ->
            assertEquals("POST", method); assertTrue(url.endsWith("/auth/email-code/request"))
            assertEquals("chosen-tenant", h["x-tenant-id"]); assertFalse(h.containsKey("Authorization"))
            assertTrue(body!!.contains("maya@example.com"))
            200 to """{"data":{"challenge_id":"$uuid","resend_after_seconds":60,"expires_in_seconds":300}}"""
        })
        assertEquals(uuid, c.requestCode(" Maya@example.com ").id)
    }
    @Test fun changedScopeStopsFallback() {
        var current = true; var calls = 0
        val c = ApiEmailCodeClient("https://chat.test", "tenant", ApiHttpTransport { _, _, _, _ ->
            calls++; current = false; 404 to "{}"
        }, { current })
        try { c.requestCode("maya@example.com"); fail("Scope changed") } catch (_: IllegalStateException) { }
        assertEquals(1, calls)
    }
    @Test fun debugCodeStillCallsRealVerify() {
        var calls = 0
        val c = ApiEmailCodeClient("https://chat.test", "tenant", ApiHttpTransport { _, url, _, body ->
            calls++; assertTrue(url.endsWith("/auth/email-code/verify")); assertTrue(body!!.contains("000000"))
            401 to """{"detail":"Invalid or expired code."}"""
        })
        try { c.verifyCode(ApiEmailCodeClient.Challenge(uuid, "maya@example.com", 60, 300), "000000"); fail("No bypass") }
        catch (_: IllegalStateException) { }
        assertEquals(1, calls)
    }
    @Test fun emailVerificationPreservesAllFourInstallationProofHeaders() {
        val expected = mapOf("x-device-id" to uuid, "x-device-public-key" to "public", "x-device-challenge-id" to "challenge", "x-device-proof" to "signature")
        val client = ApiEmailCodeClient("https://chat.test", "tenant", ApiHttpTransport { _, _, headers, _ ->
            expected.forEach { (key, value) -> assertEquals(value, headers[key]) }
            assertFalse(headers.containsKey("Authorization"))
            401 to "{}"
        })
        try { client.verifyCode(ApiEmailCodeClient.Challenge(uuid, "maya@example.com", 60, 300), "123456", expected + ("Authorization" to "must-not-forward")); fail("Expected rejection") }
        catch (_: IllegalStateException) { }
    }

}
