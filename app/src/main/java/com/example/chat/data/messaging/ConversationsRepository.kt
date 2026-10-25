package com.example.chat.data.messaging

import com.example.chat.core.crypto.MlsStorageInitializationException
import com.example.chat.core.crypto.MlsStorageInitializationFailure
import com.example.chat.core.util.parseIsoToInstant
import com.example.chat.data.e2ee.MlsE2eeService
import com.example.chat.data.remote.PostgrestQueryClient
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
    private val queryClient: PostgrestQueryClient? = null,
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
) {
    val conversationsById = LinkedHashMap<String, Conversation>()
    val messagesByChatId = HashMap<String, MutableList<ChatMessage>>()
    val usersById = HashMap<String, UserProfile>()

    private val transport = SecureMessageTransport(
        rpc = rpc,
        currentUserId = currentUserId,
        encryptor = { mls()?.let { m -> MlsServiceEncryptorAdapter(m) } },
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun loadConversations(loadMembers: Boolean = true) {
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
                encryptionStatus = EncryptionStatus.verificationNeeded,
            )
            next[id] = conversation
        }

        conversationsById.clear()
        conversationsById.putAll(next)
        messagesByChatId.keys.retainAll(next.keys)

        if (loadMembers) {
            coroutineScope {
                next.keys.map { id -> async { loadConversationMembers(id) } }
                    .forEach { it.await() }
            }
        }
    }

    suspend fun loadConversationMembers(conversationId: String) {
        val raw = rpc.rpc(
            "get_conversation_members",
            mapOf("p_conversation_id" to conversationId),
        )
        val rows = raw as? List<*> ?: return
        for (item in rows) {
            val row = item as? Map<String, Any?> ?: continue
            val profile = profileFromRow(row)
            usersById[profile.id] = profile
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
            hydratedRows.add(hydrateEncryptedMessageRow(conversationId, row))
        }
        val messages = hydratedRows.map { transport.messageFromRow(it, currentUserId()) }
            .sortedBy { it.createdAt }
            .toMutableList()

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
        messagesByChatId[conversationId] = messages

        val conversation = conversationsById[conversationId]
        if (conversation != null && messages.isNotEmpty()) {
            val latest = messages.last()
            conversationsById[conversationId] = conversation.copy(
                lastMessageText = latest.text,
                lastMessageTime = latest.createdAt,
                lastMessageSenderId = latest.senderId,
            )
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
            throw Exception("Conversation not found.")
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
        val messageId = transport.deliverEncryptedMessage(send)
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
        conversationsById.remove(conversationId)
        messagesByChatId.remove(conversationId)
        notifyChanged()
        runCatching {
            rpc.rpc(
                "delete_conversation",
                mapOf("p_conversation_id" to conversationId),
            )
        }.onFailure {
            runCatching {
                queryClient?.from("conversations")
                    ?.eq("id", conversationId)
                    ?.delete()
            }
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
    }

    /** Exact port of _toggleReactionAsync (demo mode does not exist on api). */
    suspend fun toggleReactionAsync(conversationId: String, messageId: String, emoji: String) {
        runCatching {
            rpc.rpc(
                "toggle_message_reaction",
                mapOf("p_message_id" to messageId, "p_emoji" to emoji),
            )
        }
        loadMessages(conversationId)
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
        rpc.rpc(
            "delete_chat_message",
            mapOf(
                "p_message_id" to messageId,
                "p_for_everyone" to forEveryone,
            ),
        )
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
