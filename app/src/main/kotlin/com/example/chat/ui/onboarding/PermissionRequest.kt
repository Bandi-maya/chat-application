package com.example.chat.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

/** Shared runtime-permission helpers for the onboarding + notification
 *  permission ports (permission_handler equivalents).
 *
 *  permission_handler maps:
 *  - Permission.notification -> POST_NOTIFICATIONS (always granted < API 33)
 *  - Permission.camera       -> CAMERA
 *  - Permission.microphone   -> RECORD_AUDIO
 *  - Permission.photos      -> READ_MEDIA_IMAGES (API 33+) /
 *                              READ_EXTERNAL_STORAGE (API <= 32) */
object ChatyPermissions {

    val notification: String? get() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null

    const val camera: String = Manifest.permission.CAMERA
    const val microphone: String = Manifest.permission.RECORD_AUDIO

    val photos: String get() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    /** null permission means the platform auto-grants it (pre-33 notifications). */
    fun isGranted(context: Context, permission: String?): Boolean =
        permission == null ||
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** permission_handler's openAppSettings(). */
    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** Returns a request function mirroring permission_handler's
 *  `permission.request()` from Compose:
 *  - already granted (or auto-granted platform permission) -> (true, false)
 *  - result + !shouldShowRequestPermissionRationale -> permanentlyDenied. */
@Composable
fun rememberPermissionRequester(
    onResult: (granted: Boolean, permanentlyDenied: Boolean) -> Unit,
): (String?) -> Unit {
    val context = LocalContext.current
    val activity = context as? Activity
    val pending = remember { arrayOfNulls<String>(1) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val permission = pending[0]
        pending[0] = null
        val permanentlyDenied = !granted && activity != null && permission != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        onResult(granted, permanentlyDenied)
    }
    return remember(launcher, context, activity) {
        { permission: String? ->
            if (ChatyPermissions.isGranted(context, permission)) {
                onResult(true, false)
            } else if (permission == null || activity == null) {
                onResult(false, false)
            } else {
                pending[0] = permission
                launcher.launch(permission)
            }
        }
    }
}
