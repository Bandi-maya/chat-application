package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.preferences.*
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.settings.universal.UniversalStylesScreen
import com.example.chat.ui.theme.ChatyTheme
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual settings modal with fake local storage/auth. No real preference/account writes. */
@RunWith(AndroidJUnit4::class)
class EmojiPackModalProofTest {
    @Test fun singleRowOpensBoundedDialogAndBackDismisses() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
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
        try {
            ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
                        UniversalStylesScreen(preferences = prefs, onBack = {})
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.text("Styles (Look and feel)")), 10000))
                repeat(8) {
                    if (!device.hasObject(By.text("Emoji variant"))) {
                        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                            device.displayWidth / 2, device.displayHeight / 3, 15)
                    }
                }
                assertEquals(1, device.findObjects(By.text("Emoji variant")).size)
                device.findObject(By.text("Emoji variant")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Emoji pack")), 5000))
                val title = device.findObject(By.text("Emoji pack")).visibleBounds
                assertTrue("Modal title is within the screen", title.top > 0 && title.bottom < device.displayHeight)
                assertTrue(device.hasObject(By.text("Chaty")))
                val screenshot = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No modal pixels")
                val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/11-emoji-pack-modal.png")
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
                device.pressBack()
                assertTrue(device.wait(Until.gone(By.text("Emoji pack")), 5000))
                assertTrue(device.hasObject(By.text("Emoji variant")))
            }
        } finally { scope.cancel() }
    }
}
