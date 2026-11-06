package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.preferences.*
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConnectionHealth
import com.example.chat.ui.chats.detail.ConnectionHealthIndicator
import com.example.chat.ui.icons.ProvideChatyIconTheme
import com.example.chat.ui.settings.universal.UniversalChatyIconsScreen
import com.example.chat.ui.theme.ChatyTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Production indicator/provider/reset modal with isolated memory-only prefs.
 * Fresh EMPTY STORE, not an APK uninstall/reinstall. No real user/account writes.
 * Captures require human pixel review; semantic pass alone does not prove glyph geometry. */
@RunWith(AndroidJUnit4::class)
class SignalIconPackProofTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(inst)
    private class MemoryStore : PreferencesBlobStore {
        var saved: Map<String, Any?> = emptyMap()
        override suspend fun load(userId: String?) = saved
        override suspend fun hasScoped(userId: String) = false
        override suspend fun save(userId: String?, data: Map<String, Any?>) { saved = data }
        override suspend fun clearGlobal() { saved = emptyMap() }
        override suspend fun storedUserId(): String? = null
        override suspend fun setStoredUserId(userId: String?) {}
    }
    private fun controller(scope: CoroutineScope): ChatyPreferencesController = ChatyPreferencesController(
        MemoryStore(), null,
        object : LockCredentialStore {
            override suspend fun hasCredential(method: String) = false
            override suspend fun setCredential(method: String, secret: String, pinLength: Int?) { error("No lock mutation") }
        },
        object : PreferencesAuthSource {
            override fun currentUserId(): String? = null
            override fun addListener(listener: (String?) -> Unit) {}
            override fun removeListener(listener: (String?) -> Unit) {}
        }, scope,
    )
    private fun ready(prefs: ChatyPreferencesController) {
        val deadline = android.os.SystemClock.uptimeMillis() + 8000
        var loaded = false
        while (!loaded && android.os.SystemClock.uptimeMillis() < deadline) {
            inst.runOnMainSync { loaded = prefs.preferencesReady }
            if (!loaded) android.os.SystemClock.sleep(30)
        }
        assertTrue("Isolated preferences did not finish loading", loaded)
    }
    private fun capture(name: String) {
        device.waitForIdle(1500)
        val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots/signal-icon-proof").apply { mkdirs() }
        val bitmap = inst.uiAutomation.takeScreenshot() ?: error("Missing pixels: $name")
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        device.dumpWindowHierarchy(File(dir, "$name.xml"))
        File(dir, "SCOPE.txt").writeText("Production ConnectionHealthIndicator + icon provider + reset screen; synthetic health, empty memory store, no real account or network. Empty-store default is not uninstall/reinstall migration proof. Human visual review of glyphs, tints, strength mask/offline slash still required.")
    }
    @Composable private fun SignalStates() {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = com.example.chat.ui.theme.LocalAppColors.current.background,
            contentColor = com.example.chat.ui.theme.LocalAppColors.current.foreground) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Signal pack proof")
            for (health in ConnectionHealth.values()) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(health.name)
                    ConnectionHealthIndicator(health, size = 18.dp, onTap = {})
                    ConnectionHealthIndicator(health, size = 36.dp)
                }
            }
        }
        }
    }
    private fun assertStates() {
        for (label in listOf("Connection excellent", "Connection weak", "Connection poor", "No internet connection")) {
            assertTrue("Missing signal state $label", device.wait(Until.hasObject(By.desc(label)), 5000))
        }
    }
    @Test fun emptyStoreDefaultAndExplicitPacksRenderAllFourSignalStatesLightDark() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val prefs = controller(scope)
        try {
            ready(prefs)
            assertEquals("Hugeicons Free", prefs.universal.iconTheme)
            for (pack in listOf("Hugeicons Free", "Chaty Orbit", "Technical Blueprint")) {
                inst.runOnMainSync { prefs.updateUniversal(prefs.universal.copy(iconTheme = pack, iconVariants = emptyMap())) }
                for (dark in listOf(false, true)) {
                    ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
                        scenario.onActivity { activity -> activity.setContent {
                            ChatyTheme(ThemePresets.getSystemDefaultTheme(!dark)) {
                                ProvideChatyIconTheme(prefs) { SignalStates() }
                            }
                        } }
                        assertStates()
                        capture("states-${pack.replace(' ', '-')}-${if (dark) "dark" else "light"}")
                    }
                }
                assertEquals("Explicit pack must remain selected", pack, prefs.universal.iconTheme)
            }
            // Exercise the per-icon override separately from the global pack.
            inst.runOnMainSync { prefs.updateUniversal(prefs.universal.copy(iconTheme = "Hugeicons Free", iconVariants = mapOf("signal" to "Technical Blueprint"))) }
            ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) { ProvideChatyIconTheme(prefs) { SignalStates() } }
                } }
                assertStates(); capture("signal-override-blueprint-global-hugeicons")
            }
        } finally { inst.runOnMainSync { prefs.dispose() }; scope.cancel() }
    }
    @Test fun realResetModalAppliesHugeiconsThenProviderRendersFourStates() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val prefs = controller(scope)
        try {
            ready(prefs)
            inst.runOnMainSync { prefs.updateUniversal(prefs.universal.copy(iconTheme = "Chaty Orbit", iconVariants = emptyMap())) }
            ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                        ProvideChatyIconTheme(prefs) { UniversalChatyIconsScreen(prefs, onBack = {}) }
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.text("Reset")), 8000))
                capture("reset-before-explicit-orbit")
                device.findObject(By.text("Reset")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Reset icons")), 5000))
                assertTrue(device.wait(Until.hasObject(By.text("Hugeicons Free")), 5000))
                device.findObject(By.text("Hugeicons Free")).click()
                capture("reset-modal-hugeicons-selected")
                val apply = device.findObject(By.text("Apply")) ?: error("Reset Apply missing")
                assertTrue("Reset Apply must enable after selection", apply.isEnabled)
                apply.click()
                assertTrue(device.wait(Until.gone(By.text("Reset icons")), 5000))
                inst.runOnMainSync { assertEquals("Hugeicons Free", prefs.universal.iconTheme) }
                capture("reset-after-hugeicons-active")
                scenario.onActivity { activity -> activity.setContent {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) { ProvideChatyIconTheme(prefs) { SignalStates() } }
                } }
                assertStates(); capture("reset-result-four-signal-states")
            }
        } finally { inst.runOnMainSync { prefs.dispose() }; scope.cancel() }
    }
}
