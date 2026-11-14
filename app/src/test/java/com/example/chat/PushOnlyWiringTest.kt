package com.example.chat
import java.io.File
import org.junit.Assert.*
import org.junit.Test
/** Source contracts only. Full-screen/ringing behavior requires an Android test. */
class PushOnlyWiringTest {
    private fun text(p:String):String { val root=if(File("app/src/main").exists()) File("app/src/main") else File("src/main");return File(root,p).readText() }
    @Test fun noApplicationServiceStart() { val s=text("kotlin/com/example/chat/ChatyApplication.kt");assertFalse(s.contains("ChatyRealtimeForegroundService.start("));assertTrue(s.contains("ChatyRealtimeForegroundService.stop(this)")) }
    @Test fun stickyRestartCannotRecreateNotice() { val s=text("java/com/example/chat/notifications/ChatyRealtimeForegroundService.kt").substringAfter("override fun onStartCommand").substringBefore("override fun onTaskRemoved");assertTrue(s.contains("START_NOT_STICKY"));assertFalse(s.contains("startForeground("));assertFalse(s.contains("onAppResumed()")) }
    @Test fun socketTokenGatesAllBackgroundConnects() { val s=text("kotlin/com/example/chat/data/api/ApiBackendService.kt");assertTrue(s.contains("token = { if (syncForeground) session.token else null }"));val stop=s.substringAfter("if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {").substringBefore("callCoordinator.foreground(false)");assertTrue(stop.contains("realtime.disconnect()")) }
    @Test fun incomingPushStillAwaitsAuthenticatedPoll() { assertTrue(text("java/com/example/chat/notifications/ChatyFirebaseMessagingService.kt").contains("handler.onIncomingCallNudgeAndWait(callId)"));assertTrue(text("kotlin/com/example/chat/ServiceLocator.kt").contains("apiBackend.pollCallSignalsAndAwait()")) }
    @Test fun coldPushWiresCallNotifications() { val s=text("kotlin/com/example/chat/ServiceLocator.kt").substringAfter("fun ensurePushReady(").substringBefore("private fun requireContext");assertTrue(s.contains("startCallNotifications()")) }
    @Test fun noPersistentServiceNotificationBuilderRemains() { val s=text("java/com/example/chat/notifications/ChatyRealtimeForegroundService.kt");assertFalse(s.contains("NotificationCompat.Builder"));assertFalse(s.contains("startForeground(")) }
    @Test fun fullScreenPathAndFallbackRemain() { val s=text("java/com/example/chat/notifications/ChatyNotificationPoster.kt").substringAfter("internal fun postIncomingCall(");assertTrue(s.contains("b.setFullScreenIntent(open, true)"));assertTrue(s.contains("canUseFullScreenIntent()"));assertTrue(s.contains("Build.VERSION.SDK_INT < 34"));assertTrue(s.contains("Open to answer"));assertTrue(text("AndroidManifest.xml").contains("android.permission.USE_FULL_SCREEN_INTENT")) }
}
