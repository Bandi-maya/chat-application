package com.example.chat

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic fixture only (no account, session, network). Covers: Search open / type / Back does not close
 *  the process; the name tap is inert by default (opt-in off); the signal icon is the only Connection Health
 *  tap target and no banner strip is shown. */
@RunWith(AndroidJUnit4::class)
class HomeHeaderSearchConnectionProofTest {
    private fun host(nameTapOn: Boolean = false, block: (UiDevice, ActivityScenario<HomeHeaderProofActivity>) -> Unit) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val d = UiDevice.getInstance(inst)
        val intent = Intent(inst.targetContext, HomeHeaderProofActivity::class.java)
            .putExtra(HomeHeaderProofActivity.EXTRA_NAME_TAP_ON, nameTapOn)
        ActivityScenario.launch<HomeHeaderProofActivity>(intent).use { sc ->
            assertNotNull(d.wait(Until.findObject(By.textStartsWith("lock=")), 8000))
            block(d, sc)
        }
    }

    /** Taps the leftmost header text (the identity name column), never a guessed x: the connection signal
     *  icon sits right after the short name, so a fixed fraction of the width can land on it. */
    private fun tapNameText(d: UiDevice) {
        val y = d.findObject(By.desc("Search chats")).visibleCenter.y
        val name = d.findObjects(By.text(java.util.regex.Pattern.compile(".+")))
            .filter { !it.text.startsWith("lock=") && kotlin.math.abs(it.visibleCenter.y - y) < d.displayHeight / 12 }
            .minByOrNull { it.visibleBounds.left }
        assertNotNull("identity name text not found in header", name)
        val signal = d.findObject(By.desc("Connection weak"))
        assertTrue("name text must be left of the signal icon", name!!.visibleBounds.right <= signal.visibleBounds.left)
        name.click()
    }

    private fun counter(d: UiDevice, key: String): Int =
        Regex("$key=(\\d+)").find(d.findObject(By.textStartsWith("lock=")).text)!!.groupValues[1].toInt()

    @Test fun searchOpenTypeBackKeepsAppAlive() = host { d, sc ->
        val icon = d.wait(Until.findObject(By.desc("Search chats")), 5000)
        assertNotNull("Search icon missing", icon)
        icon.click()
        val field = d.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
        assertNotNull("search field did not appear (crash on open?)", field)
        field.text = "ali"
        d.waitForIdle()
        assertNotNull(d.wait(Until.findObject(By.textContains("Alice")), 3000))
        d.pressBack() // closes the IME if shown
        d.pressBack() // closes search
        d.waitForIdle()
        assertTrue("activity died", sc.state.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        assertNotNull(d.wait(Until.findObject(By.desc("Search chats")), 3000))
    }

    @Test fun nameTapIsInertByDefaultAndHealthIsOnlyOnSignalIcon() = host { d, _ ->
        // No banner strip text for the weak state.
        assertNull(d.findObject(By.textContains("Weak connection")))
        // Tap on plain header region left of the icons (the name area): no vault auth, no health dialog.
        tapNameText(d)
        d.waitForIdle()
        assertEquals("name tap must not request vault auth by default", 0, counter(d, "lock"))
        assertEquals(0, counter(d, "health"))
        // The signal icon is the only health target.
        d.findObject(By.desc("Connection weak")).click()
        d.waitForIdle()
        assertEquals(1, counter(d, "health"))
        assertEquals(0, counter(d, "lock"))
    }

    /** Opt-in ON: the same name-area tap now goes through the vault auth gate (lock=1), still no health dialog. */
    @Test fun nameTapOptInOnRequestsVaultAuth() = host(nameTapOn = true) { d, _ ->
        tapNameText(d)
        d.waitForIdle()
        assertEquals(1, counter(d, "lock"))
        assertEquals(0, counter(d, "health"))
    }
}
