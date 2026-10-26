package com.example.chat

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.ui.kit.ChatySingleChoiceModal
import com.example.chat.ui.theme.ChatyTheme
import com.example.chat.core.theme.ThemePresets
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated in-memory settings selector. No preferences or backend mutations. */
@RunWith(AndroidJUnit4::class)
class CenteredSelectionProofTest {
    @Test fun cancelAndCloseDiscardDraftApplyCommits() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            var generation = 0
            fun show() {
                val phase = ++generation
                scenario.onActivity { activity ->
                    activity.setContent {
                        ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
                            var isOpen by remember(phase) { mutableStateOf(true) }
                            var saved by remember(phase) { mutableStateOf("Choice 1") }
                            if (isOpen) ChatySingleChoiceModal(
                                title = "Select synthetic icons", value = saved,
                                options = (1..30).map { "Choice $it" }, labelBuilder = { it },
                                onApply = { saved = it; isOpen = false }, onDismiss = { isOpen = false },
                            ) else Text("Saved: $saved")
                        }
                    }
                }
                assertTrue(device.wait(Until.hasObject(By.text("Select synthetic icons")), 10000))
            }
            show()
            device.findObject(By.text("Choice 2")).click()
            val header = device.findObject(By.text("Select synthetic icons")).visibleBounds
            val footer = device.findObject(By.text("Apply")).visibleBounds
            assertTrue("Header above fixed footer", header.bottom < footer.top)
            val image = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No pixels")
            val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/14-centered-selector.png")
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            device.findObject(By.text("Cancel")).click()
            assertTrue("Cancel discards draft", device.wait(Until.hasObject(By.text("Saved: Choice 1")), 5000))
            show()
            device.findObject(By.text("Choice 2")).click()
            device.findObject(By.desc("Close Select synthetic icons")).click()
            assertTrue("X discards draft", device.wait(Until.hasObject(By.text("Saved: Choice 1")), 5000))
            show()
            device.findObject(By.text("Choice 2")).click()
            device.findObject(By.text("Apply")).click()
            assertTrue("Apply commits draft", device.wait(Until.hasObject(By.text("Saved: Choice 2")), 5000))
        } finally { scenario.close() }
    }
}
