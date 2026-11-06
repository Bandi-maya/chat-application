package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.navigation.CurvedRadialDrawerShell
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real compact fallback at 160x240dp and 200% font, no account or preference writes. */
@RunWith(AndroidJUnit4::class)
class RadialCompactFontProofTest {
    @Test fun lastDestinationRemainsReadableAndSelectable() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val scenario = ActivityScenario.launch(RadialPlacementProofActivity::class.java)
        try {
            scenario.onActivity { activity -> activity.setContent {
                val config = ThemePresets.getSystemDefaultTheme(platformIsLight = true)
                val density = LocalDensity.current.density
                ChatyTheme(config) {
                    CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                        var selected by remember { mutableStateOf(0) }
                        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                            Box(Modifier.width(160.dp).height(240.dp)) {
                                CurvedRadialDrawerShell(
                                    colors = ChatyColors(config),
                                    navItemIds = listOf("chats", "updates", "calls", "tasks", "settings"),
                                    selectedIndex = selected, animationsDisabled = true,
                                    onSelect = { selected = it }, fab = {},
                                    content = { Text("Synthetic page") }, initialOpen = true,
                                )
                            }
                            Text("Selected index: $selected")
                        }
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.desc("Close navigation menu")), 10000))
            val diagnosticsDir = File(inst.targetContext.getExternalFilesDir(null), "screenshots")
            diagnosticsDir.mkdirs()
            fun captureState(stage: String) {
                val bitmap = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No $stage pixels")
                FileOutputStream(File(diagnosticsDir, "radial-compact-$stage.png")).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                device.dumpWindowHierarchy(File(diagnosticsDir, "radial-compact-$stage.xml"))
            }
            captureState("before-scroll")
            val scroll = device.findObject(By.scrollable(true)) ?: throw AssertionError("Missing compact scroll")
            // UiObject2 defaults start at the scroll bounds edge. Use an interior
            // gesture so the parent clip/boundary cannot reject its first event.
            val margin = (16f * inst.targetContext.resources.displayMetrics.density).toInt()
            scroll.setGestureMargins(margin, margin, margin, margin)
            var settings = device.findObject(By.text("Settings"))
            repeat(12) {
                if (settings == null) { scroll.scroll(Direction.DOWN, 0.7f); settings = device.findObject(By.text("Settings")) }
            }
            if (settings == null) captureState("last-item-unreachable")
            val target = settings ?: throw AssertionError("Last item unreachable after 12 interior gestures")
            // This fixture's short English label must remain a whole single line,
            // not merely exist inside bounds as "Setting" + orphan "s".
            val singleLine = device.findObject(By.text("Selected index: 0"))
                ?: throw AssertionError("Need single-line same-font reference for readability")
            assertTrue("Settings must stay single-line, not orphan the last letter",
                target.visibleBounds.height() <= singleLine.visibleBounds.height() * 1.25f)
            val image = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No screenshot")
            val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/radial-compact-large-font.png")
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            target.click()
            assertTrue(device.wait(Until.hasObject(By.text("Selected index: 4")), 5000))
            assertTrue(device.wait(Until.hasObject(By.desc("Open navigation menu")), 5000))
        } finally { scenario.close() }
    }
}
