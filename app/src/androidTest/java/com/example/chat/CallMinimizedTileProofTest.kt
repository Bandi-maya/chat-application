package com.example.chat

import android.content.Intent
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

@RunWith(AndroidJUnit4::class)
class CallMinimizedTileProofTest {
    @Test fun productionTileShowsStateInsteadOfFakeDurationAndSavesUncroppedPixels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        for (connected in listOf(false, true)) {
            ActivityScenario.launch<CallMinimizedTileProofActivity>(Intent(instrumentation.targetContext,
                CallMinimizedTileProofActivity::class.java).putExtra("connected", connected)).use {
                val status = if (connected) "01:20" else "Ringing…"
                val visible = device.wait(Until.hasObject(By.text(status)), 5000)
                val tag = if (connected) "connected" else "ringing"
                device.takeScreenshot(File(dir, "call-minimized-tile-$tag.png"))
                device.dumpWindowHierarchy(File(dir, "call-minimized-tile-$tag.xml"))
                assertTrue("Production tile status is $status", visible)
                assertFalse("No fake connected00:00", device.hasObject(By.text("00:00")))
            }
        }
    }
}
