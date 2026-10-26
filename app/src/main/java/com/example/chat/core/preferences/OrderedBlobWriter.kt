package com.example.chat.core.preferences

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serialised, latest-wins local save. Each snapshot gets a sequence number when it
 * is taken (call order). Writes run one at a time (Mutex).
 *  - A snapshot is skipped if a newer one was already written OR already attempted
 *    for the same user (so a late older snapshot can never overwrite a newer one,
 *    even if the newer write failed).
 *  - "Written" advances only AFTER [save] succeeds. [save] must throw on failure
 *    (a false/ignored storage result counts as failure; see PreferencesWiring).
 *  - Failure handling is bounded: up to [maxAttempts] tries with a short backoff,
 *    then the failure is recorded ([hasUnsavedFailure]) and swallowed so it cannot
 *    crash the app scope; the next persist() writes a fresh snapshot.
 *  - Sequence is tracked per user bucket, so an account switch never drops the
 *    previous account's last save.
 *  - The save is NonCancellable so cancelling the launcher cannot drop a started write.
 */
class OrderedBlobWriter(
    private val maxAttempts: Int = 3,
    private val backoffMs: Long = 100,
    private val save: suspend (String?, Map<String, Any?>) -> Unit,
) {
    private val mutex = Mutex()
    private var seq = 0L
    private val written = HashMap<String?, Long>()
    private val attempted = HashMap<String?, Long>()
    private val failed = HashSet<String?>()

    fun nextSeq(): Long = synchronized(this) { ++seq }

    fun hasUnsavedFailure(userId: String?): Boolean = synchronized(failed) { userId in failed }

    /** Returns true if this snapshot was durably saved, false if skipped or failed. */
    suspend fun write(seq: Long, userId: String?, data: Map<String, Any?>): Boolean =
        withContext(NonCancellable) {
            mutex.withLock {
                if (seq <= (written[userId] ?: 0L) || seq < (attempted[userId] ?: 0L)) return@withLock false
                attempted[userId] = seq
                var ok = false
                for (attempt in 1..maxAttempts) {
                    try {
                        save(userId, data)
                        ok = true
                        break
                    } catch (_: Exception) {
                        if (attempt < maxAttempts) delay(backoffMs * attempt)
                    }
                }
                synchronized(failed) { if (ok) failed.remove(userId) else failed.add(userId) }
                if (ok) written[userId] = seq
                ok
            }
        }
}
