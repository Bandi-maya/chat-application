package com.example.chat

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.UserProfile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/** TESTREF ONLY. Real MainActivity/AppRoot with an isolated fake REST session and
 * 20 in-memory synthetic rows. This is not production data, a login test, or backend proof.
 * Runtime APK must be built with http://10.0.2.2:8000, never a configured live server.
 */
@RunWith(AndroidJUnit4::class)
class ReferenceLayoutRuntimeProofTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(inst)
    private val out get() = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    @Suppress("UNCHECKED_CAST") private fun <T> field(name: String): T {
        val f = ServiceLocator.apiBackend.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(ServiceLocator.apiBackend) as T
    }
    private fun capture(name: String) {
        device.waitForIdle(1500)
        val bitmap = inst.uiAutomation.takeScreenshot() ?: error("Screenshot failed: $name")
        FileOutputStream(File(out, "fixture-$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        FileOutputStream(File(out, "fixture-$name.xml")).use { device.dumpWindowHierarchy(it) }
        File(out, "fixture-$name-style.txt").writeText(ServiceLocator.preferencesController.home.homeStyle)
    }
    private fun tap(label: String) {
        val found = device.wait(Until.findObject(By.desc(label)), 2000)
            ?: device.wait(Until.findObject(By.text(label)), 2000)
            ?: throw AssertionError("Missing actual destination: $label")
        found.click(); device.waitForIdle(1500)
    }
    private fun seedRows() {
        val now = Instant.now()
        val users = field<MutableList<UserProfile>>("_users")
        val rows = field<MutableList<Conversation>>("_conversations")
        rows.clear()
        for (i in 1..20) {
            val id = "synthetic-scroll-peer-$i"
            users.add(UserProfile(id=id, displayName="Synthetic peer $i", username="fixture$i",
                avatarInitials="S$i", avatarColorHex="0xFF6366F1", about="Isolated test only", lastSeenAt=now))
            rows.add(Conversation(id="synthetic-scroll-room-$i", type=ConversationType.direct,
                title="Synthetic peer $i", participantIds=listOf("synthetic-layout-user",id),
                avatarInitials="S$i", avatarColorHex="0xFF6366F1", lastMessageText="Synthetic preview $i",
                lastMessageTime=now.minusSeconds(i*60L), lastMessageSenderId=id,
                isPinned=i==1, unreadCount=if(i==2) 2 else 0))
        }
    }
    @Test fun configuredRealRootShowsRowsScrollAndPreservesSelectedPresentation() {
        val prefs = inst.targetContext.getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE)
        prefs.edit().putString("api_hub_token", "synthetic-test-token")
            .putString("api_hub_user", """{"id":"synthetic-layout-user","username":"fixture","displayName":"Synthetic Layout","avatarInitials":"SL","avatarColorHex":"0xFF6366F1","about":"Isolated test only","email":"","phone":"","avatarUrl":null}""").commit()
        try {
            val init = ServiceLocator.apiBackend.javaClass.getDeclaredField("initialized")
            init.isAccessible=true; init.setBoolean(ServiceLocator.apiBackend,false)
            ServiceLocator.apiBackend.initialize()
            assertTrue("Fake session restore failed", ServiceLocator.apiBackend.isAuthenticated)
            val deadline = System.currentTimeMillis()+10000
            while(!ServiceLocator.preferencesController.preferencesReady && System.currentTimeMillis()<deadline) Thread.sleep(100)
            assertTrue("Scoped preferences not ready",ServiceLocator.preferencesController.preferencesReady)
            seedRows()
            ServiceLocator.preferencesController.updateHome(ServiceLocator.preferencesController.home.copy(
                homeStyle="Chaty Default", disableStatusUnderName=true, hideChatSortList=true))
            ServiceLocator.themeController.setGlobalTheme(ThemePresets.monochromeDark)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                // Read the real modal stack through its own controls. The general host pass
                // normally marks changelog as seen, but this test must stand alone as well.
                repeat(6) {
                    val close = if (device.hasObject(By.text("Chaty changelog"))) device.findObject(By.text("Close")) else null
                    val prompt = close ?: device.findObject(By.text("Not now")) ?: device.findObject(By.text("Not Now"))
                    if (prompt != null) { prompt.click(); device.waitForIdle(1000) }
                    else Thread.sleep(400)
                }
                assertFalse("Changelog is still covering the tested UI", device.hasObject(By.text("Chaty changelog")))
                assertFalse("System permission prompt is still covering the tested UI",
                    device.hasObject(By.pkg("com.google.android.permissioncontroller")) ||
                    device.hasObject(By.pkg("com.android.permissioncontroller")))
                assertTrue("Real production Home rows missing", device.wait(Until.hasObject(By.text("Synthetic peer 1")),15000))
                capture("default-dark-expanded")
                val x=device.displayWidth/2; val bottom=device.displayHeight*3/4; val top=device.displayHeight*3/10
                device.swipe(x,bottom,x,top,40); capture("default-dark-collapse1")
                device.swipe(x,bottom,x,top,40); capture("default-dark-collapsed")
                device.swipe(x,top,x,bottom,40); capture("default-dark-expand1")
                device.swipe(x,top,x,bottom,40); capture("default-dark-expanded-again")
                for(label in listOf("Calls","Tasks","Settings","Chats")) {
                    tap(label); capture("default-dark-${label.lowercase()}")
                    assertEquals("Navigation changed style", "Chaty Default", ServiceLocator.preferencesController.home.homeStyle)
                }
                for((name,theme) in listOf("light" to ThemePresets.monochromeLight,"custom-ocean" to ThemePresets.ocean)) {
                    scenario.onActivity { ServiceLocator.themeController.setGlobalTheme(theme) }
                    Thread.sleep(500); capture("default-$name")
                }
                for(style in listOf("WhatsApp OLD UI","IOS STYLE","Curved Radial Menu")) {
                    scenario.onActivity { ServiceLocator.preferencesController.updateHome(ServiceLocator.preferencesController.home.copy(homeStyle=style)) }
                    Thread.sleep(500); capture("selected-${style.lowercase().replace(' ','-')}")
                    assertEquals(style,ServiceLocator.preferencesController.home.homeStyle)
                }
            }
        } finally {
            // Synthetic account only. Never copy these rows to the server or leave a real account signed in.
            prefs.edit().clear().commit()
        }
    }
}
