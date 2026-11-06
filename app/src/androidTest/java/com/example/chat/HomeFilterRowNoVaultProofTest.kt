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

/** Synthetic fixture home: the filter row keeps All/Unread/Groups/Direct and has no Vault chip. */
@RunWith(AndroidJUnit4::class)
class HomeFilterRowNoVaultProofTest {
    @Test fun filterRowHasNoVaultChip() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val intent = Intent(InstrumentationRegistry.getInstrumentation().targetContext, ChatFixtureActivity::class.java)
            .putExtra(ChatFixtureActivity.EXTRA_SCREEN, ChatFixtureActivity.SCREEN_HOME)
        ActivityScenario.launch<ChatFixtureActivity>(intent).use {
            assertNotNull(device.wait(Until.findObject(By.text("Direct")), 5000))
            for (t in listOf("All", "Unread", "Groups")) assertNotNull(device.findObject(By.text(t)))
            assertNull(device.findObject(By.text("Vault")))
        }
    }
}
