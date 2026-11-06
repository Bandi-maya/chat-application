package com.example.chat

import android.content.Intent
import android.graphics.Rect
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Whole-app mini/floating-window readiness: production chat screens at compact widths.
 * Fixture-hosted (debug build, synthetic data, no account/backend). Proves no clipping at
 * mini-window widths and fixture state retention; OEM small-window acceptance itself still
 * requires a physical device.
 */
@RunWith(AndroidJUnit4::class)
class CompactWindowProofTest {

    private data class Case(
        val screen: String,
        val widthDp: Int,
        val fontScale: Float = 1f,
        val dark: Boolean = false,
        val landscape: Boolean = false,
        val mustSee: List<String>,
    )

    @Test
    fun chatScreensStayInsideCompactWidths() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val cases = listOf(
            Case("home", 320, mustSee = listOf("Alice Rao", "Weekend Trek", "Bob Verma")),
            Case("home", 320, fontScale = 1.3f, dark = true, mustSee = listOf("Alice Rao", "Weekend Trek")),
            Case("detail", 320, mustSee = listOf("See you at 6!")),
            Case("home", 480, landscape = true, mustSee = listOf("Alice Rao", "Bob Verma")),
        )
        cases.forEach { case ->
            val tag = "${case.screen}-${case.widthDp}dp-font${case.fontScale}-${if (case.dark) "dark" else "light"}-${if (case.landscape) "land" else "port"}"
            val intent = Intent(instrumentation.targetContext, CompactWindowFixtureActivity::class.java)
                .putExtra("screen", case.screen).putExtra("widthDp", case.widthDp)
                .putExtra("fontScale", case.fontScale).putExtra("dark", case.dark)
                .putExtra("landscape", case.landscape)
            ActivityScenario.launch<CompactWindowFixtureActivity>(intent).use { scenario ->
                instrumentation.waitForIdleSync()
                var window = Rect()
                val orientationDeadline = System.currentTimeMillis() + 8000
                var correctOrientation = false
                while (!correctOrientation && System.currentTimeMillis() < orientationDeadline) {
                    scenario.onActivity { a ->
                        val decor = a.window.decorView
                        correctOrientation = if (case.landscape) decor.width > decor.height else decor.height > decor.width
                        val origin = IntArray(2); decor.getLocationOnScreen(origin)
                        val insets = ViewCompat.getRootWindowInsets(decor)?.getInsets(
                            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                            ?: error("Missing actual window insets")
                        window = Rect(origin[0] + insets.left, origin[1] + insets.top,
                            origin[0] + decor.width - insets.right, origin[1] + decor.height - insets.bottom)
                    }
                    if (!correctOrientation) Thread.sleep(100)
                }
                assertTrue("$tag: actual decor orientation settled", correctOrientation)
                val dir = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                // Capture before assertions; never crop or normalize away clipping.
                device.takeScreenshot(java.io.File(dir, "compact-window-$tag.png"))
                device.dumpWindowHierarchy(java.io.File(dir, "compact-window-$tag.xml"))
                case.mustSee.forEach { label ->
                    var node = device.wait(Until.findObject(By.text(label)), 5000)
                        ?: device.findObject(By.textContains(label))
                    if (node == null && case.screen == "home") {
                        // Compact height is allowed to scroll. Prove the real production list
                        // reaches the expected row, never replace it with a direct state call.
                        val lists = device.findObjects(By.scrollable(true).pkg(instrumentation.targetContext.packageName))
                        assertEquals("$tag: one production chat list owns vertical scrolling", 1, lists.size)
                        var moves = 0
                        while (node == null && moves++ < 6) {
                            val list = device.findObject(By.scrollable(true).pkg(instrumentation.targetContext.packageName))
                                ?: error("$tag: production scroll owner disappeared")
                            val canContinue = list.scroll(Direction.DOWN, 0.7f)
                            device.waitForIdle()
                            // scroll() false can mean that this gesture reached the end, not
                            // that it made no movement. In Next26 pixels Bob is visible while
                            // the cached accessibility tree still reports the pre-scroll rows.
                            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
                            instrumentation.uiAutomation.rootInActiveWindow?.refresh()
                            node = device.wait(Until.findObject(By.text(label)), 1500)
                                ?: device.findObject(By.textContains(label))
                            if (!canContinue && node == null) break
                        }
                        device.takeScreenshot(java.io.File(dir, "compact-window-$tag-after-scroll.png"))
                        device.dumpWindowHierarchy(java.io.File(dir, "compact-window-$tag-after-scroll.xml"))
                    }
                    assertNotNull("$tag: expected content '$label' exists", node)
                    val b = (node ?: error("$tag: missing row after bounded reachability check")).visibleBounds
                    assertTrue("$tag: '$label' inside window horizontally $b/$window",
                        b.left >= window.left - 1 && b.right <= window.right + 1)
                    assertTrue("$tag: '$label' not zero-sized $b", b.width() > 0 && b.height() > 0)
                    assertTrue("$tag: '$label' inside usable window vertically $b/$window",
                        b.top >= window.top - 1 && b.bottom <= window.bottom + 1)
                }
            }
            device.setOrientationNatural()
        }
    }

    @Test
    fun fixtureStateSurvivesRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val intent = Intent(instrumentation.targetContext, CompactWindowFixtureActivity::class.java)
            .putExtra("screen", "home").putExtra("widthDp", 320)
        ActivityScenario.launch<CompactWindowFixtureActivity>(intent).use { scenario ->
            instrumentation.waitForIdleSync()
            // Click the probe's label (unique text); the focused EditText is then the probe,
            // unambiguous even if the home screen has its own search field.
            val label = device.wait(Until.findObject(By.text("Fixture state probe")), 5000)
            assertNotNull("probe label exists", label)
            label.click()
            instrumentation.waitForIdleSync()
            val edit = device.findObject(By.focused(true))
            assertNotNull("probe field focused after label tap", edit)
            edit.text = "draft-123"
            instrumentation.waitForIdleSync()
            scenario.recreate()
            instrumentation.waitForIdleSync()
            val after = device.wait(Until.findObject(By.text("draft-123")), 5000)
            assertNotNull("rememberSaveable probe text survives recreation", after)
        }
    }
}
