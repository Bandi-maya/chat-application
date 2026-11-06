package com.example.chat

import android.graphics.BitmapFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * System bars must follow the app-selected theme on the REAL app root (MainActivity -> AppRoot):
 * with the device in night mode the status-bar and navigation-bar strips read dark; in day mode
 * they read light. Samples actual full-window framebuffer pixels (OEM-proof painted content, not
 * window-setter side effects) and saves PNGs as visual evidence. Also proves the app-selected
 * dark theme override drives dark bars while the OS stays in day mode. Restores day mode and
 * follow-system afterwards.
 * Fresh-install default is follow-system (SystemThemeFollowProofTest pins that default).
 */
@RunWith(AndroidJUnit4::class)
class SystemBarThemeProofTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var device: UiDevice
    private val outDir: File
        get() = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

    @Before fun setUp() { device = UiDevice.getInstance(instrumentation) }

    @After fun tearDown() {
        try { device.executeShellCommand("cmd uimode night no") } catch (_: Exception) {}
        try {
            ServiceLocator.themeController.setGlobalTheme(
                com.example.chat.core.theme.ThemePresets.getSystemDefaultTheme(),
                followSystem = true,
            )
        } catch (_: Exception) {}
    }

    private fun stripLuminance(file: File, top: Boolean): Double {
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        val h = bmp.height; val w = bmp.width
        val rows = (h * 0.04).toInt().coerceAtLeast(16)
        val yStart = if (top) 0 else h - rows
        var sum = 0.0; var n = 0
        for (y in yStart until yStart + rows) {
            for (x in 0 until w step 4) {
                val p = bmp.getPixel(x, y)
                sum += (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p)) / 3.0
                n++
            }
        }
        return sum / n
    }

    private fun captureNamed(name: String): File {
        val f = File(outDir, name)
        device.takeScreenshot(f)
        return f
    }

    @Test fun barsFollowAppThemeDarkAndLightOnRealRoot() {
        // Signed-out precondition: a leftover session from an earlier test (build 83 ran this
        // while still authenticated) lands the root on Home, Welcome never appears, and the bar
        // pixels would be judged on the wrong screen. Clear the persisted session before launch,
        // and if the still-running process carries an in-memory session, sign it out through the
        // real backend before gating on Welcome. The bars/theme assertions below are unchanged:
        // they run against the actual Welcome screen pixels, not just the gate.
        instrumentation.targetContext
            .getSharedPreferences("chaty_api_backend", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        device.executeShellCommand("cmd uimode night yes")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                if (ServiceLocator.apiBackend.isAuthenticated) {
                    scenario.onActivity { ServiceLocator.apiBackend.signOut() }
                }
            } catch (_: Exception) {}
            assertTrue("welcome screen never appeared (dark)", device.wait(Until.hasObject(By.textContains("Welcome")), 30000))
            device.waitForIdle(5000)
            val dark = captureNamed("system-bars-follow-dark.png")
            val darkTop = stripLuminance(dark, top = true)
            val darkBottom = stripLuminance(dark, top = false)

            device.executeShellCommand("cmd uimode night no")
            // Same activity instance: configuration change recreates it; wait for settle, not a fixed sleep.
            device.waitForIdle(5000)
            Thread.sleep(1500)
            val light = captureNamed("system-bars-follow-light.png")
            val lightTop = stripLuminance(light, top = true)
            val lightBottom = stripLuminance(light, top = false)

            assertTrue("dark mode: status strip too bright (avg=$darkTop)", darkTop < 100.0)
            assertTrue("dark mode: navigation strip too bright (avg=$darkBottom)", darkBottom < 100.0)
            assertTrue("light mode: status strip too dark (avg=$lightTop)", lightTop > 155.0)
            assertTrue("light mode: navigation strip too dark (avg=$lightBottom)", lightBottom > 155.0)

            // App-selected theme override: OS stays in day mode while the user picks a dark app
            // theme. The bars must follow the APP selection, not the OS (original requirement:
            // readable bars under the app-chosen theme even when it overrides the system setting).
            scenario.onActivity {
                ServiceLocator.themeController.setGlobalTheme(
                    com.example.chat.core.theme.ThemePresets.all.first { preset -> preset.isDark },
                    followSystem = false,
                )
            }
            device.waitForIdle(5000)
            Thread.sleep(1500)
            val ovr = captureNamed("system-bars-follow-app-override.png")
            val ovrTop = stripLuminance(ovr, top = true)
            val ovrBottom = stripLuminance(ovr, top = false)
            restoreFollowSystem(scenario)

            assertTrue("app dark override in OS day: status strip too bright (avg=$ovrTop)", ovrTop < 100.0)
            assertTrue("app dark override in OS day: navigation strip too bright (avg=$ovrBottom)", ovrBottom < 100.0)
        }
    }

    private fun restoreFollowSystem(scenario: ActivityScenario<MainActivity>) {
        try {
            scenario.onActivity {
                ServiceLocator.themeController.setGlobalTheme(
                    com.example.chat.core.theme.ThemePresets.getSystemDefaultTheme(),
                    followSystem = true,
                )
            }
        } catch (_: Exception) {}
    }
}
