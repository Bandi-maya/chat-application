package com.example.chat

import com.example.chat.ui.chats.PresenceLineLogic
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Presence header line (research 5.2): online / exact last seen /
 *  approximation bucket, fail-closed on anything else. */
class PresenceLineLogicTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-11-08T08:00:00Z") // 13:30 IST

    @Test
    fun `online wins over everything`() {
        assertEquals(
            "online",
            PresenceLineLogic.headerText(true, now, "recently", now, zone),
        )
    }

    @Test
    fun `known buckets map to Telegram-style wording`() {
        assertEquals("last seen recently", PresenceLineLogic.approxBucketText("recently"))
        assertEquals("last seen within a week", PresenceLineLogic.approxBucketText("within_week"))
        assertEquals("last seen within a month", PresenceLineLogic.approxBucketText("within_month"))
        assertEquals("last seen a long time ago", PresenceLineLogic.approxBucketText("long_ago"))
    }

    @Test
    fun `unknown bucket hides the line`() {
        assertNull(PresenceLineLogic.approxBucketText("yesterday"))
        assertNull(PresenceLineLogic.approxBucketText(null))
        assertNull(PresenceLineLogic.headerText(false, null, "sometime", now, zone))
    }

    @Test
    fun `bucket beats a stale exact instant`() {
        assertEquals(
            "last seen recently",
            PresenceLineLogic.headerText(false, Instant.parse("2026-11-01T08:00:00Z"), "recently", now, zone),
        )
    }

    @Test
    fun `exact last seen today formats with clock time`() {
        // 06:30 UTC = 12:00 PM IST same day.
        assertEquals(
            "last seen today at 12:00 PM",
            PresenceLineLogic.headerText(false, Instant.parse("2026-11-08T06:30:00Z"), null, now, zone),
        )
    }

    @Test
    fun `exact last seen yesterday`() {
        assertEquals(
            "last seen yesterday at 12:00 PM",
            PresenceLineLogic.headerText(false, Instant.parse("2026-11-07T06:30:00Z"), null, now, zone),
        )
    }

    @Test
    fun `nothing visible yields no line`() {
        assertNull(PresenceLineLogic.headerText(false, null, null, now, zone))
    }
}
