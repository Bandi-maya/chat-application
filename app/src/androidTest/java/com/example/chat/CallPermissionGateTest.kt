package com.example.chat

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.data.services.CallPermissionGate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic checks of the call permission gate: no live call, no device grant. */
@RunWith(AndroidJUnit4::class)
class CallPermissionGateTest {
    @Test
    fun audioCallNeedsOnlyMicrophone() {
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), CallPermissionGate.missing({ false }, false))
        assertTrue(CallPermissionGate.missing({ true }, true).isEmpty())
    }

    @Test
    fun videoCallNeedsMicrophoneAndCamera() {
        assertEquals(
            listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA),
            CallPermissionGate.missing({ false }, true),
        )
        assertEquals(
            listOf(Manifest.permission.CAMERA),
            CallPermissionGate.missing({ it == Manifest.permission.RECORD_AUDIO }, true),
        )
    }

    @Test
    fun deniedRequestFailsTheCallSafely() {
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val saved = CallPermissionGate.requester
        try {
            // Only meaningful while the permission is not already granted on the test device.
            val alreadyGranted = CallPermissionGate.missing(
                { androidx.core.content.ContextCompat.checkSelfPermission(ctx, it) == 0 }, false,
            ).isEmpty()
            if (alreadyGranted) return
            CallPermissionGate.requester = { perms -> perms.associateWith { false } }
            try {
                runBlocking { CallPermissionGate.ensure(ctx, false) }
                fail("denied microphone must throw")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("denied"))
            }
            CallPermissionGate.requester = null
            try {
                runBlocking { CallPermissionGate.ensure(ctx, false) }
                fail("no requester and no grant must throw")
            } catch (_: IllegalStateException) {
            }
        } finally {
            CallPermissionGate.requester = saved
        }
    }
}
