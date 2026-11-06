package com.example.chat

import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.data.services.CallSessionState
import com.example.chat.data.services.ChatyCallSession
import com.example.chat.ui.calls.bundles.*
import com.example.chat.ui.theme.ChatyTheme
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Production UI callback admission only. No call service/media/network/account operations. */
@RunWith(AndroidJUnit4::class)
class IncomingPendingIntentProofTest {
 @Test fun pendingControlsCannotEmitAnswerOrDeclineIntent() {
  val dev=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  for(bundle in listOf(CallBundleId.APPLE_IOS,CallBundleId.CHATY_DEFAULT,CallBundleId.SAMSUNG_ONE_UI,CallBundleId.VIVO_IQOO,CallBundleId.NEXTCLOUD_TALK)) {
   for(pending in listOf(true,false)) {
    val accepted=AtomicInteger();val declined=AtomicInteger()
    ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
     scenario.onActivity { activity -> activity.setContent {
      ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
       BundleIncomingCallView(bundle,ChatyCallSession("pending-${bundle.id}","synthetic","Synthetic Caller",
        isVideo=false,isOutgoing=false,state=CallSessionState.incoming,startedAt=Instant.EPOCH,isDemo=true),
        onAccept={accepted.incrementAndGet()},onDecline={declined.incrementAndGet()},actionPending=pending)
      }
     } }
     assertTrue(dev.wait(Until.hasObject(By.text("Synthetic Caller")),8000))
     val decline=dev.findObject(By.desc("Decline call")) ?: dev.findObject(By.desc("Decline")) ?: dev.findObject(By.text("Decline"))
     assertNotNull("Decline target ${bundle.id}",decline)
     decline!!.click();dev.waitForIdle(1000)
     assertEquals("Decline intent pending=$pending ${bundle.id}",if(pending)0 else 1,declined.get())
     if (bundle == CallBundleId.APPLE_IOS || bundle == CallBundleId.CHATY_DEFAULT) {
      val answer = dev.wait(Until.findObject(By.desc("Slide to answer")), 8000)
      assertNotNull("Real slide target ${bundle.id}", answer)
      val bounds = answer!!.visibleBounds
      // Drag to the screen's safe right edge. The production slider clamps to
      // its measured track, including the centered max480dp presentation.
      val margin = (24 * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density).toInt()
      assertTrue("Real answer slide injection ${bundle.id}", dev.swipe(bounds.centerX(), bounds.centerY(),
       dev.displayWidth - margin, bounds.centerY(), 60))
      dev.waitForIdle(1000)
      Thread.sleep(400) // Existing slider commit animation completes before checking callback.
     } else {
      val answer=dev.findObject(By.desc("Answer call")) ?: dev.findObject(By.desc("Accept call")) ?: dev.findObject(By.desc("Accept")) ?: dev.findObject(By.desc("Answer"))
      assertNotNull("Answer target ${bundle.id}",answer)
      answer!!.click();dev.waitForIdle(1000)
     }
     assertEquals("Answer intent pending=$pending ${bundle.id}",if(pending)0 else 1,accepted.get())
    }
   }
  }
 }
}
