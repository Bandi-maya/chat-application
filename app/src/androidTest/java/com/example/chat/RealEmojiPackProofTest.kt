package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.chats.detail.emojiLoopObserver
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real pack proof: production ChatMessageBubble + default Noto URL resolver (no bundled stand-in).
 *  Needs emulator internet; if the animation never loads this FAILS with an explicit message (it never skips
 *  or substitutes artwork). Reduced motion (animator scale 0) must show the static glyph and no frames. */
@RunWith(AndroidJUnit4::class)
class RealEmojiPackProofTest {
    private data class S(val id: String, val p: Float, val it: Int)
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val samples = CopyOnWriteArrayList<S>()
    private var saved = "1.0"

    private fun sh(c: String) = ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(c)).use { it.readBytes().decodeToString().trim() }

    @Before fun setUp() {
        saved = sh("settings get global animator_duration_scale").let { if (it == "null" || it.isBlank()) "1.0" else it }
        emojiLoopObserver = { id, p, i -> samples.add(S(id, p, i)) }
    }
    @After fun tearDown() { sh("settings put global animator_duration_scale $saved"); emojiLoopObserver = null }

    private fun shot(n: String) {
        inst.waitForIdleSync()
        val d = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val b: Bitmap = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("screenshot null")
        FileOutputStream(File(d, "$n.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
    }
    private fun launch() = ActivityScenario.launch<EmojiRealPackFixtureActivity>(Intent(inst.targetContext, EmojiRealPackFixtureActivity::class.java))

    @Test fun realNotoJumboAndReactionLoopInRealBubble() {
        sh("settings put global animator_duration_scale 1.0")
        launch().use {
            val end = System.currentTimeMillis() + 40_000
            fun maxIt(id: String) = samples.filter { s -> s.id == id }.maxOfOrNull { s -> s.it } ?: 0
            while (System.currentTimeMillis() < end && (maxIt("1f602") < 3 || samples.none { s -> s.id == "2764_fe0f" })) Thread.sleep(250)
            assertTrue("real Noto 1f602 never reached iteration 3 in the bubble (loaded ids: ${samples.map { s -> s.id }.toSet()}); network unavailable or asset id wrong", maxIt("1f602") >= 3)
            assertTrue("reaction badge heart (2764_fe0f) never rendered the animated asset (ids: ${samples.map { s -> s.id }.toSet()})", samples.any { s -> s.id == "2764_fe0f" })
            shot("realpack-1"); Thread.sleep(250); shot("realpack-2")
        }
    }

    @Test fun reducedMotionShowsStaticGlyphAndNoFrames() {
        sh("settings put global animator_duration_scale 0")
        launch().use {
            Thread.sleep(4000)
            assertTrue("animation frames reported with animator scale 0: ${samples.size}", samples.isEmpty())
            shot("realpack-reduced-motion")
        }
    }
}
