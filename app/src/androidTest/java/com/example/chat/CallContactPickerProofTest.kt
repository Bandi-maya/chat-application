package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.data.services.CallSessionState
import com.example.chat.data.services.ChatyCallSession
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.calls.AndroidCallPresentationPrefsStorage
import com.example.chat.ui.calls.CallContactPickerScreen
import com.example.chat.ui.calls.CallPresentationPreferencesStore
import com.example.chat.ui.calls.bundles.BundleIncomingCallView
import com.example.chat.ui.calls.bundles.CallBundleId
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Proof for the requested call contact picker: contacts listed, row starts a
 *  voice call for that contact, dialpad toggle switches modes. */
@RunWith(AndroidJUnit4::class)
class CallContactPickerProofTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val dev get() = UiDevice.getInstance(ins)

    private fun shot(name: String) {
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        dev.waitForIdle(3000)
        val bmp = ins.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun contact(id: String, name: String) = UserProfile(
        id = id, displayName = name, username = id,
        avatarInitials = name.split(" ").take(2).joinToString("") { it.take(1) },
        avatarColorHex = "#2AA198", about = "Synthetic contact",
        lastSeenAt = Instant.EPOCH, email = "$id@synthetic.example", phone = "+1555000$id",
    )

    @Test
    fun contactsListedRowStartsVoiceCallAndDialpadToggles() {
        val calls = CopyOnWriteArrayList<Pair<String, Boolean>>()
        val contacts = listOf(
            contact("c01", "Asha Verma"),
            contact("c02", "Rohan Iyer"),
            contact("c03", "Meera Nair"),
        )
        val scenario = ActivityScenario.launch(MediaFixtureActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                ChatyTheme(ThemePresets.whatsappIosLight) {
                    CallContactPickerScreen(
                        contacts = contacts,
                        currentUserId = "me",
                        onBack = {},
                        onStartCall = { c, video -> calls.add(c.id to video) },
                        showToast = { _, _ -> },
                    )
                }
            }
        }
        for (name in listOf("Asha Verma", "Rohan Iyer", "Meera Nair")) {
            assertTrue("contact '$name' not listed", dev.wait(Until.hasObject(By.text(name)), 8000))
        }
        shot("call-contact-picker-contacts")
        dev.findObject(By.text("Asha Verma")).click()
        Thread.sleep(900)
        assertEquals("tapping a contact row must start a voice call for it", listOf("c01" to false), calls.toList())
        shot("call-contact-picker-starting")
        val toggle = dev.wait(Until.findObject(By.desc("Open dialpad")), 5000)
        assertNotNull("dialpad toggle missing", toggle)
        toggle.click()
        assertTrue("dialpad mode did not open", dev.wait(Until.hasObject(By.desc("Show contacts")), 5000))
        shot("call-contact-picker-dialpad")
        val contactsToggle = dev.wait(Until.findObject(By.desc("Show contacts").clickable(true)),5000)
        assertNotNull("Clickable contacts toggle missing",contactsToggle)
        dev.waitForIdle()
        contactsToggle!!.click()
        val returned = dev.wait(Until.hasObject(By.desc("Open dialpad").clickable(true)),5000)
        shot("call-contact-picker-after-return-click")
        assertTrue("contacts mode did not return", returned)
        assertTrue("Contacts title must return",dev.hasObject(By.text("Contacts")))
        scenario.close()
    }
    private fun incomingSession(name: String) = ChatyCallSession(
        callId = "bundle-proof-1",
        remoteUserId = "bundle-proof-user",
        remoteDisplayName = name,
        isVideo = false,
        isOutgoing = false,
        state = CallSessionState.incoming,
        startedAt = Instant.EPOCH,
    )

    private fun swipeFrom(desc: String, dx: Int, dy: Int) {
        val obj = dev.wait(Until.findObject(By.desc(desc)), 8000) ?: run {
            shot("call-control-missing")
            val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            dev.dumpWindowHierarchy(File(dir, "call-control-missing.xml"))
            throw AssertionError("control '$desc' not found after mounted-target wait")
        }
        // Gesture must start on the actual target, not the unrelated display center.
        val bounds = obj.visibleBounds
        val x = bounds.centerX()
        val y = bounds.centerY()
        val endX = x + dx
        val endY = y + dy
        val metrics = ins.targetContext.resources.displayMetrics
        val diagnostic = "control=$desc bounds=$bounds start=($x,$y) end=($endX,$endY) " +
            "deltaPx=($dx,$dy) distancePx=${kotlin.math.hypot(dx.toDouble(), dy.toDouble())} " +
            "density=${metrics.density} densityDpi=${metrics.densityDpi} " +
            "deltaDp=(${dx / metrics.density},${dy / metrics.density}) " +
            "display=${dev.displayWidth}x${dev.displayHeight} steps=60"
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "call-gesture-injection.txt").appendText("$diagnostic\n")
        android.util.Log.i("CallGestureProof", diagnostic)
        assertTrue("Gesture start outside screen: $diagnostic",
            x in 0 until dev.displayWidth && y in 0 until dev.displayHeight)
        assertTrue("Gesture endpoint outside screen: $diagnostic",
            endX in 0 until dev.displayWidth && endY in 0 until dev.displayHeight)
        val injected = dev.swipe(x, y, endX, endY, 60)
        File(dir, "call-gesture-injection.txt").appendText("injected=$injected\n")
        assertTrue("UiDevice gesture injection failed: $diagnostic", injected)
    }

    /**
     * Readiness gate: inject the reference drag only while the input subsystem
     * is quiet. InputReader reconfigurations and StylusManager device-change
     * bursts (virtio_input_multi_touch re-enumeration) are coincident evidence
     * around some misses, not proven OS stream cancellation: run 41901570247 had
     * a miss with no in-window churn at all. A unique marker is logged, then after
     * a short quiet window the
     * device log (read via shell) must show no device-change event after that
     * marker. Bounded to ~10s; if the churn never settles the drive proceeds and
     * the fact is recorded. Diagnostics only - no retry, no assertion change.
     */
    private fun awaitQuietInput(tag: String, what: String) {
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val deadline = System.currentTimeMillis() + 10_000
        var check = 0
        while (true) {
            check++
            val marker = "CallGestureProof-quiet-$tag-$check-${System.nanoTime()}"
            android.util.Log.i("CallGestureProof", marker)
            Thread.sleep(800) // quiet window
            val noisy = try {
                // UiDevice.executeShellCommand does not interpret pipes: a piped
                // command passed "|" to logcat, which exited with a usage error, so
                // the dump was empty and this gate passed instantly on every check.
                // Raw tail-bounded dump, filtered in Kotlin below.
                val dump = dev.executeShellCommand("logcat -d -v brief -t 5000")
                if (!dump.contains(marker)) null else {
                    val afterMarker = dump.substringAfterLast(marker)
                    afterMarker.contains("Reconfiguring input devices") ||
                        afterMarker.contains("Stylus InputDevice changed")
                }
            } catch (e: Exception) {
                null
            }
            if (noisy == null) {
                File(dir, "call-gesture-injection.txt").appendText("$tag: $what input quiet unknown at check $check (missing marker or logcat error)\n")
            }
            if (noisy == false) {
                File(dir, "call-gesture-injection.txt").appendText("$tag: $what input quiet after $check check(s)" + "\n")
                return
            }
            if (System.currentTimeMillis() > deadline) {
                val note = "$tag: $what input ${if (noisy == null) "quiet unknown" else "NOT quiet"} after ${check} checks; injecting anyway"
                android.util.Log.w("CallGestureProof", note)
                File(dir, "call-gesture-injection.txt").appendText(note + "\n")
                return
            }
        }
    }

    /**
     * Single real drag, bracketed by start/end markers in logcat. After the drive,
     * whether a device-change event fell inside THIS drive's window is recorded to
     * call-gesture-injection.txt. In-window churn is coincident evidence only, not
     * proof of OS stream cancellation (run 41901570247 publisher note): it raises
     * the suspicion, it does not close the diagnosis. Never re-drives: the caller's
     * assertion stands alone.
     */
    /** Handle for the concurrent settle witness; see startSettleWitness. */
    private class SettleWitness(
        val maxOffset: java.util.concurrent.atomic.AtomicInteger,
        val stop: java.util.concurrent.atomic.AtomicBoolean,
        val thread: Thread,
    )

    /**
     * Settle witness, concurrent: a background thread samples the puck node's
     * bounds DURING the injection. A nonzero offset shows the drag moved the
     * node; a zero offset is NOT proof the stream was never admitted, because
     * a11y reads can stall behind injection (zero was recorded on drives that
     * passed, runs 41879111168 and 41901570247). Diagnostics only - the
     * caller's assertion is unchanged.
     */
    private fun startSettleWitness(desc: String): SettleWitness {
        val restBounds = try { dev.findObject(By.desc(desc))?.visibleBounds } catch (e: Exception) { null }
        val maxOffset = java.util.concurrent.atomic.AtomicInteger(-1)
        val witnessing = java.util.concurrent.atomic.AtomicBoolean(true)
        val thread = kotlin.concurrent.thread(isDaemon = true) {
            while (witnessing.get()) {
                val now = try { dev.findObject(By.desc(desc))?.visibleBounds } catch (e: Exception) { null }
                if (restBounds != null && now != null) {
                    val off = kotlin.math.abs(now.top - restBounds.top) + kotlin.math.abs(now.left - restBounds.left)
                    if (off > maxOffset.get()) maxOffset.set(off)
                }
                Thread.sleep(120)
            }
        }
        return SettleWitness(maxOffset, witnessing, thread)
    }

    private fun stopSettleWitness(witness: SettleWitness) {
        witness.stop.set(false)
        witness.thread.join(3000)
    }

    /**
     * In-window input-device churn between two logcat markers. Same pipe bug as
     * the quiet gate: "|" made logcat exit with a usage error, so
     * in-window-device-change was always recorded false. Raw tail-bounded dump,
     * filtered by the substring checks below. Coincident evidence only, not
     * proof of OS stream cancellation (run 41901570247 publisher note).
     */
    private fun inWindowDeviceChange(startMarker: String, endMarker: String): String {
        return try {
            val dump = dev.executeShellCommand("logcat -d -v brief -t 5000")
            val startIndex = dump.lastIndexOf(startMarker)
            val endIndex = dump.indexOf(endMarker, (startIndex + startMarker.length).coerceAtLeast(0))
            if (startIndex < 0 || endIndex < 0) "unknown" else {
                val inWindow = dump.substring(startIndex + startMarker.length, endIndex)
                (inWindow.contains("Reconfiguring input devices") ||
                    inWindow.contains("Stylus InputDevice changed")).toString()
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * The hold-start MotionEvent train shared by the post-swipe diagnostic and
     * the controlled hold-start case: DOWN, hold 400ms like a real finger
     * settling on the puck, then 6 slow first MOVEs over the first quarter of
     * the drag, then a steady remainder, then UP. Events go through
     * ins.uiAutomation.injectInputEvent, the same injection path UiDevice.swipe
     * uses internally (InteractionController). Returns the train error (null on
     * clean completion) and the count of rejected events. Never leaves a
     * pointer down: an interrupted train ends in ACTION_CANCEL.
     */
    private fun injectHoldStartTrain(x: Float, y: Float, delta: Pair<Int, Int>): Pair<String?, Int> {
        val endX = x + delta.first
        val endY = y + delta.second
        val downTime = android.os.SystemClock.uptimeMillis()
        var failedInjects = 0
        fun inject(action: Int, px: Float, py: Float): Boolean {
            val ev = android.view.MotionEvent.obtain(
                downTime, android.os.SystemClock.uptimeMillis(), action, px, py, 0,
            )
            ev.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try {
                val acceptedByUiAutomation = ins.uiAutomation.injectInputEvent(ev, true)
                if (!acceptedByUiAutomation) failedInjects++
                return acceptedByUiAutomation
            } finally {
                ev.recycle()
            }
        }
        var upSent = false
        var error: String? = null
        try {
            inject(android.view.MotionEvent.ACTION_DOWN, x, y)
            Thread.sleep(400)
            for (i in 1..6) {
                inject(
                    android.view.MotionEvent.ACTION_MOVE,
                    x + delta.first * (0.25f * i / 6), y + delta.second * (0.25f * i / 6),
                )
                Thread.sleep(80)
            }
            for (i in 1..50) {
                inject(
                    android.view.MotionEvent.ACTION_MOVE,
                    x + delta.first * (0.25f + 0.75f * i / 50), y + delta.second * (0.25f + 0.75f * i / 50),
                )
                Thread.sleep(20)
            }
            // upSent is set only when the UP was actually accepted, so a rejected UP
            // still triggers the finally ACTION_CANCEL below.
            upSent = inject(android.view.MotionEvent.ACTION_UP, endX, endY)
        } catch (e: Exception) {
            error = e.javaClass.simpleName + ": " + (e.message ?: "")
        } finally {
            // Never leave a pointer down if the train was interrupted.
            if (!upSent) {
                try {
                    inject(android.view.MotionEvent.ACTION_CANCEL, endX, endY)
                } catch (_: Exception) {
                }
            }
        }
        return error to failedInjects
    }

    /**
     * Interactivity gate: inject only while the fixture window holds input focus.
     * Four runs (41851643303, 41861366491, 41870523872, 41879111168) each swallowed
     * exactly one early drive after a scenario teardown: the OS delivered the full
     * DOWN..UP trajectory (TouchInteractionService saw both endpoints) yet the
     * concurrent settle witness read 0; admission was not established.
     * An a11y node being queryable does not imply the window has input focus, and
     * teardown leaves disposed input channels and in-flight transitions. Poll
     * mCurrentFocus for this package; bounded to ~10s, result recorded. If focus
     * never reaches the app, fail with the last mCurrentFocus line - no retry, no
     * silent inject-anyway, no assertion change beyond this explicit failure.
     * Hypothesis under test, not an established root cause: run 41879111168's log
     * also shows witness 0 on drives that passed, so witness 0 does not prove the
     * stream was never admitted; this gate tells the two cases apart.
     */
    private fun awaitWindowFocus(tag: String, what: String, scenario: ActivityScenario<*>? = null) {
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        // Lifecycle prep only: bring the scenario activity back to RESUMED if it
        // was backgrounded. This is NOT proof of window foreground - the gate below
        // is the only focus verdict, and the run-41926221980 packet showed the
        // fixture fully visible while a stale lastANR focus line was misread (the
        // parser fix below is the real repair for that run). No dialog dismissal
        // here: an app ANR must never be hidden by a test (existing preflight
        // guard), and no artifact pixel showed a dialog during gesture cases.
        // The gate itself is unchanged: it polls mCurrentFocus and fails honestly.
        if (scenario != null && scenario.state != androidx.lifecycle.Lifecycle.State.RESUMED) {
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            val frontNote = "$tag: $what fixture activity moved back to RESUMED before focus gate"
            android.util.Log.i("CallGestureProof", frontNote)
            File(dir, "call-gesture-injection.txt").appendText(frontNote + "\n")
        }
        val deadline = System.currentTimeMillis() + 10_000
        var checks = 0
        var focused = false
        var focusDump = ""
        var focusLines: List<String> = emptyList()
        while (System.currentTimeMillis() < deadline) {
            checks++
            // Focus lives in the DISPLAYS section, not the windows section.
            // Plain "dumpsys window" prints the LAST ANR snapshot BEFORE the live
            // sections (AOSP android14 WMS default dump calls dumpLastANRLocked
            // first): run 41926221980's firstOrNull read a stale NexusLauncher
            // ANR line while the fixture held live focus. "dumpsys window windows"
            // omits lastANR but prints no focus fields at all on this build (run
            // 41948559243 raw dumps: only mTopFocusedDisplayId). "dumpsys window
            // displays" calls mRoot.dumpDisplayContents directly - no lastANR -
            // and DisplayContent.dump prints mCurrentFocus/mFocusedApp (AOSP
            // android14 DisplayContent ~3619). All mCurrentFocus lines (one per
            // display) are collected; no blind first/last selection. No pipe:
            // "|" goes to dumpsys as an argument (run 41891946713). If the field
            // is absent the gate fails below: unknown is not focused.
            focusDump = try {
                dev.executeShellCommand("dumpsys window displays")
            } catch (e: Exception) {
                ""
            }
            focusLines = focusDump.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("mCurrentFocus") }
                .toList()
            if (focusLines.any { it.contains(ins.targetContext.packageName) }) {
                focused = true
                break
            }
            Thread.sleep(250)
        }
        val note = "$tag: $what window focus " + (if (focused) "on app after $checks check(s)" else "NOT on app after $checks checks")
        android.util.Log.i("CallGestureProof", note)
        File(dir, "call-gesture-injection.txt").appendText(note + "\n")
        if (!focused) {
            // Persist the full raw dumps so a parser question never has to be
            // re-litigated from a single extracted line (nobody can run adb):
            // the window-displays dump the gate read, plus the activity record.
            val dumpName = "call-gesture-focus-dump-" + tag.replace(Regex("[^A-Za-z0-9-]"), "-") + ".txt"
            val activitiesName = "call-gesture-activities-dump-" + tag.replace(Regex("[^A-Za-z0-9-]"), "-") + ".txt"
            try {
                File(dir, dumpName).writeText(focusDump)
            } catch (_: Exception) {
            }
            val activitiesDump = try {
                dev.executeShellCommand("dumpsys activity activities")
            } catch (e: Exception) {
                "(dumpsys activity activities failed: " + (e.message ?: "") + ")"
            }
            try {
                File(dir, activitiesName).writeText(activitiesDump)
            } catch (_: Exception) {
            }
            throw AssertionError(
                "$tag: $what window focus never reached " + ins.targetContext.packageName +
                    " after $checks checks (~10s); mCurrentFocus line(s): " +
                    (if (focusLines.isEmpty()) "(no mCurrentFocus line in dumpsys window displays output - unknown, failing closed)" else focusLines.joinToString(" | ")) +
                    "; full raw dumps saved to " + dumpName + " and " + activitiesName,
            )
        }
    }

    private fun driveGesture(tag: String, desc: String, delta: Pair<Int, Int>, what: String, scenario: ActivityScenario<*>? = null) {
        awaitWindowFocus(tag, what, scenario)
        awaitQuietInput(tag, what)
        val startMarker = "CallGestureProof-drive-start-$tag-${System.nanoTime()}"
        android.util.Log.i("CallGestureProof", startMarker)
        // Settle witness, concurrent: a background thread samples the puck node's
        // bounds DURING the injection. A nonzero offset shows the drag moved the
        // node; a zero offset is NOT proof the stream was never admitted, because
        // a11y reads can stall behind injection (zero was recorded on drives that
        // passed, runs 41879111168 and 41901570247). Diagnostics only - the
        // caller's assertion is unchanged.
        val settleWitness = startSettleWitness(desc)
        val endMarker = "CallGestureProof-drive-end-$tag-${System.nanoTime()}"
        try {
            swipeFrom(desc, delta.first, delta.second)
        } finally {
            android.util.Log.i("CallGestureProof", endMarker)
            stopSettleWitness(settleWitness)
        }
        dev.waitForIdle(3000)
        Thread.sleep(1200)
        val inWindowChange = inWindowDeviceChange(startMarker, endMarker)
        val note = "$tag: $what single drive complete; in-window-device-change=$inWindowChange; settle-witness-max-offset-px=${settleWitness.maxOffset.get()}"
        android.util.Log.i("CallGestureProof", note)
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "call-gesture-injection.txt").appendText(note + "\n")
    }

    /**
     * Diagnostic only - never asserted. Drives the same drag as an explicit
     * MotionEvent train via ins.uiAutomation.injectInputEvent, the same injection
     * path UiDevice.swipe uses internally (InteractionController), but with a
     * press-hold after DOWN and slow first MOVEs: the working hypothesis for the
     * vertical-puck misses (run 41901570247 azure answer: focus on app, quiet
     * input, no in-window churn, full DOWN..UP delivered, no callback) is that
     * UiDevice.swipe's instant first move differs from a real hold-then-drag.
     * The outcome is recorded as shape-holdstart next to the swipe shape's outcome;
     * assertions stay bound to the swipe shape alone (no retry-to-pass).
     */
    private fun driveHoldStartDiagnostic(
        tag: String,
        desc: String,
        delta: Pair<Int, Int>,
        what: String,
        fired: java.util.concurrent.atomic.AtomicBoolean,
    ): Boolean {
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        fun note(text: String) {
            android.util.Log.i("CallGestureProof", text)
            File(dir, "call-gesture-injection.txt").appendText(text + "\n")
        }
        // The whole diagnostic is exception-safe: a failure anywhere in it (target
        // lookup, idle waits, sleeps, logging) must never mask the original swipe
        // verdict, so the body is wrapped and any throw is recorded, not rethrown.
        try {
        val obj = dev.wait(Until.findObject(By.desc(desc)), 4000)
        if (obj == null) {
            note("$tag: $what shape-holdstart=target-missing (diagnostic only, never asserted)")
            return false
        }
        val b = obj.visibleBounds
        val x = b.centerX().toFloat()
        val y = b.centerY().toFloat()
        dev.waitForIdle(2000)
        // DOWN, hold 400ms like a real finger settling on the puck, then 6 slow
        // first MOVEs over the first quarter of the drag, then a steady remainder.
        val (trainError, trainFailedInjects) = injectHoldStartTrain(x, y, delta)
        val error: String? = trainError
        val failedInjects = trainFailedInjects
        dev.waitForIdle(2000)
        Thread.sleep(900)
        val result = fired.get()
        val classification = when {
            error != null -> "error($error)"
            failedInjects > 0 -> "injection-failed($failedInjects events)"
            else -> result.toString()
        }
        note(
            "$tag: $what shape-holdstart=$classification " +
                "(diagnostic only, post-swipe on the same fixture state - not a fresh scenario; " +
                "hold 400ms, 6x80ms slow start, 50x20ms; never asserted)",
        )
        return result && error == null && failedInjects == 0
        } catch (e: Exception) {
            try {
                note("$tag: $what shape-holdstart=error(" + e.javaClass.simpleName + ": " + (e.message ?: "") + ") (diagnostic only, never asserted)")
            } catch (_: Exception) {
            }
            return false
        }
    }

    /**
     * Bundle incoming-call gesture proofs (user request 2026-10-31: distinct reference
     * layouts with the real answer motion per family). Each family is rendered via the
     * real BundleIncomingCallView; the reference answer/decline motion is driven with
     * UiDevice swipes and must fire the REAL onAccept/onDecline callbacks. A small drag
     * must snap back and fire nothing (negative control).
     */
    private fun runGestureCase(
        tag: String,
        bundle: CallBundleId,
        controlDesc: String,
        acceptDelta: Pair<Int, Int>,
        declineDelta: Pair<Int, Int>?,
        declineDesc: String? = null,
    ) {
        val accepted = java.util.concurrent.atomic.AtomicBoolean(false)
        val declined = java.util.concurrent.atomic.AtomicBoolean(false)
        val scenario = ActivityScenario.launch(MediaFixtureActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    ChatyTheme(ThemePresets.whatsappIosLight) {
                        BundleIncomingCallView(
                            bundleId = bundle,
                            session = incomingSession("Gesture Caller"),
                            onAccept = { accepted.set(true) },
                            onDecline = { declined.set(true) },
                        )
                    }
                }
            }
            assertTrue("$tag: control '$controlDesc' missing", dev.wait(Until.hasObject(By.desc(controlDesc)), 8000))
            shot("bundle-incoming-$tag")

            // Both callback flags are latched immediately after each original swipe;
            // assertions below read only these snapshots, never the shared atomics
            // after the diagnostics have run.
            driveGesture(tag, controlDesc, acceptDelta, "answer motion", scenario)
            val swipeAnswer = accepted.get()
            val swipeAnswerOpposite = declined.get()
            var swipeDecline: Boolean? = null
            var swipeDeclineOpposite: Boolean? = null
            if (declineDelta != null) {
                accepted.set(false); declined.set(false)
                driveGesture(tag, declineDesc ?: controlDesc, declineDelta, "decline motion", scenario)
                swipeDecline = declined.get()
                swipeDeclineOpposite = accepted.get()
            }
            // The latched swipe outcomes are logged BEFORE any diagnostic runs.
            val dirShapes = File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            val shapeNote = "$tag: shape-swipe answer=$swipeAnswer opposite=$swipeAnswerOpposite" +
                if (declineDelta != null) " decline=$swipeDecline opposite=$swipeDeclineOpposite" else ""
            android.util.Log.i("CallGestureProof", shapeNote)
            File(dirShapes, "call-gesture-injection.txt").appendText(shapeNote + "\n")
            // Diagnostics run after BOTH original swipes of the case so a committing
            // hold-start drive cannot change the state the decline swipe starts from.
            // Diagnostic only - recorded, never asserted (no retry-to-pass).
            accepted.set(false); declined.set(false)
            driveHoldStartDiagnostic(tag, controlDesc, acceptDelta, "answer motion", accepted)
            if (declineDelta != null) {
                accepted.set(false); declined.set(false)
                driveHoldStartDiagnostic(tag, declineDesc ?: controlDesc, declineDelta, "decline motion", declined)
            }
            assertTrue("$tag: reference answer motion did not fire onAccept", swipeAnswer)
            assertFalse("$tag: answer motion must not fire onDecline", swipeAnswerOpposite)
            if (declineDelta != null) {
                assertTrue("$tag: reference decline motion did not fire onDecline", swipeDecline == true)
                assertFalse("$tag: decline motion must not fire onAccept", swipeDeclineOpposite == true)
            }

            // Negative control: a small wiggle snaps back and triggers nothing.
            accepted.set(false); declined.set(false)
            val (wx, wy) = if (kotlin.math.abs(acceptDelta.first) >= kotlin.math.abs(acceptDelta.second)) 30 to 0 else 0 to 30
            swipeFrom(controlDesc, wx, wy)
            dev.waitForIdle(2000)
            Thread.sleep(900)
            assertFalse("$tag: small drag must not accept", accepted.get())
            assertFalse("$tag: small drag must not decline", declined.get())
            if (bundle == CallBundleId.NEXTCLOUD_TALK) {
                swipeFrom(controlDesc, 30, 0)
                dev.waitForIdle(2000)
                Thread.sleep(900)
                assertFalse("$tag: horizontal wiggle must not accept", accepted.get())
                assertFalse("$tag: horizontal wiggle must not decline", declined.get())
                dev.findObject(By.desc(controlDesc)).click()
                dev.waitForIdle(2000)
                assertTrue("$tag: true tap must still accept", accepted.get())
                assertFalse("$tag: true tap must not decline", declined.get())
                shot("bundle-nextcloud-tap-after-wiggles")
            }
        } catch (failure: Throwable) {
            // Preserve the failing window, not just the screenshot before injection.
            // Diagnostic only: no retries, relaxed thresholds or removed callback assertions.
            try { shot("bundle-incoming-$tag-failure") } catch (_: Throwable) {}
            try {
                val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                dev.dumpWindowHierarchy(File(dir, "bundle-incoming-$tag-failure.xml"))
            } catch (_: Throwable) {}
            throw failure
        } finally {
            scenario.close()
        }
    }

    /**
     * Controlled hold-start case (publisher request after run 41953943371): the
     * post-swipe hold-start diagnostic passed on the same fixture ~13s after two
     * failed swipes, which is uncontrolled evidence - not a fresh scenario, not
     * a first drive. This case drives the SAME hold-start train (identical
     * timings via injectHoldStartTrain) as the FIRST gesture on a FRESH
     * scenario, behind the same focus and quiet-input gates as the swipe cases,
     * and ASSERTS the outcome independently. The original UiDevice.swipe case
     * (vertical-reset-regression / azure_ring_vertical) stays asserted and
     * unchanged: no silent replacement, no rescue retry, no assertion
     * weakening. The comparison between the two shapes on fresh state is the
     * controlled experiment for the first-drive vertical-puck miss: run
     * 41953943371's answer miss is unexplained by recorded churn (first
     * InputReader reconfig landed 0.72s AFTER the drive ended) and is not an
     * immediate-launch race (drive started ~3.25s after the fixture settled);
     * the decline miss only coincided with Stylus reconfig (47.834-48.017) -
     * coincident evidence, not proof of OS cancellation.
     */
    private fun runHoldStartControlledCase(
        tag: String,
        bundle: CallBundleId,
        controlDesc: String,
        acceptDelta: Pair<Int, Int>,
        declineDelta: Pair<Int, Int>,
    ) {
        val accepted = java.util.concurrent.atomic.AtomicBoolean(false)
        val declined = java.util.concurrent.atomic.AtomicBoolean(false)
        val scenario = ActivityScenario.launch(MediaFixtureActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    ChatyTheme(ThemePresets.whatsappIosLight) {
                        BundleIncomingCallView(
                            bundleId = bundle,
                            session = incomingSession("Gesture Caller"),
                            onAccept = { accepted.set(true) },
                            onDecline = { declined.set(true) },
                        )
                    }
                }
            }
            assertTrue("$tag: control '$controlDesc' missing", dev.wait(Until.hasObject(By.desc(controlDesc)), 8000))
            // Both callback flags are latched immediately after each controlled
            // drive; assertions read only these snapshots.
            val holdAnswer = driveHoldStartControlled(tag, controlDesc, acceptDelta, "answer motion", accepted, scenario)
            val holdAnswerOpposite = declined.get()
            accepted.set(false); declined.set(false)
            val holdDecline = driveHoldStartControlled(tag, controlDesc, declineDelta, "decline motion", declined, scenario)
            val holdDeclineOpposite = accepted.get()
            assertTrue("$tag: controlled hold-start answer motion did not fire onAccept", holdAnswer)
            assertFalse("$tag: controlled hold-start answer motion must not fire onDecline", holdAnswerOpposite)
            assertTrue("$tag: controlled hold-start decline motion did not fire onDecline", holdDecline)
            assertFalse("$tag: controlled hold-start decline motion must not fire onAccept", holdDeclineOpposite)
        } catch (failure: Throwable) {
            // Preserve the failing window, not just the screenshot before injection.
            try { shot("bundle-incoming-$tag-failure") } catch (_: Throwable) {}
            try {
                val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                dev.dumpWindowHierarchy(File(dir, "bundle-incoming-$tag-failure.xml"))
            } catch (_: Throwable) {}
            throw failure
        } finally {
            scenario.close()
        }
    }

    /**
     * One asserted hold-start drive on fresh fixture state: same gates
     * (window focus, quiet input) and same evidence (settle witness, in-window
     * device-change window) as driveGesture, but the injection is the shared
     * hold-start train. Train errors and rejected events fail the case
     * explicitly; the callback verdict is returned for the caller to latch and
     * assert. No retries.
     */
    private fun driveHoldStartControlled(
        tag: String,
        desc: String,
        delta: Pair<Int, Int>,
        what: String,
        fired: java.util.concurrent.atomic.AtomicBoolean,
        scenario: ActivityScenario<*>? = null,
    ): Boolean {
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        awaitWindowFocus(tag, what, scenario)
        awaitQuietInput(tag, what)
        val obj = dev.wait(Until.findObject(By.desc(desc)), 4000)
            ?: throw AssertionError("$tag: $what controlled hold-start target '$desc' missing")
        val b = obj.visibleBounds
        val startMarker = "CallGestureProof-drive-start-$tag-${System.nanoTime()}"
        android.util.Log.i("CallGestureProof", startMarker)
        val settleWitness = startSettleWitness(desc)
        val endMarker = "CallGestureProof-drive-end-$tag-${System.nanoTime()}"
        val (error, failedInjects) = try {
            injectHoldStartTrain(b.centerX().toFloat(), b.centerY().toFloat(), delta)
        } finally {
            android.util.Log.i("CallGestureProof", endMarker)
            stopSettleWitness(settleWitness)
        }
        dev.waitForIdle(3000)
        Thread.sleep(1200)
        val inWindowChange = inWindowDeviceChange(startMarker, endMarker)
        val result = fired.get()
        val classification = when {
            error != null -> "error($error)"
            failedInjects > 0 -> "injection-failed($failedInjects events)"
            else -> result.toString()
        }
        val note = "$tag: $what controlled hold-start drive complete; in-window-device-change=$inWindowChange" +
            "; settle-witness-max-offset-px=${settleWitness.maxOffset.get()}; shape-holdstart-controlled=$classification"
        android.util.Log.i("CallGestureProof", note)
        File(dir, "call-gesture-injection.txt").appendText(note + "\n")
        if (error != null) throw AssertionError("$tag: $what controlled hold-start train errored: $error")
        if (failedInjects > 0) throw AssertionError("$tag: $what controlled hold-start train had $failedInjects rejected event(s)")
        return result
    }

    /** Real Samsung tap targets expose accessibility descriptions and callbacks. */
    @Test
    fun samsungReferenceTapButtonsFireRealCallbacks() {
        val accepted = java.util.concurrent.atomic.AtomicInteger()
        val declined = java.util.concurrent.atomic.AtomicInteger()
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                ChatyTheme(ThemePresets.monochromeDark) {
                    BundleIncomingCallView(CallBundleId.SAMSUNG_ONE_UI, incomingSession("Synthetic caller"),
                        onAccept = { accepted.incrementAndGet() }, onDecline = { declined.incrementAndGet() })
                }
            } }
            val answer = dev.wait(Until.findObject(By.desc("Answer call")), 5000) ?: throw AssertionError("Answer tap target missing")
            answer.click(); ins.waitForIdleSync()
            assertEquals(1, accepted.get()); assertEquals(0, declined.get())
            val decline = dev.wait(Until.findObject(By.desc("Decline call")), 5000) ?: throw AssertionError("Decline tap target missing")
            decline.click(); ins.waitForIdleSync()
            assertEquals(1, accepted.get()); assertEquals(1, declined.get())
            shot("samsung-reference-tap-buttons")
        }
    }

    @Test fun horizontalReferencePucksCancelAndCommit() {
        runGestureCase("google-latest-reference", CallBundleId.GOOGLE_PHONE_LATEST,
            controlDesc = "Answer or decline puck", acceptDelta = 430 to 0, declineDelta = -430 to 0)
    }

    @Test fun sharedIncomingBannerUsesSameCallbacks() {
        val accepted = java.util.concurrent.atomic.AtomicInteger()
        val declined = java.util.concurrent.atomic.AtomicInteger()
        ActivityScenario.launch(MediaFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                ChatyTheme(ThemePresets.monochromeDark) {
                    BundleIncomingCallView(CallBundleId.APPLE_IOS, incomingSession("Synthetic caller"),
                        onAccept = { accepted.incrementAndGet() }, onDecline = { declined.incrementAndGet() }, banner = true)
                }
            } }
            dev.wait(Until.findObject(By.text("Answer")), 5000)?.click() ?: throw AssertionError("Banner Answer missing")
            ins.waitForIdleSync(); assertEquals(1, accepted.get())
            dev.findObject(By.text("Decline")).click(); ins.waitForIdleSync(); assertEquals(1, declined.get())
            shot("shared-incoming-banner-reference")
        }
    }

    @Test
    fun verticalHoldStartControlledFiresRealCallbacks() {
        runHoldStartControlledCase(
            "vertical-holdstart-controlled", CallBundleId.AZURE_RING_VERTICAL,
            controlDesc = "Azure ring answer or decline puck",
            acceptDelta = 0 to 430, declineDelta = 0 to -430,
        )
    }

    @Test
    fun verticalPuckResetsAfterSuccessBeforeOppositeAndSmallDrag() {
        runGestureCase(
            "vertical-reset-regression", CallBundleId.AZURE_RING_VERTICAL,
            controlDesc = "Azure ring answer or decline puck",
            acceptDelta = 0 to 430, declineDelta = 0 to -430,
        )
    }

    @Test
    fun bundleIncomingReferenceGesturesFireRealCallbacks() {
        // Samsung is now separately tested as reference-approved TAP controls.
        // Azure Ring Vertical: drag the ring down = answer, up = decline.
        runGestureCase(
            "azure_ring_vertical", CallBundleId.AZURE_RING_VERTICAL,
            controlDesc = "Azure ring answer or decline puck", acceptDelta = 0 to 430, declineDelta = 0 to -430,
        )
        // Mono Chevron Dark: drag the puck down = answer, up = decline.
        runGestureCase(
            "mono_chevron_dark", CallBundleId.MONO_CHEVRON_DARK,
            controlDesc = "Mono chevron answer or decline puck", acceptDelta = 0 to 430, declineDelta = 0 to -430,
        )
        // Nextcloud Talk: swipe up on the accept circle (tap also accepted by design).
        runGestureCase(
            "nextcloud_talk", CallBundleId.NEXTCLOUD_TALK,
            controlDesc = "Accept call", acceptDelta = 0 to -430,
            declineDelta = null, declineDesc = null,
        )
    }

    @Test
    fun googleLatestHorizontalPuckHasCorrectSideAndSingleFire() {
        for (answer in listOf(true, false)) {
            val accepted = java.util.concurrent.atomic.AtomicInteger(0)
            val declined = java.util.concurrent.atomic.AtomicInteger(0)
            val scenario = ActivityScenario.launch(MediaFixtureActivity::class.java)
            try {
                scenario.onActivity { activity ->
                    activity.setContent {
                        ChatyTheme(ThemePresets.whatsappIosLight) {
                            val pending = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            BundleIncomingCallView(CallBundleId.GOOGLE_PHONE_LATEST,
                                incomingSession("Synthetic Caller"),
                                onAccept = { if (!pending.value) { pending.value = true; accepted.incrementAndGet() } },
                                onDecline = { if (!pending.value) { pending.value = true; declined.incrementAndGet() } },
                                actionPending = pending.value)
                        }
                    }
                }
                val accept = dev.wait(Until.findObject(By.text("Answer")), 8000)
                val decline = dev.wait(Until.findObject(By.text("Decline")), 8000)
                assertNotNull(accept)
                assertNotNull(decline)
                assertTrue("Decline stays left of Answer", decline.visibleBounds.centerX() < accept.visibleBounds.centerX())
                assertTrue("Horizontal puck is present", dev.wait(Until.hasObject(By.desc("Answer or decline puck")), 8000))
                shot("google-latest-horizontal-puck-single-fire")
                // Labels establish side layout but are not tap actions. Drive real puck.
                repeat(2) {
                    swipeFrom("Answer or decline puck", if (answer) 430 else -430, 0)
                    dev.waitForIdle(2000)
                    Thread.sleep(600) // settle/reset completes; pending still blocks repeat
                }
                assertEquals(if (answer) 1 else 0, accepted.get())
                assertEquals(if (answer) 0 else 1, declined.get())
            } finally { scenario.close() }
        }
    }

    /**
     * Apply-path routing proof: persisting a bundle id through the REAL
     * CallPresentationPreferencesStore (same SharedPreferences backend the picker
     * writes via Settings > Calls > Call Experience Bundles) must be readable by a
     * fresh store instance, and CallBundleId.fromId(persisted) must route to that
     * family's incoming view - the same fromId + BundleIncomingCallView routing
     * GlobalCallHost uses for a live incoming call.
     */
    @Test
    fun appliedBundlePersistsAndRoutesIncomingView() {
        val appCtx = ins.targetContext
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )
        val store = CallPresentationPreferencesStore(AndroidCallPresentationPrefsStorage(appCtx), scope)
        store.initialize()
        var deadline = System.currentTimeMillis() + 8000
        while (!store.isInitialized) {
            if (System.currentTimeMillis() > deadline) fail("store did not initialize")
            Thread.sleep(50)
        }
        val original = store.value.callExperienceBundleId
        try {
            store.setCallExperienceBundle("mono_chevron_dark")
            deadline = System.currentTimeMillis() + 8000
            while (store.value.callExperienceBundleId != "mono_chevron_dark") {
                if (System.currentTimeMillis() > deadline) fail("bundle id did not persist in time")
                Thread.sleep(50)
            }

            // A fresh store over the same backend proves the write persisted.
            val store2 = CallPresentationPreferencesStore(AndroidCallPresentationPrefsStorage(appCtx), scope)
            store2.initialize()
            deadline = System.currentTimeMillis() + 8000
            while (!store2.isInitialized) {
                if (System.currentTimeMillis() > deadline) fail("second store did not initialize")
                Thread.sleep(50)
            }
            assertEquals(
                "persisted bundle id must survive a fresh store instance",
                "mono_chevron_dark", store2.value.callExperienceBundleId,
            )

            // Route exactly like GlobalCallHost: fromId(prefs) -> BundleIncomingCallView.
            val accepted = java.util.concurrent.atomic.AtomicBoolean(false)
            val scenario = ActivityScenario.launch(MediaFixtureActivity::class.java)
            try {
                scenario.onActivity { activity ->
                    activity.setContent {
                        ChatyTheme(ThemePresets.whatsappIosLight) {
                            BundleIncomingCallView(
                                bundleId = CallBundleId.fromId(store2.value.callExperienceBundleId),
                                session = incomingSession("Persist Caller"),
                                onAccept = { accepted.set(true) },
                                onDecline = {},
                            )
                        }
                    }
                }
                assertTrue(
                    "persisted id must route to the Mono Chevron incoming view",
                    dev.wait(Until.hasObject(By.desc("Mono chevron answer or decline puck")), 8000),
                )
                assertEquals("Isolated fixture remains resumed", androidx.lifecycle.Lifecycle.State.RESUMED, scenario.state)
                shot("bundle-applied-mono-chevron")
                assertTrue("Mounted Mono view remains after capture", dev.wait(Until.hasObject(By.desc("Mono chevron answer or decline puck")), 8000))
                // Same gated + witnessed drive path as the other gesture cases: this
                // drive is the first injection of the whole class run and failed
                // ungated in run 41901570247 with no readiness evidence recorded.
                driveGesture("applied-bundle-routed", "Mono chevron answer or decline puck", 0 to 430, "answer motion", scenario)
                assertTrue("routed view's answer gesture must fire onAccept", accepted.get())
            } finally {
                scenario.close()
            }
        } finally {
            store.setCallExperienceBundle(original)
            Thread.sleep(500)
        }
    }

    /**
     * First-launch modals ("Enable App Lock?" over "Chaty changelog" over the demo
     * home) are dismissed via their real UI only. Two rounds: either modal can be
     * on top of the other, and a later one can appear after the earlier is closed.
     */
    private fun dismissFirstLaunchModals() {
        repeat(2) {
            if (dev.wait(Until.hasObject(By.text("Enable App Lock?")), 3000)) {
                val notNow = dev.wait(Until.findObject(By.text("Not now")), 3000)
                    ?: dev.wait(Until.findObject(By.text("Not Now")), 3000)
                assertNotNull("App Lock dismiss action missing", notNow)
                notNow.click()
            }
            if (dev.wait(Until.hasObject(By.text("Chaty changelog")), 3000)) {
                val close = dev.wait(Until.findObject(By.text("Close")), 5000)
                assertNotNull("changelog Close action missing", close)
                close.click()
                assertTrue("changelog did not close", dev.wait(Until.gone(By.text("Chaty changelog")), 5000))
            }
        }
    }

    /**
     * The committed SettingsRootScreen exposes each row title as plain text, but a
     * merged-summary row would expose it in the tile's contentDescription instead.
     * Accept either exposure; the row must still exist and be opened via its real UI.
     */
    private fun findSettingsRow(label: String, timeoutMs: Long): androidx.test.uiautomator.UiObject2? {
        dev.wait(Until.findObject(By.text(label)), timeoutMs)?.let { return it }
        dev.wait(Until.findObject(By.desc(label)), 1000)?.let { return it }
        dev.wait(Until.findObject(By.descContains(label)), 1000)?.let { return it }
        return dev.wait(Until.findObject(By.textContains(label)), 1000)
    }

    /**
     * Production-path bundle routing proof (user 2026-10-31: apply must take effect on
     * the actual call). Drives the REAL settings UI: app shell -> Settings -> Calls ->
     * Selected Call Bundle -> picker row tap (the production picker applies on row tap
     * via SettingsSubHost -> CallPresentationPreferencesStore.setCallExperienceBundle,
     * the app's own store instance, so GlobalCallHost reacts). Then a demo INCOMING
     * session through ServiceLocator.callSignaling forces the REAL GlobalCallHost to
     * render the persisted family's incoming view. Restores the original bundle after.
     * Coverage note: demo session via the app's own host, not a connected peer call.
     */
    @Test
    fun pickerSelectionAppliesAndRoutesLiveDemoIncomingCall() {
        ins.uiAutomation.grantRuntimePermission(ins.targetContext.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val appCtx = ins.targetContext
        var store: CallPresentationPreferencesStore? = null
        var original: String? = null
        try {
            val launch = appCtx.packageManager.getLaunchIntentForPackage(appCtx.packageName)!!
            appCtx.startActivity(launch)
            val api = com.example.chat.ServiceLocator.apiBackend
            assertTrue("isolated QA profile must not contain a real account", !api.isAuthenticated || api.isDemoSession)
            if (!api.isDemoSession) {
                assertTrue("welcome never appeared", dev.wait(Until.hasObject(By.textContains("Welcome")), 30000))
                dev.findObject(By.text("Log In")).click()
                assertTrue("demo card never appeared", dev.wait(Until.hasObject(By.textContains("Try the demo")), 10000))
                dev.findObject(By.textContains("Try the demo")).click()
                dismissFirstLaunchModals()
            }
            assertTrue("only the offline demo may be used", api.isDemoSession)
            // The modals are raised by app launch, not by demo sign-in: a version bump
            // re-raises the changelog on the already-demo path too, stacked under the
            // App Lock prompt over the home (run 41901570247 pixel + failure XML).
            dismissFirstLaunchModals()
            // ConversationRow exposes a summary, not child text. Check the exact sample
            // identity in both fresh and already-demo paths, never just the Chats tab.
            assertTrue("offline sample identity must remain visible",
                dev.wait(Until.hasObject(By.descStartsWith("Aisha (sample),")), 15000))
            assertTrue("app shell visible", dev.wait(Until.hasObject(By.text("Chats")), 20000))
            val liveStore = com.example.chat.ServiceLocator.callPresentationPrefs
            store = liveStore
            var deadline = System.currentTimeMillis() + 8000
            while (!liveStore.isInitialized) {
                if (System.currentTimeMillis() > deadline) fail("live store did not initialize")
                Thread.sleep(50)
            }
            original = liveStore.value.callExperienceBundleId

            var settingsEntry = dev.wait(Until.findObject(By.text("Settings")), 8000)
            if (settingsEntry == null) settingsEntry = dev.wait(Until.findObject(By.desc("Settings")), 4000)
            assertNotNull("Settings entry not found", settingsEntry)
            settingsEntry.click()
            dev.waitForIdle(3000)

            // Root settings calls the row Call Settings. Calls is the bottom-nav destination.
            var callsRow = findSettingsRow("Call Settings", 3000)
            repeat(12) {
                if (callsRow == null) {
                    // Short drag: the old 3/4 -> 1/3 swipe flung the list, and a fling
                    // can carry the viewport past a row without it ever resting on
                    // screen (the red run's hierarchy dump shows the list already at
                    // its bottom). Short drifts keep resting viewports overlapping.
                    dev.swipe(dev.displayWidth / 2, dev.displayHeight * 2 / 3,
                        dev.displayWidth / 2, dev.displayHeight / 2, 30)
                    dev.waitForIdle(1500)
                    callsRow = findSettingsRow("Call Settings", 1500)
                }
            }
            shot("bundle-live-demo-call-settings-route")
            assertNotNull("Call Settings row not found in Settings", callsRow)
            callsRow!!.click()
            dev.waitForIdle(3000)

            val tile = dev.wait(Until.findObject(By.text("Selected Call Bundle")), 8000)
            assertNotNull("Selected Call Bundle tile missing", tile)
            tile.click()
            dev.waitForIdle(3000)

            var row = dev.wait(Until.findObject(By.text("Mono Chevron Dark")), 2000)
            repeat(10) {
                if (row == null) {
                    dev.swipe(dev.displayWidth/2, dev.displayHeight*3/4, dev.displayWidth/2, dev.displayHeight/3, 40)
                    row = dev.wait(Until.findObject(By.text("Mono Chevron Dark")),1500)
                }
            }
            shot("bundle-picker-mono-visible-before-apply")
            assertNotNull("Mono Chevron Dark option missing from picker", row)
            row!!.click()
            dev.waitForIdle(3000)
            shot("bundle-picker-applied-mono-chevron")

            // Apply now opens a scope dialog (Calls only / Messages only / Both). This
            // proof exercises the call-experience path, so it picks Calls only. If the
            // dialog is absent the apply was direct; the persistence loop below is the
            // assertion either way.
            if (dev.wait(Until.hasObject(By.textContains("Choose which layouts to change")), 5000)) {
                val callsOnly = dev.wait(Until.findObject(By.text("Calls only")), 5000)
                assertNotNull("scope dialog missing its Calls only option", callsOnly)
                callsOnly!!.click()
                dev.waitForIdle(2000)
            }

            deadline = System.currentTimeMillis() + 8000
            while (liveStore.value.callExperienceBundleId != "mono_chevron_dark") {
                if (System.currentTimeMillis() > deadline) fail("picker Apply did not persist mono_chevron_dark")
                Thread.sleep(100)
            }

            dev.pressBack(); dev.waitForIdle(2000)
            dev.pressBack(); dev.waitForIdle(2000)
            dev.pressBack(); dev.waitForIdle(2000)

            val calls = com.example.chat.ServiceLocator.callSignaling
            calls.startDemoCall("synthetic-bundle-peer", "Bundle Route Caller", null, null, isVideo = false, isOutgoing = false)
            assertTrue(
                "GlobalCallHost must render the persisted Mono Chevron incoming view",
                dev.wait(Until.hasObject(By.desc("Mono chevron answer or decline puck")), 12000),
            )
            shot("bundle-live-demo-mono-chevron")

            swipeFrom("Mono chevron answer or decline puck", 0, -430)
            deadline = System.currentTimeMillis() + 8000
            val declinedCallId = calls.currentSession?.callId
            while (calls.currentSession?.state != com.example.chat.data.services.CallSessionState.declined) {
                if (System.currentTimeMillis() > deadline) fail("demo session did not reach declined after gesture: ${calls.currentSession?.state}")
                Thread.sleep(100)
            }
            assertNotNull("Declined demo keeps its terminal record until next session", declinedCallId)
            assertEquals(declinedCallId, calls.currentSession?.callId)
            assertTrue("Decline must remove rendered incoming control", dev.wait(Until.gone(By.desc("Mono chevron answer or decline puck")), 5000))
            assertFalse("Declined call must not remain active", calls.hasActiveSession)
            shot("bundle-live-demo-declined-state-and-dismissal")
        } catch (failure: Throwable) {
            // Preserve the exact window at any entry/navigation assertion failure.
            try { shot("bundle-live-demo-failure") } catch (_: Throwable) {}
            try {
                val dir = File(appCtx.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                dev.dumpWindowHierarchy(File(dir, "bundle-live-demo-failure.xml"))
            } catch (_: Throwable) {}
            throw failure
        } finally {
            val prior = original
            if (prior != null && com.example.chat.ServiceLocator.apiBackend.isDemoSession) {
                store?.setCallExperienceBundle(prior)
                Thread.sleep(500)
            }
        }
    }
}
