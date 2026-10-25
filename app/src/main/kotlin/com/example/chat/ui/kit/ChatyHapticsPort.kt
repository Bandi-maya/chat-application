package com.example.chat.ui.kit

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

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

/** Exact Android mapping of the Dart ChatyHaptics grammar
 *  (lib/ui/core/design_system/chaty_haptics.dart).
 *
 *  Flutter's engine maps HapticFeedback types to view constants
 *  (shell/platform/android PlatformPlugin.java, verified against the live
 *  source): selectionClick -> CLOCK_TICK, lightImpact -> VIRTUAL_KEY,
 *  mediumImpact -> KEYBOARD_TAP, heavyImpact -> CONTEXT_CLICK (API 23+),
 *  standard -> LONG_PRESS. Compose's own HapticFeedbackType lacks these on
 *  the project's Compose BOM, so this goes through View.performHapticFeedback
 *  exactly like the Flutter engine does. */
object ChatyHapticsPort {

    /** ChatyHaptics.selection() = selectionClick. */
    fun selection(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** ChatyHaptics.light() = lightImpact. */
    fun light(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** ChatyHaptics.threshold() = mediumImpact. */
    fun threshold(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** ChatyHaptics.success() = lightImpact. */
    fun success(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** ChatyHaptics.warning() = mediumImpact. */
    fun warning(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** ChatyHaptics.error() = heavyImpact (CONTEXT_CLICK, API 23+ like Flutter). */
    fun error(view: View) {
        if (Build.VERSION.SDK_INT >= 23) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }
}
