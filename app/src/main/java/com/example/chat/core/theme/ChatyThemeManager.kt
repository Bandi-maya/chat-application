package com.example.chat.core.theme

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime

/** Dart FormatException: the message is shown to the user by ThemeEditorScreen. */
class ThemeFormatException(message: String) : Exception(message)

/** Port of lib/ui/core/theme/chaty_theme_manager.dart: safe theme JSON import/export. */
object ChatyThemeManager {
    const val schemaVersion = 1
    const val maxImportSizeBytes = 1024 * 1024

    private fun toJson(v: Any?): Any? = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), toJson(x)) } }
        is List<*> -> JSONArray().also { a -> v.forEach { a.put(toJson(it)) } }
        is Enum<*> -> v.name
        else -> v
    }

    private fun fromJson(v: Any?): Any? = when (v) {
        JSONObject.NULL -> null
        is JSONObject -> v.keys().asSequence().associateWith { fromJson(v.get(it)) }
        is JSONArray -> (0 until v.length()).map { fromJson(v.get(it)) }
        else -> v
    }

    /** Dart exportTheme: JsonEncoder.withIndent('  '). exportedAt is the local ISO-8601 time. */
    fun exportTheme(theme: ThemeConfig, now: LocalDateTime = LocalDateTime.now()): String {
        val payload = JSONObject()
        payload.put("schemaVersion", schemaVersion)
        payload.put("exportedAt", now.toString())
        payload.put("theme", toJson(theme.toMap()))
        return payload.toString(2)
    }

    /** Dart validateAndImportTheme; every failure is a [ThemeFormatException] with Dart's message. */
    fun validateAndImportTheme(jsonString: String): ThemeConfig {
        if (jsonString.isEmpty()) throw ThemeFormatException("Empty theme file provided.")
        if (jsonString.length > maxImportSizeBytes) throw ThemeFormatException("Theme file exceeds maximum allowed size (1 MB).")
        val decoded: Any? = try {
            val tokener = org.json.JSONTokener(jsonString)
            val value = tokener.nextValue()
            // jsonDecode rejects trailing content; JSONTokener would ignore it.
            if (tokener.nextClean() != 0.toChar()) throw JSONException("trailing content")
            fromJson(value)
        } catch (_: Exception) {
            throw ThemeFormatException("Invalid JSON payload.")
        }
        if (decoded !is Map<*, *>) throw ThemeFormatException("Expected a JSON object at root.")
        val ver = decoded["schemaVersion"]
        // Dart: ver == null || (ver is int && ver > schemaVersion)
        if (ver == null || ((ver is Int || ver is Long) && (ver as Number).toLong() > schemaVersion)) {
            throw ThemeFormatException("Unsupported or missing theme schema version.")
        }
        val rawTheme = decoded["theme"]
        if (rawTheme !is Map<*, *>) throw ThemeFormatException("Missing theme object in import payload.")
        @Suppress("UNCHECKED_CAST")
        val theme = ThemeConfig.fromMap(rawTheme as Map<String, Any?>)
        if (theme.primaryTextColor == theme.backgroundColor || theme.hasContrastIssue) {
            throw ThemeFormatException("Imported theme fails readability or accessibility contrast validation.")
        }
        return theme
    }

    /** Dart saveThemeToFile: [dir] is getApplicationDocumentsDirectory(). */
    fun saveThemeToFile(dir: File, theme: ThemeConfig, nowMillis: Long = System.currentTimeMillis()): File {
        val safeName = theme.name.replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
        val file = File(dir, "chaty_theme_${safeName}_$nowMillis.json")
        file.writeText(exportTheme(theme))
        return file
    }
}
