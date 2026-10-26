package com.example.chat

import android.content.Intent
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallControlsBoundsTest {
    @Test fun syntheticVoiceAndVideoControlsFitAndMoreInvokesNoop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        for ((video, width) in listOf(false to 320, true to 320, true to 480, true to 100)) {
            val intent = Intent(instrumentation.targetContext, CallControlsFixtureActivity::class.java)
                .putExtra("video", video).putExtra("widthDp", width)
            ActivityScenario.launch<CallControlsFixtureActivity>(intent).use { scenario ->
                var safe = Rect()
                scenario.onActivity { activity ->
                    val bars = activity.window.decorView.rootWindowInsets.getInsets(
                        android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                    safe = Rect(bars.left, bars.top, device.displayWidth - bars.right, device.displayHeight - bars.bottom)
                }
                assertTrue(device.wait(Until.hasObject(By.desc("End call")), 4000))
                val descriptions = listOf("Mute", "Camera off", "Switch camera", "Share screen", "Earpiece", "More call controls", "End call")
                val controls = descriptions.mapNotNull { device.findObject(By.desc(it)) }.map { it.visibleBounds }
                controls.forEach { assertTrue("Full control in safe window $it/$safe", safe.contains(it)) }
                controls.forEachIndexed { index, a -> controls.drop(index + 1).forEach { b -> assertFalse(Rect.intersects(a,b)) } }
                val more = device.findObject(By.desc("More call controls"))
                if (video && width <= 320) {
                    assertNotNull("Compact video exposes More", more)
                    more!!.click()
                    assertTrue("Actual overflow menu opened", device.wait(Until.hasObject(By.text("Earpiece")), 2000))
                    device.findObject(By.text("Earpiece")).click()
                    assertTrue("Noop audio callback ran", device.wait(Until.hasObject(By.text("Fixture callback:audio")), 2000))
                } else if (!video) assertNull("Voice shows all controls", more)
            }
        }
    }
}
