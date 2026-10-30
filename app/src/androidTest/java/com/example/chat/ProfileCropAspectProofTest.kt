package com.example.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.profile.ProfileImageCropperDialog
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression proof for the user-reported crop bugs (2026-10-30 device
 * screenshot): a portrait source was squashed into the 16:9 banner frame
 * (scale-to-fill instead of aspect-preserving cover) and the Cancel /
 * Crop & Use row rendered under the system navigation area.
 *
 * Locks both fixes with real pixels:
 *  1. Preview: a 1080x1920 portrait source with a red top band, a blue
 *     bottom band and a magenta circle must render COVER-fit - the red and
 *     blue bands are outside the visible window and the circle stays round.
 *  2. Safe area: the accent Crop & Use button must end above the screen
 *     bottom edge (systemBarsPadding), not clipped by the gesture/nav bar.
 *  3. Export: the cropped PNG must be the 2.5x capture of the crop window,
 *     free of the red/blue bands, with the circle still round (no stretch).
 */
@RunWith(AndroidJUnit4::class)
class ProfileCropAspectProofTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val dev get() = UiDevice.getInstance(ins)
    private val density get() = ins.targetContext.resources.displayMetrics.density

    private fun shot(name: String) {
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        dev.waitForIdle(3000)
        val bmp = ins.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun nearColor(pixel: Int, r: Int, g: Int, b: Int): Boolean =
        Math.abs(AndroidColor.red(pixel) - r) <= 28 &&
            Math.abs(AndroidColor.green(pixel) - g) <= 28 &&
            Math.abs(AndroidColor.blue(pixel) - b) <= 28

    private fun isRed(p: Int) = nearColor(p, 230, 30, 30)
    private fun isBlue(p: Int) = nearColor(p, 30, 30, 230)
    private fun isMagenta(p: Int) = nearColor(p, 220, 30, 200)
    private fun isAccent(p: Int) = nearColor(p, 0x25, 0xD3, 0x66)

    /** Portrait source whose aspect violations are pixel-detectable. */
    private fun writePortraitSource(): File {
        val src = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        canvas.drawColor(AndroidColor.rgb(90, 90, 90))
        val red = Paint().apply { color = AndroidColor.rgb(230, 30, 30) }
        val blue = Paint().apply { color = AndroidColor.rgb(30, 30, 230) }
        val magenta = Paint().apply { color = AndroidColor.rgb(220, 30, 200) }
        canvas.drawRect(0f, 0f, 1080f, 192f, red) // top 10%
        canvas.drawRect(0f, 1728f, 1080f, 1920f, blue) // bottom 10%
        canvas.drawCircle(540f, 960f, 200f, magenta)
        val dir = File(ins.targetContext.cacheDir, "profile-aspect-proof")
        dir.mkdirs()
        val f = File(dir, "portrait-source.png")
        FileOutputStream(f).use { src.compress(Bitmap.CompressFormat.PNG, 100, it) }
        src.recycle()
        val check = BitmapFactory.decodeFile(f.absolutePath)
        assertNotNull("synthetic source must decode", check)
        assertEquals(1080, check!!.width)
        assertEquals(1920, check.height)
        check.recycle()
        return f
    }

    private fun waitFor(cond: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (!cond() && SystemClock.uptimeMillis() < deadline) Thread.sleep(150)
    }

    /** Expected crop window in px, mirroring the dialog's sizing math. */
    private fun cropWindowPx(isAvatar: Boolean): Pair<Float, Float> {
        val dm = ins.targetContext.resources.displayMetrics
        val boxW = (dm.widthPixels / dm.density - 48f) * dm.density
        val boxH = if (isAvatar) boxW else boxW * 9f / 16f
        return Pair(boxW, boxH)
    }

    private class ScreenScan {
        var redCount = 0
        var blueCount = 0
        var magentaMinX = Int.MAX_VALUE
        var magentaMaxX = -1
        var magentaMinY = Int.MAX_VALUE
        var magentaMaxY = -1
    }

    private fun scan(bmp: Bitmap): ScreenScan {
        val s = ScreenScan()
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val p = bmp.getPixel(x, y)
                if (isRed(p)) s.redCount++
                if (isBlue(p)) s.blueCount++
                if (isMagenta(p)) {
                    if (x < s.magentaMinX) s.magentaMinX = x
                    if (x > s.magentaMaxX) s.magentaMaxX = x
                    if (y < s.magentaMinY) s.magentaMinY = y
                    if (y > s.magentaMaxY) s.magentaMaxY = y
                }
                x += 3
            }
            y += 3
        }
        return s
    }

    /** Lowest/highest accent rows inside the bottom quarter of the screen. */
    private fun accentButtonRows(bmp: Bitmap): Pair<Int, Int>? {
        val startY = bmp.height * 3 / 4
        var minY = Int.MAX_VALUE
        var maxY = -1
        var y = startY
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                if (isAccent(bmp.getPixel(x, y))) {
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    break
                }
                x += 2
            }
            y += 1
        }
        return if (maxY < 0) null else Pair(minY, maxY)
    }

    private fun launchDialog(isAvatar: Boolean, result: AtomicReference<File?>, error: AtomicReference<String?>): ActivityScenario<ChatFixtureActivity> {
        val source = writePortraitSource()
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                ChatyTheme(ThemePresets.whatsappIosLight) {
                    ProfileImageCropperDialog(
                        sourceFile = source,
                        isAvatar = isAvatar,
                        title = if (isAvatar) "Crop Avatar Photo" else "Crop Profile Banner",
                        onDone = { result.set(it) },
                        onError = { error.set(it) },
                    )
                }
            }
        }
        return scenario
    }

    private fun assertCoverFitPreview(tag: String, isAvatar: Boolean) {
        val (boxW, boxH) = cropWindowPx(isAvatar)
        val bmp = ins.uiAutomation.takeScreenshot() ?: fail("screenshot unavailable")
        val s = scan(bmp)
        // Squash (scale-to-fill) would paint the red top band and the blue
        // bottom band inside the frame; cover-fit keeps them off screen.
        assertTrue("$tag: red top band must not be visible (squash regression), saw ${s.redCount}px", s.redCount < 40)
        assertTrue("$tag: blue bottom band must not be visible (squash regression), saw ${s.blueCount}px", s.blueCount < 40)
        // The magenta circle must render and stay round: cover scale is
        // boxW/1080 on both axes, so width extent == height extent.
        assertTrue("$tag: circle must be visible", s.magentaMaxX > s.magentaMinX && s.magentaMaxY > s.magentaMinY)
        val w = (s.magentaMaxX - s.magentaMinX).toFloat()
        val h = (s.magentaMaxY - s.magentaMinY).toFloat()
        val expected = 400f * boxW / 1080f
        assertEquals("$tag: circle horizontal extent", expected, w, expected * 0.15f)
        assertEquals("$tag: circle vertical extent", expected, h, expected * 0.15f)
        assertTrue("$tag: circle must stay round, w=$w h=$h", Math.abs(w - h) / Math.max(w, h) < 0.08f)
        bmp.recycle()
    }

    private fun assertExportedCrop(tag: String, file: File, isAvatar: Boolean) {
        val (boxW, boxH) = cropWindowPx(isAvatar)
        val out = BitmapFactory.decodeFile(file.absolutePath) ?: fail("exported crop must decode")
        assertEquals("$tag: export width is the 2.5x capture", (boxW * 2.5f).toInt(), out.width, 2)
        assertEquals("$tag: export height is the 2.5x capture", (boxH * 2.5f).toInt(), out.height, 2)
        val s = scan(out)
        assertTrue("$tag: export must not contain the red band", s.redCount < 40)
        assertTrue("$tag: export must not contain the blue band", s.blueCount < 40)
        assertTrue("$tag: export must contain the circle", s.magentaMaxX > s.magentaMinX)
        val w = (s.magentaMaxX - s.magentaMinX).toFloat()
        val h = (s.magentaMaxY - s.magentaMinY).toFloat()
        assertTrue("$tag: exported circle must stay round, w=$w h=$h", Math.abs(w - h) / Math.max(w, h) < 0.08f)
        out.recycle()
    }

    @Test
    fun bannerPortraitSourceCoversWithoutSquashAndButtonsStayOnScreen() {
        val result = AtomicReference<File?>(null)
        val error = AtomicReference<String?>(null)
        val scenario = launchDialog(isAvatar = false, result = result, error = error)
        dev.wait(Until.findObject(By.text("Crop Profile Banner")), 8000)
            ?: fail("banner crop dialog did not render")
        waitFor { dev.hasObject(By.text("Crop & Use")) && dev.hasObject(By.text("Cancel")) }

        // Safe area: the accent button must be fully on screen with its full
        // 50dp height and a gap below it (never under the nav/gesture bar).
        val bmp = ins.uiAutomation.takeScreenshot() ?: fail("screenshot unavailable")
        val rows = accentButtonRows(bmp) ?: fail("Crop & Use accent button not found in bottom quarter")
        val minButtonHeight = 45f * density
        assertTrue(
            "Crop & Use must keep its full height, saw ${rows.second - rows.first}px (min $minButtonHeight)",
            rows.second - rows.first >= minButtonHeight,
        )
        assertTrue(
            "Crop & Use must sit above the navigation area, lowest accent row ${rows.second} of ${bmp.height}",
            rows.second < bmp.height - 2,
        )
        bmp.recycle()

        assertCoverFitPreview("banner", isAvatar = false)
        shot("profile-crop-banner-coverfit")

        dev.findObject(By.text("Crop & Use")).click()
        waitFor { result.get() != null || error.get() != null }
        assertNull("banner crop failed: ${error.get()}", error.get())
        val exported = result.get() ?: fail("Crop & Use produced no file")
        assertExportedCrop("banner", exported, isAvatar = false)
        scenario.close()
    }

    @Test
    fun avatarPortraitSourceCoversWithoutSquash() {
        val result = AtomicReference<File?>(null)
        val error = AtomicReference<String?>(null)
        val scenario = launchDialog(isAvatar = true, result = result, error = error)
        dev.wait(Until.findObject(By.text("Crop Avatar Photo")), 8000)
            ?: fail("avatar crop dialog did not render")
        waitFor { dev.hasObject(By.text("Crop & Use")) }

        assertCoverFitPreview("avatar", isAvatar = true)
        shot("profile-crop-avatar-coverfit")

        dev.findObject(By.text("Crop & Use")).click()
        waitFor { result.get() != null || error.get() != null }
        assertNull("avatar crop failed: ${error.get()}", error.get())
        val exported = result.get() ?: fail("Crop & Use produced no file")
        assertExportedCrop("avatar", exported, isAvatar = true)
        scenario.close()
    }
}
