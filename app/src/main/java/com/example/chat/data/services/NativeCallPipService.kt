package com.example.chat.data.services

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational

/**
 * Capability-gated bridge to Android's system picture-in-picture mode. Port of
 * lib/data/services/native_call_pip_service.dart; native Android needs no
 * method channel - the Activity APIs are called directly. The OS renders the
 * existing call activity, so the current remote WebRTC surface remains visible
 * without a second media pipeline.
 *
 * Integration owner wires MainActivity: android:supportsPictureInPicture="true"
 * in the manifest and onPictureInPictureModeChanged -> [onPipModeChanged].
 */
class NativeCallPipService {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun addModeListener(listener: (Boolean) -> Unit) { listeners.add(listener) }
    fun removeModeListener(listener: (Boolean) -> Unit) { listeners.remove(listener) }

    /** Forward from Activity.onPictureInPictureModeChanged. */
    fun onPipModeChanged(active: Boolean) {
        for (l in listeners) l(active)
    }

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun isActive(activity: Activity): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity.isInPictureInPictureMode

    /** Dart enter({width: 9, height: 16}). */
    fun enter(activity: Activity, width: Int = 9, height: Int = 16): Boolean {
        if (!isSupported(activity)) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(width, height))
                    .build()
                activity.enterPictureInPictureMode(params)
            } else {
                @Suppress("DEPRECATION")
                activity.enterPictureInPictureMode()
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
