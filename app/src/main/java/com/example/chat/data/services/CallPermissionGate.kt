package com.example.chat.data.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Runtime-permission gate for call media.
 *
 *  Dart: flutter_webrtc's getUserMedia (call_signaling_service.dart:_openLocalMedia) asks Android for
 *  RECORD_AUDIO (and CAMERA for video) itself and throws when denied, which the signaling service turns
 *  into a failed call; call_foreground_service.dart asks for the notification permission before
 *  startService and only logs when it is refused. This gate reproduces that order for the native
 *  WebRTC controller: microphone (+ camera for video) first, failure on denial, then a best-effort
 *  notification request. The foreground service is only started after prepare() succeeds, so
 *  RECORD_AUDIO is already granted when a microphone-type foreground service starts.
 *
 *  [requester] is installed by the activity-bound UI (AppRoot) and shows the system dialog; with no
 *  installed requester (app not in the foreground) a missing permission counts as denied. */
object CallPermissionGate {
    @Volatile
    var requester: (suspend (List<String>) -> Map<String, Boolean>)? = null

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Pure decision used by the tests: which of the required permissions are still missing. */
    fun missing(isGranted: (String) -> Boolean, isVideo: Boolean): List<String> {
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (isVideo) add(Manifest.permission.CAMERA)
        }
        return required.filterNot(isGranted)
    }

    suspend fun ensure(context: Context, isVideo: Boolean) {
        val need = missing({ granted(context, it) }, isVideo)
        if (need.isNotEmpty()) {
            val ask = requester
                ?: throw IllegalStateException("Microphone/camera permission is required for calls.")
            val result = ask(need)
            val denied = need.filterNot { result[it] == true }
            if (denied.isNotEmpty()) {
                val what = if (Manifest.permission.RECORD_AUDIO in denied) "Microphone" else "Camera"
                throw IllegalStateException("$what permission was denied.")
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && !granted(context, Manifest.permission.POST_NOTIFICATIONS)) {
            // Best effort, as Dart: a refusal only reduces background call visibility.
            try { requester?.invoke(listOf(Manifest.permission.POST_NOTIFICATIONS)) } catch (_: Exception) {}
        }
    }
}
