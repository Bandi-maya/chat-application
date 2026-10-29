package com.example.chat

import com.example.chat.ui.settings.universal.BackupArchiveLogic
import com.example.chat.ui.settings.universal.BackupCrypto
import com.example.chat.ui.settings.universal.BackupPortableLogic
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupPortableArchiveTest {

    private fun section(name: String, text: String) =
        BackupArchiveLogic.Section(name, text.toByteArray(Charsets.UTF_8))

    // ---------- container integrity ----------

    @Test fun `zip roundtrip preserves sections and verifies hashes`() {
        val zip = BackupArchiveLogic.buildZip(
            "user-1", "2026-10-29T14:00:00.000",
            listOf(section("settings.json", "{\"a\":1}"), section("media_0.bin", "bytes")),
        )
        val parsed = BackupArchiveLogic.parseZip(zip)
        assertEquals("user-1", parsed.accountId)
        assertEquals(2, parsed.sections.size)
        assertEquals("{\"a\":1}", String(parsed.sections.first { it.name == "settings.json" }.data))
    }

    private fun rawZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (n, d) -> z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() }
        }
        return out.toByteArray()
    }

    @Test fun `zip-slip entry rejected`() {
        val zip = rawZip("manifest.json" to "{}".toByteArray(), "sections/../../evil" to byteArrayOf(1))
        assertThrows(BackupArchiveLogic.InvalidArchive::class.java) { BackupArchiveLogic.parseZip(zip) }
    }

    @Test fun `absolute path entry rejected`() {
        val zip = rawZip("manifest.json" to "{}".toByteArray(), "/etc/passwd" to byteArrayOf(1))
        assertThrows(BackupArchiveLogic.InvalidArchive::class.java) { BackupArchiveLogic.parseZip(zip) }
    }

    @Test fun `backslash traversal rejected`() {
        val zip = rawZip("manifest.json" to "{}".toByteArray(), "sections/..\\evil" to byteArrayOf(1))
        assertThrows(BackupArchiveLogic.InvalidArchive::class.java) { BackupArchiveLogic.parseZip(zip) }
    }

    @Test fun `tampered section bytes rejected by manifest hash`() {
        val zip = BackupArchiveLogic.buildZip("u", "t", listOf(section("s.json", "original")))
        val parsed = BackupArchiveLogic.parseZip(zip)
        assertEquals(1, parsed.sections.size)
        // Rebuild a zip whose manifest claims the hash of "original" but carries "forged"
        val manifest = JSONObject()
            .put("kind", BackupArchiveLogic.ARCHIVE_KIND)
            .put("schemaVersion", BackupArchiveLogic.SCHEMA_VERSION)
            .put("createdAt", "t").put("accountId", "u")
            .put("sections", org.json.JSONArray().put(JSONObject()
                .put("name", "s.json").put("bytes", 6L)
                .put("sha256", BackupArchiveLogic.sha256Hex("original".toByteArray()))))
        val forged = rawZip("manifest.json" to manifest.toString().toByteArray(),
            "sections/s.json" to "forged".toByteArray())
        assertThrows(BackupArchiveLogic.InvalidArchive::class.java) { BackupArchiveLogic.parseZip(forged) }
    }

    @Test fun `wrong kind rejected`() {
        val manifest = JSONObject().put("kind", "other").put("schemaVersion", 2)
            .put("sections", org.json.JSONArray())
        val zip = rawZip("manifest.json" to manifest.toString().toByteArray())
        assertThrows(BackupArchiveLogic.InvalidArchive::class.java) { BackupArchiveLogic.parseZip(zip) }
    }

    @Test fun `oversize archive rejected before parse`() {
        val big = ByteArray(BackupArchiveLogic.MAX_ARCHIVE_BYTES.toInt() + 1)
        assertThrows(BackupArchiveLogic.InvalidArchive::class.java) { BackupArchiveLogic.parseZip(big) }
    }

    // ---------- passphrase crypto ----------

    @Test fun `passphrase roundtrip and wrong passphrase rejected`() {
        val zip = BackupArchiveLogic.buildZip("u", "t", listOf(section("s.json", "secret")))
        val enc = BackupCrypto.encryptBytesWithPassphrase(zip, "correct horse".toCharArray())
        val back = BackupCrypto.decryptBytesWithPassphrase(enc, "correct horse".toCharArray())
        assertArrayEquals(zip, back)
        assertThrows(Exception::class.java) {
            BackupCrypto.decryptBytesWithPassphrase(enc, "wrong".toCharArray())
        }
    }

    @Test fun `pbkdf2 parameters are not silently weakened`() {
        val enc = BackupCrypto.encryptWithPassphrase("x", "p".toCharArray())
        assertTrue(JSONObject(enc).getString("alg").contains("600000"))
    }

    // ---------- account isolation + transactional restore ----------

    private class FakeStore(override val name: String, private val failOnCommit: Boolean = false)
        : BackupPortableLogic.SectionStore {
        override val destinationDescription = "test destination"
        var current: ByteArray? = null
        var staged: ByteArray? = null
        var rolledBackWith: ByteArray? = null
        var rollbackCalls = 0
        override fun snapshot(): ByteArray? = current
        override fun stage(data: ByteArray) { staged = data }
        override fun commit() { if (failOnCommit) throw IllegalStateException("boom") else current = staged }
        override fun rollback(prior: ByteArray?) { rollbackCalls++; rolledBackWith = prior; current = prior }
    }

    private fun archiveFor(account: String): BackupArchiveLogic.ParsedArchive {
        val zip = BackupArchiveLogic.buildZip(account, "t",
            listOf(section("prefs", "new-prefs"), section("media", "new-media")))
        return BackupArchiveLogic.parseZip(zip)
    }

    @Test fun `import refuses mismatched account with no mutation`() {
        val store = FakeStore("prefs")
        val result = BackupPortableLogic.importArchive(
            archiveFor("account-A"), "account-B") { name -> mapOf("prefs" to store)[name] }
        assertTrue(result is BackupPortableLogic.ImportResult.WrongAccount)
        assertNull(store.staged)
        assertNull(store.current)
    }

    @Test fun `import refuses signed-out restore`() {
        val result = BackupPortableLogic.importArchive(
            archiveFor("account-A"), null) { null }
        assertTrue(result is BackupPortableLogic.ImportResult.Failure)
    }

    @Test fun `successful import applies all sections`() {
        val prefs = FakeStore("prefs"); val media = FakeStore("media")
        val result = BackupPortableLogic.importArchive(
            archiveFor("account-A"), "account-A") { name -> mapOf("prefs" to prefs, "media" to media)[name] }
        assertTrue(result is BackupPortableLogic.ImportResult.Ok)
        assertEquals("new-prefs", String(prefs.current!!))
        assertEquals("new-media", String(media.current!!))
    }

    @Test fun `commit failure rolls every store back to prior state`() {
        val prefs = FakeStore("prefs"); val media = FakeStore("media", failOnCommit = true)
        prefs.current = "old-prefs".toByteArray()
        media.current = "old-media".toByteArray()
        val result = BackupPortableLogic.importArchive(
            archiveFor("account-A"), "account-A") { name -> mapOf("prefs" to prefs, "media" to media)[name] }
        assertTrue(result is BackupPortableLogic.ImportResult.Failure)
        assertEquals("old-prefs", String(prefs.current!!))
        assertEquals("old-media", String(media.current!!))
        assertEquals(1, prefs.rollbackCalls)
        assertEquals(1, media.rollbackCalls)
    }

    @Test fun `unknown section refuses before any mutation`() {
        val prefs = FakeStore("prefs")
        prefs.current = "old".toByteArray()
        val result = BackupPortableLogic.importArchive(
            archiveFor("account-A"), "account-A") { name -> mapOf("prefs" to prefs)[name] } // no "media" store
        assertTrue(result is BackupPortableLogic.ImportResult.Failure)
        assertEquals("old", String(prefs.current!!))
        assertNull(prefs.staged)
    }

    // ---------- media chunking ----------

    @Test fun `single oversized file is skipped, others still exported`() {
        val small = "ok".toByteArray()
        val huge = ByteArray(BackupArchiveLogic.MAX_MEDIA_FILE_BYTES.toInt() + 1)
        val result = BackupArchiveLogic.chunkMediaFiles(listOf("a.jpg" to small, "big.mp4" to huge))
        assertEquals(1, result.includedFiles)
        assertEquals(1, result.skippedOversizeFiles)
        assertEquals(1, result.sections.size)
        // exported archive passes the importer's own caps
        val zip = BackupArchiveLogic.buildZip("u", "t", result.sections)
        BackupArchiveLogic.parseZip(zip)
    }

    @Test fun `many files split into bounded chunks that all parse`() {
        val file = ByteArray(BackupArchiveLogic.MEDIA_CHUNK_BYTES.toInt() / 2)
        val entries = (0 until 5).map { "f$it.bin" to file } // 2.5x chunk size total
        val result = BackupArchiveLogic.chunkMediaFiles(entries)
        assertEquals(5, result.includedFiles)
        assertTrue(result.sections.size >= 2)
        for (s in result.sections) {
            assertTrue(s.name.matches(Regex("media\.\d{3}\.zip")))
            assertTrue(s.data.size <= BackupArchiveLogic.MAX_ENTRY_BYTES)
        }
        val zip = BackupArchiveLogic.buildZip("u", "t", result.sections)
        val parsed = BackupArchiveLogic.parseZip(zip)
        assertEquals(result.sections.size, parsed.sections.size)
    }

    @Test fun `aggregate quota skips remaining files honestly`() {
        val file = ByteArray(6 * 1024 * 1024)
        val entries = (0 until 3).map { "f$it.bin" to file }
        val result = BackupArchiveLogic.chunkMediaFiles(
            entries, maxFileBytes = 8L * 1024 * 1024, maxTotalBytes = 13L * 1024 * 1024)
        assertEquals(2, result.includedFiles)
        assertEquals(1, result.skippedOverQuotaFiles)
    }

    @Test fun `exporter default caps are within importer limits`() {
        assertTrue(BackupArchiveLogic.MAX_MEDIA_FILE_BYTES < BackupArchiveLogic.MAX_ENTRY_BYTES)
        assertTrue(BackupArchiveLogic.MEDIA_CHUNK_BYTES < BackupArchiveLogic.MAX_ENTRY_BYTES)
        assertTrue(BackupArchiveLogic.MAX_MEDIA_TOTAL_BYTES < BackupArchiveLogic.MAX_ARCHIVE_BYTES)
    }

    @Test fun `multi-chunk media sections restore through one store and roll back together`() {
        val zip = BackupArchiveLogic.buildZip("u", "t", listOf(
            section("media.000.zip", "chunk-a"), section("media.001.zip", "chunk-b")))
        val parsed = BackupArchiveLogic.parseZip(zip)
        val media = FakeStore("media")
        media.current = "prior".toByteArray()
        var stageCalls = 0
        val counting = object : BackupPortableLogic.SectionStore by media {
            override fun stage(data: ByteArray) { stageCalls++; media.stage(data) }
        }
        val result = BackupPortableLogic.importArchive(parsed, "u") { name ->
            if (name.startsWith("media.")) counting else null
        }
        assertTrue(result is BackupPortableLogic.ImportResult.Ok)
        assertEquals(2, stageCalls)
    }

    @Test fun `multi-chunk restore failure rolls back with no partial media left`() {
        val zip = BackupArchiveLogic.buildZip("u", "t", listOf(
            section("media.000.zip", "chunk-a"), section("media.001.zip", "chunk-b")))
        val parsed = BackupArchiveLogic.parseZip(zip)
        val media = FakeStore("media", failOnCommit = true)
        media.current = "prior".toByteArray()
        val result = BackupPortableLogic.importArchive(parsed, "u") { name ->
            if (name.startsWith("media.")) media else null
        }
        assertTrue(result is BackupPortableLogic.ImportResult.Failure)
        assertEquals("prior", String(media.current!!))
    }
}
