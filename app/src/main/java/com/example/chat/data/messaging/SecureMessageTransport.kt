package com.example.chat.data.messaging

import com.example.chat.core.crypto.MlsEncryptor
import com.example.chat.core.util.parseIsoToInstant
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageReaction
import com.example.chat.domain.models.MessageType
import com.example.chat.domain.models.messageTypeFromDatabase
import com.example.chat.domain.models.messageTypeToDatabase
import java.time.Instant
import java.util.UUID

/** Thrown when an MLS group is still being set up; the message stays queued. */
class SecureSendPendingException(code: String) : Exception(code)

/** RPC seam over the Supabase PostgREST client (exact function names/params
 *  preserved; wiring lands with the PostgREST client slice). */
interface SupabaseRpc {
    suspend fun rpc(function: String, params: Map<String, Any?>): Any?
}

const val SECURE_SETUP_PENDING_CODE = "secure_setup_pending"

/**
 * Exact port of the api-branch secure message transport
 * (backend_service.dart sendMessage non-demo path + _deliverEncryptedMessage +
 * _messageFromRow + _isMlsSetupPendingError).
 *
 * There is intentionally NO plaintext REST send path: the api branch sends
 * only MLS ciphertexts via the send_mls_message_v1 RPC. Without a registered
 * [MlsEncryptor] every send fails closed, matching the Flutter build.
 */
class SecureMessageTransport(
    private val rpc: SupabaseRpc,
    private val currentUserId: () -> String?,
    private val encryptor: () -> MlsEncryptor?,
    private val pendingStore: PendingSecureSendStore,
) {
    data class SendResult(
        val message: ChatMessage,
        val queued: Boolean,
    )

    /** Builds the pending payload exactly as sendMessage does (metadata shape,
     *  trimmed text, uuid client id, attachment serialization). */
    fun buildPendingSend(
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
    ): PendingSecureSend {
        val effectiveClientMessageId = clientMessageId ?: UUID.randomUUID().toString()
        val attachmentMap = attachment?.let {
            mapOf<String, Any?>(
                "id" to it.id,
                "type" to it.type,
                "name" to it.name,
                "size" to it.size,
                "url" to it.url,
                "duration_seconds" to it.durationSeconds,
            )
        }
        val metadata = buildMap<String, Any?> {
            replyToMessageId?.let { put("reply_to_message_id", it) }
            replyToPreviewText?.let { put("reply_to_preview_text", it) }
            replyToSenderName?.let { put("reply_to_sender_name", it) }
            linkedTaskId?.let { put("linked_task_id", it) }
            extraMetadata?.let { putAll(it) }
            attachmentMap?.let { put("attachment", it) }
        }
        return PendingSecureSend(
            clientMessageId = effectiveClientMessageId,
            conversationId = conversationId,
            type = messageTypeToDatabase(type),
            text = text.trim(),
            metadata = metadata,
            createdAt = Instant.now(),
            attachment = attachmentMap,
            replyToMessageId = replyToMessageId,
            replyToPreviewText = replyToPreviewText,
            replyToSenderName = replyToSenderName,
            linkedTaskId = linkedTaskId,
        )
    }

    /** Exact port of _deliverEncryptedMessage: encrypt, then the
     *  send_mls_message_v1 RPC with the exact p_* parameter names. */
    suspend fun deliverEncryptedMessage(send: PendingSecureSend): String {
        val mls = encryptor()
            ?: throw IllegalStateException("Encrypted message transport is unavailable.")
        val encrypted = mls.encryptPayload(
            conversationId = send.conversationId,
            payload = mapOf(
                "type" to send.type,
                "text" to send.text,
                "metadata" to send.metadata,
            ),
        )
        val deviceId = mls.currentDeviceId
            ?: throw IllegalStateException("Encrypted device identity is unavailable.")
        val raw = rpc.rpc(
            "send_mls_message_v1",
            mapOf(
                "p_conversation_id" to send.conversationId,
                "p_client_message_id" to send.clientMessageId,
                "p_sender_device_id" to deviceId,
                "p_group_id" to encrypted.groupId,
                "p_epoch" to encrypted.epoch,
                "p_ciphertext" to encrypted.ciphertext,
            ),
        )
        return raw?.toString() ?: ""
    }

    /** Whether [error] means MLS encryption cannot happen yet because the
     *  conversation's devices have not completed key registration.
     *  Exact port of _isMlsSetupPendingError's string matching. */
    fun isMlsSetupPendingError(error: Throwable): Boolean {
        val message = error.message ?: ""
        if (message.contains("MLS group is not initialized") ||
            message.contains("every conversation member must register an MLS device") ||
            message.contains("no key packages available")
        ) return true
        val errorStr = error.toString().lowercase()
        return errorStr.contains("every conversation member must register an mls device") ||
            errorStr.contains("must register an mls device") ||
            errorStr.contains("mls group is not initialized") ||
            errorStr.contains("no key packages available") ||
            errorStr.contains("mls_membership_pending")
    }

    /** Exact port of _messageFromRow (post-decryption row -> model). */
    @Suppress("UNCHECKED_CAST")
    fun messageFromRow(row: Map<String, Any?>, currentUser: String?): ChatMessage {
        val metadata = (row["metadata"] as? Map<String, Any?>) ?: emptyMap()
        val attachmentJson = (metadata["attachment"] as? Map<String, Any?>) ?: emptyMap()
        val reactions = mutableListOf<MessageReaction>()
        (row["reactions"] as? List<*>)?.forEach { raw ->
            val item = raw as? Map<String, Any?> ?: return@forEach
            reactions.add(
                MessageReaction(
                    emoji = item["emoji"]?.toString() ?: "",
                    userIds = (item["user_ids"] as? List<*>)?.mapNotNull { it?.toString() }
                        ?: emptyList(),
                )
            )
        }
        val deletedAt = parseIsoToInstant(row["deleted_at"]?.toString())
        val senderId = row["sender_id"]?.toString() ?: ""
        val isReadByOther = row["is_read_by_other"] == true
        val isMine = senderId == currentUser

        return ChatMessage(
            id = row["id"]?.toString() ?: "",
            conversationId = row["conversation_id"]?.toString() ?: "",
            senderId = senderId,
            metadata = metadata,
            type = messageTypeFromDatabase(row["type"]?.toString()),
            text = if (deletedAt == null) row["body"]?.toString() ?: "" else "This message was deleted",
            attachment = if (attachmentJson.isEmpty()) null else MessageAttachment(
                id = attachmentJson["id"]?.toString() ?: "",
                type = attachmentJson["type"]?.toString() ?: "document",
                name = attachmentJson["name"]?.toString() ?: "Attachment",
                size = attachmentJson["size"]?.toString() ?: "",
                url = attachmentJson["url"]?.toString(),
                durationSeconds = (attachmentJson["duration_seconds"] as? Number)?.toInt()
                    ?: attachmentJson["duration_seconds"]?.toString()?.toIntOrNull() ?: 0,
            ),
            createdAt = parseIsoToInstant(row["created_at"]?.toString()) ?: Instant.now(),
            deliveryState = when {
                isReadByOther -> DeliveryState.read
                isMine -> DeliveryState.delivered
                else -> DeliveryState.delivered
            },
        )
    }
}
