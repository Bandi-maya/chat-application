package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual pointer dispatch, actual composer/session/controller, synthetic audio only. */
@RunWith(AndroidJUnit4::class)
class VoiceComposerGestureProofTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(ins)

    // TalkBack-path activation through the accessibility tree itself. UiObject2
    // has no performAction (verified against the current API reference; the
    // 798a91bd commit message's "uiautomator 2.3.0 performAction" was unverified
    // and broke compileDebugAndroidTestKotlin in run 129). UiAutomation delivers
    // the published ACTION_CLICK exactly as an accessibility service would.
    private fun a11yActivateByDesc(descPrefix: String): Boolean {
        val root = ins.uiAutomation.rootInActiveWindow ?: return false
        fun find(n: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (n == null) return null
            val d = n.contentDescription
            if (d != null && d.toString().startsWith(descPrefix) && n.isVisibleToUser &&
                n.actionList.any { it.id == android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id }
            ) return n
            for (i in 0 until n.childCount) { val r = find(n.getChild(i)); if (r != null) return r }
            return null
        }
        val node = find(root) ?: return false
        return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
    }
    private fun status(text: String) = assertTrue("Missing state: $text", device.wait(Until.hasObject(By.textContains(text)), 5000))
    private fun pointer(action: Int, x: Float, y: Float, down: Long) {
        val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        try { ins.sendPointerSync(e) } finally { e.recycle() }
    }
    private fun capture(name: String) {
        val out = File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        device.dumpWindowHierarchy(File(out, "voice-gesture-$name.xml"))
        ins.uiAutomation.takeScreenshot()?.let { b ->
            File(out, "voice-gesture-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
        }
    }
    private fun runStyle(style2: Boolean, block: () -> Unit) {
        val c = ins.targetContext
        ActivityScenario.launch<VoiceGestureFixtureActivity>(Intent(c, VoiceGestureFixtureActivity::class.java).putExtra("style2", style2)).use {
            assertTrue(device.wait(Until.hasObject(By.descStartsWith("Microphone mode.")), 10000)); block()
        }
    }
    @Test fun micTapSwitchesModesWithoutRecordingInBothStyles() {
        for (style2 in listOf(false, true)) runStyle(style2) {
            val micNode = device.findObject(By.descStartsWith("Microphone mode."))
            runCatching {
                ins.uiAutomation.takeScreenshot()?.let { b ->
                    File(File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() },
                        "voice-tap-toggle-pre-tap-$style2.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
                }
                device.dumpWindowHierarchy(File(File(ins.targetContext.getExternalFilesDir(null), "screenshots"),
                    "voice-tap-toggle-pre-tap-$style2.xml"))
            }
            micNode.click()
            // Evidence-only: timed pixel+a11y series discriminates render lag
            // (pixels change late) from a11y reflection lag (pixels change early,
            // desc lands late). Assertion and 12s window unchanged below.
            val voiceT0 = SystemClock.uptimeMillis()
            for (i in 0..7) {
                try {
                    Thread.sleep(if (i == 0) 250 else 500)
                    val landed = device.hasObject(By.descStartsWith("Video mode."))
                    ins.uiAutomation.takeScreenshot()?.let { b ->
                        File(File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() },
                            "voice-timing-$style2-" + i + ".png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
                    }
                    android.util.Log.i("VOICE-TIMING", "style=$style2 i=" + i + " t=" + (SystemClock.uptimeMillis() - voiceT0) + "ms a11yLanded=" + landed)
                    if (landed) break
                } catch (e: Exception) { android.util.Log.w("VOICE-TIMING", "capture i=" + i + " failed: " + e.message) }
            }
            val switched = device.wait(Until.hasObject(By.descStartsWith("Video mode.")), 12000) // SwiftShader a11y reflection lag: 91 proved the toggle fires (diag) with the label landing past 5s
            // DIAGNOSTIC ONLY: post-tap state capture for offline review of the tap-toggle.
            runCatching {
                device.waitForIdle(2000)
                ins.uiAutomation.takeScreenshot()?.let { b ->
                    File(File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() },
                        "voice-tap-toggle-post-tap-$style2.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
                }
                device.dumpWindowHierarchy(File(File(ins.targetContext.getExternalFilesDir(null), "screenshots"),
                    "voice-tap-toggle-post-tap-$style2.xml"))
            }
            assertTrue(switched)
            status("recording=false;locked=false;busy=false;uploads=0;sends=0;deletes=0")
            device.findObject(By.descStartsWith("Video mode.")).click()
            assertTrue(device.wait(Until.hasObject(By.descStartsWith("Microphone mode.")), 12000))
            status("recording=false;locked=false;busy=false;uploads=0;sends=0;deletes=0")
            capture("tap-toggle-$style2")
        }
    }
    @Test fun micA11yActionClickTogglesModeWithoutRecordingInBothStyles() {
        for (style2 in listOf(false, true)) runStyle(style2) {
            // TalkBack/keyboard path: activate the published ACTION_CLICK instead
            // of injecting coordinates (run-127 proof the mic node published no
            // click action). The a11y action must invoke the mode toggle ONLY;
            // hold/record is gesture-only, so uploads/sends/deletes stay 0.
            val mic = device.wait(Until.findObject(By.descStartsWith("Microphone mode.")), 10000)
                ?: throw AssertionError("mic node missing")
            assertTrue("mic node must publish ACTION_CLICK", a11yActivateByDesc("Microphone mode."))
            assertTrue(device.wait(Until.hasObject(By.descStartsWith("Video mode.")), 12000))
            status("recording=false;locked=false;busy=false;uploads=0;sends=0;deletes=0")
            val vid = device.findObject(By.descStartsWith("Video mode."))
            assertTrue("video node must publish ACTION_CLICK", a11yActivateByDesc("Video mode."))
            assertTrue(device.wait(Until.hasObject(By.descStartsWith("Microphone mode.")), 12000))
            status("recording=false;locked=false;busy=false;uploads=0;sends=0;deletes=0")
            capture("a11y-toggle-$style2")
        }
    }
    @Test fun holdThenLockAndDeleteNeverUploadsInBothStyles() {
        for (style2 in listOf(false, true)) runStyle(style2) {
            val r = device.findObject(By.descStartsWith("Microphone mode.")).visibleBounds
            val x=r.exactCenterX(); val y=r.exactCenterY(); val down=SystemClock.uptimeMillis()
            val dy=75f*ins.targetContext.resources.displayMetrics.density
            pointer(MotionEvent.ACTION_DOWN,x,y,down)
            try { status("recording=true;locked=false"); pointer(MotionEvent.ACTION_MOVE,x,y-dy,down) }
            finally { pointer(MotionEvent.ACTION_UP,x,y-dy,down) }
            status("recording=true;locked=true;busy=false")
            device.findObject(By.text("Advance synthetic audio 2s")).click()
            device.findObject(By.text("Delete synthetic recording")).click()
            status("uploads=0;sends=0;deletes=1"); capture("locked-delete-$style2")
        }
    }
    @Test fun holdSlideLeftCancelsWithoutUploadInBothStyles() {
        for (style2 in listOf(false, true)) runStyle(style2) {
            val r = device.findObject(By.descStartsWith("Microphone mode.")).visibleBounds
            val x = r.exactCenterX(); val y = r.exactCenterY(); val down = SystemClock.uptimeMillis()
            pointer(MotionEvent.ACTION_DOWN, x, y, down)
            try {
                status("recording=true;locked=false")
                val distance = 90f * ins.targetContext.resources.displayMetrics.density
                pointer(MotionEvent.ACTION_MOVE, x - distance, y, down)
            } finally { pointer(MotionEvent.ACTION_UP, x - 90f * ins.targetContext.resources.displayMetrics.density, y, down) }
            status("uploads=0;sends=0;deletes=1"); capture("drag-cancel-$style2")
        }
    }
    @Test fun holdUpLockSurvivesReleaseAndExplicitSyntheticSendInBothStyles() {
        for (style2 in listOf(false, true)) runStyle(style2) {
            val r = device.findObject(By.descStartsWith("Microphone mode.")).visibleBounds
            val x = r.exactCenterX(); val y = r.exactCenterY(); val down = SystemClock.uptimeMillis()
            val dy = 75f * ins.targetContext.resources.displayMetrics.density
            pointer(MotionEvent.ACTION_DOWN, x, y, down)
            try { status("recording=true;locked=false"); pointer(MotionEvent.ACTION_MOVE, x, y-dy, down) }
            finally { pointer(MotionEvent.ACTION_UP, x, y-dy, down) }
            status("recording=true;locked=true;busy=false"); capture("drag-locked-$style2")
            device.findObject(By.text("Advance synthetic audio 2s")).click()
            device.findObject(By.text("Send synthetic recording")).click()
            status("uploads=1;sends=1;deletes=1")
        }
    }
    @Test fun unlockedHoldReleaseSendsExactlyOnceAfterSyntheticMinimumInBothStyles() {
        for (style2 in listOf(false, true)) runStyle(style2) {
            val r = device.findObject(By.descStartsWith("Microphone mode.")).visibleBounds
            val x = r.exactCenterX(); val y = r.exactCenterY(); val down = SystemClock.uptimeMillis()
            pointer(MotionEvent.ACTION_DOWN, x, y, down)
            try {
                status("recording=true;locked=false")
                // Clock only, no second pointer while the recording gesture owns this one.
                ins.runOnMainSync { VoiceGestureFixtureActivity.advanceSyntheticAudio?.invoke() }
            } finally { pointer(MotionEvent.ACTION_UP, x, y, down) }
            status("uploads=1;sends=1;deletes=1"); capture("hold-release-$style2")
        }
    }
    @Test fun systemPointerCancelMustNeverReleaseToSendInBothStyles() {
        for (style2 in listOf(false, true)) runStyle(style2) {
            val r = device.findObject(By.descStartsWith("Microphone mode.")).visibleBounds
            val x = r.exactCenterX(); val y = r.exactCenterY(); val down = SystemClock.uptimeMillis()
            pointer(MotionEvent.ACTION_DOWN, x, y, down)
            try { status("recording=true;locked=false") }
            finally { pointer(MotionEvent.ACTION_CANCEL, x, y, down) }
            status("uploads=0;sends=0;deletes=1"); capture("system-cancel-$style2")
        }
    }
}
