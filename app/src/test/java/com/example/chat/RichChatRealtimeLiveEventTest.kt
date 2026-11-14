package com.example.chat

import com.example.chat.data.realtime.EventNotificationIcon
import com.example.chat.data.realtime.EventNotificationSink
import com.example.chat.data.realtime.RealtimeBackend
import com.example.chat.data.realtime.RealtimePreferences
import com.example.chat.data.realtime.RichChatReadPlane
import com.example.chat.data.realtime.RichChatRealtimeService
import com.example.chat.domain.models.ContactEventAlertOverride
import com.example.chat.domain.models.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live-socket wiring: onLivePresenceChanged / onLiveTypingChanged feed the
 *  same gated toast path as the postgres-changes plane. */
class RichChatRealtimeLiveEventTest {

    private class FakePrefs(
        var online: Boolean = true,
        var typing: Boolean = true,
        var overrides: Map<String, ContactEventAlertOverride> = emptyMap(),
        val gb: Map<String, Boolean> = emptyMap(),
    ) : RealtimePreferences {
        override val enableGlobalNotifications = true
        override val showMessagePreview = true
        override val notifyContactOnline get() = online
        override val notifyTypingStarted get() = typing
        override val notifyMessageDeleted = true
        override val messageRevokeAlert = false
        override val freezeLastSeen = false
        override val hideLastSeenAudience = "Everyone"
        override val hideOnlineAudience = "Everyone"
        override val recordingIndicators = true
        override val ghostMode = false
        override fun gbBool(key: String, fallback: Boolean): Boolean = gb[key] ?: fallback
        override fun gbColorArgb(key: String): Long? = null
        override fun contactEventAlertOverride(userId: String): ContactEventAlertOverride? = overrides[userId]
    }

    private class FakeBackend : RealtimeBackend {
        var directConversation: String? = "convo-1"
        var protectedConversations = setOf<String>()
        var mutedConversations = setOf<String>()
        override fun getUserById(userId: String): UserProfile? = null
        override fun isConversationMuted(conversationId: String): Boolean = conversationId in mutedConversations
        override fun directConversationIdForUser(userId: String): String? = directConversation
        override fun isConversationProtected(conversationId: String): Boolean = conversationId in protectedConversations
    }

    private class RecordingSink : EventNotificationSink {
        val titles = mutableListOf<String>()
        override fun triggerEventNotification(
            title: String, body: String, icon: EventNotificationIcon, colorArgb: Long,
            textColorArgb: Long?, userId: String?, avatarInitials: String?, avatarColorHex: String?,
        ) {
            titles.add(title)
        }
    }

    private val emptyReadPlane = object : RichChatReadPlane {
        override suspend fun presenceVisibilityRows(): List<Map<String, Any?>> = emptyList()
        override suspend fun typingStateRows(conversationId: String): List<Map<String, Any?>> = emptyList()
        override suspend fun recentMessageRows(conversationId: String): List<Map<String, Any?>> = emptyList()
        override suspend fun receiptRows(messageIds: List<String>): List<Map<String, Any?>> = emptyList()
    }

    private fun service(
        prefs: FakePrefs = FakePrefs(),
        backend: FakeBackend = FakeBackend(),
        sink: RecordingSink = RecordingSink(),
    ): RichChatRealtimeService = RichChatRealtimeService(
        preferences = prefs,
        notifications = sink,
        backend = backend,
        readPlane = emptyReadPlane,
        markDeliveredRpc = {},
        setRecordingRpc = { _, _ -> },
        channelFactory = { _, _, _, _ -> object : com.example.chat.data.remote.RealtimeChannel {
            override fun close() {}
        } },
        currentUserId = { "me" },
    )

    @Test
    fun offlineToOnlineFiresToastOncePerTransition() {
        val sink = RecordingSink()
        val svc = service(sink = sink)
        svc.onLivePresenceChanged("user-1", true)
        svc.onLivePresenceChanged("user-1", true) // duplicate event: still online
        assertEquals(1, sink.titles.size)
        svc.onLivePresenceChanged("user-1", false)
        svc.onLivePresenceChanged("user-1", true)
        assertEquals(2, sink.titles.size)
    }

    @Test
    fun onlineToastRespectsGlobalToggleAndSelf() {
        val sink = RecordingSink()
        val svc = service(prefs = FakePrefs(online = false), sink = sink)
        svc.onLivePresenceChanged("user-1", true)
        svc.onLivePresenceChanged("me", true)
        assertTrue(sink.titles.isEmpty())
    }

    @Test
    fun onlineToastSilencedForProtectedMutedOrOpenChat() {
        val sink = RecordingSink()
        val backend = FakeBackend()
        val svc = service(backend = backend, sink = sink)
        backend.protectedConversations = setOf("convo-1")
        svc.onLivePresenceChanged("user-1", true)
        backend.protectedConversations = emptySet()
        backend.mutedConversations = setOf("convo-1")
        svc.onLivePresenceChanged("user-2", true)
        backend.mutedConversations = emptySet()
        svc.activeConversationId = "convo-1"
        svc.onLivePresenceChanged("user-3", true)
        assertTrue(sink.titles.isEmpty())
    }

    @Test
    fun perContactOverrideForcesAndBlocksOnlineToast() {
        val sink = RecordingSink()
        val prefs = FakePrefs(
            online = false,
            overrides = mapOf(
                "user-1" to ContactEventAlertOverride(online = ContactEventAlertOverride.MODE_ALWAYS),
                "user-2" to ContactEventAlertOverride(online = ContactEventAlertOverride.MODE_NEVER),
            ),
        )
        val svc = service(prefs = prefs, sink = sink)
        svc.onLivePresenceChanged("user-1", true)
        svc.onLivePresenceChanged("user-2", true)
        assertEquals(1, sink.titles.size)
    }

    @Test
    fun typingStartFiresOnceUntilStop() {
        val sink = RecordingSink()
        val svc = service(sink = sink)
        svc.onLiveTypingChanged("convo-1", "user-1", true)
        svc.onLiveTypingChanged("convo-1", "user-1", true) // still typing
        assertEquals(1, sink.titles.size)
        svc.onLiveTypingChanged("convo-1", "user-1", false)
        svc.onLiveTypingChanged("convo-1", "user-1", true)
        assertEquals(2, sink.titles.size)
    }

    @Test
    fun typingToastSkipsSelfOpenAndMutedChats() {
        val sink = RecordingSink()
        val backend = FakeBackend()
        val svc = service(backend = backend, sink = sink)
        svc.onLiveTypingChanged("convo-1", "me", true)
        svc.activeConversationId = "convo-1"
        svc.onLiveTypingChanged("convo-1", "user-1", true)
        svc.activeConversationId = null
        backend.mutedConversations = setOf("convo-2")
        svc.onLiveTypingChanged("convo-2", "user-1", true)
        assertTrue(sink.titles.isEmpty())
    }

    @Test
    fun activityMapTracksLiveTypingForHeaderConsumers() {
        val svc = service()
        svc.onLiveTypingChanged("convo-1", "user-1", true)
        assertTrue(svc.activityFor("convo-1", "user-1").isTyping)
        svc.onLiveTypingChanged("convo-1", "user-1", false)
        assertEquals(false, svc.activityFor("convo-1", "user-1").isTyping)
    }
}
