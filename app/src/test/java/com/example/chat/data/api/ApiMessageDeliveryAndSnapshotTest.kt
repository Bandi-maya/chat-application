package com.example.chat.data.api

import com.example.chat.data.messaging.SnapshotCacheStore
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageType
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.detail.ChatTimelinePositionPolicy
import com.example.chat.ui.chats.detail.MediaTimelineRow
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiMessageDeliveryAndSnapshotTest {

    private class FakeSnapshotCacheStore : SnapshotCacheStore {
        val data = mutableMapOf<String, Any?>()

        override suspend fun writeJson(userId: String, scope: String, value: Any?) {
            data["$userId|$scope"] = value
        }

        override suspend fun readJson(userId: String, scope: String): Any? =
            data["$userId|$scope"]
    }

    private fun message(
        id: String = "m1",
        roomId: String = "room1",
        senderId: String = "me",
        text: String = "hello",
        state: DeliveryState = DeliveryState.sent,
        at: Instant = Instant.parse("2026-11-12T10:00:00Z"),
    ) = ChatMessage(
        id = id,
        conversationId = roomId,
        senderId = senderId,
        type = MessageType.text,
        text = text,
        createdAt = at,
        deliveryState = state,
    )

    private fun conversation(
        id: String = "room1",
        title: String = "Alice",
        lastText: String = "hello",
        lastTime: Instant = Instant.parse("2026-11-12T10:00:00Z"),
        unreadCount: Int = 0,
        roomType: String? = "direct",
        taskId: String? = null,
    ) = Conversation(
        id = id,
        type = ConversationType.direct,
        title = title,
        participantIds = listOf("me", "alice"),
        lastMessageText = lastText,
        lastMessageTime = lastTime,
        lastMessageSenderId = "me",
        unreadCount = unreadCount,
        roomType = roomType,
        taskId = taskId,
    )

    @Test
    fun `own websocket echo normalizes server hardcoded delivered down to sent`() {
        val wsEcho = message(senderId = "me", state = DeliveryState.delivered)
        val normalized = ApiMessageDeliveryMerge.normalizeRealtimeMessage(wsEcho, currentUserId = "me")
        assertEquals(DeliveryState.sent, normalized.deliveryState)
    }

    @Test
    fun `own websocket echo keeps read and inbound messages keep delivered`() {
        val ownRead = message(senderId = "me", state = DeliveryState.read)
        assertEquals(
            DeliveryState.read,
            ApiMessageDeliveryMerge.normalizeRealtimeMessage(ownRead, currentUserId = "me").deliveryState,
        )
        val peerDelivered = message(senderId = "alice", state = DeliveryState.delivered)
        assertEquals(
            DeliveryState.delivered,
            ApiMessageDeliveryMerge.normalizeRealtimeMessage(peerDelivered, currentUserId = "me").deliveryState,
        )
    }

    @Test
    fun `reloading room from rest never downgrades delivered or read back to sent`() {
        val localDelivered = message(id = "m1", state = DeliveryState.delivered)
        val localRead = message(id = "m2", state = DeliveryState.read, at = Instant.parse("2026-11-12T10:01:00Z"))
        val pendingSending = message(id = "temp_1", text = "offline", state = DeliveryState.sending, at = Instant.parse("2026-11-12T10:02:00Z"))

        // Backend GET /messages returns is_delivered=false -> DeliveryState.sent for both server rows.
        val remoteRows = listOf(
            message(id = "m1", state = DeliveryState.sent),
            message(id = "m2", state = DeliveryState.sent, at = Instant.parse("2026-11-12T10:01:00Z")),
        )

        val merged = ApiMessageDeliveryMerge.mergeRemoteMessages(
            current = listOf(localDelivered, localRead, pendingSending),
            remote = remoteRows,
            keepPending = { it.deliveryState == DeliveryState.sending },
        )

        assertEquals(3, merged.size)
        assertEquals(DeliveryState.delivered, merged[0].deliveryState)
        assertEquals(DeliveryState.read, merged[1].deliveryState)
        assertEquals("temp_1", merged[2].id)
    }

    @Test
    fun `shouldFetchRoomMessages skips network when synced clean connected and local cache matches`() {
        val t1 = Instant.parse("2026-11-12T10:00:00Z")
        val cached = listOf(message(id = "m1", at = t1))
        val convo = conversation(lastTime = t1, unreadCount = 0)

        assertFalse(
            ApiMessageDeliveryMerge.shouldFetchRoomMessages(
                hasSyncedThisSession = true,
                isDirty = false,
                socketConnected = true,
                conversation = convo,
                currentMessages = cached,
            ),
        )
        assertTrue(
            ApiMessageDeliveryMerge.shouldFetchRoomMessages(
                hasSyncedThisSession = false,
                isDirty = false,
                socketConnected = true,
                conversation = convo,
                currentMessages = cached,
            ),
        )
        assertTrue(
            ApiMessageDeliveryMerge.shouldFetchRoomMessages(
                hasSyncedThisSession = true,
                isDirty = true,
                socketConnected = true,
                conversation = convo,
                currentMessages = cached,
            ),
        )
        assertTrue(
            ApiMessageDeliveryMerge.shouldFetchRoomMessages(
                hasSyncedThisSession = true,
                isDirty = false,
                socketConnected = false,
                conversation = convo,
                currentMessages = cached,
            ),
        )
        assertTrue(
            ApiMessageDeliveryMerge.shouldFetchRoomMessages(
                hasSyncedThisSession = true,
                isDirty = false,
                socketConnected = true,
                conversation = convo.copy(lastMessageTime = t1.plusSeconds(10)),
                currentMessages = cached,
            ),
        )
        assertTrue(
            ApiMessageDeliveryMerge.shouldFetchRoomMessages(
                hasSyncedThisSession = true,
                isDirty = false,
                socketConnected = true,
                conversation = convo.copy(unreadCount = 1),
                currentMessages = cached,
            ),
        )
    }

    @Test
    fun `snapshot coordinator persists conversations messages with ticks and users isolated by namespace`() = runBlocking {
        val store = FakeSnapshotCacheStore()
        val coordinator = ApiSnapshotCacheCoordinator(store)

        val convs = listOf(
            conversation(id = "r1", title = "Alice", roomType = "direct"),
            conversation(id = "task-room", title = "Hidden Task", roomType = "task", taskId = "t1"),
        )
        val users = listOf(
            UserProfile(
                id = "alice",
                displayName = "Alice",
                username = "alice",
                avatarInitials = "A",
                avatarColorHex = "0xFF6366F1",
                about = "Hey",
                lastSeenAt = Instant.parse("2026-11-12T10:00:00Z"),
            ),
        )
        val msgs = listOf(
            message(id = "m1", state = DeliveryState.sent),
            message(id = "m2", state = DeliveryState.delivered, at = Instant.parse("2026-11-12T10:01:00Z")),
            message(id = "m3", state = DeliveryState.read, at = Instant.parse("2026-11-12T10:02:00Z")),
        )

        coordinator.persistConversations("hash-a", convs)
        coordinator.persistUsers("hash-a", users)
        coordinator.persistMessages("hash-a", "r1", msgs)

        val readConvs = coordinator.readConversations("hash-a")
        assertNotNull(readConvs)
        assertEquals(1, readConvs!!.size)
        assertEquals("r1", readConvs[0].id)
        assertEquals("direct", readConvs[0].roomType)

        val readUsers = coordinator.readUsers("hash-a")
        assertEquals(1, readUsers?.size)
        assertEquals("alice", readUsers!![0].id)

        val readMsgs = coordinator.readMessages("hash-a", "r1")
        assertEquals(listOf(DeliveryState.sent, DeliveryState.delivered, DeliveryState.read), readMsgs!!.map { it.deliveryState })

        // Separate account namespace stays completely isolated.
        assertNull(coordinator.readConversations("hash-b"))
        assertNull(coordinator.readMessages("hash-b", "r1"))
    }

    @Test
    fun `mergeRemoteMessages deduplicates remote and pending IDs while preserving highest delivery state`() {
        val t1 = Instant.parse("2026-11-12T10:00:00Z")
        val t2 = Instant.parse("2026-11-12T10:01:00Z")
        val pendingDup = message(id = "m1", text = "hi", state = DeliveryState.sending, at = t1)
        val pendingUnique = message(id = "temp_2", text = "queued", state = DeliveryState.sending, at = t2)
        val remote = listOf(
            message(id = "m1", text = "hi", state = DeliveryState.sent, at = t1),
            message(id = "m1", text = "hi", state = DeliveryState.read, at = t1),
        )

        val merged = ApiMessageDeliveryMerge.mergeRemoteMessages(
            current = listOf(pendingDup, pendingUnique),
            remote = remote,
            keepPending = { it.deliveryState == DeliveryState.sending || it.id.startsWith("temp_") },
        )

        assertEquals(listOf("m1", "temp_2"), merged.map { it.id })
        assertEquals(DeliveryState.read, merged.first().deliveryState)
    }

    @Test
    fun `timeline rowKeys disambiguates duplicate and blank message IDs for LazyColumn`() {
        val t1 = Instant.parse("2026-11-12T10:00:00Z")
        val rows = listOf(
            MediaTimelineRow(listOf(message(id = "dup-1#1", at = t1))),
            MediaTimelineRow(listOf(message(id = "dup-1", at = t1.plusSeconds(1)))),
            MediaTimelineRow(listOf(message(id = "dup-1", at = t1.plusSeconds(2)))),
            MediaTimelineRow(listOf(message(id = "", at = t1.plusSeconds(3)))),
            MediaTimelineRow(listOf(message(id = "", at = t1.plusSeconds(4)))),
            MediaTimelineRow(listOf(message(id = "row#3", at = t1.plusSeconds(5)))),
        )

        val keys = ChatTimelinePositionPolicy.rowKeys(rows)

        assertEquals(rows.size, keys.toSet().size)
        assertTrue(keys.none { it.isBlank() })
        assertEquals("dup-1#1", keys[0])
        assertEquals("dup-1", keys[1])
        assertEquals("dup-1#2", keys[2])
        assertEquals("row#3", keys[3])
        assertEquals("row#4", keys[4])
        assertEquals("row#3#5", keys[5])
    }
}
