package com.example.chat

import com.example.chat.data.api.ApiStatusClient
import com.example.chat.data.services.StatusService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM checks for the status payload codec (encodePayload/fromPayload)
 *  and the helpers that survived the move to the live backend's status
 *  plane (_safeFileName/_fallbackMime/dartRound). No network, no account. */
class StatusServiceRowParityTest {

    private fun entry(userId: String = "u1") = ApiStatusClient.StatusFeedEntry(
        userId = userId, username = "user", displayName = "User", own = true, posts = emptyList(),
    )

    private fun post(payload: String, id: String = "s1") = ApiStatusClient.StatusPostEntry(
        id = id,
        payload = payload,
        createdAt = "2026-10-27T01:00:00Z",
        expiresAt = "2026-10-28T01:00:00Z",
    )

    @Test fun textPayloadRoundTrips() {
        val payload = StatusService.encodePayload(text = "hello world", mediaType = "text")
        val r = StatusService.fromPayload(post(payload), entry())
        assertEquals("s1", r.id)
        assertEquals("u1", r.userId)
        assertEquals("hello world", r.text)
        assertEquals("text", r.mediaType)
        assertNull(r.mediaPath)
        assertEquals(0L, r.mediaSize)
        assertEquals(0L, r.durationSeconds)
        assertNull(r.deletedAt)
        assertFalse(r.hasMedia)
        assertFalse(r.isDeleted)
        assertEquals(24 * 3600L, r.expiresAt.epochSecond - r.createdAt.epochSecond)
    }

    @Test fun mediaPayloadRoundTrips() {
        val payload = StatusService.encodePayload(
            text = "look",
            mediaType = "image",
            mediaPath = "https://api.example/uploads/chat_ab12.jpg",
            mediaName = "pic.jpg",
            mimeType = "image/jpeg",
            mediaSize = 1024,
            durationSeconds = 9,
        )
        val r = StatusService.fromPayload(post(payload), entry())
        assertTrue(r.hasMedia)
        assertEquals("image", r.mediaType)
        assertEquals("https://api.example/uploads/chat_ab12.jpg", r.mediaPath)
        assertEquals("pic.jpg", r.mediaName)
        assertEquals(1024L, r.mediaSize)
        assertEquals(9L, r.durationSeconds)
    }

    @Test fun foreignPayloadSurfacesAsPlainText() {
        // Payloads not written by this client (no JSON shape) must still be readable.
        val r = StatusService.fromPayload(post("just some words"), entry())
        assertEquals("just some words", r.text)
        assertEquals("text", r.mediaType)
        assertFalse(r.hasMedia)
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
