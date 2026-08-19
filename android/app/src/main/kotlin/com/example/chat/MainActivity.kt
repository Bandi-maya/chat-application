package com.example.chat

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val launcherChannel = "chaty/launcher_icon"
    private val customShortcutId = "chaty-custom-home"

    private val launcherAliases: LinkedHashMap<String, String>
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
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, launcherChannel).setMethodCallHandler { call, result ->
            when (call.method) {
                "getCurrentLauncherIcon" -> result.success(getCurrentLauncherIcon())
                "setLauncherIcon" -> {
                    val alias = call.argument<String>("alias")
                    if (alias.isNullOrBlank() || !launcherAliases.containsKey(alias)) {
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

    private fun getCurrentLauncherIcon(): String? {
        for ((alias, className) in launcherAliases) {
            val state = packageManager.getComponentEnabledSetting(ComponentName(this, className))
            val enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && alias == "original")
            if (enabled) return alias
        }
        return null
    }

    private fun setComponent(alias: String, state: Int) {
        val className = launcherAliases[alias] ?: error("Unknown launcher icon alias: $alias")
        packageManager.setComponentEnabledSetting(
            ComponentName(this, className),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun setLauncherIcon(alias: String) {
        val previous = getCurrentLauncherIcon() ?: "original"
        if (previous == alias) return

        try {
            setComponent(alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            for (candidate in launcherAliases.keys) {
                if (candidate == alias) continue
                setComponent(candidate, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            }
        } catch (error: Exception) {
            runCatching { setComponent(previous, PackageManager.COMPONENT_ENABLED_STATE_ENABLED) }
            for (candidate in launcherAliases.keys) {
                if (candidate == previous) continue
                runCatching { setComponent(candidate, PackageManager.COMPONENT_ENABLED_STATE_DISABLED) }
            }
            throw error
        }
    }

    private fun applyCustomHomeShortcut(imagePath: String, label: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "unsupported"
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return "unsupported"
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: error("Unable to decode the custom icon image.")
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val shortcut = ShortcutInfo.Builder(this, customShortcutId)
            .setShortLabel(label.take(10))
            .setLongLabel(label.take(25))
            .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
            .setIntent(launchIntent)
            .build()

        val isAlreadyPinned = shortcutManager.pinnedShortcuts.any { it.id == customShortcutId }
        return if (isAlreadyPinned) {
            shortcutManager.updateShortcuts(listOf(shortcut))
            "updated"
        } else if (shortcutManager.isRequestPinShortcutSupported) {
            val accepted = shortcutManager.requestPinShortcut(shortcut, null)
            if (accepted) "requested" else "unsupported"
        } else {
            "unsupported"
        }
    }

    private fun removeCustomHomeShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
        shortcutManager.disableShortcuts(
            listOf(customShortcutId),
            "Custom Chaty shortcut removed. Add a new one from Settings if needed.",
        )
    }
}
