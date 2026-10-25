package com.example.chat.ui.chats.detail

import org.json.JSONArray
import org.json.JSONObject

/** Item in the bounded recently-used emoji cache (Dart RecentEmojiItem). */
data class RecentEmojiItem(
    val unicode: String,
    val label: String,
    val lastUsedTimestamp: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("unicode", unicode)
        .put("label", label)
        .put("ts", lastUsedTimestamp)

    companion object {
        fun fromJson(json: JSONObject): RecentEmojiItem = RecentEmojiItem(
            unicode = json.optString("unicode", "😊"),
            label = json.optString("label", "Smiling Face"),
            lastUsedTimestamp = json.optLong("ts", 0),
        )
    }
}

/** Persistence seam for the recents cache; the owner wires SharedPreferences
 *  (Dart pref key 'chaty_recent_emojis_v1'). Default keeps recents in memory
 *  for the process lifetime. */
interface EmojiRecentStore {
    fun load(): String?
    fun save(raw: String)

    class InMemory : EmojiRecentStore {
        private var raw: String? = null
        override fun load(): String? = raw
        override fun save(raw: String) {
            this.raw = raw
        }
    }
}

/** Exact port of lib/core/emoji/services/emoji_recent_cache.dart: bounded at
 *  32 entries, most-recently-used first, JSON-serialized. */
class EmojiRecentCache(
    private val store: EmojiRecentStore = EmojiRecentStore.InMemory(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val PREF_KEY = "chaty_recent_emojis_v1"
        const val MAX_CAPACITY = 32
    }

    private val items = mutableListOf<RecentEmojiItem>()
    private var initialized = false

    fun items(): List<RecentEmojiItem> = items.toList()

    @Synchronized
    fun initialize() {
        if (initialized) return
        try {
            val raw = store.load()
            if (!raw.isNullOrEmpty()) {
                val decoded = JSONArray(raw)
                items.clear()
                for (i in 0 until decoded.length()) {
                    val entry = decoded.optJSONObject(i) ?: continue
                    items.add(RecentEmojiItem.fromJson(entry))
                }
            }
        } catch (_: Exception) {
            // Corrupt cache starts empty rather than crashing the picker.
            items.clear()
        }
        initialized = true
    }

    @Synchronized
    fun recordUsage(unicode: String, label: String) {
        items.removeAll { it.unicode == unicode }
        items.add(0, RecentEmojiItem(unicode, label, now()))
        while (items.size > MAX_CAPACITY) {
            items.removeAt(items.size - 1)
        }
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        try {
            store.save(array.toString())
        } catch (_: Exception) {
            // Persistence failure must not break emoji selection.
        }
    }
}
