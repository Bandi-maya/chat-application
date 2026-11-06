package com.example.chat

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Availability proof only. Emulator absence is not physical Night capture proof. */
@RunWith(AndroidJUnit4::class)
class CameraNightCapabilityTest {
 @Test fun nightShowsActualAvailabilityAndNormalCameraRemainsUsable(){
  val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext;val d=UiDevice.getInstance(ins)
  val had=context.checkSelfPermission(Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED
  d.executeShellCommand("pm grant ${context.packageName} android.permission.CAMERA")
  val out=File(context.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
  fun capture(name:String){d.dumpWindowHierarchy(File(out,"camera-night-$name.xml"));ins.uiAutomation.takeScreenshot()?.let{b->File(out,"camera-night-$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
  try{ActivityScenario.launch<CameraOpeningProofActivity>(Intent(context,CameraOpeningProofActivity::class.java)).use{
   try {
    val ready=d.wait(Until.hasObject(By.desc("Color filters")),20000)
    capture("before-settings")
    assertTrue("Normal camera actually ready",ready)
    d.findObject(By.desc("Camera settings")).click()
    val panelReady=d.wait(Until.hasObject(By.desc("Camera options panel")),3000)
    capture("panel-open")
    assertTrue("Real camera options panel opens",panelReady)
    fun capabilityVisible():Boolean = d.hasObject(By.text("Night mode · hold still")) || d.hasObject(By.text("Night mode unavailable on this camera"))
    repeat(8){step->
     if(!capabilityVisible()){
      val foundPanel=d.findObject(By.desc("Camera options panel"))
      capture("panel-before-scroll-$step")
      val panel=foundPanel ?: throw AssertionError("Options panel remains mounted")
      val bounds=panel.visibleBounds
      assertTrue("Panel has usable scroll geometry",bounds.width()>0 && bounds.height()>48)
      // Gesture stays within actual visible options, never the preview/global screen.
      d.swipe(bounds.centerX(),bounds.bottom-12,bounds.centerX(),bounds.top+12,20)
      d.waitForIdle();capture("panel-scroll-$step")
     }
    }
    val available=d.hasObject(By.text("Night mode · hold still"))
    val unavailable=d.hasObject(By.text("Night mode unavailable on this camera"))
    capture("capability-before-assert")
    assertTrue("Night capability state must be honest",available||unavailable)
    assertFalse("Capability cannot be available and unavailable together",available&&unavailable)
    val capability=d.findObject(By.text(if(available)"Night mode · hold still" else "Night mode unavailable on this camera"))
        ?: throw AssertionError("Visible Night capability node remains reachable")
    assertEquals("Night enabled state matches actual availability",available,capability.isEnabled)
    assertTrue("Night row really visible after scrolling",capability.visibleBounds.width()>0 && capability.visibleBounds.height()>0)
    d.findObject(By.desc("Camera settings")).click()
    assertTrue("Options really close",d.wait(Until.gone(By.desc("Camera options panel")),3000))
    assertTrue("Normal shutter stays usable",d.hasObject(By.desc("Take photo")))
   } finally { capture("finally") }
  }}finally{if(!had)File(context.getExternalFilesDir(null),"camera-permission-cleanup").writeText("revoke")}

 }
}
