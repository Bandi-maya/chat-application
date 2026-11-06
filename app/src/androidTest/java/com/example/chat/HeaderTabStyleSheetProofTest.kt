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
        // Same rule the tile uses for its label colour; the 4.5:1 assert below stays on the resulting token pair, and the
        // pixel scan independently checks that this colour is what is drawn.
        val fg = argb(if (enabled) com.example.chat.ui.kit.chatyReadableOn(c.accent, c.onAccent, c.foreground)
            else com.example.chat.ui.kit.chatyReadableOn(c.disabled, c.foregroundSecondary, c.foreground))
        val fillSeen = shot.getPixel(b.left + (18 * d).toInt(), b.centerY())
        assertTrue("Apply fill ($tag, enabled=$enabled) expected token, saw ${Integer.toHexString(fillSeen)} vs ${Integer.toHexString(fill)}", dist(fillSeen, fill) <= 24)
        // Scan only the capsule interior: the box inscribed in a fully rounded tile (x from left+h/2 to right-h/2, middle 70%
        // of the height), so corner pixels outside the fill can never be counted. Only pixels that are NOT the fill count as
        // glyph pixels, so background or fill pixels cannot satisfy the check.
        val h = b.height()
        val x0 = b.left + h / 2; val x1 = b.right - h / 2
        val y0 = b.top + (h * 0.15f).toInt(); val y1 = b.bottom - (h * 0.15f).toInt()
        if (x1 <= x0 || y1 <= y0) throw AssertionError("Apply ROI empty or degenerate ($tag): tile=$b roi=($x0,$y0)-($x1,$y1)")
        var best = Int.MAX_VALUE; var bestPx = 0; var glyphPixels = 0
        for (x in x0 until x1) for (y in y0 until y1) {
            val p = shot.getPixel(x, y)
            if (dist(p, fillSeen) < 24) continue
            glyphPixels++
            val dd = dist(p, fg)
            if (dd < best) { best = dd; bestPx = p }
        }
        assertTrue("Apply label drew no glyph pixels inside the capsule interior ($tag, enabled=$enabled), roi=($x0,$y0)-($x1,$y1)", glyphPixels >= 30)
        assertTrue("Apply label ($tag, enabled=$enabled) drawn in foreground token, nearest glyph pixel distance $best", best <= 40)
        // Independent of the tokens: the drawn glyph colour against the drawn fill.
        assertTrue("Apply drawn glyph/fill contrast ($tag, enabled=$enabled) = ${ratio(bestPx, fillSeen)}", ratio(bestPx, fillSeen) >= 4.5)
        assertTrue("Apply token contrast ($tag, enabled=$enabled) = ${ratio(fg, fill)}", ratio(fg, fill) >= 4.5)
    }

    /** Diagnostic only (never a pass criterion): every node matching text "Apply", with its class, flags and bounds,
     *  recorded to screenshots/apply-nodes-<tag>.txt and returned for the assertion message. */
    /** Compose exposes the Apply label as its own unmerged TextView child (enabled, not clickable); the tile that
     *  carries the enabled/clickable state is its parent. Next7 diagnostics: label node enabled=true clickable=false. */
    // Nested preview strips scroll too. Pick the outer list by visible area.
    private fun outerList() = device.findObjects(By.scrollable(true))
        .maxByOrNull { val bounds = it.visibleBounds; bounds.width().toLong() * bounds.height() }

    private fun applyTile() = device.findObject(By.hasChild(By.text("Apply")))
        ?: throw AssertionError("No node with a child labelled Apply")

    private fun applyDiagnostics(tag: String): String {
        val nodes = device.findObjects(By.text("Apply")) + device.findObjects(By.hasChild(By.text("Apply")))
        val d = nodes.mapIndexed { i, n ->
            "#$i class=${n.className} res=${n.resourceName} enabled=${n.isEnabled} clickable=${n.isClickable} " +
                "selected=${n.isSelected} checkable=${n.isCheckable} checked=${n.isChecked} focusable=${n.isFocusable} bounds=${n.visibleBounds}"
        }.joinToString(" | ").ifEmpty { "no node with text Apply" }
        val f = File(inst.targetContext.getExternalFilesDir(null), "screenshots/apply-nodes-$tag.txt")
        f.parentFile?.mkdirs(); f.writeText(d)
        return d
    }

    private fun run(dark: Boolean) {
        val tag = if (dark) "dark" else "light"
        val scenario = launch(dark)
        try {
            // Group 1 is visible first; group 2 may start below the fold, so it is checked after scrolling.
            assertTrue(device.hasObject(By.text("Tab bubble style")))
            val shot0 = capture("hdr-sheet-$tag-initial")
            val diag = applyDiagnostics(tag)
            assertFalse("Apply disabled with no change; node diagnostics: $diag", applyTile().isEnabled)
            assertApplyPaints(tag, false, shot0, applyTile().visibleBounds, dark)
            shot0.recycle()

            device.findObject(By.text("Soft Rounded Pill")).click()
            device.waitForIdle()
            assertTrue("Apply enabled after change", applyTile().isEnabled)
            val shot1 = capture("hdr-sheet-$tag-selected")
            assertApplyPaints(tag, true, shot1, applyTile().visibleBounds, dark)
            shot1.recycle()
            device.findObject(By.text("Capsule Pill")).click()
            device.waitForIdle()
            assertFalse("Apply disabled when draft equals saved", applyTile().isEnabled)

            val scrollers = device.findObjects(By.scrollable(true))
            // Bounded slow-drag navigation instead of scrollUntil: controlled UiObject2.scroll
            // is consumed by the ModalBottomSheet without moving the LazyColumn (capture
            // 40823906408 diag: scrollUntil=false, list still at top in both sheets). Slow
            // drags compose each row; a bounded event wait after each drag lets the a11y
            // tree expose it. At most 8 drags; returns early on match. Assertions unchanged.
            var navDrags = 0
            var navFound = device.wait(Until.hasObject(By.text("Stack Rotate")), 1500)
            val navList = outerList()
            while (!navFound && navDrags < 8 && navList != null) {
                val nb = navList.visibleBounds
                device.swipe(nb.centerX(), nb.top + nb.height() * 7 / 10, nb.centerX(), nb.top + nb.height() * 3 / 10, 30)
                navDrags++
                navFound = device.wait(Until.hasObject(By.text("Stack Rotate")), 1500)
                capture("hdr-sheet-$tag-nav-$navDrags").recycle()
            }
            runCatching {
                diagWrite(inst, "hdr-sheet-nav-$tag.txt", "navDrags=$navDrags navFound=$navFound\n" + nodeDump())
            }
            val scrolled = navFound
            // Diagnostics only, before the assertions below: pixels and every visible text/scrollable node.
            capture("hdr-sheet-$tag-scrolled-diag").recycle()
            // Diagnostics must not replace or interrupt the assertions below.
            runCatching {
                diagWrite(inst, "hdr-sheet-scroll-$tag.txt",
                    "scrollables=${scrollers.map { n -> runCatching { n.visibleBounds }.getOrNull() }} scrollUntil=$scrolled\n" + nodeDump())
            }
            assertTrue("Second group label reachable", device.hasObject(By.text("3D pager / slider effect")) || device.findObject(By.text("Stack Rotate")) != null)
            // Diagnostics first: wait for the row (a stale tree right after scrollUntil is a hypothesis, not a finding),
            // then dump what UiAutomator sees, before the unchanged assertion.
            // a11y snapshot can lag the composed lazy list on a contended emulator (rows painted but not
            // yet exposed to UiAutomator; capture 40751995833 light run: settled pixels show the row,
            // the tree did not). Poll with idle settling; the assertion below is unchanged - the row
            // must still appear in the tree to pass.
            var last = device.wait(Until.findObject(By.text("Stack Rotate")), 3000)
            var lastPolls = 0
            while (last == null && lastPolls < 4) { device.waitForIdle(1000); last = device.findObject(By.text("Stack Rotate")); lastPolls++ }
            runCatching {
                diagWrite(inst, "hdr-sheet-last-row-$tag.txt",
                    "found=${last != null} afterWait\n" + nodeDump())
            }
            // DIAGNOSTIC ONLY (does not change the verdict): settled-tree observation after the
            // original 3s deadline has produced its verdict, recorded before the unchanged assertion.
            runCatching {
                device.waitForIdle(2000)
                val settledRow = device.findObject(By.text("Stack Rotate"))
                diagWrite(inst, "hdr-sheet-last-row-settled-$tag.txt",
                    "settledFound=${settledRow != null} afterIdleWait originalVerdict=${last != null}\n" + nodeDump())
                capture("hdr-sheet-$tag-settled-diag").recycle()
            }
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
            // Next16 light: scrollUntil returned false and the row lookup was null although the list can fit; wait for the row
            // and only scroll when it is not already present.
            if (!device.hasObject(By.text("Depth Zoom"))) outerList()?.scrollUntil(Direction.DOWN, Until.hasObject(By.text("Depth Zoom")))
            val depth = device.wait(Until.findObject(By.text("Depth Zoom")), 5000)
                ?: throw AssertionError("Depth Zoom row not found; nodes: " + device.findObjects(By.pkg(inst.targetContext.packageName)).joinToString { n ->
                    // Next21: reading a node that went stale threw StaleObjectException and hid this message.
                    runCatching { "${n.text}@${n.visibleBounds}" }.getOrDefault("<stale>")
                })
            depth.click()
            device.findObject(By.text("Apply")).click()
            assertTrue("Apply saves both", device.wait(Until.hasObject(By.text("Saved: Outlined Bubble|Depth Zoom|1")), 5000))
        }
    }

    /** Tapping the real preview (not the name or radio) selects that row's draft, commits nothing until Apply, and the
     *  preview chips stay out of the accessibility tree. Tap point = 36dp inside the selectable tile, i.e. over the preview slot
     *  (the preview has no node by design). Checks the tile's radio state, not only Apply. */
    private fun previewTapSelects(dark: Boolean) {
        val tag = if (dark) "dark" else "light"
        launch(dark).use {
            assertTrue(device.hasObject(By.text("Tab bubble style")))
            assertTrue("Preview chips must not be exposed to accessibility (Unread seen)", device.findObjects(By.text("Unread")).isEmpty())
            assertFalse("Apply disabled before any change", applyTile().isEnabled)
            val label = device.findObject(By.text("Soft Rounded Pill")) ?: throw AssertionError("No Soft Rounded Pill row")
            // The label sits in a Column inside the selectable Row, so label.parent is the Column. Walk up to the
            // actual selectable tile (Role.RadioButton => checkable), whose checked state is the draft selection.
            var tile = label.parent
            while (tile != null && !tile.isCheckable) tile = tile.parent
            tile = tile ?: throw AssertionError("No checkable ancestor of the Soft Rounded Pill label")
            assertFalse("Row must start unselected", tile.isChecked)
            val tb = tile.visibleBounds
            val dp = inst.targetContext.resources.displayMetrics.density
            // Row padding is 12dp, then the preview slot (<=42% of the row, 150dp preview): 36dp from the left edge is
            // inside the preview and left of the label Column.
            val x = tb.left + (36 * dp).toInt(); val y = tb.centerY()
            assertTrue("Tap point ($x) must be left of the label (${label.visibleBounds.left})", x < label.visibleBounds.left)
            capture("hdr-sheet-$tag-before-preview-tap").recycle()
            device.click(x, y)
            device.waitForIdle()
            capture("hdr-sheet-$tag-after-preview-tap").recycle()
            val tile2 = device.findObject(By.text("Soft Rounded Pill"))?.let { var t = it.parent; while (t != null && !t.isCheckable) t = t.parent; t }
            assertTrue("Preview tap at ($x,$y) in tile $tb did not check the row's radio", tile2?.isChecked == true)
            assertTrue("Apply enabled after draft change", applyTile().isEnabled)
            // While the sheet is open the host shows no "Saved:" text (it only exists once closed), so the invariant is
            // read from the host's onApply counter: the sheet's only commit path. 0 now means no commit, not even a
            // temporary one that is rolled back later.
            assertTrue("Nothing may be committed before Apply (onApply calls=${HeaderProofActivity.applyCalls})", HeaderProofActivity.applyCalls == 0)
            device.findObject(By.text("Cancel")).click()
            assertTrue("Cancel keeps the saved value", device.wait(Until.hasObject(By.text("Saved: Capsule Pill|Cube 3D|0")), 5000))
            assertTrue("No commit after Cancel (onApply calls=${HeaderProofActivity.applyCalls})", HeaderProofActivity.applyCalls == 0)
        }
    }

    @Test fun lightPreviewTap() = previewTapSelects(dark = false)
    @Test fun darkPreviewTap() = previewTapSelects(dark = true)
    @Test fun lightSheet() = run(dark = false)
    @Test fun darkSheet() = run(dark = true)

    /** Diagnostic file writer that cannot fail silently: creates the directory, and echoes the text to logcat (tag HdrDiag)
     *  so the evidence survives even if the artifact folder is not pulled. */
    /** Node dump that cannot throw: Next23 produced no dark scroll/last-row file, consistent with a node going stale while
     *  the text was being built (the argument is evaluated before the write, inside runCatching). Each node is read on its
     *  own; a stale node is recorded as such. */
    private fun nodeDump(): String = try {
        device.findObjects(By.pkg(inst.targetContext.packageName)).joinToString("\n") { n ->
            try { "${n.className} text=${n.text} desc=${n.contentDescription} bounds=${n.visibleBounds}" } catch (_: androidx.test.uiautomator.StaleObjectException) { "<stale node>" }
        }
    } catch (e: Exception) { "nodeDump failed: $e" }

    private fun diagWrite(inst: android.app.Instrumentation, name: String, text: String) {
        android.util.Log.w("HdrDiag", "$name\n$text")
        val f = File(inst.targetContext.getExternalFilesDir(null), "screenshots/$name")
        f.parentFile?.mkdirs()
        f.writeText(text)
    }
}
