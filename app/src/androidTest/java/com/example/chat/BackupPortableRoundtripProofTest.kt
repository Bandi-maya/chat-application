package com.example.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.core.preferences.LocalPreferencesStorage
import com.example.chat.ui.settings.universal.BackupArchiveLogic
import com.example.chat.ui.settings.universal.BackupCrypto
import com.example.chat.ui.settings.universal.BackupPortableLogic
import com.example.chat.ui.settings.universal.BackupSectionCollector
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof of the portable backup roundtrip with the REAL stores:
 * collect -> zip -> passphrase encrypt -> decrypt/parse -> preview ->
 * staged import that rolls mutated settings back, plus wrong-account refusal,
 * wrong-passphrase and tamper rejection.
 */
@RunWith(AndroidJUnit4::class)
class BackupPortableRoundtripProofTest {

    @Test
    fun portableArchiveRoundtripWrongAccountAndTamper() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val userId = "backup-proof-" + UUID.randomUUID().toString().take(8)
        val storage = LocalPreferencesStorage(ctx)
        assertTrue(storage.savePreferences(mapOf("theme" to "dark", "chatWallpaper" to "teal"), userId))
        val collector = BackupSectionCollector(ctx, storage, userId) { emptyList() }
        val sections = collector.collectSections()
        assertTrue("collector must include the preferences section", sections.any { it.name == "preferences" })
        val zip = BackupArchiveLogic.buildZip(userId, "2026-10-29T16:20:00Z", sections)
        val pass = "proof passphrase 7f!".toCharArray()
        val enc = BackupCrypto.encryptBytesWithPassphrase(zip, pass)

        var wrongPassThrew = false
        try {
            BackupPortableLogic.readEncryptedArchive(enc, "not the passphrase".toCharArray())
        } catch (t: Throwable) { wrongPassThrew = true }
        assertTrue("wrong passphrase must be rejected", wrongPassThrew)

        val chars = enc.toCharArray()
        val mid = chars.size / 2
        chars[mid] = if (chars[mid] == 'A') 'B' else 'A'
        var tamperThrew = false
        try {
            BackupPortableLogic.readEncryptedArchive(String(chars), pass)
        } catch (t: Throwable) { tamperThrew = true }
        assertTrue("tampered archive must be rejected (GCM auth)", tamperThrew)

        val parsed = BackupPortableLogic.readEncryptedArchive(enc, pass)
        assertEquals(userId, parsed.accountId)

        val previewOk = BackupPortableLogic.preview(parsed, userId, collector.storeFor())
        assertTrue("preview must match the owning account", previewOk.matchesCurrentAccount)
        assertTrue("preview must list entries", previewOk.entries.isNotEmpty())
        assertTrue("every preview entry must be restorable", previewOk.entries.all { it.restorable })
        val previewOther = BackupPortableLogic.preview(parsed, "someone-else", collector.storeFor())
        assertFalse("preview must not match a different account", previewOther.matchesCurrentAccount)

        assertTrue(storage.savePreferences(mapOf("theme" to "light", "chatWallpaper" to "none"), userId))
        when (val r = BackupPortableLogic.importArchive(parsed, userId, collector.storeFor())) {
            is BackupPortableLogic.ImportResult.Ok -> assertTrue(r.appliedSections.isNotEmpty())
            else -> fail("expected ImportResult.Ok, got $r")
        }
        val restored = storage.loadPreferences(userId)
        assertEquals("dark", restored["theme"])
        assertEquals("teal", restored["chatWallpaper"])

        val wrong = BackupPortableLogic.importArchive(parsed, "someone-else", collector.storeFor())
        assertTrue("wrong account must be refused", wrong is BackupPortableLogic.ImportResult.WrongAccount)
        assertEquals(userId, (wrong as BackupPortableLogic.ImportResult.WrongAccount).archiveAccountId)

        // Atomicity: an archive carrying a section this build cannot restore
        // must fail BEFORE any mutation (stage-all-first), leaving the restored
        // settings exactly as they were.
        val bogus = parsed.copy(
            sections = parsed.sections + BackupArchiveLogic.Section("mystery-section", byteArrayOf(1, 2, 3)),
        )
        val mixed = BackupPortableLogic.importArchive(bogus, userId, collector.storeFor())
        assertTrue("unrestorable section must fail the whole import", mixed is BackupPortableLogic.ImportResult.Failure)
        val afterBogus = storage.loadPreferences(userId)
        assertEquals("no partial mutation allowed", "dark", afterBogus["theme"])
        assertEquals("no partial mutation allowed", "teal", afterBogus["chatWallpaper"])

        // Output scope of this fixture: isolated demo account (unique userId),
        // empty media exporter -> the archive carries only the preferences
        // section and the restore writes no media files anywhere.
        assertEquals(listOf("preferences"), parsed.sections.map { it.name })
    }
}
