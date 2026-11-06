package com.example.chat.data.media

import android.content.Context

/** Locally consumed view-once message ids, per account, so leaving and
 *  reopening a chat keeps an expired photo/video/voice note expired (the
 *  UI promise "disappears after one view" is enforced locally). Enforcement
 *  is on-device only: it cannot stop screenshots or control a recipient's
 *  build.
 *
 *  Append-only, no cap, no eviction: a consumed id must never fall out of
 *  the set and resurrect expired media as playable. An id absent from the
 *  set resolves to the locked card (fail-closed), never to content.
 *  markConsumed performs a synchronous durable write (SharedPreferences
 *  commit) and reports success; callers run it off the main thread BEFORE
 *  revealing media, and keep the id consumed in memory even when the write
 *  fails so the card stays locked/expired for the session. Pruning on
 *  message deletion is intentionally not wired here because deletion lives
 *  in the data/repo lane (out of frontend scope). */
class ViewOnceConsumedStore private constructor(
    private val prefs: Prefs,
    accountKey: String,
) {
    /** Storage seam so the store is JVM-testable without Android. */
    interface Prefs {
        fun read(key: String): Set<String>
        /** Synchronous durable write; true only when the value hit disk. */
        fun write(key: String, ids: Set<String>): Boolean
    }

    private val key = "ids_" + accountKey.ifBlank { "default" }

    /** Result of reading the consumed set. [Failed] means UNKNOWN, never
     *  empty: callers must fail closed (block the open, show the storage
     *  error) rather than treat the id as unconsumed. */
    sealed interface ReadResult {
        data class Ok(val ids: Set<String>) : ReadResult
        data object Failed : ReadResult
    }

    /** Safe read: storage failures return [ReadResult.Failed];
     *  CancellationException propagates. */
    fun readConsumed(): ReadResult = try {
        ReadResult.Ok(prefs.read(key))
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (t: Throwable) {
        ReadResult.Failed
    }

    /** Convenience for tests/tools: empty on read failure. UI code must use
     *  [readConsumed] so failure is not mistaken for an empty set. */
    fun consumed(): Set<String> = (readConsumed() as? ReadResult.Ok)?.ids.orEmpty()

    /** Appends id durably. Returns false when the write did not reach disk;
     *  callers must still treat the id as consumed for the session. */
    @Synchronized
    fun markConsumed(id: String): Boolean {
        if (id.isBlank()) return false
        val current = prefs.read(key)
        if (current.contains(id)) return true
        return prefs.write(key, current + id)
    }

    companion object {
        /** Test seam: builds a store on any Prefs implementation. */
        internal fun create(prefs: Prefs, accountKey: String): ViewOnceConsumedStore =
            ViewOnceConsumedStore(prefs, accountKey)

        fun android(context: Context, accountKey: String): ViewOnceConsumedStore {
            val sp = context.applicationContext
                .getSharedPreferences("chaty_view_once_consumed", Context.MODE_PRIVATE)
            return ViewOnceConsumedStore(
                object : Prefs {
                    override fun read(key: String): Set<String> =
                        sp.getStringSet(key, null)?.toSet().orEmpty()

                    override fun write(key: String, ids: Set<String>): Boolean =
                        sp.edit().putStringSet(key, ids).commit()
                },
                accountKey,
            )
        }
    }
}
