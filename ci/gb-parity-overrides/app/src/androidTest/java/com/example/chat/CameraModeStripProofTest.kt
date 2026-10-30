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
     // Inline video (user redesign): the shared preview stays and the shutter
     // becomes a red-ring video shutter. No separate host screen, no fake send.
     val videoReady=d.wait(Until.hasObject(By.desc("Record video")),20000)
     capture("video-ready");assertTrue("Video mode arms the inline recorder on the shared preview",videoReady)
     assertFalse("Photo shutter semantics leave while video mode is open",d.hasObject(By.desc("Take photo")))
     assertFalse("Local scope does not pretend send",d.hasObject(By.text("Use video")))
     d.findObject(By.desc("Record video")).click()
     val recording=d.wait(Until.hasObject(By.desc("Stop recording")),20000)
     capture("video-recording");assertTrue("Shutter tap really starts recording",recording)
     assertTrue("Recording time shows while recording",d.wait(Until.hasObject(By.desc("Recording time")),5000))
     assertTrue("Idle mode strip hides while recording",d.wait(Until.gone(By.desc("Camera modes")),5000))
     assertTrue("Gallery shortcut hides while recording",d.wait(Until.gone(By.desc("Pick image from gallery")),5000))
     assertTrue("Pause control shows while recording",d.wait(Until.hasObject(By.desc("Pause recording")),5000))
     d.findObject(By.desc("Pause recording")).click()
     assertTrue("Resume control shows after pausing",d.wait(Until.hasObject(By.desc("Resume recording")),5000))
     assertTrue("Recording time stays while paused",d.hasObject(By.desc("Recording time")))
     assertFalse("Idle mode strip stays hidden while paused",d.hasObject(By.desc("Camera modes")))
     d.findObject(By.desc("Resume recording")).click()
     assertTrue("Pause control returns after resuming",d.wait(Until.hasObject(By.desc("Pause recording")),5000))
     if (d.hasObject(By.desc("Take snapshot while recording"))) {
      // Device bound the 3-use-case combination on API 29+: the tap must save a real
      // JPEG to the gallery, not just leave recording alive.
      val projection = arrayOf(android.provider.MediaStore.Images.Media._ID)
      fun chatySnapshots():Int = c.contentResolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,projection,android.provider.MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?",arrayOf("chaty-recording-%"),null)?.use{it.count} ?: 0
      val before = chatySnapshots()
      d.findObject(By.desc("Take snapshot while recording")).click()
      var saved = false
      val saveDeadline = android.os.SystemClock.uptimeMillis() + 15000
      while (!saved && android.os.SystemClock.uptimeMillis() < saveDeadline) { Thread.sleep(500); saved = chatySnapshots() > before }
      assertTrue("Snapshot really saved to the gallery while recording",saved)
      assertTrue("Recording continues after a recording snapshot",d.hasObject(By.desc("Stop recording")))
     }
     Thread.sleep(1200)
     d.findObject(By.desc("Stop recording")).click()
     val review=d.wait(Until.hasObject(By.text("Record again")),20000)
     capture("video-review");assertTrue("Stopping lands in the local review",review)
     assertFalse("No send is connected, so no use-video action",d.hasObject(By.text("Use video")))
     d.findObject(By.text("Record again")).click()
     assertTrue("Retake asks before discarding the draft",d.wait(Until.hasObject(By.text("Discard this unsaved video?")),5000))
     d.findObject(By.text("Discard video")).click()
     assertTrue("Discarding the draft returns to an armed recorder",d.wait(Until.hasObject(By.desc("Record video")),20000))
     assertTrue("Mode strip returns after recording stops",d.wait(Until.hasObject(By.desc("Camera modes")),10000))
     target("Photo").click()
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

 /** When the device cannot bind Preview+VideoCapture+ImageCapture, the session
  *  must fall back to video-only and the UI must NOT draw a dead snapshot button. */
 @Test fun recordingSnapshotHidesWhenCombinationUnavailable(){
  val ins=InstrumentationRegistry.getInstrumentation();val c=ins.targetContext;val d=UiDevice.getInstance(ins)
  d.executeShellCommand("pm grant ${c.packageName} android.permission.CAMERA")
  com.example.chat.ui.camera.CameraVideoNativeProof.forceSnapshotUnavailable = true
  try {
   ActivityScenario.launch<CameraOpeningProofActivity>(Intent(c,CameraOpeningProofActivity::class.java)).use {
    assertTrue(d.wait(Until.hasObject(By.desc("Take photo")),20000))
    repeat(6){val r=d.findObject(By.desc("Camera modes")).visibleBounds;d.swipe(r.left+12,r.centerY(),r.right-12,r.centerY(),12)}
    var video:UiObject2?=null
    repeat(8){
     video=d.findObject(By.desc("Video camera mode"))
     if(video!=null&&video!!.visibleBounds.width()>0)break
     val r=d.findObject(By.desc("Camera modes")).visibleBounds;d.swipe(r.right-12,r.centerY(),r.left+12,r.centerY(),12)
    }
    assertTrue("Video mode reachable",video!=null)
    video!!.click()
    assertTrue(d.wait(Until.hasObject(By.desc("Record video")),20000))
    d.findObject(By.desc("Record video")).click()
    assertTrue(d.wait(Until.hasObject(By.desc("Stop recording")),20000))
    Thread.sleep(1000)
    assertFalse("No dead snapshot button when the 3-use-case bind is unavailable",d.hasObject(By.desc("Take snapshot while recording")))
    assertTrue("Video recording itself still works without the snapshot combination",d.hasObject(By.desc("Stop recording")))
    d.findObject(By.desc("Stop recording")).click()
    assertTrue("Video-only fallback still lands in review",d.wait(Until.hasObject(By.text("Record again")),20000))
   }
  } finally { com.example.chat.ui.camera.CameraVideoNativeProof.forceSnapshotUnavailable = false }
 }
}
