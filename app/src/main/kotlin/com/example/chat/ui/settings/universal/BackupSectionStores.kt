package com.example.chat.ui.settings.universal

import android.content.Context
import com.example.chat.core.preferences.LocalPreferencesStorage
import com.example.chat.ui.settings.universal.BackupArchiveLogic.Section
import com.example.chat.ui.settings.universal.BackupPortableLogic.SectionStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Android wiring between BackupPortableLogic's transactional SectionStore
 * contract and the app's real on-device stores. Every store is bound to the
 * currently signed-in account captured at export/import start; credentials,
 * E2EE key material, push tokens and device/deployment config are never
 * part of any section.
 *
 * Sections (v1):
 *  - "preferences"    per-user settings blob via LocalPreferencesStorage
 *  - "wallpapers"     chaty_chat_wallpapers JSON map (preset ids per chat)
 *  - "customization"  chaty_customization key/value map
 *  - "media.zip"      downloaded-media export (see MediaBackupSections)
 */

/** Backs up and restores one SharedPreferences file as a raw JSON map of its
 *  entries. Only used for stores that hold no secrets (see file header). */
private class PrefsFileStore(
    context: Context,
    private val prefsName: String,
    override val name: String,
    override val destinationDescription: String,
    private val allowedKeys: Set<String>? = null,
) : SectionStore {
    private val app = context.applicationContext
    private var stagedData: ByteArray? = null

    private fun readAll(): ByteArray {
        val prefs = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val obj = JSONObject()
        for ((k, v) in prefs.all) {
            if (allowedKeys != null && k !in allowedKeys) continue
            when (v) {
                is String -> obj.put(k, v)
                is Int, is Long, is Boolean, is Float -> obj.put(k, v)
                is Set<*> -> obj.put(k, org.json.JSONArray(v))
                null -> Unit
            }
        }
        return obj.toString().toByteArray(Charsets.UTF_8)
    }

    private fun writeAll(bytes: ByteArray?) {
        val prefs = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val editor = prefs.edit().clear()
        if (bytes != null) {
            val obj = JSONObject(String(bytes, Charsets.UTF_8))
            for (k in obj.keys()) {
                if (allowedKeys != null && k !in allowedKeys) continue
                when (val v = obj.get(k)) {
                    is String -> editor.putString(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is Boolean -> editor.putBoolean(k, v)
                    is Float -> editor.putFloat(k, v)
                    is Double -> editor.putFloat(k, v.toFloat())
                    is org.json.JSONArray -> editor.putStringSet(k, (0 until v.length()).map { v.getString(it) }.toSet())
                }
            }
        }
        if (!editor.commit()) throw IllegalStateException("Could not write $name")
    }

    override fun snapshot(): ByteArray? {
        val prefs = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return if (prefs.all.isEmpty()) null else readAll()
    }

    override fun stage(data: ByteArray) {
        // Validate the JSON now so import aborts before any mutation.
        JSONObject(String(data, Charsets.UTF_8))
        stagedData = data
    }

    override fun commit() {
        writeAll(stagedData ?: throw IllegalStateException("nothing staged"))
    }

    override fun rollback(prior: ByteArray?) {
        writeAll(prior)
    }
}

/** The per-user settings blob (theme/template + universal preferences) lives
 *  behind LocalPreferencesStorage's per-account namespace; backed up as the
 *  resolved map for the current account only. */
private class UserPreferencesStore(
    private val storage: LocalPreferencesStorage,
    private val userId: String,
) : SectionStore {
    override val name = "preferences"
    override val destinationDescription = "App settings and theme for this account (replaces current settings)"
    private var stagedData: ByteArray? = null
    private var priorData: ByteArray? = null

    private suspend fun read(): ByteArray =
        JSONObject(storage.loadPreferences(userId)).toString().toByteArray(Charsets.UTF_8)

    override fun snapshot(): ByteArray? = runBlockingIO { priorData = read(); priorData }
    override fun stage(data: ByteArray) {
        JSONObject(String(data, Charsets.UTF_8))
        stagedData = data
    }

    override fun commit() {
        val data = stagedData ?: throw IllegalStateException("nothing staged")
        runBlockingIO {
            @Suppress("UNCHECKED_CAST")
            val map = org.json.JSONObject(String(data, Charsets.UTF_8)).toMap() as Map<String, Any?>
            if (!storage.savePreferences(map, userId)) throw IllegalStateException("preferences write failed")
        }
    }

    override fun rollback(prior: ByteArray?) {
        runBlockingIO {
            @Suppress("UNCHECKED_CAST")
            val map = if (prior == null) emptyMap()
            else org.json.JSONObject(String(prior, Charsets.UTF_8)).toMap() as Map<String, Any?>
            storage.savePreferences(map, userId)
        }
    }

    private fun <T> runBlockingIO(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { withContext(Dispatchers.IO) { block() } }
}

/** JSONObject -> Map helper (org.json has no toMap on Android). */
private fun JSONObject.toMap(): Map<String, Any?> {
    val out = LinkedHashMap<String, Any?>()
    for (k in keys()) out[k] = if (isNull(k)) null else get(k)
    return out
}


/**
 * Restores the media.zip section as ordinary files in the user's Downloads
 * under Download/ChatyRestore-<epochSeconds>/. Restore ADDS user-visible
 * copies only: it never writes into the app's encrypted AttachmentFileCache
 * (entries there are keyed by live server message refs and would be orphaned
 * and evicted) and never touches restricted/view-once content, which was
 * never cached in the first place. Commit records every created content URI;
 * rollback deletes exactly those.
 */
private class MediaRestoreStore(
    context: Context,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : SectionStore {
    override val name = "media"
    override val destinationDescription = "Downloaded photos, videos and files (added as copies in Downloads/ChatyRestore; existing files untouched)"
    private val app = context.applicationContext
    private var stagedEntries: List<Pair<String, ByteArray>>? = null
    private val createdUris = mutableListOf<android.net.Uri>()

    override fun snapshot(): ByteArray? = null

    override fun stage(data: ByteArray) {
        if (data.size > BackupArchiveLogic.MAX_ENTRY_BYTES) {
            throw BackupArchiveLogic.InvalidArchive("Media backup is too large.")
        }
        val entries = mutableListOf<Pair<String, ByteArray>>()
        java.util.zip.ZipInputStream(data.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val safe = entry.name
                if (!Regex("[A-Za-z0-9._-]+").matches(safe)) {
                    throw BackupArchiveLogic.InvalidArchive("Bad media file name in backup: $safe")
                }
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = zip.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > BackupArchiveLogic.MAX_ENTRY_BYTES) {
                        throw BackupArchiveLogic.InvalidArchive("A media file in the backup is too large.")
                    }
                    out.write(buf, 0, n)
                }
                entries += safe to out.toByteArray()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        // Multiple media.NNN.zip chunks stage into ONE store: append.
        stagedEntries = (stagedEntries ?: emptyList()) + entries
    }

    override fun commit() {
        val entries = stagedEntries ?: throw IllegalStateException("nothing staged")
        val resolver = app.contentResolver
        val folder = "Download/ChatyRestore-${nowSeconds()}"
        for ((fileName, bytes) in entries) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, folder)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create $fileName in Downloads")
            createdUris += uri
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Could not write $fileName")
        }
    }

    override fun rollback(prior: ByteArray?) {
        val resolver = app.contentResolver
        for (uri in createdUris) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
        }
        createdUris.clear()
    }
}

/** Collects the exportable sections for the current account. */
class BackupSectionCollector(
    context: Context,
    private val storage: LocalPreferencesStorage,
    private val userId: String,
    private val mediaExporter: suspend () -> List<Section>,
) {
    private val app = context.applicationContext

    /** Resolves a section name to its store. Media sections are chunked
     *  (media.NNN.zip) so any single entry stays under the archive cap. */
    fun storeFor(): (String) -> SectionStore? {
        val fixed: Map<String, SectionStore> = mapOf(
            "preferences" to UserPreferencesStore(storage, userId),
            "wallpapers" to PrefsFileStore(
                app, "chaty_chat_wallpapers", "wallpapers",
                destinationDescription = "Per-chat wallpaper choices (replaces current choices)",
                allowedKeys = setOf("chaty_chat_wallpapers_v1"),
            ),
            "customization" to PrefsFileStore(
                app, "chaty_customization", "customization",
                destinationDescription = "Look-and-feel variants - theme, bubbles, navigation (replaces current)",
                allowedKeys = setOf("chaty.customization.snapshot_v1"),
            ),
        )
        val media = MediaRestoreStore(app)
        return { name ->
            fixed[name] ?: if (name.startsWith("media.") && name.endsWith(".zip")) media else null
        }
    }

    suspend fun collectSections(): List<Section> {
        val sections = mutableListOf<Section>()
        sections += Section(
            "preferences",
            JSONObject(storage.loadPreferences(userId)).toString().toByteArray(Charsets.UTF_8),
        )
        sections += Section(
            "wallpapers",
            PrefsFileStore(app, "chaty_chat_wallpapers", "wallpapers",
                destinationDescription = "", allowedKeys = setOf("chaty_chat_wallpapers_v1"))
                .snapshot() ?: "{}".toByteArray(),
        )
        sections += Section(
            "customization",
            PrefsFileStore(app, "chaty_customization", "customization",
                destinationDescription = "", allowedKeys = setOf("chaty.customization.snapshot_v1"))
                .snapshot() ?: "{}".toByteArray(),
        )
        sections += mediaExporter() ?: emptyList()
        return sections
    }
}
/** Result of a media export: chunked sections plus skip counts for the UI. */
data class MediaExportResult(
    val sections: List<Section>,
    val includedFiles: Int,
    val skippedOversizeFiles: Int,
    val skippedOverQuotaFiles: Int,
)

/** Reads the CURRENT account's encrypted attachment cache via
 *  AttachmentFileCache's own verified-read API (account/tenant re-checked
 *  per read; view-once and restricted attachments are never in the cache)
 *  and packs original-name files into bounded media.NNN.zip chunks.
 *  Misses/corrupt entries are skipped, never fetched from the network. */
suspend fun exportMediaSections(
    api: com.example.chat.data.api.ApiBackendService,
    cache: com.example.chat.data.local.AttachmentFileCache,
): MediaExportResult = withContext(Dispatchers.IO) {
    val files = mutableListOf<Pair<String, ByteArray>>()
    var index = 0
    for (conv in api.conversations) {
        for (message in api.getMessages(conv.id)) {
            val attachment = message.attachment ?: continue
            val ref = com.example.chat.data.media.AttachmentRefs.refOf(conv.id, message) ?: continue
            val read = cache.readVerified(ref, BackupArchiveLogic.MAX_MEDIA_FILE_BYTES.toInt())
            if (read !is com.example.chat.data.local.StoreRead.Hit) continue
            val base = attachment.name.ifEmpty { ref.attachmentId }
                .replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "file" }
            files += "${index}_$base" to read.value
            index++
        }
    }
    val chunked = BackupArchiveLogic.chunkMediaFiles(files)
    MediaExportResult(
        sections = chunked.sections,
        includedFiles = chunked.includedFiles,
        skippedOversizeFiles = chunked.skippedOversizeFiles,
        skippedOverQuotaFiles = chunked.skippedOverQuotaFiles,
    )
}
