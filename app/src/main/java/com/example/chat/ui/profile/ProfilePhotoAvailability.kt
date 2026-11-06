package com.example.chat.ui.profile

internal sealed interface ProfilePhotoFeatureState {
    data object Ready : ProfilePhotoFeatureState
    data class CleanupBlocked(val message: String) : ProfilePhotoFeatureState
}
/** Startup/disposal exceptions disable this feature only; never escape lifecycle callbacks. */
internal class ProfilePhotoAvailability {
    @Volatile private var value: ProfilePhotoFeatureState = ProfilePhotoFeatureState.Ready
    fun state() = value
    fun block() { value = ProfilePhotoFeatureState.CleanupBlocked("Profile photo cleanup blocked. Close and restart Chaty to retry local profile photo cleanup. Pending camera results may still block capture.") }
    fun safely(action: () -> Unit) { try { action() } catch (_: Exception) { block() } }
    fun requireReady() {
        val current = value
        check(current is ProfilePhotoFeatureState.Ready) { (current as ProfilePhotoFeatureState.CleanupBlocked).message }
    }
}
