package com.example.chat

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic fixture only: proves the unavailable PRESENTATION. Not capture preflight, not
 *  AppRoot routing, not live identity. */
@RunWith(AndroidJUnit4::class)
class StatusUnavailablePaneTest {
    @Test fun showsExplanationAndNoStatusActions() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val intent = Intent(inst.targetContext, StatusUnavailableFixtureActivity::class.java)
        ActivityScenario.launch<StatusUnavailableFixtureActivity>(intent).use {
            val msg = "Status isn't available for this account connection yet. Nothing was sent."
            assertNotNull(device.wait(Until.findObject(By.text(msg)), 5000))
            assertNull(device.findObject(By.textContains("My status")))
            assertNull(device.findObject(By.textContains("Add status")))
            assertNull(device.findObject(By.clickable(true).textContains("Post")))
        }
    }
}
