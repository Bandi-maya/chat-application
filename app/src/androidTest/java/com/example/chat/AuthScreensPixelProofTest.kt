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
        android.util.Log.i("PixelProof", "setUp: launching MainActivity")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        android.util.Log.i("PixelProof", "setUp: launch returned, state=${scenario.state}")
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    private fun awaitText(text: String, timeoutMs: Long = 20000) {
        if (!device.wait(Until.hasObject(By.text(text)), timeoutMs)) {
            // Failure diagnostics captured at the moment of failure (not after teardown).
            val resumed = try {
                device.executeShellCommand("dumpsys activity activities").lineSequence()
                    .filter { it.contains("ResumedActivity", true) || it.contains("topResumedActivity", true) }
                    .take(4).joinToString(" | ")
            } catch (e: Exception) { "n/a: ${e.message}" }
            val texts = try {
                val out = java.io.ByteArrayOutputStream()
                device.dumpWindowHierarchy(out)
                Regex("(?:text|content-desc)=\"([^\"]+)\"").findAll(out.toString()).map { it.groupValues[1] }
                    .filter { it.isNotBlank() }.take(40).toList()
            } catch (e: Exception) { listOf("dump failed: ${e.message}") }
            val errs = try {
                device.executeShellCommand("logcat -d -t 20000").lineSequence()
                    .filter { (it.contains("PixelProof") || it.contains("com.example.chat") || it.contains("ActivityScenario") || it.contains("AndroidRuntime") || it.contains("FATAL")) && !it.contains("AppsFilter") }
                    .toList().takeLast(60).joinToString("\n") { it.take(240) }
            } catch (e: Exception) { "n/a: ${e.message}" }
            val st = try { scenario.state.toString() } catch (e: Exception) { "state n/a: ${e.message}" }
            try { capture("00-failure-state") } catch (_: Exception) {}
            throw AssertionError(
                "expected screen containing \"$text\"; resumed=[$resumed]; scenarioState=$st; texts=$texts; errors=[\n$errs]",
            )
        }
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
        // Pre-send state of forgot_password_screen.dart (the "secure recovery link" copy only appears after a
        // real recovery email is sent, which this fixture run must not do).
        awaitText("Send Recovery Email", 20000)
        capture("04-forgot-password")
        device.pressBack()
        awaitText("Welcome Back!")

        // Login -> register organization entry.
        clickText("Register an Organization")
        awaitText("Register Organization")
        capture("05-register-organization")
    }
}
