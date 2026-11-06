package com.example.chat

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemeController
import com.example.chat.core.theme.ThemePresets
import com.example.chat.core.theme.ThemeStateStore
import com.example.chat.ui.theme.ChatyTheme
import com.example.chat.ui.theme.SyncChatySystemTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic config changes, real theme/controller/rendering. No user's saved theme changed. */
@RunWith(AndroidJUnit4::class)
class SystemThemeFollowProofTest {
    @Test fun systemChangesWithoutRecreateAndManualOverrideStays() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val controller = ThemeController(object : ThemeStateStore {
            override suspend fun loadThemeState() = emptyMap<String, Any?>()
            override suspend fun saveThemeState(data: Map<String, Any?>) = true
        }, scope)
        val night = mutableStateOf(false)
        val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        try {
            ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    var revision by remember { mutableIntStateOf(0) }
                    DisposableEffect(controller) {
                        val listener: () -> Unit = { revision++ }
                        controller.addListener(listener)
                        onDispose { controller.removeListener(listener) }
                    }
                    val config = Configuration(LocalConfiguration.current).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    }
                    CompositionLocalProvider(LocalConfiguration provides config) {
                        SyncChatySystemTheme(controller, revision)
                        ChatyTheme(controller.globalTheme) {
                            Column { Text("Theme dark ${controller.globalTheme.isDark}"); Text("Follow ${controller.followSystem}") }
                        }
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.text("Theme dark false")), 10000))
                inst.runOnMainSync { night.value = true }
                assertTrue(device.wait(Until.hasObject(By.text("Theme dark true")), 5000))
                device.takeScreenshot(File(dir, "system-theme-synthetic-dark.png"))
                inst.runOnMainSync { controller.setGlobalTheme(ThemePresets.warmNeutral) }
                assertTrue(device.wait(Until.hasObject(By.text("Follow false")), 5000))
                assertTrue(device.wait(Until.hasObject(By.text("Theme dark false")), 5000))
                inst.runOnMainSync { night.value = false }
                inst.waitForIdleSync()
                inst.runOnMainSync { night.value = true }
                inst.waitForIdleSync()
                assertTrue(device.wait(Until.hasObject(By.text("Theme dark false")), 5000))
                inst.runOnMainSync { controller.resetToDefaults() }
                assertTrue(device.wait(Until.hasObject(By.text("Theme dark true")), 5000))
                assertTrue(device.hasObject(By.text("Follow true")))
            }
        } finally { scope.cancel() }
    }
}
