package com.example.chat

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic fixture only: no account, session or network. */
@RunWith(AndroidJUnit4::class)
class PushedPageClickThroughTest {
    private fun run(guard: Boolean, block: (UiDevice) -> Unit) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val intent = Intent(inst.targetContext, PushedPageFixtureActivity::class.java)
            .putExtra(PushedPageFixtureActivity.EXTRA_GUARD, guard)
        ActivityScenario.launch<PushedPageFixtureActivity>(intent).use {
            assertNotNull(device.wait(Until.findObject(By.text("Header")), 5000))
            block(device)
        }
    }

    private fun count(device: UiDevice, key: String): Int =
        Regex("$key=(\\d+)").find(device.findObject(By.textStartsWith("$key=")).text)!!.groupValues[1].toInt()

    @Test fun plainTextAndBackgroundDoNotReachLayerBelow() = run(guard = true) { d ->
        d.findObject(By.text("Header")).click()
        d.findObject(By.text("Plain description text")).click()
        d.click(d.displayWidth / 2, d.displayHeight * 3 / 4) // empty page background
        d.waitForIdle()
        assertEquals("taps leaked to the Home layer", 0, count(d, "home"))
        assertEquals(0, count(d, "action"))
    }

    @Test fun explicitForegroundActionStillWorks() = run(guard = true) { d ->
        d.findObject(By.text("Explicit action")).click()
        d.waitForIdle()
        assertEquals(1, count(d, "action"))
        assertEquals(0, count(d, "home"))
    }

    /** Control: without the guard the same taps do reach the layer below, proving the test detects it. */
    @Test fun controlWithoutGuardLeaksToLayerBelow() = run(guard = false) { d ->
        d.click(d.displayWidth / 2, d.displayHeight * 3 / 4)
        d.waitForIdle()
        assertEquals(1, count(d, "home"))
    }
}
