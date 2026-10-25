package com.example.chat

import com.example.chat.core.validators.ChatyValidators
import com.example.chat.data.local.SnapshotCodec
import com.example.chat.data.messaging.PendingSecureSend
import com.example.chat.data.messaging.PendingSecureSendStore
import com.example.chat.data.messaging.SnapshotCacheStore
import com.example.chat.data.messaging.TasksRepository
import com.example.chat.data.services.CallHistoryService
import com.example.chat.data.services.ChatMediaService
import com.example.chat.data.services.PresenceService
import com.example.chat.domain.models.CallDirection
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.EncryptionStatus
import com.example.chat.domain.models.MessageType
import com.example.chat.domain.models.PresenceState
import com.example.chat.domain.models.PrivacyPreferencesSnapshot
import com.example.chat.domain.models.PrivacyPublication
import com.example.chat.domain.models.PrivacyPublicationPolicy
import com.example.chat.domain.models.TaskPriority
import com.example.chat.domain.models.TaskStatus
import com.example.chat.domain.models.messageTypeFromDatabase
import com.example.chat.domain.models.messageTypeToDatabase
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior tests derived from api-branch Dart expectations (not from the
 * Kotlin source): each test names the Dart function whose observable
 * behavior it pins.
 */
class ParityBehaviorTest {

    // ── input_validators.dart ────────────────────────────────────────────

    @Test fun `normalizeUsername matches Dart normalizeUsername`() {
        assertEquals("bandi", ChatyValidators.normalizeUsername("  @@Bandi "))
        assertEquals("", ChatyValidators.normalizeUsername(" @@@ "))
    }

    @Test fun `validateUsername reserved only for registration`() {
        assertEquals(
            "This username is reserved and cannot be registered",
            ChatyValidators.validateUsername("admin"),
        )
        assertNull(ChatyValidators.validateUsername("admin", forRegistration = false))
        assertEquals(
            "Username must be at least 3 characters",
            ChatyValidators.validateUsername("ab"),
        )
        assertEquals(
            "Only letters, numbers, and underscores are allowed",
            ChatyValidators.validateUsername("bad-name"),
        )
    }

    @Test fun `validatePassword enforces the Dart rule chain`() {
        assertEquals(
            "Password must be at least 12 characters",
            ChatyValidators.validatePassword("Short1!"),
        )
        assertNull(ChatyValidators.validatePassword("ValidPass123!"))
        assertEquals(
            "Password must include a symbol",
            ChatyValidators.validatePassword("NoSymbol1234a"),
        )
    }

    // ── backend_service.dart task status/priority mappings ───────────────

    @Test fun `task priority db mapping keeps medium-normal asymmetry`() {
        // _taskPriorityToDatabase: medium -> 'normal'
        assertEquals("normal", TasksRepository.taskPriorityToDatabase(TaskPriority.medium))
        // _taskPriorityFromDatabase: 'normal' -> medium (default)
        assertEquals(TaskPriority.medium, TasksRepository.taskPriorityFromDatabase("normal"))
        assertEquals(TaskPriority.medium, TasksRepository.taskPriorityFromDatabase(null))
        assertEquals(TaskPriority.urgent, TasksRepository.taskPriorityFromDatabase("urgent"))
    }

    @Test fun `task status db mapping keeps archived-cancelled asymmetry`() {
        // taskStatusFromDatabase: 'cancelled' AND 'archived' -> archived
        assertEquals(TaskStatus.archived, TasksRepository.taskStatusFromDatabase("cancelled"))
        assertEquals(TaskStatus.archived, TasksRepository.taskStatusFromDatabase("archived"))
        assertEquals(TaskStatus.inbox, TasksRepository.taskStatusFromDatabase("anything-else"))
        // taskStatusToDatabase: archived -> 'cancelled'
        assertEquals("cancelled", TasksRepository.taskStatusToDatabase(TaskStatus.archived))
        assertEquals("inbox", TasksRepository.taskStatusToDatabase(TaskStatus.inbox))
    }

    // ── chat_message.dart message type mappings ──────────────────────────

    @Test fun `message type db round trip preserves taskCard-task mapping`() {
        assertEquals("task", messageTypeToDatabase(MessageType.taskCard))
        assertEquals(MessageType.taskCard, messageTypeFromDatabase("task"))
        assertEquals(MessageType.text, messageTypeFromDatabase("unknown"))
    }

    // ── backend_service.dart presence publication rules ──────────────────

    @Test fun `effectivePublishedPresence hides online when nobody-audience`() {
        val hidden = PresenceService.PrivacySnapshot(
            freezeLastSeen = false,
            hideLastSeenAudience = "Nobody",
            hideOnlineAudience = "Same as Last Seen",
        )
        assertEquals(
            PresenceState.offline,
            PresenceService.effectivePublishedPresence(PresenceState.online, hidden),
        )
        assertEquals(
            PresenceState.offline,
            PresenceService.effectivePublishedPresence(PresenceState.typing, hidden),
        )
        // Away is not forced offline by that rule.
        assertEquals(
            PresenceState.away,
            PresenceService.effectivePublishedPresence(PresenceState.away, hidden),
        )
        // Null privacy publishes unchanged (controller unregistered path).
        assertEquals(
            PresenceState.online,
            PresenceService.effectivePublishedPresence(PresenceState.online, null),
        )
    }

    // ── privacy_publication_policy.dart ──────────────────────────────────

    @Test fun `publication policy answers before any network operation`() {
        val policy = PrivacyPublicationPolicy()
        val prefs = PrivacyPreferencesSnapshot(
            readReceipts = false,
            typingIndicators = true,
            hideViewStatus = true,
            freezeLastSeen = true,
            hideLastSeenAudience = "Nobody",
            hideOnlineAudience = "Same as Last Seen",
        )
        assertFalse(policy.allows(PrivacyPublication.readReceipt, prefs))
        assertTrue(policy.allows(PrivacyPublication.typing, prefs))
        assertFalse(policy.allows(PrivacyPublication.statusView, prefs))
        assertFalse(policy.allows(PrivacyPublication.presence, prefs))
        assertFalse(policy.allows(PrivacyPublication.lastSeen, prefs))
    }

    // ── snapshot_codec.dart ──────────────────────────────────────────────

    @Test fun `snapshot conversation round trip and unknown enum fallback`() {
        val json = mapOf<String, Any?>(
            "id" to "c1",
            "type" to "group",
            "title" to "T",
            "participant_ids" to listOf("a", "b"),
            "admin_ids" to listOf<String>(),
            "last_message_text" to "hi",
            "last_message_time" to "2026-10-25T07:00:00Z",
            "last_message_sender_id" to "a",
            "unread_count" to 3,
            "is_pinned" to true,
            "is_archived" to false,
            "is_muted" to false,
            "disappearing_seconds" to "3600",
            "encryption_status" to "totally-new-future-value",
            "draft_text" to "draft",
        )
        val c = SnapshotCodec.conversationFromJson(json)
        assertEquals(ConversationType.group, c.type)
        assertEquals(3, c.unreadCount)
        // Unknown enum falls back to the safe default, never crashes.
        assertEquals(EncryptionStatus.verificationNeeded, c.encryptionStatus)
        assertEquals(3600, c.disappearingDuration?.seconds)
        val back = SnapshotCodec.conversationToJson(c)
        assertEquals(json["id"], back["id"])
        assertEquals("3600", back["disappearing_seconds"])
        assertEquals("verificationNeeded", back["encryption_status"])
        // Null optionals are omitted, matching the Dart encoder.
        assertFalse(back.containsKey("wallpaper_id"))
    }

    @Test fun `snapshot message codec keeps delivery state default delivered`() {
        val m = SnapshotCodec.chatMessageFromJson(
            mapOf(
                "id" to "m1",
                "conversation_id" to "c1",
                "sender_id" to "u1",
                "type" to "taskCard",
                "text" to "hello",
                "created_at" to "2026-10-25T07:00:00Z",
                "delivery_state" to "future-unknown-state",
            ),
        )
        assertEquals(MessageType.taskCard, m.type)
        assertEquals(DeliveryState.delivered, m.deliveryState)
        assertTrue(m.reactions.isEmpty())
        val back = SnapshotCodec.chatMessageToJson(m)
        assertEquals("taskCard", back["type"])
        assertFalse(back.containsKey("edited_at"))
    }

    // ── pending_secure_send_store.dart queue behavior ────────────────────

    class FakeCache : SnapshotCacheStore {
        val data = HashMap<String, Any?>()
        override suspend fun readJson(userId: String, scope: String): Any? = data["$userId/$scope"]
        override suspend fun writeJson(userId: String, scope: String, value: Any?) {
            data["$userId/$scope"] = value
        }
    }

    private fun pending(id: String, createdAt: String) = PendingSecureSend(
        clientMessageId = id,
        conversationId = "c1",
        type = "text",
        text = "t$id",
        metadata = emptyMap(),
        createdAt = Instant.parse(createdAt),
    )

    @Test fun `pending send store dedupes by client message id and sorts`() = runTest {
        val cache = FakeCache()
        val store = PendingSecureSendStore(cache)
        store.put("u1", pending("b", "2026-10-25T07:02:00Z"))
        store.put("u1", pending("a", "2026-10-25T07:01:00Z"))
        store.put("u1", pending("b", "2026-10-25T07:03:00Z"))
        val items = store.read("u1")
        assertEquals(2, items.size)
        // Sorted by createdAt after dedupe: a, then the replaced b.
        assertEquals(listOf("a", "b"), items.map { it.clientMessageId })
        assertEquals(Instant.parse("2026-10-25T07:03:00Z"), items[1].createdAt)
    }

    @Test fun `pending send store enforces the 128 cap`() = runTest {
        val cache = FakeCache()
        val store = PendingSecureSendStore(cache)
        repeat(128) { i ->
            store.put("u1", pending("id$i", "2026-10-25T07:00:00Z"))
        }
        var threw = false
        try {
            store.put("u1", pending("overflow", "2026-10-25T07:00:00Z"))
        } catch (e: IllegalStateException) {
            threw = true
            assertEquals("Secure pending-send queue is full.", e.message)
        }
        assertTrue(threw)
    }

    // ── call_history_service.dart mapCallHistoryRow ──────────────────────

    @Test fun `call history maps unconnected inbound as missed even when ended`() {
        val row = mapOf<String, Any?>(
            "id" to "call1",
            "caller_id" to "other",
            "callee_id" to "me",
            "status" to "ended",
            "kind" to "voice",
            "started_at" to "2026-10-25T07:00:00Z",
            // no connected_at / ended_at
        )
        val record = CallHistoryService.mapCallHistoryRow(row, "me")!!
        assertEquals(CallDirection.missed, record.direction)
        assertEquals(0, record.durationSeconds)
        // Non-terminal rows never surface.
        assertNull(
            CallHistoryService.mapCallHistoryRow(row + ("status" to "ringing"), "me"),
        )
        // Rows not involving the current user never surface.
        assertNull(CallHistoryService.mapCallHistoryRow(row, "someone-else"))
    }

    // ── chat_media_service.dart helpers ──────────────────────────────────

    @Test fun `media helpers match Dart formatting and limits`() {
        assertEquals("999 B", ChatMediaService.formatBytes(999))
        assertEquals("1.5 KB", ChatMediaService.formatBytes(1536))
        assertEquals("2.0 MB", ChatMediaService.formatBytes(2 * 1024 * 1024))
        assertEquals("attachment", ChatMediaService.safeFileName("///"))
        assertEquals("a_b.jpg", ChatMediaService.safeFileName("a  b.jpg"))
        val longName = "x".repeat(200)
        assertEquals(120, ChatMediaService.safeFileName(longName).length)
        assertEquals("image/jpeg", ChatMediaService.fallbackMimeForType("image"))
        assertEquals("application/octet-stream", ChatMediaService.fallbackMimeForType("file"))
    }

    @Test fun `media maxBytes follows image and audio preference chains`() {
        val service = ChatMediaService(
            storage = throwawayStorage(),
            currentUserId = { "u1" },
            sendImagesFullResolutionMb = { 6 },
            gbSizeLimitMb = { key, fallback ->
                if (key == "abo_saleh_audio_limit_check") 200.0 else fallback
            },
        )
        // image: default Up_size_limit falls back to fullResMb (6) -> 6 MB.
        assertEquals(6L * 1024 * 1024, service.maxBytes("image"))
        // audio: configured 200 clamps to the 50 MB storage ceiling.
        assertEquals(50L * 1024 * 1024, service.maxBytes("audio"))
        // video: fallback 50 MB.
        assertEquals(50L * 1024 * 1024, service.maxBytes("video"))
    }

    private fun throwawayStorage(): com.example.chat.data.remote.SupabaseStorageClient =
        com.example.chat.data.remote.SupabaseStorageClient(
            supabaseUrl = "https://example.invalid",
            publishableKey = "k",
            accessToken = { null },
        )
}
