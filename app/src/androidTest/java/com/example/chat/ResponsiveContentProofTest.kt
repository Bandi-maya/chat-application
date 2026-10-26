package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.chat.core.templates.UpdatesLayoutMode
import com.example.chat.core.templates.UpdatesTemplate
import com.example.chat.core.theme.ThemePresets
import com.example.chat.data.services.StatusRecord
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.settings.security.LockCredentialSetupContent
import com.example.chat.ui.settings.security.LockSetupService
import com.example.chat.ui.theme.ChatyTheme
import com.example.chat.ui.updates.GridStatusCard
import com.example.chat.ui.updates.StatusContent
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Narrow content constraints + 200% text in isolated UI; no real credentials/accounts. */
@RunWith(AndroidJUnit4::class)
class ResponsiveContentProofTest {
    private fun capture(name: String) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val image = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No pixels")
        val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/$name.png")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    @Test fun sixDigitPinUsesOneAccessibleFieldAtNarrowLargeText() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val writes = AtomicInteger()
        val service = object : LockSetupService {
            override suspend fun hasCredential(method: String) = false
            override suspend fun verifyCredential(method: String, secret: String) = false
            override suspend fun setCredential(method: String, secret: String, pinLength: Int?) { writes.incrementAndGet() }
            override suspend fun authenticateBiometric(reason: String) = false
            override suspend fun authenticateDeviceCredential(reason: String) = false
        }
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                        Column(Modifier.safeDrawingPadding().widthIn(max = 320.dp)) {
                            LockCredentialSetupContent("PIN", 6, service, false, {})
                        }
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("6-digit PIN")), 10000))
            val field = device.findObject(By.clazz("android.widget.EditText"))
                ?: throw AssertionError("Accessible PIN field missing")
            val bounds = field.visibleBounds
            assertTrue("PIN field fits window", bounds.left >= 0 && bounds.right <= device.displayWidth)
            assertTrue("PIN hit height >=48dp", bounds.height() >= 48 * inst.targetContext.resources.displayMetrics.density)
            capture("30-pin-narrow-large-font")
            field.setText("12") // Synthetic incomplete input must never save.
            device.pressBack() // Hide IME; does not change credentials.
            val continueButton = device.findObject(By.text("Continue")) ?: throw AssertionError("Continue missing")
            continueButton.click()
            assertTrue(device.wait(Until.hasObject(By.text("Enter all 6 digits.")), 3000))
            assertEquals("No credential writes from incomplete input", 0, writes.get())
            var edit = device.findObject(By.clazz("android.widget.EditText"))
                ?: throw AssertionError("PIN field lost after validation")
            edit.click()
            assertTrue("PIN remains focusable with IME", device.wait(Until.hasObject(By.clazz("android.widget.EditText").focused(true)), 3000))
            edit.setText("1234")
            edit.setText("123") // Delete preserves partial input without advancing.
            assertEquals("Partial/delete cannot save", 0, writes.get())
            edit.setText("123456") // Paste a complete synthetic PIN into step one.
            assertTrue("Complete PIN advances to confirmation", device.wait(Until.hasObject(By.text("Confirm PIN")), 3000))
            assertEquals("First entry cannot save", 0, writes.get())
            edit = device.findObject(By.clazz("android.widget.EditText"))
                ?: throw AssertionError("Confirmation PIN field missing")
            edit.setText("123456")
            val deadline = System.currentTimeMillis() + 3000
            while (writes.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertEquals("Only matching confirmation writes to fake service once", 1, writes.get())
            capture("30b-pin-confirmation-with-ime")
        }
    }

    @Test fun statusCardAndSecureButtonGrowForLargeText() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val now = Instant.now()
        val user = UserProfile("synthetic", "Synthetic Person", "fixture", "SP", "0xFF6366F1", "", lastSeenAt = now)
        val status = StatusRecord("fixture", user.id, "Synthetic update with a longer subtitle", "document",
            "fixture.pdf", "Synthetic document", 1, 0, now, now.plusSeconds(3600), null)
        val colors = ChatyColors(ThemePresets.getSystemDefaultTheme(true))
        val template = UpdatesTemplate(UpdatesLayoutMode.gridTiles)
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                        Column(Modifier.safeDrawingPadding().widthIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                            GridStatusCard(status, template, colors, user, { user }, { null }, {})
                            StatusContent(status, "https://example.invalid/fixture", colors, template, { _, _ -> }, {})
                        }
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("Open Securely")), 10000))
            val button = device.findObject(By.text("Open Securely"))
            assertTrue("Button label within width", button.visibleBounds.right <= device.displayWidth)
            assertTrue("Status timestamp remains visible", device.findObject(By.textContains("ago")) != null || device.findObject(By.text("Just now")) != null || device.findObject(By.text("just now")) != null || device.findObject(By.text("Now")) != null)
            capture("31-updates-narrow-large-font")
        }
    }
}
