package com.example.chat.ui.profile

import java.io.Closeable
import java.io.File

/** Owns one local cropped avatar until explicit Save or discard. Never uploads. */
class ProfileAvatarDraft(private val file: File, private val delete: (File) -> Unit = { it.delete(); Unit }) : Closeable {
    private var closed = false
    val previewPath: String get() = synchronized(this) {
        check(!closed) { "Profile photo draft was discarded." }
        file.absolutePath
    }

    /** Read and deletion serialize so discard cannot remove an active reader's file.
     * Caller must compress on IO, then use the captured account's scoped upload on explicit Save only. */
    fun readBytes(): ByteArray = synchronized(this) {
        check(!closed) { "Profile photo draft was discarded." }
        check(file.length() in 1..MAX_BYTES) { "Profile photo draft is empty or too large." }
        file.readBytes().also { check(it.size.toLong() in 1..MAX_BYTES) { "Profile photo draft is empty or too large." } }
    }

    override fun close() = synchronized(this) {
        if (!closed) {
            delete(file)
            closed = true
        }
        Unit
    }

    companion object { const val MAX_BYTES = 6L * 1024 * 1024 }
}
