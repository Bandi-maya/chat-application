package com.example.chat.ui.settings.universal

import com.example.chat.ui.settings.universal.BackupArchiveLogic.InvalidArchive
import com.example.chat.ui.settings.universal.BackupArchiveLogic.ParsedArchive
import com.example.chat.ui.settings.universal.BackupArchiveLogic.Section

/**
 * Account-bound, transactional import for portable Chaty backups.
 *
 * Guarantees enforced here (each covered by unit tests):
 *  - ACCOUNT ISOLATION: an archive imports only into the account it was
 *    exported from. A signed-in-account mismatch is an unconditional refusal;
 *    there is no confirmation path that bypasses ownership. Switching
 *    accounts means signing in to the matching account through the normal
 *    auth flow first.
 *  - WHOLE-RESTORE TRANSACTIONALITY: every target store is staged and
 *    snapshotted before any visible mutation. A failure while staging or
 *    committing ANY section rolls every store back to its pre-import state,
 *    so a half-applied backup is never left behind.
 *  - NO LIVE-STATE INJECTION: only explicitly registered section stores are
 *    touched. Message snapshots are never a registered store - cached old
 *    messages are never written into live server-backed state.
 *
 * Pure JVM: stores are injected so tests can stage failures.
 */
object BackupPortableLogic {

    sealed class ImportResult {
        data class Ok(val appliedSections: List<String>, val message: String) : ImportResult()
        /** Archive belongs to a different account than the signed-in one. */
        data class WrongAccount(val archiveAccountId: String) : ImportResult()
        data class Failure(val message: String) : ImportResult()
    }

    /**
     * One restorable section destination (e.g. the per-user preferences blob,
     * wallpaper files, downloaded media).
     */
    interface SectionStore {
        val name: String
        /** Shown in the restore preview so the user sees where data lands. */
        val destinationDescription: String
        /** Current contents for rollback; null when nothing exists yet. */
        fun snapshot(): ByteArray?
        /** Validates and prepares [data] without visible mutation. Throws on invalid. */
        fun stage(data: ByteArray)
        /** Applies the staged data. Throws on failure. */
        fun commit()
        /** Restores [prior] (from [snapshot]); null means remove what commit created. */
        fun rollback(prior: ByteArray?)
    }

    fun importArchive(
        archive: ParsedArchive,
        currentAccountId: String?,
        storeFor: (String) -> SectionStore?,
    ): ImportResult {
        if (currentAccountId.isNullOrEmpty()) {
            return ImportResult.Failure("Sign in before restoring a backup.")
        }
        if (archive.accountId != currentAccountId) {
            return ImportResult.WrongAccount(archive.accountId)
        }
        val targets = mutableListOf<Pair<SectionStore, Section>>()
        for (section in archive.sections) {
            val store = storeFor(section.name)
                ?: return ImportResult.Failure("This backup contains data this app version cannot restore: ${section.name}")
            targets += store to section
        }
        // Stage everything first: any invalid section aborts before mutation.
        val snapshots = LinkedHashMap<String, ByteArray?>()
        try {
            for ((store, section) in targets) {
                snapshots[store.name] = store.snapshot()
                store.stage(section.data)
            }
        } catch (e: Exception) {
            return ImportResult.Failure("Backup could not be read: ${e.message ?: e.javaClass.simpleName}")
        }
        // Commit with whole-restore rollback on any failure.
        val committed = mutableListOf<SectionStore>()
        try {
            for ((store, _) in targets) {
                store.commit()
                committed += store
            }
        } catch (e: Exception) {
            val rollbackErrors = mutableListOf<String>()
            for (store in targets.map { it.first }) {
                try {
                    store.rollback(snapshots[store.name])
                } catch (r: Exception) {
                    rollbackErrors += store.name
                }
            }
            return if (rollbackErrors.isEmpty()) {
                ImportResult.Failure("Restore failed and your previous data was kept: ${e.message ?: e.javaClass.simpleName}")
            } else {
                ImportResult.Failure(
                    "Restore failed and some sections could not be rolled back (${rollbackErrors.joinToString()}). " +
                        "Error: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
        val names = committed.map { it.name }
        return ImportResult.Ok(names, "Backup restored: ${names.joinToString()}")
    }

    /** One line of the restore preview: what the section is and where it lands. */
    data class PreviewEntry(
        val sectionName: String,
        val bytes: Long,
        val destinationDescription: String,
        val restorable: Boolean,
    )

    data class Preview(
        val accountId: String,
        val createdAt: String,
        val entries: List<PreviewEntry>,
        val matchesCurrentAccount: Boolean,
    )

    /** Builds the pre-restore preview WITHOUT applying anything. */
    fun preview(
        archive: ParsedArchive,
        currentAccountId: String?,
        storeFor: (String) -> SectionStore?,
    ): Preview = Preview(
        accountId = archive.accountId,
        createdAt = archive.createdAt,
        matchesCurrentAccount = currentAccountId != null && archive.accountId == currentAccountId,
        entries = archive.sections.map { section ->
            val store = storeFor(section.name)
            PreviewEntry(
                sectionName = section.name,
                bytes = section.data.size.toLong(),
                destinationDescription = store?.destinationDescription ?: "Not restorable by this app version",
                restorable = store != null,
            )
        },
    )

    /** Validates an encrypted passphrase archive end-to-end without applying it. */
    fun readEncryptedArchive(encryptedText: String, passphrase: CharArray): ParsedArchive {
        return BackupArchiveLogic.parseZip(BackupCrypto.decryptBytesWithPassphrase(encryptedText, passphrase))
    }

    /** Packages [sections] into an encrypted archive for [accountId]. */
    fun buildEncryptedArchive(
        accountId: String,
        createdAt: String,
        sections: List<Section>,
        passphrase: CharArray,
    ): String {
        val zip = BackupArchiveLogic.buildZip(accountId, createdAt, sections)
        return BackupCrypto.encryptBytesWithPassphrase(zip, passphrase)
    }
}
