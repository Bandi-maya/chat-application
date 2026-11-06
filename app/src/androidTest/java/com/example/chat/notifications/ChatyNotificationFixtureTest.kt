package com.example.chat.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Synthetic emulator fixture for the notification cluster. Prerequisites (see test names):
 *  - emulator/AVD only, API 26+; media volume muted before the run (the incoming-call fixture posts a HIGH channel alert);
 *  - fresh install or app data cleared, so channel state is the app's defaults (a user-changed channel is NOT overridden by code);
 *  - no signed-in account and no network are used: a fake [ChatyNotificationBridge] stands in for the live scope check.
 * Proves: channel config, action sets, PendingIntent->receiver->bridge plumbing, stale/unknown-nonce handling at the receiver.
 * Does NOT prove: live-scope revalidation (ChatyNotificationCoordinator.dispatch), real calls, OS DND behavior, audible output.
 */
@RunWith(AndroidJUnit4::class)
class ChatyNotificationFixtureTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val nm get() = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val convo = "fixture-convo-1"

    private class Call(val nonce: String?, val action: String, val reply: CharSequence?)
    private class FakeBridge : ChatyNotificationBridge {
        val calls = CopyOnWriteArrayList<Call>()
        @Volatile var signal = CompletableDeferred<Unit>()
        override suspend fun dispatch(nonce: String?, action: String, replyText: CharSequence?) {
            calls.add(Call(nonce, action, replyText)); signal.complete(Unit)
        }
        override fun registerCallAction(action: String): Pair<String, CallActionView>? = null
    }
    private val bridge = FakeBridge()
    private var previous: ChatyNotificationBridge? = null

    @Before fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        previous = ChatyNotificationBridgeHolder.bridge
        ChatyNotificationBridgeHolder.bridge = bridge
        ChatyNotificationPoster.ensureChannels(ctx)
        ChatyNotificationPoster.cancelMessage(ctx, convo)
        ChatyNotificationPoster.cancelIncomingCall(ctx)
    }
    @After fun tearDown() {
        ChatyNotificationPoster.cancelMessage(ctx, convo)
        ChatyNotificationPoster.cancelIncomingCall(ctx)
        ChatyNotificationBridgeHolder.bridge = previous
    }

    /** notify() is an async binder call, so poll briefly instead of reading activeNotifications once. */
    private fun <T> poll(timeoutMs: Long = 5_000, f: () -> T?): T? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { f()?.let { return it }; Thread.sleep(100) }
        return f()
    }
    private fun messageNotification() = poll {
        nm.activeNotifications.firstOrNull { it.id == ChatyNotificationPolicy.messageNotificationId(convo) && it.tag == convo }?.notification
    }
    private fun incomingCallNotification() = poll {
        nm.activeNotifications.firstOrNull { it.id == ChatyNotificationPolicy.INCOMING_CALL_ID }?.notification
    }
    /** Non-sensitive failure context: counts and tags only (synthetic fixture values), no bodies/extras. */
    private fun diag(): String = "active=" + nm.activeNotifications.map { "${it.tag}/${it.id}/${it.notification.channelId}" } +
        " enabled=" + nm.areNotificationsEnabled() + " sdk=" + Build.VERSION.SDK_INT +
        " syswarn=" + sysWarnTail()

    /** Bounded system-side rejection evidence: NotificationService/PendingIntent/ActivityManager WARN+ lines
     *  for THIS app only, no bodies/extras/account data. Best-effort; empty on devices that restrict logcat. */
    private fun sysWarnTail(): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "200", "-b", "main",
            "NotificationService:W", "PendingIntent:W", "ActivityManager:W", "*:S"))
        val lines = p.inputStream.bufferedReader().readLines()
        lines.filter { it.contains(ctx.packageName) || it.contains("notification", ignoreCase = true) }
            .takeLast(8).joinToString(" | ").take(600)
    } catch (e: Exception) { "logcat-unavailable:${e.javaClass.simpleName}" }

    @Test fun channels_are_high_importance_and_incoming_call_has_ringtone_sound() {
        val msgs = nm.getNotificationChannel(ChatyNotificationPolicy.CH_MESSAGES)
        val call = nm.getNotificationChannel(ChatyNotificationPolicy.CH_INCOMING_CALL)
        assertNotNull(msgs); assertNotNull(call)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, msgs.importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, call.importance)
        assertNotNull("messages channel must have a sound (default notification sound)", msgs.sound)
        assertNotNull("incoming call channel must have a ringtone sound", call.sound)
        assertEquals(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE, call.audioAttributes.usage)
    }

    @Test fun ensureChannels_does_not_override_a_user_changed_channel() {
        // Android ignores importance raises on an existing channel; re-running must leave it untouched.
        val before = nm.getNotificationChannel(ChatyNotificationPolicy.CH_MESSAGES).importance
        ChatyNotificationPoster.ensureChannels(ctx)
        assertEquals(before, nm.getNotificationChannel(ChatyNotificationPolicy.CH_MESSAGES).importance)
    }

    @Test fun message_with_reply_and_dismiss_shows_exactly_those_two_actions() {
        ChatyNotificationPoster.postMessage(ctx, ChatyNotificationStyle(), convo, "Fixture Friend", "hello", 1_000L, "nonceR", "nonceD")
        val n = messageNotification()
        assertNotNull(diag(), n)
        val titles = n!!.actions.map { it.title.toString() }
        assertEquals(listOf("Reply", "Dismiss"), titles)
        assertNotNull("Reply must carry a RemoteInput", n.actions[0].remoteInputs?.firstOrNull())
        assertNull("Dismiss must not carry a RemoteInput", n.actions[1].remoteInputs)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, nm.getNotificationChannel(n.channelId).importance)
    }


    // Isolation: which action shape (if any) stops the notification from appearing?
    @Test fun isolation_reply_only_posts() {
        ChatyNotificationPoster.postMessage(ctx, ChatyNotificationStyle(), convo, "A", "b", 1L, "nonceR", null)
        assertNotNull(diag(), messageNotification())
    }
    @Test fun isolation_dismiss_only_posts() {
        ChatyNotificationPoster.postMessage(ctx, ChatyNotificationStyle(), convo, "A", "b", 1L, null, "nonceD")
        assertNotNull(diag(), messageNotification())
    }

    @Test fun generic_message_without_nonces_has_no_actions() {
        ChatyNotificationPoster.postMessage(ctx, ChatyNotificationStyle(), convo, "Chaty", "New message", 1_000L, null, null)
        val n = messageNotification()
        assertNotNull(n)
        assertTrue(n!!.actions.isNullOrEmpty())
    }

    @Test fun style_change_applies_on_next_post_color_and_icon() {
        ChatyNotificationPoster.postMessage(ctx, ChatyNotificationStyle(0xFF112233.toInt(), ChatyNotificationStyle.ICON_BELL), convo, "A", "b", 1L, null, null)
        val n = messageNotification()!!
        assertEquals(0xFF112233.toInt(), n.color)
        assertEquals(ChatyNotificationPoster.smallIcon(ChatyNotificationStyle(0, ChatyNotificationStyle.ICON_BELL)), n.smallIcon.resId)
    }

    @Test fun dismiss_action_reaches_bridge_with_its_own_nonce_only() = runBlocking {
        ChatyNotificationPoster.postMessage(ctx, ChatyNotificationStyle(), convo, "A", "b", 1L, "nonceR", "nonceD")
        bridge.signal = CompletableDeferred()
        messageNotification()!!.actions[1].actionIntent.send()
        withTimeout(TimeUnit.SECONDS.toMillis(10)) { bridge.signal.await() }
        val c = bridge.calls.single()
        assertEquals("nonceD", c.nonce); assertEquals(ChatyNotificationPolicy.ACTION_DISMISS, c.action); assertNull(c.reply)
    }

    @Test fun reply_broadcast_passes_typed_text_only() = runBlocking {
        val intent = Intent(ChatyNotificationPolicy.ACTION_REPLY).setClass(ctx, ChatyNotificationActionReceiver::class.java)
            .putExtra(ChatyNotificationPolicy.EXTRA_NONCE, "nonceR")
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ChatyNotificationPolicy.KEY_REPLY_TEXT).build()), intent,
            Bundle().apply { putCharSequence(ChatyNotificationPolicy.KEY_REPLY_TEXT, "typed words") },
        )
        bridge.signal = CompletableDeferred()
        ctx.sendBroadcast(intent)
        withTimeout(TimeUnit.SECONDS.toMillis(10)) { bridge.signal.await() }
        val c = bridge.calls.single()
        assertEquals("nonceR", c.nonce); assertEquals("typed words", c.reply.toString())
    }

    @Test fun broadcast_without_installed_bridge_is_a_noop_and_does_not_crash() = runBlocking {
        ChatyNotificationBridgeHolder.bridge = null
        ctx.sendBroadcast(Intent(ChatyNotificationPolicy.ACTION_DISMISS).setClass(ctx, ChatyNotificationActionReceiver::class.java)
            .putExtra(ChatyNotificationPolicy.EXTRA_NONCE, "stale"))
        Thread.sleep(1500)
        assertTrue(bridge.calls.isEmpty())
    }

    @Test fun incoming_call_posts_decline_and_open_only_then_cancels() {
        ChatyNotificationPoster.postIncomingCall(ctx, ChatyNotificationStyle(), "Fixture Caller", false, "nonceX")
        val n = incomingCallNotification()
        assertNotNull(diag(), n)
        assertEquals(ChatyNotificationPolicy.CH_INCOMING_CALL, n!!.channelId)
        assertEquals(listOf("Decline", "Open to answer"), n.actions.map { it.title.toString() })
        ChatyNotificationPoster.cancelIncomingCall(ctx)
        assertTrue("call notification still active: " + diag(), poll(1_500) { if (nm.activeNotifications.none { it.id == ChatyNotificationPolicy.INCOMING_CALL_ID }) true else null } == true)
    }

    @Test fun incoming_call_without_decline_nonce_offers_no_decline() {
        ChatyNotificationPoster.postIncomingCall(ctx, ChatyNotificationStyle(), "Fixture Caller", true, null)
        val n = incomingCallNotification()!!
        assertTrue(n.actions.none { it.title.toString() == "Decline" })
    }
}
