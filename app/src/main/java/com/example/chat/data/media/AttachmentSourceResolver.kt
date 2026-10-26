package com.example.chat.data.media

import com.example.chat.data.local.AttachmentRef
import com.example.chat.data.local.AttachmentStore
import com.example.chat.data.local.EditKind
import com.example.chat.data.local.EditResult
import com.example.chat.data.local.EditSession
import com.example.chat.data.local.ExportPurpose
import com.example.chat.data.local.ExportResult
import com.example.chat.data.local.LeasedFile
import com.example.chat.data.local.StorePut
import com.example.chat.data.local.StoreRead
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException

/**
 * What the trusted message state says about an attachment. Supplied by [AttachmentSourceResolver]'s `lookup`
 * (implemented over the signed-in account's message repository), NEVER by the caller: callers cannot claim
 * "not restricted" or invent a conversation/message/attachment triple.
 */
data class TrustedAttachment(val type: String, val viewOnce: Boolean, val restricted: Boolean)

/**
 * What a consumer asks for. The identity is conversationId + messageId + attachmentId (all non blank). The URL is
 * never part of the identity. [authorizedUrl] is called lazily and only when the network is really needed.
 * [userInitiated] = the user explicitly opened/downloaded it.
 */
class AttachmentSourceRequest(
    val ref: AttachmentRef,
    val userInitiated: Boolean,
    /** The store stamp ([AttachmentSourceResolver.currentSession]) the consumer's binding captured when it started
     *  and authorized this request. Enforced against the store before anything runs, and handed to every store
     *  write, so a switch between binding and call, or during the download, can never publish old-account data. */
    val expectedSession: String,
    /** Returns a fresh, currently authorized absolute https URL for THIS attachment. Throw IOException on failure. */
    val authorizedUrl: suspend () -> String,
)

/** Result of resolving. Consumers MUST handle every case; there is no implicit fallback. */
sealed class ResolvedSource {
    /**
     * Use the network URL directly. Only for (a) view-once/restricted while online and signed in, or (b) a plain
     * miss that could not be stored for a capacity/IO reason. For ALL private media Coil disk+memory caches and any
     * media3 cache must be OFF for Remote loads, else a parallel UNENCRYPTED copy appears on disk.
     */
    data class Remote(val url: String) : ResolvedSource()
    /** Fully verified plaintext bytes. Coil: data=bytes with disk+memory cache DISABLED. */
    class Bytes(val bytes: ByteArray) : ResolvedSource()
    /** Fully verified decrypted temp file. Consumer MUST call lease.release() when done. */
    class Leased(val lease: LeasedFile) : ResolvedSource()
    /** Nothing can be shown; show an error state. */
    data class Unavailable(val reason: Reason) : ResolvedSource()

    /** NOT_ALLOWED: unknown/deleted message, blank ids, or a policy denial (never falls back to the network). */
    enum class Reason { OFFLINE, SIGNED_OUT, CORRUPT, DOWNLOAD_FAILED, NOT_ALLOWED }
}

/**
 * Policy owner for local media availability. Every path, INCLUDING a local cache hit, is guarded:
 *  - a session stamp is captured first and re-checked after every suspension and as the very last step before
 *    anything is returned (Bytes are dropped, leases released); a different stamp => SIGNED_OUT;
 *  - the trusted lookup is applied at the start and again after each await; unknown/deleted => NOT_ALLOWED, and a
 *    restriction that appears mid flight => NOT_ALLOWED and the local copy is invalidated.
 * Order: signed out => SIGNED_OUT; blank ids or lookup null => NOT_ALLOWED; view-once/restricted (from the lookup
 * only) => never local: online Remote(fresh url), offline OFFLINE; then local read FIRST without signing a URL;
 * Corrupt => never Remote (only explicit userInitiated + online re-downloads and verifies, else CORRUPT);
 * Miss => offline OFFLINE, not userInitiated Remote (no caching), userInitiated bounded download + store + verified
 * read back. Store results: Closed => SIGNED_OUT, NotAllowed => NOT_ALLOWED (policy, not capacity), and only
 * TooLarge/Error on a plain miss may stream Remote uncached.
 */
class AttachmentSourceResolver(
    private val store: AttachmentStore,
    private val isOnline: () -> Boolean,
    /** Trusted message state for this account; null when the message/attachment is unknown, deleted or not ours. */
    private val lookup: (AttachmentRef) -> TrustedAttachment?,
    /** Opens remote bytes (caller closes). Throws IOException on failure; must not log the URL. */
    private val download: suspend (url: String) -> InputStream,
    private val maxBytesInMemory: Int = 8 * 1024 * 1024,
) {
    /** The current store stamp (null when signed out). Capture it in the consumer's binding together with the identity. */
    fun currentSession(): String? = store.session()

    /** PURE epoch check for guards that run under the owner's auth-scope lock: see [AttachmentStore.isEpochCurrent]. */
    fun isEpochCurrent(expected: String): Boolean = store.isEpochCurrent(expected)

    private fun un(r: ResolvedSource.Reason) = ResolvedSource.Unavailable(r)

    private fun validRef(ref: AttachmentRef) =
        ref.conversationId.isNotBlank() && ref.messageId.isNotBlank() && ref.attachmentId.isNotBlank()

    suspend fun resolve(r: AttachmentSourceRequest): ResolvedSource {
        val stamp = r.expectedSession
        if (store.session() != stamp) return un(ResolvedSource.Reason.SIGNED_OUT)   // binding is stale or signed out
        if (!validRef(r.ref)) return un(ResolvedSource.Reason.NOT_ALLOWED)
        val first = lookup(r.ref) ?: return un(ResolvedSource.Reason.NOT_ALLOWED)
        return finalGuard(r.ref, stamp, first, resolveInner(r, stamp, first))
    }

    /**
     * Exports a plaintext copy for EDIT/SHARE/OPEN. [expectedSession] is the stamp the UI was showing; the same
     * trusted lookup decides view-once/restricted (callers cannot pass flags). On a cache MISS, when the caller
     * passes [authorizedUrl] (an explicit user action) and the device is online, the item is downloaded with the
     * existing bound, stored, VERIFIED by reading it back, and only then exported from the stored copy; a raw
     * download is never exported. Without [authorizedUrl] a miss is returned as ExportResult.Miss.
     */
    suspend fun exportCopy(ref: AttachmentRef, expectedSession: String, purpose: ExportPurpose, extension: String,
                           maxLifetimeMs: Long, authorizedUrl: (suspend () -> String)? = null): ExportResult {
        if (!validRef(ref)) return ExportResult.Denied("invalid reference")
        if (store.session() != expectedSession) return ExportResult.Denied("session changed")
        val t = lookup(ref) ?: return ExportResult.Denied("unknown or deleted")
        var res = store.exportCopy(ref, expectedSession, purpose, extension, t.viewOnce, t.restricted, maxLifetimeMs)
        if (res is ExportResult.Miss && authorizedUrl != null && !t.viewOnce && !t.restricted) {
            if (!isOnline()) return ExportResult.Failed
            val url = try { authorizedUrl() } catch (e: CancellationException) { throw e } catch (_: Exception) { return ExportResult.Failed }
            if (store.session() != expectedSession) return ExportResult.Denied("session changed")
            val cur = lookup(ref)
            if (cur == null || cur.viewOnce || cur.restricted) return ExportResult.Denied("policy changed")
            val put = try {
                download(url).use { store.put(ref, it, viewOnce = false, restricted = false, expectedSession = expectedSession) }
            } catch (e: CancellationException) { throw e } catch (_: IOException) { return ExportResult.Failed }
            if (store.session() != expectedSession) return ExportResult.Denied("session changed")
            val after = lookup(ref)
            if (put == StorePut.Stored && (after == null || after.viewOnce || after.restricted)) { store.invalidate(ref); return ExportResult.Denied("policy changed") }
            if (put != StorePut.Stored) return if (put == StorePut.NotAllowed || put == StorePut.Closed) ExportResult.Denied("not storable") else ExportResult.Failed
            // verified read-back happens inside exportCopy (authenticated decrypt); the raw download is never exported
            res = store.exportCopy(ref, expectedSession, purpose, extension, false, false, maxLifetimeMs)
        }
        if (res is ExportResult.Granted) {
            val cur = lookup(ref)
            if (store.session() != expectedSession || cur == null || cur.viewOnce || cur.restricted) {
                res.token.release(); return ExportResult.Denied("session or policy changed")
            }
        }
        return res
    }

    /** Starts an edit (input = export of the original, output = cache-owned part file). Same guards as [exportCopy];
     *  on a cache miss with [authorizedUrl] the original is first downloaded, stored and verified. */
    suspend fun beginEdit(ref: AttachmentRef, expectedSession: String, kind: EditKind, maxLifetimeMs: Long,
                          authorizedUrl: (suspend () -> String)? = null): EditResult {
        if (!validRef(ref)) return EditResult.Denied("invalid reference")
        if (store.session() != expectedSession) return EditResult.Denied("session changed")
        val t = lookup(ref) ?: return EditResult.Denied("unknown or deleted")
        var res = store.beginEdit(ref, expectedSession, kind, t.viewOnce, t.restricted, maxLifetimeMs)
        if (res is EditResult.Miss && authorizedUrl != null && !t.viewOnce && !t.restricted) {
            when (val r = fetchIntoStore(ref, expectedSession, authorizedUrl)) {
                null -> res = store.beginEdit(ref, expectedSession, kind, false, false, maxLifetimeMs)
                else -> return if (r.startsWith("denied")) EditResult.Denied(r) else EditResult.Failed
            }
        }
        if (res is EditResult.Started) {
            val cur = lookup(ref)
            if (store.session() != expectedSession || cur == null || cur.viewOnce || cur.restricted) {
                store.abortEdit(res.session); return EditResult.Denied("session or policy changed")
            }
        }
        return res
    }

    /** Output is validated and re-checked by the store; this only adds the owner's session/policy gate first. */
    suspend fun commitEdit(session: EditSession, ref: AttachmentRef, expectedSession: String, purpose: ExportPurpose, maxLifetimeMs: Long): ExportResult {
        val cur = lookup(ref)
        if (store.session() != expectedSession || cur == null || cur.viewOnce || cur.restricted) {
            store.abortEdit(session); return ExportResult.Denied("session or policy changed")
        }
        // The store validates content natively (blocking decode/parse), so it runs on IO. The store may mint a
        // token while the caller is being cancelled, and withContext then throws instead of returning it. So the
        // Granted result is captured INSIDE the IO block into an outer var, and every non-delivery path releases it
        // (release is idempotent, so a double path is safe). Not wrapped in NonCancellable: cancellation stays prompt.
        var minted: ExportResult.Granted? = null
        try {
            val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                store.commitEdit(session, purpose, maxLifetimeMs).also { if (it is ExportResult.Granted) minted = it }
            }
            if (res is ExportResult.Granted) {
                val again = lookup(ref)
                if (store.session() != expectedSession || again == null || again.viewOnce || again.restricted) {
                    res.token.release(); store.abortEdit(session)
                    return ExportResult.Denied("session or policy changed")
                }
            }
            minted = null   // ownership transferred to the caller only after the post-await recheck
            return res
        } catch (e: Throwable) {   // includes CancellationException: rethrown after cleanup
            minted?.token?.release(); store.abortEdit(session)
            throw e
        }
    }

    fun abortEdit(session: EditSession) = store.abortEdit(session)

    /** null = stored OK; "denied..." / "failed..." otherwise. */
    private suspend fun fetchIntoStore(ref: AttachmentRef, expectedSession: String, authorizedUrl: suspend () -> String): String? {
        if (!isOnline()) return "failed offline"
        val url = try { authorizedUrl() } catch (e: CancellationException) { throw e } catch (_: Exception) { return "failed url" }
        if (store.session() != expectedSession) return "denied session"
        val cur = lookup(ref)
        if (cur == null || cur.viewOnce || cur.restricted) return "denied policy"
        val put = try {
            download(url).use { store.put(ref, it, viewOnce = false, restricted = false, expectedSession = expectedSession) }
        } catch (e: CancellationException) { throw e } catch (_: IOException) { return "failed download" }
        if (store.session() != expectedSession) return "denied session"
        val after = lookup(ref)
        if (put == StorePut.Stored && (after == null || after.viewOnce || after.restricted)) { store.invalidate(ref); return "denied policy" }
        return when (put) { StorePut.Stored -> null; StorePut.NotAllowed, StorePut.Closed -> "denied not storable"; else -> "failed store" }
    }

    /** Last step before anything leaves: same session, still authorized, no new restriction. */
    private fun finalGuard(ref: AttachmentRef, stamp: String, t0: TrustedAttachment, result: ResolvedSource): ResolvedSource {
        if (result is ResolvedSource.Unavailable) return result
        val sameSession = store.session() == stamp
        val cur = lookup(ref)
        val restrictedNow = cur != null && (cur.viewOnce || cur.restricted)
        val policyOk = cur != null && (t0.viewOnce || t0.restricted || !restrictedNow)
        if (sameSession && policyOk) return result
        when (result) {                                                  // drop plaintext handed out too early
            is ResolvedSource.Leased -> result.lease.release()
            is ResolvedSource.Bytes -> java.util.Arrays.fill(result.bytes, 0)   // wipe our copy
            else -> Unit
        }
        if (sameSession && restrictedNow) store.invalidate(ref)          // restriction appeared: wipe the local copy
        return un(if (!sameSession) ResolvedSource.Reason.SIGNED_OUT else ResolvedSource.Reason.NOT_ALLOWED)
    }

    private suspend fun resolveInner(r: AttachmentSourceRequest, stamp: String, t: TrustedAttachment): ResolvedSource {
        val online = isOnline()
        if (t.viewOnce || t.restricted) {
            if (!online) return un(ResolvedSource.Reason.OFFLINE)
            val url = signUrl(r) ?: return un(ResolvedSource.Reason.DOWNLOAD_FAILED)
            return ResolvedSource.Remote(url)
        }
        return when (val local = readLocal(r.ref, t.type)) {
            is Local.Ok -> local.src
            Local.Closed -> un(ResolvedSource.Reason.SIGNED_OUT)
            Local.Corrupt ->
                if (online && r.userInitiated) fetchStoreServe(r, t, stamp, corruptBefore = true)
                else un(ResolvedSource.Reason.CORRUPT)
            Local.Miss -> when {
                !online -> un(ResolvedSource.Reason.OFFLINE)
                !r.userInitiated -> signUrl(r)?.let { ResolvedSource.Remote(it) } ?: un(ResolvedSource.Reason.DOWNLOAD_FAILED)
                else -> fetchStoreServe(r, t, stamp, corruptBefore = false)
            }
        }
    }

    private suspend fun signUrl(r: AttachmentSourceRequest): String? =
        try { r.authorizedUrl() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private suspend fun fetchStoreServe(r: AttachmentSourceRequest, t: TrustedAttachment, stamp: String, corruptBefore: Boolean): ResolvedSource {
        val url = signUrl(r) ?: return un(ResolvedSource.Reason.DOWNLOAD_FAILED)
        if (store.session() != stamp) return un(ResolvedSource.Reason.SIGNED_OUT)   // before any network use of the url
        val cur = lookup(r.ref)
        if (cur == null || cur.viewOnce || cur.restricted) return un(ResolvedSource.Reason.NOT_ALLOWED)
        val put = try {
            download(url).use { store.put(r.ref, it, viewOnce = false, restricted = false, expectedSession = stamp) }
        } catch (e: CancellationException) { throw e } catch (_: IOException) {
            return un(ResolvedSource.Reason.DOWNLOAD_FAILED)
        }
        if (store.session() != stamp) return un(ResolvedSource.Reason.SIGNED_OUT)
        return when (put) {
            StorePut.Stored -> when (val again = readLocal(r.ref, t.type)) {
                is Local.Ok -> again.src
                Local.Closed -> un(ResolvedSource.Reason.SIGNED_OUT)
                else -> un(ResolvedSource.Reason.CORRUPT)   // stored bytes failed verification
            }
            StorePut.Closed -> un(ResolvedSource.Reason.SIGNED_OUT)
            StorePut.NotAllowed -> un(ResolvedSource.Reason.NOT_ALLOWED)   // policy denial: never the network
            StorePut.TooLarge, StorePut.Error ->
                if (corruptBefore) un(ResolvedSource.Reason.CORRUPT) else ResolvedSource.Remote(url)
        }
    }

    private sealed class Local {
        class Ok(val src: ResolvedSource) : Local()
        object Miss : Local()
        object Corrupt : Local()
        object Closed : Local()
    }

    private fun readLocal(ref: AttachmentRef, type: String): Local {
        if (type == "image") {
            when (val b = store.readVerified(ref, maxBytesInMemory)) {
                is StoreRead.Hit -> return Local.Ok(ResolvedSource.Bytes(b.value))
                StoreRead.Miss -> return Local.Miss
                StoreRead.Corrupt -> return Local.Corrupt
                StoreRead.Closed -> return Local.Closed
                StoreRead.TooLarge -> Unit            // verified but big: fall through to a lease
            }
        }
        return when (val l = store.lease(ref)) {
            is StoreRead.Hit -> Local.Ok(ResolvedSource.Leased(l.value))
            StoreRead.Miss, StoreRead.TooLarge -> Local.Miss
            StoreRead.Corrupt -> Local.Corrupt
            StoreRead.Closed -> Local.Closed
        }
    }
}
