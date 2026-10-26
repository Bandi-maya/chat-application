package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.AppColors
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.pow
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Grouped Header tab style sheet, hosted in the debug-only HeaderProofActivity (no backend, preferences or
 * session). Light and dark. Contrast is judged from the theme tokens that should be drawn, and the screenshot
 * is used to confirm those tokens are what was actually painted. Run on a normal and a narrow (about 320dp)
 * emulator profile: one run cannot change the device width.
 */
@RunWith(AndroidJUnit4::class)
class HeaderTabStyleSheetProofTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)

    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch((c shr 16) and 255) + 0.7152 * ch((c shr 8) and 255) + 0.0722 * ch(c and 255)
    }
    private fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }
    private fun dist(a: Int, b: Int) = abs(((a shr 16) and 255) - ((b shr 16) and 255)) + abs(((a shr 8) and 255) - ((b shr 8) and 255)) + abs((a and 255) - (b and 255))
    private fun argb(c: androidx.compose.ui.graphics.Color) = c.toArgb()

    private fun capture(name: String): Bitmap {
        val shot = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No pixels for $name")
        val f = File(inst.targetContext.getExternalFilesDir(null), "screenshots/$name.png")
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return shot
    }

    private fun launch(dark: Boolean): ActivityScenario<HeaderProofActivity> =
        ActivityScenario.launch<HeaderProofActivity>(
            Intent(inst.targetContext, HeaderProofActivity::class.java)
                .putExtra(HeaderProofActivity.EXTRA_DARK, dark).putExtra(HeaderProofActivity.EXTRA_SCREEN, HeaderProofActivity.SCREEN_SHEET)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ).also { assertTrue(device.wait(Until.hasObject(By.text("Header tab style")), 10000)) }

    /** Apply tile: the fill must be the expected token (inset pixel), and some label pixel must be the expected
     *  foreground token (the glyph core of a bold label). The token pair itself must meet 4.5:1. */
    private fun assertApplyPaints(tag: String, enabled: Boolean, shot: Bitmap, b: Rect, dark: Boolean) {
        val c = AppColors(ThemePresets.getSystemDefaultTheme(platformIsLight = !dark))
        val d = inst.targetContext.resources.displayMetrics.density
        val fill = argb(if (enabled) c.accent else c.disabled)
        val fg = argb(if (enabled) c.onAccent else c.foregroundSecondary)
        val fillSeen = shot.getPixel(b.left + (18 * d).toInt(), b.centerY())
        assertTrue("Apply fill ($tag, enabled=$enabled) expected token, saw ${Integer.toHexString(fillSeen)} vs ${Integer.toHexString(fill)}", dist(fillSeen, fill) <= 24)
        var best = Int.MAX_VALUE
        val inset = (12 * d).toInt()
        for (x in (b.left + inset) until (b.right - inset)) for (y in (b.top + inset / 2) until (b.bottom - inset / 2)) best = minOf(best, dist(shot.getPixel(x, y), fg))
        assertTrue("Apply label ($tag, enabled=$enabled) drawn in foreground token, nearest pixel distance $best", best <= 40)
        assertTrue("Apply token contrast ($tag, enabled=$enabled) = ${ratio(fg, fill)}", ratio(fg, fill) >= 4.5)
    }

    private fun run(dark: Boolean) {
        val tag = if (dark) "dark" else "light"
        val scenario = launch(dark)
        try {
            // Group 1 is visible first; group 2 may start below the fold, so it is checked after scrolling.
            assertTrue(device.hasObject(By.text("Tab bubble style")))
            assertFalse("Apply disabled with no change", device.findObject(By.text("Apply")).isEnabled)
            val shot0 = capture("hdr-sheet-$tag-initial")
            assertApplyPaints(tag, false, shot0, device.findObject(By.text("Apply")).visibleBounds, dark)
            shot0.recycle()

            device.findObject(By.text("Soft Rounded Pill")).click()
            device.waitForIdle()
            assertTrue("Apply enabled after change", device.findObject(By.text("Apply")).isEnabled)
            val shot1 = capture("hdr-sheet-$tag-selected")
            assertApplyPaints(tag, true, shot1, device.findObject(By.text("Apply")).visibleBounds, dark)
            shot1.recycle()
            device.findObject(By.text("Capsule Pill")).click()
            device.waitForIdle()
            assertFalse("Apply disabled when draft equals saved", device.findObject(By.text("Apply")).isEnabled)

            device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.hasObject(By.text("Stack Rotate")))
            assertTrue("Second group label reachable", device.hasObject(By.text("3D pager / slider effect")) || device.findObject(By.text("Stack Rotate")) != null)
            val last = device.findObject(By.text("Stack Rotate"))
            assertTrue("Last row reachable", last != null)
            assertTrue("Last row above footer", last.visibleBounds.bottom <= device.findObject(By.text("Apply")).visibleBounds.top)
            capture("hdr-sheet-$tag-scrolled").recycle()

            last.click()
            device.findObject(By.text("Cancel")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Saved: Capsule Pill|Cube 3D|0")), 5000))
        } finally { scenario.close() }

        launch(dark).use { // X discards
            device.findObject(By.text("Glassmorphism Glow")).click()
            device.findObject(By.desc("Close Header tab style")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Saved: Capsule Pill|Cube 3D|0")), 5000))
        }
        launch(dark).use { // Apply saves both values exactly once
            device.findObject(By.text("Outlined Bubble")).click()
            device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.hasObject(By.text("Depth Zoom")))
            device.findObject(By.text("Depth Zoom")).click()
            device.findObject(By.text("Apply")).click()
            assertTrue("Apply saves both", device.wait(Until.hasObject(By.text("Saved: Outlined Bubble|Depth Zoom|1")), 5000))
        }
    }

    @Test fun lightSheet() = run(dark = false)
    @Test fun darkSheet() = run(dark = true)
}
