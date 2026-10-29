package com.example.chat

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator camera preview only. Never presses shutter or sends/uploads. */
@RunWith(AndroidJUnit4::class)
class CameraOpeningProofTest {
    private fun preview(view: View): PreviewView? {
        if (view is PreviewView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            preview(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    @Test
    fun grantedCameraStreamsAndSurvivesRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val hadPermission = context.checkSelfPermission(Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        device.executeShellCommand("pm grant ${context.packageName} android.permission.CAMERA")
        try {
            ActivityScenario.launch<CameraOpeningProofActivity>(Intent(context, CameraOpeningProofActivity::class.java)).use { scenario ->
                fun awaitStream() {
                    var streaming = false
                    val until = android.os.SystemClock.uptimeMillis() + 20_000
                    while (!streaming && android.os.SystemClock.uptimeMillis() < until) {
                        scenario.onActivity { activity ->
                            streaming = preview(activity.window.decorView)?.previewStreamState?.value == PreviewView.StreamState.STREAMING
                        }
                        if (!streaming) Thread.sleep(100)
                    }
                    assertTrue("Camera must produce preview frames, not remain on loading spinner", streaming)
                    assertTrue(device.wait(Until.hasObject(By.desc("Take photo")), 3_000))
                }
                awaitStream()
                val output = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(output, "camera-open-streaming.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                scenario.recreate()
                awaitStream()
            }
        } finally {
            // Revoking a runtime permission kills this package, including the instrumentation runner.
            // The outer emulator harness restores only the permission this test granted AFTER the suite exits.
            if (!hadPermission) File(context.getExternalFilesDir(null), "camera-permission-cleanup").writeText("revoke")
        }
    }
    @Test fun settingsDocumentAndSafeBottomAcrossViewportMatrix() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val hadPermission = context.checkSelfPermission(Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        device.executeShellCommand("pm grant ${context.packageName} android.permission.CAMERA")
        val output = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        try {
            for (landscape in listOf(false, true)) for (font in listOf(1f, 2f)) {
                if (landscape) device.setOrientationLeft() else device.setOrientationNatural()
                val label = "camera-${if (landscape) "landscape" else "portrait"}-font${font.toInt()}"
                ActivityScenario.launch<CameraOpeningProofActivity>(Intent(context, CameraOpeningProofActivity::class.java).putExtra("fontScale", font).putExtra("landscape",landscape)).use { scenario ->
                    assertTrue(device.wait(Until.hasObject(By.desc("Take photo")), 20_000))
                    val orientationDeadline = android.os.SystemClock.uptimeMillis()+8000
                    var actualLandscape = false
                    do {
                        scenario.onActivity { actualLandscape = it.window.decorView.width > it.window.decorView.height }
                        if(actualLandscape != landscape) Thread.sleep(100)
                    } while(actualLandscape != landscape && android.os.SystemClock.uptimeMillis()<orientationDeadline)
                    fun capture(name: String) {
                        device.dumpWindowHierarchy(File(output, "$label-$name.xml"))
                        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                            File(output, "$label-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                        }
                    }
                    fun assertBottom() {
                        var safe = android.graphics.Rect()
                        scenario.onActivity { activity ->
                            val view = activity.window.decorView
                            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
                            safe = android.graphics.Rect(insets?.left ?: 0, insets?.top ?: 0, view.width - (insets?.right ?: 0), view.height - (insets?.bottom ?: 0))
                        }
                        for (label in listOf("Take photo", "Pick image from gallery", "Switch camera")) {
                            val node = device.findObject(By.desc(label))
                            assertTrue("$label exists", node != null)
                            val rect = node.visibleBounds
                            File(output, "camera-geometry.txt").appendText("$label bounds=$rect safe=$safe\n")
                            assertTrue("$label remains fully above system bars", safe.contains(rect) && rect.height() > 0 && rect.width() > 0)
                        }
                    }
                    capture("normal")
                    val adjust=device.findObject(By.desc("Toggle precise zoom"))
                    assertTrue("Precise zoom entry exists",adjust!=null)
                    adjust.click()
                    val opened=device.wait(Until.hasObject(By.desc("Circular zoom dial, drag horizontally to zoom")),3000)
                    capture("zoom-expanded")
                    assertTrue("Precise zoom really opens",opened)
                    val dial=device.wait(Until.findObject(By.desc("Circular zoom dial, drag horizontally to zoom")),3000)
                    assertTrue("Circular zoom dial is present and interactive",dial!=null)
                    val db=dial.visibleBounds
                    device.swipe(db.centerX(),db.centerY(),(db.centerX()+db.width()/4).coerceAtMost(db.right),db.centerY(),12)
                    device.waitForIdle()
                    assertTrue("Circular zoom dial survives drag interaction",device.hasObject(By.desc("Circular zoom dial, drag horizontally to zoom")))
                    device.waitForIdle()
                    val closeZoom=device.wait(Until.findObject(By.desc("Toggle precise zoom")),3000)
                    capture("zoom-before-collapse")
                    assertTrue("Stable zoom toggle remains reachable",closeZoom!=null)
                    closeZoom.click()
                    val closed=device.wait(Until.gone(By.desc("Circular zoom dial, drag horizontally to zoom")),3000)
                    capture("zoom-collapsed")
                    assertTrue("Precise zoom really closes",closed)
                    assertTrue("Requested orientation actually applied: $label ${device.displayWidth}x${device.displayHeight}",actualLandscape == landscape)
                    assertBottom()
                    device.findObject(By.desc("Camera settings")).click(); Thread.sleep(300)
                    capture("settings"); assertBottom()
                    val filter=device.findObject(By.desc("Color filters"))
                    assertTrue("Effects icon beside Settings exists",filter!=null)
                    filter.click()
                    val effectsOpened=device.wait(Until.hasObject(By.desc("Camera effects tray")),3000)
                    capture("effects")
                    assertTrue("Effects tray actually opens",effectsOpened)
                    assertTrue("Effects replaces settings",!device.hasObject(By.text("Camera options")))
                    device.waitForIdle()
                    val hideFilters=device.wait(Until.findObject(By.desc("Color filters")),3000)
                    capture("effects-before-collapse")
                    assertTrue("Effects toggle remains reachable",hideFilters!=null)
                    hideFilters.click()
                    val effectsClosed=device.wait(Until.gone(By.desc("Camera effects tray")),3000)
                    capture("effects-collapsed")
                    assertTrue("Effects tray actually closes",effectsClosed)
                    device.findObject(By.desc("Camera settings")).click()
                    Thread.sleep(200)
                    assertTrue("same fixture activity", device.currentPackageName == context.packageName)
                    device.findObject(By.desc("Camera settings")).click()
                    repeat(6) {
                        if(!device.hasObject(By.desc("Document camera mode"))) {
                            val modes=device.findObject(By.desc("Camera modes")).visibleBounds
                            device.swipe(modes.right-12,modes.centerY(),modes.left+12,modes.centerY(),15)
                        }
                    }
                    assertTrue("Document mode reachable in mode strip",device.hasObject(By.desc("Document camera mode")))
                    device.findObject(By.desc("Document camera mode")).click(); Thread.sleep(300)
                    capture("document-mode"); assertBottom()
                    assertTrue("Document stays in camera with shutter", device.hasObject(By.desc("Take photo")))
                    assertTrue("Document stays in package", device.currentPackageName == context.packageName)
                }
            }
        } finally {
            device.unfreezeRotation()
            if (!hadPermission) File(context.getExternalFilesDir(null), "camera-permission-cleanup").writeText("revoke")
        }
    }

}
