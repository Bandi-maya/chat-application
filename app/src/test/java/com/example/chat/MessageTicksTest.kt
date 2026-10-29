package com.example.chat

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.DeliveryState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tick mapping from backend_python message payloads (one tick / two grey / blue). */
class MessageTicksTest {
    private fun msg(vararg extra: Pair<String, Any?>) = ChatMessage.fromApi(
        mapOf("uid" to "m1", "room_id" to "r1", "sender_id" to "me", "content" to "hi",
            "created_at" to "2026-10-29T10:00:00Z") + extra,
    )

    @Test fun `server has it but other phone does not - single tick`() =
        assertEquals(DeliveryState.sent, msg("is_read" to false, "is_delivered" to false).deliveryState)

    @Test fun `reached the other phone - two grey ticks`() =
        assertEquals(DeliveryState.delivered, msg("is_read" to false, "is_delivered" to true).deliveryState)

    @Test fun `read - blue ticks`() =
        assertEquals(DeliveryState.read, msg("is_read" to true, "is_delivered" to true).deliveryState)

    @Test fun `older server without is_delivered keeps the previous default`() =
        assertEquals(DeliveryState.delivered, msg("is_read" to false).deliveryState)
}
