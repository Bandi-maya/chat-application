package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
import com.example.chat.ui.auth.*
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated presentational controls, no authentication/network or saved-account mutation. */
@RunWith(AndroidJUnit4::class)
class PrdAuthAccessibilityProofTest {
    @Test fun narrowDoubleFontAuthTargetsAndEditableLabelRemainUsable() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val entered = AtomicReference("")
        val back = AtomicInteger()
        val submit = AtomicInteger()
        var pixelsPerDp = 0f
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val density = LocalDensity.current.density
                    pixelsPerDp = density
                    CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                        ChatyTheme(ThemePresets.monochromeDark) {
                            val value = androidx.compose.runtime.remember { mutableStateOf("") }
                            androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                            Column(Modifier.width(280.dp)) {
                                AuthBackButton { back.incrementAndGet() }
                                AuthTextField("Email", "name@example.invalid", ThemePresets.monochromeDark,
                                    value.value, { value.value = it; entered.set(it) })
                                AuthPrimaryButton("Continue with this account", ThemePresets.monochromeDark,
                                    { submit.incrementAndGet() })
                            }
                            }
                            }
                        }
                    }
                }
            }
            assertTrue("Back action missing", device.wait(Until.hasObject(By.desc("Back")), 10000))
            val backNode = device.findObject(By.desc("Back")) ?: throw AssertionError("Back action missing")
            device.waitForIdle()
            val diagnostics = File(inst.targetContext.getExternalFilesDir(null), "screenshots")
            diagnostics.mkdirs()
            device.takeScreenshot(File(diagnostics, "prd-auth-before-target-assertion.png"))
            device.dumpWindowHierarchy(File(diagnostics, "prd-auth-before-target-assertion.xml"))
            assertTrue("Back must expose the clickable target", backNode.isClickable)
            val bounds = backNode.visibleBounds
            assertTrue("Back width must be at least48dp", bounds.width() + 1 >= 48f * pixelsPerDp)
            assertTrue("Back height must be at least48dp", bounds.height() + 1 >= 48f * pixelsPerDp)
            backNode.click()
            assertTrue("Email accessibility label missing", device.wait(Until.hasObject(By.desc("Email")), 5000))
            val field = device.findObject(By.clazz("android.widget.EditText").textContains("Email"))
                ?: device.findObject(By.clazz("android.widget.EditText").desc("Email"))
                ?: device.findObject(By.clazz("android.widget.EditText").hasDescendant(By.text("Email")))
                ?: device.findObject(By.clazz("android.widget.EditText").hasDescendant(By.desc("Email")))
                ?: throw AssertionError("Native labelled editable Email field missing")
            field.text = "test@example.invalid"
            device.waitForIdle()
            // Programmatic setText may not open an IME. Back in that state finishes
            // this fixture Activity. Hide IME directly instead of sending navigation.
            scenario.onActivity { activity ->
                androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                activity.window.decorView.clearFocus()
            }
            device.waitForIdle()
            device.takeScreenshot(File(diagnostics, "prd-auth-after-input-ime-dismissed.png"))
            device.dumpWindowHierarchy(File(diagnostics, "prd-auth-after-input-ime-dismissed.xml"))
            val action = device.wait(Until.findObject(By.text("Continue with this account")), 5000)
                ?: throw AssertionError("Growing primary action unreachable at280dp/font2x")
            val image = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No actual auth pixels")
            val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/prd-auth-280dp-font2x.png")
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            action.click()
            inst.waitForIdleSync()
            assertEquals(1, back.get())
            assertEquals(1, submit.get())
            assertEquals("test@example.invalid", entered.get())
        }
    }
}
