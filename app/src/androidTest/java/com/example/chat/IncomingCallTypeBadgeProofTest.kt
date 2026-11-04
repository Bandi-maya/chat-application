package com.example.chat

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.data.services.CallSessionState
import com.example.chat.data.services.ChatyCallSession
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.calls.AndroidCallPresentationPrefsStorage
import com.example.chat.ui.calls.CallContactPickerScreen
import com.example.chat.ui.calls.CallPresentationPreferencesStore
import com.example.chat.ui.calls.bundles.BundleIncomingCallView
import com.example.chat.ui.calls.bundles.CallBundleId
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingCallTypeBadgeProofTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val dev get() = UiDevice.getInstance(ins)

    private fun shot(name: String) {
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        dev.waitForIdle(3000)
        val bmp = ins.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun incomingSession(name: String) = ChatyCallSession(
        callId = "bundle-proof-1",
        remoteUserId = "bundle-proof-user",
        remoteDisplayName = name,
        isVideo = false,
        isOutgoing = false,
        state = CallSessionState.incoming,
        startedAt = Instant.EPOCH,
    )

    @Test
    fun everyIncomingBundleDisplaysVoiceOrVideoTypeBadge() {
        for (bundle in CallBundleId.entries) for (video in listOf(false, true)) {
            val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
            try {
                scenario.onActivity { activity ->
                    activity.setContent {
                        ChatyTheme(ThemePresets.whatsappIosLight) {
                            BundleIncomingCallView(bundle,
                                incomingSession("Synthetic Caller").copy(isVideo = video),
                                onAccept = {}, onDecline = {})
                        }
                    }
                }
                assertTrue("${bundle.id}: call-type badge missing",
                    dev.wait(Until.hasObject(By.desc(if (video) "Video call" else "Voice call")), 8000))
                shot("incoming-type-${bundle.id}-${if (video) "video" else "voice"}")
            } finally { scenario.close() }
        }
    }

}
