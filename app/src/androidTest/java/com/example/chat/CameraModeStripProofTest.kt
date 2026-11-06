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
     // Support probed INDEPENDENTLY of the strip under test (CameraX
     // ExtensionsManager on the back camera): a supported mode missing from the
     // strip must fail below, never pass as "hidden".
     val camProvider=androidx.camera.lifecycle.ProcessCameraProvider.getInstance(c).get(10,java.util.concurrent.TimeUnit.SECONDS)
     val extMgr=androidx.camera.extensions.ExtensionsManager.getInstanceAsync(c,camProvider).get(10,java.util.concurrent.TimeUnit.SECONDS)
     val backSel=androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
     val hwSupport=mapOf("Portrait" to extMgr.isExtensionAvailable(backSel,androidx.camera.extensions.ExtensionMode.BOKEH),"Night" to extMgr.isExtensionAvailable(backSel,androidx.camera.extensions.ExtensionMode.NIGHT))
     for(name in listOf("Portrait","Night")) {
      if(!hwSupport[name]!!){
       // Approved contract (user direction 2026-10-30, relayed by main): unsupported
       // modes are hidden entirely - no "unavailable" label. Absence after a full
       // strip traversal IS the honest behavior on this device.
       repeat(6){val r=d.findObject(By.desc("Camera modes")).visibleBounds;d.swipe(r.left+12,r.centerY(),r.right-12,r.centerY(),12)}
       var advertised=false
       repeat(10){
        if(d.hasObject(By.desc("$name camera mode"))||d.hasObject(By.desc("$name unavailable camera mode"))){advertised=true}
        val r=d.findObject(By.desc("Camera modes")).visibleBounds;d.swipe(r.right-12,r.centerY(),r.left+12,r.centerY(),12)
       }
       capture("hidden-$name")
       assertFalse("$name is unsupported on this device and must stay hidden, not advertised",advertised)
       continue
      }
      val node=target(name)
      capture("before-$name")
      run {
       node.click()
       val active=if(name=="Night")"Night Â· hold still while capturing"else"Portrait Â· device background blur"
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
     run {
      // Fresh-tree poll (AppRoot-proven stale a11y cache for in-place composition
      // updates such as this AlertDialog); timeout and assertion message unchanged.
      val dl=android.os.SystemClock.uptimeMillis()+5000;var seenPrompt=false
      while(android.os.SystemClock.uptimeMillis()<dl){if(android.os.Build.VERSION.SDK_INT>=33)ins.uiAutomation.clearCache();if(d.hasObject(By.text("Discard this unsaved video?"))){seenPrompt=true;break};Thread.sleep(250)}
      if(!seenPrompt)capture("retake-prompt-miss")
      assertTrue("Retake asks before discarding the draft",seenPrompt)
     }
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
     if(video==null||video!!.visibleBounds.width()<=0){
      video=d.findObject(By.desc("Video camera mode"))
      if(video==null||video!!.visibleBounds.width()<=0){val r=d.findObject(By.desc("Camera modes")).visibleBounds;d.swipe(r.right-12,r.centerY(),r.left+12,r.centerY(),12)}
     }
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

 @Test fun videoRecordingBakesSelectedLens(){
  val ins=InstrumentationRegistry.getInstrumentation();val c=ins.targetContext;val d=UiDevice.getInstance(ins)
  d.executeShellCommand("pm grant ${c.packageName} android.permission.CAMERA")
  // DEBUG-only synthetic pose in preview coordinates: the emulator camera sees
  // no real face, so this hook proves the recorded-output compositing path with
  // real video pixels instead of pretending detection.
  val pose = com.example.chat.ui.camera.FacePose(0,
   com.example.chat.ui.camera.LensRect(390f,600f,690f,950f),
   com.example.chat.ui.camera.LensOffset(470f,740f), com.example.chat.ui.camera.LensOffset(630f,740f),
   com.example.chat.ui.camera.LensOffset(550f,830f), com.example.chat.ui.camera.LensOffset(550f,900f),
   0.0,0.0,0.0,1.0)
  fun latestVideo(before:Set<String>):File?{
   val dir=File(c.cacheDir,"camera-video")
   return dir.listFiles()?.filter{it.extension=="mp4" && it.name !in before}?.maxByOrNull{it.lastModified()}
  }
  fun frameAt(video:File,us:Long):Bitmap{
   val mmr=android.media.MediaMetadataRetriever()
   try{mmr.setDataSource(video.absolutePath);return mmr.getFrameAtTime(us) ?: error("no frame")}
   finally{mmr.release()}
  }
  fun regionMeanDiff(a:Bitmap,b:Bitmap,l:Float,t:Float,r:Float,bo:Float):Double{
   var sum=0.0;var n=0
   var x=l.toInt().coerceIn(0,a.width-1)
   while(x<r.toInt().coerceIn(1,a.width)){
    var y=t.toInt().coerceIn(0,a.height-1)
    while(y<bo.toInt().coerceIn(1,a.height)){
     val p=a.getPixel(x,y);val q=b.getPixel(x,y)
     sum+=Math.abs(android.graphics.Color.red(p)-android.graphics.Color.red(q))+
      Math.abs(android.graphics.Color.green(p)-android.graphics.Color.green(q))+
      Math.abs(android.graphics.Color.blue(p)-android.graphics.Color.blue(q))
     n++;y+=8
    }
    x+=8
   }
   return if(n==0) 0.0 else sum/n
  }
  try {
   ActivityScenario.launch<CameraOpeningProofActivity>(Intent(c,CameraOpeningProofActivity::class.java)).use {
    assertTrue(d.wait(Until.hasObject(By.desc("Take photo")),20000))
    // Select a real lens through the carousel tap path.
    val lensesModeNode=d.findObject(By.desc("Lenses camera mode"))
    android.util.Log.w("MODESTRIP-DIAG","lensesModeNodeFound="+(lensesModeNode!=null))
    lensesModeNode?.click()
    val thumb=d.wait(Until.findObject(By.desc("Gold Crown lens thumbnail")),5000)
    assertTrue("Lens carousel offers the Gold Crown lens",thumb!=null)
    // Settle gate: run-124 LENS-DIAG evidence proved taps injected from
    // mid-animation a11y bounds land one slot off (tap on the Gold Crown node
    // delivered onTap index=1 glasses while the snap watcher logged
    // firstVisible=1+99). Wait for the node's fresh bounds to be stable across
    // two reads before injecting the tap. Assertion below is unchanged.
    run {
     var last=thumb!!.visibleBounds
     val sdl=android.os.SystemClock.uptimeMillis()+8000
     while(android.os.SystemClock.uptimeMillis()<sdl){
      Thread.sleep(300)
      if(android.os.Build.VERSION.SDK_INT>=33)ins.uiAutomation.clearCache()
      val fresh=d.findObject(By.desc("Gold Crown lens thumbnail"))?:continue
      val b=fresh.visibleBounds
      if(b==last)break
      last=b
     }
    }
    if(android.os.Build.VERSION.SDK_INT>=33)ins.uiAutomation.clearCache()
    run {
     // Fresh-bounds coordinate tap (run-127 evidence: the settle-gated
     // UiObject2.click() produced NO select() in LENS-DIAG and the miss dump
     // showed the carousel dismissed - the injected coordinates came from a
     // cached snapshot's bounds). Re-query after the cache clear, inject at the
     // freshly-read center, and log coordinates + post-tap carousel presence
     // for hit/dismiss discrimination. Coordinate path, target and all
     // assertions unchanged.
     val tapTarget=d.findObject(By.desc("Gold Crown lens thumbnail"))?:thumb!!
     val tb=tapTarget.visibleBounds
     android.util.Log.w("MODESTRIP-DIAG","tapTarget bounds="+tb.toShortString()+" center=("+tb.centerX()+","+tb.centerY()+") preCarousel="+d.hasObject(By.descContains("lens thumbnail")))
     d.click(tb.centerX(),tb.centerY())
     Thread.sleep(500)
     android.util.Log.w("MODESTRIP-DIAG","postTap carouselPresent="+d.hasObject(By.descContains("lens thumbnail")))
    }
    run {
     // Fresh-tree poll: same stale-cache shape as CameraLensSheetProofTest's in-use
     // desc waits; on a real miss keep the actual tree as evidence.
     val dl=android.os.SystemClock.uptimeMillis()+5000;var marked=false
     while(android.os.SystemClock.uptimeMillis()<dl){if(android.os.Build.VERSION.SDK_INT>=33)ins.uiAutomation.clearCache();if(d.hasObject(By.desc("Gold Crown lens thumbnail, in use"))){marked=true;break};Thread.sleep(250)}
     if(!marked)d.dumpWindowHierarchy(File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"camera-mode-strip-lens-miss.xml"))
     assertTrue("Lens selection is marked in the carousel",marked)
    }
    // Record WITH the lens composited (synthetic pose), then WITHOUT.
    fun recordOnce():File{
     val dir=File(c.cacheDir,"camera-video")
     // Lifecycle evidence (run-130: full record/discard flow completed but no
     // mp4 at the assert). Contract question (main 16:09): CameraVideoSession
     // deletes reviewFile on discard - if the asserted file IS the review file,
     // post-discard absence is correct cleanup and the assertion POINT is
     // wrong; if the file never appears after stop, recording/encoding failed.
     // Snapshot the dir per stage to discriminate. Assertions unchanged.
     val lifecycle=StringBuilder()
     fun snap(stage:String){ val l=dir.listFiles()?.sortedBy{it.name}?.joinToString(","){it.name+":"+it.length()}?:"<none>"; lifecycle.append(stage).append(" -> ").append(l).append('\n'); android.util.Log.w("MODESTRIP-DIAG","lifecycle "+stage+" -> "+l) }
     val before=dir.listFiles()?.filter{it.extension=="mp4"}?.map{it.name}?.toSet() ?: emptySet()
     d.findObject(By.desc("Video camera mode")).click()
     assertTrue(d.wait(Until.hasObject(By.desc("Record video")),20000))
     d.findObject(By.desc("Record video")).click()
     android.util.Log.w("MODESTRIP-DIAG","recordStart t="+System.currentTimeMillis()+" fakePosesSet="+(com.example.chat.ui.camera.CameraVideoNativeProof.fakeLensPoses!=null))
     Thread.sleep(1800)
     snap("recording")
     // Run-131 NPE here: Stop recording was absent from the served a11y tree
     // 1.8s after the Record tap while the mp4 grew (emulator a11y publish
     // lag, same family as the mic desc lag). Guard with the same measured
     // 20s Until-wait the sibling steps use; flow unchanged otherwise.
     assertTrue("Stop recording must appear after recording starts",d.wait(Until.hasObject(By.desc("Stop recording")),20000))
     d.findObject(By.desc("Stop recording")).click()
     assertTrue(d.wait(Until.hasObject(By.text("Record again")),20000))
     snap("stopped-review")
     // Contract (main 17:25): prove the take BEFORE the discard flow, and keep
     // discard cleanup covered. The review file is asserted at review and
     // copied for the pixel decode; discard is then asserted to REMOVE the
     // review file (CameraVideoSession deletes reviewFile on discard:
     // lines 213/298/321). The pre-131 assert-after-discard conflicted with
     // that cleanup contract.
     val v=latestVideo(before)
     snap("at-review-assert")
     assertTrue("Recorded video file exists at review",v!=null && v.length()>0)
     val keep=File(c.cacheDir,"camera-video-keep-"+System.currentTimeMillis()+".mp4")
     v!!.copyTo(keep,overwrite=true)
     d.findObject(By.text("Record again")).click()
     assertTrue(d.wait(Until.hasObject(By.text("Discard this unsaved video?")),5000))
     d.findObject(By.text("Discard video")).click()
     assertTrue(d.wait(Until.hasObject(By.desc("Record video")),20000))
     snap("post-discard")
     assertTrue("Discard removes the unsaved review file",!v.exists())
     File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"camera-video-lifecycle.txt").appendText(lifecycle.toString())
     return keep
    }
    com.example.chat.ui.camera.CameraVideoNativeProof.fakeLensPoses=listOf(pose)
    android.util.Log.w("MODESTRIP-DIAG","fakeLensPoses installed t="+System.currentTimeMillis())
    val withLens=recordOnce()
    com.example.chat.ui.camera.CameraVideoNativeProof.fakeLensPoses=null
    // No real face on the emulator, so without the hook nothing composites.
    val bare=recordOnce()
    // Run-133 failed the assertion below: center diff 2.48 vs corner 0.0, no root
    // cause. Evidence-only discriminator, capture/apply timing vs actual output:
    // sample both recordings at three offsets and log per-offset diffs. A diff that
    // rises with time = lens applied after early frames were captured (timing
    // race); flat-low at every offset = recorded output never baked. The 900us
    // assertion itself is unchanged.
    fun frameAtClosest(video:File,us:Long):Bitmap{
     val mmr=android.media.MediaMetadataRetriever()
     try{mmr.setDataSource(video.absolutePath);return mmr.getFrameAtTime(us,android.media.MediaMetadataRetriever.OPTION_CLOSEST) ?: error("no frame")}
     finally{mmr.release()}
    }
    // Run-134: sync-mode sampling collapsed onto one keyframe (identical diffs at
    // all three offsets), so it could not discriminate timing from output. The
    // diag samples now extract the ACTUAL frame at each offset (OPTION_CLOSEST);
    // the 900us assertion below keeps its original sync-mode extraction.
    for (us in listOf(300000L,900000L,1500000L)) {
     val ta=frameAtClosest(withLens,us);val tb=frameAtClosest(bare,us)
     if(ta.width==tb.width && ta.height==tb.height){
      val tw=ta.width.toFloat();val th=ta.height.toFloat()
      android.util.Log.w("MODESTRIP-DIAG","lensframe us="+us+" centerDiff="+regionMeanDiff(ta,tb,tw*0.30f,th*0.20f,tw*0.70f,th*0.60f)+" cornerDiff="+regionMeanDiff(ta,tb,0f,0f,tw*0.15f,th*0.12f)+" size="+ta.width+"x"+ta.height)
     } else android.util.Log.w("MODESTRIP-DIAG","lensframe us="+us+" sizeMismatch "+ta.width+"x"+ta.height+" vs "+tb.width+"x"+tb.height)
    }
    val fa=frameAt(withLens,900000);val fb=frameAt(bare,900000)
    assertTrue("Frames decoded at same size",fa.width==fb.width && fa.height==fb.height)
    val w=fa.width.toFloat();val h=fa.height.toFloat()
    val center=regionMeanDiff(fa,fb,w*0.30f,h*0.20f,w*0.70f,h*0.60f)
    val corner=regionMeanDiff(fa,fb,0f,0f,w*0.15f,h*0.12f)
    assertTrue("Lens pixels appear in the recorded video (center diff $center vs corner $corner)",center>8.0 && center>corner*1.5)
   }
  } finally { com.example.chat.ui.camera.CameraVideoNativeProof.reset() }
 }
}
