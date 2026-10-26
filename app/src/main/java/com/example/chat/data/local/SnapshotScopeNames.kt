package com.example.chat.data.local

/** Pure helper: recover scope names from snapshot file names
 *  "chaty_snapshot_<safeUser>_<safeScope>.bin". Only "conversations" and
 *  "messages_<id>" names are returned. Callers must still authenticate each name
 *  through the store's readJson (its AAD binds the exact userId:scope), which also
 *  covers prefix ambiguity such as user "a" vs "a_b". Read-only. */
object SnapshotScopeNames {
    private val ALLOWED = Regex("^(conversations|messages_[A-Za-z0-9_.:-]{1,96})$")

    fun scopesFor(safeUser: String, fileNames: List<String>): List<String> {
        val prefix = "chaty_snapshot_${safeUser}_"
        return fileNames.asSequence()
            .filter { it.startsWith(prefix) && it.endsWith(".bin") }
            .map { it.removePrefix(prefix).removeSuffix(".bin") }
            .filter { ALLOWED.matches(it) }
            .distinct().sorted().toList()
    }
}
