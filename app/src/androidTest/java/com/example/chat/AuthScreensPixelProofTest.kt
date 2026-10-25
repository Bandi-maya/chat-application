package com.example.chat

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pixel proof tier: launches MainActivity on the emulator, walks the auth
 * flow (Welcome -> Login -> back -> Register -> Forgot Password ->
 * Register Organization) and captures real rendered frames as PNGs.
 *
 * The workflow pulls the PNGs off the device and uploads them as CI
 * artifacts so a human can inspect actual UI pixels. This is visual
 * evidence only; it asserts the screens render and navigate, not that
 * auth works against a backend.
 */
@RunWith(AndroidJUnit4::class)
class AuthScreensPixelProofTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var device: UiDevice
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val outDir: File
        get() = File(
            instrumentation.targetContext.getExternalFilesDir(null),
            "screenshots",
        ).apply { mkdirs() }

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    private fun awaitText(text: String, timeoutMs: Long = 20000) {
        assertTrue(
            "expected screen containing \"$text\"",
            device.wait(Until.hasObject(By.text(text)), timeoutMs),
        )
    }

    private fun clickText(text: String, timeoutMs: Long = 20000) {
        awaitText(text, timeoutMs)
        val obj = device.findObject(By.text(text))
            ?: throw AssertionError("click target \"$text\" not found")
        obj.click()
    }

    private fun capture(name: String) {
        // Let Compose finish its transition frames before grabbing pixels.
        device.waitForIdle(3000)
        Thread.sleep(750)
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("takeScreenshot returned null")
        FileOutputStream(File(outDir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    @Test
    fun authFlowRendersAndNavigates() {
        awaitText("Welcome to Chaty")
        capture("01-welcome")

        clickText("Log In")
        awaitText("Welcome Back!")
        capture("02-login")

        device.pressBack()
        awaitText("Welcome to Chaty")

        clickText("Register")
        awaitText("Everything You Need!")
        capture("03-register")

        device.pressBack()
        awaitText("Welcome to Chaty")

        // Login -> forgot password link.
        clickText("Log In")
        awaitText("Welcome Back!")
        clickText("Forgot Password?")
        awaitText("secure recovery link", 20000)
        capture("04-forgot-password")
        device.pressBack()
        awaitText("Welcome Back!")

        // Login -> register organization entry.
        clickText("Register a New Organization")
        awaitText("Register Organization")
        capture("05-register-organization")
    }
}
