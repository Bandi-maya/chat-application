package com.example.chat

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ChatyChoiceTile
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedCapsuleActionProofTest {
    @Test fun lightCapsuleActionsAtLargeText() = run(false)
    @Test fun darkCapsuleActionsAtLargeText() = run(true)

    @Test fun rtlCapsuleActionsAtLargeText() = run(true, true)

    private fun run(dark: Boolean, rtl: Boolean = false) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(dark)) {
                        var count by remember { mutableIntStateOf(0) }
                        Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChatyChoiceTile("Apply", { count++ }, Modifier.fillMaxWidth(), prominent = true)
                            ChatyChoiceTile("Cancel", { count++ }, Modifier.fillMaxWidth())
                            ChatyChoiceTile("Disabled action", { count++ }, Modifier.fillMaxWidth(), enabled = false)
                            ChatyChoiceTile("A longer action label that wraps at large text", { count++ }, Modifier.width(220.dp))
                            Text("Capsule callbacks $count")
                        }
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("Apply")), 10000))
            for (label in listOf("Apply", "Cancel", "Disabled action", "A longer action label that wraps at large text")) {
                var action = device.findObject(By.text(label)) ?: error("Missing $label")
                while (!action.isClickable && action.parent != null) action = action.parent
                val min = 48 * inst.targetContext.resources.displayMetrics.density - 1
                assertTrue("$label target remains48dp", action.visibleBounds.width() >= min && action.visibleBounds.height() >= min)
            }
            device.takeScreenshot(File(dir, "shared-capsule-${if (dark) "dark" else "light"}-${if (rtl) "rtl" else "ltr"}-font2.png"))
            device.dumpWindowHierarchy(File(dir, "shared-capsule-${if (dark) "dark" else "light"}-${if (rtl) "rtl" else "ltr"}-font2.xml"))
            device.findObject(By.text("Apply")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Capsule callbacks 1")), 3000))
            device.findObject(By.text("Disabled action")).click()
            device.waitForIdle()
            assertTrue("Disabled action unchanged", device.hasObject(By.text("Capsule callbacks 1")))
        }
    }
}
