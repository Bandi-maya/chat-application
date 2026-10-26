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
}
