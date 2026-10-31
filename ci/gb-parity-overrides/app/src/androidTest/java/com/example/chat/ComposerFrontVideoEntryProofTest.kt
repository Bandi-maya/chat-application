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
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic emulator camera only: button-entry preset, no implicit capture or wire send. */
@RunWith(AndroidJUnit4::class)
class ComposerFrontVideoEntryProofTest {
    @Test fun frontVideoOpensReadyWithoutAutorecordAndKeepsPhotoRoute() {
        val ins=InstrumentationRegistry.getInstrumentation(); val c=ins.targetContext
        val d=UiDevice.getInstance(ins)
        val had=c.checkSelfPermission(Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED
        d.executeShellCommand("pm grant ${c.packageName} android.permission.CAMERA")
        val folder=File(c.cacheDir,"camera-video");val before=folder.listFiles()?.map{it.absolutePath}?.toSet()?:emptySet()
        val out=File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
        fun capture(name:String){d.dumpWindowHierarchy(File(out,"composer-front-video-$name.xml"));ins.uiAutomation.takeScreenshot()?.let{b->File(out,"composer-front-video-$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
        File(out,"composer-front-video-camera-inventory-before.txt").writeText(d.executeShellCommand("dumpsys media.camera"))
        try {
            ActivityScenario.launch<CameraOpeningProofActivity>(Intent(c,CameraOpeningProofActivity::class.java).putExtra("composerFrontVideo",true)).use {
                try {
                val ready=d.wait(Until.hasObject(By.desc("Record video")),20000)
                capture("ready-or-timeout")
                assertTrue("Front video reaches actual Ready",ready)
                // Actual bound camera from CameraService, not app state or the
                // requested preset: derive the front/back camera ids from this
                // emulator's own HAL static info in the same dump, then require
                // the app's ACTIVE camera client to be the front-facing id.
                class CamTruth(val dump: String, val frontId: String?, val backId: String?, val active: Set<String>)
                fun cameraTruth(): CamTruth {
                    val dump = d.executeShellCommand("dumpsys media.camera")
                    fun idFor(f: String) = Regex("internal/(\\d+)[^\\n]*static information: ==\\n\\s*Facing: " + f).find(dump)?.groupValues?.get(1)
                    // Only the CURRENT "Active Camera Clients" block; the dumpsys
                    // events log below it is history and must not count.
                    val activeSection = dump.substringAfter("Active Camera Clients:", "").substringBefore("\n]", "")
                    val active = Regex("Camera ID: (\\d+),[^)]*Client Package Name: " + Regex.escape(c.packageName)).findAll(activeSection).map { it.groupValues[1] }.toSet()
                    return CamTruth(dump, idFor("Front"), idFor("Back"), active)
                }
                // The video session binds asynchronously; poll for the app's client.
                var truth = cameraTruth()
                val bindDeadline = android.os.SystemClock.uptimeMillis() + 10000
                while (truth.active.isEmpty() && android.os.SystemClock.uptimeMillis() < bindDeadline) { Thread.sleep(500); truth = cameraTruth() }
                File(out,"composer-front-video-camera-inventory-ready.txt").writeText(truth.dump)
                val frontId = truth.frontId; val backId = truth.backId
                assertTrue("camera HAL exposes facing ids for the bound-camera proof", frontId != null && backId != null)
                // Observe the whole binding window, not one instant: the front
                // client must persist and the back id must never appear.
                var frontAlways = frontId != null && truth.active.contains(frontId)
                var sawBack = backId != null && truth.active.contains(backId)
                repeat(6) {
                    Thread.sleep(400)
                    val t = cameraTruth()
                    if (frontId != null && !t.active.contains(frontId)) frontAlways = false
                    if (backId != null && t.active.contains(backId)) sawBack = true
                }
                assertTrue("Actual CameraX bound camera is front", frontAlways)
                assertFalse("Never silently bind back", sawBack)
                assertFalse("Never auto-record",d.hasObject(By.desc("Stop recording")))
                assertFalse("No unrecorded review",d.hasObject(By.text("Play recording")))
                Thread.sleep(800)
                assertTrue("No draft on entry",folder.listFiles()?.none{it.absolutePath !in before}?:true)
                // Retired VideoCaptureHost "Back to photo" pill; the current photo
                // route is the mode strip.
                d.findObject(By.desc("Photo camera mode")).click()
                assertTrue(d.wait(Until.hasObject(By.desc("Take photo")),20000));capture("photo-kept")
                } finally {
                    capture("front-entry-finally")
                    File(out,"composer-front-video-camera-inventory-finally.txt").writeText(d.executeShellCommand("dumpsys media.camera"))
                }
            }
            ActivityScenario.launch<CameraOpeningProofActivity>(Intent(c,CameraOpeningProofActivity::class.java)).use {
                assertTrue("Other camera entry still defaults photo",d.wait(Until.hasObject(By.desc("Take photo")),20000));capture("default-photo")
            }
        } finally { if(!had)File(c.getExternalFilesDir(null),"camera-permission-cleanup").writeText("revoke") }
    }
}
