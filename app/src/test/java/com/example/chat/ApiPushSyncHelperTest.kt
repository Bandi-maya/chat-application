package com.example.chat

import com.example.chat.data.api.ApiPushSyncHelper
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiPushSyncHelperTest {

    private fun conv(
        id: String,
        unread: Int = 0,
        lastTimeSec: Long = 100L,
    ) = Conversation(
        id = id,
        type = ConversationType.direct,
        title = "Chat $id",
        participantIds = listOf("me", "peer"),
        lastMessageText = "hello",
        lastMessageTime = Instant.ofEpochSecond(lastTimeSec),
        lastMessageSenderId = "peer",
        unreadCount = unread,
    )

    private fun msg(
        id: String,
        roomId: String = "r1",
        senderId: String = "peer",
        timeSec: Long = 100L,
        deliveryState: DeliveryState = DeliveryState.delivered,
        type: MessageType = MessageType.text,
    ) = ChatMessage(
        id = id,
        conversationId = roomId,
        senderId = senderId,
        type = type,
        text = "msg-$id",
        createdAt = Instant.ofEpochSecond(timeSec),
        deliveryState = deliveryState,
    )

    @Test
    fun selectsDirtyUnreadAndAdvancedRoomsOrderedNewestFirst() {
        val before = listOf(
            conv("r1", unread = 0, lastTimeSec = 100),
            conv("r2", unread = 1, lastTimeSec = 200),
            conv("r3", unread = 0, lastTimeSec = 300),
        )
        val after = listOf(
            conv("r1", unread = 1, lastTimeSec = 150),
            conv("r2", unread = 1, lastTimeSec = 200),
            conv("r3", unread = 0, lastTimeSec = 300),
            conv("r4", unread = 2, lastTimeSec = 400),
        )
        val selected = ApiPushSyncHelper.selectRoomsToSyncForPush(
            conversationsBefore = before,
            conversationsAfter = after,
            dirtyRoomIds = setOf("r1"),
            syncedRoomIds = setOf("r2", "r3"),
            cachedMessagesByRoom = mapOf(
                "r2" to listOf(msg("m2", roomId = "r2", timeSec = 200)),
                "r3" to listOf(msg("m3", roomId = "r3", timeSec = 300)),
            ),
        )
        assertEquals(listOf("r4", "r1"), selected.map { it.id })
    }

    @Test
    fun coldUnreadRoomWithoutPriorCacheNotifiesUpToUnreadCount() {
        val c = conv("r1", unread = 2, lastTimeSec = 300)
        val fetched = listOf(
            msg("old", timeSec = 100, deliveryState = DeliveryState.read),
            msg("mine", senderId = "me", timeSec = 150),
            msg("new1", timeSec = 200),
            msg("new2", timeSec = 300),
        )
        val notify = ApiPushSyncHelper.findNewIncomingMessagesForPush(
            myUserId = "me",
            conversation = c,
            messagesBefore = emptyList(),
            messagesAfter = fetched,
            alreadyNotifiedIds = emptySet(),
        )
        assertEquals(listOf("new1", "new2"), notify.map { it.id })
    }

    @Test
    fun coldReadRoomWithoutPriorCacheSuppressesHistoricalMessages() {
        val c = conv("r1", unread = 0, lastTimeSec = 300)
        val fetched = listOf(
            msg("m1", timeSec = 100, deliveryState = DeliveryState.read),
            msg("m2", timeSec = 200, deliveryState = DeliveryState.read),
        )
        val notify = ApiPushSyncHelper.findNewIncomingMessagesForPush(
            myUserId = "me",
            conversation = c,
            messagesBefore = emptyList(),
            messagesAfter = fetched,
            alreadyNotifiedIds = emptySet(),
        )
        assertTrue(notify.isEmpty())
    }

    @Test
    fun incrementalPushDeduplicatesKnownAndAlreadyNotifiedIds() {
        val c = conv("r1", unread = 2, lastTimeSec = 400)
        val before = listOf(msg("m1", timeSec = 100))
        val fetched = listOf(
            msg("m1", timeSec = 100),
            msg("m2", timeSec = 200),
            msg("m3", senderId = "me", timeSec = 250),
            msg("m4", timeSec = 300),
            msg("m5", type = MessageType.system, timeSec = 350),
        )
        val notify = ApiPushSyncHelper.findNewIncomingMessagesForPush(
            myUserId = "me",
            conversation = c,
            messagesBefore = before,
            messagesAfter = fetched,
            alreadyNotifiedIds = setOf("m2"),
        )
        assertEquals(listOf("m4"), notify.map { it.id })
    }
}
