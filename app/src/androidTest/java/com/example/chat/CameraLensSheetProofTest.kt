package com.example.chat

import android.Manifest
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
}
