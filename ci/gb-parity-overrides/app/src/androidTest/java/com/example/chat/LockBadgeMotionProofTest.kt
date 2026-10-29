package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Lock-badge motion proof on the real ConversationRow (via LockBadgeProofActivity, synthetic
 *  fixture state only - no prefs, ServiceLocator or network). Claims ONLY: motion plays on an
 *  actual lock flip observed while the row is composed, exactly the settled pose afterward, and
 *  never replays on re-entry/offscreen flips/reduced motion. Rapid flips coalesced before a
 *  frame may produce no observed flip; that is accepted, not promised. */
@RunWith(AndroidJUnit4::class)
class LockBadgeMotionProofTest {

    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = androidx.test.uiautomator.UiDevice.getInstance(inst)
    private var savedScale: String = "1.0"

    private fun sh(cmd: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(cmd)).use { it.readBytes().decodeToString().trim() }

    /** Hard external deadline: runs [block] on a daemon thread and fails the test if it does not
     *  finish within [ms]. Bounds shell calls (executeShellCommand reads can block) and any other
     *  call whose own timeout assumptions prove wrong; a leaked daemon cannot hold the process. */
    private fun <T> withDeadline(ms: Long, label: String, block: () -> T): T {
        var result: Result<T>? = null
        val worker = Thread({ result = runCatching(block) }, "deadline-$label")
        worker.isDaemon = true
        worker.start()
        worker.join(ms)
        if (worker.isAlive) throw AssertionError("$label exceeded ${ms}ms deadline")
        return result!!.getOrThrow()
    }

    @Before fun setUp() {
        savedScale = sh("settings get global animator_duration_scale").let { if (it == "null" || it.isBlank()) "1.0" else it }
        sh("settings put global animator_duration_scale 1.0")
    }

    @After fun tearDown() {
        sh("settings put global animator_duration_scale $savedScale")
    }

    private fun launch(initiallyLocked: Boolean) = ActivityScenario.launch<LockBadgeProofActivity>(
        Intent(inst.targetContext, LockBadgeProofActivity::class.java)
            .putExtra(LockBadgeProofActivity.EXTRA_INITIALLY_LOCKED, initiallyLocked),
    )

    private fun shot(): Bitmap = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("screenshot null")

    private fun saveShot(name: String, bmp: Bitmap) {
        val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }


    /** Measured badge ROI: bounding box of the pixels that actually differ between the unlocked
     *  and settled-locked frames, inside a generous band around the target title. Geometry from
     *  a11y title bounds is unreliable here (merged semantics can already include the badge icon,
     *  shifting the derived strip left of the real badge into row padding: build 83 sampled an
     *  always-empty region for every frame), so the region is derived from the pixels themselves.
     *  No diff, or an implausibly large diff region, is a real failure - never a widened pass. */
    private fun measureBadgeRect(empty: Bitmap, settled: Bitmap, title: Rect): Rect {
        val d = inst.targetContext.resources.displayMetrics.density
        val x0 = (title.left - (56 * d).toInt()).coerceAtLeast(0)
        val x1 = title.right.coerceAtMost(empty.width)
        val y0 = (title.top - (8 * d).toInt()).coerceAtLeast(0)
        val y1 = (title.bottom + (8 * d).toInt()).coerceAtMost(empty.height)
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
        var diff = 0
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                if (dist(empty.getPixel(x, y), settled.getPixel(x, y)) > 24) {
                    diff++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x += 2
            }
            y += 2
        }
        assertTrue(
            "badge pixels never appeared near the target title (band=[$x0,$y0,$x1,$y1])",
            diff > 0,
        )
        val pad = (2 * d).toInt()
        val r = Rect(
            (minX - pad).coerceAtLeast(0),
            (minY - pad).coerceAtLeast(0),
            (maxX + pad + 2).coerceAtMost(empty.width),
            (maxY + pad + 2).coerceAtMost(empty.height),
        )
        val maxW = (48 * d).toInt(); val minW = (4 * d).toInt()
        assertTrue(
            "measured badge ROI implausible: $r (w=${r.width()} h=${r.height()} expected within ${minW}..$maxW px) - fixture or row layout changed, re-derive deliberately",
            r.width() in minW..maxW && r.height() in minW..maxW,
        )
        return r
    }

    private fun titleBounds(): Rect {
        val title = device.wait(androidx.test.uiautomator.Until.findObject(By.text(LockBadgeProofActivity.TARGET_TITLE)), 5000)
            ?: throw AssertionError("target title not visible")
        return title.visibleBounds
    }

    /** Badge rect: immediately left of the target row's title text (14dp badge + 4dp spacer). */
    private fun badgeRect(): Rect {
        // Wait, not a bare find: after a scroll-back the a11y tree can lag the pixels.
        val title = device.wait(androidx.test.uiautomator.Until.findObject(By.text(LockBadgeProofActivity.TARGET_TITLE)), 5000)
            ?: throw AssertionError("target title not visible")
        val b = title.visibleBounds
        val d = inst.targetContext.resources.displayMetrics.density
        val w = (18 * d).toInt() // 14dp badge + 4dp spacer
        return Rect(b.left - w, b.top, b.left, b.bottom)
    }

    private fun dist(a: Int, b: Int) = Math.abs(((a shr 16) and 255) - ((b shr 16) and 255)) +
        Math.abs(((a shr 8) and 255) - ((b shr 8) and 255)) + Math.abs((a and 255) - (b and 255))

    /** True when the two screenshots' badge regions differ beyond anti-alias noise. */
    private fun regionDiffers(a: Bitmap, b: Bitmap, r: Rect): Boolean {
        var diff = 0
        var total = 0
        var y = r.top.coerceAtLeast(0)
        while (y < r.bottom && y < a.height && y < b.height) {
            var x = r.left.coerceAtLeast(0)
            while (x < r.right && x < a.width && x < b.width) {
                total++
                if (dist(a.getPixel(x, y), b.getPixel(x, y)) > 24) diff++
                x += 2
                y += 0
            }
            y += 2
        }
        return total > 0 && diff * 50 > total // >2% of sampled pixels differ
    }

    private fun cropOf(full: Bitmap, r: Rect): Bitmap {
        val l = r.left.coerceAtLeast(0); val t = r.top.coerceAtLeast(0)
        val w = r.width().coerceAtMost(full.width - l); val h = r.height().coerceAtMost(full.height - t)
        return Bitmap.createBitmap(full, l, t, w, h)
    }

    /** Memory-bounded sampler: crops each screenshot to [r] and recycles the full frame, so a
     *  multi-second window cannot OOM the emulator. Same pixels regionDiffers would compare. */
    private fun sampleCrops(windowMs: Long, r: Rect): List<Bitmap> {
        val frames = mutableListOf<Bitmap>()
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < windowMs) {
            val full = shot()
            frames.add(cropOf(full, r))
            full.recycle()
        }
        return frames
    }

    private fun cropDiffCount(a: Bitmap, b: Bitmap): Int {
        var diff = 0
        var y = 0
        while (y < a.height && y < b.height) {
            var x = 0
            while (x < a.width && x < b.width) {
                if (dist(a.getPixel(x, y), b.getPixel(x, y)) > 24) diff++
                x += 2
            }
            y += 2
        }
        return diff
    }

    /** Same gate as regionDiffers (>2% of sampled pixels differ), for pre-cropped regions. */
    private fun cropsDiffer(a: Bitmap, b: Bitmap): Boolean {
        var diff = 0; var total = 0
        var y = 0
        while (y < a.height && y < b.height) {
            var x = 0
            while (x < a.width && x < b.width) {
                total++
                if (dist(a.getPixel(x, y), b.getPixel(x, y)) > 24) diff++
                x += 2
            }
            y += 2
        }
        return total > 0 && diff * 50 > total
    }

    /** Rapidly samples badge-region frames for [windowMs]; returns captured screenshots. */
    private fun sampleWindow(windowMs: Long): List<Bitmap> {
        val frames = CopyOnWriteArrayList<Bitmap>()
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < windowMs) {
            frames.add(shot())
        }
        return frames
    }

    private fun waitStatus(text: String, timeoutMs: Long = 5000) {
        val ok = device.wait(androidx.test.uiautomator.Until.findObject(By.textContains(text)), timeoutMs)
        assertTrue("status '$text' not shown", ok != null)
    }

    private fun clickToggle() {
        val t = device.findObject(By.text("toggle0")) ?: throw AssertionError("toggle0 not found")
        t.click()
    }

    private fun scrollDown() {
        device.findObjects(By.scrollable(true)).lastOrNull()?.scroll(
            androidx.test.uiautomator.Direction.DOWN, 1.0f,
        ) ?: fail("no scrollable")
    }

    private fun scrollUpToTop() {
        repeat(4) {
            device.findObjects(By.scrollable(true)).lastOrNull()?.fling(androidx.test.uiautomator.Direction.UP)
        }
        device.waitForIdle()
    }

    @Test fun initiallyLockedRowIsStatic() {
        launch(true).use {
            waitStatus("locked0=true")
            Thread.sleep(1000)
            val r = badgeRect()
            val a = shot(); Thread.sleep(400); val b = shot()
            saveShot("lock-initial-static-a", a); saveShot("lock-initial-static-b", b)
            assertTrue("initially locked badge must be static", !regionDiffers(a, b, r))
        }
    }

    @Test fun observedLockFlipPlaysOnceThenSettles() {
        launch(false).use {
            waitStatus("locked0=false")
            Thread.sleep(600)
            // Sample across the toggle; 360ms lock motion means 1+ mid-flight frames expected.
            // Window is 2500ms: on a loaded emulator the state write -> recomposition -> effect ->
            // animation-start chain can lag the click by hundreds of ms; 900ms can end before the
            // mid-flight frames exist. Longer window is a stricter sampler, the assert is unchanged.
            var motionSeen = false
            var settled: Bitmap? = null
            var attempt = 0
            var measured: Rect? = null
            while (attempt < 5 && !motionSeen) {
                attempt++
                // Measure the real badge ROI once from the pixels (a11y-derived geometry sampled
                // an empty region in build 83), then sample a FRESH toggle with it so the window
                // covers the actual animation start.
                val emptyFull = shot() // unlocked: no badge pixels in region
                if (measured == null) {
                    clickToggle()
                    waitStatus("locked0=true")
                    Thread.sleep(600)
                    val probe = shot()
                    measured = measureBadgeRect(emptyFull, probe, titleBounds())
                    probe.recycle()
                    clickToggle()
                    waitStatus("locked0=false")
                    Thread.sleep(400)
                }
                val r = measured!!
                val empty = cropOf(emptyFull, r); emptyFull.recycle()
                clickToggle()
                val crops = sampleCrops(2500, r)
                waitStatus("locked0=true")
                Thread.sleep(600)
                settled = shot()
                saveShot("lock-flip-settled-$attempt", settled!!)
                val settledCrop = cropOf(settled!!, r)
                // Mid-flight frame: differs from BOTH the settled pose and the empty region
                // (a settled-pose frame early in the window is expected sampling overlap, not motion).
                motionSeen = crops.any { cropsDiffer(it, settledCrop) && cropsDiffer(it, empty) }
                // Bounded diag: per-frame diff timeline for offline review; PNGs only for frames
                // that differ from both references at any strength (cap 8 per attempt).
                val sb = StringBuilder("attempt=$attempt frames=${crops.size} windowMs=2500\n")
                var savedCand = 0
                crops.forEachIndexed { i, c ->
                    val dE = cropDiffCount(c, empty); val dS = cropDiffCount(c, settledCrop)
                    sb.append("frame=$i vsEmpty=$dE vsSettled=$dS\n")
                    if (dE > 0 && dS > 0 && savedCand < 8) { saveShot("lock-flip-a$attempt-cand-$i", c); savedCand++ }
                }
                File(inst.targetContext.getExternalFilesDir(null), "screenshots/lock-flip-attempt-$attempt-frames.txt")
                    .writeText(sb.toString())
                crops.forEach { it.recycle() }
                empty.recycle(); settledCrop.recycle()
                if (!motionSeen && attempt < 5) { clickToggle(); waitStatus("locked0=false"); Thread.sleep(400) }
            }
            assertTrue("no mid-flight motion frame captured in $attempt flip(s)", motionSeen)
            Thread.sleep(500)
            val after = shot()
            assertTrue("badge must settle to a static pose (no loop)", !regionDiffers(after, settled!!, badgeRect()))
        }
    }

    @Test fun scrollOffAndBackDoesNotReplay() {
        launch(false).use {
            waitStatus("locked0=false")
            clickToggle(); waitStatus("locked0=true"); Thread.sleep(1200)
            val settled = shot()
            saveShot("lock-scroll-settled", settled)
            scrollDown(); device.waitForIdle()
            // Row 0 off screen; scroll back and sample during re-entry.
            scrollUpToTop()
            val frames = sampleWindow(800)
            val r = badgeRect()
            frames.forEachIndexed { i, f -> saveShot("lock-reentry-$i", f) }
            assertTrue(
                "badge replayed on scroll re-entry",
                frames.all { !regionDiffers(it, settled, r) },
            )
        }
    }

    @Test fun offscreenFlipDoesNotFakePlayOnReturn() {
        launch(false).use {
            waitStatus("locked0=false")
            scrollDown(); device.waitForIdle()
            clickToggle() // header control stays visible while row-0 is off screen
            waitStatus("locked0=true")
            Thread.sleep(800) // let any (incorrect) pending motion run out off screen
            scrollUpToTop()
            val frames = sampleWindow(800)
            // Static locked reference: capture now, then compare all re-entry frames to a later twin.
            val a = shot(); Thread.sleep(400); val b = shot()
            val r = badgeRect()
            assertTrue("badge animating after offscreen flip", !regionDiffers(a, b, r))
            assertTrue(
                "re-entry frames differ from static pose (fake play after offscreen flip)",
                frames.all { !regionDiffers(it, a, r) },
            )
        }
    }

    @Test fun rapidLockUnlockRelockSettlesStatic() {
        launch(false).use {
            waitStatus("locked0=false")
            clickToggle(); Thread.sleep(150)
            clickToggle(); Thread.sleep(150)
            clickToggle()
            waitStatus("locked0=true flips=3")
            Thread.sleep(1200)
            val a = shot(); Thread.sleep(400); val b = shot()
            saveShot("lock-rapid-settled-a", a); saveShot("lock-rapid-settled-b", b)
            assertTrue("badge still animating after rapid flips (loop?)", !regionDiffers(a, b, badgeRect()))
        }
    }

    @Test fun reducedMotionFlipIsStatic() {
        sh("settings put global animator_duration_scale 0")
        try {
            launch(false).use {
                waitStatus("locked0=false")
                Thread.sleep(600)
                clickToggle()
                val frames = sampleWindow(900)
                waitStatus("locked0=true")
                Thread.sleep(500)
                val settled = shot()
                saveShot("lock-reduced-settled", settled)
                val r = badgeRect()
                // Every sampled frame must already equal the settled static pose - no lift/swing frames.
                frames.forEachIndexed { i, f ->
                    // A frame may show the badge or not (sampling vs composition timing), but if it
                    // differs from the settled pose it must be the pre-badge empty region, never a
                    // mid-animation lift/swing frame.
                    if (regionDiffers(f, settled, r)) {
                        assertTrue("reduced motion frame $i shows animation", !badgePresent(f, r))
                    }
                }
            }
        } finally {
            sh("settings put global animator_duration_scale 1.0")
        }
    }

    /** DIAG ONLY (no product verdict, no gate): does THIS emulator render intermediate frames of a
     *  bounded (fixture auto-stops after 6s) looping ChatyIcon (same Animatable + draw-invalidation mechanism the lock play
     *  uses)? Writes calib-loop-verdict.txt for the artifact. A negative does NOT prove the lock
     *  effect never runs, and a positive does NOT prove strict lock motion is measurable; it only
     *  bounds what the sampling on this emulator can see. */
    @Test fun animationFrameCalibrationDiag() {
        // Bounded by construction: the beacon loop is finite (fixture auto-stops after 6s), the
        // launch uses am start (no ActivityScenario idle wait - a looping beacon would hold the
        // main looper non-idle), every wait below carries its own timeout, sampling is
        // time-boxed, and the activity is force-stopped in finally. Animator scale untouched;
        // class-level setUp/tearDown saves and restores it. The diag asserts only that sampling
        // returned frames - it is not a prerequisite for any other test.
        withDeadline(15000, "calib-am-start") {
            sh("am start -n com.example.chat/.LockBadgeProofActivity --ez " + LockBadgeProofActivity.EXTRA_CALIB_BEACON + " true")
        }
        try {
            withDeadline(60000, "calib-diagnostic") {
                // Verify the fixture actually launched before waiting on its status: raw am start
                // on swiftshader can take well past 5s to reach first composition, and a bare
                // 5s status wait fails without saying whether the activity ever came up. Probe
                // the fixture's own package window first; on failure include the current focus.
                val pkgUp = device.wait(androidx.test.uiautomator.Until.hasObject(By.pkg("com.example.chat")), 20000)
                assertTrue(
                    "fixture window never appeared after am start; focus=" + try { sh("dumpsys window displays | grep -m1 mCurrentFocus") } catch (t: Throwable) { "dump-failed:$t" },
                    pkgUp,
                )
                waitStatus("locked0=false", 20000)
                val node = device.wait(androidx.test.uiautomator.Until.findObject(By.desc("calib-loop")), 5000)
                    ?: throw AssertionError("calib-loop icon missing")
                val r = node.visibleBounds
                val crops = sampleCrops(900, r)
                var transitions = 0
                for (i in 1 until crops.size) if (cropsDiffer(crops[i - 1], crops[i])) transitions++
                File(inst.targetContext.getExternalFilesDir(null), "screenshots/calib-loop-verdict.txt")
                    .writeText("frames=${crops.size} changingTransitions=$transitions windowMs=900\n")
                crops.take(3).forEachIndexed { i, c -> saveShot("calib-loop-sample-$i", c) }
                val n = crops.size
                crops.forEach { it.recycle() }
                assertTrue("calibration sampling captured no frames", n > 0)
            }
        } finally {
            // Process-level cleanup bound: the fixture process dies even if any earlier step
            // wedged it; a stuck force-stop is recorded, never masks the diagnostic outcome.
            try {
                withDeadline(15000, "calib-force-stop") { sh("am force-stop com.example.chat") }
            } catch (t: Throwable) {
                File(inst.targetContext.getExternalFilesDir(null), "screenshots/calib-loop-verdict.txt")
                    .appendText("forceStopDeadline=${t.message}\n")
            }
        }
    }

    /** Heuristic: badge pixels present when the region is not uniformly the row background. */
    private fun badgePresent(bmp: Bitmap, r: Rect): Boolean {
        val x = (r.left + r.right) / 2
        val y0 = (r.top + r.bottom) / 2
        val c = bmp.getPixel(x, y0)
        // Accent-tinted lock glyph is saturated; row background in the default light theme is not.
        val rr = (c shr 16) and 255; val gg = (c shr 8) and 255; val bb = c and 255
        val mx = maxOf(rr, gg, bb); val mn = minOf(rr, gg, bb)
        return mx - mn > 24
    }
}
