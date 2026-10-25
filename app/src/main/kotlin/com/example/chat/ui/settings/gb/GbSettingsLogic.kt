package com.example.chat.ui.settings.gb

import java.io.File
import java.util.Locale

/** Pure logic from gb_settings_screen.dart (backup/restore filtering + expired image cache cleanup). */
object GbSettingsLogic {
    /** portableGbKeys (copy) and allowedGbAppearanceKeys (restore) are the same 35-key list in Dart. */
    val portableGbKeys: Set<String> = linkedSetOf(
        "ModConTextColor", "ModContactNameColor", "HomeCounterBK",
        "HomeCounterText", "ModOnlineColor", "ModlastseenColor",
        "onlineDotchatColor", "ModConColor", "tabindicator",
        "bubble_style", "tick_style", "text_size_pick", "ConvoBack",
        "ModChatRightBubble", "ModChatBubbleText", "date_right_color",
        "ModChatLeftBubble", "ModChatBubbleTextLeft", "date_left_color",
        "ModCallsBackground", "ModCallsTextColor", "ModCallsIconColors",
        "ModChatColor", "ModChatGStatusB", "ModChatGStatusT",
        "ModConPickColor", "HomeBarText", "ModConBackColor",
        "list_bg_color", "ModDarkConPickColor", "ModDarkConPickColorNav",
        "BGColor", "home_stories_style", "ui_home_styleV3",
    )

    /** Dart _safeSettingsMap: drops path-like, name-override and custom wallpaper keys, recursing into maps. */
    @Suppress("UNCHECKED_CAST")
    fun safeSettingsMap(input: Map<String, Any?>): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        for ((k, value) in input) {
            val key = k.lowercase()
            if (key == "path" || key.endsWith("path") || key.endsWith("filepath") || key.contains("file_path") ||
                key.contains("mynameoverride") || key == "my_name" || key.contains("customwallpaperimage")
            ) continue
            result[k] = when (value) {
                is Map<*, *> -> safeSettingsMap(value.entries.associate { it.key.toString() to it.value })
                is List<*> -> value.toList()
                else -> value
            }
        }
        return result
    }

    /** Dart portableGb: only keys present in the gb feature map. */
    fun portableGb(gbFeatures: Map<String, Any?>): Map<String, Any?> =
        portableGbKeys.filter { gbFeatures.containsKey(it) }.associateWith { gbFeatures[it] }

    class Profile(
        val home: Map<String, Any?>, val conversation: Map<String, Any?>, val universal: Map<String, Any?>,
        val effects: Map<String, Any?>, val gb: Map<String, Any?>, val theme: Map<String, Any?>, val template: Map<String, Any?>,
    )

    /**
     * Dart _restoreSettingsBackup validation. Throws IllegalArgumentException (Dart FormatException) for
     * wrong kind/version, non-map sections, unknown appearance keys, unknown template or component.
     */
    fun validateProfile(decoded: Any?, templateKeys: Set<String>, componentNames: Set<String>): Profile {
        if (decoded !is Map<*, *> || decoded["kind"] != "chaty_settings_profile" || decoded["schemaVersion"].let { it !is Number || it.toInt() != 1 || it.toDouble() != 1.0 }) {
            throw IllegalArgumentException("Unsupported Chaty settings profile.")
        }
        fun optionalMap(key: String): Map<String, Any?> {
            val value = decoded[key] ?: return emptyMap()
            if (value !is Map<*, *>) throw IllegalArgumentException("Invalid $key settings.")
            return value.entries.associate { it.key.toString() to it.value }
        }
        val gb = optionalMap("gbAppearance")
        if (gb.keys.any { it !in portableGbKeys }) throw IllegalArgumentException("Unknown appearance setting in profile.")
        val template = optionalMap("template")
        if (template.isNotEmpty()) {
            if (template["base"] !in templateKeys) throw IllegalArgumentException("Unknown template profile.")
            val rawOverrides = template["overrides"]
            if (rawOverrides != null && rawOverrides !is Map<*, *>) throw IllegalArgumentException("Invalid component overrides.")
            if (rawOverrides is Map<*, *>) {
                for ((k, v) in rawOverrides) {
                    if (k !in componentNames || v !in templateKeys) throw IllegalArgumentException("Unknown component or template.")
                }
            }
        }
        return Profile(optionalMap("home"), optionalMap("conversation"), optionalMap("universal"), optionalMap("effects"), gb, optionalMap("theme"), template)
    }

    private val generatedImage = Regex("^chaty_[0-9a-fA-F-]{36}\\.jpg$")

    data class CacheStats(val count: Int, val bytes: Long)

    /** Dart _scanExpiredCompressedImages / _cleanExpiredCompressedImages over the temp (cache) dir. */
    fun scanExpiredCompressedImages(temp: File, nowMillis: Long = System.currentTimeMillis()): CacheStats =
        walkExpired(temp, nowMillis, delete = false)

    fun cleanExpiredCompressedImages(temp: File, nowMillis: Long = System.currentTimeMillis()): CacheStats =
        walkExpired(temp, nowMillis, delete = true)

    private fun walkExpired(temp: File, nowMillis: Long, delete: Boolean): CacheStats {
        if (!temp.exists()) return CacheStats(0, 0)
        val cutoff = nowMillis - 24L * 60 * 60 * 1000
        var count = 0
        var bytes = 0L
        for (entity in temp.listFiles().orEmpty()) {
            if (!entity.isFile || !generatedImage.matches(entity.name)) continue
            try {
                if (entity.lastModified() >= cutoff) continue
                val length = entity.length()
                if (delete && !entity.delete()) continue
                count++
                bytes += length
            } catch (_: Exception) {
            }
        }
        return CacheStats(count, bytes)
    }

    fun formatCacheSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024))
    }
}
