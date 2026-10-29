// gb-parity shadow of the overlay-resident file of the same path (candidate-overlay manifest entry).
// Next26 (post-build-93 diagnosis): adds a bounded state-settle between the six fling swipes and the
// 'chats' click in the external-change step; see the Next26 comment in run(). No assertion, deadline,
// or app behavior changed. Override count bumped 50 -> 51 in build-kotlin-apk.yml with this file.
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
        // The strip scrolls the selected tab into view with an animation (and a bold label widens its tab), and
        // UiAutomator idle does not cover Compose animations: poll up to 3s for the settled state, then assert
        // exactly the same thresholds below on the last measurement.
        val settleBy = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < settleBy) {
            try {
                val t0 = device.findObject(res("home-tab-$id")); val l0 = device.findObject(By.text(label))
                if (t0 != null && l0 != null && t0.visibleBounds.width() >= 320f * d / ids.size - 2 &&
                    t0.visibleBounds.width() - l0.visibleBounds.width() >= 24 * d - 2) break
            } catch (_: androidx.test.uiautomator.StaleObjectException) { }
            Thread.sleep(100)
        }
        val scroller = device.wait(Until.findObject(res("home-tabs-scroll")), 3000)
        assertTrue("Scroller present ($tag)", scroller != null)
        val tab = device.wait(Until.findObject(res("home-tab-$id")), 3000)
        assertTrue("Tab '$id' present ($tag)", tab != null)
        val text = device.findObject(By.text(label))
        assertTrue("Label '$label' present ($tag)", text != null)
        val sb = scroller.visibleBounds; val tb = tab.visibleBounds; val lb = text.visibleBounds
        // Diagnostics only (no pass criterion): pixels and rects recorded BEFORE any assertion below can fail.
        shot("hdr-tabs-diag-$tag-$id")
        File(inst.targetContext.getExternalFilesDir(null), "screenshots/hdr-tabs-diag-$tag-$id.txt").apply { parentFile?.mkdirs() }
            .writeText("scroller=$sb tab=$tb label=$lb density=$d tabsPresent=${device.findObjects(By.res("home-tab-")).size}\n" +
                ids.joinToString("\n") { i -> "$i=" + device.findObject(res("home-tab-$i"))?.visibleBounds })
        assertTrue("Tab '$id' ($tag) inside viewport: tab=$tb viewport=$sb",
            tb.left >= sb.left - 1 && tb.right <= sb.right + 1)
        assertTrue("Tab '$id' ($tag) fully shown, not clipped by the viewport: width ${tb.width()} < share ${320f * d / ids.size}",
            tb.width() >= 320f * d / ids.size - 2)
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
                // Small, bounded steps (<= 30 x 15% of the viewport, slow so there is little fling). Direction comes from the
                // measured state: clipped on the left -> RIGHT, clipped on the right or off-screen to the right -> LEFT.
                val dens = inst.targetContext.resources.displayMetrics.density
                val label = NavigationCatalog.labelFor(id).uppercase()
                var tries = 0
                while (tries++ < 30) {
                    val sc = device.findObject(res("home-tabs-scroll"))
                    val t = device.findObject(res("home-tab-$id"))
                    val l = device.findObject(By.text(label))
                    // Same thresholds as the final assert: inside the viewport, at least its equal share wide, and the
                    // full 24dp of label padding (a one-side-clipped tab keeps only 12dp).
                    if (t != null && l != null && t.visibleBounds.left >= sc.visibleBounds.left - 1 && t.visibleBounds.right <= sc.visibleBounds.right + 1 &&
                        t.visibleBounds.width() >= 320f * dens / ids.size - 2 &&
                        t.visibleBounds.width() - l.visibleBounds.width() >= 24 * dens - 2) break
                    val dir = when {
                        t != null && t.visibleBounds.left <= sc.visibleBounds.left + 1 -> Direction.RIGHT
                        t != null -> Direction.LEFT
                        ids.indices.firstOrNull { j -> device.findObject(res("home-tab-${ids[j]}")) != null }?.let { it < i } ?: true -> Direction.LEFT
                        else -> Direction.RIGHT
                    }
                    sc.swipe(dir, 0.15f, 800)
                }
                assertFullyShown(id, tag)
                device.findObject(res("home-tab-$id")).click()
                val selectedAfterTap = device.wait(Until.hasObject(By.text("Tab: $i")), 3000)
                // Diagnostics before the unchanged assertion: where the click landed and what the screen says now.
                if (!selectedAfterTap) runCatching {
                    shot("hdr-tabs-$tag-$i-tap-failed")
                    val tb = device.findObject(res("home-tab-$id"))?.visibleBounds
                    File(inst.targetContext.getExternalFilesDir(null), "screenshots/hdr-tabs-$tag-$i-tap-failed.txt").writeText(
                        "tab=$tb selected=${device.findObject(res("home-tab-$id"))?.isSelected} " +
                            device.findObjects(By.pkg(inst.targetContext.packageName)).joinToString(" | ") { "${it.className} text=${it.text} bounds=${it.visibleBounds}" })
                }
                assertTrue("Tab '$id' ($tag) selected after tap", selectedAfterTap)
                assertFullyShown(id, tag)
                shot("hdr-tabs-$tag-$i")
            }
            // External change while the strip is scrolled elsewhere: scroll back to the start, then select "settings".
            repeat(6) { device.findObject(res("home-tabs-scroll")).swipe(Direction.RIGHT, 0.8f) }
            // Next21 evidence: the loop above ends on 'settings', so the host already held sel=4 and "External select" (sel = 4)
            // changed nothing; no selection change means no scroll-to-selected, and the strip stayed at 0. Select 'chats' first
            // so the external change to 4 is a real change while the strip sits at the start.
            // Next26 settle: the six swipes above are fast fling swipes (0.8f, default speed); a tap landing while the fling
            // is still decelerating is consumed as a scroll stop instead of a selection (post-fling tap consumption remains a
            // hypothesis for the x1/x2 failures - intermittent app-selection failure is NOT ruled out by this change). Wait for
            // the fixture's "Scroll:" readout to hold one value across three samples 500ms apart, i.e. a >=1s observed-stable
            // window; two quick equal samples alone cannot establish idle. Bounded at 10s: on timeout we proceed and the
            // original unchanged 3s selection assertion below still decides pass/fail. No assertion or deadline widened.
            val scrollRe = Regex("^Scroll: (\\d+)/(\\d+)")
            var lastScroll = -1
            var stableSamples = 0
            var waitedMs = 0
            while (stableSamples < 3 && waitedMs < 10000) {
                val v = device.findObject(By.textStartsWith("Scroll:"))?.text
                    ?.let { scrollRe.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: -1
                if (v >= 0 && v == lastScroll) stableSamples++ else { stableSamples = 0; lastScroll = v }
                if (stableSamples < 3) { Thread.sleep(500); waitedMs += 500 }
            }
            device.wait(Until.findObject(res("home-tab-chats")), 3000)!!.click()
            assertTrue("Chats selected before the external change ($tag)", device.wait(Until.hasObject(By.text("Tab: 0")), 3000))
            device.waitForIdle()
            device.findObject(By.text("External select")).click()
            // Diagnostics only: tab-1 'settings' rect sampled for 3s after the external selection, to show whether the
            // strip ever moves (then returns) or never scrolls at all.
            File(inst.targetContext.getExternalFilesDir(null), "screenshots/hdr-tabs-ext-samples-$tag.txt").apply { parentFile?.mkdirs() }
                .writeText((0..12).joinToString("\n") { n ->
                    Thread.sleep(250)
                    // Next24: this sampler is diagnostics only; a node going stale mid-sample threw StaleObjectException here
                    // (HomeHeaderTabsResponsiveProofTest.kt:141-142) and failed the test before any assertion ran.
                    fun <T> safe(f: () -> T): String = try { f().toString() } catch (_: androidx.test.uiautomator.StaleObjectException) { "<stale>" }
                    "t=${n * 250 + 250}ms ${safe { device.findObject(By.textStartsWith("Scroll:"))?.text }} chats=${safe { device.findObject(res("home-tab-chats"))?.visibleBounds }} settings=${safe { device.findObject(res("home-tab-settings"))?.visibleBounds }}" })
            assertTrue("External selection applied ($tag)", device.wait(Until.hasObject(By.text("Tab: 4")), 3000))
            device.waitForIdle()
            assertFullyShown("settings", tag + "-external")
            shot("hdr-tabs-$tag-external")
        } finally { s.close() }
    }

    @Test fun tabsReachableAtFontScale1() = run(1f, "x1")
    @Test fun tabsReachableAtFontScale2() = run(2f, "x2")
}
