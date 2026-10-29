package com.example.chat

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stress reproduction for the emulator-observed chat-open crash (builds
 * 79/81/82: FATAL NPE in endReplaceGroup at ChatDetailHost during the
 * open/sign-out recomposition window; build 83 did not reproduce it, so the
 * race is latent, not fixed). Loops the real demo path: Welcome -> Log In ->
 * Try the demo -> open the seeded Aisha chat -> Exit demo -> Welcome, 15
 * times inside one activity scenario so repeated ChatDetailHost enter/dispose
 * cycles run back to back. Every iteration asserts the full path; a failed
 * step dumps the crash buffer tail into the failure message so a reproduction
 * carries its own FATAL evidence. REPRO-ITER markers bracket each iteration
 * in logcat. No product behavior is altered and no wait is unbounded.
 */
@RunWith(AndroidJUnit4::class)
class DemoCrashReproProofTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var device: UiDevice

    @Before fun setUp() { device = UiDevice.getInstance(instrumentation) }

    @After fun tearDown() {
        try {
            if (ServiceLocator.apiBackend.isDemoSession) ServiceLocator.apiBackend.signOut()
        } catch (_: Exception) {}
        try {
            instrumentation.targetContext
                .getSharedPreferences("chaty_api_backend", android.content.Context.MODE_PRIVATE)
                .edit().clear().commit()
        } catch (_: Exception) {}
    }

    private fun dismissDemoEntryPrompts() {
        if (device.wait(Until.hasObject(By.text("Enable App Lock?")), 5000)) {
            device.findObject(By.text("Not now")).click()
        }
        if (device.wait(Until.hasObject(By.text("Not Now")), 4000)) {
            device.findObject(By.text("Not Now")).click()
        }
    }

    private fun crashTail(): String = try {
        device.executeShellCommand("logcat -b crash -d -t 40").takeLast(4000)
    } catch (e: Exception) {
        "crash buffer unavailable: $e"
    }

    @Test fun demoChatOpenExitLoopDoesNotCrash() {
        val iterations = 15
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(
                "welcome never appeared",
                device.wait(Until.hasObject(By.textContains("Welcome")), 30000),
            )
            device.waitForIdle(3000)
            for (i in 1..iterations) {
                Log.i("DemoCrashRepro", "REPRO-ITER $i START t=${System.currentTimeMillis()}")
                try {
                    // Welcome -> Login -> demo entry.
                    assertTrue("no Log In button", device.wait(Until.hasObject(By.text("Log In")), 10000))
                    device.findObject(By.text("Log In")).click()
                    assertTrue("demo card never appeared", device.wait(Until.hasObject(By.textContains("Try the demo")), 10000))
                    device.waitForIdle(1000)
                    device.findObject(By.textContains("Try the demo")).click()
                    dismissDemoEntryPrompts()
                    assertTrue("demo home never appeared", device.wait(Until.hasObject(By.textContains("Aisha (sample)")), 15000))
                    device.waitForIdle(1000)

                    // Open the chat and sit in the recomposition window.
                    device.findObject(By.textContains("Aisha (sample)")).click()
                    Log.i("DemoCrashRepro", "REPRO-ITER $i OPENED t=${System.currentTimeMillis()}")
                    assertTrue(
                        "chat never opened",
                        device.wait(Until.hasObject(By.textContains("Exactly. Everything here is offline demo data.")), 10000),
                    )
                    device.waitForIdle(2000)

                    // Exit demo -> Welcome.
                    device.findObject(By.text("Exit demo")).click()
                    assertTrue("welcome never reappeared after exit", device.wait(Until.hasObject(By.textContains("Welcome")), 15000))
                    assertTrue("demo session survived exit", !ServiceLocator.apiBackend.isDemoSession)
                    device.waitForIdle(1000)
                } catch (t: Throwable) {
                    Log.e("DemoCrashRepro", "REPRO-ITER $i FAILED t=${System.currentTimeMillis()}", t)
                    throw AssertionError("REPRO-ITER $i failed: ${t.message}\n--- crash buffer tail ---\n${crashTail()}", t)
                }
                Log.i("DemoCrashRepro", "REPRO-ITER $i OK t=${System.currentTimeMillis()}")
            }
        }
    }
}
