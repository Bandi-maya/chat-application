package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.ChatyColorPickerModal
import com.example.chat.ui.kit.ColorPickerLogic
import com.example.chat.ui.kit.MorphingSegmentItem
import com.example.chat.ui.kit.MorphingSegmentedControl
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Targeted semantics proof for the writer-E accessibility fixes. Isolated
 * presentational controls in the fixture activity; no network, no saved state.
 * Each test dumps PNG + window-hierarchy XML next to the assertions so the
 * semantics tree is provable from the run artifacts.
 *
 * The controls expose label + role + selection + the single click action on ONE
 * node via clearAndSetSemantics (the CameraCaptureModeButton pattern). Selection
 * uses Role.Tab because Compose maps SemanticsProperties.Selected to
 * AccessibilityNodeInfo.isSelected only for Role.Tab - other roles map it to
 * isChecked - and the already-selected tab is natively advertised as selected,
 * not clickable (AndroidComposeViewAccessibilityDelegateCompat).
 *
 *  1. backButtonWhole48dpTargetIsClickable: the whole 48dp ChatyBackButton area is
 *     one node (desc "Back", clickable, >= 48dp bounds, tap fires).
 *  2. colorSwatchesLabeled48dpWithSelectedState: every preset swatch exposes
 *     "Preset color <hex>", >= 48dp, actionable or selected, and the active color
 *     is the one selected swatch; tapping another swatch moves the selection.
 *  3. segmentedControlSegments48dpWithSelectedState: segments expose their label
 *     and selected state, the unselected segment is clickable and >= 48dp tall,
 *     and tapping it moves the selection.
 */
@RunWith(AndroidJUnit4::class)
class ChatyKitSemanticsProofTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val outDir by lazy {
        File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    }

    private fun capture(name: String) {
        instrumentation.uiAutomation.takeScreenshot()?.let { bmp ->
            File(outDir, "chatykit-$name.png").outputStream().use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bmp.recycle()
        }
        device.dumpWindowHierarchy(File(outDir, "chatykit-$name.xml"))
    }

    @Test fun backButtonWhole48dpTargetIsClickable() {
        val back = AtomicInteger()
        var pixelsPerDp = 1f
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    pixelsPerDp = LocalDensity.current.density
                    ChatyTheme(ThemePresets.monochromeDark) {
                        Surface(Modifier.fillMaxSize()) {
                            Column { ChatyBackButton(onPressed = { back.incrementAndGet() }) }
                        }
                    }
                }
            }
            assertTrue("Back action missing", device.wait(Until.hasObject(By.desc("Back")), 10000))
            val nodes = device.findObjects(By.desc("Back"))
            device.waitForIdle()
            capture("back")
            assertEquals("Back must be exactly one node", 1, nodes.size)
            val node = nodes[0]
            assertTrue("Back must expose one clickable target", node.isClickable)
            val bounds = node.visibleBounds
            assertTrue("Back width under 48dp: ${bounds.width()}", bounds.width() + 1 >= 48f * pixelsPerDp)
            assertTrue("Back height under 48dp: ${bounds.height()}", bounds.height() + 1 >= 48f * pixelsPerDp)
            node.click()
            device.waitForIdle()
            assertEquals("Back tap did not fire", 1, back.get())
        }
    }

    @Test fun colorSwatchesLabeled48dpWithSelectedState() {
        val selected = ColorPickerLogic.presetSwatches.first()
        var pixelsPerDp = 1f
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    pixelsPerDp = LocalDensity.current.density
                    ChatyTheme(ThemePresets.monochromeDark) {
                        Surface(Modifier.fillMaxSize()) {
                            ChatyColorPickerModal(
                                title = "Accent color",
                                currentColor = selected,
                                onColorSelected = {},
                                onDismiss = {},
                            )
                        }
                    }
                }
            }
            assertTrue(
                "Swatches missing",
                device.wait(Until.hasObject(By.descContains("Preset color")), 10000),
            )
            device.waitForIdle()
            capture("swatches")
            val swatches = device.findObjects(By.descContains("Preset color"))
            assertEquals("Swatch count mismatch", ColorPickerLogic.presetSwatches.size, swatches.size)
            var selectedCount = 0
            for (swatch in swatches) {
                // Native tab contract: the already-selected swatch is advertised as
                // selected, not clickable; every other swatch is clickable.
                assertTrue(
                    "Swatch must be actionable or selected: ${swatch.contentDescription}",
                    swatch.isClickable || swatch.isSelected,
                )
                val bounds = swatch.visibleBounds
                assertTrue(
                    "Swatch target under 48dp: ${swatch.contentDescription}",
                    bounds.width() + 1 >= 48f * pixelsPerDp && bounds.height() + 1 >= 48f * pixelsPerDp,
                )
                if (swatch.isSelected) selectedCount++
            }
            assertEquals("Exactly the active swatch must be selected", 1, selectedCount)

            // Tapping an unselected swatch moves the selection through the same single node.
            val targetDesc = "Preset color ${ColorPickerLogic.toHex(ColorPickerLogic.presetSwatches[1])}"
            val target = device.findObject(By.desc(targetDesc)) ?: throw AssertionError("Second swatch missing")
            assertTrue("Unselected swatch must be clickable", target.isClickable)
            target.click()
            device.waitForIdle()
            capture("swatches-after-tap")
            val tapped = device.findObject(By.desc(targetDesc)) ?: throw AssertionError("Swatch gone after tap")
            assertTrue("Tapped swatch must become selected", tapped.isSelected)
            assertEquals(
                "Selection must stay exclusive after tap",
                1,
                device.findObjects(By.descContains("Preset color")).count { it.isSelected },
            )
        }
    }

    @Test fun segmentedControlSegments48dpWithSelectedState() {
        val chosen = AtomicInteger(-1)
        var pixelsPerDp = 1f
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    pixelsPerDp = LocalDensity.current.density
                    ChatyTheme(ThemePresets.monochromeDark) {
                        // Column wrapper: a direct child of Surface(fillMaxSize) receives
                        // tight full-height constraints that clamp the bar's own
                        // height(56dp) up to the screen. The Column passes loose
                        // constraints, so the fixture shows the normal 56dp layout.
                        Column {
                            // Stateful fixture: taps must recompose the control with the
                            // new selection, like real callers do.
                            var current by remember { mutableStateOf(0) }
                            MorphingSegmentedControl(
                                items = listOf(
                                    MorphingSegmentItem(0, "Bubbles"),
                                    MorphingSegmentItem(1, "Motion"),
                                ),
                                selectedValue = current,
                                onSelected = { chosen.set(it); current = it },
                            )
                        }
                    }
                }
            }
            assertTrue("Segments missing", device.wait(Until.hasObject(By.text("Bubbles")), 10000))
            val first = device.findObject(By.text("Bubbles")) ?: throw AssertionError("First segment missing")
            val second = device.findObject(By.text("Motion")) ?: throw AssertionError("Second segment missing")
            device.waitForIdle()
            capture("segments")
            // Native tab contract: the already-selected segment is advertised as
            // selected, not clickable; the unselected segment is the clickable one.
            assertTrue("Selected segment must expose selected state", first.isSelected)
            assertTrue("Unselected segment must not expose selected state", !second.isSelected)
            assertTrue("Unselected segment must be clickable", second.isClickable)
            val bounds = second.visibleBounds
            assertTrue("Segment hit height under 48dp: ${bounds.height()}", bounds.height() + 1 >= 48f * pixelsPerDp)
            second.click()
            device.waitForIdle()
            assertEquals("Segment tap did not fire", 1, chosen.get())
            capture("segments-after-tap")
            val firstAfter = device.findObject(By.text("Bubbles")) ?: throw AssertionError("First segment gone")
            val secondAfter = device.findObject(By.text("Motion")) ?: throw AssertionError("Second segment gone")
            assertTrue("Tapped segment must become selected", secondAfter.isSelected)
            assertTrue("Previous segment must become unselected", !firstAfter.isSelected)
        }
    }
}
