package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * All 59 bubble styles, me and them, light and dark, drawn by the real ChatyBubbleContainer in the debug-only
 * BubbleSheetProofActivity. Pass 1 (silhouettes, no text) asserts that every pair of styles differs on at least
 * MIN_DIFF_FRACTION of the cell pixels, per side and theme. Pass 2 saves contact sheets WITH text as
 * bubble-sheet-<light|dark>-p<N>.png. The threshold only proves styles are not duplicates: readability, fill,
 * tails, accents and clipping are judged by looking at the saved sheets, not by this test.
 */
@RunWith(AndroidJUnit4::class)
class BubbleStyleContactSheetProofTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)

    private fun launch(dark: Boolean, page: Int, text: Boolean): ActivityScenario<BubbleSheetProofActivity> =
        ActivityScenario.launch<BubbleSheetProofActivity>(
            Intent(inst.targetContext, BubbleSheetProofActivity::class.java)
                .putExtra(BubbleSheetProofActivity.EXTRA_DARK, dark)
                .putExtra(BubbleSheetProofActivity.EXTRA_PAGE, page)
                .putExtra(BubbleSheetProofActivity.EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ).also { assertTrue(device.wait(Until.hasObject(By.descStartsWith("bubble-")), 10000)) }

    private fun shot(name: String?): Bitmap {
        val b = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No pixels for $name")
        if (name != null) {
            val f = File(inst.targetContext.getExternalFilesDir(null), "screenshots/$name.png")
            f.parentFile?.mkdirs()
            FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return b
    }

    private fun cells(dark: Boolean, page: Int): Map<String, IntArray> {
        val scenario = launch(dark, page, text = false)
        try {
            val s = shot(null)
            return device.findObjects(By.descStartsWith("bubble-")).associate { o ->
                val r = o.visibleBounds
                val bmp = Bitmap.createBitmap(s, r.left, r.top, r.width(), r.height())
                val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                o.contentDescription to px
            }
        } finally { scenario.close() }
    }

    private fun diffFraction(a: IntArray, b: IntArray): Double {
        if (a.size != b.size) return 1.0
        var n = 0
        for (i in a.indices) {
            val x = a[i]; val y = b[i]
            if (abs(((x shr 16) and 255) - ((y shr 16) and 255)) + abs(((x shr 8) and 255) - ((y shr 8) and 255)) + abs((x and 255) - (y and 255)) > 48) n++
        }
        return n.toDouble() / a.size
    }

    private fun run(dark: Boolean) {
        val tag = if (dark) "dark" else "light"
        val all = LinkedHashMap<String, IntArray>()
        for (p in 0 until BubbleSheetProofActivity.pageCount()) all.putAll(cells(dark, p))
        assertTrue("Expected 118 cells (59 styles x me/them), got ${all.size}", all.size == 118)
        val failures = mutableListOf<String>()
        var worst = 1.0
        for (side in listOf("me", "them")) {
            val keys = all.keys.filter { it.endsWith("-$side") }
            for (i in keys.indices) for (j in i + 1 until keys.size) {
                val f = diffFraction(all[keys[i]]!!, all[keys[j]]!!)
                worst = minOf(worst, f)
                if (f < MIN_DIFF_FRACTION) failures += "${keys[i]} vs ${keys[j]} differ on only ${"%.3f".format(f)} of pixels"
            }
        }
        for (p in 0 until BubbleSheetProofActivity.pageCount()) {
            val s = launch(dark, p, text = true)
            try { shot("bubble-sheet-$tag-p$p") } finally { s.close() }
        }
        assertTrue("Near-duplicate bubble styles ($tag, least different pair $worst): ${failures.joinToString("; ")}", failures.isEmpty())
    }

    @Test fun allBubbleStylesAreDistinctLight() = run(false)
    @Test fun allBubbleStylesAreDistinctDark() = run(true)

    companion object { const val MIN_DIFF_FRACTION = 0.004 }
}
