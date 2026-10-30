package com.example.chat

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.chat.data.services.CallForegroundService
import com.example.chat.notifications.CallActionReceiver
import com.example.chat.notifications.ChatyNotifications
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Notification-button routing proofs for the call flow (user report: connected voice
 * call had no hangup UI; notification buttons not working).
 *
 * Scope honesty: these tests verify the LOCAL route (posted notification -> system
 * delivers the PendingIntent -> CallActionReceiver runs the call action). They do NOT
 * prove real two-party peer termination, which needs a live call. T4 is a
 * service-lifecycle smoke check, not a video-call crash root-cause repro.
 */
@RunWith(AndroidJUnit4::class)
class CallNotificationActionsProofTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device: UiDevice = UiDevice.getInstance(instrumentation)

    private fun awaitCondition(timeoutMs: Long, what: String, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > deadline) fail("timed out waiting for $what")
            Thread.sleep(100)
        }
    }

    private fun activeNotification(id: Int): Notification? {
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm.activeNotifications.firstOrNull { it.id == id }?.notification
    }

    private fun grant(permission: String) {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
    }

    @After fun tearDown() {
        CallActionReceiver.endCallProbe = null
        CallActionReceiver.declineProbe = null
        ChatyNotifications.cancelIncomingCall(context)
        CallForegroundService.stop(context)
    }

    @Test fun ongoingCallNotificationHasWorkingHangUpButton() {
        grant(Manifest.permission.POST_NOTIFICATIONS)
        grant(Manifest.permission.RECORD_AUDIO)
        val start = Intent(context, CallForegroundService::class.java)
            .putExtra(CallForegroundService.EXTRA_TITLE, "Proof Caller")
            .putExtra(CallForegroundService.EXTRA_TEXT, "Voice call")
            .putExtra(CallForegroundService.EXTRA_IS_VIDEO, false)
        context.startForegroundService(start)
        awaitCondition(8000, "ongoing-call notification to post") {
            activeNotification(CallForegroundService.SERVICE_ID) != null
        }
        val notification = activeNotification(CallForegroundService.SERVICE_ID)!!
        val actions = notification.actions
        assertNotNull("ongoing-call notification must expose actions (previously it had none)", actions)
        assertTrue("hang-up action present", actions!!.isNotEmpty())
        val fired = AtomicBoolean(false)
        CallActionReceiver.endCallProbe = { fired.set(true) }
        actions[0].actionIntent.send()
        awaitCondition(5000, "hang-up PendingIntent to route to CallActionReceiver") { fired.get() }
        CallForegroundService.stop(context)
    }

    @Test fun incomingCallNotificationDeclineButtonRoutesToReceiver() {
        grant(Manifest.permission.POST_NOTIFICATIONS)
        ChatyNotifications.showIncomingCall(context, "proof-call-1", "caller-1", "Proof Caller", false)
        awaitCondition(8000, "incoming-call notification to post") {
            activeNotification(7410) != null
        }
        val notification = activeNotification(7410)!!
        val actions = notification.actions
        assertNotNull("incoming-call notification must expose actions", actions)
        assertTrue("answer + decline actions present", actions!!.size >= 2)
        val decline = actions.firstOrNull { it.title.toString().contains("Decline", ignoreCase = true) }
        assertNotNull("a Decline action exists on the incoming-call notification", decline)
        val fired = AtomicBoolean(false)
        CallActionReceiver.declineProbe = { fired.set(true) }
        decline!!.actionIntent.send()
        awaitCondition(5000, "decline PendingIntent to route to CallActionReceiver") { fired.get() }
        ChatyNotifications.cancelIncomingCall(context, "proof-call-1")
        ChatyNotifications.forgetCall("proof-call-1")
    }

    @Test fun videoCallServiceSurvivesBackgroundForegroundCycles() {
        grant(Manifest.permission.POST_NOTIFICATIONS)
        grant(Manifest.permission.RECORD_AUDIO)
        grant(Manifest.permission.CAMERA)
        val start = Intent(context, CallForegroundService::class.java)
            .putExtra(CallForegroundService.EXTRA_TITLE, "Proof Caller")
            .putExtra(CallForegroundService.EXTRA_TEXT, "Video call")
            .putExtra(CallForegroundService.EXTRA_IS_VIDEO, true)
        context.startForegroundService(start)
        awaitCondition(8000, "video-call notification to post") {
            activeNotification(CallForegroundService.SERVICE_ID) != null
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        repeat(3) { round ->
            device.pressHome()
            Thread.sleep(1200)
            assertTrue(
                "call service notification must survive backgrounding (round $round)",
                activeNotification(CallForegroundService.SERVICE_ID) != null,
            )
            context.startActivity(launch)
            Thread.sleep(1200)
            assertTrue(
                "call service notification must survive returning to the app (round $round)",
                activeNotification(CallForegroundService.SERVICE_ID) != null,
            )
        }
        CallForegroundService.stop(context)
    }
}
