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
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.calls.CallContactPickerScreen
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Proof for the requested call contact picker: contacts listed, row starts a
 *  voice call for that contact, dialpad toggle switches modes. */
@RunWith(AndroidJUnit4::class)
class CallContactPickerProofTest {
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

    private fun contact(id: String, name: String) = UserProfile(
        id = id, displayName = name, username = id,
        avatarInitials = name.split(" ").take(2).joinToString("") { it.take(1) },
        avatarColorHex = "#2AA198", about = "Synthetic contact",
        lastSeenAt = Instant.EPOCH, email = "$id@synthetic.example", phone = "+1555000$id",
    )

    @Test
    fun contactsListedRowStartsVoiceCallAndDialpadToggles() {
        val calls = CopyOnWriteArrayList<Pair<String, Boolean>>()
        val contacts = listOf(
            contact("c01", "Asha Verma"),
            contact("c02", "Rohan Iyer"),
            contact("c03", "Meera Nair"),
        )
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                ChatyTheme(ThemePresets.whatsappIosLight) {
                    CallContactPickerScreen(
                        contacts = contacts,
                        currentUserId = "me",
                        onBack = {},
                        onStartCall = { c, video -> calls.add(c.id to video) },
                        showToast = { _, _ -> },
                    )
                }
            }
        }
        for (name in listOf("Asha Verma", "Rohan Iyer", "Meera Nair")) {
            assertTrue("contact '$name' not listed", dev.wait(Until.hasObject(By.text(name)), 8000))
        }
        shot("call-contact-picker-contacts")
        dev.findObject(By.text("Asha Verma")).click()
        Thread.sleep(900)
        assertEquals("tapping a contact row must start a voice call for it", listOf("c01" to false), calls.toList())
        shot("call-contact-picker-starting")
        val toggle = dev.wait(Until.findObject(By.desc("Open dialpad")), 5000)
        assertNotNull("dialpad toggle missing", toggle)
        toggle.click()
        assertTrue("dialpad mode did not open", dev.wait(Until.hasObject(By.desc("Show contacts")), 5000))
        shot("call-contact-picker-dialpad")
        dev.findObject(By.desc("Show contacts")).click()
        assertTrue("contacts mode did not return", dev.wait(Until.hasObject(By.desc("Open dialpad")), 5000))
        scenario.close()
    }
}
