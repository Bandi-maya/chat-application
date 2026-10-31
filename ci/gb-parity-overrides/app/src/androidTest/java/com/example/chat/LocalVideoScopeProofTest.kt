package com.example.chat

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic emulator camera, local-only draft. Never sends/uploads or changes real auth. */
@RunWith(AndroidJUnit4::class)
class LocalVideoScopeProofTest {
    @Test fun recordDecodePlayBackCancelDiscardAndSyntheticAuthContainment() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val device=UiDevice.getInstance(instrumentation)
        val had=context.checkSelfPermission(Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED
        val folder=File(context.cacheDir,"camera-video");val before=folder.listFiles()?.map{it.absolutePath}?.toSet()?:emptySet()
        device.executeShellCommand("pm grant ${context.packageName} android.permission.CAMERA")
        val output=File(context.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
        fun capture(name:String){device.dumpWindowHierarchy(File(output,"local-video-$name.xml"));instrumentation.uiAutomation.takeScreenshot()?.let{b->File(output,"local-video-$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
        try {
            for(authChange in listOf(false,true)) {
                ActivityScenario.launch<CameraOpeningProofActivity>(Intent(context,CameraOpeningProofActivity::class.java)).use{scenario->
                    assertTrue(device.wait(Until.hasObject(By.desc("Take photo")),20000))
                    repeat(6){if(device.findObject(By.desc("Video camera mode"))?.visibleBounds?.width()?.let{it>0} != true){val r=device.findObject(By.desc("Camera modes")).visibleBounds;device.swipe(r.right-12,r.centerY(),r.left+12,r.centerY(),15)}}
                    val foundVideo=device.wait(Until.findObject(By.desc("Video camera mode")),3000)
                    capture("before-video-$authChange")
                    val video=foundVideo ?: throw AssertionError("Video mode reachable")
                    assertTrue("Video mode reachable",video.visibleBounds.width()>0)
                    assertTrue("Real Video mode enabled",video.isEnabled)
                    video.click()
                    assertTrue(device.wait(Until.hasObject(By.desc("Record video")),20000));device.findObject(By.desc("Record video")).click()
                    assertTrue(device.wait(Until.hasObject(By.desc("Stop recording")),10000));Thread.sleep(2200);device.findObject(By.desc("Stop recording")).click()
                    assertTrue(device.wait(Until.hasObject(By.text("Play recording")),20000))
                    val file=folder.listFiles()!!.filter{it.absolutePath !in before}.maxBy{it.lastModified()}
                    val reader=MediaMetadataRetriever()
                    try{reader.setDataSource(file.absolutePath);assertTrue((reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0)>0);val frame=reader.getFrameAtTime(0);assertNotNull(frame);frame?.recycle()}finally{reader.release()}
                    device.findObject(By.text("Play recording")).click();capture("review-$authChange")
                    assertFalse("No pretend send",device.hasObject(By.text("Use video")))
                    if(authChange){
                        scenario.onActivity{it.testAuth!!.invalidate()}
                        Thread.sleep(400)
                        assertFalse("old review removed",device.hasObject(By.text("Play recording")))
                        assertFalse("old auth clip deleted after reader cleanup",file.exists())
                        // Original host contract (VideoCaptureHost LaunchedEffect(auth):
                        // stopReader, session close, onAuthInvalidated): invalidation
                        // dismisses the whole camera. Assert the actual teardown, not a relaunch.
                        var tornDown=false
                        val ddl=android.os.SystemClock.uptimeMillis()+5000
                        while(android.os.SystemClock.uptimeMillis()<ddl && !tornDown){Thread.sleep(200);tornDown=scenario.state==androidx.lifecycle.Lifecycle.State.DESTROYED}
                        assertTrue("auth invalidation tears down the camera session (host contract dismissal)",tornDown)
                    }else{
                        device.pressBack();assertTrue(device.wait(Until.hasObject(By.text("Discard video")),3000));capture("exit-prompt")
                        device.findObject(By.text("Keep reviewing")).click();assertTrue(file.exists())
                        device.pressBack();assertTrue(device.wait(Until.hasObject(By.text("Discard video")),3000))
                        device.findObject(By.text("Discard video")).click();assertTrue(device.wait(Until.hasObject(By.desc("Take photo")),10000));assertFalse(file.exists())
                    }
                }
                if(authChange){
                    ActivityScenario.launch<CameraOpeningProofActivity>(Intent(context,CameraOpeningProofActivity::class.java)).use{
                        assertTrue("relaunch after invalidation lands on clean photo default",device.wait(Until.hasObject(By.desc("Take photo")),20000))
                        assertFalse("no stale review after relaunch",device.hasObject(By.text("Play recording")))
                    }
                }
            }
        }finally{folder.listFiles()?.filter{it.absolutePath !in before}?.forEach{it.delete()};if(!had)File(context.getExternalFilesDir(null),"camera-permission-cleanup").writeText("revoke")}
    }
}
