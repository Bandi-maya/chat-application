package com.example.chat

import android.content.ComponentName
import android.content.pm.PackageManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val launcherChannel = "chaty/launcher_icon"

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
}
