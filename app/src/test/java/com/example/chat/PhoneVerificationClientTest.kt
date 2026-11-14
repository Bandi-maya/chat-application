package com.example.chat

import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test

class PhoneVerificationClientTest {
    @Test fun disabledSmsIsNotSuccessAndNeverReturnsAChallengeOrProof() {
        val client = ApiPhoneVerificationClient("https://example.invalid", "org", ApiHttpTransport { _, _, _, _ ->
            503 to """{"detail":{"code":"SMS_UNAVAILABLE","message":"disabled"}}"""
        }, { true })
        try { client.challenge("+15555550123"); fail("Expected unavailable") }
        catch (failure: PhoneVerificationFailure) { assertEquals("SMS_UNAVAILABLE", failure.errorCode); assertTrue(failure.message!!.contains("No SMS was sent")) }
    }
    @Test fun domesticNumberIsRejectedWithoutRequest() {
        var called = false
        val client = ApiPhoneVerificationClient("https://example.invalid", "org", ApiHttpTransport { _, _, _, _ -> called = true; 200 to "{}" }, { true })
        try { client.challenge("5555550123"); fail("Expected format rejection") } catch (_: IllegalArgumentException) { }
        assertFalse(called)
    }
}
