package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.example.chat.ui.chats.detail.emojiLoopObserver
import com.example.chat.ui.chats.detail.emojiLottieSpecResolver
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the production single-emoji render path with a bundled 1s Lottie
 *  (offline) and proves it keeps looping: more than one iteration, continued
 *  progress after scrolling away and back, after activity recreation, and in a
 *  freshly opened screen. Captures screenshots. Nothing is sent. */
@RunWith(AndroidJUnit4::class)
class SingleEmojiLoopProofTest {

    private data class Sample(val t: Long, val progress: Float, val iteration: Int)

    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val samples = CopyOnWriteArrayList<Sample>()
    private val originalResolver = emojiLottieSpecResolver

    private val json = """{"v":"5.7.0","fr":30,"ip":0,"op":30,"w":100,"h":100,"nm":"loop","ddd":0,"assets":[],"layers":[{"ddd":0,"ind":1,"ty":4,"nm":"s","sr":1,"ks":{"o":{"a":0,"k":100},"r":{"a":1,"k":[{"t":0,"s":[0],"i":{"x":[0.5],"y":[1]},"o":{"x":[0.5],"y":[0]}},{"t":30,"s":[360]}]},"p":{"a":0,"k":[50,50,0]},"a":{"a":0,"k":[0,0,0]},"s":{"a":0,"k":[100,100,100]}},"ao":0,"shapes":[{"ty":"gr","it":[{"ty":"rc","d":1,"s":{"a":0,"k":[60,30]},"p":{"a":0,"k":[0,0]},"r":{"a":0,"k":4}},{"ty":"fl","c":{"a":0,"k":[1,0.6,0,1]},"o":{"a":0,"k":100},"r":1},{"ty":"tr","p":{"a":0,"k":[0,0]},"a":{"a":0,"k":[0,0]},"s":{"a":0,"k":[100,100]},"r":{"a":0,"k":0},"o":{"a":0,"k":100}}]}],"ip":0,"op":30,"st":0,"bm":0}]}"""

    @Before fun setUp() {
        val scale = Settings.Global.getFloat(inst.targetContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        assumeTrue("animator scale 0 disables animation by design", scale != 0f)
        emojiLottieSpecResolver = { LottieCompositionSpec.JsonString(json) }
        emojiLoopObserver = { _, p, i -> samples.add(Sample(System.nanoTime(), p, i)) }
    }

    @After fun tearDown() {
        emojiLottieSpecResolver = originalResolver
        emojiLoopObserver = null
    }

    private fun shot(name: String) {
        inst.waitForIdleSync()
        val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("screenshot null")
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    /** Waits until at least [minNew] samples arrive after now with >= [minDistinct] distinct progress values. */
    private fun assertAdvancing(label: String, windowMs: Long = 2500, minDistinct: Int = 5) {
        val from = samples.size
        Thread.sleep(windowMs)
        val fresh = samples.drop(from)
        val distinct = fresh.map { (it.progress * 100).toInt() }.toSet().size
        assertTrue("$label: expected live progress, got ${fresh.size} samples / $distinct distinct", distinct >= minDistinct)
    }

    private fun launch() = ActivityScenario.launch<EmojiLoopFixtureActivity>(
        Intent(inst.targetContext, EmojiLoopFixtureActivity::class.java),
    )

    @Test fun singleEmojiKeepsLoopingAcrossScrollRecreateAndReopen() {
        launch().use { sc ->
            // Lottie iteration starts at 1 and increments when the next pass BEGINS, so >=3 means two full 1s cycles completed.
            val deadline = System.currentTimeMillis() + 8000
            while (System.currentTimeMillis() < deadline && (samples.maxOfOrNull { it.iteration } ?: 0) < 3) Thread.sleep(100)
            assertTrue("fewer than 2 full loops completed (max iteration ${samples.maxOfOrNull { it.iteration }})", (samples.maxOfOrNull { it.iteration } ?: 0) >= 3)
            shot("emoji-loop-1-playing")
            Thread.sleep(300)
            shot("emoji-loop-2-playing")

            sc.onActivity { it.scrollToIndex(55) }
            Thread.sleep(1200)
            sc.onActivity { it.scrollToIndex(0) }
            Thread.sleep(500)
            assertAdvancing("after scroll away and back")
            shot("emoji-loop-3-after-scroll")

            sc.recreate()
            Thread.sleep(500)
            assertAdvancing("after recreation")
            shot("emoji-loop-4-after-recreate")
        }
        samples.clear()
        launch().use {
            Thread.sleep(500)
            assertAdvancing("after reopening screen")
            shot("emoji-loop-5-reopened")
        }
    }
}
