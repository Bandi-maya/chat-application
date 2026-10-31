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
        val bmp = ins.uiAutomation.takeScreenshot() ?: return
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
    private fun incomingSession(name: String) = ChatyCallSession(
        callId = "bundle-proof-1",
        remoteUserId = "bundle-proof-user",
        remoteDisplayName = name,
        isVideo = false,
        isOutgoing = false,
        state = CallSessionState.incoming,
        startedAt = Instant.EPOCH,
    )

    private fun swipeFrom(desc: String, dx: Int, dy: Int) {
        val obj = dev.findObject(By.desc(desc)) ?: fail("control '$desc' not found")
        val b = obj.visibleBounds
        dev.swipe(b.centerX(), b.centerY(), b.centerX() + dx, b.centerY() + dy, 60)
    }

    /**
     * Bundle incoming-call gesture proofs (user request 2026-10-31: distinct reference
     * layouts with the real answer motion per family). Each family is rendered via the
     * real BundleIncomingCallView; the reference answer/decline motion is driven with
     * UiDevice swipes and must fire the REAL onAccept/onDecline callbacks. A small drag
     * must snap back and fire nothing (negative control).
     */
    private fun runGestureCase(
        tag: String,
        bundle: CallBundleId,
        controlDesc: String,
        acceptDelta: Pair<Int, Int>,
        declineDelta: Pair<Int, Int>?,
        declineDesc: String? = null,
    ) {
        val accepted = java.util.concurrent.atomic.AtomicBoolean(false)
        val declined = java.util.concurrent.atomic.AtomicBoolean(false)
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    ChatyTheme(ThemePresets.whatsappIosLight) {
                        BundleIncomingCallView(
                            bundleId = bundle,
                            session = incomingSession("Gesture Caller"),
                            onAccept = { accepted.set(true) },
                            onDecline = { declined.set(true) },
                        )
                    }
                }
            }
            assertTrue("$tag: control '$controlDesc' missing", dev.wait(Until.hasObject(By.desc(controlDesc)), 8000))
            shot("bundle-incoming-$tag")

            swipeFrom(controlDesc, acceptDelta.first, acceptDelta.second)
            dev.waitForIdle(3000)
            Thread.sleep(1200)
            assertTrue("$tag: reference answer motion did not fire onAccept", accepted.get())
            assertFalse("$tag: answer motion must not fire onDecline", declined.get())

            if (declineDelta != null) {
                accepted.set(false); declined.set(false)
                swipeFrom(declineDesc ?: controlDesc, declineDelta.first, declineDelta.second)
                dev.waitForIdle(3000)
                Thread.sleep(1200)
                assertTrue("$tag: reference decline motion did not fire onDecline", declined.get())
                assertFalse("$tag: decline motion must not fire onAccept", accepted.get())
            }

            // Negative control: a small wiggle snaps back and triggers nothing.
            accepted.set(false); declined.set(false)
            val (wx, wy) = if (kotlin.math.abs(acceptDelta.first) >= kotlin.math.abs(acceptDelta.second)) 30 to 0 else 0 to 30
            swipeFrom(controlDesc, wx, wy)
            dev.waitForIdle(2000)
            Thread.sleep(900)
            assertFalse("$tag: small drag must not accept", accepted.get())
            assertFalse("$tag: small drag must not decline", declined.get())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun bundleIncomingReferenceGesturesFireRealCallbacks() {
        // Samsung One UI: drag the action circle OUT of its halo.
        runGestureCase(
            "samsung_one_ui", CallBundleId.SAMSUNG_ONE_UI,
            controlDesc = "Answer call", acceptDelta = 420 to 0,
            declineDelta = -420 to 0, declineDesc = "Decline call",
        )
        // Google Phone Latest: drag the center puck right = answer, left = decline.
        runGestureCase(
            "google_phone_latest", CallBundleId.GOOGLE_PHONE_LATEST,
            controlDesc = "Answer or decline puck", acceptDelta = 430 to 0, declineDelta = -430 to 0,
        )
        // Azure Ring Vertical: drag the ring down = answer, up = decline.
        runGestureCase(
            "azure_ring_vertical", CallBundleId.AZURE_RING_VERTICAL,
            controlDesc = "Azure ring answer or decline puck", acceptDelta = 0 to 430, declineDelta = 0 to -430,
        )
        // Mono Chevron Dark: drag the puck down = answer, up = decline.
        runGestureCase(
            "mono_chevron_dark", CallBundleId.MONO_CHEVRON_DARK,
            controlDesc = "Mono chevron answer or decline puck", acceptDelta = 0 to 430, declineDelta = 0 to -430,
        )
        // Nextcloud Talk: swipe up on the accept circle (tap also accepted by design).
        runGestureCase(
            "nextcloud_talk", CallBundleId.NEXTCLOUD_TALK,
            controlDesc = "Accept call", acceptDelta = 0 to -430,
            declineDelta = null, declineDesc = null,
        )
    }

    /**
     * Apply-path routing proof: persisting a bundle id through the REAL
     * CallPresentationPreferencesStore (same SharedPreferences backend the picker
     * writes via Settings > Calls > Call Experience Bundles) must be readable by a
     * fresh store instance, and CallBundleId.fromId(persisted) must route to that
     * family's incoming view - the same fromId + BundleIncomingCallView routing
     * GlobalCallHost uses for a live incoming call.
     */
    @Test
    fun appliedBundlePersistsAndRoutesIncomingView() {
        val appCtx = ins.targetContext
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )
        val store = CallPresentationPreferencesStore(AndroidCallPresentationPrefsStorage(appCtx), scope)
        store.initialize()
        var deadline = System.currentTimeMillis() + 8000
        while (!store.isInitialized) {
            if (System.currentTimeMillis() > deadline) fail("store did not initialize")
            Thread.sleep(50)
        }
        val original = store.value.callExperienceBundleId
        try {
            store.setCallExperienceBundle("mono_chevron_dark")
            deadline = System.currentTimeMillis() + 8000
            while (store.value.callExperienceBundleId != "mono_chevron_dark") {
                if (System.currentTimeMillis() > deadline) fail("bundle id did not persist in time")
                Thread.sleep(50)
            }

            // A fresh store over the same backend proves the write persisted.
            val store2 = CallPresentationPreferencesStore(AndroidCallPresentationPrefsStorage(appCtx), scope)
            store2.initialize()
            deadline = System.currentTimeMillis() + 8000
            while (!store2.isInitialized) {
                if (System.currentTimeMillis() > deadline) fail("second store did not initialize")
                Thread.sleep(50)
            }
            assertEquals(
                "persisted bundle id must survive a fresh store instance",
                "mono_chevron_dark", store2.value.callExperienceBundleId,
            )

            // Route exactly like GlobalCallHost: fromId(prefs) -> BundleIncomingCallView.
            val accepted = java.util.concurrent.atomic.AtomicBoolean(false)
            val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
            try {
                scenario.onActivity { activity ->
                    activity.setContent {
                        ChatyTheme(ThemePresets.whatsappIosLight) {
                            BundleIncomingCallView(
                                bundleId = CallBundleId.fromId(store2.value.callExperienceBundleId),
                                session = incomingSession("Persist Caller"),
                                onAccept = { accepted.set(true) },
                                onDecline = {},
                            )
                        }
                    }
                }
                assertTrue(
                    "persisted id must route to the Mono Chevron incoming view",
                    dev.wait(Until.hasObject(By.desc("Mono chevron answer or decline puck")), 8000),
                )
                shot("bundle-applied-mono-chevron")
                swipeFrom("Mono chevron answer or decline puck", 0, 430)
                dev.waitForIdle(3000)
                Thread.sleep(1200)
                assertTrue("routed view's answer gesture must fire onAccept", accepted.get())
            } finally {
                scenario.close()
            }
        } finally {
            store.setCallExperienceBundle(original)
            Thread.sleep(500)
        }
    }
}
}

