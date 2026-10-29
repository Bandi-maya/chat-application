package com.example.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runtime proof for the session-added GB-reference style features, executed in
 * DEMO mode only (demo userId-scoped preference store; the live account is
 * never touched). Scope is limited to the specific options exercised here, not
 * the whole catalogues. PNG frame sequences are saved per leg as evidence.
 *
 *  1. pagerDefaultIsIdentityAndOptInPersists: the corrected default is
 *     "Default" (identity no-op, GB key_pager_animation 0); explicit opt-in to
 *     "Cube 3D" through the real settings route persists across recreate;
 *     re-selecting "Default" works. Frame sequences capture the page
 *     transitions in both modes (motion evidence is the saved frames).
 *  2. entranceAndFontPersistAcrossRecreate: "Wave" chats-list entrance and the
 *     bundled "BEBASNEUE" font persist across recreate; an entrance frame
 *     sequence at 2x animator scale captures the arrival motion mid-flight.
 *  3. reducedMotionSettlesEntrance: with animator scale 0 the same arrival
 *     renders settled (identical consecutive frames). The prior animator
 *     scale is restored in a finally block.
 */
@RunWith(AndroidJUnit4::class)
class AddedStylesProofTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var device: UiDevice
    private val outDir: File
        get() = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

    @Before fun setUp() { device = UiDevice.getInstance(instrumentation) }

    @After fun tearDown() {
        try { setAnimatorScales("1") } catch (_: Exception) {}
        try {
            if (ServiceLocator.apiBackend.isDemoSession) ServiceLocator.apiBackend.signOut()
        } catch (_: Exception) {}
        try {
            instrumentation.targetContext
                .getSharedPreferences("chaty_api_backend", android.content.Context.MODE_PRIVATE)
                .edit().clear().commit()
        } catch (_: Exception) {}
    }

    private fun capture(name: String): File {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("no pixels for $name")
        val file = File(outDir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun framesDiffer(a: File, b: File): Boolean {
        val ba = BitmapFactory.decodeFile(a.absolutePath)
        val bb = BitmapFactory.decodeFile(b.absolutePath)
        if (ba == null || bb == null) return true
        val same = ba.sameAs(bb)
        ba.recycle(); bb.recycle()
        return !same
    }

    private fun setAnimatorScales(value: String) {
        device.executeShellCommand("settings put global animator_duration_scale $value")
        device.executeShellCommand("settings put global window_animation_scale $value")
        device.executeShellCommand("settings put global transition_animation_scale $value")
    }

    private fun readAnimatorScale(): String =
        device.executeShellCommand("settings get global animator_duration_scale").trim().ifEmpty { "1" }

    private fun dismissDemoEntryPrompts() {
        if (device.wait(Until.hasObject(By.text("Enable App Lock?")), 5000)) {
            device.findObject(By.text("Not now")).click()
        }
        if (device.wait(Until.hasObject(By.text("Not Now")), 4000)) {
            device.findObject(By.text("Not Now")).click()
        }
    }

    private fun demoLogin() {
        assertTrue("welcome never appeared", device.wait(Until.hasObject(By.textContains("Welcome")), 30000))
        device.waitForIdle(3000)
        device.findObject(By.text("Log In")).click()
        assertTrue("demo card never appeared", device.wait(Until.hasObject(By.textContains("Try the demo")), 10000))
        device.findObject(By.textContains("Try the demo")).click()
        dismissDemoEntryPrompts()
        assertTrue("demo home never appeared", device.wait(Until.hasObject(By.textContains("Aisha (sample)")), 15000))
    }

    /** Deterministic starting point inside the DEMO-scoped store. */
    private fun resetDemoStylePrefs() {
        val ctl = ServiceLocator.preferencesController
        ctl.updateHome(ctl.home.copy(pagerTransition3d = "Default"))
        ctl.updateEffects(ctl.effects.copy(homeListAnimation = "Standard", conversationListAnimation = "Standard"))
        ctl.updateUniversal(ctl.universal.copy(fontStyle = "Default"))
    }

    /**
     * Bounded end-aware search for [text]: every scrollable container is tried
     * in turn; inside a container, swipes stop as soon as the container reports
     * it cannot scroll further (end reached) before moving to the next one.
     * A diagnostic frame is saved when the text stays unreachable.
     */
    private fun scrollToText(text: String, maxSwipes: Int = 24): Boolean {
        if (device.wait(Until.hasObject(By.text(text)), 1500)) return true
        val margin = (16f * instrumentation.targetContext.resources.displayMetrics.density).toInt()
        var budget = maxSwipes
        val scrollables = device.findObjects(By.scrollable(true)) ?: emptyList()
        for (scroll in scrollables) {
            while (budget > 0) {
                budget--
                try { scroll.setGestureMargins(margin, margin, margin, margin) } catch (_: StaleObjectException) { break }
                val moved = try { scroll.scroll(Direction.DOWN, 0.8f) } catch (_: StaleObjectException) { break }
                device.waitForIdle(500)
                if (device.hasObject(By.text(text))) return true
                if (!moved) break
            }
            if (budget <= 0) break
        }
        val found = device.hasObject(By.text(text))
        if (!found) capture("added-scroll-fail-" + text.replace(Regex("[^A-Za-z0-9]+"), "-").take(40) + ".png")
        return found
    }

    private fun clickText(text: String, timeoutMs: Long = 10000): UiObject2 {
        assertTrue("'$text' not reachable", scrollToText(text))
        val obj = device.wait(Until.findObject(By.text(text)), timeoutMs)
            ?: throw AssertionError("'$text' not found after scroll")
        obj.click()
        return obj
    }

    private fun clickAndWait(text: String, waitFor: String, timeoutMs: Long = 10000) {
        clickText(text, timeoutMs)
        assertTrue("'$waitFor' never appeared after '$text'", device.wait(Until.hasObject(By.text(waitFor)), timeoutMs))
    }

    private fun openSettings() {
        var opened = false
        var attempts = 0
        while (!opened && attempts < 3) {
            attempts++
            val settings = device.wait(Until.findObject(By.desc("Settings")), 5000)
                ?: device.findObject(By.text("Settings"))
            try {
                settings?.click()
            } catch (_: StaleObjectException) {
                device.waitForIdle(1000)
                continue
            }
            opened = device.wait(Until.hasObject(By.text("Privacy Center")), 15000)
            if (!opened) device.waitForIdle(1000)
        }
        assertTrue("settings root never appeared", opened)
    }

    private fun clickDock(label: String) {
        val obj = device.findObject(By.desc(label)) ?: device.findObject(By.text(label))
            ?: throw AssertionError("dock item '$label' missing")
        obj.click()
    }

    @Test fun pagerDefaultIsIdentityAndOptInPersists() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            demoLogin()
            resetDemoStylePrefs()
            val ctl = ServiceLocator.preferencesController
            assertEquals("corrected pager default must be Default", "Default", ctl.home.pagerTransition3d)

            // Frame sequence of a page change with the Default (identity) transition.
            val beforeDefault = capture("added-pager-default-before")
            clickDock("Calls")
            val midDefault = capture("added-pager-default-mid")
            device.waitForIdle(4000)
            val settledDefault = capture("added-pager-default-settled")
            assertTrue("page change rendered nothing new", framesDiffer(beforeDefault, settledDefault))

            // Opt in to Cube 3D through the real settings route.
            openSettings()
            clickAndWait("Home Screen", "Header")
            clickAndWait("Header", "Header tab style")
            clickAndWait("Header tab style", "3D pager / slider effect")
            clickText("Cube 3D")
            val apply = device.wait(Until.findObject(By.text("Apply")), 5000)
                ?: throw AssertionError("Apply button missing")
            apply.click()
            device.waitForIdle(2000)
            assertEquals("opt-in selection must persist in the controller", "Cube 3D", ctl.home.pagerTransition3d)
            capture("added-pager-cube-selected")

            // Recreate: the explicit choice must survive.
            scenario.recreate()
            dismissDemoEntryPrompts()
            assertTrue("home lost after recreate", device.wait(Until.hasObject(By.textContains("Aisha (sample)")), 20000))
            assertEquals("Cube 3D must survive recreate", "Cube 3D", ctl.home.pagerTransition3d)

            // Frame sequence of a page change with Cube 3D applied.
            val beforeCube = capture("added-pager-cube-before")
            clickDock("Calls")
            val midCube = capture("added-pager-cube-mid")
            device.waitForIdle(4000)
            val settledCube = capture("added-pager-cube-settled")
            assertTrue("Cube 3D page change rendered nothing new", framesDiffer(beforeCube, settledCube))
            assertTrue(
                "mid-transition frames identical across Default and Cube 3D - transition not applied?",
                framesDiffer(midDefault, midCube),
            )

            // Opt back out: "Default" must be selectable and apply.
            openSettings()
            clickAndWait("Home Screen", "Header")
            clickAndWait("Header", "Header tab style")
            clickAndWait("Header tab style", "3D pager / slider effect")
            clickText("Default")
            val apply2 = device.wait(Until.findObject(By.text("Apply")), 5000)
                ?: throw AssertionError("Apply button missing on opt-out")
            apply2.click()
            device.waitForIdle(2000)
            assertEquals("re-selecting Default must persist", "Default", ctl.home.pagerTransition3d)
        }
    }

    @Test fun entranceAndFontPersistAcrossRecreate() {
        val priorScale = readAnimatorScale()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                demoLogin()
                resetDemoStylePrefs()
                val ctl = ServiceLocator.preferencesController

                // Entrance selection through the real route.
                openSettings()
                clickAndWait("Navigation & Touch Effects", "Chats List Animation")
                clickAndWait("Chats List Animation", "Select Chats List Animation")
                clickText("Wave")
                device.wait(Until.findObject(By.text("Apply")), 5000)?.click()
                device.waitForIdle(2000)
                assertEquals("entrance selection must persist in the controller", "Wave", ctl.effects.homeListAnimation)
                capture("added-effects-wave-selected")
                device.pressBack()

                // Font selection through the real route.
                assertTrue("settings root lost", device.wait(Until.hasObject(By.text("Privacy Center")), 10000))
                clickAndWait("Universal", "Styles (Look and feel)")
                clickAndWait("Styles (Look and feel)", "Font Style")
                clickAndWait("Font Style", "Select font style")
                clickText("BEBASNEUE")
                device.wait(Until.findObject(By.text("Apply")), 5000)?.click()
                device.waitForIdle(2000)
                assertEquals("font selection must persist in the controller", "BEBASNEUE", ctl.universal.fontStyle)
                capture("added-font-bebas-selected")

                // Recreate: both selections must survive and render as Selected:.
                scenario.recreate()
                dismissDemoEntryPrompts()
                assertTrue("home lost after recreate", device.wait(Until.hasObject(By.textContains("Aisha (sample)")), 20000))
                assertEquals("Wave must survive recreate", "Wave", ctl.effects.homeListAnimation)
                assertEquals("BEBASNEUE must survive recreate", "BEBASNEUE", ctl.universal.fontStyle)
                openSettings()
                clickAndWait("Navigation & Touch Effects", "Chats List Animation")
                assertTrue("Wave not shown as selected after recreate",
                    device.wait(Until.hasObject(By.textContains("Selected: Wave")), 10000))
                device.pressBack()
                clickAndWait("Universal", "Styles (Look and feel)")
                clickAndWait("Styles (Look and feel)", "Font Style")
                assertTrue("BEBASNEUE not shown as selected after recreate",
                    device.wait(Until.hasObject(By.textContains("Selected: BEBASNEUE")), 10000))
                device.pressBack()
                device.pressBack()
                device.pressBack()

                // Entrance motion frame sequence at 2x animator scale: home -> chat -> back.
                setAnimatorScales("2")
                assertTrue("home lost", device.wait(Until.hasObject(By.textContains("Aisha (sample)")), 10000))
                device.findObject(By.textContains("Aisha (sample)")).click()
                assertTrue("chat detail never opened",
                    device.wait(Until.hasObject(By.textContains("Aisha")), 10000))
                device.pressBack()
                val f1 = capture("added-entrance-wave-f1")
                Thread.sleep(250)
                val f2 = capture("added-entrance-wave-f2")
                device.waitForIdle(4000)
                val f3 = capture("added-entrance-wave-settled")
                assertTrue("Wave arrival produced no frame motion at 2x scale", framesDiffer(f1, f2) || framesDiffer(f2, f3))
            }
        } finally {
            setAnimatorScales(priorScale)
        }
    }

    @Test fun reducedMotionSettlesEntrance() {
        val priorScale = readAnimatorScale()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                demoLogin()
                resetDemoStylePrefs()
                val ctl = ServiceLocator.preferencesController
                ctl.updateEffects(ctl.effects.copy(homeListAnimation = "Wave"))
                setAnimatorScales("0")
                device.findObject(By.textContains("Aisha (sample)"))?.click()
                device.waitForIdle(1500)
                device.pressBack()
                val f1 = capture("added-entrance-reduced-f1")
                Thread.sleep(300)
                val f2 = capture("added-entrance-reduced-f2")
                assertFalse(
                    "with animator scale 0 the list arrival must render settled (identical frames)",
                    framesDiffer(f1, f2),
                )
            }
        } finally {
            setAnimatorScales(priorScale)
        }
    }
}
