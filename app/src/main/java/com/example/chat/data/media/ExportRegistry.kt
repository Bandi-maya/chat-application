package com.example.chat.data.media

import com.example.chat.data.local.AttachmentRef
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry of live plaintext exports, consulted by the export ContentProvider on EVERY open.
 * Token ids are random 128-bit values, never file paths. An entry opens only if: registered, not expired, the
 * session stamp still equals the one it was minted in, the file still exists and the owner policy (trusted
 * message lookup: still exists, still not view-once/restricted) still allows the source. Any failed check removes
 * the entry and deletes the file. The registry dies with the process (so no token survives a restart; leftover
 * files are purged at next start). It cannot stop a reader that already holds an open descriptor or copied bytes.
 */
class ExportRegistry(
    private val session: () -> String?,
    private val policyAllows: (AttachmentRef) -> Boolean,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    class Entry(val id: String, val file: File, val mime: String, val name: String, val ref: AttachmentRef,
                val stamp: String, val deadline: Long)

    private val entries = ConcurrentHashMap<String, Entry>()
    /** Called after every removal (expiry, revoke, clear, delete) so the owner can also revoke URI grants. */
    @Volatile var onRemoved: (String) -> Unit = {}
    private val random = SecureRandom()

    fun register(file: File, mime: String, ref: AttachmentRef, stamp: String, deadline: Long): String {
        val b = ByteArray(16).also { random.nextBytes(it) }
        val id = b.joinToString("") { "%02x".format(it) }
        entries[id] = Entry(id, file, mime, "export." + file.extension, ref, stamp, deadline)
        return id
    }

    /** The entry if (and only if) every check passes right now; otherwise removes it and deletes its file. */
    fun open(id: String): Entry? {
        val e = entries[id] ?: return null
        val ok = now() < e.deadline && session() == e.stamp && e.file.exists() && runCatching { policyAllows(e.ref) }.getOrDefault(false)
        if (!ok) { remove(id); return null }
        return e
    }

    fun isLive(id: String): Boolean = entries[id]?.let { now() < it.deadline && session() == it.stamp && it.file.exists() } == true

    fun remove(id: String) { val e = entries.remove(id) ?: return; e.file.delete(); runCatching { onRemoved(id) } }

    /** Revokes every entry matching [pred] (account clear/delete). */
    fun revokeWhere(pred: (Entry) -> Boolean) { entries.values.filter(pred).forEach { remove(it.id) } }

    /** Message/attachment deleted: revoke every export of that source. */
    fun revokeFor(ref: AttachmentRef) { entries.values.filter { it.ref == ref }.forEach { remove(it.id) } }

    fun revokeAll() { entries.keys.toList().forEach { remove(it) } }

    fun sweepExpired() { val t = now(); entries.values.filter { t >= it.deadline }.forEach { remove(it.id) } }

    companion object {
        fun mimeFor(extension: String): String = when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"
            "mp4" -> "video/mp4"; "m4a" -> "audio/mp4"; "aac" -> "audio/aac"; "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"; else -> "application/octet-stream"
        }
    }
}
