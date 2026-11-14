package com.example.chat.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ChatFolder(val id: String, val name: String)

/** Local, account-scoped chat folders. Membership is client-side only: one
 *  SharedPreferences file per account, never synced.
 *  Purge on sign-out is not wired yet. */
class ChatFolderStore(context: Context, accountKey: String) {

    private data class State(val folders: List<ChatFolder>, val members: Map<String, Set<String>>)

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_PREFIX + java.security.MessageDigest.getInstance("SHA-256").digest(accountKey.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) },
        Context.MODE_PRIVATE,
    )
    private val lock = Any()
    private var corrupted = false

    /** Bumped on every write so Compose observers re-read. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    fun folders(): List<ChatFolder> = synchronized(lock) { read().folders }

    fun memberIds(folderId: String): Set<String> =
        synchronized(lock) { read().members[folderId] ?: emptySet() }

    /** Null when the name is blank or already used (case-insensitive). */
    fun createFolder(name: String): ChatFolder? {
        val trimmed = name.trim().take(24)
        if (trimmed.isEmpty()) return null
        synchronized(lock) {
            val state = read()
            if (state.folders.any { it.name.equals(trimmed, ignoreCase = true) }) return null
            val folder = ChatFolder(id = "f_" + java.util.UUID.randomUUID().toString(), name = trimmed)
            return if (write(state.copy(folders = state.folders + folder))) folder else null
        }
    }

    fun renameFolder(folderId: String, name: String) {
        val trimmed = name.trim().take(24)
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            val state = read()
            if (state.folders.any { it.id != folderId && it.name.equals(trimmed, ignoreCase = true) }) return
            write(state.copy(folders = state.folders.map {
                if (it.id == folderId) it.copy(name = trimmed) else it
            }))
        }
    }

    fun deleteFolder(folderId: String) {
        synchronized(lock) {
            val state = read()
            write(state.copy(
                folders = state.folders.filterNot { it.id == folderId },
                members = state.members - folderId,
            ))
        }
    }

    fun setMembers(folderId: String, conversationIds: Set<String>) {
        synchronized(lock) {
            val state = read()
            if (state.folders.none { it.id == folderId }) return
            write(state.copy(members = state.members + (folderId to conversationIds)))
        }
    }

    fun toggleMember(folderId: String, conversationId: String) {
        synchronized(lock) {
            val state = read()
            if (state.folders.none { it.id == folderId }) return
            val current = state.members[folderId] ?: emptySet()
            val next = if (conversationId in current) current - conversationId else current + conversationId
            write(state.copy(members = state.members + (folderId to next)))
        }
    }

    private fun read(): State {
        val raw = prefs.getString(KEY_STATE, null) ?: return State(emptyList(), emptyMap())
        return runCatching {
            val root = JSONObject(raw)
            val folders = root.optJSONArray("folders") ?: JSONArray()
            val folderList = (0 until folders.length()).mapNotNull { i ->
                val o = folders.optJSONObject(i) ?: return@mapNotNull null
                ChatFolder(id = o.optString("id"), name = o.optString("name"))
                    .takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
            }
            val membersObj = root.optJSONObject("members") ?: JSONObject()
            val members = mutableMapOf<String, Set<String>>()
            for (key in membersObj.keys()) {
                val arr = membersObj.optJSONArray(key) ?: continue
                members[key] = (0 until arr.length()).mapNotNull { arr.optString(it, null) }.toSet()
            }
            State(folderList, members)
        }.getOrElse { corrupted = true; State(emptyList(), emptyMap()) }
    }

    private fun write(state: State): Boolean {
        if (corrupted) return false // Preserve unreadable data rather than silently overwriting it.
        val root = JSONObject()
        root.put("folders", JSONArray().apply {
            state.folders.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
        })
        root.put("members", JSONObject().apply {
            state.members.forEach { (k, v) -> put(k, JSONArray(v.toList())) }
        })
        prefs.edit().putString(KEY_STATE, root.toString()).apply()
        _revision.value = _revision.value + 1
        return true
    }

    private companion object {
        const val PREFS_PREFIX = "chat_folders_"
        const val KEY_STATE = "state"
    }
}
