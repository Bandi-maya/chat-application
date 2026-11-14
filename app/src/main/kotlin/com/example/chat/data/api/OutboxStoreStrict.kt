package com.example.chat.data.api

import android.content.SharedPreferences

/** Strict read/write contract for the encrypted outbox key, used by the
 *  quarantine (held-conversations) flow.
 *
 *  Why: SharedPreferences (and the EncryptedSharedPreferences wrapper)
 *  cannot signal failure through the lenient API - getString returns null
 *  for BOTH "never saved" and "present but unreadable", and commit()
 *  returns a boolean most callers ignore. For the quarantine contract that
 *  ambiguity fails open: a hold that silently never persisted, or an
 *  unreadable store read as "empty", drops the quarantine on restart.
 *
 *  Contract:
 *  - [readStrict] returns null ONLY when the key was never written
 *    (legitimate-empty). A present-but-unreadable value throws.
 *  - [writeStrict] returns only after commit() reported success; a false
 *    result or an exception propagates.
 *  - Read/write errors thrown by the store itself (keystore failures)
 *    propagate; nothing is swallowed.
 *
 *  Pure functions over the SharedPreferences interface so they are
 *  JVM-testable with a fake; EncryptedMultiAccountStore delegates here. */
internal object OutboxStoreStrict {

    /** Null = key absent (legitimate-empty); throws when the key exists but
     *  the value cannot be read. */
    fun readStrict(prefs: SharedPreferences, key: String): String? {
        if (!prefs.contains(key)) return null
        return prefs.getString(key, null)
            ?: throw IllegalStateException(
                "Encrypted outbox is present but could not be read; refusing to treat it as empty.")
    }

    /** Returns only when the write (or removal, for null/blank json) was
     *  durably committed; throws otherwise. */
    fun writeStrict(prefs: SharedPreferences, key: String, json: String?) {
        val ed = prefs.edit()
        if (json.isNullOrBlank()) ed.remove(key) else ed.putString(key, json)
        check(ed.commit()) { "Encrypted outbox write was not durably committed." }
    }
}
