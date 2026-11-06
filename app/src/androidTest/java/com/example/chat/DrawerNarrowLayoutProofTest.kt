package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
        var stage = "launch"
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
                                Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {
                                    contentDescription = "Drawer fixture generation ${if (perspective) "perspective" else "modern"}"
                                }) {
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
                stage = "${if (perspective) "perspective" else "modern"}: open menu"
                val fixtureDescription = "Drawer fixture generation ${if (perspective) "perspective" else "modern"}"
                assertTrue("Current drawer fixture committed", device.wait(Until.hasObject(By.desc(fixtureDescription)), 10000))
                device.waitForIdle()
                assertTrue(device.wait(Until.hasObject(By.desc("Open menu")), 10000))
                device.wait(Until.findObject(By.desc("Open menu")), 3000)!!.click()
                device.waitForIdle()
                stage = "${if (perspective) "perspective" else "modern"}: reach last destination"
                assertTrue("Menu must scroll", device.wait(Until.hasObject(By.scrollable(true)), 3000))
                repeat(12) {
                    if (!device.hasObject(By.text("Settings"))) {
                        // Scrolling recomposes the drawer. Never reuse a UiObject2
                        // from a previous hierarchy generation or screenshot.
                        val liveScroll = device.wait(Until.findObject(By.scrollable(true)), 3000)
                            ?: throw AssertionError("Menu scroll surface disappeared")
                        liveScroll.scroll(androidx.test.uiautomator.Direction.DOWN, 0.7f)
                        device.waitForIdle()
                    }
                }
                assertTrue("Last destination unreachable", device.wait(Until.hasObject(By.text("Settings")), 3000))
                val screenshot = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No screenshot")
                val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/drawer-narrow-${if (perspective) "perspective" else "modern"}.png")
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
                stage = "${if (perspective) "perspective" else "modern"}: select settings"
                val liveItem = device.wait(Until.findObject(By.text("Settings")), 3000)
                    ?: throw AssertionError("Last destination disappeared before click")
                liveItem.click() // Single action: do not retry failed navigation.
                assertTrue(device.wait(Until.hasObject(By.text("Result: settings")), 5000))
                stage = "${if (perspective) "perspective" else "modern"}: reopen and Back"
                device.wait(Until.findObject(By.desc("Open menu")), 3000)!!.click()
                device.waitForIdle()
                device.pressBack()
                assertTrue(device.wait(Until.hasObject(By.desc("Open menu")), 5000))
            }
        } catch (failure: Throwable) {
            val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            android.util.Log.e("DrawerNarrowProof", "Failure stage=$stage", failure)
            File(dir, "drawer-failure-stage.txt").writeText(stage)
            runCatching { device.takeScreenshot(File(dir, "drawer-failure.png")) }
            runCatching { device.dumpWindowHierarchy(File(dir, "drawer-failure.xml")) }
            throw failure
        } finally { scenario.close() }
    }
}
