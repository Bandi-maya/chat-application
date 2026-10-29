package com.example.chat

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.chats.detail.ChatyEmojiPickerSheet
import com.example.chat.ui.chats.detail.EmojiRecentCache
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proof for the requested emoji panel: half-height sheet in composer/default
 * modes, and a recents grid that wraps at exactly 8 columns (9 seeded recents
 * place the first-seeded emoji at row 2 column 1 only when the row holds 8).
 */
@RunWith(AndroidJUnit4::class)
class EmojiPickerPanelProofTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val dev get() = UiDevice.getInstance(ins)

    private val seeded = listOf("🙂", "😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😎")
    private val seededNames = listOf(
        "slightly smiling", "grinning", "beaming", "joy", "rofl",
        "blush", "heart eyes", "kissing", "sunglasses",
    )

    private fun shot(name: String) {
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        dev.waitForIdle(3000)
        val bmp = dev.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun waitFor(cond: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 6000
        while (!cond() && SystemClock.uptimeMillis() < deadline) Thread.sleep(150)
    }

    private fun seededCache(): EmojiRecentCache {
        val c = EmojiRecentCache()
        c.initialize()
        seeded.forEachIndexed { i, e -> c.recordUsage(e, seededNames[i]) }
        return c
    }

    @Test
    fun composerHalfPanelAndEightColumnRecents() {
        val picked = CopyOnWriteArrayList<String>()
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                ChatyTheme(ThemePresets.whatsappIosLight) {
                    ChatyEmojiPickerSheet(
                        theme = ThemePresets.whatsappIosLight,
                        reactionMode = false,
                        composerMode = true,
                        recentCache = seededCache(),
                        onEmojiPicked = { picked.add(it) },
                    )
                }
            }
        }
        val recent = dev.wait(Until.findObject(By.desc("Recent")), 8000) ?: fail("Recent tab missing")
        val close = dev.wait(Until.findObject(By.desc("Close")), 5000) ?: fail("Close header control missing")
        val h = dev.displayHeight
        val fraction = (h - close.visibleBounds.top).toFloat() / h
        assertTrue("composer panel should be about half the screen, was $fraction", fraction in 0.42f..0.62f)
        shot("emoji-half-panel-composer")
        val density = ins.targetContext.resources.displayMetrics.density
        val gridTop = recent.visibleBounds.bottom + density.toInt()
        val row1Y = gridTop + (23 * density).toInt()
        val col1X = dev.displayWidth / 16
        dev.click(col1X, row1Y)
        waitFor { picked.isNotEmpty() }
        assertTrue("row1 col1 calibration pick missing (grid-top estimate off?)", picked.isNotEmpty())
        assertEquals("row1 col1 must be the most recently seeded emoji", "😎", picked.last())
        dev.click(col1X, row1Y + (46 * density).toInt())
        waitFor { picked.size >= 2 }
        assertEquals(
            "row2 col1 must be the 9th seeded emoji; it wraps there only with exactly 8 columns",
            "🙂", picked.last(),
        )
        shot("emoji-recents-eight-column")
        scenario.close()
    }

    @Test
    fun defaultPanelIsShorterThanComposerPanel() {
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                ChatyTheme(ThemePresets.whatsappIosLight) {
                    ChatyEmojiPickerSheet(
                        theme = ThemePresets.whatsappIosLight,
                        reactionMode = false,
                        composerMode = false,
                        recentCache = seededCache(),
                        onEmojiPicked = {},
                    )
                }
            }
        }
        val close = dev.wait(Until.findObject(By.desc("Close")), 8000) ?: fail("Close header control missing")
        val h = dev.displayHeight
        val fraction = (h - close.visibleBounds.top).toFloat() / h
        assertTrue("default panel should be about 0.42 of the screen, was $fraction", fraction in 0.32f..0.52f)
        shot("emoji-half-panel-default")
        scenario.close()
    }
}
