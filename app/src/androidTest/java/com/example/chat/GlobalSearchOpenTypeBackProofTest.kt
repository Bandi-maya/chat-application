package com.example.chat

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Global-search route proof (synthetic session only). Historical user report (Oct 26): tapping a search
 * entry closed the app. The INLINE header search already has coverage (HomeHeaderSearchConnectionProofTest);
 * this covers the OTHER surface: EmptyChatsState "Find people" -> AuthRoute.GlobalSearch ->
 * GlobalSearchScreen (AppRoot eager deps: apiBackend, preferencesController, localLockService). Seeds a FAKE
 * REST session (token "synthetic-test-token") into the app's own chaty_api_backend preferences, launches the
 * real MainActivity/AppRoot with NO conversations so the empty state shows, then: open Find people, assert
 * the search field composes (crash on open would kill the process/Activity), type a query, press Back, and
 * assert Home's empty state returns. REST base URL is the emulator loopback (10.0.2.2:8000); no live backend,
 * account, network, or messages. Diagnosis aid only: a crash yields the FATAL trace; a pass is route evidence,
 * not a fix claim.
 */
@RunWith(AndroidJUnit4::class)
class GlobalSearchOpenTypeBackProofTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val outDir: File
        get() = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

    private fun capture(device: UiDevice, name: String) {
        device.waitForIdle(3000)
        Thread.sleep(1500)
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(outDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun logScreenTexts(device: UiDevice, tag: String) {
        try {
            val out = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(out)
            val texts = Regex("text=\"([^\"]*)\"").findAll(out.toString("UTF-8")).map { it.groupValues[1] }.filter { it.isNotBlank() }.take(60).toList()
            android.util.Log.w("PixelProof", "SCREENTEXTS $tag texts=$texts")
            println("SCREENTEXTS $tag texts=$texts")
        } catch (_: Exception) {}
    }

    private fun dismissOnboardingPrompts(device: UiDevice) {
        repeat(4) {
            val btn = device.wait(Until.findObject(By.text("Not Now")), 8000) ?: return
            btn.click()
            device.waitForIdle()
            Thread.sleep(800)
        }
    }

    private fun setInitialized(value: Boolean) {
        val f = ServiceLocator.apiBackend.javaClass.getDeclaredField("initialized")
        f.isAccessible = true
        f.setBoolean(ServiceLocator.apiBackend, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(name: String): T {
        val f = ServiceLocator.apiBackend.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(ServiceLocator.apiBackend) as T
    }

    @Test
    fun findPeopleOpenTypeBackKeepsAppAlive() {
        val ctx = instrumentation.targetContext
        val prefs = ctx.getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("api_hub_token", "synthetic-test-token")
            .putString(
                "api_hub_user",
                """{"id":"synthetic-user-1","username":"testuser","displayName":"Test User","avatarInitials":"T","avatarColorHex":"0xFF6366F1","about":"Synthetic","email":"","phone":"","avatarUrl":null}""",
            )
            .commit()
        val device = UiDevice.getInstance(instrumentation)
        // Snapshot everything this test mutates; restore in finally (RealHostVideoHoldProofTest pattern).
        val priorPrefs = prefs.all.toMap()
        val oldRooms = field<MutableList<Conversation>>("_conversations").toList()
        val oldMessages = field<MutableMap<String, MutableList<ChatMessage>>>("_messages").toMap()
        setInitialized(false)
        ServiceLocator.apiBackend.initialize()
        assertTrue("synthetic session restored", ServiceLocator.apiBackend.isAuthenticated)
        // Empty state required: clear any in-memory conversations left by earlier tests in this process.
        field<MutableList<Conversation>>("_conversations").clear()
        field<MutableMap<String, MutableList<ChatMessage>>>("_messages").clear()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            dismissOnboardingPrompts(device)
            if (!device.wait(Until.hasObject(By.text("No conversations yet")), 30000)) {
                logScreenTexts(device, "empty-state"); capture(device, "gs-00-failure-state")
                throw AssertionError("empty chats state not shown (expected with no seeded conversations)")
            }
            val findPeople = device.wait(Until.findObject(By.text("Find people")), 5000)
            assertNotNull("Find people entry missing", findPeople)
            capture(device, "gs-01-empty-state")
            findPeople.click()
            val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000)
            if (field == null) { logScreenTexts(device, "global-search-open"); capture(device, "gs-02-open-failed") }
            assertNotNull("GlobalSearch field did not appear (crash on open?)", field)
            capture(device, "gs-02-global-search-open")
            field.text = "synthetic"
            device.waitForIdle()
            assertTrue("activity died after query input", scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
            capture(device, "gs-03-query-typed")
            device.pressBack() // dismiss IME if shown
            device.pressBack() // leave GlobalSearch
            device.waitForIdle()
            assertTrue("activity died after Back", scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
            if (!device.wait(Until.hasObject(By.text("No conversations yet")), 10000)) {
                logScreenTexts(device, "back-to-home"); capture(device, "gs-04-back-failed")
                throw AssertionError("Back from GlobalSearch did not return to Home empty state")
            }
            capture(device, "gs-04-back-home")
        } finally {
            scenario.close()
            field<MutableList<Conversation>>("_conversations").apply { clear(); addAll(oldRooms) }
            field<MutableMap<String, MutableList<ChatMessage>>>("_messages").apply { clear(); putAll(oldMessages) }
            prefs.edit().clear().apply {
                priorPrefs.forEach { (k, v) ->
                    when (v) {
                        is String -> putString(k, v); is Boolean -> putBoolean(k, v)
                        is Int -> putInt(k, v); is Long -> putLong(k, v); is Float -> putFloat(k, v)
                    }
                }
            }.commit()
            setInitialized(false)
            ServiceLocator.apiBackend.initialize()
        }
    }
}
