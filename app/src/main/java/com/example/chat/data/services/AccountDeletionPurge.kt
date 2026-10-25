package com.example.chat.data.services

import java.io.File

/**
 * Port of purgeLocalDataForDeletedAccount (lib/features/profile/profile_actions.dart:217, api@3bee780d).
 * Runs after the server-side deletion was requested. Order and failure behavior are the Dart ones:
 *  1. EncryptedMessageOutbox.clear(userId)         - NOT guarded: a failure aborts the purge.
 *  2. MlsE2eeService.purgeLocalIdentityForUser     - guarded: failure is logged and skipped.
 *  3. ChatMediaService.purgeLocalTemporaryFiles    - best effort, never throws.
 *  4. LocalSnapshotCacheService.deleteUser(userId) - NOT guarded.
 *  5. secure storage deleteAll()                   - last, NOT guarded (covers anything older).
 */
class AccountDeletionPurge(
    private val clearOutbox: suspend (userId: String) -> Unit,
    private val purgeMlsIdentity: suspend (userId: String) -> Unit,
    private val purgeTemporaryFiles: suspend () -> Unit,
    private val deleteSnapshots: suspend (userId: String) -> Unit,
    private val deleteAllSecureStorage: suspend () -> Unit,
    private val log: (String) -> Unit = {},
) {
    suspend fun purge(deletingUserId: String) {
        clearOutbox(deletingUserId)
        try {
            purgeMlsIdentity(deletingUserId)
        } catch (error: Throwable) {
            log("Chaty MLS identity purge skipped: $error")
        }
        try {
            purgeTemporaryFiles()
        } catch (error: Throwable) {
            log("Chaty media purge failed: $error")
        }
        deleteSnapshots(deletingUserId)
        deleteAllSecureStorage()
    }

    companion object {
        /** ChatMediaService.purgeLocalTemporaryFiles: delete regular files named `chaty_*` directly inside the
         *  temp directory (no recursion, no symlink following); per-file failures are skipped. */
        fun purgeChatyTemporaryFiles(tempDir: File, log: (String) -> Unit = {}) {
            try {
                if (!tempDir.exists()) return
                tempDir.listFiles()?.forEach { entity ->
                    if (!entity.isFile || java.nio.file.Files.isSymbolicLink(entity.toPath())) return@forEach
                    if (!entity.name.startsWith("chaty_")) return@forEach
                    try {
                        entity.delete()
                    } catch (error: Throwable) {
                        log("Chaty media purge skipped ${entity.name}: $error")
                    }
                }
            } catch (error: Throwable) {
                log("Chaty media purge failed: $error")
            }
        }
    }
}
