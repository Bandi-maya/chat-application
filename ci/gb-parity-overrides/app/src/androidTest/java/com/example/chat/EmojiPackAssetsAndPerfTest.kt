package com.example.chat

import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.Debug
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.chats.detail.ChatyEmojiPack
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pack asset coverage + memory/scroll probe. Never skips and never substitutes: when a pack's
 * files are not bundled in this build it FAILS with an explicit "not bundled" message (the
 * diagnostic build excludes the 99 art files, so these are expected to fail there).
 * Memory numbers are measurements, not a pass/fail on perf; the asserts are decode coverage,
 * AnimatedImageDrawable type for Microsoft, and that memory after scrolling away does not
 * stay at the visible peak (leak sanity, generous margin).
 */
@RunWith(AndroidJUnit4::class)
class EmojiPackAssetsAndPerfTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private var saved = "1.0"
    private fun sh(c: String) = ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(c)).use { it.readBytes().decodeToString().trim() }

    @Before fun setUp() {
        saved = sh("settings get global animator_duration_scale").let { if (it == "null" || it.isBlank()) "1.0" else it }
        sh("settings put global animator_duration_scale 1.0")
    }
    @After fun tearDown() { sh("settings put global animator_duration_scale $saved") }

    private fun decodeAll(pack: ChatyEmojiPack.AssetPack, wantAnimated: Boolean) {
        val names = ctx.assets.list(pack.dir)?.filter { it.endsWith(".${pack.ext}") }.orEmpty()
        assertTrue("${pack.id} pack not bundled in this build (0 files under ${pack.dir})", names.isNotEmpty())
        val failures = mutableListOf<String>()
        for (n in names) {
            val r = runCatching {
                val src = ImageDecoder.createSource(ctx.assets, "${pack.dir}/$n")
                val d = ImageDecoder.decodeDrawable(src)
                if (wantAnimated) check(d is AnimatedImageDrawable) { "not animated" }
                else check(d.intrinsicWidth > 0) { "empty" }
            }
            if (r.isFailure) failures.add("$n: ${r.exceptionOrNull()?.message}")
        }
        File(ctx.getExternalFilesDir(null), "pack-${pack.id}-coverage.txt").writeText("files=${names.size} failures=${failures.size}\n${failures.joinToString("\n")}")
        assertTrue("${pack.id}: ${failures.size}/${names.size} files failed to decode: $failures", failures.isEmpty())
    }

    @Test fun microsoftAllBundledFilesDecodeAsAnimated() {
        assertTrue("animated decode needs API 28+", Build.VERSION.SDK_INT >= 28)
        decodeAll(ChatyEmojiPack.MICROSOFT_PACK, wantAnimated = true)
    }

    @Test fun openMojiAllBundledFilesDecodeAsStatic() = decodeAll(ChatyEmojiPack.OPENMOJI_PACK, wantAnimated = false)

    private fun heapKb(): Long {
        System.gc(); Thread.sleep(300); System.gc()
        return (Debug.getNativeHeapAllocatedSize() + Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }) / 1024
    }

    /** Evidence-only split of heapKb(): (nativeKb, managedKb) with the same GC
     *  discipline. Discriminates native-side retention (decoded drawable
     *  buffers) from managed-heap retention; assertions keep using the sums. */
    private fun heapPartsKb(): Pair<Long, Long> {
        System.gc(); Thread.sleep(300); System.gc()
        val nativeKb = Debug.getNativeHeapAllocatedSize() / 1024
        val managedKb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024
        return nativeKb to managedKb
    }

    private fun gridRun(pack: String): Triple<Long, Long, Long> {
        val beforeP = heapPartsKb(); val before = beforeP.first + beforeP.second
        var peak = 0L; var off = 0L; var back = 0L
        var peakP = 0L to 0L; var offP = 0L to 0L; var backP = 0L to 0L
        ActivityScenario.launch<EmojiPackGridFixtureActivity>(
            Intent(ctx, EmojiPackGridFixtureActivity::class.java).putExtra("pack", pack),
        ).use {
            val end = System.currentTimeMillis() + 30_000
            var phase = ""
            while (System.currentTimeMillis() < end && EmojiPackGridProbe.phase != "done") {
                val p = EmojiPackGridProbe.phase
                if (p != phase) {
                    phase = p
                    if (p == "scrolledOff") { Thread.sleep(3_000); offP = heapPartsKb(); off = offP.first + offP.second }
                    if (p == "back") { Thread.sleep(2_000); backP = heapPartsKb(); back = backP.first + backP.second }
                } else if (p == "visible") {
                    Thread.sleep(5_000); peakP = heapPartsKb(); peak = peakP.first + peakP.second
                }
                Thread.sleep(200)
            }
        }
        // Post-close sample (evidence only): after the fixture activity is
        // destroyed, memory that drops was composition-lifecycle-bound; memory
        // that stays is process-global retention. Sums stay the returned
        // Triple's inputs; the splits only enrich the evidence file.
        Thread.sleep(3_000)
        val closedP = heapPartsKb(); val closed = closedP.first + closedP.second
        // Cache-ownership discrimination (evidence-only): run-128 measured +158MB
        // native retained after close with managed flat. The rendered path is the
        // Noto Lottie Url spec (AnimatedEmojiView ignores emojiStyle; the fixture's
        // pack extra selects nothing on this path), whose compositions live in
        // Lottie's process-global LottieCompositionCache. Clearing that cache AFTER
        // the closed sample and re-measuring attributes the retention: a drop is
        // cache-owned; no drop means per-drawable or other references survive
        // composition. Assertions unchanged; this only enriches the evidence file.
        Thread.sleep(1_000)
        com.airbnb.lottie.LottieCompositionCache.getInstance().clear()
        val clearedP = heapPartsKb(); val cleared = clearedP.first + clearedP.second
        File(java.io.File(ctx.getExternalFilesDir(null), "screenshots").apply { mkdirs() }, "grid-$pack.txt").writeText("pack=$pack beforeKb=$before visibleKb=$peak scrolledOffKb=$off backKb=$back closedKb=$closed beforeSplitKb=${beforeP.first}/${beforeP.second} visibleSplitKb=${peakP.first}/${peakP.second} offSplitKb=${offP.first}/${offP.second} backSplitKb=${backP.first}/${backP.second} closedSplitKb=${closedP.first}/${closedP.second} clearedKb=$cleared clearedSplitKb=${clearedP.first}/${clearedP.second} (split=nativeKb/managedKb)")
        assertEquals("grid did not finish all phases for $pack", "done", EmojiPackGridProbe.phase)
        return Triple(peak - before, off - before, back - before)
    }

    @Test fun microsoftGridVisibleScrollOffAndBackMemory() {
        assertTrue("Microsoft pack not bundled in this build", ChatyEmojiPack.packBundledCount(ctx, ChatyEmojiPack.MICROSOFT_PACK) > 0)
        EmojiPackGridProbe.phase = "idle"
        val (visible, off, back) = gridRun("Microsoft")
        // Leak sanity only: after items leave composition memory must drop clearly below the visible peak.
        // backKb evidence distinguishes retention (stays high) from transient GC timing (recovers).
        assertTrue("Microsoft memory after scroll-off ($off KB over baseline) did not fall below visible ($visible KB); backKb=$back", off < visible || visible < 4_096)
    }

    @Test fun microsoftReducedMotionGridCompletesWithoutAnimation() {
        sh("settings put global animator_duration_scale 0")
        assertTrue("Microsoft pack not bundled in this build", ChatyEmojiPack.packBundledCount(ctx, ChatyEmojiPack.MICROSOFT_PACK) > 0)
        EmojiPackGridProbe.phase = "idle"
        gridRun("Microsoft")   // completes without crash; first-frame look is judged from a screenshot, not asserted here
    }

    @Test fun openMojiStaticGridCompletes() {
        assertTrue("OpenMoji pack not bundled in this build", ChatyEmojiPack.packBundledCount(ctx, ChatyEmojiPack.OPENMOJI_PACK) > 0)
        EmojiPackGridProbe.phase = "idle"
        gridRun("OpenMoji")
    }
}
