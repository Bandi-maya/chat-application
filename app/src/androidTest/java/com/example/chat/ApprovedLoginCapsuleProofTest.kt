package com.example.chat

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.auth.LoginScreen
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated real Login composable. Never creates a backend session or submits credentials. */
@RunWith(AndroidJUnit4::class)
class ApprovedLoginCapsuleProofTest {
    @Test fun normalLightEmptyValidationAndRecoveryNavigation() = capture(false, 1f)
    @Test fun organizationDarkWorkspaceAndPasswordVisibility() = capture(true, 1f)
    @Test fun organizationDarkLargeTextRemainsScrollable() = capture(true, 2f)

    private fun capture(organization: Boolean, fontScale: Float) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val loginCalls = AtomicInteger()
        val forgotCalls = AtomicInteger()
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                        ChatyTheme(ThemePresets.getSystemDefaultTheme(!organization)) {
                            LoginScreen(
                                onBack = {}, onLoggedIn = { throw AssertionError("No session expected") },
                                onForgotPassword = { forgotCalls.incrementAndGet() },
                                onRegister = {}, onRegisterOrganization = {},
                                login = { _, _, _, _ -> loginCalls.incrementAndGet(); throw AssertionError("No backend calls allowed") },
                                loadSavedTenant = { if (organization) "fixture-workspace" to false else null to true },
                            )
                        }
                    }
                }
            }
            assertTrue(device.wait(Until.hasObject(By.text("Welcome back")), 10000))
            if (organization) {
                assertTrue(device.wait(Until.hasObject(By.desc("Workspace ID")), 5000))
            }
            val folder = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            val stem = "approved-login-${if (organization) "organization-dark" else "normal-light"}-font$fontScale"
            device.waitForIdle()
            assertTrue(device.takeScreenshot(File(folder, "$stem.png")))
            device.dumpWindowHierarchy(File(folder, "$stem.xml"))
            val scroll = device.findObject(By.scrollable(true))
            repeat(5) {
                if (device.hasObject(By.text("Register a New Organization"))) return@repeat
                scroll?.scroll(androidx.test.uiautomator.Direction.DOWN, 0.65f)
            }
            assertNotNull("Organization link unreachable", device.findObject(By.text("Register a New Organization")))
            assertNotNull("Primary action unreachable", device.findObject(By.text("Log In")))
            if (fontScale == 1f) {
                if (organization) {
                    val eye = device.findObject(By.desc("Show password"))
                    assertNotNull("Password visibility action missing", eye)
                    eye!!.click()
                    assertTrue(device.wait(Until.hasObject(By.desc("Hide password")), 3000))
                }
                device.findObject(By.text("Log In")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Enter your email or username")), 5000))
                device.takeScreenshot(File(folder, "$stem-empty-validation.png"))
                device.findObject(By.text("Forgot Password?")).click()
                inst.waitForIdleSync()
                assertEquals(1, forgotCalls.get())
            }
            assertEquals("No network-auth seam may run", 0, loginCalls.get())
        }
    }
}
