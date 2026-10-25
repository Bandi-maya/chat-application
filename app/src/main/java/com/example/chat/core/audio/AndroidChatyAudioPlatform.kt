package com.example.chat.core.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Production Android implementation of [ChatyAudioPlatform], replacing the
 * Flutter MethodChannel native side of lib/core/audio/chaty_audio_platform.dart
 * with direct AudioManager calls. Error fallbacks match the Dart catch-blocks
 * (default capabilities / systemDefault / notVerified).
 *
 * HARDWARE-VERIFICATION GAP: route enumeration, setCommunicationDevice
 * behavior, and dual-output status are device- and OEM-dependent and cannot
 * be validated without a physical device.
 */
class AndroidChatyAudioPlatform(context: Context) : ChatyAudioPlatform {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override suspend fun getCapabilities(useCase: AudioUseCase): AudioCapabilities =
        try {
            queryCapabilities()
        } catch (_: Exception) {
            defaultAudioCapabilities(useCase)
        }

    override suspend fun getCurrentRoute(useCase: AudioUseCase): AudioOutputRoute =
        try {
            currentCommunicationRoute()
        } catch (_: Exception) {
            AudioOutputRoute.systemDefault
        }

    override suspend fun setPreferredRoute(
        useCase: AudioUseCase,
        route: AudioOutputRoute,
    ): AudioCapabilities =
        try {
            if (useCase == AudioUseCase.communication) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            }
            applyRoute(route)
            queryCapabilities()
        } catch (_: Exception) {
            defaultAudioCapabilities(useCase)
        }

    override suspend fun getDualOutputStatus(useCase: AudioUseCase): DualOutputStatus =
        // No standard Android API can verify simultaneous earpiece+speaker
        // output; matches the Dart notVerified fallback when the platform
        // channel could not confirm.
        DualOutputStatus.notVerified

    override suspend fun releaseSession(useCase: AudioUseCase) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.isSpeakerphoneOn = false
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
            // Ignored for release, matching the Dart implementation.
        }
    }

    override val capabilityChanges: Flow<AudioCapabilities> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                trySend(queryCapabilities())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                trySend(queryCapabilities())
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    override val routeChanges: Flow<AudioOutputRoute> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                trySend(currentCommunicationRoute())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                trySend(currentCommunicationRoute())
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    private fun queryCapabilities(): AudioCapabilities {
        val routes = mutableListOf<AudioOutputRoute>()
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasEarpiece = devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
        val hasSpeaker = devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val hasBluetooth = devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        val hasWired = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }
        val hasUsb = devices.any {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        routes.add(AudioOutputRoute.systemDefault)
        if (hasSpeaker) routes.add(AudioOutputRoute.speaker)
        if (hasEarpiece) routes.add(AudioOutputRoute.receiver)
        if (hasBluetooth) routes.add(AudioOutputRoute.bluetooth)
        if (hasWired) routes.add(AudioOutputRoute.wiredHeadset)
        if (hasUsb) routes.add(AudioOutputRoute.usb)
        return AudioCapabilities(
            availableRoutes = routes,
            activeRoute = currentCommunicationRoute(),
            dualOutputStatus = DualOutputStatus.notVerified,
            isStereoSupported = false,
            platform = "android",
        )
    }

    private fun currentCommunicationRoute(): AudioOutputRoute {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (audioManager.communicationDevice?.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutputRoute.speaker
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioOutputRoute.bluetooth
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES ->
                    AudioOutputRoute.wiredHeadset
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> AudioOutputRoute.usb
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioOutputRoute.receiver
                else -> AudioOutputRoute.systemDefault
            }
        }
        @Suppress("DEPRECATION")
        return when {
            audioManager.isBluetoothScoOn -> AudioOutputRoute.bluetooth
            audioManager.isSpeakerphoneOn -> AudioOutputRoute.speaker
            audioManager.isWiredHeadsetOn -> AudioOutputRoute.wiredHeadset
            else -> AudioOutputRoute.receiver
        }
    }

    private fun applyRoute(route: AudioOutputRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetType = when (route) {
                AudioOutputRoute.speaker -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                AudioOutputRoute.bluetooth -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                AudioOutputRoute.wiredHeadset -> AudioDeviceInfo.TYPE_WIRED_HEADSET
                AudioOutputRoute.usb -> AudioDeviceInfo.TYPE_USB_HEADSET
                else -> null
            }
            if (targetType == null || route == AudioOutputRoute.receiver || route == AudioOutputRoute.systemDefault) {
                audioManager.clearCommunicationDevice()
            } else {
                val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
                    ?: throw IllegalStateException("No communication device for route $route")
                if (!audioManager.setCommunicationDevice(device)) {
                    throw IllegalStateException("setCommunicationDevice rejected route $route")
                }
            }
        } else {
            @Suppress("DEPRECATION")
            when (route) {
                AudioOutputRoute.speaker -> {
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = true
                }
                AudioOutputRoute.bluetooth -> {
                    audioManager.isSpeakerphoneOn = false
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
                else -> {
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                }
            }
        }
    }
}
