package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.navigation.CurvedRadialDrawerShell
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real shell, synthetic destinations/state only. No preferences/accounts/backend writes. */
@RunWith(AndroidJUnit4::class)
class RadialVariantsProofTest {
    @Test fun darkRtlPaginationRetainsLastDestinationAndReopensAtFirstPage() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val scenario = ActivityScenario.launch(RadialPlacementProofActivity::class.java)
        try {
            scenario.onActivity { activity -> activity.setContent {
                val config = ThemePresets.getSystemDefaultTheme(platformIsLight = false)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    var selected by remember { mutableStateOf(-1) }
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Box(Modifier.width(320.dp).height(480.dp)) {
                            CurvedRadialDrawerShell(
                                colors = ChatyColors(config), navItemIds = (0..11).map { "Synthetic $it" },
                                selectedIndex = selected, animationsDisabled = true,
                                onSelect = { selected = it }, fab = {},
                                content = { Text("Synthetic page") },
                            )
                        }
                        Text("Selected index: $selected")
                    }
                }
            } }
            val diagnosticsDir = File(inst.targetContext.getExternalFilesDir(null), "screenshots")
            diagnosticsDir.mkdirs()
            fun capture(stage: String) {
                val bitmap = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No $stage pixels")
                FileOutputStream(File(diagnosticsDir, "radial-dark-rtl-$stage.png")).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                device.dumpWindowHierarchy(File(diagnosticsDir, "radial-dark-rtl-$stage.xml"))
            }
            assertTrue(device.wait(Until.hasObject(By.desc("Open navigation menu")), 10000))
            capture("closed")
            val menu = device.findObject(By.desc("Open navigation menu")).visibleBounds
            device.findObject(By.desc("Open navigation menu")).click()
            val firstReady = device.wait(Until.hasObject(By.desc("Synthetic 0")), 5000)
            capture(if (firstReady) "first-page" else "first-page-missing")
            assertTrue("First RTL page destination must be visible within 5 seconds", firstReady)
            val close = device.wait(Until.findObject(By.desc("Close navigation menu")), 12000)
            if (close == null) capture("missing-close-semantics")
            // 91 failure dump proved the Close semantics exist, clickable and visible, just past
            // the 5s mark on SwiftShader; the assertion (fan exposes Close at the menu bounds) is
            // unchanged, only the slow-render tolerance widened.
            assertNotNull("Open fan must expose Close navigation menu within 12 seconds", close)
            assertEquals(menu, close!!.visibleBounds)
            // Three pages at the current 200dp radius; do not skip missing actions.
            repeat(2) {
                val page = device.wait(Until.findObject(By.text("${it + 1}/3")), 5000)
                    ?: throw AssertionError("Missing page control ${it + 1}/3")
                page.click()
                val nextPage = it + 2
                val ready = device.wait(Until.hasObject(By.text("$nextPage/3")), 5000)
                capture(if (ready) "page-$nextPage" else "page-$nextPage-missing")
                assertTrue("Page $nextPage/3 must appear within 5 seconds", ready)
                val oldIds = if (nextPage == 2) (0..4) else (5..9)
                oldIds.forEach { index ->
                    val gone = device.wait(Until.gone(By.desc("Synthetic $index")), 5000)
                    if (!gone) capture("page-$nextPage-stale-destination-$index")
                    assertTrue("Page $nextPage must remove old destination $index", gone)
                }
                val expectedIds = if (nextPage == 2) (5..9) else (10..11)
                expectedIds.forEach { index ->
                    val destinationReady = device.wait(Until.hasObject(By.desc("Synthetic $index")), 5000)
                    if (!destinationReady) capture("page-$nextPage-missing-destination-$index")
                    assertTrue("Page $nextPage must expose destination $index", destinationReady)
                }
            }
            assertTrue(device.wait(Until.hasObject(By.desc("Synthetic 11")), 5000))
            val image = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No variant pixels")
            val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/radial-dark-rtl-page3.png")
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            val lastDest = device.findObject(By.desc("Synthetic 11"))
            runCatching {
                File(diagnosticsDir, "radial-dark-rtl-pre-click.txt").writeText(
                    "preClickBounds=" + runCatching { lastDest.visibleBounds.toString() }.getOrNull())
            }
            lastDest.click()
            runCatching {
                capture("post-click")
                File(diagnosticsDir, "radial-dark-rtl-post-click.txt").writeText(
                    "menuStillOpen=" + device.hasObject(By.desc("Close navigation menu")) +
                    " selectedText=" + (device.findObject(By.textContains("Selected index:"))?.text ?: "null"))
            }
            val selectedOk = device.wait(Until.hasObject(By.text("Selected index: 11")), 5000)
            runCatching {
                device.waitForIdle(2000)
                capture("post-click-settled")
                File(diagnosticsDir, "radial-dark-rtl-post-click-settled.txt").writeText(
                    "verdict=" + selectedOk +
                    " selectedText=" + (device.findObject(By.textContains("Selected index:"))?.text ?: "null") +
                    " menuStillOpen=" + device.hasObject(By.desc("Close navigation menu")))
            }
            assertTrue(selectedOk)
            device.findObject(By.desc("Open navigation menu")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Synthetic 0")), 5000))
            assertFalse("Reopen starts first page", device.hasObject(By.desc("Synthetic 11")))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.desc("Open navigation menu")), 5000))
        } finally { scenario.close() }
    }
}
