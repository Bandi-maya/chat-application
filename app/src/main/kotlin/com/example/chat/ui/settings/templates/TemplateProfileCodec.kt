package com.example.chat.ui.settings.templates

import com.example.chat.core.templates.ChatyNavigationDestinationIds
import com.example.chat.core.templates.ChatyTemplateId
import com.example.chat.core.templates.TemplateComponentType
import com.example.chat.core.templates.UserTemplateConfiguration
import org.json.JSONArray
import org.json.JSONObject

/** Exact port of templates_settings_screen.dart's template-profile
 *  export/import payload handling: the clipboard JSON shape and the import
 *  validation chain. Pure and JVM-testable; the screen shows the single
 *  snackbar 'Import failed. Check the template JSON and try again.' for any
 *  [ProfileFormatException], matching the Dart catch-all. */
object TemplateProfileCodec {

    const val KIND = "chaty_template_profile"
    const val SCHEMA_VERSION = 1

    class ProfileFormatException(message: String) : Exception(message)

    /** _exportConfiguration payload:
     *  {'kind': KIND, 'schemaVersion': 1, 'configuration': config.toMap()}. */
    fun encode(config: UserTemplateConfiguration): String {
        val payload = JSONObject()
        payload.put("kind", KIND)
        payload.put("schemaVersion", SCHEMA_VERSION)
        payload.put("configuration", JSONObject(config.toMap()))
        return payload.toString()
    }

    /** _importConfiguration validation, in the Dart order, with the Dart
     *  FormatException messages. Returns UserTemplateConfiguration.fromMap of
     *  the validated configuration map. */
    fun decode(text: String): UserTemplateConfiguration {
        val decoded: Any? = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ProfileFormatException("Unsupported template profile format.")
        }
        val root = decoded as? JSONObject
            ?: throw ProfileFormatException("Unsupported template profile format.")
        val configuration = root.optJSONObject("configuration")
        if (root.optString("kind", null) != KIND ||
            root.optInt("schemaVersion", Int.MIN_VALUE) != SCHEMA_VERSION ||
            configuration == null
        ) {
            throw ProfileFormatException("Unsupported template profile format.")
        }

        val baseKey = if (configuration.isNull("base")) null else configuration.opt("base")
        if (baseKey !is String || ChatyTemplateId.values().none { it.key == baseKey }) {
            throw ProfileFormatException("Unknown base template.")
        }

        val rawOverrides = if (configuration.isNull("overrides")) null else configuration.opt("overrides")
        if (rawOverrides != null && rawOverrides !is JSONObject) {
            throw ProfileFormatException("Invalid component overrides.")
        }
        if (rawOverrides is JSONObject) {
            for (key in rawOverrides.keys()) {
                val value = rawOverrides.opt(key)
                val knownComponent = TemplateComponentType.values().any { it.name == key }
                val knownTemplate = value is String && ChatyTemplateId.values().any { it.key == value }
                if (!knownComponent || !knownTemplate) {
                    throw ProfileFormatException("Unknown component or template.")
                }
            }
        }

        val primaryMissing = configuration.isNull("navPrimary")
        val overflowMissing = configuration.isNull("navOverflow")
        if (primaryMissing != overflowMissing) {
            throw ProfileFormatException("Both primary and overflow destinations are required.")
        }
        if (!primaryMissing) {
            val rawPrimary = configuration.opt("navPrimary")
            val rawOverflow = configuration.opt("navOverflow")
            if (rawPrimary !is JSONArray || rawOverflow !is JSONArray) {
                throw ProfileFormatException("Invalid navigation destination lists.")
            }
            val primary = rawPrimary.asStrings()
            val overflow = rawOverflow.asStrings()
            val primarySet = primary.toSet()
            val overflowSet = overflow.toSet()
            val assigned = primarySet + overflowSet
            val known = ChatyNavigationDestinationIds.known
            if (primary.size != rawPrimary.length() ||
                overflow.size != rawOverflow.length() ||
                primary.isEmpty() ||
                primary.size > 4 ||
                primarySet.size != primary.size ||
                overflowSet.size != overflow.size ||
                primarySet.intersect(overflowSet).isNotEmpty() ||
                primary.any { it !in known } ||
                overflow.any { it !in known } ||
                assigned.size != ChatyNavigationDestinationIds.all.size ||
                !assigned.containsAll(ChatyNavigationDestinationIds.all)
            ) {
                throw ProfileFormatException(
                    "Navigation profile must assign every supported destination exactly once."
                )
            }
        }

        return UserTemplateConfiguration.fromMap(configuration.toMap())
    }

    /** Dart whereType<String>().toList(): keeps only string items. */
    private fun JSONArray.asStrings(): List<String> {
        val out = ArrayList<String>(length())
        for (i in 0 until length()) {
            val item = opt(i)
            if (item is String) out.add(item)
        }
        return out
    }

    /** JSONObject -> the Map shape UserTemplateConfiguration.fromMap expects
     *  (overrides as Map<String,String>, destination lists as List<String>). */
    private fun JSONObject.toMap(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for (key in keys()) {
            val value = opt(key)
            out[key] = when (value) {
                null, JSONObject.NULL -> null
                is JSONObject -> buildMap<String, Any?> {
                    for (k in value.keys()) put(k, value.opt(k))
                }
                is JSONArray -> (0 until value.length()).map { value.opt(it) }
                else -> value
            }
        }
        return out
    }
}
