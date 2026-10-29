package com.example.chat.ui.settings.universal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Container format for Chaty's portable encrypted backup.
 *
 * The plaintext payload is a ZIP with a fixed, minimal layout:
 *   manifest.json       - kind/schemaVersion/createdAt/accountId + per-section
 *                         sha256 and byte counts
 *   sections/<name>     - one entry per exported section (settings JSON,
 *                         wallpaper files, media files, message snapshot)
 *
 * The whole ZIP is then encrypted with BackupCrypto (device-bound v1 or
 * passphrase v2). Validation rules are defense-in-depth against hostile or
 * corrupted archives: entry names must match a strict whitelist shape (no
 * absolute paths, no "..", no backslashes, no drive letters), and archive and
 * per-entry sizes are capped before decompression to bound memory use.
 *
 * Pure JVM so the unit harness exercises every rule.
 */
object BackupArchiveLogic {

    const val ARCHIVE_KIND = "chaty_portable_backup"
    const val SCHEMA_VERSION = 2
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val SECTION_PREFIX = "sections/"
    private val SECTION_NAME = Regex("[A-Za-z0-9._-]+")

    /** Hard caps applied before any decompression. */
    const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

    class InvalidArchive(message: String) : Exception(message)

    data class Section(val name: String, val data: ByteArray) {
        val sha256: String get() = sha256Hex(data)
    }

    data class ParsedArchive(
        val kind: String,
        val schemaVersion: Int,
        val createdAt: String,
        val accountId: String,
        val sections: List<Section>,
    )

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Builds the plaintext ZIP for [sections] tagged to [accountId]. */
    fun buildZip(accountId: String, createdAt: String, sections: List<Section>): ByteArray {
        for (s in sections) {
            require(SECTION_NAME.matches(s.name)) { "Illegal section name: ${s.name}" }
        }
        val manifest = JSONObject()
            .put("kind", ARCHIVE_KIND)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("createdAt", createdAt)
            .put("accountId", accountId)
            .put("sections", JSONArray().apply {
                sections.forEach { s ->
                    put(JSONObject()
                        .put("name", s.name)
                        .put("bytes", s.data.size)
                        .put("sha256", s.sha256))
                }
            })
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            sections.forEach { s ->
                zip.putNextEntry(ZipEntry(SECTION_PREFIX + s.name))
                zip.write(s.data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /**
     * Parses and fully verifies a plaintext archive ZIP. Throws
     * [InvalidArchive] on any structural, naming, size or hash violation.
     */
    fun parseZip(bytes: ByteArray): ParsedArchive {
        if (bytes.size > MAX_ARCHIVE_BYTES) throw InvalidArchive("Backup file is too large.")
        var manifestJson: String? = null
        val found = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val isManifest = name == MANIFEST_ENTRY
                val isSection = name.startsWith(SECTION_PREFIX) &&
                    SECTION_NAME.matches(name.removePrefix(SECTION_PREFIX))
                if (!isManifest && !isSection) {
                    throw InvalidArchive("Backup contains an unexpected file: $name")
                }
                if (entry.size > MAX_ENTRY_BYTES) {
                    throw InvalidArchive("A file inside the backup is too large.")
                }
                val data = zip.readBytesCapped(MAX_ENTRY_BYTES)
                if (isManifest) manifestJson = String(data, Charsets.UTF_8)
                else found[name.removePrefix(SECTION_PREFIX)] = data
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val manifestText = manifestJson
            ?: throw InvalidArchive("Backup has no manifest; it is not a Chaty backup.")
        val manifest = try { JSONObject(manifestText) } catch (e: Exception) {
            throw InvalidArchive("Backup manifest is unreadable.")
        }
        if (manifest.optString("kind") != ARCHIVE_KIND) {
            throw InvalidArchive("Not a Chaty portable backup file.")
        }
        val version = manifest.optInt("schemaVersion", -1)
        if (version != SCHEMA_VERSION) {
            throw InvalidArchive("Unsupported backup version $version.")
        }
        val accountId = manifest.optString("accountId")
        val declared = manifest.optJSONArray("sections")
            ?: throw InvalidArchive("Backup manifest lists no contents.")
        val declaredByName = mutableMapOf<String, Pair<Long, String>>()
        for (i in 0 until declared.length()) {
            val d = declared.getJSONObject(i)
            declaredByName[d.getString("name")] = d.getLong("bytes") to d.getString("sha256")
        }
        if (declaredByName.keys != found.keys) {
            throw InvalidArchive("Backup contents do not match its manifest.")
        }
        val sections = found.map { (name, data) ->
            val (bytes, hash) = declaredByName.getValue(name)
            if (data.size.toLong() != bytes || sha256Hex(data) != hash) {
                throw InvalidArchive("Backup contents were modified: $name")
            }
            Section(name, data)
        }
        return ParsedArchive(
            kind = ARCHIVE_KIND,
            schemaVersion = version,
            createdAt = manifest.optString("createdAt"),
            accountId = accountId,
            sections = sections,
        )
    }

    private fun ZipInputStream.readBytesCapped(cap: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > cap) throw InvalidArchive("A file inside the backup is too large.")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
    /** Largest single media file the exporter will include: its chunk zip must
     *  stay under MAX_ENTRY_BYTES after zip overhead, so the importer never
     *  rejects an archive the exporter built. */
    const val MAX_MEDIA_FILE_BYTES = 48L * 1024 * 1024
    /** Target size per media.NNN.zip chunk. */
    const val MEDIA_CHUNK_BYTES = 32L * 1024 * 1024
    /** Aggregate media cap keeping the whole archive under MAX_ARCHIVE_BYTES. */
    const val MAX_MEDIA_TOTAL_BYTES = 448L * 1024 * 1024

    data class MediaChunkResult(
        val sections: List<Section>,
        val includedFiles: Int,
        val skippedOversizeFiles: Int,
        val skippedOverQuotaFiles: Int,
    )

    /** Chunks named media files into media.NNN.zip sections. A file over
     *  [MAX_MEDIA_FILE_BYTES] is skipped (counted); once the aggregate cap is
     *  hit the rest are skipped (counted). Deterministic and pure for tests. */
    fun chunkMediaFiles(
        entries: List<Pair<String, ByteArray>>,
        maxFileBytes: Long = MAX_MEDIA_FILE_BYTES,
        chunkBytes: Long = MEDIA_CHUNK_BYTES,
        maxTotalBytes: Long = MAX_MEDIA_TOTAL_BYTES,
    ): MediaChunkResult {
        val sections = mutableListOf<Section>()
        var included = 0
        var skippedOversize = 0
        var skippedQuota = 0
        var chunkOut = ByteArrayOutputStream()
        var chunkZip = ZipOutputStream(chunkOut)
        var chunkBytes = 0L
        var chunkIndex = 0
        var totalBytes = 0L
        fun flush() {
            if (chunkBytes == 0L) return
            chunkZip.close()
            sections += Section("media.%03d.zip".format(chunkIndex), chunkOut.toByteArray())
            chunkIndex++
            chunkOut = ByteArrayOutputStream()
            chunkZip = ZipOutputStream(chunkOut)
            chunkBytes = 0L
        }
        for ((name, bytes) in entries) {
            if (bytes.size > maxFileBytes) { skippedOversize++; continue }
            if (totalBytes + bytes.size > maxTotalBytes) { skippedQuota++; continue }
            if (chunkBytes > 0 && chunkBytes + bytes.size > chunkBytes) flush()
            chunkZip.putNextEntry(ZipEntry(name))
            chunkZip.write(bytes)
            chunkZip.closeEntry()
            chunkBytes += bytes.size
            totalBytes += bytes.size
            included++
        }
        flush()
        return MediaChunkResult(sections, included, skippedOversize, skippedQuota)
    }
}
