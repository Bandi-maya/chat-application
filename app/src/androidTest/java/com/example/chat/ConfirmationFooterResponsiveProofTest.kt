package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ChatyConfirmationDialog
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual shared dialog, synthetic callbacks only. No account/state effects. */
@RunWith(AndroidJUnit4::class)
class ConfirmationFooterResponsiveProofTest {
    @Test fun narrowLargeTextActionsWrapAndRemainReachable() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val cancels = AtomicInteger()
        val confirms = AtomicInteger()
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                        ChatyConfirmationDialog(
                            onDismissRequest = {}, modifier = Modifier.widthIn(max = 240.dp),
                            title = { Text("Synthetic confirmation") },
                            text = { Text((1..40).joinToString(" ") { "Body $it" }) },
                            dismissButton = { TextButton(onClick = { cancels.incrementAndGet() }) { Text("Keep editing") } },
                            confirmButton = { TextButton(onClick = { confirms.incrementAndGet() }) { Text("Save changes") } },
                        )
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("Save changes")), 10000))
            val cancel = device.findObject(By.text("Keep editing"))
            val confirm = device.findObject(By.text("Save changes"))
            val c = cancel.visibleBounds
            val a = confirm.visibleBounds
            assertTrue("Both labels within screen", c.left >= 0 && c.right <= device.displayWidth && a.left >= 0 && a.right <= device.displayWidth)
            assertTrue("Narrow actions wrap instead of overlap", a.top >= c.bottom)
            val image = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No actual dialog pixels")
            val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/confirmation-footer-240dp-200pct.png")
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            cancel.click()
            confirm.click()
            assertEquals(1, cancels.get())
            assertEquals(1, confirms.get())
        }
    }
}
