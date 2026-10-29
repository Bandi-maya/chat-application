package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.templates.ChatyNavigationDestinationIds
import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.navigation.PresetNavigationShell
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proof for the requested "labels on every navigation style": each
 * AppNavigationMode shell must show the destination labels (opening its drawer
 * first when the style has one). Failures are collected per style so one run
 * names every style that regressed.
 */
@RunWith(AndroidJUnit4::class)
class NavStyleLabelsProofTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val dev get() = UiDevice.getInstance(ins)

    private fun shot(name: String) {
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        dev.waitForIdle(3000)
        val bmp = dev.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    @Test
    fun labelsVisibleInEveryNavigationStyle() {
        val config = ThemePresets.whatsappIosLight
        val labels = listOf("Chats", "Updates", "Calls", "Settings")
        val modeState = mutableStateOf(AppNavigationMode.bottomNav)
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                ChatyTheme(config) {
                    key(modeState.value) {
                        PresetNavigationShell(
                            mode = modeState.value,
                            colors = ChatyColors(config),
                            navItemIds = ChatyNavigationDestinationIds.all,
                            selectedIndex = 0,
                            onSelect = {},
                            fab = {},
                            content = { Text("Synthetic page body") },
                        )
                    }
                }
            }
        }
        val failures = LinkedHashMap<String, List<String>>()
        val w = dev.displayWidth
        val h = dev.displayHeight
        for (m in AppNavigationMode.values()) {
            scenario.onActivity { modeState.value = m }
            dev.waitForIdle(3000)
            Thread.sleep(900)
            var missing = labels.filter { !dev.hasObject(By.text(it)) }
            var openedDrawer = false
            if (missing.isNotEmpty() && dev.hasObject(By.desc("Open menu"))) {
                dev.findObject(By.desc("Open menu")).click()
                openedDrawer = true
                Thread.sleep(900)
                dev.swipe(w / 2, (h * 0.7f).toInt(), w / 2, (h * 0.3f).toInt(), 24)
                Thread.sleep(500)
                missing = labels.filter { !dev.hasObject(By.text(it)) }
            }
            shot("nav-labels-${m.name}")
            if (missing.isNotEmpty()) failures[m.name] = missing
            if (openedDrawer) {
                dev.pressBack()
                Thread.sleep(400)
            }
        }
        // AppNavigationMode has 13 values; the request said "12 styles". The
        // assertion covers ALL actual enum modes, named here for the report.
        val modeNames = AppNavigationMode.values().joinToString { it.name }
        assertTrue("nav labels missing per style: $failures (modes checked: $modeNames)", failures.isEmpty())
        scenario.close()
    }
}
