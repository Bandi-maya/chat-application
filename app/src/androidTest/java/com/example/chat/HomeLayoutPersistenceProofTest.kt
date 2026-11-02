package com.example.chat

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.preferences.LocalPreferencesStorage
import com.example.chat.core.preferences.LockCredentialStore
import com.example.chat.core.preferences.PreferencesAuthSource
import com.example.chat.core.preferences.SaveState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Home layout choices ("apply and stay applied until the user changes them"):
 *  writes through the REAL SharedPreferences-backed storage, then reads back
 *  through a fresh storage instance and fresh controller.
 *
 *  Evidence label: this is a controller-recreation/read-back proof inside one
 *  process. It is NOT process-death persistence proof and does not verify UI
 *  re-render after relaunch. It covers the write path (updateHome ->
 *  OrderedBlobWriter -> commit()) and the load path (initLoad -> namespaced
 *  blob) that any relaunch depends on. */
@RunWith(AndroidJUnit4::class)
class HomeLayoutPersistenceProofTest {

    private val userId = "synthetic-layout-persistence-user"
    private val otherUserId = "synthetic-layout-persistence-other"

    private fun await(deadlineMs: Long = 15000, cond: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + deadlineMs
        while (SystemClock.uptimeMillis() < end) {
            if (cond()) return true
            SystemClock.sleep(50)
        }
        return cond()
    }

    private fun noTouchLock() = object : LockCredentialStore {
        override suspend fun hasCredential(method: String) = false
        override suspend fun setCredential(method: String, secret: String, pinLength: Int?) {
            throw AssertionError("No credential changes in this proof")
        }
    }

    private fun fixedAuth(uid: String) = object : PreferencesAuthSource {
        override fun currentUserId(): String? = uid
        override fun addListener(listener: (String?) -> Unit) {}
        override fun removeListener(listener: (String?) -> Unit) {}
    }

    @Test fun homeLayoutChoicesSurviveControllerRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val prefs1 = ChatyPreferencesController(
            AndroidPreferencesBlobStore(LocalPreferencesStorage(context)),
            null, noTouchLock(), fixedAuth(userId), scope1,
        )
        try {
            assertTrue("first controller finishes initial load", await { prefs1.preferencesReady })

            prefs1.updateHome(
                prefs1.home.copy(
                    homeStyle = "Proof Layout Style",
                    tabBubbleStyle = "Proof Square Tabs",
                    pagerTransition3d = "Proof Flip Pager",
                ),
            )
            assertTrue("local save reports success", await { prefs1.saveState.value == SaveState.Saved })
        } finally {
            scope1.cancel()
        }

        // Fresh storage instance + fresh controller over the same on-disk file:
        // the recreation half of the proof.
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val prefs2 = ChatyPreferencesController(
            AndroidPreferencesBlobStore(LocalPreferencesStorage(context)),
            null, noTouchLock(), fixedAuth(userId), scope2,
        )
        try {
            assertTrue("recreated controller finishes initial load", await { prefs2.preferencesReady })
            assertEquals("home style persists", "Proof Layout Style", prefs2.home.homeStyle)
            assertEquals("tab bubble style persists", "Proof Square Tabs", prefs2.home.tabBubbleStyle)
            assertEquals("pager transition persists", "Proof Flip Pager", prefs2.home.pagerTransition3d)
        } finally {
            scope2.cancel()
        }

        // Namespace control: another account must never read these choices.
        val scope3 = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val prefs3 = ChatyPreferencesController(
            AndroidPreferencesBlobStore(LocalPreferencesStorage(context)),
            null, noTouchLock(), fixedAuth(otherUserId), scope3,
        )
        try {
            assertTrue("other-user controller finishes initial load", await { prefs3.preferencesReady })
            assertEquals("other user keeps default home style", "Chaty Default", prefs3.home.homeStyle)
            assertEquals("other user keeps default tab style", "Capsule Pill", prefs3.home.tabBubbleStyle)
            assertEquals("other user keeps default pager transition", "Default", prefs3.home.pagerTransition3d)
        } finally {
            scope3.cancel()
        }

        // Cleanup: blank the synthetic namespaces so reruns start clean.
        val cleanup = LocalPreferencesStorage(context)
        cleanup.savePreferences(emptyMap(), userId)
        cleanup.savePreferences(emptyMap(), otherUserId)
        Unit
    }
}
