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
    @Test fun protectedSurfaceSetsFlagAndRestoresOrdinaryWindow() {
        val active = mutableStateOf(true)
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                activity.setContent { ProtectSensitiveWindow(active.value) }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { assertTrue(secure(it)); active.value = false }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { assertFalse(secure(it)) }
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
            scenario.onActivity { assertTrue(secure(it)) }
        } finally { scenario.close() }
    }
}
