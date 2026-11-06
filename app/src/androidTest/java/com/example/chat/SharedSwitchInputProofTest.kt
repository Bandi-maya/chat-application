package com.example.chat

import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import java.io.File
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.GbCustomSwitch
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Local input only; no settings, accounts or backend mutations. */
@RunWith(AndroidJUnit4::class)
class SharedSwitchInputProofTest {
    @Test fun standaloneSwitchHasStateTouchTargetAndOneCallbackPerTapOrSpace() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                    var value by remember { mutableStateOf(false) }
                    var count by remember { mutableIntStateOf(0) }
                    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                        GbCustomSwitch(value, { value = it; count++ })
                        Text("Switch callbacks $count")
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.checkable(true)), 10000))
            var control = device.findObject(By.checkable(true))
            assertFalse("Initial false is exposed", control.isChecked)
            val bounds = control.visibleBounds
            val density = inst.targetContext.resources.displayMetrics.density
            assertTrue("At least 48dp target height", bounds.height() >= 48 * density - 1)
            assertTrue("At least 48dp target width", bounds.width() >= 48 * density - 1)
            control.click()
            assertTrue(device.wait(Until.hasObject(By.text("Switch callbacks 1")), 3000))
            control = device.findObject(By.checkable(true))
            assertTrue("Updated true is exposed", control.isChecked)
            device.pressKeyCode(KeyEvent.KEYCODE_TAB)
            assertTrue("Switch reachable by keyboard", device.wait(Until.hasObject(By.checkable(true).focused(true)), 3000))
            val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            device.takeScreenshot(File(dir, "shared-switch-focused-before-space.png"))
            device.dumpWindowHierarchy(File(dir, "shared-switch-focused-before-space.xml"))
            device.pressKeyCode(KeyEvent.KEYCODE_SPACE)
            assertTrue(device.wait(Until.hasObject(By.text("Switch callbacks 2")), 3000))
            assertFalse("Space toggles back to false", device.findObject(By.checkable(true)).isChecked)
            device.takeScreenshot(File(dir, "shared-switch-after-space.png"))
            device.pressKeyCode(KeyEvent.KEYCODE_SPACE)
            assertTrue("Second Space invokes exactly one callback", device.wait(Until.hasObject(By.text("Switch callbacks 3")), 3000))
            assertTrue(device.findObject(By.checkable(true)).isChecked)
        }
    }

    @Test fun nestedVisualDoesNotInstallInputOrChangeItsThirtyDpHeight() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                    var count by remember { mutableIntStateOf(0) }
                    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                        Box(Modifier.semantics { contentDescription = "Visual only switch" }) {
                            GbCustomSwitch(false, { count++ }, interactive = false)
                        }
                        Text("Visual callbacks $count")
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.desc("Visual only switch")), 10000))
            val visual = device.findObject(By.desc("Visual only switch"))
            val expected = 30 * inst.targetContext.resources.displayMetrics.density
            assertTrue("Nested visual keeps 30dp height", kotlin.math.abs(visual.visibleBounds.height() - expected) <= 2)
            visual.click()
            device.waitForIdle()
            assertTrue("Visual-only cannot invoke callback", device.hasObject(By.text("Visual callbacks 0")))
            assertTrue("No child switch input semantics", device.findObjects(By.checkable(true)).isEmpty())
        }
    }
}
