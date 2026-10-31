package com.example.chat

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.data.services.CallForegroundService
import com.example.chat.data.services.CallSessionState
import com.example.chat.notifications.CallActionReceiver
import com.example.chat.notifications.ChatyNotifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // Per-test start/stop owner: a couriered stop from one test must never
    // match (and kill) a later test's freshly started service.
    private var testOwner: String? = null

    @After fun tearDown() {
        CallActionReceiver.endCallProbe = null
        CallActionReceiver.declineProbe = null
        ChatyNotifications.cancelIncomingCall(context)
        CallForegroundService.stop(context, "test", testOwner)
        testOwner = null
    }

    @Test fun ongoingCallNotificationHasWorkingHangUpButton() {
        grant(Manifest.permission.POST_NOTIFICATIONS)
        grant(Manifest.permission.RECORD_AUDIO)
        testOwner = "zz-callnotif-hangup"
        val start = Intent(context, CallForegroundService::class.java)
            .putExtra(CallForegroundService.EXTRA_TITLE, "Proof Caller")
            .putExtra(CallForegroundService.EXTRA_TEXT, "Voice call")
            .putExtra(CallForegroundService.EXTRA_IS_VIDEO, false)
            .putExtra(CallForegroundService.EXTRA_OWNER, "test:zz-callnotif-hangup")
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
        CallForegroundService.stop(context, "test", "zz-callnotif-hangup")
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
        testOwner = "zz-callnotif-video"
        val start = Intent(context, CallForegroundService::class.java)
            .putExtra(CallForegroundService.EXTRA_TITLE, "Proof Caller")
            .putExtra(CallForegroundService.EXTRA_TEXT, "Video call")
            .putExtra(CallForegroundService.EXTRA_IS_VIDEO, true)
            .putExtra(CallForegroundService.EXTRA_OWNER, "test:zz-callnotif-video")
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
        CallForegroundService.stop(context, "test", "zz-callnotif-video")
    }

    @Test fun staleOwnerStopCannotKillNewerSession() {
        grant(Manifest.permission.POST_NOTIFICATIONS)
        grant(Manifest.permission.RECORD_AUDIO)
        testOwner = "zz-callnotif-owner-scope"
        val start = Intent(context, CallForegroundService::class.java)
            .putExtra(CallForegroundService.EXTRA_TITLE, "Proof Caller")
            .putExtra(CallForegroundService.EXTRA_TEXT, "Voice call")
            .putExtra(CallForegroundService.EXTRA_IS_VIDEO, false)
            .putExtra(CallForegroundService.EXTRA_OWNER, "test:zz-callnotif-owner-scope")
        context.startForegroundService(start)
        awaitCondition(8000, "owner-scoped notification to post") {
            activeNotification(CallForegroundService.SERVICE_ID) != null
        }
        // A courier from an older owner generation must be ignored.
        CallForegroundService.stop(context, "test", "zz-callnotif-older-generation")
        Thread.sleep(1500)
        assertNotNull(
            "stale-owner stop must not kill the live session's notification",
            activeNotification(CallForegroundService.SERVICE_ID),
        )
        // The same callId under a DIFFERENT owner token must also be ignored
        // (recreation serves the same call under a new wiring token).
        CallForegroundService.stop(context, "other-token", "zz-callnotif-owner-scope")
        Thread.sleep(1500)
        assertNotNull(
            "same callId under another owner token must not stop the session",
            activeNotification(CallForegroundService.SERVICE_ID),
        )
        // The serving owner's own stop still works promptly (hang-up path).
        CallForegroundService.stop(context, "test", "zz-callnotif-owner-scope")
        awaitCondition(5000, "serving owner's stop to remove the notification") {
            activeNotification(CallForegroundService.SERVICE_ID) == null
        }
    }

    @Test fun repeatedDemoHangUpCyclesStable() {
        // User-reported hang-up crash (Oct 30 + renewed Oct 31): targeted
        // reproduction through the REAL endCall cascade - a demo session drives
        // signaling -> FGS courier -> notification teardown, hung up via the
        // actual "End call" affordance, including hang-up during the ringing
        // window. Asserts the user expectation (no crash, session cleared,
        // notification removed, affordance reachable every cycle); crash
        // evidence = crash-buffer FATAL diff + process pid stability.
        grant(Manifest.permission.POST_NOTIFICATIONS)
        fun sh(c: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(c)).use { it.readBytes().decodeToString().trim() }
        fun fatalCount() = sh("logcat -b crash -d").lines().count { it.contains("FATAL EXCEPTION") }
        val calls = com.example.chat.ServiceLocator.callSignaling
        val pidBefore = sh("pidof ${context.packageName}")
        assertTrue("app process running before cycles", pidBefore.isNotEmpty())
        val fatalBefore = fatalCount()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        context.startActivity(launch)
        assertTrue("app shell visible", device.wait(Until.hasObject(By.text("Chats")), 20000))
        val cycleLog = StringBuilder()
        for (i in 1..6) {
            val duringRinging = i >= 5
            val t0 = System.currentTimeMillis()
            calls.startDemoCall("synthetic-peer-$i", "Cycle $i", null, null, isVideo = false, isOutgoing = true)
            if (duringRinging) Thread.sleep(600)
            val endBtn = device.wait(Until.findObject(By.desc("End call")), 8000)
            assertTrue("End call affordance reachable (cycle $i, duringRinging=$duringRinging)", endBtn != null)
            val tapAt = System.currentTimeMillis() - t0
            endBtn!!.click()
            awaitCondition(8000, "demo session cleared after hang-up (cycle $i)") { calls.currentSession == null }
            cycleLog.append("cycle").append(i).append(" duringRinging=").append(duringRinging)
                .append(" tapAtMs=").append(tapAt).append(" clearedMs=").append(System.currentTimeMillis() - t0).append('\n')
            Thread.sleep(400)
        }
        awaitCondition(5000, "call notification removed after final hang-up") {
            activeNotification(CallForegroundService.SERVICE_ID) == null
        }
        assertTrue("no new crash-buffer FATAL across 6 hang-up cycles", fatalCount() == fatalBefore)
        assertEquals("app process survived repeated hang-ups", pidBefore, sh("pidof ${context.packageName}"))
        java.io.File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        java.io.File(context.getExternalFilesDir(null), "screenshots/call-hangup-demo-cycles.txt").writeText(cycleLog.toString())
    }

    @Test fun repeatedRealMediaEndCallCyclesDoNotCrash() = kotlinx.coroutines.runBlocking {
        // Companion repro with REAL WebRTC media (loopback-test construction):
        // a real WebRtcCallMediaController on a multi-threaded scope,
        // back-to-back initiate -> hang-up cycles including hang-up immediately
        // after initiate (the connect-window race candidate seen in source:
        // prepare() and disposeInternal() share no synchronization). Media setup
        // failure on this emulator is evidence, not assertion failure; the
        // assertions are cascade completion, full teardown, and zero new crashes.
        grant(Manifest.permission.RECORD_AUDIO)
        fun sh(c: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(c)).use { it.readBytes().decodeToString().trim() }
        fun fatalCount() = sh("logcat -b crash -d").lines().count { it.contains("FATAL EXCEPTION") }
        val media = com.example.chat.data.services.WebRtcCallMediaController(context)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val service = com.example.chat.data.services.CallSignalingService(
            scope, { "synthetic-local" },
            signalSender = com.example.chat.data.services.CallSignalSender { _, _ -> },
            mediaFactory = { media },
            iceServersProvider = { emptyList() },
        )
        val fatalBefore = fatalCount()
        val cycleLog = StringBuilder()
        try {
            for (i in 1..6) {
                val immediate = i >= 5
                val t0 = System.currentTimeMillis()
                val launched = com.example.chat.ui.calls.runCallAction({ cycleLog.append("cycle").append(i).append(" action-failed\n") }) {
                    service.initiateCall("synthetic-peer-$i", "Cycle $i", isVideo = false)
                }
                if (!immediate) {
                    var waited = 0L
                    while (waited < 8000 && !media.hasLocalMedia && service.currentSession?.state?.isTerminal != true) { Thread.sleep(150); waited += 150 }
                }
                service.endCall(reason = "repro_cycle")
                var cleared = 0L
                while (cleared < 8000 && service.currentSession != null) { Thread.sleep(150); cleared += 150 }
                cycleLog.append("cycle").append(i).append(" immediate=").append(immediate)
                    .append(" launched=").append(launched).append(" endState=").append(service.currentSession?.state)
                    .append(" localMedia=").append(media.hasLocalMedia).append(" ms=").append(System.currentTimeMillis() - t0).append('\n')
            }
            var drained = 0L
            while (drained < 8000 && media.hasLocalMedia) { Thread.sleep(200); drained += 200 }
            assertFalse("media torn down after repeated hang-ups", media.hasLocalMedia)
            assertTrue("no new crash-buffer FATAL across 6 real-media hang-up cycles", fatalCount() == fatalBefore)
        } finally {
            java.io.File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            java.io.File(context.getExternalFilesDir(null), "screenshots/call-hangup-media-cycles.txt").writeText(cycleLog.toString())
            service.dispose()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}
