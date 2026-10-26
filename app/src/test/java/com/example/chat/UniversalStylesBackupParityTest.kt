package com.example.chat

import com.example.chat.ui.core.controllers.BrandIconSource
import com.example.chat.ui.core.controllers.LauncherIconVariant
import com.example.chat.ui.core.controllers.launcherIconVariantFromId
import com.example.chat.ui.core.controllers.title
import com.example.chat.ui.core.notifications.ChatyNotificationIconPresets
import com.example.chat.ui.settings.universal.BackupRestoreLogic
import com.example.chat.ui.settings.universal.CustomFontLogic
import com.example.chat.ui.settings.universal.UniversalStylesCatalog
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UniversalStylesBackupParityTest {

    // ---------- Launcher icon variants ----------

    @Test fun `launcher variant titles are verbatim`() {
        assertEquals("Swift Flight", LauncherIconVariant.bird.title)
        assertEquals("Warm Signature", LauncherIconVariant.warm.title)
        assertEquals("Warm Outline", LauncherIconVariant.outline.title)
        assertEquals("Obsidian", LauncherIconVariant.obsidian.title)
        assertEquals("Spatial Glass", LauncherIconVariant.glass.title)
        assertEquals("Signal", LauncherIconVariant.signal.title)
        assertEquals("Fold", LauncherIconVariant.fold.title)
    }

    @Test fun `launcher fromId legacy aliases and id fallback`() {
        assertEquals(LauncherIconVariant.bird, launcherIconVariantFromId("swift"))
        assertEquals(LauncherIconVariant.warm, launcherIconVariantFromId("original"))
        assertEquals(LauncherIconVariant.outline, launcherIconVariantFromId("minimal"))
        assertEquals(LauncherIconVariant.obsidian, launcherIconVariantFromId("midnight"))
        assertEquals(LauncherIconVariant.glass, launcherIconVariantFromId("bubble"))
        assertEquals(LauncherIconVariant.signal, launcherIconVariantFromId("ocean"))
        assertEquals(LauncherIconVariant.fold, launcherIconVariantFromId("violet"))
        // Dart firstWhere(orElse: bird): raw ids match, unknown falls to bird
        assertEquals(LauncherIconVariant.warm, launcherIconVariantFromId("warm"))
        assertEquals(LauncherIconVariant.fold, launcherIconVariantFromId("fold"))
        assertEquals(LauncherIconVariant.bird, launcherIconVariantFromId(null))
        assertEquals(LauncherIconVariant.bird, launcherIconVariantFromId("bogus"))
    }

    // ---------- Catalog ----------

    @Test fun `catalog launcher list is verbatim 7 entries, slice takes 6`() {
        assertEquals(7, UniversalStylesCatalog.launcherIcons.size)
        assertEquals("Swift Flight", UniversalStylesCatalog.launcherIcons[0].name)
        assertEquals(LauncherIconVariant.fold, UniversalStylesCatalog.launcherIcons[6].variant)
        assertEquals(0xFF25D366, UniversalStylesCatalog.launcherIcons[0].colorArgb)
        assertEquals(6, UniversalStylesCatalog.displayedLauncherIcons(false).size)
        assertEquals(7, UniversalStylesCatalog.displayedLauncherIcons(true).size)
        assertEquals(4, UniversalStylesCatalog.launcherGridColumns(379))
        assertEquals(5, UniversalStylesCatalog.launcherGridColumns(380))
        assertEquals(0.62f, UniversalStylesCatalog.launcherGridAspect(379))
        assertEquals(0.78f, UniversalStylesCatalog.launcherGridAspect(380))
    }

    @Test fun `catalog font styles are verbatim 11 entries`() {
        val names = UniversalStylesCatalog.fontStyles.map { it.name }
        assertEquals(
            listOf(
                "Default", "Roboto-Light", "Roboto-Medium", "ProductSans",
                "FrutigerLTStdRoman", "Bariol", "ComicSans", "BEBASNEUE",
                "Comfortaa", "TRANSFORMERS", "HappyGiraffe",
            ),
            names,
        )
        val bebas = UniversalStylesCatalog.fontStyles.first { it.name == "BEBASNEUE" }
        assertEquals(900, bebas.weight)
        assertEquals(1.5, bebas.letterSpacing, 0.0001)
        val giraffe = UniversalStylesCatalog.fontStyles.first { it.name == "HappyGiraffe" }
        assertTrue(giraffe.italic)
        assertEquals("sans-serif-condensed", UniversalStylesCatalog.fontStyles.first { it.name == "Bariol" }.fontFamilyKey)
    }

    @Test fun `reconcile launcher pref only when bundled and mismatched`() {
        assertNull(
            UniversalStylesCatalog.reconcileLauncherIconPref(
                "Classic", LauncherIconVariant.bird, BrandIconSource.custom,
            )
        )
        assertNull(
            UniversalStylesCatalog.reconcileLauncherIconPref(
                "Swift Flight", LauncherIconVariant.bird, BrandIconSource.bundled,
            )
        )
        assertEquals(
            "Swift Flight",
            UniversalStylesCatalog.reconcileLauncherIconPref(
                "Classic", LauncherIconVariant.bird, BrandIconSource.bundled,
            )
        )
    }

    // ---------- Notification presets ----------

    @Test fun `notification presets are 28, first White, forName falls back to first`() {
        assertEquals(28, ChatyNotificationIconPresets.values.size)
        assertEquals("White", ChatyNotificationIconPresets.values.first().name)
        assertEquals(0xFFFFFFFF, ChatyNotificationIconPresets.values.first().colorArgb)
        assertEquals("Notifybar 27", ChatyNotificationIconPresets.values.last().name)
        assertEquals("Gold", ChatyNotificationIconPresets.forName("Gold").name)
        assertEquals("White", ChatyNotificationIconPresets.forName("nope").name)
        assertEquals("White", ChatyNotificationIconPresets.forName(null).name)
    }

    // ---------- Backup / restore logic ----------

    private fun tempRoot(tag: String): File {
        val root = File(System.getProperty("java.io.tmpdir"), "br-$tag-${System.nanoTime()}")
        root.mkdirs()
        return root
    }

    private class Fixture(root: File) {
        val docs = File(root, "docs").apply { mkdirs() }
        val temp = File(root, "cache").apply { mkdirs() }
        val appliedUniversal = mutableListOf<Map<String, Any?>>()
        val prefs = linkedMapOf(
            "universal" to mapOf<String, Any?>("launcherIcon" to "Classic", "emojiStyle" to "Chaty Motion"),
            "home" to mapOf<String, Any?>("rows" to 2),
            "conversation" to mapOf<String, Any?>("bubble" to "stock"),
            "privacy" to mapOf<String, Any?>("online" to false),
            "security" to mapOf<String, Any?>("locked" to emptyList<String>()),
            "notification" to mapOf<String, Any?>("tone" to "default"),
        )
        val conversations = listOf(
            linkedMapOf<String, Any?>(
                "id" to "c1", "title" to "Alice", "type" to "direct",
                "participantIds" to listOf("u1", "u2"), "lastMessageText" to "hi",
                "lastMessageTime" to "2026-10-25T10:00:00.000", "unreadCount" to 3,
                "isMuted" to false, "isPinned" to true, "isArchived" to false,
            )
        )
        val logic = BackupRestoreLogic(
            docsDir = { docs },
            tempDir = { temp },
            preferencesSnapshot = { prefs },
            conversationsSnapshot = { conversations },
            applyUniversal = { appliedUniversal.add(it) },
            nowMillis = { 1761438600000L },
        )
    }

    @Test fun `backup payload shape matches Dart exactly`() {
        val f = Fixture(tempRoot("shape"))
        val payload = f.logic.buildBackupPayload("Chaty database", "2026-10-25T20:30:00.000")
        assertEquals("chaty_full_backup", payload["kind"])
        assertEquals(1, payload["schemaVersion"])
        assertEquals("2026-10-25T20:30:00.000", payload["createdAt"])
        assertEquals("Chaty database", payload["target"])
        @Suppress("UNCHECKED_CAST")
        val prefsMap = payload["preferences"] as Map<String, Any?>
        assertEquals(BackupRestoreLogic.PREFERENCE_KEYS.toSet(), prefsMap.keys)
        @Suppress("UNCHECKED_CAST")
        val convos = payload["conversations"] as List<Map<String, Any?>>
        assertEquals(1, convos.size)
        assertEquals(BackupRestoreLogic.CONVERSATION_KEYS.toSet(), convos[0].keys)
        assertEquals("c1", convos[0]["id"])
        assertEquals(3, convos[0]["unreadCount"])
    }

    @Test fun `performBackup writes chaty_backup file with fixed timestamp and message`() {
        val f = Fixture(tempRoot("backup"))
        val result = f.logic.performBackup("Chaty database")
        assertTrue(result is BackupRestoreLogic.Result.BackupOk)
        val ok = result as BackupRestoreLogic.Result.BackupOk
        assertEquals("chaty_backup_1761438600000.json", ok.fileName)
        assertEquals("Chaty database backup created successfully! Saved to chaty_backup_1761438600000.json", ok.message)
        val written = File(f.docs, "ChatyBackups/chaty_backup_1761438600000.json")
        assertTrue(written.exists())
        val parsed = BackupRestoreLogic.fromJson(written.readText())
        assertEquals("chaty_full_backup", parsed["kind"])
        // JSON round trip preserves conversation values
        @Suppress("UNCHECKED_CAST")
        val convos = parsed["conversations"] as List<Map<String, Any?>>
        assertEquals("Alice", convos[0]["title"])
    }

    @Test fun `restore applies only universal prefs, from newest backup`() {
        val f = Fixture(tempRoot("restore"))
        // Older backup with a different universal value
        val dir = f.logic.ensureBackupDir()
        val older = File(dir, "chaty_backup_1000.json")
        older.writeText(
            BackupRestoreLogic.toJson(
                mapOf(
                    "kind" to "chaty_full_backup",
                    "preferences" to mapOf("universal" to mapOf("launcherIcon" to "Obsidian")),
                )
            )
        )
        older.setLastModified(1000L)
        f.logic.performBackup("Chaty database")
        val newer = File(dir, "chaty_backup_1761438600000.json")
        newer.setLastModified(2000L)

        val result = f.logic.performRestore("Chaty database")
        assertTrue(result is BackupRestoreLogic.Result.RestoreOk)
        assertEquals("chaty_backup_1761438600000.json", (result as BackupRestoreLogic.Result.RestoreOk).fileName)
        // universal from the NEWER file applied (Classic, from the fixture snapshot)
        assertEquals(1, f.appliedUniversal.size)
        assertEquals("Classic", f.appliedUniversal[0]["launcherIcon"])
    }

    @Test fun `restore failures match Dart messages`() {
        val f = Fixture(tempRoot("failures"))
        // No directory at all
        var result = f.logic.performRestore("Chaty database")
        assertEquals("No backup directory found.", (result as BackupRestoreLogic.Result.Failure).message)
        // Directory but no backups
        f.logic.ensureBackupDir()
        result = f.logic.performRestore("Chaty database")
        assertEquals("No local backups found to restore.", (result as BackupRestoreLogic.Result.Failure).message)
        // Invalid manifest kind
        File(f.docs, "ChatyBackups/bad.json").writeText("""{"kind":"something_else"}""")
        result = f.logic.performRestore("Chaty database")
        val msg = (result as BackupRestoreLogic.Result.Failure).message
        assertTrue(msg, msg.startsWith("Restore failed: "))
        assertTrue(msg, msg.contains("Invalid or unverified backup manifest format."))
    }

    @Test fun `applyBackupManifest rejects wrong kind and ignores missing prefs`() {
        val f = Fixture(tempRoot("manifest"))
        try {
            f.logic.applyBackupManifest(mapOf("kind" to "other"))
            fail("expected rejection")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid or unverified backup manifest format.", e.message)
        }
        // No preferences map: no application, no crash
        f.logic.applyBackupManifest(mapOf("kind" to "chaty_full_backup"))
        // Preferences map without universal: still no application
        f.logic.applyBackupManifest(mapOf("kind" to "chaty_full_backup", "preferences" to mapOf("home" to mapOf("rows" to 9))))
        assertTrue(f.appliedUniversal.isEmpty())
    }

    @Test fun `clear wipes temp files and keeps newest backup only`() {
        val f = Fixture(tempRoot("clear"))
        File(f.temp, "a.log").writeText("12345")       // 5 bytes
        File(f.temp, "sub").mkdirs()
        File(f.temp, "sub/b.tmp").writeText("123")     // 3 bytes
        val dir = f.logic.ensureBackupDir()
        val b1 = File(dir, "chaty_backup_1.json"); b1.writeText("12")  // 2 bytes
        val b2 = File(dir, "chaty_backup_2.json"); b2.writeText("1234") // 4 bytes
        b1.setLastModified(1000L)
        b2.setLastModified(2000L)

        val result = f.logic.clearTemporaryAndBackups()
        assertTrue(result is BackupRestoreLogic.Result.ClearedOk)
        val ok = result as BackupRestoreLogic.Result.ClearedOk
        assertEquals(5 + 3 + 2, ok.freedBytes) // temp files + oldest backup
        assertEquals("Old temporary backups & cache cleared! (0.0 MB freed)", ok.message)
        assertFalse(File(f.temp, "a.log").exists())
        assertFalse(b1.exists())
        assertTrue(b2.exists()) // newest kept
    }

    @Test fun `storage footprint sums docs and temp recursively`() {
        val f = Fixture(tempRoot("storage"))
        File(f.docs, "d1.bin").writeText("1234567890")   // 10
        val dir = f.logic.ensureBackupDir()
        File(dir, "chaty_backup_9.json").writeText("12345") // 5 (inside docs, counted like Dart)
        File(f.temp, "t1.bin").writeText("123")          // 3
        assertEquals(18L, f.logic.totalStorageBytes())
        assertEquals("ChatyBackups", f.logic.backupDirName)
        assertTrue(f.logic.backupPath.endsWith("ChatyBackups"))
        assertTrue(f.logic.databasePath.endsWith("Databases"))
    }

    @Test fun `formatMb matches Dart toStringAsFixed(1)`() {
        assertEquals("1.5", BackupRestoreLogic(
            docsDir = { File(".") }, tempDir = { File(".") },
            preferencesSnapshot = { emptyMap() }, conversationsSnapshot = { emptyList() },
            applyUniversal = {},
        ).formatMb((1.5 * 1024 * 1024).toLong()))
        assertEquals("0.0", BackupRestoreLogic(
            docsDir = { File(".") }, tempDir = { File(".") },
            preferencesSnapshot = { emptyMap() }, conversationsSnapshot = { emptyList() },
            applyUniversal = {},
        ).formatMb(100))
    }

    // ---------- Custom font install ----------

    @Test fun `custom font validation and install`() {
        val root = tempRoot("font")
        val docs = File(root, "docs").apply { mkdirs() }
        val logic = CustomFontLogic { docs }
        // Missing file
        var result = logic.install(File(root, "nope.ttf"), "nope.ttf")
        assertEquals("Invalid or empty font file.", (result as CustomFontLogic.Result.Failure).message)
        // Empty file
        val empty = File(root, "empty.otf"); empty.writeBytes(ByteArray(0))
        result = logic.install(empty, "empty.otf")
        assertEquals("Invalid or empty font file.", (result as CustomFontLogic.Result.Failure).message)
        // Valid file copies to custom_font_<name>
        val valid = File(root, "cool.ttf"); valid.writeBytes(byteArrayOf(1, 2, 3))
        result = logic.install(valid, "cool.ttf")
        assertTrue(result is CustomFontLogic.Result.Loaded)
        assertEquals("Custom font \"cool.ttf\" loaded successfully!", (result as CustomFontLogic.Result.Loaded).message)
        val dest = File(docs, "custom_font_cool.ttf")
        assertTrue(dest.exists())
        assertEquals(3L, dest.length())
    }
}
