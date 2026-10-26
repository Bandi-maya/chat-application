package com.example.chat

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadialPlacementProofTest {
    @Test fun dedicatedStripProtectsIdentityAndBannerAndTriggerStaysFixed() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val scenario = ActivityScenario.launch(RadialPlacementProofActivity::class.java)
        val density = inst.targetContext.resources.displayMetrics.density
        fun bounds(desc: String): Rect {
            assertTrue("Missing $desc", device.wait(Until.hasObject(By.desc(desc)), 10000))
            return device.findObject(By.desc(desc)).visibleBounds
        }
        fun screenshot(name: String) {
            val bitmap = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No screenshot")
            val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/$name.png")
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            val menu = bounds("Open navigation menu")
            val banner = bounds("radial-proof-banner")
            val identity = bounds("radial-proof-identity")
            assertFalse("Trigger must not overlap banner", Rect.intersects(menu, banner))
            assertFalse("Trigger must not overlap identity", Rect.intersects(menu, identity))
            assertTrue("Page below reserved navigation strip", banner.top >= menu.bottom)
            assertTrue("Trigger remains 56dp", menu.width() >= 55 * density)
            scenario.onActivity { activity ->
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?: throw AssertionError("No root insets")
                val safe = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout())
                assertTrue("Trigger below notch/status", menu.top >= safe.top)
                assertTrue("Trigger clear of left cutout", menu.left >= safe.left)
                assertTrue("One top inset plus 72dp strip, not doubled", kotlin.math.abs(
                    banner.top - safe.top - 72f * density) <= 2f * density)
            }
            screenshot("radial-placement-closed")
            device.findObject(By.desc("Open navigation menu")).click()
            val close = bounds("Close navigation menu")
            assertEquals("Menu/X must not shift", menu, close)
            // Actual destination bounds come from accessibility, not predicted drawing offsets.
            val centers = listOf("Chats", "Updates", "Calls", "Tasks", "Settings").map { label ->
                val r = bounds(label)
                assertTrue("Circular target width $label", r.width() >= 55 * density)
                hypot((r.exactCenterX() - close.exactCenterX()).toDouble(),
                    (r.exactCenterY() - close.exactCenterY()).toDouble())
            }
            assertTrue("Equal-radius actual arc", centers.maxOrNull()!! - centers.minOrNull()!! <= 2.5 * density)
            screenshot("radial-placement-open")
            device.pressBack()
            assertEquals(menu, bounds("Open navigation menu"))
            device.findObject(By.desc("Open navigation menu")).click()
            device.findObject(By.desc("Tasks")).click()
            assertTrue("Selection retained", device.wait(Until.hasObject(By.text("Selected: tasks")), 5000))
            assertEquals(menu, bounds("Open navigation menu"))
        } finally { scenario.close() }
    }
}
