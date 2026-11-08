package com.example.chat

import com.example.chat.data.api.ApiCallHistoryClient
import com.example.chat.data.services.ApiCallHistoryService
import com.example.chat.domain.models.CallDirection
import com.example.chat.domain.models.CallType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Server call-history entry -> Calls-tab row mapping (research 5.4). */
class ApiCallHistoryServiceTest {

    private fun entry(
        direction: String,
        status: String,
        kind: String = "audio",
        other: String = "user-b",
        startedAt: String? = "2026-11-08T07:30:00Z",
        durationSec: Int = 95,
    ) = ApiCallHistoryClient.CallEntry(
        id = "log-1", callId = "call-1", direction = direction,
        otherUserId = other, otherUsername = "bob", otherDisplayName = "Bob",
        kind = kind, status = status, startedAt = startedAt, durationSec = durationSec,
    )

    @Test
    fun `outgoing answered video call`() {
        val r = ApiCallHistoryService.mapEntry(entry("outgoing", "answered", kind = "video"), "me")!!
        assertEquals(CallDirection.outgoing, r.direction)
        assertEquals(CallType.video, r.type)
        assertEquals("me", r.callerId)
        assertEquals(listOf("me", "user-b"), r.participantIds)
        assertEquals(95L, r.durationSeconds)
        assertEquals("log-1", r.id)
        assertEquals(1794123000L, r.timestamp.epochSecond)
    }

    @Test
    fun `incoming answered call is incoming`() {
        val r = ApiCallHistoryService.mapEntry(entry("incoming", "answered"), "me")!!
        assertEquals(CallDirection.incoming, r.direction)
        assertEquals("user-b", r.callerId)
    }

    @Test
    fun `incoming missed declined and failed all show as missed`() {
        for (status in listOf("missed", "declined", "failed")) {
            assertEquals(
                status,
                CallDirection.missed,
                ApiCallHistoryService.mapEntry(entry("incoming", status), "me")!!.direction,
            )
        }
    }

    @Test
    fun `empty other user drops the row`() {
        assertNull(ApiCallHistoryService.mapEntry(entry("incoming", "answered", other = ""), "me"))
    }

    @Test
    fun `unparseable timestamp keeps the row with a fallback`() {
        val r = ApiCallHistoryService.mapEntry(entry("outgoing", "answered", startedAt = "junk"), "me")!!
        assertTrue(r.timestamp.epochSecond > 0)
    }

    @Test
    fun `negative duration clamps to zero`() {
        assertEquals(0L, ApiCallHistoryService.mapEntry(entry("outgoing", "answered", durationSec = -5), "me")!!.durationSeconds)
    }
}
