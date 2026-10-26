package com.example.chat

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.data.api.ApiAuthSession
import com.example.chat.data.api.ApiBackendService
import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiRealtimeClient
import com.example.chat.domain.models.ChatMessage
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Service boundary proof, not real backend delivery. The fake transport fails
 * if the real service calls it on Android's main thread. No sockets/session
 * initialize, recipients or live endpoints are used. */
@RunWith(AndroidJUnit4::class)
class ChatMainThreadRegressionTest {
    private fun field(service: ApiBackendService, name: String): Any {
        val f = service.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(service)!!
    }

    @Test fun sendLoadAndIncomingNotifyOnUiThread() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val calls = mutableListOf<String>()
        val service = ApiBackendService(ctx, ApiHttpTransport { method, _, _, body ->
            check(Looper.myLooper() != Looper.getMainLooper()) { "REST called on UI thread" }
            synchronized(calls) { calls.add(method) }
            if (method == "POST") {
                assertTrue(body!!.contains("synthetic outbound"))
                200 to """{"data":{"uid":"confirmed-1","room_id":"fixture-room","sender_id":"fixture-me","content":"synthetic outbound","created_at":"2026-10-26T10:00:00Z"}}"""
            } else 200 to """{"data":[{"uid":"remote-1","room_id":"fixture-room","sender_id":"fixture-peer","content":"synthetic remote","created_at":"2026-10-26T10:01:00Z"}]}"""
        })
        @Suppress("UNCHECKED_CAST")
        (field(service, "_conversations") as MutableList<com.example.chat.domain.models.Conversation>).add(
            com.example.chat.domain.models.Conversation(id = "fixture-room", type = com.example.chat.domain.models.ConversationType.direct,
                title = "Fixture", participantIds = listOf("fixture-me", "fixture-peer"), avatarInitials = "F", avatarColorHex = "0xFF10B981",
                lastMessageText = "", lastMessageTime = Instant.now(), lastMessageSenderId = "fixture-peer"),
        )
        val session = field(service, "session") as ApiAuthSession
        // Isolated service instance, no initialize() and hence no realtime connect.
        session.javaClass.getDeclaredField("token").apply { isAccessible = true }.set(session, "fixture-token")
        val notified = CountDownLatch(1)
        service.addListener {
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            notified.countDown()
        }
        withContext(Dispatchers.Main) {
            val confirmed = service.sendMessage("fixture-room", "synthetic outbound")
            assertEquals("confirmed-1", confirmed?.id)
            assertTrue(service.getMessages("fixture-room").none { it.id.startsWith("temp_") })
            service.loadMessages("fixture-room")
            assertEquals("synthetic remote", service.getMessages("fixture-room").single().text)
        }
        assertTrue(notified.await(5, TimeUnit.SECONDS))
        val incomingObserved = CountDownLatch(1)
        service.addListener {
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            if (service.getMessages("fixture-room").any { it.id == "incoming-1" }) incomingObserved.countDown()
        }
        withContext(Dispatchers.IO) {
            (field(service, "realtimeListener") as ApiRealtimeClient.Listener).onNewMessage(
                ChatMessage(id = "incoming-1", conversationId = "fixture-room", senderId = "fixture-peer", text = "synthetic incoming", createdAt = Instant.now()),
            )
        }
        assertTrue("incoming refresh reaches UI observer", incomingObserved.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("POST", "GET"), calls)
    }
}
