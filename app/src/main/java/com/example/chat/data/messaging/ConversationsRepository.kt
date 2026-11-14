package com.example.chat.data.messaging

import com.example.chat.core.crypto.MlsStorageInitializationException
import com.example.chat.core.crypto.MlsStorageInitializationFailure
import com.example.chat.core.util.parseIsoToInstant
import com.example.chat.data.e2ee.MlsE2eeService
import com.example.chat.data.local.SnapshotCodec
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.EncryptionStatus
import com.example.chat.domain.models.PresenceState
import com.example.chat.domain.models.UserProfile
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageReaction
import com.example.chat.domain.models.MessageType
import com.example.chat.domain.models.messageTypeFromDatabase
import com.example.chat.domain.models.messageTypeToDatabase
import java.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Port of the conversation/message loading paths in backend_service.dart
 * (_loadConversations, _loadConversationMembers, ensureConversationLoaded,
 * _loadMessages, _hydrateEncryptedMessageRow, _decryptionFailureRow,
 * markAsRead, _profileFromRow). Demo mode is not ported: the api branch's
 * production path is the only behavior.
 */
class ConversationsRepository(
    private val rpc: SupabaseRpc,
    /** Table client for the deleteConversation direct-table fallback
     *  (Dart: _client.from('conversations').delete().eq('id', ..)). */
    private val mls: () -> MlsE2eeService?,
    private val currentUserId: () -> String?,
    private val shouldSendReadReceipts: (String) -> Boolean = { true },
    private val notifyChanged: () -> Unit = {},
    /** Persistent queue for sends that cannot be encrypted yet (Dart
     *  _pendingSecureSends). Required by [sendMessage]'s offline path. */
    private val pendingStore: PendingSecureSendStore? = null,
    /** Mirrors the locator<ConnectionHealthService>().updateQueuedCount call. */
    private val onQueuedCountChanged: (Int) -> Unit = {},
    /** Mirrors the unawaited _retryPendingSecureSends kick after enqueueing. */
    private val onPendingEnqueued: () -> Unit = {},
    /** Mirrors the eventBus RealtimeEventType.messageCreated publish. */
    private val onMessageCreated: (conversationId: String, messageId: String) -> Unit = { _, _ -> },
    /** Snapshot-cache seam for _persistConversationSnapshot /
     *  _persistMessageSnapshot (LocalSnapshotCacheService). */
    private val snapshotCache: SnapshotCacheStore? = null,
    private val messageSnapshotNamespace: (Any?) -> String? = { currentUserId() },
    /** Scope for the Dart-unawaited snapshot writes. */
    private val snapshotScope: kotlinx.coroutines.CoroutineScope? = null,
    /** Account-scope capture/guard: stale responses from a previous login cannot mutate this repository. */
    private val scopeSnapshot: () -> Any? = { null },
    private val withScope: (Any?, () -> Unit) -> Boolean = { _, action -> action(); true },
    /** Called with the attachments of messages that were deleted/removed so local media copies can be invalidated.
     *  Default no-op; must not throw (guarded) and must not block for long. */
    private val onAttachmentsGone: (List<com.example.chat.data.local.AttachmentRef>) -> Unit = {},
    /** D1: the encrypted send/edit/delete plane rides the Chaty backend via
     *  ApiMlsRpcClient; defaults to [rpc] (Supabase) until wired. */
    private val secureRpc: SupabaseRpc? = null,
    /** Experimental router injection. Must honor caller account and target scope. */
    private val restTextSend: (suspend (conversationId: String, text: String) -> Unit)? = null,
    private val restSendsInFlight: (suspend (conversationId: String) -> Boolean)? = null,
    private val experimentalSecureRoutingEnabled: () -> Boolean = { com.example.chat.ReleaseFeatures.EXPERIMENTAL_MLS_CHAT_ROUTING },
    /** Cross-plane dispatch lock shared with the REST outbox flush: see
     *  ConversationDispatchLocks. When null (tests), sends and toggles run
     *  unlocked - the single-plane behavior from before. */
    private val dispatchLocks: ConversationDispatchLocks? = null,
    /** Full account+tenant+backend identity for the dispatch-lock key; wired
     *  by ServiceLocator from the live auth scope. Default (userId only)
     *  keeps tests un-keyed by tenant/backend. */
    private val lockAccountKey: () -> String = {
        ConversationDispatchLocks.accountKey(currentUserId(), null, null)
    },
    /** REST-plane quarantine hooks, invoked by [setSecureChannel] INSIDE the
     *  conversation's dispatch lock (already held for the whole toggle), so
     *  the flag write and the hold are one critical section: no enqueue,
     *  quick-reply or flush delivery can slip between them. The hooks receive
     *  the toggle's captured scope: the dispatch lock is keyed to THAT
     *  account, so the hooks must act on that identity (never live auth) and
     *  throw if the account changed. They must NOT re-acquire the dispatch
     *  lock (the Mutex is not reentrant) and must throw when the hold/release
     *  is not durable. Wired to
     *  ApiBackendService.holdQueuedMessagesForConversationLocked /
     *  releaseQueuedMessagesForConversationLocked. */
    private val holdRestSends: (suspend (expectedScope: Any?, conversationId: String) -> Int)? = null,
    private val releaseRestSends: (suspend (expectedScope: Any?, conversationId: String) -> Unit)? = null,
    /** Storage-key namespace for the secure_channels flag and sync_cursors:
     *  a hash of tenant+backend+user (wired by ServiceLocator from the live
     *  auth scope), so the same uid on another backend can never load this
     *  backend's routing flags or cursors. Reads fall back to the legacy
     *  raw-userId file when the namespaced file is absent; old files are
     *  NEVER deleted or migrated destructively. Default (tests): raw userId. */
    private val storageNamespace: () -> String = { currentUserId() ?: "anonymous" },
) {
    private suspend fun <T> withDispatchLock(
        conversationId: String,
        block: suspend () -> T,
    ): T {
        val locks = dispatchLocks ?: return block()
        return locks.withLock(lockAccountKey(), conversationId, block)
    }
    private fun notifyGone(refs: List<com.example.chat.data.local.AttachmentRef>) {
        if (refs.isNotEmpty()) runCatching { onAttachmentsGone(refs) }
    }

    /** S1: offline read fallback for [loadMessages]; reads only this repo's own
     *  snapshot-cache scope, no ApiBackendService state. */
    private val snapshotFallback = MessageSnapshotFallback(snapshotCache, messageSnapshotNamespace, withScope)

    private val stateLock = Any()
    val conversationsById = LinkedHashMap<String, Conversation>()
    val messagesByChatId = HashMap<String, MutableList<ChatMessage>>()
    val usersById = HashMap<String, UserProfile>()

    /** Clears all in-memory repository state atomically on account switch/sign-out. */
    fun resetForAccountSwitch() {
        synchronized(stateLock) {
            conversationsById.clear()
            messagesByChatId.clear()
            usersById.clear()
            lastSeqByChatId.clear()
            cursorsRestored = false
            secureChannelIds.clear()
            secureChannelsLoaded = false
            secureChannelsScope = null
        }
        notifyChanged()
    }

    // ── Per-conversation secure-channel flags (Phase 1, experimental) ──
    // When a conversation is flagged, the chat UI routes its send/read/edit/
    // delete path through the MLS plane (this repository + SecureMessageTransport
    // + MlsE2eeService) instead of the plaintext REST plane. The flag is local
    // and per-account: it lives in the encrypted snapshot cache, survives
    // restarts, and is never stored server-side.
    //
    // Failure contract (fail closed):
    //  - A storage error while reading the flag throws; it never resolves to
    //    "off", so an unreadable flag can never drop a chat onto the plaintext
    //    plane. Callers must treat "unknown" as "block all sending".
    //  - A failed update write changes nothing: memory and storage move
    //    together or not at all.
    //  - Every load/update/persist runs under the captured account scope
    //    (scopeSnapshot/withScope), so an account switch mid-flight can never
    //    read, write, or apply another account's flags - including on the
    //    failure path.
    private val secureChannelIds = HashSet<String>()
    @Volatile private var secureChannelsLoaded = false
    @Volatile private var secureChannelsScope: Any? = null
    /** Serializes flag load/update: concurrent per-chat toggles cannot
     *  overwrite each other, and an older load cannot erase a newer toggle. */
    private val secureChannelMutex = Mutex()

    private suspend fun ensureSecureChannelsLoaded() = secureChannelMutex.withLock {
        ensureSecureChannelsLoadedLocked()
    }

    private suspend fun ensureSecureChannelsLoadedLocked() {
        val expectedScope = scopeSnapshot()
        if (secureChannelsLoaded && secureChannelsScope === expectedScope) return
        val userId = currentUserId()
            ?: throw IllegalStateException("The encryption setting is unavailable until sign-in completes.")
        val cache = snapshotCache
            ?: throw IllegalStateException("Secure settings storage is unavailable.")
        // Strict read: a corrupt or undecryptable flag file throws (fail
        // closed, never false); a missing file is null = nothing flagged yet;
        // a malformed payload (not a list of strings) also throws - corruption
        // must never read as "off". The namespaced (tenant+backend+user) file
        // wins; when it is absent the LEGACY raw-userId file is read (strict
        // too) so existing installs keep their flags. Old files are never
        // deleted; the next toggle writes the namespaced file only.
        var capturedStorage: String? = null
        check(withScope(expectedScope) { capturedStorage = storageNamespace() }) { "Account changed while loading secure routing." }
        val storageId = checkNotNull(capturedStorage)
        val raw = cache.readJsonStrict(storageId, "secure_channels")
        if (raw == null && storageId != userId) {
            val legacy = cache.readJsonStrict(userId, "secure_channels")
            check(legacy == null || legacy == emptyList<String>()) {
                "A legacy routing setting has no verified backend owner. Recheck it before sending; no settings were imported."
            }
        }
        val ids = when (raw) {
            null -> emptyList()
            is List<*> -> raw.map {
                it as? String
                    ?: throw IllegalStateException("The encryption setting is corrupt (unexpected entry). Open the chat again.")
            }
            else -> throw IllegalStateException("The encryption setting is corrupt (unexpected shape). Open the chat again.")
        }
        var applied = false
        if (!withScope(expectedScope) {
            synchronized(stateLock) {
                secureChannelIds.clear()
                secureChannelIds.addAll(ids)
            }
            // Inside the scope check: a raced account switch must not mark the
            // next account's flags as loaded.
            secureChannelsScope = expectedScope
            secureChannelsLoaded = true
            applied = true
        } || !applied) {
            throw IllegalStateException("Account changed while the encryption setting was loading. Open the chat again.")
        }
    }

    /** Whether this conversation's UI path uses the end-to-end encrypted plane.
     *  Throws when the flag cannot be read; callers must block sending in that
     *  case instead of assuming off. A true here is a routing request, not
     *  proof of encryption: readiness is decided by [isSecureTransportReady]
     *  and by the server at send time (it fails closed while setup is pending). */
    suspend fun isSecureChannel(conversationId: String): Boolean {
        val expectedScope = scopeSnapshot()
        return secureChannelMutex.withLock {
            var current = false
            check(withScope(expectedScope) { current = true } && current) { "Account changed while checking secure routing." }
            ensureSecureChannelsLoadedLocked()
            var result = false
            current = false
            check(withScope(expectedScope) {
                synchronized(stateLock) { result = secureChannelIds.contains(conversationId) }
                current = true
            } && current) { "Account changed while checking secure routing." }
            result
        }
    }

    /** Non-suspending fast read of the flag for AttachmentUploadTracker's
     *  in-dispatch-lock re-check. NEVER takes secureChannelMutex: the lock
     *  order is flag mutex -> dispatch lock, so taking it under a dispatch
     *  lock would invert and deadlock the toggle. Throws when the flags are
     *  not yet loaded for the current scope; callers must warm them with the
     *  suspending [isSecureChannel] BEFORE acquiring the dispatch lock. The
     *  in-memory set is authoritative because [setSecureChannel] persists
     *  before swapping memory (strict write), and loads are strict. */
    fun isSecureChannelLoaded(conversationId: String): Boolean {
        val expectedScope = scopeSnapshot()
        check(secureChannelsLoaded && secureChannelsScope === expectedScope) {
            "Encryption setting not loaded for this account; warm it before the dispatch lock."
        }
        return synchronized(stateLock) { secureChannelIds.contains(conversationId) }
    }

    /** Outcome of one secure-routing toggle, for the host to surface: how
     *  many queued plaintext texts were quarantined (enable) - those were
     *  NOT sent and stay visible with the clock icon - and which account
     *  identity the transition ran under. */
    data class SecureChannelTransition(
        val conversationId: String,
        val enabled: Boolean,
        val heldCount: Int,
    )

    /** Turns the experimental routing request on/off for one conversation.
     *  Persists first, then swaps memory under the captured account scope.
     *  A failed post-write scope check attempts rollback to the captured set;
     *  a rollback I/O failure requires re-reading the original account's file. Messages sent while the
     *  flag was off stay on the server unencrypted; enabling does not
     *  re-encrypt history. */
    suspend fun setSecureChannel(conversationId: String, enabled: Boolean, expectedScope: Any? = scopeSnapshot()): SecureChannelTransition {
        return secureChannelMutex.withLock {
        // Hold the conversation's dispatch lock for the whole toggle: no
        // plane on this device can be mid-delivery while the flag changes
        // (lock order: flag mutex first, dispatch lock second; flushMutex is
        // never acquired under a dispatch lock - see ConversationDispatchLocks).
        withDispatchLock(conversationId) {
        var stillCurrent = false
        check(withScope(expectedScope) { stillCurrent = true } && stillCurrent) { "Account changed while saving secure routing." }
        ensureSecureChannelsLoadedLocked()
        val cache = snapshotCache
            ?: throw IllegalStateException("Secure settings storage is unavailable.")
        if (enabled) {
            check(experimentalSecureRoutingEnabled()) { "Experimental secure routing is disabled in this build." }
            // Snapshot check only, not a proven atomic transition. Keep master gate disabled.
            val pending = restSendsInFlight ?: error("Cannot inspect pending regular sends.")
            check(!pending(conversationId)) { "Regular messages are still pending. Secure routing was not enabled." }
        }
        var capturedUser: String? = null
        var capturedStorageId: String? = null
        var previous: List<String> = emptyList()
        var next: List<String> = emptyList()
        var current = false
        check(withScope(expectedScope) {
            capturedUser = currentUserId()
            capturedStorageId = storageNamespace()
            synchronized(stateLock) {
                previous = secureChannelIds.toList()
                val copy = LinkedHashSet(secureChannelIds)
                if (enabled) copy.add(conversationId) else copy.remove(conversationId)
                next = copy.toList()
            }
            current = true
        } && current) { "Account changed while saving secure routing." }
        checkNotNull(capturedUser) { "Sign in to change secure routing." }
        // Namespaced (tenant+backend+user hash) storage key, captured under
        // the same scope guard: writes and rollbacks never touch another
        // backend's file, and the legacy raw-userId file is left in place.
        val storageId = checkNotNull(capturedStorageId) { "Sign in to change secure routing." }
        // Hold and flag write are ONE critical section under this
        // conversation's dispatch lock: queued REST texts are quarantined
        // (durably, or this throws) before the flag moves, and no send can
        // enqueue or deliver in between. If anything after the hold fails,
        // the hold is rolled back (best effort; a rollback failure fails
        // closed - messages stay quarantined while routing is unchanged).
        var heldForToggle = false
        var heldCount = 0
        try {
            if (enabled) {
                heldCount = holdRestSends?.invoke(expectedScope, conversationId) ?: 0
                heldForToggle = true
            }
            // Persist first (strict: failures throw) leaving both storage and
            // memory as they were, so a chat never half-moves between planes.
            // NOTE: writeJsonStrict can throw AFTER a durable write, so on
            // failure the durable flag state is UNCERTAIN.
            cache.writeJsonStrict(storageId, "secure_channels", next)
            var applied = false
            if (!withScope(expectedScope) {
                synchronized(stateLock) {
                    secureChannelIds.clear()
                    secureChannelIds.addAll(next)
                }
                applied = true
            } || !applied) {
                // The write already landed for the captured account; roll it back
                // with THAT account's captured set (still keyed to its userId).
                // The hold is NOT released here: the account changed, so the
                // release hooks would target the wrong account's outbox. The
                // original account's queue stays quarantined - fail closed.
                runCatching { cache.writeJsonStrict(storageId, "secure_channels", previous) }
                throw IllegalStateException("Account changed while saving secure routing. Reopen the original account to check the saved setting; its queued messages stay quarantined.")
            }
        } catch (t: Throwable) {
            // Release the hold ONLY when the old flag is positively durable
            // again under the ORIGINAL account identity AND that account is
            // still current. A failed/uncertain rollback keeps the hold:
            // on restart the chat may load as encrypted with its queue still
            // quarantined (fail closed), never the reverse.
            if (heldForToggle) {
                val flagRestored = runCatching {
                    cache.writeJsonStrict(storageId, "secure_channels", previous)
                }.isSuccess
                var sameAccount = false
                withScope(expectedScope) { sameAccount = true }
                if (flagRestored && sameAccount) {
                    runCatching { releaseRestSends?.invoke(expectedScope, conversationId) }
                }
            }
            throw t
        }
        if (!enabled) {
            // Releasing AFTER the flag write, still under the dispatch lock:
            // a send racing the toggle either saw the old flag (refused) or
            // sees the new one - and then the hold must already be gone. A
            // throw here fails closed: routing moved back to REST but the
            // queue stays quarantined rather than half-released.
            releaseRestSends?.invoke(expectedScope, conversationId)
        }
        notifyChanged()
        SecureChannelTransition(conversationId, enabled, heldCount)
        }
        }
    }

    /** Experimental shared text routing. Not yet an app-wide transport invariant:
     *  concurrent toggle, queued and in-flight sends need atomic dispatch guards. */
    suspend fun routeTextSend(conversationId: String, text: String, extraMetadata: Map<String, Any?>? = null) {
        val expected = scopeSnapshot()
        val secure = isSecureChannel(conversationId)
        var current = false
        check(withScope(expected) { current = true } && current) { "Account changed; nothing sent." }
        if (secure) {
            check(experimentalSecureRoutingEnabled()) { "Secure routing is unavailable in this build." }
            sendMessage(conversationId, text, extraMetadata = extraMetadata)
        } else {
            check(extraMetadata.isNullOrEmpty()) { "Text routing cannot silently discard metadata." }
            val rest = restTextSend ?: error("Regular text routing is unavailable.")
            rest(conversationId, text)
        }
    }

    /** True only when this device's MLS identity is initialized and holds a
     *  device id. Conversation-level readiness (every member's device
     *  registered) is decided by the server at send time, which fails closed
     *  with a setup-pending error; use this only to pick between
     *  "setup pending" and "ready" UX, never to claim encryption. */
    fun isSecureTransportReady(): Boolean {
        val service = mls()
        return service != null && service.isReady && service.currentDeviceId != null
    }

    /** Drives MLS group setup/catch-up for a conversation (device
     *  registration, key-package claims, group publish/join). Throws while
     *  setup cannot complete yet; callers surface "setup pending", never
     *  "encrypted". */
    suspend fun ensureSecureConversationReady(conversationId: String) {
        val service = mls()
            ?: throw IllegalStateException("Secure messaging is not available on this device.")
        service.ensureConversationReady(conversationId)
    }

    private val transport = SecureMessageTransport(
        rpc = secureRpc ?: rpc,
        currentUserId = currentUserId,
        encryptor = { mls()?.let { m -> MlsServiceEncryptorAdapter(m) } },
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun loadConversations(loadMembers: Boolean = true) {
        val expectedScope = scopeSnapshot()
        val raw = rpc.rpc("get_my_conversations", emptyMap())
        val rows = raw as? List<*> ?: emptyList<Any?>()
        val next = LinkedHashMap<String, Conversation>()

        for (item in rows) {
            val row = item as? Map<String, Any?> ?: continue
            val id = row["conversation_id"]?.toString() ?: ""
            if (id.isEmpty()) continue
            val participantIds = stringList(row["participant_ids"])
            val adminIds = stringList(row["admin_ids"])

            val conversation = Conversation(
                id = id,
                type = if (row["kind"] == "group") ConversationType.group else ConversationType.direct,
                title = row["title"]?.toString() ?: "Conversation",
                participantIds = participantIds,
                adminIds = adminIds,
                avatarInitials = row["avatar_initials"]?.toString(),
                avatarColorHex = row["avatar_color_hex"]?.toString(),
                lastMessageText = row["last_message"]?.toString() ?: "",
                lastMessageTime = parseIsoToInstant(row["last_message_at"]?.toString()) ?: Instant.now(),
                lastMessageSenderId = row["last_message_sender_id"]?.toString() ?: "",
                unreadCount = intOf(row["unread_count"]),
                isPinned = row["is_pinned"] == true,
                isArchived = row["is_archived"] == true,
                isMuted = row["is_muted"] == true,
                draftText = row["draft_text"]?.toString() ?: "",
                // The secure-channel flag alone never proves MLS readiness, so
                // it must not surface EncryptionStatus.encrypted here.
                encryptionStatus = EncryptionStatus.verificationNeeded,
            )
            next[id] = conversation
        }

        val applied = withScope(expectedScope) {
            synchronized(stateLock) {
                conversationsById.clear()
                conversationsById.putAll(next)
                messagesByChatId.keys.retainAll(next.keys)
            }
            val capturedUser = currentUserId()
            val snapshotRows = next.values.toList()
            // Dart: unawaited(_persistConversationSnapshot()) - failures logged,
            // never fatal.
            snapshotScope?.launch { persistConversationSnapshot(expectedScope, capturedUser, snapshotRows) }
        }
        if (!applied) return

        if (loadMembers) {
            coroutineScope {
                next.keys.map { id -> async { loadConversationMembers(id, expectedScope) } }
                    .forEach { it.await() }
            }
        }
    }

    suspend fun loadConversationMembers(conversationId: String, expectedScope: Any? = scopeSnapshot()) {
        val raw = rpc.rpc(
            "get_conversation_members",
            mapOf("p_conversation_id" to conversationId),
        )
        val rows = raw as? List<*> ?: return
        val profiles = rows.mapNotNull { item -> (item as? Map<String, Any?>)?.let { profileFromRow(it) } }
        withScope(expectedScope) {
            synchronized(stateLock) {
                for (profile in profiles) {
                    usersById[profile.id] = profile
                }
            }
        }
    }

    suspend fun ensureConversationLoaded(conversationId: String) {
        if (!conversationsById.containsKey(conversationId)) {
            loadConversations()
        }
        loadConversationMembers(conversationId)
        loadMessages(conversationId)
        markAsRead(conversationId)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun loadMessages(conversationId: String) {
        val expectedScope = scopeSnapshot()
        var fromSnapshot = false
        val messages: MutableList<ChatMessage> = try {
            val raw = rpc.rpc(
                "get_conversation_messages",
                mapOf(
                    "p_conversation_id" to conversationId,
                    "p_limit" to 100,
                ),
            )
            val rows = raw as? List<*> ?: emptyList<Any?>()
            val hydratedRows = mutableListOf<Map<String, Any?>>()
            for (item in rows) {
                val row = item as? Map<String, Any?> ?: continue
                if (row["is_hidden"] == true) continue
                noteSeq(conversationId, row)
                hydratedRows.add(hydrateEncryptedMessageRow(conversationId, row))
            }
            hydratedRows.map { transport.messageFromRow(it, currentUserId()) }
                .sortedBy { it.createdAt }
                .toMutableList()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            // S1 offline read fallback (Bandi, 2026-11-14): serve the encrypted
            // snapshot cache so a cold-opened chat shows its saved messages, no
            // spinner. Cached rows never advance seq cursors and are not
            // re-persisted. Nothing cached -> keep the old error path.
            val cached = snapshotFallback.read(conversationId, expectedScope)
            if (cached == null) throw error
            fromSnapshot = true
            cached.toMutableList()
        }

        // Retain any optimistic / queued messages currently in memory so they
        // don't disappear on reload.
        val currentList = messagesByChatId[conversationId] ?: mutableListOf()
        for (current in currentList) {
            if (current.deliveryState == DeliveryState.queued ||
                current.deliveryState == DeliveryState.sending
            ) {
                if (messages.none { it.id == current.id }) {
                    messages.add(current)
                }
            }
        }
        messages.sortBy { it.createdAt }
        var heldBefore: List<ChatMessage>? = null
        val applied = withScope(expectedScope) {
            heldBefore = messagesByChatId[conversationId]?.toList()
            messagesByChatId[conversationId] = messages
            notifyGone(com.example.chat.data.media.AttachmentRefs.refsGone(conversationId, heldBefore, messages))

            val conversation = conversationsById[conversationId]
            if (conversation != null && messages.isNotEmpty()) {
                val latest = messages.last()
                conversationsById[conversationId] = conversation.copy(
                    lastMessageText = latest.text,
                    lastMessageTime = latest.createdAt,
                    lastMessageSenderId = latest.senderId,
                )
            }
            val capturedUser = currentUserId()
            val snapshotCopy = messages.toList()
            // Dart: unawaited(_persistMessageSnapshot(conversationId, ..)).
            if (!fromSnapshot) snapshotScope?.launch { persistMessageSnapshot(conversationId, snapshotCopy, expectedScope, capturedUser) }
        }
        if (!applied) return
    }

    // ── Gap-ordered sync (Phase 6 / offline_sync matrix) ────────────────
    // The server assigns a per-room monotonic seq; the client tracks the
    // highest seq applied per conversation and heals gaps with after_seq
    // instead of trusting arrival order (Telegram pts/getDifference, room-scoped).

    private val lastSeqByChatId = HashMap<String, Long>()

    private fun noteSeq(conversationId: String, row: Map<String, Any?>) {
        val seq = (row["seq"] as? Number)?.toLong() ?: return
        val expected = scopeSnapshot()
        var capturedUser: String? = null
        var snapshotCursors: Map<String, Long>? = null
        synchronized(stateLock) {
            val current = lastSeqByChatId[conversationId]
            if (current == null || seq > current) {
                lastSeqByChatId[conversationId] = seq
                capturedUser = currentUserId()
                snapshotCursors = lastSeqByChatId.toMap()
            }
        }
        snapshotCursors?.let { cursors ->
            snapshotScope?.launch { persistSyncCursors(expected, capturedUser, cursors) }
        }
    }

    private suspend fun persistSyncCursors(
        expectedScope: Any? = scopeSnapshot(),
        capturedUserId: String? = currentUserId(),
        cursors: Map<String, Long>? = null,
    ) {
        val userId = capturedUserId ?: return
        val cache = snapshotCache ?: return
        // Capture the namespace INSIDE the scope guard: a switch between the
        // check and the read must not write this account's cursors into the
        // next account's file.
        var stillCurrent = false
        var ns: String? = null
        if (!withScope(expectedScope) { ns = storageNamespace(); stillCurrent = true } || !stillCurrent) return
        val payload = cursors ?: synchronized(stateLock) { lastSeqByChatId.toMap() }
        // Namespaced cursor file (tenant+backend+user hash); the legacy
        // raw-userId file is left untouched (no destructive migration).
        runCatching { cache.writeJson(ns ?: return, "sync_cursors", payload) }
    }

    /** Restores persisted cursors at session start (called from the locator
     *  after the repository is wired). */
    suspend fun restoreSyncCursors() {
        val expected = scopeSnapshot()
        val cache = snapshotCache ?: return
        var storageId: String? = null
        if (!withScope(expected) { storageId = storageNamespace() }) return
        val raw = runCatching { cache.readJson(storageId ?: return, "sync_cursors") }.getOrNull() ?: return
        @Suppress("UNCHECKED_CAST")
        val map = raw as? Map<String, Any?> ?: return
        val parsed = map.mapNotNull { (key, value) -> value?.toString()?.toLongOrNull()?.let { key to it } }.toMap()
        withScope(expected) { synchronized(stateLock) { lastSeqByChatId.putAll(parsed) } }
    }

    /** Fetches every message newer than the local cursor, oldest first, and
     *  merges them (dedupe by id). Used for reconnect catch-up and when a
     *  realtime envelope reveals a seq gap. */
    @Volatile private var cursorsRestored = false

    suspend fun catchUpConversation(conversationId: String) {
        if (!cursorsRestored) {
            cursorsRestored = true
            restoreSyncCursors()
        }
        val expectedScope = scopeSnapshot()
        val params = mutableMapOf<String, Any?>(
            "p_conversation_id" to conversationId,
            "p_limit" to 200,
        )
        lastSeqByChatId[conversationId]?.let { params["p_after_seq"] = it }
        val raw = rpc.rpc("get_conversation_messages", params)
        val rows = raw as? List<*> ?: return
        val applied = mutableListOf<ChatMessage>()
        for (item in rows) {
            val row = item as? Map<String, Any?> ?: continue
            if (row["is_hidden"] == true) continue
            noteSeq(conversationId, row)
            val hydrated = hydrateEncryptedMessageRow(conversationId, row)
            applied.add(transport.messageFromRow(hydrated, currentUserId()))
        }
        if (applied.isEmpty()) return
        withScope(expectedScope) {
            val list = messagesByChatId.getOrPut(conversationId) { mutableListOf() }
            for (message in applied) {
                if (list.none { it.id == message.id }) list.add(message)
            }
            list.sortBy { it.createdAt }
            snapshotScope?.launch { persistMessageSnapshot(conversationId, list) }
        }
        notifyChanged()
    }

    /** Realtime secure envelope from the websocket (backend new_message with
     *  the opaque MLS payload). A seq gap triggers catch-up instead of
     *  decrypting out of order; otherwise the envelope is hydrated and
     *  appended immediately. */
    suspend fun ingestSecureEnvelope(data: Map<String, Any?>) {
        val conversationId = data["room_id"]?.toString() ?: return
        val seq = (data["seq"] as? Number)?.toLong()
        val last = lastSeqByChatId[conversationId]
        if (seq != null && last != null && seq > last + 1) {
            catchUpConversation(conversationId)
            return
        }
        val row = mutableMapOf<String, Any?>(
            "id" to data["id"],
            "conversation_id" to conversationId,
            "sender_id" to data["sender_id"],
            "client_id" to data["client_id"],
            "seq" to seq,
            "created_at" to data["created_at"],
            "msg_type" to (data["msg_type"] ?: "text"),
            "encryption_version" to 2,
            "encryption_protocol" to MlsE2eeService.protocolSuite,
            "sender_device_id" to data["sender_device_id"],
            "encrypted_payload" to data["encrypted_payload"],
        )
        val expectedScope = scopeSnapshot()
        val hydrated = hydrateEncryptedMessageRow(conversationId, row)
        val message = transport.messageFromRow(hydrated, currentUserId())
        withScope(expectedScope) {
            val list = messagesByChatId.getOrPut(conversationId) { mutableListOf() }
            if (list.none { it.id == message.id }) {
                list.add(message)
                list.sortBy { it.createdAt }
                val capturedUser = currentUserId()
                val snapshotCopy = list.toList()
                snapshotScope?.launch { persistMessageSnapshot(conversationId, snapshotCopy, expectedScope, capturedUser) }
            }
        }
        seq?.let { noteSeq(conversationId, mapOf("seq" to it)) }
        notifyChanged()
    }

    /** MLS edit fan-out (chat UI matrix, 15-min server window): re-decrypt the
     *  replacement ciphertext and update the local copy in place. */
    suspend fun applySecureEdit(data: Map<String, Any?>) {
        val conversationId = data["room_id"]?.toString() ?: return
        val id = data["id"]?.toString() ?: return
        val row = mutableMapOf<String, Any?>(
            "id" to id,
            "conversation_id" to conversationId,
            "sender_id" to data["sender_id"],
            "created_at" to data["created_at"],
            "edited_at" to data["edited_at"],
            "msg_type" to (data["msg_type"] ?: "text"),
            "encryption_version" to 2,
            "encryption_protocol" to MlsE2eeService.protocolSuite,
            "sender_device_id" to data["sender_device_id"],
            "encrypted_payload" to data["encrypted_payload"],
        )
        val expectedScope = scopeSnapshot()
        val hydrated = hydrateEncryptedMessageRow(conversationId, row)
        val message = transport.messageFromRow(hydrated, currentUserId())
        withScope(expectedScope) {
            val list = messagesByChatId[conversationId] ?: return@withScope
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = message.copy(
                    createdAt = list[index].createdAt,
                    editedAt = message.editedAt ?: java.time.Instant.now(),
                )
                val capturedUser = currentUserId()
                val snapshotCopy = list.toList()
                snapshotScope?.launch { persistMessageSnapshot(conversationId, snapshotCopy, expectedScope, capturedUser) }
            }
        }
        notifyChanged()
    }

    /** Delete-for-everyone fan-out (48 h server window): tombstone the local
     *  copy; the next server fetch confirms (list rows arrive payload-less). */
    suspend fun applyRemoteDeleteForEveryone(data: Map<String, Any?>) {
        val conversationId = data["room_id"]?.toString() ?: return
        val id = data["id"]?.toString() ?: return
        if (data["for_everyone"] != true) return
        val expectedDeleteScope = scopeSnapshot()
        withScope(expectedDeleteScope) {
            val list = messagesByChatId[conversationId] ?: return@withScope
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = list[index].copy(
                    text = "",
                    isDeletedForEveryone = true,
                    editedAt = null,
                )
                val capturedUser = currentUserId()
                val snapshotCopy = list.toList()
                snapshotScope?.launch { persistMessageSnapshot(conversationId, snapshotCopy, expectedDeleteScope, capturedUser) }
            }
        }
        notifyChanged()
    }

    /** Exact port of _persistConversationSnapshot. */
    private suspend fun persistConversationSnapshot(
        expectedScope: Any? = scopeSnapshot(),
        capturedUserId: String? = currentUserId(),
        items: List<Conversation>? = null,
    ) {
        val userId = capturedUserId ?: return
        val cache = snapshotCache ?: return
        var stillCurrent = false
        if (!withScope(expectedScope) { stillCurrent = true } || !stillCurrent) return
        val rows = items ?: synchronized(stateLock) { conversationsById.values.toList() }
        runCatching {
            cache.writeJson(
                userId = userId,
                scope = "conversations",
                value = rows.map { SnapshotCodec.conversationToJson(it) },
            )
        }
    }

    /** Matrix 11/18: mirrors decrypted, user-visible messages into the
     *  SQLCipher local DB for offline global search. Installed by
     *  ServiceLocator; null in JVM tests. */
    var localMessageMirror: (suspend (expectedScope: Any?, conversationId: String, messages: List<ChatMessage>) -> Unit)? = null

    /** Exact port of _persistMessageSnapshot. Content is encrypted at rest by
     *  LocalSnapshotCacheService; nothing plaintext is ever written to disk. */
    private suspend fun persistMessageSnapshot(
        conversationId: String,
        messages: List<ChatMessage>?,
        expectedScope: Any? = scopeSnapshot(),
        capturedUserId: String? = currentUserId(),
    ) {
        val userId = messageSnapshotNamespace(expectedScope) ?: return
        val cache = snapshotCache ?: return
        if (messages.isNullOrEmpty()) return
        var stillCurrent = false
        if (!withScope(expectedScope) { stillCurrent = true } || !stillCurrent) return
        val snapshot = messages.map(com.example.chat.core.messages.ViewOnceTextPolicy::redact)
        runCatching {
            cache.writeJson(
                userId = userId,
                scope = "messages_$conversationId",
                value = snapshot.map { SnapshotCodec.chatMessageToJson(it) },
            )
        }
        if (!withScope(expectedScope) { stillCurrent = true } || !stillCurrent) return
        localMessageMirror?.let { mirror ->
            runCatching { mirror(expectedScope, conversationId, snapshot) }
        }
    }

    /** Exact port of _hydrateEncryptedMessageRow: non-MLS rows pass through;
     *  own-device messages reuse local text; others decrypt or degrade to the
     *  "Unable to decrypt" system row. Never fabricates plaintext. */
    @Suppress("UNCHECKED_CAST")
    suspend fun hydrateEncryptedMessageRow(
        conversationId: String,
        source: Map<String, Any?>,
    ): Map<String, Any?> {
        val row = source.toMutableMap()
        if (row["view_once"] == true || (row["metadata"] as? Map<*, *>)?.get("view_once") == true) {
            row["body"] = com.example.chat.core.messages.ViewOnceTextPolicy.PLACEHOLDER
            row["encrypted_payload"] = null
            row["metadata"] = mapOf("view_once" to true, "view_once_state" to row["view_once_state"],
                "encrypted" to (row["encryption_protocol"] == MlsE2eeService.protocolSuite))
            return row
        }
        if (row["encryption_protocol"] != MlsE2eeService.protocolSuite) return row
        if (row["deleted_at"] != null) return row

        val senderId = row["sender_id"]?.toString() ?: ""
        val senderDeviceId = row["sender_device_id"]?.toString() ?: ""
        val currentUser = currentUserId()
        val service = mls()
        val currentDeviceId = service?.currentDeviceId

        // If this message was sent by the current user from this local MLS
        // device, do not feed it back through processMessage(). Use the
        // existing local text / optimistic payload.
        if (currentUser != null && senderId == currentUser &&
            currentDeviceId != null && senderDeviceId == currentDeviceId
        ) {
            val existing = (messagesByChatId[conversationId] ?: mutableListOf())
                .firstOrNull {
                    it.id == row["id"] ||
                        (row["client_message_id"] != null && it.id == row["client_message_id"])
                }
            if (existing != null && existing.text.isNotEmpty()) {
                row["body"] = existing.text
                row["type"] = messageTypeToDatabase(existing.type)
                return row
            }
            // If we don't have it in memory, body may already be populated
            // from a cached snapshot.
            if (row["body"] != null && row["body"].toString().isNotEmpty()) {
                return row
            }
        }

        val ciphertext = row["encrypted_payload"]?.toString() ?: ""
        if (ciphertext.isEmpty() || service == null) {
            return decryptionFailureRow(row)
        }
        return try {
            val decrypted = service.decryptPayload(
                conversationId = conversationId,
                ciphertext = ciphertext,
            )
            val decryptedMetadata = (decrypted["metadata"] as? Map<String, Any?>) ?: emptyMap()
            val serverMetadata = (row["metadata"] as? Map<String, Any?>) ?: emptyMap()
            row["body"] = decrypted["text"]?.toString() ?: ""
            row["type"] = decrypted["type"]?.toString() ?: "text"
            row["metadata"] = decryptedMetadata + serverMetadata
            row
        } catch (error: Throwable) {
            decryptionFailureRow(row)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun decryptionFailureRow(source: Map<String, Any?>): Map<String, Any?> {
        val row = source.toMutableMap()
        row["type"] = "system"
        row["body"] = "Unable to decrypt this message"
        row["metadata"] =
            ((row["metadata"] as? Map<String, Any?>) ?: emptyMap()) + ("decryption_failed" to true)
        return row
    }

    /** Exact port of sendMessage's non-demo path: build the pending payload,
     *  try encrypted delivery, otherwise persist + optimistic queued message.
     *  Demo mode does not exist on api; without an MLS encryptor this fails
     *  closed with "Encrypted message transport is unavailable." */
    suspend fun sendMessage(
        conversationId: String,
        text: String,
        type: MessageType = MessageType.text,
        attachment: MessageAttachment? = null,
        replyToMessageId: String? = null,
        replyToPreviewText: String? = null,
        replyToSenderName: String? = null,
        linkedTaskId: String? = null,
        extraMetadata: Map<String, Any?>? = null,
        clientMessageId: String? = null,
    ): ChatMessage {
        val me = currentUserId() ?: throw Exception("Authentication required.")
        if (!conversationsById.containsKey(conversationId)) {
            // A secure-channel send can arrive before any conversation list
            // load this session (the flag lives outside the list): hydrate the
            // list once instead of failing a send the server would accept.
            loadConversations(loadMembers = false)
            if (!conversationsById.containsKey(conversationId)) {
                throw Exception("Conversation not found.")
            }
        }
        if (mls() == null) {
            throw IllegalStateException("Encrypted message transport is unavailable.")
        }

        val pendingSend = transport.buildPendingSend(
            conversationId = conversationId,
            text = text,
            type = type,
            attachment = attachment,
            replyToMessageId = replyToMessageId,
            replyToPreviewText = replyToPreviewText,
            replyToSenderName = replyToSenderName,
            linkedTaskId = linkedTaskId,
            extraMetadata = extraMetadata,
            clientMessageId = clientMessageId,
        )

        try {
            return deliverAndResolve(pendingSend, fallbackType = type)
        } catch (error: Throwable) {
            if (error is MlsStorageInitializationException &&
                error.failure != MlsStorageInitializationFailure.databaseInUse
            ) {
                // A native compatibility/storage failure cannot be repaired
                // by the pending-send retry loop: do not queue.
                throw error
            }

            val store = pendingStore ?: throw error
            store.put(me, pendingSend)

            val optimisticMsg = ChatMessage(
                id = pendingSend.clientMessageId,
                conversationId = conversationId,
                senderId = me,
                type = type,
                text = pendingSend.text,
                attachment = attachment,
                replyToMessageId = replyToMessageId,
                replyToPreviewText = replyToPreviewText,
                replyToSenderName = replyToSenderName,
                linkedTaskId = linkedTaskId,
                metadata = pendingSend.metadata,
                createdAt = pendingSend.createdAt,
                deliveryState = DeliveryState.queued,
            )
            val list = messagesByChatId.getOrPut(conversationId) { mutableListOf() }
            if (list.none { it.id == pendingSend.clientMessageId }) {
                list.add(optimisticMsg)
            }
            notifyChanged()

            onQueuedCountChanged(store.read(me).size)

            if (transport.isMlsSetupPendingError(error)) {
                throw SecureSendPendingException(SECURE_SETUP_PENDING_CODE)
            }
            // Kick the background retry (Dart: unawaited _retryPendingSecureSends).
            onPendingEnqueued()
            return optimisticMsg
        }
    }

    /** Exact port of retryPendingSecureMessage: redeliver a queued pending
     *  send without duplicating the optimistic message or re-enqueueing. */
    suspend fun retryPendingSecureMessage(item: PendingSecureSend): ChatMessage {
        return try {
            deliverAndResolve(item, fallbackType = messageTypeFromDatabase(item.type))
        } catch (error: Throwable) {
            if (transport.isMlsSetupPendingError(error)) {
                throw SecureSendPendingException(SECURE_SETUP_PENDING_CODE)
            }
            throw error
        }
    }

    /** Shared tail of _deliverEncryptedMessage: RPC, refresh, resolve the
     *  stored row or fall back to a sent-state local message, publish the
     *  messageCreated event. */
    private suspend fun deliverAndResolve(
        send: PendingSecureSend,
        fallbackType: MessageType,
    ): ChatMessage {
        // The single MLS dispatch point (direct sends AND queued retries):
        // takes the cross-plane lock so a toggle can never catch an MLS
        // delivery mid-flight.
        val messageId = withDispatchLock(send.conversationId) {
            transport.deliverEncryptedMessage(send)
        }
        kotlinx.coroutines.coroutineScope {
            val a = launch { loadMessages(send.conversationId) }
            val b = launch { loadConversations(loadMembers = false) }
            a.join(); b.join()
        }
        val me = currentUserId()
        val result = messagesByChatId[send.conversationId]
            ?.firstOrNull { it.id == messageId }
            ?: ChatMessage(
                id = messageId,
                conversationId = send.conversationId,
                senderId = me ?: "",
                type = fallbackType,
                text = send.text,
                attachment = attachmentFromPending(send.attachment),
                createdAt = send.createdAt,
                deliveryState = DeliveryState.sent,
            )
        onMessageCreated(send.conversationId, result.id)
        return result
    }

    /** Exact port of markAsUnread (local bump, then best-effort RPC). */
    suspend fun markAsUnread(conversationId: String) {
        val current = conversationsById[conversationId]
        if (current != null) {
            conversationsById[conversationId] = current.copy(
                unreadCount = if (current.unreadCount > 0) current.unreadCount else 1,
            )
            notifyChanged()
        }
        runCatching {
            rpc.rpc(
                "mark_conversation_unread",
                mapOf("p_conversation_id" to conversationId),
            )
        }
    }

    /** Exact port of clearConversation. */
    fun clearConversation(conversationId: String) {
        messagesByChatId.remove(conversationId)
        val current = conversationsById[conversationId]
        if (current != null) {
            conversationsById[conversationId] = current.copy(lastMessageText = "")
        }
        notifyChanged()
    }

    /** Exact port of deleteConversation: local removal, RPC with the
     *  direct-table delete fallback, then reload. */
    suspend fun deleteConversation(conversationId: String) {
        val heldMessages = messagesByChatId[conversationId]?.toList()
        conversationsById.remove(conversationId)
        messagesByChatId.remove(conversationId)
        notifyGone(com.example.chat.data.media.AttachmentRefs.refsOf(conversationId, heldMessages ?: emptyList()))
        notifyChanged()
        runCatching {
            rpc.rpc(
                "delete_conversation",
                mapOf("p_conversation_id" to conversationId),
            )
        }
        loadConversations()
        notifyChanged()
    }

    /** Exact port of _setConversationStateAsync (pinned/archived/muted). */
    suspend fun setConversationStateAsync(conversationId: String, field: String, value: Boolean) {
        rpc.rpc(
            "set_conversation_state",
            mapOf(
                "p_conversation_id" to conversationId,
                "p_field" to field,
                "p_value" to value,
            ),
        )
        loadConversations(loadMembers = false)
        notifyChanged()
    }

    /** Exact port of _setMessageStateAsync (pinned/starred). */
    suspend fun setMessageStateAsync(
        conversationId: String,
        messageId: String,
        field: String,
        value: Boolean,
    ) {
        rpc.rpc(
            "set_message_user_state",
            mapOf(
                "p_message_id" to messageId,
                "p_field" to field,
                "p_value" to value,
            ),
        )
        loadMessages(conversationId)
        notifyChanged()
    }

    /** Exact port of setDraft: local update + silent RPC persist. */
    suspend fun setDraft(conversationId: String, draft: String) {
        val current = conversationsById[conversationId]
        if (current != null) {
            conversationsById[conversationId] = current.copy(draftText = draft)
            notifyChanged()
        }
        persistDraft(conversationId, draft)
    }

    /** Exact port of persistDraftSilently (no listener notification). */
    suspend fun persistDraftSilently(conversationId: String, draft: String) {
        val current = conversationsById[conversationId]
        if (current != null) {
            conversationsById[conversationId] = current.copy(draftText = draft)
        }
        persistDraft(conversationId, draft)
    }

    /** Exact port of _persistDraft (best-effort, fire-and-forget in Dart). */
    suspend fun persistDraft(conversationId: String, draft: String) {
        runCatching {
            rpc.rpc(
                "set_conversation_draft",
                mapOf(
                    "p_conversation_id" to conversationId,
                    "p_draft" to draft,
                ),
            )
        }
    }

    /** Exact port of toggleReaction's optimistic half: applies the local
     *  reaction change immediately; call [toggleReactionAsync] after it. */
    fun optimisticToggleReaction(conversationId: String, messageId: String, emoji: String) {
        val userId = currentUserId() ?: return
        val list = messagesByChatId[conversationId] ?: return
        val msgIndex = list.indexOfFirst { it.id == messageId }
        if (msgIndex < 0) return

        val msg = list[msgIndex]
        val updatedReactions = mutableListOf<MessageReaction>()
        var userHadSameReaction = false

        for (r in msg.reactions) {
            val newUserIds = r.userIds.toMutableList()
            if (newUserIds.remove(userId) && r.emoji == emoji) {
                userHadSameReaction = true
            }
            if (newUserIds.isNotEmpty()) {
                updatedReactions.add(r.copy(userIds = newUserIds))
            }
        }

        if (!userHadSameReaction) {
            val existingIndex = updatedReactions.indexOfFirst { it.emoji == emoji }
            if (existingIndex >= 0) {
                val r = updatedReactions[existingIndex]
                updatedReactions[existingIndex] = r.copy(userIds = r.userIds + userId)
            } else {
                updatedReactions.add(MessageReaction(emoji = emoji, userIds = listOf(userId)))
            }
        }

        list[msgIndex] = msg.copy(reactions = updatedReactions)
        notifyChanged()
        // Keep the optimistic reaction in the encrypted offline snapshot too.
        snapshotScope?.launch {
            persistMessageSnapshot(conversationId, messagesByChatId[conversationId]?.toList())
        }
    }

    /** Syncs a reaction when the deployed backend exposes the RPC. If it does not,
     *  the local optimistic reaction remains visible and is retained in the encrypted
     *  account-scoped snapshot instead of being erased by an unconditional reload. */
    suspend fun toggleReactionAsync(conversationId: String, messageId: String, emoji: String) {
        val synced = runCatching {
            rpc.rpc(
                "toggle_message_reaction",
                mapOf("p_message_id" to messageId, "p_emoji" to emoji),
            )
        }.isSuccess
        if (synced) {
            loadMessages(conversationId)
        } else {
            persistMessageSnapshot(conversationId, messagesByChatId[conversationId]?.toList())
        }
        notifyChanged()
    }

    /** Exact port of editMessage: guards, optimistic edit, MLS re-encrypt at
     *  the current epoch (never plaintext through the legacy path for MLS
     *  messages), rollback on failure. */
    suspend fun editMessage(
        conversationId: String,
        messageId: String,
        newText: String,
    ) {
        val text = newText.trim()
        if (text.isEmpty()) {
            throw IllegalArgumentException("Message cannot be empty.")
        }

        var list = messagesByChatId[conversationId]
            ?: throw IllegalStateException("Conversation messages are not loaded.")
        var index = list.indexOfFirst { it.id == messageId }
        if (index < 0) {
            throw IllegalStateException("Message is no longer available to edit.")
        }

        // Old encrypted snapshots predate metadata persistence: rehydrate the
        // authoritative server row before choosing a transport so an offline
        // cache can never make an MLS message look like a legacy row.
        if (list[index].metadata.isEmpty()) {
            loadMessages(conversationId)
            list = messagesByChatId[conversationId]
                ?: throw IllegalStateException("Conversation messages could not be refreshed.")
            index = list.indexOfFirst { it.id == messageId }
            if (index < 0) {
                throw IllegalStateException("Message is no longer available to edit.")
            }
        }

        val original = list[index]
        if (original.isDeletedForEveryone ||
            original.metadata["decryption_failed"] == true
        ) {
            throw IllegalStateException("This message cannot be edited.")
        }
        if (original.type != MessageType.text) {
            throw IllegalArgumentException("Only text messages can be edited.")
        }
        list[index] = original.copy(text = text, editedAt = Instant.now())
        notifyChanged()
        try {
            // MLS messages must be re-encrypted at the current group epoch.
            // Never write their plaintext through the legacy RPC or table API.
            val isMlsMessage =
                original.metadata["mls_group_id"] != null ||
                    original.metadata["encryption_protocol"] == MlsE2eeService.protocolSuite
            if (isMlsMessage) {
                val service = mls()
                    ?: throw IllegalStateException("Secure messaging is not available on this device.")
                val deviceId = service.currentDeviceId
                if (!service.isReady || deviceId == null) {
                    throw IllegalStateException("Secure messaging is still initializing.")
                }
                val appMetadata = original.metadata.toMutableMap()
                appMetadata.remove("encrypted")
                appMetadata.remove("mls_group_id")
                appMetadata.remove("mls_epoch")
                appMetadata.remove("encryption_protocol")
                appMetadata.remove("decryption_failed")
                val encrypted = service.encryptEditedPayload(
                    conversationId = conversationId,
                    payload = mapOf(
                        "type" to messageTypeToDatabase(original.type),
                        "text" to text,
                        "metadata" to appMetadata,
                    ),
                )
                rpc.rpc(
                    "edit_mls_message_v1",
                    mapOf(
                        "p_message_id" to messageId,
                        "p_sender_device_id" to deviceId,
                        "p_group_id" to encrypted.groupId,
                        "p_epoch" to encrypted.epoch,
                        "p_ciphertext" to encrypted.ciphertext,
                    ),
                )
            } else {
                rpc.rpc(
                    "edit_chat_message",
                    mapOf(
                        "p_message_id" to messageId,
                        "p_text" to text,
                    ),
                )
            }
        } catch (error: Throwable) {
            // Restore the exact prior message; no unencrypted fallback write.
            list[index] = original
            notifyChanged()
            throw error
        }

        loadMessages(conversationId)
        notifyChanged()
    }

    /** Exact port of _deleteMessageAsync's non-demo path. */
    suspend fun deleteMessage(
        conversationId: String,
        messageId: String,
        forEveryone: Boolean = false,
    ) {
        val target = messagesByChatId[conversationId]?.firstOrNull { it.id == messageId }
        rpc.rpc(
            "delete_chat_message",
            mapOf(
                "p_message_id" to messageId,
                "p_for_everyone" to forEveryone,
            ),
        )
        // RPC succeeded (it throws otherwise): the message is gone for this user, drop its local media now.
        if (target != null) notifyGone(listOfNotNull(com.example.chat.data.media.AttachmentRefs.refOf(conversationId, target)))
        kotlinx.coroutines.coroutineScope {
            val a = launch { loadMessages(conversationId) }
            val b = launch { loadConversations(loadMembers = false) }
            a.join(); b.join()
        }
        notifyChanged()
    }

    suspend fun markAsRead(conversationId: String) {
        if (shouldSendReadReceipts(conversationId)) {
            rpc.rpc(
                "mark_conversation_read",
                mapOf("p_conversation_id" to conversationId),
            )
            loadConversations(loadMembers = false)
            if (messagesByChatId.containsKey(conversationId)) {
                loadMessages(conversationId)
            }
        } else {
            val current = conversationsById[conversationId]
            if (current != null && current.unreadCount != 0) {
                conversationsById[conversationId] = current.copy(unreadCount = 0)
            }
        }
        notifyChanged()
    }

    /** Exact port of searchUsersRemote's non-demo path (search_profiles
     *  RPC, result caching into usersById, optional self prepend). */
    suspend fun searchUsersRemote(
        query: String,
        includeSelf: Boolean = false,
        currentUser: () -> UserProfile? = { null },
    ): List<UserProfile> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val raw = rpc.rpc("search_profiles", mapOf("p_query" to trimmed))
        val rows = raw as? List<*> ?: emptyList<Any?>()
        val results = rows.mapNotNull { item ->
            (item as? Map<String, Any?>)?.let { profileFromRow(it) }
        }.toMutableList()
        for (profile in results) {
            usersById[profile.id] = profile
        }
        val me = currentUser()
        if (includeSelf && me != null) results.add(0, me)
        notifyChanged()
        return results
    }

    /** Exact port of getOrCreateDirectConversationAsync's non-demo path. */
    suspend fun getOrCreateDirectConversationAsync(otherUser: UserProfile): Conversation {
        val raw = rpc.rpc(
            "create_direct_conversation",
            mapOf("p_other_user_id" to otherUser.id),
        )
        val id = raw?.toString() ?: ""
        if (id.isEmpty()) throw IllegalStateException("Unable to create conversation.")
        usersById[otherUser.id] = otherUser
        loadConversations(true)
        ensureConversationLoaded(id)
        notifyChanged()
        return conversationsById[id]!!
    }

    /** Exact port of getOrCreateDirectConversation (local scan only; demo
     *  branch does not exist on api). */
    fun getOrCreateDirectConversation(
        otherUser: UserProfile,
        currentUserId: () -> String?,
    ): Conversation {
        val meId = currentUserId()
        if (meId != null) {
            for (conversation in conversationsById.values) {
                if (conversation.type == ConversationType.direct &&
                    conversation.participantIds.contains(meId) &&
                    conversation.participantIds.contains(otherUser.id)
                ) {
                    return conversation
                }
            }
        }
        throw IllegalStateException(
            "Conversation is not loaded. Use getOrCreateDirectConversationAsync().",
        )
    }

    /** Exact port of createGroup's non-demo path. */
    suspend fun createGroup(
        title: String,
        memberUserIds: List<String>,
    ): Conversation {
        val raw = rpc.rpc(
            "create_group_conversation",
            mapOf(
                "p_title" to title.trim(),
                "p_member_ids" to memberUserIds,
            ),
        )
        val id = raw?.toString() ?: ""
        if (id.isEmpty()) throw IllegalStateException("Unable to create group.")
        loadConversations(true)
        notifyChanged()
        return conversationsById[id]!!
    }

    /** Exact port of markAllAsRead: local counters cleared first and again
     *  after any server refresh, receipts persisted only where the read-
     *  receipt policy allows (ghost mode / privacy suppresses publication). */
    suspend fun markAllAsRead() {
        val unreadIds = conversationsById.values
            .filter { it.unreadCount > 0 }
            .map { it.id }
        if (unreadIds.isEmpty()) return

        fun clearLocalCounters() {
            for (id in unreadIds) {
                val conversation = conversationsById[id]
                if (conversation != null && conversation.unreadCount != 0) {
                    conversationsById[id] = conversation.copy(unreadCount = 0)
                }
            }
            notifyChanged()
        }

        clearLocalCounters()

        val persistIds = unreadIds.filter { shouldSendReadReceipts(it) }
        if (persistIds.isNotEmpty()) {
            kotlinx.coroutines.coroutineScope {
                persistIds.map { id ->
                    launch {
                        runCatching {
                            rpc.rpc(
                                "mark_conversation_read",
                                mapOf("p_conversation_id" to id),
                            )
                        }
                    }
                }.forEach { it.join() }
            }
            runCatching { loadConversations(loadMembers = false) }
        }

        // A refresh can reintroduce server-side unread counts when read
        // receipts are intentionally suppressed. Keep the user-requested
        // state.
        clearLocalCounters()
    }

    companion object {
        fun presenceFromDatabase(value: String?): PresenceState = when (value) {
            "online" -> PresenceState.online
            "away" -> PresenceState.away
            "typing" -> PresenceState.typing
            else -> PresenceState.offline
        }

        /** Exact port of _profileFromRow. */
        fun profileFromRow(
            row: Map<String, Any?>,
            email: String = "",
            phone: String = "",
        ): UserProfile = UserProfile(
            id = row["id"]?.toString() ?: "",
            displayName = row["display_name"]?.toString() ?: "Chaty User",
            username = row["username"]?.toString() ?: "user",
            avatarInitials = row["avatar_initials"]?.toString() ?: "CU",
            avatarColorHex = row["avatar_color_hex"]?.toString() ?: "0xFF6366F1",
            about = row["about"]?.toString() ?: row["bio"]?.toString() ?: "",
            presence = presenceFromDatabase(row["presence"]?.toString()),
            lastSeenAt = parseIsoToInstant(row["last_seen_at"]?.toString()) ?: Instant.now(),
            isVerified = row["is_verified"] == true,
            email = email,
            phone = if (phone.isNotEmpty()) phone else row["phone"]?.toString() ?: "",
            safetyNumber = "",
            avatarUrl = row["avatar_url"]?.toString(),
            bannerUrl = row["banner_url"]?.toString(),
        )

        internal fun stringList(value: Any?): List<String> =
            (value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

        /** Port of the sendMessage/retry fallback attachment reconstruction
         *  from a PendingSecureSend.attachment map. */
        fun attachmentFromPending(attachment: Map<String, Any?>?): MessageAttachment? {
            if (attachment == null) return null
            return MessageAttachment(
                id = attachment["id"]?.toString() ?: "",
                type = attachment["type"]?.toString() ?: "file",
                name = attachment["name"]?.toString() ?: "",
                size = attachment["size"]?.toString() ?: "",
                url = attachment["url"]?.toString(),
                durationSeconds = (attachment["duration_seconds"] as? Number)?.toInt()
                    ?: attachment["duration_seconds"]?.toString()?.toIntOrNull() ?: 0,
            )
        }

        internal fun intOf(value: Any?): Int =
            (value as? Number)?.toInt() ?: value?.toString()?.toIntOrNull() ?: 0
    }
}

/** Adapts MlsE2eeService to the transport's MlsEncryptor seam. */
class MlsServiceEncryptorAdapter(
    private val service: MlsE2eeService,
) : com.example.chat.core.crypto.MlsEncryptor {
    override val currentDeviceId: String? get() = service.currentDeviceId
    override val isReady: Boolean get() = service.isReady

    override suspend fun encryptPayload(
        conversationId: String,
        payload: Map<String, Any?>,
    ): com.example.chat.core.crypto.MlsEncryptedPayload = service.encryptPayload(conversationId, payload)

    override suspend fun decryptPayload(conversationId: String, ciphertext: String): Map<String, Any?> =
        service.decryptPayload(conversationId, ciphertext)

    override suspend fun encryptEditedPayload(
        conversationId: String,
        payload: Map<String, Any?>,
    ): com.example.chat.core.crypto.MlsEncryptedPayload = service.encryptEditedPayload(conversationId, payload)
}
