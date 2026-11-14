package com.example.chat

import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test

class ApiViewOnceTextContractTest {
    private fun owner() = ApiAccountScope("u", "synthetic-token", "t", "https://example.invalid", "", 1)
    @Test fun openIsOneShotEvenWhenResponseIsLost() {
        var opens = 0
        val client = ApiViewOnceTextClient(owner(), ApiHttpTransport { method, _, _, _ ->
            if (method == "GET") 200 to """{"data":{"view_once_v1":true}}"""
            else { opens++; throw java.io.IOException("synthetic response loss") }
        }, { true })
        try { client.openPlain("message"); fail("Expected network loss") } catch (_: java.io.IOException) {}
        assertEquals(1, opens)
    }
    @Test fun capabilityStringIsNotAnApprovalToSend() {
        var writes = 0
        val client = ApiViewOnceTextClient(owner(), ApiHttpTransport { method, _, _, _ ->
            if (method != "GET") writes++
            200 to """{"data":{"view_once_v1":"true"}}"""
        }, { true })
        try { client.send("room", "private fixture", "id"); fail("Expected refusal") } catch (_: IllegalStateException) {}
        assertEquals(0, writes)
    }
    @Test fun accountSwitchDiscardsOpenResponse() {
        var active = true
        val client = ApiViewOnceTextClient(owner(), ApiHttpTransport { method, _, _, _ ->
            if (method == "GET") 200 to """{"data":{"view_once_v1":true}}"""
            else { active = false; 200 to """{"data":{"content":"private fixture","encrypted_payload":null}}""" }
        }, { active })
        try { client.openPlain("message"); fail("Expected refusal") } catch (error: IllegalStateException) { assertEquals("Account changed.", error.message) }
    }
}
