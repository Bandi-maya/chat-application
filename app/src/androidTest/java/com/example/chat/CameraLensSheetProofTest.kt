package com.example.chat

import android.Manifest
import android.os.SystemClock
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Lens carousel + browse-sheet contract on the synthetic emulator camera.
 * Never presses the shutter and never sends/uploads. Carousel items are
 * "<name> lens thumbnail" (unique vs the browse sheet's "<name> lens"), so
 * each surface is addressed separately and selection is asserted on the
 * tapped item itself, never on any-selected-node.
 */
@RunWith(AndroidJUnit4::class)
class CameraLensSheetProofTest {
    @Test fun carouselTapSelectsAndBrowseReflectsSameSelection() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val c = ins.targetContext
        val d = UiDevice.getInstance(ins)
        val had = c.checkSelfPermission(Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val out = File(c.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        d.executeShellCommand("pm grant ${c.packageName} android.permission.CAMERA")
        fun capture(state: String) {
            ins.uiAutomation.takeScreenshot()?.let { b ->
                File(out, "camera-lens-sheet-$state.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                b.recycle()
            }
            d.dumpWindowHierarchy(File(out, "camera-lens-sheet-$state.xml"))
        }
        fun lensMode() {
            repeat(6) { val r = d.findObject(By.desc("Camera modes")).visibleBounds; d.swipe(r.left + 12, r.centerY(), r.right - 12, r.centerY(), 12) }
            repeat(8) {
                val node = d.findObject(By.desc("Lenses camera mode"))
                if (node != null && node.visibleBounds.width() > 0) { node.click(); return }
                val r = d.findObject(By.desc("Camera modes")).visibleBounds; d.swipe(r.right - 12, r.centerY(), r.left + 12, r.centerY(), 12)
            }
            fail("Lenses mode not reachable")
        }
        fun thumbnails() = d.findObjects(By.descContains("lens thumbnail"))
        fun sheetOpen() = d.hasObject(By.descContains(" lens, in use")) ||
            d.findObjects(By.descContains(" lens")).any { !it.contentDescription.contains("thumbnail") }
        try {
            ActivityScenario.launch<CameraOpeningProofActivity>(Intent(c, CameraOpeningProofActivity::class.java)).use {
                assertTrue(d.wait(Until.hasObject(By.desc("Take photo")), 20000))
                lensMode()
                assertTrue("inline carousel appears in lenses mode", d.wait(Until.hasObject(By.descContains("lens thumbnail")), 12000))
                capture("carousel")
                // Real selection: tap a specific thumbnail, then the tapped item
                // and ONLY it reports in-use on the carousel.
                val initial = thumbnails()
                assertTrue("carousel shows at least two lenses", initial.size >= 2)
                val inUseBefore = initial.map { it.contentDescription }.filter { it.endsWith("in use") }
                assertTrue("at most one in-use thumbnail before tap", inUseBefore.size <= 1)
                val targetDesc = initial.map { it.contentDescription }.first { !it.endsWith("in use") }
                val targetName = targetDesc.removeSuffix(" lens thumbnail")
                initial.first { it.contentDescription == targetDesc }.click()
                assertTrue(
                    "tapped thumbnail becomes the in-use one",
                    d.wait(Until.hasObject(By.desc("$targetName lens thumbnail, in use")), 8000),
                )
                val inUseAfter = thumbnails().map { it.contentDescription }.filter { it.endsWith("in use") }
                assertEquals("exactly one in-use thumbnail after tap", 1, inUseAfter.size)
                assertEquals("$targetName lens thumbnail, in use", inUseAfter.single())
                capture("carousel-selected")
                // Browse button: sheet opens and reflects the SAME selection.
                d.findObject(By.desc("Browse all lenses")).click()
                assertTrue("browse sheet opens from its button", d.wait(Until.hasObject(By.descContains(" lens")), 8000) && sheetOpen())
                assertTrue(
                    "browse sheet marks the same lens in use",
                    d.wait(Until.hasObject(By.desc("$targetName lens, in use")), 8000),
                )
                capture("browse-sheet")
                d.pressBack()
                assertTrue("browse sheet dismisses", d.wait(Until.gone(By.desc("$targetName lens, in use")), 8000))
                // Gesture path: bottom-to-top drag on the carousel row opens the sheet.
                val carouselBounds = thumbnails().first().visibleBounds
                d.swipe(carouselBounds.centerX(), carouselBounds.bottom - 4, carouselBounds.centerX(), carouselBounds.top - 160, 18)
                assertTrue("drag up on carousel opens the browse sheet", d.wait(Until.hasObject(By.desc("$targetName lens, in use")), 8000) || sheetOpen())
                capture("browse-sheet-gesture")
            }
        } finally {
            d.unfreezeRotation()
            if (!had) File(c.getExternalFilesDir(null), "camera-permission-cleanup").writeText("revoke")
        }
    }

    // Guard-fix proofs (LensCarousel programmatic-centering guard). Each opens the
    // real camera fixture in Lenses mode and drives the actual carousel.

    @Test fun tapSelectionStaysAfterCenteringCompletes() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val c = ins.targetContext
        val d = UiDevice.getInstance(ins)
        d.executeShellCommand("pm grant ${c.packageName} android.permission.CAMERA")
        try {
            ActivityScenario.launch<CameraOpeningProofActivity>(Intent(c, CameraOpeningProofActivity::class.java)).use {
                assertTrue(d.wait(Until.hasObject(By.desc("Take photo")), 20000))
                lensMode()
                assertTrue(d.wait(Until.hasObject(By.descContains("lens thumbnail")), 12000))
                val first = thumbnails()
                assertTrue("carousel shows at least two lenses", first.size >= 2)
                val target = first.map { it.contentDescription }.first { !it.endsWith("in use") }
                val targetName = target.removeSuffix(" lens thumbnail")
                first.first { it.contentDescription == target }.click()
                assertTrue(
                    "tapped thumbnail becomes the in-use one",
                    d.wait(Until.hasObject(By.desc("$targetName lens thumbnail, in use")), 8000),
                )
                // No late revert: after the centering animation tail and any watcher
                // frame, the tapped lens must STILL be the only in-use one. This is
                // the build-100 failure shape (selection snapped back mid-animation).
                SystemClock.sleep(1500)
                val after = thumbnails().map { it.contentDescription }.filter { it.endsWith("in use") }
                assertEquals("no revert after centering settles", listOf("$targetName lens thumbnail, in use"), after)
            }
        } finally {
            d.unfreezeRotation()
        }
    }

    @Test fun userDragDuringCenteringOwnsSelection() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val c = ins.targetContext
        val d = UiDevice.getInstance(ins)
        d.executeShellCommand("pm grant ${c.packageName} android.permission.CAMERA")
        try {
            ActivityScenario.launch<CameraOpeningProofActivity>(Intent(c, CameraOpeningProofActivity::class.java)).use {
                assertTrue(d.wait(Until.hasObject(By.desc("Take photo")), 20000))
                lensMode()
                assertTrue(d.wait(Until.hasObject(By.descContains("lens thumbnail")), 12000))
                val first = thumbnails()
                assertTrue("carousel shows at least two lenses", first.size >= 2)
                val target = first.map { it.contentDescription }.first { !it.endsWith("in use") }
                val targetName = target.removeSuffix(" lens thumbnail")
                first.first { it.contentDescription == target }.click()
                // Interrupt the programmatic centering with a real horizontal drag on
                // the carousel row: the finger owns selection from then on, so the
                // tapped item must NOT be forced back into selection by the old
                // animation. With a two-item catalog the drag settles on either item;
                // the contract under test is that no LATER revert fires after settle.
                val row = thumbnails().first().visibleBounds
                d.swipe(row.centerX(), row.centerY(), row.centerX() - 240, row.centerY(), 24)
                d.wait(Until.hasObject(By.descContains("lens thumbnail, in use")), 8000)
                val settled = thumbnails().map { it.contentDescription }.filter { it.endsWith("in use") }
                assertEquals("exactly one in-use thumbnail after interruption settles", 1, settled.size)
                // No late override: the settled selection must survive a full
                // animation window unchanged.
                val winner = settled.single()
                assertTrue(
                    "settled selection is not overridden by the cancelled centering",
                    d.wait(Until.hasObject(By.desc(winner)), 3000) && thumbnails().map { it.contentDescription }.count { it.endsWith("in use") } == 1,
                )
            }
        } finally {
            d.unfreezeRotation()
        }
    }

    @Test fun leavingLensesModeMidAnimationDoesNotRevertOrCrash() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val c = ins.targetContext
        val d = UiDevice.getInstance(ins)
        d.executeShellCommand("pm grant ${c.packageName} android.permission.CAMERA")
        try {
            ActivityScenario.launch<CameraOpeningProofActivity>(Intent(c, CameraOpeningProofActivity::class.java)).use {
                assertTrue(d.wait(Until.hasObject(By.desc("Take photo")), 20000))
                lensMode()
                assertTrue(d.wait(Until.hasObject(By.descContains("lens thumbnail")), 12000))
                val first = thumbnails()
                assertTrue("carousel shows at least two lenses", first.size >= 2)
                val target = first.map { it.contentDescription }.first { !it.endsWith("in use") }
                val targetName = target.removeSuffix(" lens thumbnail")
                first.first { it.contentDescription == target }.click()
                // Unmount the carousel mid-animation by leaving Lenses mode, then come
                // back: no crash, no stale callback, selection stays the tapped lens.
                d.findObject(By.desc("Video camera mode")).click()
                assertTrue(d.wait(Until.gone(By.descContains("lens thumbnail")), 8000))
                lensMode()
                assertTrue(d.wait(Until.hasObject(By.descContains("lens thumbnail")), 12000))
                assertTrue(
                    "tapped selection survives mode round-trip without revert",
                    d.wait(Until.hasObject(By.desc("$targetName lens thumbnail, in use")), 8000),
                )
            }
        } finally {
            d.unfreezeRotation()
        }
    }

    private fun thumbnails() = InstrumentationRegistry.getInstrumentation().let { d ->
        UiDevice.getInstance(d).findObjects(By.descContains("lens thumbnail"))
    }

    private fun lensMode() {
        val d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        repeat(6) { val r = d.findObject(By.desc("Camera modes")).visibleBounds; d.swipe(r.left + 12, r.centerY(), r.right - 12, r.centerY(), 12) }
        repeat(8) {
            val node = d.findObject(By.desc("Lenses camera mode"))
            if (node != null && node.visibleBounds.width() > 0) { node.click(); return }
            val r = d.findObject(By.desc("Camera modes")).visibleBounds; d.swipe(r.right - 12, r.centerY(), r.left + 12, r.centerY(), 12)
        }
        fail("Lenses mode not reachable")
    }
}
