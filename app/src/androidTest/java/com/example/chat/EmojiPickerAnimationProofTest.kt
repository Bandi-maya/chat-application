package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.example.chat.ui.chats.detail.emojiLoopObserver
import com.example.chat.ui.chats.detail.emojiLottieSpecResolver
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Production ChatyEmojiPickerSheet (recents tab, two seeded tiles) with a bundled 1s composition so it runs
 *  offline: both visible tiles must keep advancing (iteration >= 3, i.e. two full cycles), tapping a tile
 *  still picks that emoji, and the picker still shows the tiles afterward. Animator scale forced to 1 and restored. */
@RunWith(AndroidJUnit4::class)
class EmojiPickerAnimationProofTest {
    private data class S(val id: String, val p: Float, val it: Int)
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val samples = CopyOnWriteArrayList<S>()
    private val origResolver = emojiLottieSpecResolver
    private var saved = "1.0"
    private val json = """{"v":"5.7.0","fr":30,"ip":0,"op":30,"w":100,"h":100,"nm":"loop","ddd":0,"assets":[],"layers":[{"ddd":0,"ind":1,"ty":4,"nm":"s","sr":1,"ks":{"o":{"a":0,"k":100},"r":{"a":1,"k":[{"t":0,"s":[0],"i":{"x":[0.5],"y":[1]},"o":{"x":[0.5],"y":[0]}},{"t":30,"s":[360]}]},"p":{"a":0,"k":[50,50,0]},"a":{"a":0,"k":[0,0,0]},"s":{"a":0,"k":[100,100,100]}},"ao":0,"shapes":[{"ty":"gr","it":[{"ty":"rc","d":1,"s":{"a":0,"k":[60,30]},"p":{"a":0,"k":[0,0]},"r":{"a":0,"k":4}},{"ty":"fl","c":{"a":0,"k":[1,0.6,0,1]},"o":{"a":0,"k":100},"r":1},{"ty":"tr","p":{"a":0,"k":[0,0]},"a":{"a":0,"k":[0,0]},"s":{"a":0,"k":[100,100]},"r":{"a":0,"k":0},"o":{"a":0,"k":100}}]}],"ip":0,"op":30,"st":0,"bm":0}]}"""

    private fun sh(c: String) = ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(c)).use { it.readBytes().decodeToString().trim() }

    @Before fun setUp() {
        saved = sh("settings get global animator_duration_scale").let { if (it == "null" || it.isBlank()) "1.0" else it }
        sh("settings put global animator_duration_scale 1.0")
        EmojiPickerFixtureActivity.picked.clear()
        emojiLottieSpecResolver = { LottieCompositionSpec.JsonString(json) }
        emojiLoopObserver = { id, p, i -> samples.add(S(id, p, i)) }
    }
    @After fun tearDown() { sh("settings put global animator_duration_scale $saved"); emojiLottieSpecResolver = origResolver; emojiLoopObserver = null }

    private fun shot(n: String) {
        inst.waitForIdleSync()
        val d = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val b: Bitmap = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("screenshot null")
        FileOutputStream(File(d, "$n.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
    }

    @Test fun pickerTilesLoopAndStillPick() {
        ActivityScenario.launch<EmojiPickerFixtureActivity>(Intent(inst.targetContext, EmojiPickerFixtureActivity::class.java)).use {
            val end = System.currentTimeMillis() + 10_000
            fun maxIt(id: String) = samples.filter { s -> s.id == id }.maxOfOrNull { s -> s.it } ?: 0
            while (System.currentTimeMillis() < end && (maxIt("1f602") < 3 || maxIt("2764_fe0f") < 3)) Thread.sleep(200)
            assertTrue("picker tile 1f602 did not loop twice (ids ${samples.map { s -> s.id }.toSet()})", maxIt("1f602") >= 3)
            assertTrue("picker tile 2764_fe0f did not loop twice", maxIt("2764_fe0f") >= 3)
            shot("picker-anim-1")
            val dev = UiDevice.getInstance(inst)
            val tile = dev.wait(Until.findObject(By.desc("Emoji \uD83D\uDE02")), 3000)
            assertTrue("tile semantics 'Emoji 😂' not found", tile != null)
            tile.click()
            Thread.sleep(500)
            assertEquals(listOf("\uD83D\uDE02"), EmojiPickerFixtureActivity.picked.toList())
            assertTrue("tiles gone after pick", dev.hasObject(By.desc("Emoji \uD83D\uDE02")))
            shot("picker-anim-2-after-pick")
        }
    }
}
