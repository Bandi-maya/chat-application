package com.example.chat.data.local

import java.io.File
import java.io.InputStream

/**
 * Trusted identity of one attachment. The attachment id alone is NOT trustworthy: the sender picks it
 * (a client generated UUID inside the message payload), so two messages, or a hostile sender, can reuse an id.
 * The cache key is therefore conversationId + messageId + attachmentId (never the URL, which rotates).
 */
data class AttachmentRef(val conversationId: String, val messageId: String, val attachmentId: String)

/** A decrypted temporary copy for consumers that need a file/URI (video/audio). Caller MUST [release]. */
class LeasedFile(val file: File, private val onRelease: () -> Unit) : AutoCloseable {
    @Volatile private var released = false
    fun release() { if (!released) { released = true; onRelease() } }
    override fun close() = release()
}

/** Outcome of reading the local store. Corrupt and Closed are NOT misses and must never fall back to plaintext/network. */
sealed class StoreRead<out T> {
    data class Hit<T>(val value: T) : StoreRead<T>()
    /** No entry (never stored or evicted). Safe to fetch again. */
    object Miss : StoreRead<Nothing>()
    /** Entry existed but failed authentication/decryption/truncation; it has been deleted. */
    object Corrupt : StoreRead<Nothing>()
    /** Nobody signed in (or account changed mid-read): local media is closed. */
    object Closed : StoreRead<Nothing>()
    /** Verified entry exists but is larger than the memory limit asked for. */
    object TooLarge : StoreRead<Nothing>()
}

/** Typed result of a write; callers must not treat every non-Stored as "capacity". */
sealed class StorePut {
    object Stored : StorePut()
    /** Signed out, or the account/tenant changed during the write. Never fall back to the network. */
    object Closed : StorePut()
    /** view-once / restricted: a POLICY denial, not a capacity problem. Never fall back to the network. */
    object NotAllowed : StorePut()
    object TooLarge : StorePut()
    /** I/O or encryption failure; nothing was published. */
    object Error : StorePut()
}

enum class ExportPurpose { EDIT, SHARE, OPEN }

/**
 * An owned plaintext copy handed to an editor or an external app. [uri] is a content:// URI on the dedicated export
 * provider (use it, never the file path). It lives in app cache under the account's directory and is deleted, with
 * its URI grants revoked, by [release], by a mandatory expiry timer, on logout/account switch and at app start.
 * This stops NEW opens only: bytes an external app already read, or a file descriptor it already holds, cannot be
 * revoked.
 */
class ExportToken(val file: File, val uri: String, val purpose: ExportPurpose, private val validCheck: () -> Boolean, private val onRelease: () -> Unit) : AutoCloseable {
    @Volatile private var released = false
    fun isValid(): Boolean = !released && validCheck()
    fun release() { if (!released) { released = true; onRelease() } }
    override fun close() = release()
}

sealed class ExportResult {
    class Granted(val token: ExportToken) : ExportResult()
    /** Policy or session denial (view-once/restricted, session mismatch). */
    data class Denied(val reason: String) : ExportResult()
    object Miss : ExportResult()
    object Corrupt : ExportResult()
    object Failed : ExportResult()
}

/** What an editor is allowed to produce. The MIME and the file magic are checked again at commit. */
enum class EditKind(val extension: String, val mime: String) {
    IMAGE_JPEG("jpg", "image/jpeg"), VIDEO_MP4("mp4", "video/mp4"), AUDIO_MP4("m4a", "audio/mp4")
}

/**
 * Cache-minted edit session. The editor READS [inputUri] (content:// on the export provider, registry gated) and
 * WRITES ONLY [outputFile] (a cache-owned ".part" path it must not change). Commit takes this object, never a path.
 */
class EditSession internal constructor(val id: String, val inputUri: String, val outputFile: File, val kind: EditKind)

sealed class EditResult {
    class Started(val session: EditSession) : EditResult()
    data class Denied(val reason: String) : EditResult()
    object Miss : EditResult()
    object Failed : EditResult()
}

/** Storage seam used by the resolver; implemented by [AttachmentFileCache] (and fakes in JVM tests). */
interface AttachmentStore {
    /** PURE, lock-free: true iff signed in per the LAST INSTALLED snapshot and its stamp equals [expected]. One atomic
     *  read: no identity lookup, no purge/revoke, no callback, no IO, so it is safe inside an owner's auth-scope lock.
     *  It cannot see an identity change that has not been installed yet; pair it with the owner's own scope check
     *  (same ApiAccountScope reference) and refresh [session] OUTSIDE the lock. */
    fun isEpochCurrent(expected: String): Boolean
    /** Stores fully or not at all. Keyed by account + [AttachmentRef]; never by URL.
     *  [expectedSession] is the stamp the caller authorized the download under (before signing the URL and
     *  opening [source]). The store compares it with the CURRENT stamp BEFORE the first write and at every publish
     *  check; a mismatch returns [StorePut.Closed] and writes nothing, so bytes fetched for the old account can
     *  never land in the new account's namespace. */
    fun put(ref: AttachmentRef, source: InputStream, viewOnce: Boolean, restricted: Boolean, expectedSession: String): StorePut

    /** Entire verified plaintext (final authentication passed). [maxBytes] bounds memory. */
    fun readVerified(ref: AttachmentRef, maxBytes: Int): StoreRead<ByteArray>

    /** Fully verified decrypted copy in a private temp file. Deleted on release/clear/logout/start. */
    fun lease(ref: AttachmentRef): StoreRead<LeasedFile>

    /** Decrypts to an owned export file. Denied when [expectedSession] != [session] (checked before decrypting and
     *  again after), or when view-once/restricted. [maxLifetimeMs] is an upper bound; no UX promise is implied. */
    fun exportCopy(ref: AttachmentRef, expectedSession: String, purpose: ExportPurpose, extension: String,
                   viewOnce: Boolean, restricted: Boolean, maxLifetimeMs: Long): ExportResult

    /** Mints an edit session: input = export of the ORIGINAL (EDIT), output = cache-owned empty ".part" file.
     *  Denied for view-once/restricted or a session mismatch. The original entry is never modified. */
    fun beginEdit(ref: AttachmentRef, expectedSession: String, kind: EditKind, viewOnce: Boolean, restricted: Boolean,
                  maxLifetimeMs: Long): EditResult

    /** Validates the session is ours, same account/session, source still allowed, output under the owned dir (no
     *  symlink), size within the cap, magic bytes match [EditKind]; then returns a NEW export token for the OUTPUT
     *  (purpose SHARE/OPEN) and deletes the input export. Any failure deletes only files this session owns. */
    fun commitEdit(session: EditSession, purpose: ExportPurpose, maxLifetimeMs: Long): ExportResult

    /** Cancel/failure: removes the input export and the output part of THIS session only. */
    fun abortEdit(session: EditSession)

    /** False while signed out. */
    fun isOpen(): Boolean

    /** Opaque stamp of the current session; changes on ANY tenant/user change, on logout (even login to the same
     *  account), on clear and on delete. null when signed out. Compare before and after every await. */
    fun session(): String?

    fun invalidate(ref: AttachmentRef)
}
