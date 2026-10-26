package com.example.chat

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ChatyDropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory popup only: no backend, real account, navigation, or messages. */
@RunWith(AndroidJUnit4::class)
class OverflowSizingProofTest {
    /** Screen-coordinate safe bounds from the actual activity window, including cutouts. */
    private fun safeBounds(scenario: ActivityScenario<ChatFixtureActivity>): Rect {
        var bounds: Rect? = null
        scenario.onActivity { activity ->
            val decor = activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)
                ?: throw AssertionError("No actual window insets available")
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val location = IntArray(2)
            decor.getLocationOnScreen(location)
            bounds = Rect(location[0] + safe.left, location[1] + safe.top,
                location[0] + decor.width - safe.right, location[1] + decor.height - safe.bottom)
        }
        return bounds ?: throw AssertionError("No actual safe window bounds")
    }

    private fun assertInsideSafeWindow(bounds: Rect, safe: Rect, context: String) {
        assertTrue("$context left inside safe window: $bounds / $safe", bounds.left >= safe.left)
        assertTrue("$context right inside safe window: $bounds / $safe", bounds.right <= safe.right)
        assertTrue("$context top below status/cutout: $bounds / $safe", bounds.top >= safe.top)
        assertTrue("$context bottom above navigation/cutout: $bounds / $safe", bounds.bottom <= safe.bottom)
    }

    @Test fun realHomeAndChatOverflowHaveBoundedSurfaces() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        for ((screen, firstLabel, name) in listOf(
            Triple(ChatFixtureActivity.SCREEN_HOME, "New Group", "28-real-home-overflow"),
            Triple(ChatFixtureActivity.SCREEN_DETAIL, "View contact info", "29-real-chat-overflow"),
        )) {
            val intent = android.content.Intent(inst.targetContext, ChatFixtureActivity::class.java)
                .putExtra(ChatFixtureActivity.EXTRA_SCREEN, screen)
            ActivityScenario.launch<ChatFixtureActivity>(intent).use { scenario ->
                val more = By.desc(if (screen == ChatFixtureActivity.SCREEN_HOME) "More options" else "Chat options")
                assertTrue("Overflow trigger exists: $screen", device.wait(Until.hasObject(more), 10000))
                device.findObject(more).click()
                assertTrue("Real menu opens: $screen", device.wait(Until.hasObject(By.text(firstLabel)), 5000))
                var surface = device.findObject(By.text(firstLabel))
                while (surface != null && !surface.isScrollable) surface = surface.parent
                assertTrue("Real popup exposes scroll surface: $screen", surface != null)
                val bounds = surface!!.visibleBounds
                val safe = safeBounds(scenario)
                assertInsideSafeWindow(bounds, safe, "Real $screen popup")
                val config = inst.targetContext.resources.configuration
                val density = inst.targetContext.resources.displayMetrics.density
                assertTrue("Menu height <=80%: $screen", bounds.height() <= config.screenHeightDp * density * 0.8f + 2f)
                assertTrue("Menu inside screen: $screen", bounds.left >= 0 && bounds.right <= device.displayWidth)
                // On ordinary phone widths these labels need much less than the screen width.
                if (config.screenWidthDp >= 320 && config.fontScale <= 1.3f) {
                    assertTrue("Menu does not fill device width: $screen", bounds.width() < device.displayWidth * 0.9f)
                }
                android.util.Log.i("OverflowSizingProof", "$screen popup bounds=$bounds screen=${device.displayWidth}x${device.displayHeight}")
                val image = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No real-menu pixels")
                val orientation = if (device.displayWidth > device.displayHeight) "landscape" else "portrait"
                val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/$name-$orientation.png")
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
                device.pressBack() // Never select real actions.
            }
        }
    }

    @Test fun popupPortraitFitsAndScrolls() = popupFitsContentCapsHeightAndScrollsToLastAction()

    @Test fun popupLandscapeFitsAndScrolls() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.setOrientationLeft()
        try { popupFitsContentCapsHeightAndScrollsToLastAction() }
        finally { device.setOrientationNatural(); device.unfreezeRotation() }
    }

    private fun popupFitsContentCapsHeightAndScrollsToLastAction() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val picks = AtomicInteger()
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            var generation = 0
            fun show(labels: List<String>) {
                val phase = ++generation
                scenario.onActivity { activity -> activity.setContent {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                        var isOpen by remember(phase) { mutableStateOf(true) }
                        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopEnd) {
                            if (isOpen) ChatyDropdownMenu(
                                expanded = true, onDismissRequest = { isOpen = false },
                                modifier = Modifier.semantics { contentDescription = "Overflow test surface" },
                            ) {
                                labels.forEach { label -> ChatyDropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { picks.incrementAndGet(); isOpen = false },
                                ) }
                            }
                        }
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.desc("Overflow test surface")), 10000))
                device.waitForIdle()
            }
            fun capture(name: String) {
                val image = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No pixels")
                val orientation = if (device.displayWidth > device.displayHeight) "landscape" else "portrait"
                val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/$name-$orientation.png")
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
            show(listOf("Short"))
            val shortWidth = device.findObject(By.desc("Overflow test surface")).visibleBounds.width()
            capture("25-overflow-short")
            show((1..30).map { if (it == 1) "Message Scheduler" else "Action $it" })
            val menu = device.findObject(By.desc("Overflow test surface")).visibleBounds
            android.util.Log.i("OverflowSizingProof", "Synthetic shortWidth=$shortWidth longBounds=$menu screen=${device.displayWidth}x${device.displayHeight}")
            val safe = safeBounds(scenario)
            assertInsideSafeWindow(menu, safe, "Synthetic popup")
            val config = inst.targetContext.resources.configuration
            val density = inst.targetContext.resources.displayMetrics.density
            assertTrue("Content determines width", menu.width() > shortWidth)
            assertTrue("Popup remains within device width", menu.left >= 0 && menu.right <= device.displayWidth)
            assertTrue("Popup remains within device height", menu.top >= 0 && menu.bottom <= device.displayHeight)
            assertTrue("Popup height capped at 80%", menu.height() <= config.screenHeightDp * density * 0.8f + 2f)
            capture("26-overflow-long-before-scroll")
            repeat(12) {
                if (device.findObject(By.text("Action 30")) == null) {
                    device.swipe(menu.centerX(), menu.bottom - 30, menu.centerX(), menu.top + 30, 25)
                    device.waitForIdle()
                }
            }
            assertTrue("Last action reachable by scroll", device.wait(Until.hasObject(By.text("Action 30")), 3000))
            capture("27-overflow-last-action")
            device.findObject(By.text("Action 30")).click()
            assertTrue("Action dismisses popup", device.wait(Until.gone(By.desc("Overflow test surface")), 3000))
            assertEquals("One callback", 1, picks.get())
            show(listOf("Back dismiss"))
            device.pressBack()
            assertTrue("Back dismissal preserved", device.wait(Until.gone(By.desc("Overflow test surface")), 3000))
        }
    }
}
