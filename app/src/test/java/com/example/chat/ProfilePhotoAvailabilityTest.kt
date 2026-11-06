package com.example.chat
import com.example.chat.ui.profile.*
import org.junit.Assert.*
import org.junit.Test
class ProfilePhotoAvailabilityTest {
    @Test fun startupFailureDoesNotThrowAndBlocksOnlyPhotoAction() {
        val a=ProfilePhotoAvailability();a.safely{error("persist failed")}
        assertTrue(a.state() is ProfilePhotoFeatureState.CleanupBlocked)
        assertTrue(runCatching{a.requireReady()}.isFailure)
        var appContinues=false;a.safely{appContinues=true};assertTrue(appContinues)
        assertTrue(a.state() is ProfilePhotoFeatureState.CleanupBlocked)
    }
    @Test fun cleanupFailureIsVisibleNotSilentlyReady() {
        val a=ProfilePhotoAvailability();a.safely{error("delete failed")}
        assertTrue((a.state() as ProfilePhotoFeatureState.CleanupBlocked).message.contains("cleanup blocked"))
    }
}
