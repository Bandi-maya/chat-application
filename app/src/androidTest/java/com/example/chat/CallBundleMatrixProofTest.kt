package com.example.chat

import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
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
import com.example.chat.ui.calls.OngoingCallScreen
import com.example.chat.ui.calls.bundles.*
import com.example.chat.ui.settings.CallPresentationState
import com.example.chat.ui.settings.CallSettingsScreen
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/** Production composables, synthetic sessions/callbacks only. No signalling/media.
 * Not a live call, hardware/audio, notification, migration or performance proof. */
@RunWith(AndroidJUnit4::class)
class CallBundleMatrixProofTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val dev get() = UiDevice.getInstance(ins)
    private fun capture(name: String) {
        dev.waitForIdle(1500)
        val dir = File(ins.targetContext.getExternalFilesDir(null), "call-matrix").apply { mkdirs() }
        val bmp = ins.uiAutomation.takeScreenshot() ?: error("No screenshot: $name")
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        dev.dumpWindowHierarchy(File(dir, "$name.xml"))
        File(dir, "SCOPE.txt").writeText("Production composables; synthetic sessions; no real media/signalling/account; no 144Hz claim")
    }
    @Test fun allBundlesIncomingAndOngoingStatePixels() {
        for (bundle in CallBundleId.entries) {
            for (state in listOf(CallSessionState.incoming, CallSessionState.ringing,
                CallSessionState.connecting, CallSessionState.connected,
                CallSessionState.reconnecting, CallSessionState.busy, CallSessionState.failed)) {
                val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
                try {
                    val session = ChatyCallSession("matrix-${bundle.id}-$state", "synthetic-peer",
                        "Synthetic Caller", isVideo = false, isOutgoing = state != CallSessionState.incoming,
                        state = state, startedAt = Instant.EPOCH, isDemo = true)
                    val presentation = resolveCallBundlePresentation(bundle.id, state)
                    scenario.onActivity { activity -> activity.setContent {
                        ChatyTheme(ThemePresets.monochromeDark) {
                            if (presentation.incoming) BundleIncomingCallView(bundle, session, {}, {})
                            else OngoingCallScreen(session = session,
                                callDurationSeconds = if (presentation.showConnectedDuration) 142 else 0,
                                hasLocalMedia = false, videoSurfaces = null, setupError = null,
                                template = presentation.template, bundleId = bundle,
                                pictureInPictureEnabled = false, observeNativePip = false,
                                autoHideControls = false, onBack = {}, onSessionGone = {}, onEndCall = {},
                                onToggleMute = {}, onToggleCamera = {}, onSwitchCamera = {},
                                onToggleScreenShare = {}, onToggleAudioRoute = {}, onEnterNativePip = {})
                        }
                    } }
                    assertTrue("Caller missing ${bundle.id}/$state", dev.wait(Until.hasObject(By.text("Synthetic Caller")), 8000))
                    if (state != CallSessionState.incoming) {
                        assertFalse("Outgoing/active must not offer Answer", dev.hasObject(By.text("Answer")))
                        assertTrue("End action missing", dev.hasObject(By.desc("End call")))
                    }
                    capture("${bundle.id}-$state-voice")
                } finally { scenario.close() }
            }
        }
    }
    @Test fun googleLatestWidthsAndFontScales() {
        for (width in listOf(220, 360)) for (font in listOf(1f, 1.5f)) {
            for (state in listOf(CallSessionState.ringing, CallSessionState.connecting, CallSessionState.connected)) {
                val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
                try {
                    val session = ChatyCallSession("google-width-$width-$font-$state", "synthetic-peer",
                        "Synthetic Caller", isVideo = false, isOutgoing = true, state = state,
                        startedAt = Instant.EPOCH, isDemo = true)
                    val presentation = resolveCallBundlePresentation(CallBundleId.GOOGLE_PHONE_LATEST.id, state)
                    scenario.onActivity { activity -> activity.setContent {
                        val density = androidx.compose.ui.platform.LocalDensity.current.density
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density, font)) {
                            ChatyTheme(ThemePresets.monochromeDark) {
                                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier
                                    .width(width.dp)) {
                                    OngoingCallScreen(session = session, callDurationSeconds = if (state == CallSessionState.connected) 142 else 0,
                                        hasLocalMedia = false, videoSurfaces = null, setupError = null,
                                        template = presentation.template, bundleId = CallBundleId.GOOGLE_PHONE_LATEST,
                                        pictureInPictureEnabled = false, observeNativePip = false,
                                        autoHideControls = false, onBack = {}, onSessionGone = {}, onEndCall = {},
                                        onToggleMute = {}, onToggleCamera = {}, onSwitchCamera = {},
                                        onToggleScreenShare = {}, onToggleAudioRoute = {}, onEnterNativePip = {})
                                }
                            }
                        }
                    } }
                    assertTrue(dev.wait(Until.hasObject(By.text("Synthetic Caller")), 8000))
                    assertTrue("End action missing", dev.hasObject(By.desc("End call")))
                    assertFalse("Outgoing must not offer Answer", dev.hasObject(By.text("Answer")))
                    capture("google-latest-$state-width$width-font$font")
                } finally { scenario.close() }
            }
        }
    }
    @Test fun callsSettingsHasOnlyGlobalBundleChoice() {
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        try {
            scenario.onActivity { activity -> activity.setContent {
                ChatyTheme(ThemePresets.monochromeDark) {
                    CallSettingsScreen(preferences = ServiceLocator.preferencesController,
                        call = CallPresentationState(incomingCallTemplate = "sheet", ongoingCallTemplate = "card"),
                        templates = emptyList(), contacts = emptyList(), onBack = {},
                        onIncomingTemplate = {}, onOngoingTemplate = {}, onDynamicIsland = {},
                        onPictureInPicture = {}, onLowData = {}, onOpenAudioSettings = {})
                }
            } }
            assertTrue(dev.wait(Until.hasObject(By.text("Selected Call Bundle")), 8000))
            assertFalse(dev.hasObject(By.text("Incoming Call Screen")))
            assertFalse(dev.hasObject(By.text("Ongoing Call Screen")))
            capture("calls-settings-global-only")
        } finally { scenario.close() }
    }
}
