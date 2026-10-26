package com.example.chat.ui.settings.universal

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure-logic port of lib/features/settings/universal/backup_and_restore_screen.dart
 * (every function except widget build). Pure JVM - directories and preference
 * snapshots are constructor-injected so the JVM harness can exercise it.
 *
 * Faithful behaviors preserved:
 * - Backup writes docs/ChatyBackups/chaty_backup_<epochMillis>.json with the
 *   exact payload shape (kind/schemaVersion/createdAt/target/preferences/
 *   conversations).
 * - Restore reads the NEWEST .json by modified time, validates
 *   kind == chaty_full_backup, and applies ONLY the universal preferences map
 *   (the Dart restore intentionally ignores home/conversation/privacy/security/
 *   notification and the conversations array).
 * - Clear deletes every file under the temp dir recursively and all but the
 *   newest backup; freed bytes are summed BEFORE each delete.
 * - Storage footprint = recursive size of docs dir + temp dir (docs includes
 *   the ChatyBackups contents, as in Dart).
 */
class BackupRestoreLogic(
    private val docsDir: () -> File,
    private val tempDir: () -> File,
    private val preferencesSnapshot: () -> Map<String, Map<String, Any?>>,
    private val conversationsSnapshot: () -> List<Map<String, Any?>>,
    private val applyUniversal: (Map<String, Any?>) -> Unit,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    sealed class Result {
        data class BackupOk(val fileName: String, val message: String) : Result()
        data class RestoreOk(val fileName: String, val message: String) : Result()
        data class ClearedOk(val freedBytes: Long, val message: String) : Result()
        /** User-facing failure with the exact Dart snackbar text. */
        data class Failure(val message: String) : Result()
    }

    val backupDirName: String get() = "ChatyBackups"
    val backupPath: String get() = File(docsDir(), backupDirName).path
    val databasePath: String get() = File(docsDir(), "Databases").path

    fun ensureBackupDir(): File {
        val dir = File(docsDir(), backupDirName)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.walkTopDown().forEach { f -> if (f.isFile) size += f.length() }
        return size
    }

    /** Dart _initPathsAndStorage: docDir + tempDir recursive sizes. */
    fun totalStorageBytes(): Long = dirSize(docsDir()) + dirSize(tempDir())

    fun formatMb(bytes: Long): String =
        String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024.0))

    /** Dart payload assembly, extracted for testability. */
    fun buildBackupPayload(target: String, createdAtIso: String): Map<String, Any?> {
        val conversations = conversationsSnapshot()
        return linkedMapOf(
            "kind" to BACKUP_KIND,
            "schemaVersion" to 1,
            "createdAt" to createdAtIso,
            "target" to target,
            "preferences" to preferencesSnapshot(),
            "conversations" to conversations,
        )
    }

    /** Dart _performBackup. Returns the created file name or Failure. */
    fun performBackup(target: String): Result {
        // CONTAINMENT: the legacy export wrote PLAINTEXT JSON (preferences + chat
        // list text). Disabled until an encrypted backup replaces it. No bypass flag.
        // Existing files are never read, changed or deleted here.
        if (true) {
            return Result.Failure(
                "Backup is paused: the old export was not encrypted. An encrypted backup " +
                    "is being built. Nothing was written and existing files were not changed.",
            )
        }
        return try {
            val dir = ensureBackupDir()
            val timestamp = nowMillis()
            val file = File(dir, "chaty_backup_$timestamp.json")
            // Dart DateTime.now().toIso8601String(): local ISO-8601, no zone suffix.
            // java.time is unavailable below API 26 without desugaring (not enabled
            // in this project), so millisecond ISO via SimpleDateFormat.
            val createdAtIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date())
            val payload = buildBackupPayload(target, createdAtIso)
            file.writeText(toJson(payload))
            Result.BackupOk(
                file.name,
                "$target backup created successfully! Saved to ${file.name}",
            )
        } catch (e: Exception) {
            Result.Failure("Backup failed: $e")
        }
    }

    /**
     * Dart manifest validation + application. Throws IllegalArgumentException
     * with the Dart FormatException message when kind does not match.
     * Applies ONLY universal preferences, matching the Dart restore.
     */
    fun applyBackupManifest(map: Map<String, Any?>) {
        if (map["kind"] != BACKUP_KIND) {
            throw IllegalArgumentException("Invalid or unverified backup manifest format.")
        }
        @Suppress("UNCHECKED_CAST")
        val prefsMap = map["preferences"] as? Map<String, Any?>
        if (prefsMap != null) {
            @Suppress("UNCHECKED_CAST")
            val univMap = prefsMap["universal"] as? Map<String, Any?>
            if (univMap != null) {
                applyUniversal(univMap)
            }
        }
    }

    /** Dart newest-backup selection: latest modified .json, null when none. */
    fun selectLatestBackup(dir: File): File? {
        if (!dir.exists()) return null
        var latest: File? = null
        var latestTime = 0L
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.path.endsWith(".json")) {
                val modified = f.lastModified()
                if (modified > latestTime) {
                    latestTime = modified
                    latest = f
                }
            }
        }
        return latest
    }

    /** Dart _performRestore. */
    fun performRestore(target: String): Result {
        // CONTAINMENT: restore auto-picked the newest unauthenticated .json (anyone
        // or anything able to drop a file in app storage could change settings).
        // Disabled until restore authenticates its source. Files are left untouched.
        if (true) {
            return Result.Failure(
                "Restore is paused until encrypted backups are ready. Your existing " +
                    "backup files were not read, changed or deleted.",
            )
        }
        return try {
            val dir = File(docsDir(), backupDirName)
            if (!dir.exists()) {
                return Result.Failure("No backup directory found.")
            }
            val latestBackup = selectLatestBackup(dir)
                ?: return Result.Failure("No local backups found to restore.")

            val map = fromJson(latestBackup.readText())
            applyBackupManifest(map)
            Result.RestoreOk(
                latestBackup.name,
                "$target data restored successfully from ${latestBackup.name}!",
            )
        } catch (e: Exception) {
            Result.Failure("Restore failed: $e")
        }
    }

    /** Dart _clearTemporaryAndBackups: wipe temp files, keep newest backup. */
    fun clearTemporaryAndBackups(): Result {
        var freed = 0L
        try {
            val temp = tempDir()
            if (temp.exists()) {
                temp.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        val len = f.length()
                        if (f.delete()) freed += len
                    }
                }
            }
            val backupDir = File(docsDir(), backupDirName)
            if (backupDir.exists()) {
                val backups = backupDir.listFiles()?.filter { it.isFile }
                    ?.sortedByDescending { it.lastModified() } ?: emptyList()
                // Keep the most recent backup, clear older ones
                if (backups.size > 1) {
                    for (i in 1 until backups.size) {
                        val len = backups[i].length()
                        if (backups[i].delete()) freed += len
                    }
                }
            }
        } catch (_: Exception) {
        }
        return Result.ClearedOk(
            freed,
            "Old temporary backups & cache cleared! (${formatMb(freed)} MB freed)",
        )
    }

    companion object {
        const val BACKUP_KIND = "chaty_full_backup"

        /** Conversation map keys in the exact Dart payload order. */
        val CONVERSATION_KEYS = listOf(
            "id", "title", "type", "participantIds", "lastMessageText",
            "lastMessageTime", "unreadCount", "isMuted", "isPinned", "isArchived",
        )

        /** Preference section keys in the exact Dart payload order. */
        val PREFERENCE_KEYS = listOf(
            "universal", "home", "conversation", "privacy", "security", "notification",
        )

        fun toJson(value: Any?): String = when (value) {
            null -> "null"
            is String -> JSONObject.quote(value)
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> value.entries.joinToString(",", "{", "}") {
                "${JSONObject.quote(it.key.toString())}:${toJson(it.value)}"
            }
            is List<*> -> value.joinToString(",", "[", "]") { toJson(it) }
            else -> JSONObject.quote(value.toString())
        }

        @Suppress("UNCHECKED_CAST")
        fun fromJson(text: String): Map<String, Any?> = jsonToAny(JSONObject(text)) as Map<String, Any?>

        private fun jsonToAny(v: Any?): Any? = when (v) {
            null, JSONObject.NULL -> null
            is JSONObject -> {
                val out = LinkedHashMap<String, Any?>()
                for (key in v.keys()) out[key] = jsonToAny(v.get(key))
                out
            }
            is JSONArray -> (0 until v.length()).map { jsonToAny(v.get(it)) }
            else -> v
        }
    }
}
