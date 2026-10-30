package com.example.chat

import android.Manifest
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.chat.ui.camera.CameraVideoNativeProof
import com.example.chat.ui.camera.CameraVideoSession
import com.example.chat.ui.camera.LensSize
import com.example.chat.ui.camera.VideoAuthPort
import com.example.chat.ui.camera.VideoScopeLease
import com.example.chat.ui.camera.VideoSessionState
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Diagnostic isolation for the native-recording stalls: binds CameraVideoSession
 *  directly (no chat UI) under three controlled combos and records whether the
 *  Recorder ever reaches Recording on the emulator. Effect on/off and the
 *  3-use-case snapshot combination are isolated independently. Evidence-first:
 *  every case dumps its full state sequence before asserting. */
@RunWith(AndroidJUnit4::class)
class VideoEffectComboDiagProofTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val d = UiDevice.getInstance(ins)

    private fun capture(name: String) {
        val out = File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        d.dumpWindowHierarchy(File(out, "combo-diag-$name.xml"))
        ins.uiAutomation.takeScreenshot()?.let { b ->
            File(out, "combo-diag-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle()
        }
    }

    private fun awaitStates(label: String, states: CopyOnWriteArrayList<Pair<Long, VideoSessionState>>, predicate: (VideoSessionState) -> Boolean): Boolean {
        val t0 = SystemClock.uptimeMillis()
        val end = t0 + 20000
        while (states.none { predicate(it.second) } && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
        capture(label)
        // Full state contents (Error messages, Ready capability fields) with relative
        // timing - the stall shape and any Finalize error text are the evidence.
        val dump = states.joinToString(" | ") { "+${it.first - t0}ms:${it.second}" }
        android.util.Log.i("SHARD-EVIDENCE", "combo-gate=$label hit=${states.any { predicate(it.second) }} states=$dump")
        return states.any { predicate(it.second) }
    }

    /** Always-signed-in synthetic port: the diagnostic owns no real auth state. */
    private class FakePort : VideoAuthPort {
        private val snap = Any()
        override fun snapshot(): Any = snap
        override fun isSignedIn(snapshot: Any): Boolean = snapshot === snap
        override fun withScope(expected: Any, block: () -> Unit): Boolean {
            if (expected !== snap) return false
            block()
            return true
        }
        override fun addListener(listener: (Any) -> Unit) {}
        override fun removeListener(listener: (Any) -> Unit) {}
    }

    private fun runCombo(name: String, forceEffect: Boolean, forceSnapshot: Boolean) {
        val c = ins.targetContext
        d.executeShellCommand("pm grant ${c.packageName} android.permission.CAMERA")
        CameraVideoNativeProof.reset()
        CameraVideoNativeProof.forceEffectUnavailable = forceEffect
        CameraVideoNativeProof.forceSnapshotUnavailable = forceSnapshot
        val states = CopyOnWriteArrayList<Pair<Long, VideoSessionState>>()
        var scenario: ActivityScenario<MediaFixtureActivity>? = null
        var session: CameraVideoSession? = null
        var lease: VideoScopeLease? = null
        try {
            scenario = ActivityScenario.launch(MediaFixtureActivity::class.java)
            var activity: MediaFixtureActivity? = null
            scenario!!.onActivity { activity = it }
            val host = activity!!
            ins.runOnMainSync {
                val previewView = PreviewView(host)
                host.setContentView(previewView)
                lease = VideoScopeLease(FakePort()) {}
                session = CameraVideoSession(
                    context = host,
                    owner = host,
                    previewView = previewView,
                    selector = CameraSelector.DEFAULT_FRONT_CAMERA,
                    desiredStabilization = false,
                    auth = lease!!,
                    viewportProvider = { LensSize(previewView.width.toFloat(), previewView.height.toFloat()) },
                    onState = { states.add(SystemClock.uptimeMillis() to it) },
                )
            }
            SystemClock.sleep(500) // let the PreviewView lay out so the viewport is real
            ins.runOnMainSync { session!!.bind() }
            val ready = awaitStates("$name-ready", states) { it is VideoSessionState.Ready }
            if (!ready) {
                assertTrue("$name: Ready never reached; states=$states", ready)
                return
            }
            ins.runOnMainSync { session!!.start(false) }
            val recording = awaitStates("$name-recording", states) { it is VideoSessionState.Recording }
            assertTrue("$name: Recording never reached; states=$states", recording)
        } finally {
            try {
                ins.runOnMainSync {
                    try { session?.close() } catch (_: Exception) {}
                    try { lease?.close() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            try { scenario?.close() } catch (_: Exception) {}
            CameraVideoNativeProof.reset()
        }
    }

    @Test fun plainBindRecordsWithoutEffect() = runCombo("plain", forceEffect = true, forceSnapshot = true)

    @Test fun effectBindRecordsWithoutSnapshot() = runCombo("effect-no-snap", forceEffect = false, forceSnapshot = true)

    @Test fun effectBindRecordsWithSnapshotCombo() = runCombo("effect-with-snap", forceEffect = false, forceSnapshot = false)
}
