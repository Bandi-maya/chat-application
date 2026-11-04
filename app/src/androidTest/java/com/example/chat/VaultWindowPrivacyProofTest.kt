package com.example.chat

import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.kit.ProtectSensitiveWindow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultWindowPrivacyProofTest {
    private fun secure(activity: ChatFixtureActivity) = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    private fun awaitFlag(scenario: ActivityScenario<ChatFixtureActivity>, expected: Boolean, label: String) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        var actual = !expected
        do {
            // Compose effects commit on a display frame, not merely main-looper idle.
            scenario.onActivity { actual = secure(it) }
            if (actual == expected) return
            Thread.sleep(32)
        } while (android.os.SystemClock.uptimeMillis() < deadline)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.getExternalFilesDir(null), "screenshots/vault-window-flags.txt")
        file.parentFile?.mkdirs()
        file.appendText("$label expected=$expected actual=$actual\n")
        assertEquals(label, expected, actual)
    }
    @Test fun protectedSurfaceSetsFlagAndRestoresOrdinaryWindow() {
        val active = mutableStateOf(true)
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                activity.setContent { ProtectSensitiveWindow(active.value) }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            awaitFlag(scenario,true,"Protected surface must set FLAG_SECURE")
            scenario.onActivity { active.value = false }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            awaitFlag(scenario,false,"Ordinary surface must restore its original flag")
        } finally { scenario.close() }
    }
    @Test fun preExistingProtectionIsNotCleared() {
        val active = mutableStateOf(true)
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                activity.setContent { ProtectSensitiveWindow(active.value) }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { active.value = false }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            awaitFlag(scenario,true,"Pre-existing protection must remain")
        } finally { scenario.close() }
    }
}
