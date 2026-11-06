package com.example.chat

import androidx.test.core.app.ActivityScenario
import com.example.chat.data.demo.DemoMode
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
 * Offline demo login (NOT in Flutter) on the REAL app root: the login page
 * shows the labelled demo card, the demo session lands on the seeded sample
 * home with the demo banner, the chat detail keeps the demo context visible,
 * and Exit demo returns to Welcome. Saves PNG evidence at each step.
 */
@RunWith(AndroidJUnit4::class)
class DemoModeProofTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var device: UiDevice
    private val outDir: File
        get() = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

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

    private fun captureNamed(name: String): File {
        val f = File(outDir, name)
        device.takeScreenshot(f)
        return f
    }

    /** Real persisted REST credentials (even a damaged token-without-user pair)
     *  must fail demo entry closed: no overwrite, no demo session. */
    /** Post-demo-entry the product stacks its real, optional prompts in order:
     *  the App Lock offer ("Enable App Lock?" / "Not now") and then the
     *  notification education sheet ("Not Now"). Dismiss each through its own
     *  UI when present; both are show-once, so later entries and restarts skip
     *  them and the bounded waits simply elapse. No product guard is bypassed:
     *  these are the same buttons a user taps. */
    private fun dismissDemoEntryPrompts() {
        if (device.wait(Until.hasObject(By.text("Enable App Lock?")), 5000)) {
            device.findObject(By.text("Not now")).click()
        }
        if (device.wait(Until.hasObject(By.text("Not Now")), 4000)) {
            device.findObject(By.text("Not Now")).click()
        }
    }

    @Test fun demoEntryRefusedWithPersistedRealCredentials() {
        val prefs = instrumentation.targetContext
            .getSharedPreferences("chaty_api_backend", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("api_hub_token", "real-token").remove("api_hub_user").commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTrue("welcome never appeared", device.wait(Until.hasObject(By.textContains("Welcome")), 30000))
                device.waitForIdle(3000)
                device.findObject(By.text("Log In")).click()
                assertTrue("demo card never appeared", device.wait(Until.hasObject(By.textContains("Try the demo")), 10000))
                device.findObject(By.textContains("Try the demo")).click()
                assertTrue(
                    "refusal reason never shown",
                    device.wait(Until.hasObject(By.textContains("Sign out before starting the demo.")), 10000),
                )
                assertTrue("demo session must not start", !ServiceLocator.apiBackend.isDemoSession)
                assertTrue(
                    "persisted real token must survive",
                    prefs.getString("api_hub_token", null) == "real-token",
                )
            }
        } finally {
            prefs.edit().clear().commit()
        }
    }

    /** Restore boundary: a demo session survives process restart through the
     *  persisted api_hub user payload, with the offline guard pre-armed
     *  BEFORE session.restore() fires auth transitions (peer review: restore
     *  assertions were missing). */
    @Test fun demoSessionRestoresAfterRestart() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("welcome never appeared", device.wait(Until.hasObject(By.textContains("Welcome")), 30000))
            device.waitForIdle(3000)
            device.findObject(By.text("Log In")).click()
            assertTrue("demo card never appeared", device.wait(Until.hasObject(By.textContains("Try the demo")), 10000))
            device.findObject(By.textContains("Try the demo")).click()
            dismissDemoEntryPrompts()
            assertTrue("demo home never appeared", device.wait(Until.hasObject(By.textContains("Aisha (sample)")), 15000))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            dismissDemoEntryPrompts()
            assertTrue(
                "demo session was not restored after restart",
                device.wait(Until.hasObject(By.textContains("Aisha (sample)")), 30000),
            )
            assertTrue(
                "demo banner missing after restore",
                device.wait(Until.hasObject(By.textContains("Demo mode - offline sample data")), 10000),
            )
            assertTrue("restored session must still be demo", ServiceLocator.apiBackend.isDemoSession)
        }
    }

    /** Peer Edge A: a real-adopt boundary (GoTrue or REST) must clear the
     *  PERSISTED demo REST identity too, not just memory - otherwise
     *  isDemoSession stays true with the flag-only guards disarmed. Calls
     *  the same internal purge the GoTrue auth listener invokes. */
    @Test fun realAdoptPurgeClearsPersistedDemoIdentity() {
        val prefs = instrumentation.targetContext
            .getSharedPreferences("chaty_api_backend", android.content.Context.MODE_PRIVATE)
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("welcome never appeared", device.wait(Until.hasObject(By.textContains("Welcome")), 30000))
            device.waitForIdle(3000)
            device.findObject(By.text("Log In")).click()
            assertTrue("demo card never appeared", device.wait(Until.hasObject(By.textContains("Try the demo")), 10000))
            device.findObject(By.textContains("Try the demo")).click()
            dismissDemoEntryPrompts()
            assertTrue("demo home never appeared", device.wait(Until.hasObject(By.textContains("Aisha (sample)")), 15000))
            assertTrue("must be a demo session", ServiceLocator.apiBackend.isDemoSession)
            assertTrue("demo identity must be persisted for restart", prefs.getString("api_hub_token", null) != null)

            ServiceLocator.apiBackend.purgeDemoResidueForRealAdopt()

            assertTrue("demo session must be gone", !ServiceLocator.apiBackend.isDemoSession)
            assertTrue("persisted demo token must be cleared", prefs.getString("api_hub_token", null) == null)
            assertTrue("persisted demo user must be cleared", prefs.getString("api_hub_user", null) == null)
            assertTrue("offline flag must be disarmed", !DemoMode.active)
        }
    }

    /** Peer mixed-store blocker: REAL token + demo-id JSON in the store must
     *  NEVER be classified demo or erased - not by the real-adopt purge and
     *  not by signing out whatever session restore composed. */
    @Test fun mixedStoreRealTokenSurvivesPurge() {
        val prefs = instrumentation.targetContext
            .getSharedPreferences("chaty_api_backend", android.content.Context.MODE_PRIVATE)
        val demoJson = """{"id":"demo:offline-user","username":"demo"}"""
        prefs.edit().putString("api_hub_token", "real-jwt-token").putString("api_hub_user", demoJson).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                device.wait(Until.hasObject(By.textContains("Welcome")), 30000)
                device.waitForIdle(3000)
                // restore composed a mixed session: it must be QUARANTINED
                // (in-memory drop, no network-enabled corrupt identity),
                // never demo-armed, raw bytes preserved.
                assertTrue("mixed store must not classify as demo", !ServiceLocator.apiBackend.isDemoSession)
                assertTrue("offline flag must stay disarmed", !DemoMode.active)
                assertTrue("mixed session must be quarantined (logged out)", ServiceLocator.apiBackend.currentUser == null)
                assertTrue("quarantine must not erase the real token", prefs.getString("api_hub_token", null) == "real-jwt-token")
                // The real-adopt purge must also preserve the raw bytes.
                ServiceLocator.apiBackend.purgeDemoResidueForRealAdopt()
                assertTrue("purge must not erase the real token", prefs.getString("api_hub_token", null) == "real-jwt-token")
                assertTrue("purge must not erase the stored user payload", prefs.getString("api_hub_user", null) == demoJson)
            }
        } finally {
            prefs.edit().clear().commit()
        }
    }

    /** Second mixed ordering at initialize orchestration (no network): demo
     *  token + real-id JSON must also quarantine - no demo arming, no
     *  authenticated branch (loads/FCM/realtime all skip on isAuthenticated),
     *  raw bytes preserved. */
    @Test fun mixedStoreDemoTokenRealJsonQuarantined() {
        val prefs = instrumentation.targetContext
            .getSharedPreferences("chaty_api_backend", android.content.Context.MODE_PRIVATE)
        val realJson = """{"id":"user-123","username":"bandi"}"""
        prefs.edit().putString("api_hub_token", "demo:offline-token").putString("api_hub_user", realJson).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTrue("welcome never appeared", device.wait(Until.hasObject(By.textContains("Welcome")), 30000))
                device.waitForIdle(3000)
                assertTrue("mixed session must be quarantined (logged out)", ServiceLocator.apiBackend.currentUser == null)
                assertTrue("offline flag must stay disarmed", !DemoMode.active)
                assertTrue("mixed store must not classify as demo", !ServiceLocator.apiBackend.isDemoSession)
                assertTrue("demo token bytes must survive quarantine", prefs.getString("api_hub_token", null) == "demo:offline-token")
                assertTrue("real user payload must survive quarantine", prefs.getString("api_hub_user", null) == realJson)
            }
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun demoLoginShowsSampleDataAndExitsCleanly() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("welcome never appeared", device.wait(Until.hasObject(By.textContains("Welcome")), 30000))
            device.waitForIdle(3000)

            // Welcome -> Login.
            assertTrue("no Log In button", device.wait(Until.hasObject(By.text("Log In")), 10000))
            device.findObject(By.text("Log In")).click()
            assertTrue("demo card never appeared", device.wait(Until.hasObject(By.textContains("Try the demo")), 10000))
            device.waitForIdle(2000)
            captureNamed("demo-login-card.png")

            // Demo login -> seeded home with banner.
            device.findObject(By.textContains("Try the demo")).click()
            dismissDemoEntryPrompts()
            assertTrue("demo home never appeared", device.wait(Until.hasObject(By.textContains("Aisha (sample)")), 15000))
            assertTrue("demo banner missing on home", device.wait(Until.hasObject(By.textContains("Demo mode - offline sample data")), 5000))
            assertTrue("sample group missing", device.hasObject(By.textContains("Product Crew (sample)")))
            device.waitForIdle(2000)
            captureNamed("demo-home.png")

            // Chat detail keeps the demo context visible.
            device.findObject(By.textContains("Aisha (sample)")).click()
            assertTrue("chat never opened", device.wait(Until.hasObject(By.textContains("Exactly. Everything here is offline demo data.")), 10000))
            assertTrue("demo banner missing in chat", device.wait(Until.hasObject(By.textContains("Demo mode - offline sample data")), 5000))
            device.waitForIdle(2000)
            captureNamed("demo-chat.png")

            // Exit demo -> back to Welcome, session cleared.
            device.findObject(By.text("Exit demo")).click()
            assertTrue("welcome never reappeared after exit", device.wait(Until.hasObject(By.textContains("Welcome")), 15000))
            assertTrue("demo session survived exit", !ServiceLocator.apiBackend.isDemoSession)
            assertTrue("still authenticated after demo exit", !ServiceLocator.apiBackend.isAuthenticated)
        }
    }
}
