package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.AppColors
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OFF-state ChatySwitch in the debug-only HeaderProofActivity, light and dark. Expected colors come from the
 * theme tokens (thumb, 22% track tint, 70% outline), the screenshot confirms those are what was painted, and the
 * token pairs themselves must meet 3:1 (thumb vs track) and 2.5:1 (outline vs card). One tap, one callback.
 */
@RunWith(AndroidJUnit4::class)
class SwitchOffContrastProofTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch((c shr 16) and 255) + 0.7152 * ch((c shr 8) and 255) + 0.0722 * ch(c and 255)
    }
    private fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }
    private fun dist(a: Int, b: Int) = abs(((a shr 16) and 255) - ((b shr 16) and 255)) + abs(((a shr 8) and 255) - ((b shr 8) and 255)) + abs((a and 255) - (b and 255))

    private fun run(dark: Boolean) {
        val tag = if (dark) "dark" else "light"
        val theme = ThemePresets.getSystemDefaultTheme(platformIsLight = !dark)
        val c = AppColors(theme)
        val card = Color(theme.surfaceColor)
        val thumbExp = c.foregroundSecondary.compositeOver(card).toArgb()
        val trackExp = c.foregroundSecondary.copy(alpha = 0.22f).compositeOver(card)
        val outlineExp = c.foregroundSecondary.copy(alpha = 0.7f).compositeOver(card).toArgb()
        assertTrue("token thumb vs track ($tag) ${ratio(thumbExp, trackExp.toArgb())}", ratio(thumbExp, trackExp.toArgb()) >= 3.0)
        assertTrue("token outline vs card ($tag) ${ratio(outlineExp, card.toArgb())}", ratio(outlineExp, card.toArgb()) >= 2.5)
        val scenario = ActivityScenario.launch<HeaderProofActivity>(
            Intent(inst.targetContext, HeaderProofActivity::class.java)
                .putExtra(HeaderProofActivity.EXTRA_DARK, dark).putExtra(HeaderProofActivity.EXTRA_SCREEN, HeaderProofActivity.SCREEN_SWITCH)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            assertTrue(device.wait(Until.hasObject(By.desc("synthetic-switch")), 10000))
            val b = device.findObject(By.desc("synthetic-switch")).visibleBounds
            val shot = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No pixels")
            val f = File(inst.targetContext.getExternalFilesDir(null), "screenshots/switch-off-$tag.png")
            f.parentFile?.mkdirs()
            FileOutputStream(f).use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val d = inst.targetContext.resources.displayMetrics.density
            val trackSeen = shot.getPixel(b.right - (9 * d).toInt(), b.centerY())               // OFF thumb sits left
            val thumbSeen = shot.getPixel(b.left + (14 * d).toInt(), b.centerY() - (6 * d).toInt()) // 22dp thumb, above its dash
            val edgeSeen = shot.getPixel(b.centerX(), b.top + (0.6f * d).toInt())
            shot.recycle()
            assertTrue("track painted as token ($tag): ${Integer.toHexString(trackSeen)}", dist(trackSeen, trackExp.toArgb()) <= 30)
            assertTrue("thumb painted as token ($tag): ${Integer.toHexString(thumbSeen)}", dist(thumbSeen, thumbExp) <= 30)
            assertTrue("outline painted as token ($tag): ${Integer.toHexString(edgeSeen)}", dist(edgeSeen, outlineExp) <= 60)
            device.findObject(By.desc("synthetic-switch")).click()
            assertTrue("one callback per tap", device.wait(Until.hasObject(By.text("Taps: 1")), 5000))
        } finally { scenario.close() }
    }

    @Test fun lightOff() = run(false)
    @Test fun darkOff() = run(true)
}
