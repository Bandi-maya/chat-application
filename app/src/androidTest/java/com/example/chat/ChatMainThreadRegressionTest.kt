package com.example.chat

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.data.api.ApiAuthSession
import com.example.chat.data.api.ApiAuthSnapshot
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
        val sharedAuth = ctx.getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE)
        val priorToken = sharedAuth.getString("api_hub_token", null)
        val priorUser = sharedAuth.getString("api_hub_user", null)
        // Keep the real service/session persistence path, but isolate its storage namespace.
        // applicationContext must remain this wrapper: the service deliberately asks for it.
        val fixturePrefsName = "chaty_thread_proof_" + java.util.UUID.randomUUID().toString()
        val isolatedContext = object : ContextWrapper(ctx) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(if(name == "chaty_api_backend") fixturePrefsName else name, mode)
        }
        var bodyFailure: Throwable? = null
        try {
        val calls = mutableListOf<String>()
        val service = ApiBackendService(isolatedContext, ApiHttpTransport { method, _, _, body ->
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
        // Isolated service AND session storage, no initialize() and hence no realtime connect.
        // ApiAuthSession keeps token+user+generation in one immutable state; token is a getter.
        session.adopt(com.example.chat.data.api.ApiAuthResult(
            "fixture-token", com.example.chat.data.api.ApiUserProfile("fixture-me", "fixture-me", "Synthetic fixture", "SF"),
        ))
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
        } catch (error: Throwable) {
            bodyFailure = error
            throw error
        } finally {
            // Label-only failures: never print auth values, and never mask the test body's failure.
            val cleanupFailures = mutableListOf<Throwable>()
            try {
                if (!ctx.deleteSharedPreferences(fixturePrefsName)) cleanupFailures.add(AssertionError("Fixture storage cleanup failed"))
            } catch (_: Throwable) { cleanupFailures.add(AssertionError("Fixture storage cleanup threw")) }
            try {
                if (priorToken != sharedAuth.getString("api_hub_token", null)) cleanupFailures.add(AssertionError("Shared auth token changed"))
                if (priorUser != sharedAuth.getString("api_hub_user", null)) cleanupFailures.add(AssertionError("Shared auth user changed"))
            } catch (_: Throwable) { cleanupFailures.add(AssertionError("Shared auth isolation check failed")) }
            val original = bodyFailure
            if (original != null) cleanupFailures.forEach { original.addSuppressed(it) }
            else if (cleanupFailures.isNotEmpty()) {
                val first = cleanupFailures.first()
                cleanupFailures.drop(1).forEach { first.addSuppressed(it) }
                throw first
            }
        }
    }
}
