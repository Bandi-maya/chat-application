package com.example.chat

import android.app.ActivityManager
import android.content.ComponentName
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
import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

open class MainActivity : FlutterFragmentActivity() {
    private val launcherChannel = "chaty/launcher_icon"

    protected val launcherComponents: LinkedHashMap<String, String>
        get() = linkedMapOf(
            "original" to "$packageName.LauncherOriginal",
            "minimal" to "$packageName.LauncherMinimal",
            "bubble" to "$packageName.LauncherBubble",
            "midnight" to "$packageName.LauncherMidnight",
            "ocean" to "$packageName.LauncherOcean",
            "violet" to "$packageName.LauncherViolet",
        )

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        reconcileCustomLauncherMode()
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, launcherChannel).setMethodCallHandler { call, result ->
            when (call.method) {
                "getCurrentLauncherIcon" -> result.success(getCurrentLauncherIcon())
                "isCustomHomeShortcutPinned" -> result.success(isCustomHomeShortcutPinned())
                "isCustomLauncherModeActive" -> result.success(isCustomLauncherModeActive())
                "setLauncherIcon" -> {
                    val alias = call.argument<String>("alias")
                    if (alias.isNullOrBlank() || !launcherComponents.containsKey(alias)) {
                        result.error("invalid_alias", "Unknown launcher icon alias.", null)
                        return@setMethodCallHandler
                    }
                    try {
                        setLauncherIcon(alias)
                        result.success(alias)
                    } catch (error: Exception) {
                        result.error("launcher_icon_change_failed", error.message, null)
                    }
                }
                "resetLauncherIcon" -> {
                    try {
                        setLauncherIcon("original")
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
                        result.success(applyCustomHomeShortcut(imagePath, label))
                    } catch (error: Exception) {
                        result.error("custom_shortcut_failed", error.message, null)
                    }
                }
                "removeCustomHomeShortcut" -> {
                    try {
                        removeCustomHomeShortcut()
                        result.success(null)
                    } catch (error: Exception) {
                        result.error("custom_shortcut_remove_failed", error.message, null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reconcileCustomLauncherMode()
        applyCustomTaskDescriptionIfNeeded()
    }

    protected fun nativePreferences() = getSharedPreferences(NATIVE_PREFERENCES_NAME, MODE_PRIVATE)

    private fun componentFor(alias: String): ComponentName {
        val className = launcherComponents[alias] ?: error("Unknown launcher icon alias: $alias")
        return ComponentName(this, className)
    }

    private fun componentState(alias: String): Int =
        packageManager.getComponentEnabledSetting(componentFor(alias))

    private fun isComponentEnabled(alias: String): Boolean {
        val state = componentState(alias)
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && alias == "original")
    }

    private fun getCurrentLauncherIcon(): String? {
        for ((alias, _) in launcherComponents) {
            if (isComponentEnabled(alias)) {
                nativePreferences().edit().putString(LAUNCHER_PREFERENCE_KEY, alias).apply()
                return alias
            }
        }

        val persisted = nativePreferences().getString(LAUNCHER_PREFERENCE_KEY, null)
        return persisted?.takeIf(launcherComponents::containsKey)
    }

    private fun setComponent(alias: String, state: Int) {
        packageManager.setComponentEnabledSetting(
            componentFor(alias),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun setLauncherIcon(alias: String) {
        val previous = getCurrentLauncherIcon() ?: "original"
        try {
            restoreBundledLauncher(alias)
            nativePreferences().edit()
                .putString(LAUNCHER_PREFERENCE_KEY, alias)
                .putBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)
                .apply()
            disableCustomShortcutIfPresent()
        } catch (error: Exception) {
            runCatching { restoreBundledLauncher(previous) }
            nativePreferences().edit()
                .putString(LAUNCHER_PREFERENCE_KEY, previous)
                .putBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)
                .apply()
            throw error
        }
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
        if (!isCustomHomeShortcutPinned()) return false
        disableBundledLaunchersForCustomMode()
        nativePreferences().edit().putBoolean(CUSTOM_MODE_PREFERENCE_KEY, true).apply()
        applyCustomTaskDescriptionIfNeeded()
        return true
    }

    private fun isCustomLauncherModeActive(): Boolean {
        if (!nativePreferences().getBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)) return false
        if (!isCustomHomeShortcutPinned()) return false
        return launcherComponents.keys.none(::isComponentEnabled)
    }

    private fun reconcileCustomLauncherMode() {
        val wantsCustomMode = nativePreferences().getBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)
        if (wantsCustomMode && isCustomHomeShortcutPinned()) {
            runCatching { disableBundledLaunchersForCustomMode() }
            return
        }

        if (!wantsCustomMode && launcherComponents.keys.none(::isComponentEnabled)) {
            val selected = nativePreferences().getString(LAUNCHER_PREFERENCE_KEY, "original")
                ?.takeIf(launcherComponents::containsKey)
                ?: "original"
            runCatching { restoreBundledLauncher(selected) }
        }
    }

    private fun restartApp() {
        val customMode = isCustomLauncherModeActive()
        val launchIntent = if (customMode) {
            Intent(this, CustomLauncherActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        } else {
            val selected = getCurrentLauncherIcon() ?: "original"
            Intent(Intent.ACTION_MAIN).apply {
                component = componentFor(selected)
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
        startActivity(launchIntent)
        finishAffinity()
    }

    private fun isCustomHomeShortcutPinned(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return false
        return shortcutManager.pinnedShortcuts.any { it.id == CUSTOM_SHORTCUT_ID && it.isEnabled }
    }

    private fun applyCustomHomeShortcut(imagePath: String, label: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "unsupported"
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return "unsupported"
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: error("Unable to decode the custom icon image.")
        val launchIntent = Intent(this, CustomLauncherActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val shortcut = ShortcutInfo.Builder(this, CUSTOM_SHORTCUT_ID)
            .setShortLabel(label.take(10))
            .setLongLabel(label.take(25))
            .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
            .setIntent(launchIntent)
            .build()

        nativePreferences().edit()
            .putString(CUSTOM_IMAGE_PATH_PREFERENCE_KEY, imagePath)
            .putBoolean(CUSTOM_MODE_PREFERENCE_KEY, true)
            .apply()

        return if (shortcutManager.pinnedShortcuts.any { it.id == CUSTOM_SHORTCUT_ID }) {
            shortcutManager.updateShortcuts(listOf(shortcut))
            enterCustomLauncherMode()
            "updated"
        } else if (shortcutManager.isRequestPinShortcutSupported) {
            val requested = shortcutManager.requestPinShortcut(shortcut, null)
            if (requested) "requested" else "unsupported"
        } else {
            "unsupported"
        }
    }

    private fun disableCustomShortcutIfPresent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
        if (shortcutManager.pinnedShortcuts.any { it.id == CUSTOM_SHORTCUT_ID && it.isEnabled }) {
            shortcutManager.disableShortcuts(
                listOf(CUSTOM_SHORTCUT_ID),
                "Custom Chaty icon is no longer active.",
            )
        }
    }

    private fun removeCustomHomeShortcut() {
        val selected = nativePreferences().getString(LAUNCHER_PREFERENCE_KEY, "original")
            ?.takeIf(launcherComponents::containsKey)
            ?: "original"
        restoreBundledLauncher(selected)
        nativePreferences().edit()
            .remove(CUSTOM_IMAGE_PATH_PREFERENCE_KEY)
            .putBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)
            .apply()
        disableCustomShortcutIfPresent()
    }

    private fun applyCustomTaskDescriptionIfNeeded() {
        if (!nativePreferences().getBoolean(CUSTOM_MODE_PREFERENCE_KEY, false)) return
        val imagePath = nativePreferences().getString(CUSTOM_IMAGE_PATH_PREFERENCE_KEY, null) ?: return
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return

        if (Build.VERSION.SDK_INT >= 37) {
            val description = ActivityManager.TaskDescription.Builder()
                .setLabel("Chaty")
                .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
                .build()
            setTaskDescription(description)
        } else {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription("Chaty", bitmap, Color.BLACK))
        }
    }

    companion object {
        const val CUSTOM_SHORTCUT_ID = "chaty-custom-home"
        const val NATIVE_PREFERENCES_NAME = "chaty_launcher_native"
        const val LAUNCHER_PREFERENCE_KEY = "selected_launcher"
        const val CUSTOM_IMAGE_PATH_PREFERENCE_KEY = "custom_image_path"
        const val CUSTOM_MODE_PREFERENCE_KEY = "custom_launcher_mode"
    }
}

class CustomLauncherActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyRuntimeCustomLaunchBackground()
        super.onCreate(savedInstanceState)
    }

    private fun applyRuntimeCustomLaunchBackground() {
        val imagePath = nativePreferences().getString(CUSTOM_IMAGE_PATH_PREFERENCE_KEY, null) ?: return
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return
        window.setBackgroundDrawable(CircularCustomLaunchDrawable(bitmap, resources.displayMetrics.density))
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
