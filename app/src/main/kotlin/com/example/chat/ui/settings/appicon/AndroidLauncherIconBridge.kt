package com.example.chat.ui.settings.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Android implementation of the Dart MethodChannel('chaty/launcher_icon')
 *  contract (lib/ui/core/controllers/app_icon_controller.dart):
 *
 *  - getCurrentLauncherIcon -> active alias id (never null here: falls back
 *    to 'bird', the Dart default variant).
 *  - setLauncherIcon {alias} -> enables exactly that activity-alias (or the
 *    .MainActivity entry for 'bird') via PackageManager with DONT_KILL_APP,
 *    then RE-READS the state and returns the confirmed alias, satisfying the
 *    Dart "returned alias must equal requested alias" check.
 *  - pickCustomIconImage {source: photos|camera} -> prepared temp file path
 *    or null (cancelled). Photos use PickVisualMedia (no permission);
 *    camera checks Manifest.permission.CAMERA and throws
 *    LauncherIconPickException("camera_permission_denied") so the
 *    controller maps the exact Dart error message.
 *
 *  OWNER WIRING (integration owner, sole writer of MainActivity/manifest):
 *  1. Merge LauncherIconAliasesManifest.snippet.xml into AndroidManifest
 *     (7 activity-alias entries, all android:enabled="false", plus per-alias
 *     icon resources @drawable/ic_launcher_<alias> - artwork is owner-side
 *     resource work; bird reuses @drawable/ic_launcher).
 *  2. In MainActivity (already a ComponentActivity), before onCreate
 *     finishes: `launcherIconBridge.register(this)`.
 *  3. FileProvider must be declared for "${applicationId}.fileprovider"
 *     with cache-path access (standing camera-worker gap) - camera picks
 *     fail without it; photos picks do not need it.
 *
 *  No live launcher mutation happens at install time: all aliases ship
 *  disabled, so the installed entry (the .MainActivity icon) is unchanged
 *  until the user picks a variant. */
class AndroidLauncherIconBridge(
    context: Context,
    private val packageManager: PackageManager = context.packageManager,
) : LauncherIconBridge {

    private val appContext = context.applicationContext
    private val packageName = appContext.packageName

    // ------------------------------------------------------------------
    // Icon switching
    // ------------------------------------------------------------------

    override suspend fun getCurrentLauncherIcon(): String? {
        val enabled = LauncherIconAliasPlan.ALIASES
            .map { LauncherIconAliasPlan.aliasComponent(packageName, it) }
            .filter { isEnabled(it) }
            .toSet()
        return LauncherIconAliasPlan.currentAlias(packageName, enabled)
    }

    override suspend fun setLauncherIcon(alias: String): String? {
        require(LauncherIconAliasPlan.isValidAlias(alias)) {
            "Unknown launcher icon alias: $alias"
        }
        for ((component, enabled) in LauncherIconAliasPlan.transitions(packageName, alias)) {
            packageManager.setComponentEnabledSetting(
                ComponentName(packageName, component),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        // Dart contract: return the CONFIRMED alias; the controller throws
        // 'launcher_icon_mismatch' if it differs from the requested one.
        return getCurrentLauncherIcon()
    }

    /** An alias missing from the manifest (aliases are not merged yet) reads as
     *  not enabled; setLauncherIcon then fails loudly instead of faking a switch. */
    private fun isEnabled(component: String): Boolean = try {
        packageManager.getComponentEnabledSetting(
            ComponentName(packageName, component),
        ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } catch (_: IllegalArgumentException) {
        false
    }

    // ------------------------------------------------------------------
    // Image picking (requires register() from the host activity)
    // ------------------------------------------------------------------

    private var pickVisualLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var takePictureLauncher: ActivityResultLauncher<Uri>? = null
    private val pickMutex = Mutex()
    private var pendingPick: CompletableDeferred<String?>? = null
    private var pendingCameraFile: File? = null

    /** Owner: call from MainActivity before onCreate returns
     *  (registerForActivityResult must run before the activity is STARTED). */
    fun register(activity: ComponentActivity) {
        pickVisualLauncher = activity.registerForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri -> completePick(uri?.let(::copyToTempFile)) }
        takePictureLauncher = activity.registerForActivityResult(
            ActivityResultContracts.TakePicture(),
        ) { ok -> completePick(if (ok) pendingCameraFile?.absolutePath else null) }
    }

    override suspend fun pickCustomIconImage(source: CustomIconInputSource): String? =
        pickMutex.withLock {
            val deferred = CompletableDeferred<String?>()
            pendingPick = deferred
            when (source) {
                CustomIconInputSource.PHOTOS -> {
                    val launcher = pickVisualLauncher
                        ?: error("AndroidLauncherIconBridge.register(activity) was not called")
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                CustomIconInputSource.CAMERA -> {
                    // Dart: PlatformException code 'camera_permission_denied'.
                    if (ContextCompat.checkSelfPermission(
                            appContext, android.Manifest.permission.CAMERA,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingPick = null
                        throw LauncherIconPickException(
                            "camera_permission_denied",
                            "Camera permission is required only when you choose Camera.",
                        )
                    }
                    val launcher = takePictureLauncher
                        ?: error("AndroidLauncherIconBridge.register(activity) was not called")
                    val file = File(
                        File(appContext.cacheDir, "launcher_icon_pick"),
                        "custom_icon_${System.currentTimeMillis()}.jpg",
                    )
                    file.parentFile?.mkdirs()
                    pendingCameraFile = file
                    launcher.launch(
                        FileProvider.getUriForFile(
                            appContext,
                            "$packageName.fileprovider",
                            file,
                        ),
                    )
                }
            }
            deferred.await()
        }

    private fun completePick(path: String?) {
        pendingCameraFile = null
        pendingPick?.complete(path)
        pendingPick = null
    }

    /** Photos URIs are granted only for the pick; copy into our cache so the
     *  returned path stays readable for the crop/persist pipeline. */
    private fun copyToTempFile(uri: Uri): String? {
        val outDir = File(appContext.cacheDir, "launcher_icon_pick").apply { mkdirs() }
        val out = File(outDir, "gallery_${System.currentTimeMillis()}.jpg")
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } ?: return null
            out.absolutePath
        } catch (error: Exception) {
            out.delete()
            null
        }
    }
}
