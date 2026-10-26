package com.example.chat.data.local

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Account-scoped, encrypted-at-rest store for media the user explicitly opened/downloaded.
 * The limit and per-entry cap are required (no defaults): [maxBytes] and
 * [maxEntryBytes] come from the user setting and the existing media limit.
 *
 * This is encryption AT REST of bytes the app already downloaded in whatever form the server
 * served them. It is unrelated to transfer/wire encryption and changes no wire format.
 * Encryption is androidx.security.crypto.EncryptedFile (AES256_GCM_HKDF_4KB streaming AEAD,
 * AndroidKeystore master key). The default MasterKey ALIAS is shared with the snapshot cache, but the files use
 * different wrapped-key mechanisms. It is NOT per-account crypto-erase: deleting the alias would make every
 * entry unreadable (a miss), and one account's files are not cryptographically separate from another's.
 *
 * Safety rules:
 *  - files live in filesDir/chaty_media/<hash(tenant|user)>/ (durable, not OS-evictable cacheDir).
 *  - the account is captured when an operation starts and re-checked before an entry is published
 *    and on every read of a returned stream; logout/account switch mid-operation => the entry is
 *    discarded / the read fails. Logged out ([identity] null) => everything is closed.
 *  - view-once/restricted attachments are never stored.
 *  - entry bytes are written under the final name (EncryptedFile binds the file name as associated
 *    data) while a ".w" marker exists; the marker is removed only after a complete, still-valid write.
 *    In-flight names are tracked so purge/eviction never touches another writer's partial file.
 *  - streaming AEAD can yield an authenticated PREFIX before the final segment is verified, so
 *    consumers get only fully verified bytes ([readVerified]) or a fully verified temp copy ([lease]).
 *  - any decrypt/auth failure deletes the entry and reads as a miss. Never plaintext, never partial.
 *  - one atomic [SessionEpoch] snapshot (tenant, user, seq, generation) is captured by every operation and re-checked
 *    before bytes/leases/tokens are handed out or published: A->B->A and clear+re-login of the same user change it.
 */
class AttachmentFileCache(
    context: Context,
    /** ONE atomic read of the message-plane identity (tenant, user, auth generation); null = signed out. */
    private val identity: () -> SessionEpoch.Identity?,
    /** Required (user setting): total ENCRYPTED bytes on disk (file sizes, ~0.4% above plaintext) allowed for the
     *  signed-in account's entries, LRU-evicted. Read on every eviction, so a lowered limit applies via [trimToLimit]. */
    private val maxBytes: () -> Long,
    /** Required: hard per-entry cap, checked while streaming, so a malicious size cannot fill storage. */
    private val maxEntryBytes: Long,
    /** Owner policy for exports: true only while the source still exists and is not view-once/restricted.
     *  Default DENY, so an unwired cache can never open an export. */
    private val exportPolicy: (AttachmentRef) -> Boolean = { false },
    /** Content check of edited output at commit. Default DENY: an unwired cache can never commit an edit.
     *  Production wiring: AndroidEditedOutputValidator. Tests may inject a fake (never production trust). */
    private val editedOutputValidator: EditedOutputValidator = EditedOutputValidator.DENY,
    private val now: () -> Long = { System.currentTimeMillis() },
) : AttachmentStore {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "chaty_media")
    private val tmpRoot = File(app.noBackupFilesDir, "chaty_media_tmp")
    private val masterKey by lazy { MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build() }
    private val inFlight = ConcurrentHashMap.newKeySet<String>()   // "<dir>/<name>"
    private val lock = Any()                                        // guards entry publish / evict / delete
    /** Serializes export/edit publication against revocation. Lock order: publishLock is a LEAF. While holding it,
     *  never call the identity source, the trusted lookup, exportPolicy or any owner callback (they may take the
     *  account-scope lock, whose holders may call back into this cache): only [SessionEpoch.stillCurrent], files
     *  and the registry. */
    private val publishLock = Any()
    private val epoch = SessionEpoch(identity)

    private class TooBig : IOException("entry too large")

    init { require(maxEntryBytes > 0) }

    private fun snap(): SessionEpoch.Snap = epoch.current { purgeTransient() }
    private fun valid(s: SessionEpoch.Snap) = epoch.valid(s) { purgeTransient() }
    private fun dirFor(s: SessionEpoch.Snap) = File(root, AttachmentCachePolicy.accountPath(s.tenant!!, s.uid!!))
    private fun tmpDirFor(s: SessionEpoch.Snap) = File(tmpRoot, AttachmentCachePolicy.accountPath(s.tenant!!, s.uid!!))
    private fun outDirFor(s: SessionEpoch.Snap) = File(outRoot, AttachmentCachePolicy.accountPath(s.tenant!!, s.uid!!))
    private val leased = ConcurrentHashMap.newKeySet<File>()
    /** Exports live in noBackupFilesDir/chaty_exports: outside filesDir and cacheDir, i.e. outside every root the
     *  app's legacy FileProvider (@xml/file_paths: cache ".", files ".") can map. Served only by the registry-gated
     *  ChatyExportContentProvider. (Other FileProvider paths: this was the only paths xml found in the repo.) */
    private val outRoot = File(app.noBackupFilesDir, "chaty_exports")
    val exportRegistry = com.example.chat.data.media.ExportRegistry({ session() }, exportPolicy, now).also { reg ->
        // Every removal path (expiry, revoke, clear, delete) also revokes the URI grant, uniformly.
        reg.onRemoved = { id -> runCatching { app.revokeUriPermission(android.net.Uri.parse(uriFor(id)), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    }
    private fun uriFor(id: String) = "content://" + app.packageName + ".exportprovider/x/" + id

    /** True when an account is signed in (view-once callers use it to refuse the network while signed out). */
    override fun isOpen(): Boolean = snap().uid != null

    override fun isEpochCurrent(expected: String): Boolean = epoch.peek().let { it.uid != null && it.stamp == expected }

    override fun session(): String? { val s = snap(); if (s.uid == null) return null; sweepExpiredExports(); return s.stamp }
    private fun enc(file: File) =
        EncryptedFile.Builder(app, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()

    override fun put(ref: AttachmentRef, source: InputStream, viewOnce: Boolean, restricted: Boolean, expectedSession: String): StorePut {
        if (!AttachmentCachePolicy.mayPersist(viewOnce, restricted)) return StorePut.NotAllowed
        val ep = snap()
        if (ep.uid == null) return StorePut.Closed
        // The caller's authorization belongs to the session it started in. If that is no longer the current one,
        // nothing is written (no dir, no marker, no byte), so old-account bytes cannot reach the new namespace.
        if (ep.stamp != expectedSession) return StorePut.Closed
        val dir = dirFor(ep)
        val name = AttachmentCachePolicy.entryName(ref)
        val key = "${dir.path}/$name"
        val file = File(dir, name)
        val marker = File(dir, "$name.w")
        if (!inFlight.add(key)) return StorePut.Error  // same entry already being written
        try {
            synchronized(lock) {
                if (!valid(ep)) throw IOException("revoked")
                dir.mkdirs()
                purgePartials(dir)
                file.delete()
                marker.createNewFile()
            }
            var total = 0L
            enc(file).openFileOutput().use { o ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = source.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxEntryBytes) throw TooBig()
                    if (!valid(ep)) throw IOException("account changed or cache cleared")
                    o.write(buf, 0, n)
                }
            }
            if (total == 0L) throw IOException("empty")
            synchronized(lock) {
                if (!valid(ep) || !file.exists()) throw IOException("revoked")
                marker.delete()
                evict(dir)
            }
            return StorePut.Stored
        } catch (t: Throwable) {
            file.delete(); marker.delete()
            return when {
                t is TooBig -> StorePut.TooLarge
                !valid(ep) -> StorePut.Closed
                else -> StorePut.Error
            }
        } finally {
            inFlight.remove(key)
        }
    }

    override fun readVerified(ref: AttachmentRef, maxBytes: Int): StoreRead<ByteArray> {
        val ep = snap()
        if (ep.uid == null) return StoreRead.Closed
        val file = completeEntry(ep, ref) ?: return StoreRead.Miss
        return try {
            val out = java.io.ByteArrayOutputStream()
            enc(file).openFileInput().use { i ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = i.read(buf); if (n < 0) break
                    if (out.size() + n > maxBytes) return StoreRead.TooLarge
                    out.write(buf, 0, n)
                }
            }
            // Full stamp, not just the user id: clear + re-login of the SAME user, or A -> B -> A, also fails here.
            if (!valid(ep)) return StoreRead.Closed
            touch(file); StoreRead.Hit(out.toByteArray())
        } catch (_: Throwable) {
            // A failure caused by a revoked session (files wiped under us) is not corruption: keep other data.
            if (!valid(ep)) StoreRead.Closed else { file.delete(); StoreRead.Corrupt }
        }
    }

    override fun lease(ref: AttachmentRef): StoreRead<LeasedFile> {
        val ep = snap()
        if (ep.uid == null) return StoreRead.Closed
        val file = completeEntry(ep, ref) ?: return StoreRead.Miss
        val dir = tmpDirFor(ep)
        val tmp = File(dir, "t_" + UUID.randomUUID() + ".tmp")
        return try {
            dir.mkdirs()
            leased.add(tmp)                                   // registered BEFORE the stamp check, so a purge finds it
            enc(file).openFileInput().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }  // throws on tamper/truncation
            if (!valid(ep)) { leased.remove(tmp); tmp.delete(); return StoreRead.Closed }
            touch(file)
            StoreRead.Hit(LeasedFile(tmp) { leased.remove(tmp); tmp.delete() })
        } catch (_: Throwable) {
            leased.remove(tmp); tmp.delete()
            if (!valid(ep)) StoreRead.Closed else { file.delete(); StoreRead.Corrupt }
        }
    }

    override fun invalidate(ref: AttachmentRef) {
        val ep = snap()
        if (ep.uid == null) return
        val dir = dirFor(ep)
        val name = AttachmentCachePolicy.entryName(ref)
        synchronized(lock) { File(dir, name).delete(); File(dir, "$name.w").delete() }
    }

    override fun exportCopy(ref: AttachmentRef, expectedSession: String, purpose: ExportPurpose, extension: String,
                            viewOnce: Boolean, restricted: Boolean, maxLifetimeMs: Long): ExportResult {
        if (!AttachmentCachePolicy.mayPersist(viewOnce, restricted)) return ExportResult.Denied("view-once or restricted")
        val ep = snap()
        if (ep.uid == null) return ExportResult.Denied("signed out")
        if (ep.stamp != expectedSession) return ExportResult.Denied("session changed")
        val file = completeEntry(ep, ref) ?: return ExportResult.Miss
        val ext = extension.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.take(5).ifEmpty { "bin" }
        val dir = outDirFor(ep)
        val out = File(dir, "x_" + UUID.randomUUID() + "." + ext)
        val deadline = now() + maxLifetimeMs
        var id: String? = null
        return try {
            dir.mkdirs()
            enc(file).openFileInput().use { i -> out.outputStream().use { o -> i.copyTo(o) } }   // throws on tamper
            val tokenId: String
            if (!valid(ep)) { out.delete(); return ExportResult.Denied("session changed") }   // fresh identity read, no lock held
            synchronized(publishLock) {                       // register and revoke are serialized
                if (!epoch.stillCurrent(ep)) { out.delete(); return ExportResult.Denied("session changed") }
                tokenId = exportRegistry.register(out, com.example.chat.data.media.ExportRegistry.mimeFor(ext), ref, ep.stamp, deadline)
            }
            id = tokenId
            // Mandatory expiry: remove the entry + file when the lifetime ends, even if nobody releases.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ dropExport(tokenId) }, maxLifetimeMs.coerceAtLeast(0))
            ExportResult.Granted(ExportToken(out, uriFor(tokenId), purpose, { exportRegistry.isLive(tokenId) }) { dropExport(tokenId) })
        } catch (_: Throwable) {
            id?.let { exportRegistry.remove(it) }; out.delete()
            if (!valid(ep)) ExportResult.Denied("session changed") else { file.delete(); ExportResult.Corrupt }
        }
    }

    private fun dropExport(id: String) = exportRegistry.remove(id)   // remove() also revokes the URI grant

    private class EditState(val session: EditSession, val ref: AttachmentRef, val stamp: String, val inputId: String,
                            val inputUri: String, val dir: File, val deadline: Long)
    private val edits = ConcurrentHashMap<String, EditState>()

    override fun beginEdit(ref: AttachmentRef, expectedSession: String, kind: EditKind, viewOnce: Boolean, restricted: Boolean,
                           maxLifetimeMs: Long): EditResult {
        val ep = snap()
        if (ep.uid == null) return EditResult.Denied("signed out")
        val exp = exportCopy(ref, expectedSession, ExportPurpose.EDIT, kind.extension, viewOnce, restricted, maxLifetimeMs)
        val token = when (exp) {
            is ExportResult.Granted -> exp.token
            is ExportResult.Denied -> return EditResult.Denied(exp.reason)
            ExportResult.Miss -> return EditResult.Miss
            else -> return EditResult.Failed
        }
        val dir = outDirFor(ep)
        val id = UUID.randomUUID().toString()
        val part = File(dir, "e_$id.part")
        return try {
            dir.mkdirs(); part.createNewFile()
            val session = EditSession(id, token.uri, part, kind)
            if (!valid(ep)) { token.release(); part.delete(); return EditResult.Denied("session changed") }
            synchronized(publishLock) {
                if (!epoch.stillCurrent(ep)) { token.release(); part.delete(); return EditResult.Denied("session changed") }
                edits[id] = EditState(session, ref, ep.stamp, id, token.uri, dir, now() + maxLifetimeMs)
                inputTokens[id] = token
            }
            EditResult.Started(session)
        } catch (_: Throwable) { token.release(); part.delete(); EditResult.Failed }
    }
    private val inputTokens = ConcurrentHashMap<String, ExportToken>()

    /**
     * Commit protocol (each step fails closed and cleans up everything it owns):
     *  1. CLAIM the session atomically (edits.remove): a second commit/abort/cancel finds nothing, so exactly one
     *     path owns the cleanup.
     *  2. Pre-checks (stamp, deadline, policy, canonical owned `e_<id>.part`, no symlink, size cap).
     *  3. COPY the editor's output into a NEW cache-owned file (new inode). After this the editor's file
     *     descriptor, if it is still open, writes only to the deleted part file; the copy has no other writer.
     *  4. Validate the COPY (magic bytes + native decode). This is the expensive step.
     *  5. Under [publishLock]: re-check stamp, deadline, policy and that the copy is still the same size, then
     *     register the NEW token. Revocation (purge/clear/delete) takes the same lock, so a token is either
     *     registered before the revoke (and is revoked) or the post-check here fails and nothing is minted.
     * Limit: a process or user with the same Unix uid/root could still modify files; that is outside this model.
     */
    override fun commitEdit(session: EditSession, purpose: ExportPurpose, maxLifetimeMs: Long): ExportResult {
        val st = edits[session.id]
        if (st == null || st.session !== session) return ExportResult.Denied("unknown edit session")   // not minted here
        if (!edits.remove(session.id, st)) return ExportResult.Denied("edit already finished")           // claim
        var copy: File? = null
        fun fail(r: String): ExportResult {
            copy?.delete(); runCatching { session.outputFile.delete() }
            inputTokens.remove(session.id)?.release()
            return ExportResult.Denied(r)
        }
        val ep = snap()
        if (ep.uid == null) return fail("signed out")
        if (ep.stamp != st.stamp || now() >= st.deadline) return fail("expired or session changed")
        if (!exportPolicy.invoke(st.ref)) return fail("source no longer allowed")
        val out = session.outputFile
        val canon = runCatching { out.canonicalFile }.getOrNull() ?: return fail("bad output")
        val dirCanon = runCatching { st.dir.canonicalFile }.getOrNull() ?: return fail("bad output")
        if (canon.parentFile != dirCanon || canon.name != "e_${session.id}.part" ||
            java.nio.file.Files.isSymbolicLink(out.toPath()) || !canon.isFile) return fail("output not owned")
        val final = File(st.dir, "x_" + UUID.randomUUID() + "." + session.kind.extension)
        copy = final
        val size = try {
            var total = 0L
            canon.inputStream().use { i -> final.outputStream().use { o ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = i.read(buf); if (n < 0) break
                    total += n
                    if (total > maxEntryBytes) throw TooBig()
                    o.write(buf, 0, n)
                }
            } }
            total
        } catch (_: Throwable) { return fail("copy failed or too large") }
        canon.delete()                                                  // editor output no longer needed
        if (size <= 0L) return fail("bad size")
        if (!magicMatches(final, session.kind)) return fail("content does not match type")
        if (!runCatching { editedOutputValidator.isValid(final, session.kind) }.getOrDefault(false)) return fail("content failed validation")
        val tokenId: String
        // Owner callbacks (identity source, trusted lookup) may take their own locks, so they run BEFORE publishLock.
        if (!valid(ep) || ep.stamp != st.stamp || now() >= st.deadline) return fail("expired or session changed")
        if (!exportPolicy.invoke(st.ref)) return fail("source no longer allowed")
        synchronized(publishLock) {
            // Inside the lock only lock-free checks: a transition installs its snapshot BEFORE its purge, and the purge
            // needs this lock, so either we see the new snapshot here or the purge revokes the token we register.
            if (!epoch.stillCurrent(ep)) return fail("expired or session changed")
            if (!final.isFile || final.length() != size) return fail("output changed")
            tokenId = exportRegistry.register(final, session.kind.mime, st.ref, st.stamp, now() + maxLifetimeMs)
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ dropExport(tokenId) }, maxLifetimeMs.coerceAtLeast(0))
        inputTokens.remove(session.id)?.release()    // input export released, output kept, original untouched
        return ExportResult.Granted(ExportToken(final, uriFor(tokenId), purpose, { exportRegistry.isLive(tokenId) }) { dropExport(tokenId) })
    }

    override fun abortEdit(session: EditSession) {
        val st = edits[session.id]
        if (st == null || st.session !== session) return           // only sessions this cache minted
        if (!edits.remove(session.id, st)) return                  // someone else (commit/purge) owns the cleanup
        inputTokens.remove(session.id)?.release()
        session.outputFile.delete()
    }

    private fun magicMatches(f: File, kind: EditKind): Boolean = runCatching {
        val h = ByteArray(12); val n = f.inputStream().use { it.read(h) }
        when (kind) {
            EditKind.IMAGE_JPEG -> n >= 3 && h[0] == 0xFF.toByte() && h[1] == 0xD8.toByte() && h[2] == 0xFF.toByte()
            EditKind.VIDEO_MP4, EditKind.AUDIO_MP4 -> n >= 8 && String(h, 4, 4, Charsets.ISO_8859_1) == "ftyp"
        }
    }.getOrDefault(false)

    /** Message/attachment deleted: revoke its exports (registry opens fail immediately) and remove its local copy. */
    fun onAttachmentGone(ref: AttachmentRef) { exportRegistry.revokeFor(ref); invalidate(ref) }

    /** Deletes expired export files now (also runs on every session() call and on a timer per export). */
    fun sweepExpiredExports() = exportRegistry.sweepExpired()

    /** Revokes everything handed out for one account dir: export tokens (+URI grants), edit sessions and input
     *  tokens, decrypted leases. Caller has already invalidated the stamp when the account is the signed-in one. */
    private fun revokeHandedOut(accountSubPath: (File) -> Boolean) {
        synchronized(publishLock) {
            exportRegistry.revokeWhere { accountSubPath(it.file) }
            edits.values.filter { accountSubPath(it.dir) }.forEach { e ->
                if (edits.remove(e.session.id, e)) { inputTokens.remove(e.session.id)?.release(); e.session.outputFile.delete() }
            }
        }
        leased.filter { accountSubPath(it) }.forEach { leased.remove(it); it.delete() }
    }

    /** User-facing "Clear Local Media Cache" for the signed-in account (entries, decrypted temps, exports, edits). */
    fun clearCurrentAccount() {
        val s0 = snap()
        if (s0.uid == null) return
        epoch.bump()                                    // every earlier read/lease/token/edit of this account is now stale
        val dir = dirFor(s0); val out = outDirFor(s0); val tmp = tmpDirFor(s0)
        revokeHandedOut { it.path.startsWith(out.path) || it.path.startsWith(tmp.path) }
        synchronized(lock) { dir.deleteRecursively() }
        tmp.deleteRecursively(); out.deleteRecursively()
    }

    /** Applies a lowered limit now (call after the user changes the setting). */
    fun trimToLimit() { val s = snap(); if (s.uid == null) return; synchronized(lock) { evict(dirFor(s)) } }

    fun totalBytes(): Long = snap().takeIf { it.uid != null }?.let { dirFor(it) }?.listFiles { f -> f.name.endsWith(".bin") }?.sumOf { it.length() } ?: 0L

    /** Account deletion across EVERY tenant namespace (user dir name is tenant independent); works while logged out.
     *  Only that account's handed-out items are revoked; another signed-in account is preserved. */
    fun deleteAccount(userId: String) {
        if (userId.isEmpty()) return
        val name = AttachmentCachePolicy.userDir(userId)
        val s = snap()
        if (s.uid == userId) epoch.bump()
        revokeHandedOut { f -> generateSequence(f) { it.parentFile }.any { it.name == name } }
        synchronized(lock) { root.listFiles()?.forEach { File(it, name).deleteRecursively() } }
        tmpRoot.listFiles()?.forEach { File(it, name).deleteRecursively() }
        outRoot.listFiles()?.forEach { File(it, name).deleteRecursively() }
    }

    /** Full wipe, every account. */
    fun deleteAll() {
        epoch.bump()
        revokeHandedOut { true }
        synchronized(lock) { root.deleteRecursively() }; tmpRoot.deleteRecursively(); outRoot.deleteRecursively()
    }

    /** Call at app start, on logout and on account switch: decrypted temps must not outlive a session. */
    fun purgeTransientTemps() { epoch.renew(); purgeTransient() }

    /** Identity change already installed a new stamp; this removes what the old session was handed. Not reentrant
     *  into the epoch (the registry callbacks take no cache lock). New-session exports minted concurrently with a
     *  transition may be removed too: that reads as a miss (fail closed), never as stale access. */
    private fun purgeTransient() {
        revokeHandedOut { true }
        tmpRoot.deleteRecursively(); outRoot.deleteRecursively()
    }

    private fun completeEntry(s: SessionEpoch.Snap, ref: AttachmentRef): File? {
        val dir = dirFor(s)
        val name = AttachmentCachePolicy.entryName(ref)
        val file = File(dir, name)
        if (!file.exists() || File(dir, "$name.w").exists() || inFlight.contains("${dir.path}/$name")) return null
        return file
    }

    private fun touch(f: File) { f.setLastModified(now()) }

    /** Caller holds [lock]. Never deletes a name that a writer currently owns. */
    private fun purgePartials(dir: File) {
        dir.listFiles { f -> f.name.endsWith(".w") }?.forEach { m ->
            val name = m.name.removeSuffix(".w")
            if (!inFlight.contains("${dir.path}/$name")) { File(dir, name).delete(); m.delete() }
        }
    }

    /** Caller holds [lock]. Only complete entries are candidates. */
    private fun evict(dir: File) {
        val entries = dir.listFiles { f ->
            f.name.endsWith(".bin") && !File(dir, f.name + ".w").exists() && !inFlight.contains("${dir.path}/${f.name}")
        }?.associate { it.name to (it.length() to it.lastModified()) } ?: return
        AttachmentCachePolicy.evictionOrder(entries, maxBytes()).forEach { File(dir, it).delete() }
    }
}
