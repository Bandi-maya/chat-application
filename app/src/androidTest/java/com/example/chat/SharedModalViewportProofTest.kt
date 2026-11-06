package com.example.chat

import android.graphics.Rect
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ChatySelectionModal
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real shared modal, local draft only. No backend, preferences, or real credentials. */
@RunWith(AndroidJUnit4::class)
class SharedModalViewportProofTest {
    @Test fun portraitLargeTextFooterClearsIme() = run(false)
    @Test fun landscapeLargeTextFooterClearsIme() = run(true)
    @Test fun landscapeRtlLargeTextBodyAndActionsClearIme() = run(true, rtl = true)
    @Test fun landscapeLongLabelsBodyAndActionsClearIme() = run(true, longLabels = true)
    @Test fun outsideTapDiscardsDraft() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                    var modalOpen by remember { mutableStateOf(true) }
                    if (modalOpen) ChatySelectionModal("Outside proof", { modalOpen = false }, { error("Must not apply") }) {
                        Text("Uncommitted draft", Modifier.padding(12.dp))
                    } else Text("Outside dismissed without apply", Modifier.safeDrawingPadding())
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("Outside proof")), 10000))
            val title = device.findObject(By.text("Outside proof")).visibleBounds
            // This short centered card leaves visible space above it, below the status bar.
            device.click(device.displayWidth / 2, title.top / 2)
            assertTrue("Outside tap dismisses without applying", device.wait(Until.hasObject(By.text("Outside dismissed without apply")), 5000))
        }
    }

    private fun run(landscape: Boolean, rtl: Boolean = false, longLabels: Boolean = false) {
        val modalTitle = if (longLabels) "A longer selector title for narrow available space" else "Viewport proof"
        val applyText = if (longLabels) "Apply changes" else "Apply"
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val dialogView = AtomicReference<View?>()
        val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val mode = (if (landscape) "landscape" else "portrait") + (if (rtl) "-rtl" else "") + (if (longLabels) "-long-labels" else "")
        if (landscape) device.setOrientationLeft() else device.setOrientationNatural()
        try {
            ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                            var isOpen by remember { mutableStateOf(true) }
                            var saved by remember { mutableStateOf("Original") }
                            var draft by remember { mutableStateOf("Original") }
                            if (isOpen) ChatySelectionModal(
                                title = modalTitle, applyLabel = applyText, onDismiss = { isOpen = false },
                                onApply = { saved = draft; isOpen = false },
                            ) {
                                val view = LocalView.current
                                SideEffect { dialogView.set(view) }
                                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
                                    OutlinedTextField(value = draft, onValueChange = { draft = it },
                                        label = { Text("Synthetic draft") }, modifier = Modifier.fillMaxWidth())
                                    repeat(30) { Text("Synthetic body row $it") }
                                }
                            } else Box(Modifier.fillMaxSize().safeDrawingPadding()) { Text("Saved: $saved") }
                        }
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.text("Synthetic draft")), 10000))
                val input = device.findObject(By.clazz("android.widget.EditText")) ?: error("No editable draft")
                input.click()
                input.setText("Changed")
                var keyboardVisible = false
                val deadline = System.currentTimeMillis() + 5000
                while (!keyboardVisible && System.currentTimeMillis() < deadline) {
                    inst.runOnMainSync {
                        keyboardVisible = dialogView.get()?.let { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) } == true
                    }
                    if (!keyboardVisible) Thread.sleep(100)
                }
                assertTrue("Actual dialog IME is visible", keyboardVisible)
                device.waitForIdle()
                var safe: Rect? = null
                inst.runOnMainSync {
                    val view = dialogView.get() ?: error("No dialog view")
                    val root = view.rootView
                    val insets = ViewCompat.getRootWindowInsets(view) ?: error("No dialog insets")
                    // The visible display frame already accounts for adjustResize/IME.
                    // Subtracting IME from root.height again would double-count it.
                    val frame = Rect(); root.getWindowVisibleDisplayFrame(frame)
                    assertTrue("Visible dialog window frame exists", frame.width() > 0 && frame.height() > 0)
                    safe = frame
                }
                device.takeScreenshot(File(dir, "shared-modal-$mode-ime.png"))
                device.dumpWindowHierarchy(File(dir, "shared-modal-$mode-ime.xml"))
                val liveDraft = device.findObject(By.clazz("android.widget.EditText")) ?: error("Focused draft disappeared behind chrome")
                val draftBounds = liveDraft.visibleBounds
                val minimumDraftHeight = 48 * inst.targetContext.resources.displayMetrics.density - 1
                assertTrue("Editable body stays usable above actual IME: $draftBounds vs $safe",
                    draftBounds.width() > 0 && draftBounds.height() >= minimumDraftHeight && safe!!.contains(draftBounds))
                assertTrue("Edited draft remains visible", liveDraft.text.contains("Changed"))
                for (label in listOf(modalTitle, "Cancel", applyText)) {
                    val node = device.findObject(By.text(label)) ?: error("Missing fixed control: $label")
                    assertTrue("$label clears system bars/cutout/IME: ${node.visibleBounds} vs $safe", safe!!.contains(node.visibleBounds))
                }
                for (label in listOf("Cancel", applyText)) {
                    var action = device.findObject(By.text(label)) ?: error("Missing action $label")
                    while (!action.isClickable && action.parent != null) action = action.parent
                    assertTrue("$label has an enabled clickable owner", action.isClickable && action.isEnabled)
                    assertTrue("Entire $label target clears IME and system bars", safe!!.contains(action.visibleBounds))
                    val minimum = 48 * inst.targetContext.resources.displayMetrics.density - 1
                    assertTrue("$label retains 48dp target", action.visibleBounds.height() >= minimum && action.visibleBounds.width() >= minimum)
                }
                val close = device.findObject(By.desc("Close $modalTitle")) ?: error("Close missing")
                assertTrue("Close stays inside usable viewport", safe!!.contains(close.visibleBounds))
                val cancelBounds = device.findObject(By.text("Cancel")).visibleBounds
                val applyBounds = device.findObject(By.text(applyText)).visibleBounds
                assertFalse("Action labels do not overlap", Rect.intersects(cancelBounds, applyBounds))
                assertFalse("Close does not overlap Apply label", Rect.intersects(close.visibleBounds, applyBounds))
                device.findObject(By.text("Cancel")).click()
                assertTrue("Cancel discards edited draft", device.wait(Until.hasObject(By.text("Saved: Original")), 5000))
            }
        } catch (failure: Throwable) {
            runCatching { device.takeScreenshot(File(dir, "shared-modal-$mode-failure.png")) }
            runCatching { device.dumpWindowHierarchy(File(dir, "shared-modal-$mode-failure.xml")) }
            throw failure
        } finally { device.setOrientationNatural(); device.unfreezeRotation() }
    }
}
