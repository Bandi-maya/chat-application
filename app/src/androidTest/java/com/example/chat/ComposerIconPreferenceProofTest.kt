package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.templates.ComposerActionPlacement
import com.example.chat.core.templates.ComposerTemplate
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.chats.detail.ChatEntryBar
import com.example.chat.ui.chats.detail.ComposerColors
import com.example.chat.ui.icons.*
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real shared composer pixels with synthetic local settings only. No account or send. */
@RunWith(AndroidJUnit4::class)
class ComposerIconPreferenceProofTest {
    @Test fun perIconSendPreferenceChangesActualComposerPixels() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        ActivityScenario.launch(ChatFixtureActivity::class.java).use { scenario ->
            fun show(variant: ChatyIconThemeId): Bitmap {
                scenario.onActivity { activity -> activity.setContent {
                    val theme = ThemePresets.getSystemDefaultTheme(false)
                    ChatyTheme(theme) {
                        CompositionLocalProvider(LocalChatyIconTheme provides ChatyIconThemeId.chatySigil,
                            LocalChatyIconVariants provides mapOf(ChatyIconId.send.name to variant.name)) {
                            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) {
                                ChatEntryBar(entryStyle = com.example.chat.ui.chats.detail.STOCK_ENTRY_STYLE, colors = ComposerColors.resolve(theme),
                                    template = ComposerTemplate(ComposerActionPlacement.integrated, enableSendMorph = false), theme = theme,
                                    text = "Synthetic draft", onTextChange = {})
                            }
                        }
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.desc("Send message")), 10000))
                inst.waitForIdleSync()
                val bounds = device.findObject(By.desc("Send message")).visibleBounds
                assertTrue(bounds.width() > 0 && bounds.height() > 0)
                val screenshot = inst.uiAutomation.takeScreenshot() ?: throw AssertionError("No pixels")
                val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                FileOutputStream(File(dir, "10-composer-${variant.name}.png")).use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                val crop = Bitmap.createBitmap(screenshot, bounds.left, bounds.top, bounds.width(), bounds.height())
                screenshot.recycle()
                return crop
            }
            val first = show(ChatyIconThemeId.precisionContour)
            val second = show(ChatyIconThemeId.monolithicCutout)
            try { assertFalse("Per-icon preference must affect actual composer pixels", first.sameAs(second)) }
            finally { first.recycle(); second.recycle() }
        }
    }
}
