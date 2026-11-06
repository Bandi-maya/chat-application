package com.example.chat

import com.example.chat.data.services.StatusService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM parity checks for status_service.dart _fromRow/_safeFileName/_fallbackMime.
 *  No network, no account. Source vs api: lib/data/services/status_service.dart:437-481. */
class StatusServiceRowParityTest {

    @Test fun fromRowDefaultsMatchDart() {
        val r = StatusService.fromRow(emptyMap())
        assertEquals("", r.id)
        assertEquals("text", r.mediaType)
        assertNull(r.mediaPath)
        assertEquals(0L, r.mediaSize)
        assertEquals(0L, r.durationSeconds)
        assertNull(r.deletedAt)
        assertFalse(r.hasMedia)
        assertFalse(r.isDeleted)
    }

    @Test fun fromRowNumericFieldsRoundLikeDart() {
        // Dart: (num).round() -> 2.6 becomes 3, 2.4 becomes 2.
        val r = StatusService.fromRow(mapOf("media_size" to 2.6, "duration_seconds" to 7.5))
        assertEquals(3L, r.mediaSize)
        assertEquals(8L, r.durationSeconds)
        val s = StatusService.fromRow(mapOf("media_size" to "1024", "duration_seconds" to "9"))
        assertEquals(1024L, s.mediaSize)
        assertEquals(0L, s.durationSeconds) // Dart: non-num duration -> 0
    }

    @Test fun dartRoundHalfAwayFromZero() {
        assertEquals(3L, StatusService.dartRound(2.5))
        assertEquals(-3L, StatusService.dartRound(-2.5))
        assertEquals(-2L, StatusService.dartRound(-2.4))
        assertEquals(-3L, StatusService.dartRound(-2.6))
        assertEquals(0L, StatusService.dartRound(0.4))
        assertEquals(5L, StatusService.dartRound(5))
        assertEquals(4_294_967_297L, StatusService.dartRound(4_294_967_296.5))
    }

    @Test fun fromRowNegativeHalfMatchesDart() {
        val r = StatusService.fromRow(mapOf("media_size" to -2.5, "duration_seconds" to -0.5))
        assertEquals(-3L, r.mediaSize)
        assertEquals(-1L, r.durationSeconds)
    }

    @Test fun fromRowMediaAndDeleted() {
        val r = StatusService.fromRow(
            mapOf(
                "id" to "s1", "user_id" to "u1", "content" to "hi", "media_type" to "image",
                "media_path" to "u1/a.jpg", "created_at" to "2026-10-27T01:00:00Z",
                "expires_at" to "2026-10-28T01:00:00Z", "deleted_at" to "2026-10-27T02:00:00Z",
            ),
        )
        assertTrue(r.hasMedia)
        assertTrue(r.isDeleted)
        assertEquals(24 * 3600L, r.expiresAt.epochSecond - r.createdAt.epochSecond)
    }

    @Test fun safeFileName() {
        assertEquals("status", StatusService.safeFileName(""))
        assertEquals("my_photo_1_.jpg", StatusService.safeFileName("my photo (1).jpg"))
        assertEquals("a_b", StatusService.safeFileName("a///b"))
        val long = "x".repeat(200) + ".png"
        val out = StatusService.safeFileName(long)
        assertEquals(120, out.length)
        assertTrue(out.endsWith(".png"))
    }

    @Test fun fallbackMime() {
        assertEquals("image/jpeg", StatusService.fallbackMime("image"))
        assertEquals("video/mp4", StatusService.fallbackMime("video"))
        assertEquals("audio/mp4", StatusService.fallbackMime("audio"))
        assertEquals("application/octet-stream", StatusService.fallbackMime("text"))
    }
}
