package com.example.chat.domain.models

import java.time.Instant

/** Exact port of lib/domain/models/contact_relationship.dart
 *  (ContactPrivacyOverride + ContactConnectionStatus). */

data class ContactPrivacyOverride(
    val ownerUserId: String,
    val targetUserId: String,
    val hideDeliveryReceipts: Boolean = false,
    val hideReadReceipts: Boolean = false,
    val hideTyping: Boolean = false,
    val hideRecording: Boolean = false,
    val hideOnline: Boolean = false,
    val hideLastSeen: Boolean = false,
) {
    fun toDatabaseMap(): Map<String, Any?> = mapOf(
        "owner_user_id" to ownerUserId,
        "target_user_id" to targetUserId,
        "hide_delivery_receipts" to hideDeliveryReceipts,
        "hide_read_receipts" to hideReadReceipts,
        "hide_typing" to hideTyping,
        "hide_recording" to hideRecording,
        "hide_online" to hideOnline,
        "hide_last_seen" to hideLastSeen,
        "updated_at" to Instant.now().toString(),
    )

    companion object {
        fun fromDatabaseMap(map: Map<String, Any?>): ContactPrivacyOverride =
            ContactPrivacyOverride(
                ownerUserId = map["owner_user_id"]?.toString() ?: "",
                targetUserId = map["target_user_id"]?.toString() ?: "",
                hideDeliveryReceipts = map["hide_delivery_receipts"] == true,
                hideReadReceipts = map["hide_read_receipts"] == true,
                hideTyping = map["hide_typing"] == true,
                hideRecording = map["hide_recording"] == true,
                hideOnline = map["hide_online"] == true,
                hideLastSeen = map["hide_last_seen"] == true,
            )
    }
}

data class ContactConnectionStatus(
    val conversationId: String? = null,
    val myAccepted: Boolean = false,
    val otherAccepted: Boolean = false,
) {
    val callsAllowed: Boolean get() = myAccepted && otherAccepted
    val isPendingIncoming: Boolean get() = !myAccepted && otherAccepted
    val isWaitingForOther: Boolean get() = myAccepted && !otherAccepted
    val exists: Boolean get() = !conversationId.isNullOrEmpty()

    companion object {
        fun fromDatabaseMap(map: Map<String, Any?>): ContactConnectionStatus =
            ContactConnectionStatus(
                conversationId = map["conversation_id"]?.toString(),
                myAccepted = map["my_accepted"] == true,
                otherAccepted = map["other_accepted"] == true,
            )
    }
}
