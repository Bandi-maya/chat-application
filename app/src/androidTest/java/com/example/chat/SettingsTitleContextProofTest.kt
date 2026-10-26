package com.example.chat

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.preferences.*
import com.example.chat.core.theme.ThemeController
import com.example.chat.core.theme.ThemePresets
import com.example.chat.core.theme.ThemeStateStore
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.navigation.LocalDestinationTitlePresentation
import com.example.chat.ui.settings.SettingsRootScreen
import com.example.chat.ui.theme.ChatyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Real SettingsRootScreen with fake storage/auth only. No actual account changes. */
@RunWith(AndroidJUnit4::class)
class SettingsTitleContextProofTest {
    @Test fun labelOnlyIsContextualActionsAndBackRemain() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val store = object : PreferencesBlobStore {
            override suspend fun load(userId: String?) = emptyMap<String, Any?>()
            override suspend fun hasScoped(userId: String) = false
            override suspend fun save(userId: String?, data: Map<String, Any?>) {}
            override suspend fun clearGlobal() {}
            override suspend fun storedUserId(): String? = null
            override suspend fun setStoredUserId(userId: String?) {}
        }
        val lock = object : LockCredentialStore {
            override suspend fun hasCredential(method: String) = false
            override suspend fun setCredential(method: String, secret: String, pinLength: Int?) { throw AssertionError("No credential changes") }
        }
        val auth = object : PreferencesAuthSource {
            override fun currentUserId(): String? = null
            override fun addListener(listener: (String?) -> Unit) {}
            override fun removeListener(listener: (String?) -> Unit) {}
        }
        val prefs = ChatyPreferencesController(store, null, lock, auth, scope)
        val theme = ThemeController(object : ThemeStateStore {
            override suspend fun loadThemeState() = emptyMap<String, Any?>()
            override suspend fun saveThemeState(data: Map<String, Any?>) = true
        }, scope)
        val user = UserProfile("synthetic", "Synthetic Person", "fixture", "SP", "0xFF6366F1", "", lastSeenAt = Instant.now())
        try {
            ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
                for ((context, back, visible) in listOf(Triple("settings", false, false), Triple(null, false, true), Triple("settings", true, true))) {
                    scenario.onActivity { activity -> activity.setContent {
                        CompositionLocalProvider(LocalDestinationTitlePresentation provides context) {
                            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                                SettingsRootScreen(prefs, theme, user, back, {}, {}, {}, {}, {})
                            }
                        }
                    } }
                    assertTrue(device.wait(Until.hasObject(By.desc("Edit profile")), 10000))
                    device.waitForIdle()
                    assertEquals("Only duplicate label is hidden", visible, device.findObject(By.text("Settings")) != null)
                    for (label in listOf("Edit profile", "Search settings", "QR code")) {
                        assertTrue("Action retained: $label", device.findObject(By.desc(label)) != null)
                    }
                    if (back) assertTrue("Back retained", device.findObject(By.desc("Back")) != null)
                }
            }
        } finally { scope.cancel() }
    }
}
