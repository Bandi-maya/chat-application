package com.example.chat

import android.Manifest
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

open class MainActivity : FlutterFragmentActivity() {
    private val launcherChannel = "chaty/launcher_icon"
    private val imageExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var pendingImageResult: MethodChannel.Result? = null
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null

    private val photoPickerLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) {
                finishImageRequest(null, null)
            } else {
                processSelectedImage(uri, deleteRawAfter = false)
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCameraCapture()
            } else {
                finishImageRequest(
                    null,
                    PlatformImageError("camera_permission_denied", "Camera permission was not granted."),
                )
            }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
            val uri = pendingCameraUri
            if (!captured || uri == null) {
                pendingCameraFile?.delete()
                finishImageRequest(null, null)
            } else {
                processSelectedImage(uri, deleteRawAfter = true)
            }
        }

    private val launcherManager: LauncherIconManager by lazy {
        LauncherIconManager(this)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        launcherManager.reconcile()
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, launcherChannel).setMethodCallHandler { call, result ->
            when (call.method) {
                "getCurrentLauncherIcon" -> result.success(launcherManager.getCurrentLauncherIcon())
                "getCustomLauncherState" -> result.success(launcherManager.getCustomLauncherState())
                "isCustomHomeShortcutPinned" -> result.success(launcherManager.isCustomHomeShortcutPinned())
                "isCustomLauncherModeActive" -> result.success(launcherManager.isCustomLauncherModeActive())
                "setLauncherIcon" -> {
                    val alias = call.argument<String>("alias")
                    if (alias.isNullOrBlank() || !launcherManager.isKnownAlias(alias)) {
                        result.error("invalid_alias", "Unknown launcher icon alias.", null)
                        return@setMethodCallHandler
                    }
                    try {
                        launcherManager.setLauncherIcon(alias)
                        result.success(alias)
                    } catch (error: Exception) {
                        result.error("launcher_icon_change_failed", error.message, null)
                    }
                }
                "resetLauncherIcon" -> {
                    try {
                        launcherManager.setLauncherIcon("original")
                        result.success("original")
                    } catch (error: Exception) {
                        result.error("launcher_icon_reset_failed", error.message, null)
                    }
                }
                "restartApp" -> {
                    try {
                        restartApp()
                        result.success(null)
                    } catch (error: Exception) {
                        result.error("restart_failed", error.message, null)
                    }
                }
                "applyCustomHomeShortcut" -> {
                    val imagePath = call.argument<String>("imagePath")
                    val label = call.argument<String>("label") ?: "Chaty"
                    if (imagePath.isNullOrBlank()) {
                        result.error("invalid_image", "Custom icon image path is missing.", null)
                        return@setMethodCallHandler
                    }
                    try {
                        result.success(launcherManager.applyCustomHomeShortcut(imagePath, label))
                    } catch (error: Exception) {
                        result.error("custom_shortcut_failed", error.message, null)
                    }
                }
                "removeCustomHomeShortcut" -> {
                    try {
                        launcherManager.removeCustomHomeShortcut()
                        result.success(null)
                    } catch (error: Exception) {
                        result.error("custom_shortcut_remove_failed", error.message, null)
                    }
                }
                "pickCustomIconImage" -> {
                    val source = call.argument<String>("source") ?: "photos"
                    startImageRequest(source, result)
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launcherManager.reconcile()
        launcherManager.applyTaskDescription(this)
    }

    override fun onDestroy() {
        pendingImageResult?.error("activity_destroyed", "Image selection was interrupted.", null)
        pendingImageResult = null
        imageExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun startImageRequest(source: String, result: MethodChannel.Result) {
        if (pendingImageResult != null) {
            result.error("picker_busy", "Another custom icon image request is already active.", null)
            return
        }
        pendingImageResult = result

        when (source) {
            "photos" -> {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            "camera" -> {
                val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) {
                    launchCameraCapture()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            else -> finishImageRequest(
                null,
                PlatformImageError("invalid_source", "Unsupported custom icon image source."),
            )
        }
    }

    private fun launchCameraCapture() {
        try {
            val directory = File(cacheDir, "custom_icon_capture").apply { mkdirs() }
            val file = File(directory, "capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file,
            )
            pendingCameraFile = file
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (error: Exception) {
            finishImageRequest(
                null,
                PlatformImageError("camera_launch_failed", error.message ?: "Could not open the camera."),
            )
        }
    }

    private fun processSelectedImage(uri: Uri, deleteRawAfter: Boolean) {
        imageExecutor.execute {
            val outcome = runCatching {
                CustomIconImageProcessor.prepare(this, uri)
            }
            if (deleteRawAfter) {
                pendingCameraFile?.delete()
            }
            runOnUiThread {
                outcome.fold(
                    onSuccess = { file -> finishImageRequest(file.absolutePath, null) },
                    onFailure = { error ->
                        finishImageRequest(
                            null,
                            PlatformImageError(
                                "image_processing_failed",
                                error.message ?: "The selected image could not be processed.",
                            ),
                        )
                    },
                )
            }
        }
    }

    private fun finishImageRequest(path: String?, error: PlatformImageError?) {
        val result = pendingImageResult
        pendingImageResult = null
        pendingCameraUri = null
        pendingCameraFile = null

        if (error != null) {
            result?.error(error.code, error.message, null)
        } else {
            result?.success(path)
        }
    }

    private fun restartApp() {
        val intent = launcherManager.buildRestartIntent()
        startActivity(intent)
        finishAffinity()
    }
}

private data class PlatformImageError(
    val code: String,
    val message: String,
)

internal class LauncherIconManager(
    private val context: Context,
) {
    private val packageManager: PackageManager = context.packageManager
    private val preferences = context.getSharedPreferences(NATIVE_PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val launcherComponents: LinkedHashMap<String, String>
        get() = linkedMapOf(
            "original" to "${context.packageName}.LauncherOriginal",
            "minimal" to "${context.packageName}.LauncherMinimal",
            "bubble" to "${context.packageName}.LauncherBubble",
            "midnight" to "${context.packageName}.LauncherMidnight",
            "ocean" to "${context.packageName}.LauncherOcean",
            "violet" to "${context.packageName}.LauncherViolet",
        )

    fun isKnownAlias(alias: String): Boolean = launcherComponents.containsKey(alias)

    fun getCurrentLauncherIcon(): String? {
        for (alias in launcherComponents.keys) {
            if (isComponentEnabled(alias)) {
                preferences.edit().putString(LAUNCHER_PREFERENCE_KEY, alias).apply()
                return alias
            }
        }
        return selectedBundledAlias()
    }

    fun getCustomLauncherState(): String {
        return when {
            isCustomLauncherModeActive() -> "active"
            preferences.getBoolean(CUSTOM_PENDING_PREFERENCE_KEY, false) -> "pending"
            preferences.getBoolean(CUSTOM_MODE_PREFERENCE_KEY, false) -> "failed"
            else -> "inactive"
        }
    }

    fun isCustomHomeShortcutPinned(): Boolean {
        val shortcut = customShortcutInfo() ?: return false
        return shortcut.isEnabled
    }

    fun isCustomLauncherModeActive(): Boolean {
        if (!preferences.getBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)) return false
        if (!isCustomHomeShortcutPinned()) return false
        return launcherComponents.keys.none(::isComponentEnabled)
    }

    fun setLauncherIcon(alias: String) {
        require(isKnownAlias(alias)) { "Unknown launcher icon alias: $alias" }
        val previous = selectedBundledAlias()
        try {
            restoreBundledLauncher(alias)
            preferences.edit()
                .putString(LAUNCHER_PREFERENCE_KEY, alias)
                .putBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)
                .putBoolean(CUSTOM_PENDING_PREFERENCE_KEY, false)
                .apply()
            disableCustomShortcutIfPresent()
        } catch (error: Exception) {
            runCatching { restoreBundledLauncher(previous) }
            preferences.edit()
                .putString(LAUNCHER_PREFERENCE_KEY, previous)
                .putBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)
                .putBoolean(CUSTOM_PENDING_PREFERENCE_KEY, false)
                .apply()
            throw error
        }
    }

    fun applyCustomHomeShortcut(imagePath: String, label: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "unsupported"
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return "unsupported"
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: error("Unable to decode the custom icon image.")
        val launchIntent = Intent(context, CustomLauncherActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val shortcut = ShortcutInfo.Builder(context, CUSTOM_SHORTCUT_ID)
            .setShortLabel(label.take(10))
            .setLongLabel(label.take(25))
            .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
            .setIntent(launchIntent)
            .build()

        preferences.edit()
            .putString(CUSTOM_IMAGE_PATH_PREFERENCE_KEY, imagePath)
            .apply()

        if (customShortcutInfo() != null) {
            runCatching { shortcutManager.enableShortcuts(listOf(CUSTOM_SHORTCUT_ID)) }
            shortcutManager.updateShortcuts(listOf(shortcut))
            if (enterCustomLauncherMode()) {
                return "active"
            }
            return "failed"
        }

        if (!shortcutManager.isRequestPinShortcutSupported) {
            preferences.edit()
                .putBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)
                .putBoolean(CUSTOM_PENDING_PREFERENCE_KEY, false)
                .apply()
            return "unsupported"
        }

        val callbackIntent = Intent(context, CustomShortcutPinnedReceiver::class.java).apply {
            action = ACTION_CUSTOM_SHORTCUT_PINNED
        }
        val callback = PendingIntent.getBroadcast(
            context,
            PIN_CALLBACK_REQUEST_CODE,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        preferences.edit()
            .putBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)
            .putBoolean(CUSTOM_PENDING_PREFERENCE_KEY, true)
            .apply()

        val requested = shortcutManager.requestPinShortcut(shortcut, callback.intentSender)
        if (!requested) {
            preferences.edit().putBoolean(CUSTOM_PENDING_PREFERENCE_KEY, false).apply()
            return "unsupported"
        }
        return "pending"
    }

    fun removeCustomHomeShortcut() {
        val selected = selectedBundledAlias()
        restoreBundledLauncher(selected)
        disableCustomShortcutIfPresent()
        preferences.edit()
            .remove(CUSTOM_IMAGE_PATH_PREFERENCE_KEY)
            .putBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)
            .putBoolean(CUSTOM_PENDING_PREFERENCE_KEY, false)
            .apply()
    }

    fun reconcile() {
        val selected = selectedBundledAlias()
        val pinned = customShortcutInfo() != null
        val pending = preferences.getBoolean(CUSTOM_PENDING_PREFERENCE_KEY, false)
        val customMode = preferences.getBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)

        if ((customMode || pending) && pinned) {
            val activated = runCatching {
                enableCustomShortcutIfPresent()
                enterCustomLauncherMode()
            }.getOrDefault(false)
            if (activated) return
        }

        if (customMode && !pinned) {
            runCatching { restoreBundledLauncher(selected) }
            preferences.edit()
                .putBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)
                .putBoolean(CUSTOM_PENDING_PREFERENCE_KEY, false)
                .apply()
            return
        }

        if (pending && !pinned) {
            if (launcherComponents.keys.none(::isComponentEnabled)) {
                runCatching { restoreBundledLauncher(selected) }
            }
            preferences.edit().putBoolean(CUSTOM_PENDING_PREFERENCE_KEY, false).apply()
            return
        }

        if (launcherComponents.keys.none(::isComponentEnabled)) {
            runCatching { restoreBundledLauncher(selected) }
            preferences.edit().putBoolean(CUSTOM_MODE_PREFERENCE_KEY, false).apply()
        }
    }

    fun onCustomShortcutPinned() {
        if (customShortcutInfo() == null) return
        enableCustomShortcutIfPresent()
        enterCustomLauncherMode()
    }

    fun buildRestartIntent(): Intent {
        return if (isCustomLauncherModeActive()) {
            Intent(context, CustomLauncherActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        } else {
            Intent(Intent.ACTION_MAIN).apply {
                component = componentFor(selectedBundledAlias())
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
    }

    fun applyTaskDescription(activity: MainActivity) {
        if (!preferences.getBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)) return
        val imagePath = preferences.getString(CUSTOM_IMAGE_PATH_PREFERENCE_KEY, null) ?: return
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return

        if (Build.VERSION.SDK_INT >= 37) {
            val description = ActivityManager.TaskDescription.Builder()
                .setLabel("Chaty")
                .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
                .build()
            activity.setTaskDescription(description)
        } else {
            @Suppress("DEPRECATION")
            activity.setTaskDescription(ActivityManager.TaskDescription("Chaty", bitmap, Color.BLACK))
        }
    }

    private fun selectedBundledAlias(): String {
        return preferences.getString(LAUNCHER_PREFERENCE_KEY, "original")
            ?.takeIf(::isKnownAlias)
            ?: "original"
    }

    private fun componentFor(alias: String): ComponentName {
        val className = launcherComponents[alias] ?: error("Unknown launcher icon alias: $alias")
        return ComponentName(context, className)
    }

    private fun componentState(alias: String): Int =
        packageManager.getComponentEnabledSetting(componentFor(alias))

    private fun isComponentEnabled(alias: String): Boolean {
        val state = componentState(alias)
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && alias == "original")
    }

    private fun setComponent(alias: String, state: Int) {
        packageManager.setComponentEnabledSetting(
            componentFor(alias),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun restoreBundledLauncher(alias: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val changes = launcherComponents.keys.map { candidate ->
                PackageManager.ComponentEnabledSetting(
                    componentFor(candidate),
                    if (candidate == alias) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    },
                    PackageManager.DONT_KILL_APP,
                )
            }
            packageManager.setComponentEnabledSettings(changes)
        } else {
            setComponent(alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            for (candidate in launcherComponents.keys) {
                if (candidate != alias) {
                    setComponent(candidate, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                }
            }
        }
    }

    private fun disableBundledLaunchersForCustomMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val changes = launcherComponents.keys.map { candidate ->
                PackageManager.ComponentEnabledSetting(
                    componentFor(candidate),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            packageManager.setComponentEnabledSettings(changes)
        } else {
            for (candidate in launcherComponents.keys) {
                setComponent(candidate, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            }
        }
    }

    private fun enterCustomLauncherMode(): Boolean {
        if (customShortcutInfo() == null) return false
        enableCustomShortcutIfPresent()
        if (!isCustomHomeShortcutPinned()) return false

        disableBundledLaunchersForCustomMode()
        preferences.edit()
            .putBoolean(CUSTOM_MODE_PREFERENCE_KEY, true)
            .putBoolean(CUSTOM_PENDING_PREFERENCE_KEY, false)
            .apply()
        return true
    }

    private fun customShortcutInfo(): ShortcutInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return null
        return shortcutManager.pinnedShortcuts.firstOrNull { it.id == CUSTOM_SHORTCUT_ID }
    }

    private fun enableCustomShortcutIfPresent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        if (customShortcutInfo() != null) {
            runCatching { shortcutManager.enableShortcuts(listOf(CUSTOM_SHORTCUT_ID)) }
        }
    }

    private fun disableCustomShortcutIfPresent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcut = customShortcutInfo() ?: return
        if (shortcut.isEnabled) {
            shortcutManager.disableShortcuts(
                listOf(CUSTOM_SHORTCUT_ID),
                "Custom Chaty icon is inactive. Re-enable it from Chaty Settings.",
            )
        }
    }

    companion object {
        const val CUSTOM_SHORTCUT_ID = "chaty-custom-home"
        const val NATIVE_PREFERENCES_NAME = "chaty_launcher_native"
        const val LAUNCHER_PREFERENCE_KEY = "selected_launcher"
        const val CUSTOM_IMAGE_PATH_PREFERENCE_KEY = "custom_image_path"
        const val CUSTOM_MODE_PREFERENCE_KEY = "custom_launcher_mode"
        const val CUSTOM_PENDING_PREFERENCE_KEY = "custom_launcher_pending"
        const val ACTION_CUSTOM_SHORTCUT_PINNED = "com.example.chat.CUSTOM_SHORTCUT_PINNED"
        const val PIN_CALLBACK_REQUEST_CODE = 4107
    }
}

class CustomShortcutPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != LauncherIconManager.ACTION_CUSTOM_SHORTCUT_PINNED) return
        LauncherIconManager(context.applicationContext).onCustomShortcutPinned()
    }
}

class LauncherRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> LauncherIconManager(context.applicationContext).reconcile()
        }
    }
}

class CustomLauncherActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyRuntimeCustomLaunchBackground()
        super.onCreate(savedInstanceState)
    }

    private fun applyRuntimeCustomLaunchBackground() {
        val preferences = getSharedPreferences(
            LauncherIconManager.NATIVE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val imagePath = preferences.getString(
            LauncherIconManager.CUSTOM_IMAGE_PATH_PREFERENCE_KEY,
            null,
        ) ?: return
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return
        window.setBackgroundDrawable(CircularCustomLaunchDrawable(bitmap, resources.displayMetrics.density))
    }
}

private object CustomIconImageProcessor {
    private const val MAX_INPUT_DIMENSION = 2048

    fun prepare(context: Context, uri: Uri): File {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Unable to open selected image." }
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or corrupted image." }

        var sampleSize = 1
        var sampledWidth = bounds.outWidth
        var sampledHeight = bounds.outHeight
        while (maxOf(sampledWidth, sampledHeight) / 2 >= MAX_INPUT_DIMENSION) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Unable to reopen selected image." }
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("Unable to decode selected image.")

        val orientation = runCatching {
            resolver.openInputStream(uri).use { stream ->
                if (stream == null) {
                    ExifInterface.ORIENTATION_NORMAL
                } else {
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val oriented = applyExifOrientation(decoded, orientation)
        val directory = File(context.cacheDir, "custom_icon_input").apply { mkdirs() }
        val output = File(directory, "prepared_${System.currentTimeMillis()}.png")
        FileOutputStream(output).use { stream ->
            check(oriented.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to encode selected image."
            }
        }

        if (oriented !== decoded) decoded.recycle()
        oriented.recycle()
        return output
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return bitmap
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

private class CircularCustomLaunchDrawable(
    bitmap: Bitmap,
    density: Float,
) : Drawable() {
    private val backgroundPaint = Paint().apply { color = Color.BLACK }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val sourceWidth = bitmap.width.toFloat()
    private val sourceHeight = bitmap.height.toFloat()
    private val maxDiameterPx = 128f * density
    private val matrix = Matrix()

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, backgroundPaint)
        val diameter = minOf(maxDiameterPx, bounds.width() * 0.34f, bounds.height() * 0.34f)
        val left = bounds.exactCenterX() - diameter / 2f
        val top = bounds.exactCenterY() - diameter / 2f
        val target = RectF(left, top, left + diameter, top + diameter)

        val scale = maxOf(diameter / sourceWidth, diameter / sourceHeight)
        val dx = target.left + (diameter - sourceWidth * scale) / 2f
        val dy = target.top + (diameter - sourceHeight * scale) / 2f
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
        imagePaint.shader?.setLocalMatrix(matrix)
        canvas.drawOval(target, imagePaint)
    }

    override fun setAlpha(alpha: Int) {
        imagePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        imagePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

class LauncherOriginal : MainActivity()
class LauncherMinimal : MainActivity()
class LauncherBubble : MainActivity()
class LauncherMidnight : MainActivity()
class LauncherOcean : MainActivity()
class LauncherViolet : MainActivity()
