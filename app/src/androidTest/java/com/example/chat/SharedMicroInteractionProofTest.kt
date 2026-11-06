package com.example.chat

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ChatyChoiceTile
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic shared control only. Restores the exact animator-scale setting in finally. */
@RunWith(AndroidJUnit4::class)
class SharedMicroInteractionProofTest {
    @Test fun pressFeedbackAndCancelKeepSingleInputOwner() = run(false)
    @Test fun reducedMotionKeepsPressStaticAndStillActivates() = run(true)

    private fun run(reduced: Boolean) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val original = device.executeShellCommand("settings get global animator_duration_scale").trim()
        val coords = AtomicReference<LayoutCoordinates?>()
        val probe = AtomicReference<LayoutCoordinates?>()
        val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        var pointerDown = false
        var downAt = 0L
        var x = 0f; var y = 0f
        fun touch(action: Int) {
            if (action == MotionEvent.ACTION_DOWN) downAt = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                assertTrue("Inject active gesture action=$action", inst.uiAutomation.injectInputEvent(event, true))
                pointerDown = action == MotionEvent.ACTION_DOWN
            } finally { event.recycle() }
        }
        fun bounds(): Rect {
            var result: Rect? = null
            inst.runOnMainSync { result = coords.get()?.boundsInWindow() }
            return result ?: error("No live tile coordinates")
        }
        try {
            device.executeShellCommand("settings put global animator_duration_scale ${if (reduced) 0 else 1}")
            ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                        var count by remember { mutableIntStateOf(0) }
                        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                            ChatyChoiceTile("Micro tap", { count++ }, Modifier.width(200.dp)
                                .onGloballyPositioned { coords.set(it) })
                            Text("Micro callbacks $count")
                            Box(Modifier.springInteractiveScale(0.98f, enableHaptic = false)
                                .size(120.dp, 48.dp).background(Color.Blue)
                                .semantics { contentDescription = "Motion probe" }
                                .onGloballyPositioned { probe.set(it) })
                            ChatyChoiceTile("Disabled micro", { count++ }, enabled = false)
                        }
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.text("Micro tap")), 10000))
                val initial = bounds()
                val target = device.findObject(By.text("Micro tap")).visibleBounds
                x = target.exactCenterX(); y = target.exactCenterY()
                touch(MotionEvent.ACTION_DOWN)
                SystemClock.sleep(250)
                val held = bounds()
                assertTrue("Press alone must not activate", device.hasObject(By.text("Micro callbacks 0")))
                device.takeScreenshot(File(dir, "shared-micro-${if (reduced) "reduced" else "motion"}-held.png"))
                // The supplied outer modifier remains unscaled for hit-target/layout purposes.
                assertEquals("Stable layout width while pressed", initial.width, held.width, 1f)
                touch(MotionEvent.ACTION_UP)
                assertTrue(device.wait(Until.hasObject(By.text("Micro callbacks 1")), 3000))
                touch(MotionEvent.ACTION_DOWN)
                SystemClock.sleep(50)
                touch(MotionEvent.ACTION_CANCEL)
                device.waitForIdle()
                assertTrue("Cancellation must not activate", device.hasObject(By.text("Micro callbacks 1")))
                device.findObject(By.text("Disabled micro")).click()
                device.waitForIdle()
                assertTrue("Disabled must not activate", device.hasObject(By.text("Micro callbacks 1")))
                device.takeScreenshot(File(dir, "shared-micro-${if (reduced) "reduced" else "motion"}-released.png"))
                fun probeWidth(): Float {
                    var width = 0f
                    inst.runOnMainSync { width = probe.get()!!.boundsInWindow().width }
                    return width
                }
                val probeInitial = probeWidth()
                val probeBounds = device.findObject(By.desc("Motion probe")).visibleBounds
                x = probeBounds.exactCenterX(); y = probeBounds.exactCenterY()
                touch(MotionEvent.ACTION_DOWN)
                SystemClock.sleep(250)
                val probeHeld = probeWidth()
                if (reduced) assertEquals("Reduced motion stays unscaled", probeInitial, probeHeld, 1f)
                else assertEquals("Subtle 0.98 press scale", probeInitial * 0.98f, probeHeld, 1f)
                device.takeScreenshot(File(dir, "shared-micro-${if (reduced) "reduced" else "motion"}-probe.png"))
                touch(MotionEvent.ACTION_CANCEL)
            }
        } finally {
            // Never assert a stray CANCEL after the fixture window is already closed.
            // Best-effort cleanup cannot mask an earlier test failure or skip restoring OS policy.
            try { if (pointerDown) runCatching { touch(MotionEvent.ACTION_CANCEL) } }
            finally {
                if (original == "null") device.executeShellCommand("settings delete global animator_duration_scale")
                else device.executeShellCommand("settings put global animator_duration_scale $original")
            }
        }
    }
}
