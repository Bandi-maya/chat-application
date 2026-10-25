package com.example.chat.core.audio

/**
 * Handles short in-app notification sounds without reconfiguring active call
 * audio routes. Exact port of lib/core/audio/notification_audio_controller.dart.
 */
class NotificationAudioController(private val coordinator: AudioPolicyGate) {

    suspend fun playInAppNotificationSound(playAction: suspend () -> Unit) {
        coordinator.playNotificationSound(playerAction = playAction)
    }
}
