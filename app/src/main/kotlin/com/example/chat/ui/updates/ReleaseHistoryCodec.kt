package com.example.chat.ui.updates

import org.json.JSONArray
import org.json.JSONObject

internal object ReleaseHistoryCodec {
    data class Recovery(val entries: List<ReleaseHistoryEntry>, val quarantine: String?, val canWrite: Boolean)
    fun recover(raw: String?, quarantine: String?): Recovery {
        val entries = read(raw)
        if (entries != null) return Recovery(entries, quarantine, true)
        // One bounded backup slot. Never overwrite an earlier corrupt payload with another.
        return if (quarantine == null || quarantine == raw) Recovery(emptyList(), raw, true)
            else Recovery(emptyList(), quarantine, false)
    }
    fun read(raw: String?): List<ReleaseHistoryEntry>? {
        if (raw == null) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val entry = array.getJSONObject(i)
                val changes = entry.getJSONArray("changes")
                ReleaseHistoryEntry(entry.getString("versionName"), entry.getLong("versionCode"),
                    entry.getString("revision"), (0 until changes.length()).map { changes.getString(it) })
            }
        }.getOrNull()
    }
    fun write(entries: List<ReleaseHistoryEntry>): String = JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject().put("versionName", entry.versionName).put("versionCode", entry.versionCode)
                .put("revision", entry.revision).put("changes", JSONArray(entry.changes)))
        }
    }.toString()
}
