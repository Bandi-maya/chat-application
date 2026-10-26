package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.navigation.ModernSideMenuShell
import com.example.chat.ui.navigation.PerspectiveDrawerShell
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Existing debug host, isolated synthetic callbacks. No session/preferences/network writes. */
@RunWith(AndroidJUnit4::class)
class DrawerNarrowLayoutProofTest {
    @Test fun narrowShortLargeFontMenusReachLastDestinationAndDismiss() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        try {
            for (perspective in listOf(false, true)) {
                scenario.onActivity { activity ->
                    activity.setContent {
                        val density = LocalDensity.current.density
                        val config = ThemePresets.getSystemDefaultTheme(platformIsLight = true)
                        ChatyTheme(config) {
                            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                                var selected by remember(perspective) { mutableStateOf(0) }
                                val ids = listOf("chats", "updates", "calls", "tasks", "settings")
                                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                                    Box(Modifier.width(200.dp).height(200.dp).background(ChatyColors(config).surface)) {
                                        if (perspective) PerspectiveDrawerShell(
                                            colors = ChatyColors(config), navItemIds = ids, selectedIndex = selected,
                                            animationsDisabled = true, onSelect = { selected = it }, fab = {},
                                            content = { Text("Synthetic page") },
                                        ) else ModernSideMenuShell(
                                            colors = ChatyColors(config), navItemIds = ids, selectedIndex = selected,
                                            currentUser = null, myNameOverride = { "Synthetic long identity" },
                                            onSelect = { selected = it }, fab = {}, content = { Text("Synthetic page") },
                                        )
                                    }
                                    Text("Result: ${ids[selected]}")
                                }
                            }
                        }
                    }
                }
                assertTrue(device.wait(Until.hasObject(By.desc("Open menu")), 10000))
                device.findObject(By.desc("Open menu")).click()
                device.waitForIdle()
                val scroll = device.findObject(By.scrollable(true)) ?: throw AssertionError("Menu must scroll")
                var target = device.findObject(By.text("Settings"))
                repeat(12) {
                    if (target == null) {
                        scroll.scroll(androidx.test.uiautomator.Direction.DOWN, 0.7f)
                        target = device.findObject(By.text("Settings"))
                    }
                }
                val item = target ?: throw AssertionError("Last destination unreachable")
                val screenshot = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No screenshot")
                val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/drawer-narrow-${if (perspective) "perspective" else "modern"}.png")
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
                item.click()
                assertTrue(device.wait(Until.hasObject(By.text("Result: settings")), 5000))
                device.findObject(By.desc("Open menu")).click()
                device.pressBack()
                assertTrue(device.wait(Until.hasObject(By.desc("Open menu")), 5000))
            }
        } finally { scenario.close() }
    }
}
