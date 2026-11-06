package com.example.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.data.local.AttachmentCachePolicy
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Offline Android JSON implementation only. No backend, user content, camera or microphone. */
@RunWith(AndroidJUnit4::class)
class MediaEnvelopeRestrictionParserProofTest {
    private fun parse(flag: Any?, present: Boolean = true, type: String = "image"): ChatMessage {
        val envelope = JSONObject().put("type", type).put("url", "https://offline.invalid/synthetic")
            .put("name", "fixture").put("size", "1 KB").put("text", "caption")
        if (present) envelope.put("view_once", flag ?: JSONObject.NULL)
        return ChatMessage.fromApi(mapOf("uid" to "message-real-id", "room_id" to "room-real-id",
            "sender_id" to "sender-real-id", "sender_name" to "Synthetic sender",
            "content" to envelope.toString(), "created_at" to "2026-10-01T00:00:00Z"))
    }
    @Test fun imageAndVideoProtectionSurvivesRealEnvelopeParser() {
        for (type in listOf("image", "video")) {
            val m = parse(true, type = type)
            assertEquals(if (type == "image") MessageType.image else MessageType.video, m.type)
            assertEquals(true, m.metadata["view_once"])
            assertTrue(AttachmentCachePolicy.viewOnceRestricted(m.metadata))
            assertFalse(AttachmentCachePolicy.mayPersist(AttachmentCachePolicy.viewOnceRestricted(m.metadata), false))
            assertEquals("message-real-id", m.id); assertEquals("message-real-id", m.attachment?.id)
            assertEquals("room-real-id", m.conversationId); assertEquals("caption", m.text)
            assertEquals("Synthetic sender", m.metadata["sender_name"])
        }
    }
    @Test fun malformedProtectionRemainsRestrictedRatherThanDisappearing() {
        val malformed = listOf<Any>(1, -1, 0, "", "unknown", JSONObject().put("value", true), org.json.JSONArray().put(true))
        for (flag in malformed) {
            val m = parse(flag)
            assertTrue("Protection flag disappeared: $flag", m.metadata.containsKey("view_once"))
            assertTrue("Malformed flag became unrestricted: $flag", AttachmentCachePolicy.viewOnceRestricted(m.metadata))
        }
    }
    @Test fun canonicalFalseAndAbsentFollowCurrentCachePolicyWithoutInventingProtection() {
        for (flag in listOf<Any?>(false, "false", " FALSE ", null)) {
            assertFalse(AttachmentCachePolicy.viewOnceRestricted(parse(flag).metadata))
        }
        assertFalse(parse(true, present = false).metadata.containsKey("view_once"))
        assertFalse(AttachmentCachePolicy.viewOnceRestricted(parse(true, present = false).metadata))
        assertTrue(AttachmentCachePolicy.viewOnceRestricted(parse(" TrUe ").metadata))
    }
    @Test fun forwardedAndUnknownHintsDoNotEraseProtectionOrBecomeAuthority() {
        val json = JSONObject().put("type", "image").put("url", "https://offline.invalid/synthetic")
            .put("view_once", true).put("forwarded", true).put("future_flag", "untrusted")
        val m = ChatMessage.fromApi(mapOf("id" to "real-message", "content" to json.toString()))
        assertEquals(true, m.metadata["forwarded"])
        assertFalse(m.metadata.containsKey("future_flag"))
        assertEquals(true, m.metadata["unsupported_envelope_metadata"])
        assertTrue(AttachmentCachePolicy.viewOnceRestricted(m.metadata))
        assertFalse(m.metadata.containsKey("url"))
        assertFalse(m.metadata.containsKey("type"))
    }
}
