package com.example.chat

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic emulator camera. OEM-unavailable modes stay unavailable, never forced on. */
@RunWith(AndroidJUnit4::class)
class CameraModeStripProofTest {
 @Test fun modesAreReachableAndUnavailableModesAreHonest(){
  val ins=InstrumentationRegistry.getInstrumentation();val c=ins.targetContext;val d=UiDevice.getInstance(ins)
  val had=c.checkSelfPermission(Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED
  val out=File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
  d.executeShellCommand("pm grant ${c.packageName} android.permission.CAMERA")
  try {
   for(landscape in listOf(false,true))for(font in listOf(1f,2f)) {
    val tag="camera-modes-${if(landscape)"landscape"else"portrait"}-font${font.toInt()}"
    fun capture(state:String){ins.uiAutomation.takeScreenshot()?.let{b->File(out,"$tag-$state.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()};d.dumpWindowHierarchy(File(out,"$tag-$state.xml"))}
    ActivityScenario.launch<CameraOpeningProofActivity>(Intent(c,CameraOpeningProofActivity::class.java).putExtra("landscape",landscape).putExtra("fontScale",font)).use { scenario ->
     assertTrue(d.wait(Until.hasObject(By.desc("Take photo")),20000))
     val until=android.os.SystemClock.uptimeMillis()+8000;var correct=false
     do{scenario.onActivity{correct=(it.window.decorView.width>it.window.decorView.height)==landscape};if(!correct)Thread.sleep(100)}while(!correct&&android.os.SystemClock.uptimeMillis()<until)
     capture("opening");assertTrue("Actual requested orientation",correct)
     fun target(name:String):UiObject2 {
      // Always search from physical start, then traverse the real scroll strip.
      repeat(6){val r=d.findObject(By.desc("Camera modes")).visibleBounds;d.swipe(r.left+12,r.centerY(),r.right-12,r.centerY(),12)}
      repeat(8){
       val node=d.findObject(By.desc("$name camera mode"))?:d.findObject(By.desc("$name unavailable camera mode"))
       if(node!=null&&node.visibleBounds.width()>0)return node
       val r=d.findObject(By.desc("Camera modes")).visibleBounds;d.swipe(r.right-12,r.centerY(),r.left+12,r.centerY(),12)
      }
      capture("missing-$name");fail("$name mode not reachable");error("unreachable")
     }
     for(name in listOf("Portrait","Night")) {
      val node=target(name);val unsupported=node.contentDescription.contains("unavailable")
      capture("before-$name")
      if(unsupported){
       assertFalse("$name unavailable is disabled",node.isEnabled)
       assertFalse("$name unavailable is not selected",node.isSelected)
      } else {
       node.click()
       val active=if(name=="Night")"Night · hold still while capturing"else"Portrait · device background blur"
       val activated=d.wait(Until.hasObject(By.text(active)),12000)
       capture("after-$name")
       assertTrue("Advertised $name enters OEM mode or must report explicit fallback",activated||d.hasObject(By.textContains("$name unavailable. Photo restored.")))
       assertTrue("Shutter remains on camera",d.hasObject(By.desc("Take photo")))
       target("Photo").click();capture("return-$name")
      }
     }
     target("Lenses").click()
     // Product contract: the Lenses tab shows the shutter + inline lens
     // carousel and NEVER auto-opens the filters tray; the tray opens only
     // from the 3-circles "Color filters" button, never mixed with lenses.
     val carouselUp=d.wait(Until.hasObject(By.descContains("lens thumbnail")),12000)
     capture("lenses")
     assertTrue("Lenses selection is real",target("Lenses").isSelected)
     assertTrue("Lenses shows the inline lens carousel",carouselUp)
     assertTrue("Lenses never auto-opens the filters tray",d.wait(Until.gone(By.desc("Camera effects tray")),3000))
     assertTrue("Lenses keeps camera shutter",d.hasObject(By.desc("Take photo")))
     d.findObject(By.desc("Color filters")).click()
     val trayInLenses=d.wait(Until.hasObject(By.desc("Camera effects tray")),12000)
     capture("lenses-tray")
     assertTrue("3-circles button opens the tray even in lenses mode",trayInLenses)
     d.findObject(By.desc("Color filters")).click()
     assertTrue("3-circles button closes the tray",d.wait(Until.gone(By.desc("Camera effects tray")),12000))
     target("Photo").click();capture("return-lenses")
     val carouselGone=d.wait(Until.gone(By.descContains("lens thumbnail")),12000)
     capture("photo-carousel-closed")
     assertTrue("Photo really selected",target("Photo").isSelected)
     assertTrue("Photo closes the lens carousel",carouselGone)
     target("Video").click()
     val ready=d.wait(Until.hasObject(By.text("Record")),20000)
     capture("video-ready");assertTrue("Video enters real local recorder",ready)
     assertFalse("Local scope does not pretend send",d.hasObject(By.text("Use video")))
     d.findObject(By.text("Back to photo")).click()
     val photo=d.wait(Until.hasObject(By.desc("Take photo")),20000)
     capture("return-video");assertTrue("Video returns to photo lifecycle",photo)
     target("Document").click();Thread.sleep(300);capture("document")
     assertTrue("Document remains same camera",d.hasObject(By.desc("Take photo")))
     target("Photo").click();capture("return-document")
     assertEquals(c.packageName,d.currentPackageName)
    }
   }
  } finally {d.unfreezeRotation();if(!had)File(c.getExternalFilesDir(null),"camera-permission-cleanup").writeText("revoke")}
 }
}
