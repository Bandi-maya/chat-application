package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real production screens, synthetic callbacks. Does not prove AppRoot wiring or live persistence. */
@RunWith(AndroidJUnit4::class)
class ContactsProfileRouteProofTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(inst)
    private fun capture(name: String) {
        val image = inst.uiAutomation.takeScreenshot() ?: error("No pixels")
        val file = File(inst.targetContext.getExternalFilesDir(null), "screenshots/contacts-profile-$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun click(text: String) {
        assertTrue("Missing $text", device.wait(Until.hasObject(By.text(text)), 5000))
        device.findObject(By.text(text)).click()
        inst.waitForIdleSync()
    }
    private fun awaitValue(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertTrue(message, condition())
    }
    @Test fun dirtyBackKeepEditingThenDiscardDoesNotSave() {
        ActivityScenario.launch<ContactsProfileProofActivity>(Intent(inst.targetContext, ContactsProfileProofActivity::class.java)).use { scenario ->
            var host: ContactsProfileProofActivity? = null
            scenario.onActivity { host = it }
            assertTrue(device.wait(Until.hasObject(By.text("Fixture Maya")), 10000))
            device.findObject(By.text("Fixture Maya")).text = "Changed fixture"
            device.findObject(By.desc("Back")).click()
            click("Keep Editing")
            assertEquals(0, host!!.backCount)
            assertEquals(0, host!!.saveCount)
            device.findObject(By.desc("Back")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Discard changes?")), 5000))
            capture("discard-dialog")
            click("Discard")
            assertEquals(1, host!!.backCount)
            assertEquals(0, host!!.saveCount)
        }
    }
    @Test fun cancelledAvatarAndBannerLeaveCleanEditor() {
        ActivityScenario.launch<ContactsProfileProofActivity>(Intent(inst.targetContext, ContactsProfileProofActivity::class.java)).use { scenario ->
            var host: ContactsProfileProofActivity? = null
            scenario.onActivity { host = it }
            assertTrue(device.wait(Until.hasObject(By.desc("Change avatar")), 10000))
            for (description in listOf("Change avatar", "Change banner photo")) {
                device.findObject(By.desc(description)).click()
                click("Pick / Choose from Gallery")
                awaitValue("Picker callback completed") { host!!.pickCount == if (description == "Change avatar") 1 else 2 }
                assertTrue(device.wait(Until.hasObject(By.text("Save")), 5000))
            }
            device.findObject(By.desc("Back")).click()
            awaitValue("Clean editor should leave") { host!!.backCount == 1 }
            assertEquals(0, host!!.saveCount)
            assertFalse(device.hasObject(By.text("Discard changes?")))
            capture("cancelled-media-clean")
        }
    }
    @Test fun saveFailureKeepsEditorAndUsesTrimmedValues() {
        ActivityScenario.launch<ContactsProfileProofActivity>(Intent(inst.targetContext, ContactsProfileProofActivity::class.java).putExtra("saveFailure", true)).use { scenario ->
            var host: ContactsProfileProofActivity? = null
            scenario.onActivity { host = it }
            assertTrue(device.wait(Until.hasObject(By.text("Fixture Maya")), 10000))
            device.findObject(By.text("Fixture Maya")).text = "  Updated Fixture  "
            click("Save")
            awaitValue("Save invoked") { host!!.saveCount == 1 }
            assertEquals(listOf("Updated Fixture", "fixture_me", "Synthetic about", null, null), host!!.saved)
            assertTrue(device.wait(Until.hasObject(By.text("Could not save profile: Synthetic save failure")), 8000))
            assertEquals(0, host!!.backCount)
            capture("save-failure")
        }
    }
    @Test fun successfulSaveCallsBackOnceAndLeaves() {
        ActivityScenario.launch<ContactsProfileProofActivity>(Intent(inst.targetContext, ContactsProfileProofActivity::class.java)).use { scenario ->
            var host: ContactsProfileProofActivity? = null
            scenario.onActivity { host = it }
            assertTrue(device.wait(Until.hasObject(By.text("Fixture Maya")), 10000))
            click("Save")
            awaitValue("Successful save exits after snackbar") { host!!.backCount == 1 }
            assertEquals(1, host!!.saveCount)
            assertEquals("Fixture Maya", host!!.saved.first())
            capture("saved")
        }
    }
    @Test fun contactPrivacyFailureRollsBackSyntheticSwitch() {
        ActivityScenario.launch<ContactsProfileProofActivity>(Intent(inst.targetContext, ContactsProfileProofActivity::class.java).putExtra("privacy", true)).use { scenario ->
            var host: ContactsProfileProofActivity? = null
            scenario.onActivity { host = it }
            assertTrue(device.wait(Until.hasObject(By.text("Privacy: Fixture Member")), 10000))
            assertTrue(device.wait(Until.hasObject(By.checkable(true)), 5000))
            val switches = device.findObjects(By.checkable(true))
            assertFalse(switches.first().isChecked)
            switches.first().click()
            assertTrue(device.wait(Until.hasObject(By.text("Synthetic privacy failure")), 5000))
            assertFalse("Failure must roll back", device.findObjects(By.checkable(true)).first().isChecked)
            assertEquals(1, host!!.privacySaveCount)
            assertEquals("me", host!!.savedPrivacy!!.ownerUserId)
            assertEquals("peer", host!!.savedPrivacy!!.targetUserId)
            assertTrue(host!!.savedPrivacy!!.hideDeliveryReceipts)
            capture("privacy-rollback")
        }
    }
    @Test fun groupMembersAdminAndLeaveAreSyntheticCallbacks() {
        ActivityScenario.launch<ContactsProfileProofActivity>(Intent(inst.targetContext, ContactsProfileProofActivity::class.java).putExtra("group", true)).use { scenario ->
            var host: ContactsProfileProofActivity? = null
            scenario.onActivity { host = it }
            assertTrue(device.wait(Until.hasObject(By.text("Fixture Group")), 10000))
            assertTrue(device.hasObject(By.text("Fixture Maya (You)")))
            assertTrue(device.hasObject(By.text("Admin")))
            assertTrue(device.hasObject(By.text("Fixture Member")))
            capture("group-members")
            if (!device.hasObject(By.text("Leave Group"))) device.swipe(device.displayWidth/2, device.displayHeight*3/4, device.displayWidth/2, device.displayHeight/4, 30)
            click("Leave Group")
            assertEquals(1, host!!.leaveCount)
            assertEquals(0, host!!.backCount)
        }
    }
}
