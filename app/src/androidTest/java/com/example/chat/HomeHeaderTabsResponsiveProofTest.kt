package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.ui.navigation.NavigationCatalog
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WhatsApp OLD UI header tabs in the debug-only HeaderProofActivity (no backend): 5 tabs at 320dp wide with the
 * font scale at 1.0 and 2.0. Tabs are found by stable test tags (home-tab-<id>, scroller home-tabs-scroll).
 * For every tab: reachable by scrolling the tag-identified scroller, at least 48dp tall, FULLY contained in the
 * scroller's viewport, label fully inside the tab (tab width minus label width equals the 24dp padding, which a
 * clipped tab or label cannot satisfy), and tapping selects it. Then an external (non-tap) selection of the last
 * tab must bring that tab fully into view. Screenshots hdr-tabs-<scale>-*.png still need a visual look.
 */
@RunWith(AndroidJUnit4::class)
class HomeHeaderTabsResponsiveProofTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private val pkg get() = inst.targetContext.packageName
    private val ids = listOf("chats", "updates", "calls", "tasks", "settings")

    private fun shot(name: String) {
        val b = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No pixels for $name")
        val f = File(inst.targetContext.getExternalFilesDir(null), "screenshots/$name.png")
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun res(tag: String) = By.res(tag)

    /** Fails unless the tab and its label are completely visible inside the scroller. */
    private fun assertFullyShown(id: String, tag: String) {
        val d = inst.targetContext.resources.displayMetrics.density
        val label = NavigationCatalog.labelFor(id).uppercase()
        val scroller = device.wait(Until.findObject(res("home-tabs-scroll")), 3000)
        assertTrue("Scroller present ($tag)", scroller != null)
        val tab = device.wait(Until.findObject(res("home-tab-$id")), 3000)
        assertTrue("Tab '$id' present ($tag)", tab != null)
        val text = device.findObject(By.text(label))
        assertTrue("Label '$label' present ($tag)", text != null)
        val sb = scroller.visibleBounds; val tb = tab.visibleBounds; val lb = text.visibleBounds
        assertTrue("Tab '$id' ($tag) inside viewport: tab=$tb viewport=$sb",
            tb.left >= sb.left - 1 && tb.right <= sb.right + 1)
        assertTrue("Tab '$id' ($tag) height >= 48dp, was ${tb.height() / d}dp", tb.height() >= 48 * d - 1)
        assertTrue("Label '$label' ($tag) not clipped: tab ${tb.width()} label ${lb.width()} padding ${24 * d}",
            tb.width() - lb.width() >= 24 * d - 2)
    }

    private fun run(fontScale: Float, tag: String) {
        val s = ActivityScenario.launch<HeaderProofActivity>(
            Intent(inst.targetContext, HeaderProofActivity::class.java)
                .putExtra(HeaderProofActivity.EXTRA_SCREEN, HeaderProofActivity.SCREEN_TABS)
                .putExtra(HeaderProofActivity.EXTRA_FONT_SCALE, fontScale)
                .putExtra(HeaderProofActivity.EXTRA_WIDTH_DP, 320)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            assertTrue(device.wait(Until.hasObject(res("home-tabs-scroll")), 10000))
            shot("hdr-tabs-$tag-start")
            ids.forEachIndexed { i, id ->
                var tries = 0
                // Scroll right until the whole tab is in the viewport.
                while (tries++ < 8) {
                    val sc = device.findObject(res("home-tabs-scroll"))
                    val t = device.findObject(res("home-tab-$id"))
                    if (t != null && t.visibleBounds.left >= sc.visibleBounds.left - 1 && t.visibleBounds.right <= sc.visibleBounds.right + 1) break
                    sc.swipe(Direction.LEFT, 0.5f)
                }
                assertFullyShown(id, tag)
                device.findObject(res("home-tab-$id")).click()
                assertTrue("Tab '$id' ($tag) selected after tap", device.wait(Until.hasObject(By.text("Tab: $i")), 3000))
                assertFullyShown(id, tag)
                shot("hdr-tabs-$tag-$i")
            }
            // External change while the strip is scrolled elsewhere: scroll back to the start, then select "settings".
            repeat(6) { device.findObject(res("home-tabs-scroll")).swipe(Direction.RIGHT, 0.8f) }
            device.findObject(By.text("External select")).click()
            assertTrue("External selection applied ($tag)", device.wait(Until.hasObject(By.text("Tab: 4")), 3000))
            device.waitForIdle()
            assertFullyShown("settings", tag + "-external")
            shot("hdr-tabs-$tag-external")
        } finally { s.close() }
    }

    @Test fun tabsReachableAtFontScale1() = run(1f, "x1")
    @Test fun tabsReachableAtFontScale2() = run(2f, "x2")
}
