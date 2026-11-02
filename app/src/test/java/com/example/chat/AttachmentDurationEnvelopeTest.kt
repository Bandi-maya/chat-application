package com.example.chat

import com.example.chat.domain.models.ChatMessage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AttachmentDurationEnvelopeTest {
    private fun duration(value: Any?, include: Boolean = true): Int {
        val envelope = JSONObject().put("type", "audio").put("url", "/synthetic/voice.m4a")
            .put("id", "synthetic-audio").put("text", "")
        if (include) envelope.put("duration", value ?: JSONObject.NULL)
        return ChatMessage.fromApi(mapOf("id" to "synthetic", "room_id" to "synthetic", "sender_id" to "synthetic",
            "created_at" to "2026-01-01T00:00:00Z", "content" to envelope.toString())).attachment!!.durationSeconds
    }
    @Test fun serverEchoPreservesPositiveWholeSeconds() { assertEquals(17, duration(17)); assertEquals(1, duration(1L)) }
    @Test fun missingAndZeroRemainUnknown() { assertEquals(0, duration(null, false)); assertEquals(0, duration(null)); assertEquals(0, duration(0)) }
    @Test fun malformedAndOutOfRangeRemainUnknown() {
        for (value in listOf(-1, 1.5, "17", "bad", true, 2147483648L)) assertEquals("value=$value", 0, duration(value))
    }
}
