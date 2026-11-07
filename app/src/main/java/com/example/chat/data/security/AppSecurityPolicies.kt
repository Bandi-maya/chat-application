package com.example.chat.data.security

/** Matrix 18 security UX policies - all pure logic, JVM-testable.
 *  Android glue (BiometricPrompt, FLAG_SECURE, WorkManager) reads these;
 *  no UI work happens without Bandi's screenshot approval. */

/** App lock with a relock timer. Bandi's hard rule: never prompt at startup
 *  right after login - the lock only engages when the app returns to the
 *  foreground after the timer elapsed, or after the user manually locks. */
class AppLockPolicy(private val relockMillis: Long = 30_000) {

    sealed class Gate {
        object Unlocked : Gate()
        object RequireBiometric : Gate()
    }

    /** freshLogin marks the session the app just entered via login. While it
     *  stands, no lock prompt may appear on startup/resume. */
    fun gate(
        enabled: Boolean,
        freshLogin: Boolean,
        manuallyLocked: Boolean,
        backgroundedAtMillis: Long?,
        nowMillis: Long,
    ): Gate = when {
        !enabled -> Gate.Unlocked
        freshLogin -> Gate.Unlocked                 // hard rule: no prompt after login
        manuallyLocked -> Gate.RequireBiometric
        backgroundedAtMillis == null -> Gate.Unlocked
        nowMillis - backgroundedAtMillis >= relockMillis -> Gate.RequireBiometric
        else -> Gate.Unlocked
    }
}

/** Locked-chats folder: conversations hidden from the main list behind a
 *  secret code. The code itself is never stored - only its SHA-256. */
class LockedChatsPolicy(
    private val store: MutableMap<String, String>,   // conversationId -> codeSha256
    private val hash: (String) -> String,            // SHA-256 in production
) {
    fun lock(conversationId: String, secretCode: String) {
        require(secretCode.length >= 4) { "Secret code must be at least 4 characters" }
        store[conversationId] = hash(secretCode)
    }

    fun isLocked(conversationId: String): Boolean = store.containsKey(conversationId)

    /** Wrong code reveals nothing about whether the chat exists. */
    fun unlock(conversationId: String, secretCode: String): Boolean =
        if (store[conversationId] == hash(secretCode)) {
            store.remove(conversationId); true
        } else false

    /** Main conversation list filters these out; locked folder lists them. */
    fun visibleConversationIds(allIds: List<String>): List<String> =
        allIds.filterNot { isLocked(it) }
}

/** Screen security toggle (Signal-style): when on, glue sets FLAG_SECURE and
 *  swaps recents thumbnails. Per-screen so view-once can force it on. */
data class ScreenSecurityPolicy(val blockScreenshots: Boolean) {
    fun flagSecureFor(screenIsViewOnce: Boolean): Boolean =
        blockScreenshots || screenIsViewOnce
}

/** Contact key-change notice (WhatsApp-style): fire once when a contact's
 *  MLS identity key differs from the one we pinned. */
class KeyChangeDetector(
    private val pinned: MutableMap<String, String>,  // userId -> identityKeyFingerprint
) {
    sealed class Result {
        object FirstSeen : Result()
        object Unchanged : Result()
        data class Changed(val previousFingerprint: String) : Result()
    }

    fun observe(userId: String, fingerprint: String): Result {
        val old = pinned[userId]
        return when {
            old == null -> { pinned[userId] = fingerprint; Result.FirstSeen }
            old == fingerprint -> Result.Unchanged
            else -> { pinned[userId] = fingerprint; Result.Changed(old) }
        }
    }
}

/** Auto-backup schedule policy: WorkManager periodic interval + when a
 *  backup is due. Encrypted backups only (recovery-phrase relay, P1 done). */
object AutoBackupPolicy {
    const val DEFAULT_INTERVAL_HOURS: Long = 24

    fun backupDue(lastBackupMillis: Long?, nowMillis: Long, intervalHours: Long = DEFAULT_INTERVAL_HOURS): Boolean =
        lastBackupMillis == null || nowMillis - lastBackupMillis >= intervalHours * 3_600_000

    fun nextFireMillis(lastBackupMillis: Long, intervalHours: Long = DEFAULT_INTERVAL_HOURS): Long =
        lastBackupMillis + intervalHours * 3_600_000
}
