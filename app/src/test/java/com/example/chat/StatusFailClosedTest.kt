package com.example.chat

import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiStatusClient
import com.example.chat.data.services.StatusIdentityUnboundException
import com.example.chat.data.services.StatusRecord
import com.example.chat.data.services.StatusService
import com.example.chat.domain.models.PrivacyPreferencesSnapshot
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Offline JVM tests: every StatusService write fails closed while identity is unbound.
 *  The live status plane runs over a counting fake ApiHttpTransport. */
class StatusFailClosedTest {
    private var http = 0
    private var uploads = 0

    private val transport = ApiHttpTransport { method, url, _, body ->
        http++
        when {
            method == "POST" && url.endsWith("/status/post") ->
                200 to """{"success":true,"data":{"id":"s1","expires_at":"2026-10-28T01:00:00Z"}}"""
            method == "GET" && url.endsWith("/status/feed") ->
                200 to """{"success":true,"data":{"statuses":[]}}"""
            method == "DELETE" ->
                200 to """{"success":true,"data":null}"""
            else -> 200 to """{"success":true,"data":{}}"""
        }
    }

    private fun svc(bound: (() -> Boolean)? = null): StatusService {
        val client = ApiStatusClient({ "http://10.0.2.2:8000" }, { "chat-application" }, { "tok" }, transport)
        val upload: suspend (String, ByteArray, String) -> String = { _, _, _ ->
            uploads++
            "http://h/uploads/x"
        }
        return if (bound == null) StatusService(client, upload, { "A" }, { PrivacyPreferencesSnapshot() })
        else StatusService(client, upload, { "A" }, { PrivacyPreferencesSnapshot() }, identityBound = bound)
    }

    private val rec = StatusRecord("s1", "A", "", "text", null, null, 0, 0, Instant.now(), Instant.now(), null)

    private fun assertUnbound(block: suspend () -> Unit) = runBlocking {
        try { block(); fail("expected StatusIdentityUnboundException") } catch (_: StatusIdentityUnboundException) {}
        assertEquals(0, http); assertEquals(0, uploads)
    }

    @Test fun defaultIsNotAvailable() { assertFalse(svc().isAvailable()) }
    @Test fun publishTextZeroNetwork() = assertUnbound { svc().publishText("hello") }
    @Test fun publishMediaZeroNetwork() = assertUnbound {
        svc().publishMediaBytes(ByteArray(4) { 1 }, "a.jpg", "image", mimeType = "image/jpeg")
    }
    @Test fun audioMediaZeroNetwork() = assertUnbound {
        svc().publishMediaBytes(ByteArray(4) { 1 }, "a.m4a", "audio")
    }
    @Test fun markViewedZeroNetwork() = assertUnbound { svc().markViewed(rec) }
    @Test fun deleteZeroNetwork() = assertUnbound { svc().deleteStatus(rec) }

    @Test fun viewersZeroNetwork() = assertUnbound { svc().viewersFor("s1") }
    @Test fun signedUrlZeroNetwork() = assertUnbound { svc().createSignedUrl("A/x.jpg") }

    @Test fun watchEmitsEmptyAndMakesZeroNetworkCalls() = runBlocking {
        val first = svc().watchActiveStatuses().first()
        assertTrue(first.isEmpty())
        assertEquals(0, http); assertEquals(0, uploads)
    }

    @Test fun messageIsTheAgreedUserText() {
        assertEquals("Status isn't available for this account connection yet. Nothing was sent.",
            StatusIdentityUnboundException().message)
    }

    @Test fun messageLeaksNoIdentity() {
        val m = StatusIdentityUnboundException().message!!
        assertFalse(m.contains("@") || m.contains("Bearer") || m.contains("user_id") || m.contains("supabase", true))
    }

    @Test fun explicitlyBoundPublishesOverTheLivePlane() = runBlocking {
        val s = svc { true }
        assertTrue(s.isAvailable())
        s.publishText("hello")
        assertEquals(1, http)
        s.deleteStatus(rec)
        assertEquals(2, http)
        s.watchActiveStatuses().first()
        assertEquals(3, http) // the status feed poll ran
        assertEquals(0, uploads)
    }
}
